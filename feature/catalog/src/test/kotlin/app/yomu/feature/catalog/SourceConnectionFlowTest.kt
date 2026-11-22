package app.yomu.feature.catalog

import app.yomu.source.ConnectionKind
import app.yomu.source.ProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The wizard's steps and every way back: a connection is saved only after a check, and Back never saves. */
class SourceConnectionFlowTest {
    private val opds = ProbeResult.Recognised(ConnectionKind.OPDS, title = "My catalogue", sampleItems = 3)

    private fun atAddress(method: ConnectionMethod = ConnectionMethod.OPDS, url: String = "https://example.org/opds") =
        SourceConnectionFlow.editAddress(
            SourceConnectionFlow.chooseMethod(SourceConnectionFlow.chooseContent(SourceConnectionFlow.initial, ConnectionContent.BOOKS), method),
            address = url,
        )

    @Test
    fun `the first question is the content`() {
        assertEquals(ConnectionStep.Content, SourceConnectionFlow.initial.step)
        assertEquals(
            listOf(ConnectionMethod.OPDS, ConnectionMethod.FILES, ConnectionMethod.FOLDER),
            SourceConnectionFlow.methodsFor(ConnectionContent.BOOKS),
        )
        assertTrue(SourceConnectionFlow.methodsFor(ConnectionContent.COMICS).contains(ConnectionMethod.EXTENSION))
        assertEquals(listOf(ConnectionMethod.AUDIO_LINK, ConnectionMethod.RSS), SourceConnectionFlow.methodsFor(ConnectionContent.AUDIO))
        assertTrue(SourceConnectionFlow.methodsFor(null).isEmpty())
    }

    @Test
    fun `a hand-off method keeps the user in the wizard but opens no form`() {
        val state = SourceConnectionFlow.chooseMethod(SourceConnectionFlow.chooseContent(SourceConnectionFlow.initial, ConnectionContent.COMICS), ConnectionMethod.EXTENSION)
        assertEquals(ConnectionStep.HandOff(ConnectionMethod.EXTENSION), state.step)
        val back = SourceConnectionFlow.back(state)
        assertEquals(ConnectionStep.Method, back.step)
        assertNull(back.method)
    }

    @Test
    fun `the feed subscription is a real method, not a hand-off`() {
        val state = SourceConnectionFlow.chooseMethod(SourceConnectionFlow.chooseContent(SourceConnectionFlow.initial, ConnectionContent.AUDIO), ConnectionMethod.RSS)
        assertEquals(ConnectionStep.Address(ConnectionMethod.RSS), state.step)
    }

    @Test
    fun `an implemented method leads to the address`() {
        val state = SourceConnectionFlow.chooseMethod(SourceConnectionFlow.chooseContent(SourceConnectionFlow.initial, ConnectionContent.BOOKS), ConnectionMethod.OPDS)
        assertEquals(ConnectionStep.Address(ConnectionMethod.OPDS), state.step)
    }

    @Test
    fun `a blank address fails without a request`() {
        val state = atAddress(url = "   ")
        val checked = SourceConnectionFlow.startCheck(state)
        val failure = checked.step as ConnectionStep.Failed
        assertTrue(failure.reason is ConnectionFailure.Check)
        assertEquals("empty", (failure.reason as ConnectionFailure.Check).result.let { (it as ProbeResult.InvalidUrl).reason })
        assertEquals("", checked.checkedAddress)
    }

    @Test
    fun `the address is trimmed before the check and the save repeats it`() {
        val state = atAddress(url = "  https://example.org/opds  ")
        val probing = SourceConnectionFlow.startCheck(state)
        assertEquals("https://example.org/opds", probing.checkedAddress)
        assertEquals(ConnectionStep.Probing("https://example.org/opds"), probing.step)
        val preview = SourceConnectionFlow.checked(probing, opds)
        val saving = SourceConnectionFlow.startSave(preview)
        assertEquals("https://example.org/opds", saving?.checkedAddress)
        assertEquals(ConnectionStep.Saving, saving?.step)
    }

    @Test
    fun `a recognised document shows its preview and its own title`() {
        val state = SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(atAddress()), opds)
        assertEquals(ConnectionStep.Preview(opds), state.step)
        assertEquals("My catalogue", state.checkedTitle)
        assertEquals("My catalogue", SourceConnectionFlow.suggestedName(state))
    }

    @Test
    fun `a failure says what happened and never reaches the preview`() {
        listOf(
            ProbeResult.NeedsAuth,
            ProbeResult.NeedsBrowser,
            ProbeResult.NetworkError,
            ProbeResult.Unsupported(ConnectionKind.WEB_PAGE),
            ProbeResult.InvalidUrl("missing scheme"),
        ).forEach { result ->
            val state = SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(atAddress()), result)
            val failure = state.step as ConnectionStep.Failed
            assertEquals(ConnectionFailure.Check(result), failure.reason)
            assertNull(SourceConnectionFlow.startSave(state))
        }
    }

    @Test
    fun `saving is possible only from a preview of the checked address`() {
        assertNull(SourceConnectionFlow.startSave(SourceConnectionFlow.initial))
        assertNull(SourceConnectionFlow.startSave(atAddress()))
        val done = SourceConnectionFlow.saved(SourceConnectionFlow.startSave(SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(atAddress()), opds))!!)
        assertEquals(ConnectionStep.Done, done.step)
    }

    @Test
    fun `a save error is not a failed check`() {
        val checked = SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(atAddress()), opds)
        val failed = SourceConnectionFlow.saveFailed(checked, IllegalStateException("database is busy"))
        val reason = (failed.step as ConnectionStep.Failed).reason
        assertTrue(reason is ConnectionFailure.Save)
        assertEquals(ConnectionStep.Address(ConnectionMethod.OPDS), SourceConnectionFlow.back(failed).step)
    }

    @Test
    fun `back walks the questions in reverse and stops at the first`() {
        val address = atAddress()
        assertEquals(ConnectionStep.Method, SourceConnectionFlow.back(address).step)
        assertEquals(ConnectionStep.Content, SourceConnectionFlow.back(SourceConnectionFlow.back(address)).step)
        assertEquals(ConnectionStep.Content, SourceConnectionFlow.back(SourceConnectionFlow.initial).step)
        val preview = SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(address), opds)
        assertEquals(ConnectionStep.Address(ConnectionMethod.OPDS), SourceConnectionFlow.back(preview).step)
        val probing = SourceConnectionFlow.startCheck(address)
        assertEquals(ConnectionStep.Address(ConnectionMethod.OPDS), SourceConnectionFlow.back(probing).step)
        assertEquals(ConnectionStep.Content, SourceConnectionFlow.back(SourceConnectionFlow.back(SourceConnectionFlow.back(SourceConnectionFlow.back(address)))).step)
    }

    @Test
    fun `the suggested name falls back to the host, then to the address`() {
        val host = atAddress(url = "https://catalogue.example.org/opds")
        assertEquals(
            "catalogue.example.org",
            SourceConnectionFlow.suggestedName(SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(host), ProbeResult.Recognised(ConnectionKind.OPDS))),
        )
        // The name comes from the address that was actually checked, not from what is being typed.
        val bare = SourceConnectionFlow.startCheck(SourceConnectionFlow.editAddress(host, address = "https://catalogue.example.org"))
        assertEquals("catalogue.example.org", SourceConnectionFlow.suggestedName(bare))
        assertEquals("", SourceConnectionFlow.suggestedName(SourceConnectionFlow.editAddress(host, address = "")))
    }

    @Test
    fun `a pasted link starts a check without a chosen method`() {
        val state = SourceConnectionFlow.startPastedLink(SourceConnectionFlow.initial, "  https://example.org/feed.xml  ")
        assertEquals(ConnectionStep.Probing("https://example.org/feed.xml"), state.step)
        assertEquals("https://example.org/feed.xml", state.checkedAddress)
        assertNull(state.method)
        val detected = SourceConnectionFlow.detected(state, ProbeResult.Recognised(ConnectionKind.RSS, title = "A feed"))
        assertEquals(ConnectionMethod.RSS, detected.method)
        assertEquals(ConnectionStep.Preview(ProbeResult.Recognised(ConnectionKind.RSS, title = "A feed")), detected.step)
    }

    @Test
    fun `back from a pasted link returns to the first screen, not an empty method list`() {
        val pasted = SourceConnectionFlow.startPastedLink(SourceConnectionFlow.initial, "https://example.org/opds")
        val address = SourceConnectionFlow.back(pasted)
        assertTrue(address.step is ConnectionStep.Address)
        assertEquals(ConnectionStep.Content, SourceConnectionFlow.back(address).step)
    }

    @Test
    fun `a pasted blank link fails at once`() {
        val state = SourceConnectionFlow.startPastedLink(SourceConnectionFlow.initial, "   ")
        val failure = state.step as ConnectionStep.Failed
        assertEquals("empty", ((failure.reason as ConnectionFailure.Check).result as ProbeResult.InvalidUrl).reason)
    }

    @Test
    fun `correcting a pasted link after a 404 can discover a podcast`() {
        val pasted = SourceConnectionFlow.startPastedLink(SourceConnectionFlow.initial, "https://example.org/missing")
        val failed = SourceConnectionFlow.detected(pasted, ProbeResult.HttpFailure(404))
        val address = SourceConnectionFlow.back(failed)
        assertEquals(ConnectionStep.Address(null), address.step)
        val probing = SourceConnectionFlow.startCheck(SourceConnectionFlow.editAddress(address, address = "https://example.org/podcast"))
        val preview = SourceConnectionFlow.detected(probing, ProbeResult.Recognised(ConnectionKind.RSS))
        assertEquals(ConnectionMethod.RSS, preview.method)
        assertEquals(ConnectionStep.Saving, SourceConnectionFlow.startSave(preview)?.step)
    }

    @Test
    fun `a web page is a set of candidates, not a saveable connection`() {
        val page = ProbeResult.Recognised(
            ConnectionKind.WEB_PAGE,
            title = "A library",
            candidates = listOf(app.yomu.source.LinkCandidate("https://example.org/feed", ConnectionKind.RSS, "News")),
        )
        val state = SourceConnectionFlow.detected(SourceConnectionFlow.startPastedLink(SourceConnectionFlow.initial, "https://example.org/"), page)
        assertNull(state.method)
        assertNull(SourceConnectionFlow.startSave(state))
    }

    @Test
    fun `a blank title from the document does not become a name`() {
        val state = SourceConnectionFlow.checked(SourceConnectionFlow.startCheck(atAddress()), ProbeResult.Recognised(ConnectionKind.OPDS, title = "   "))
        assertEquals("example.org", SourceConnectionFlow.suggestedName(state))
    }
}
