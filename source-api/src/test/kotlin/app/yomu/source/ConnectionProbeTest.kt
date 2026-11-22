package app.yomu.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The connection probe decides what a user actually connected: the content type alone is not trusted, and a failure
 * names its cause instead of reporting one generic error.
 */
class ConnectionProbeTest {
    private val xml = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"/>"""
    private val rss = """<?xml version="1.0"?><rss version="2.0"/>"""
    private val html = "<!DOCTYPE html><html><body>Not a catalogue</body></html>"

    @Test
    fun `an opds feed is recognised by its declared type`() {
        assertEquals(ConnectionKind.OPDS, ConnectionProbe.classify("application/atom+xml", xml))
        assertEquals(ConnectionKind.OPDS, ConnectionProbe.classify("application/opds+xml", xml))
    }

    @Test
    fun `opds served as generic xml is recognised by its root`() {
        val body = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog"/>"""
        assertEquals(ConnectionKind.OPDS, ConnectionProbe.classify("application/xml", body))
    }

    @Test
    fun `an rss feed is told apart from an opds feed`() {
        assertEquals(ConnectionKind.RSS, ConnectionProbe.classify("application/rss+xml", rss))
        assertEquals(ConnectionKind.RSS, ConnectionProbe.classify("application/xml", rss))
    }

    @Test
    fun `an html error page served as xml is a web page not a catalogue`() {
        assertEquals(ConnectionKind.WEB_PAGE, ConnectionProbe.classify("application/xml", html))
        assertEquals(ConnectionKind.WEB_PAGE, ConnectionProbe.classify("text/html", html))
    }

    @Test
    fun `an audio address is recognised`() {
        assertEquals(ConnectionKind.AUDIO, ConnectionProbe.classify("audio/mpeg", ""))
        assertEquals(ConnectionKind.AUDIO, ConnectionProbe.classify("audio/mp4", ""))
    }

    @Test
    fun `an unrecognised document is unknown`() {
        assertEquals(ConnectionKind.UNKNOWN, ConnectionProbe.classify("application/octet-stream", "binary"))
    }

    @Test
    fun `a valid opds response is accepted`() {
        val result = ConnectionProbe.verdict(200, "application/atom+xml", xml, setOf(ConnectionKind.OPDS))
        assertTrue(result is ProbeResult.Recognised)
        assertEquals(ConnectionKind.OPDS, (result as ProbeResult.Recognised).kind)
    }

    @Test
    fun `an html page where opds was expected is unsupported`() {
        val result = ConnectionProbe.verdict(200, "text/html", html, setOf(ConnectionKind.OPDS))
        assertEquals(ProbeResult.Unsupported(ConnectionKind.WEB_PAGE), result)
    }

    @Test
    fun `a redirect to a web page is not accepted`() {
        val result = ConnectionProbe.verdict(200, "text/html", html, setOf(ConnectionKind.OPDS, ConnectionKind.WEB_PAGE), followedRedirects = true)
        assertEquals(ProbeResult.Unsupported(ConnectionKind.WEB_PAGE), result)
    }

    @Test
    fun `a login requirement and a browser check are different answers`() {
        assertEquals(ProbeResult.NeedsAuth, ConnectionProbe.verdict(401, null, "", setOf(ConnectionKind.OPDS)))
        assertEquals(ProbeResult.NeedsBrowser, ConnectionProbe.verdict(403, null, "", setOf(ConnectionKind.OPDS), browserChallenge = true))
    }

    @Test
    fun `http errors retain their status without being treated as browser checks`() {
        for (status in listOf(403, 404, 408, 429, 503)) {
            assertEquals(ProbeResult.HttpFailure(status), ConnectionProbe.verdict(status, null, "", setOf(ConnectionKind.OPDS)))
        }
    }

    @Test
    fun `an address without a scheme or host is rejected before any request`() {
        assertTrue(ConnectionProbe.validateUrl("") is ProbeResult.InvalidUrl)
        assertTrue(ConnectionProbe.validateUrl("example.com/catalog") is ProbeResult.InvalidUrl)
        assertTrue(ConnectionProbe.validateUrl("file:///etc/passwd") is ProbeResult.InvalidUrl)
        assertTrue(ConnectionProbe.validateUrl("javascript:alert(1)") is ProbeResult.InvalidUrl)
        assertTrue(ConnectionProbe.validateUrl("https:///catalog") is ProbeResult.InvalidUrl)
        assertNull(ConnectionProbe.validateUrl("https://example.com/opds"))
        assertNull(ConnectionProbe.validateUrl("http://192.168.1.10:8080/opds"))
    }

    @Test
    fun `a private lan address is a normal address`() {
        assertNull(ConnectionProbe.validateUrl("http://10.0.0.5/catalog"))
        assertNull(ConnectionProbe.validateUrl("http://localhost:8080/opds"))
    }

    @Test
    fun `inferred capabilities keep the tabs a source declares`() {
        val source = object : CatalogSource {
            override val id = 1L
            override val name = "Test"
            override val lang = "en"
            override val content = SourceContent.MANGA
            override val supportsLatest = false
            override val supportsSearch = true
            override suspend fun getPopular(page: Int) = ItemsPage(emptyList(), false)
            override suspend fun getLatest(page: Int) = ItemsPage(emptyList(), false)
            override suspend fun search(query: String, filters: FilterList, page: Int) = ItemsPage(emptyList(), false)
        }
        val caps = SourceCapabilities.infer(source)
        assertTrue(caps.popular)
        assertFalse(caps.latest)
        assertTrue(caps.search)
        assertEquals(listOf(SourceTab.POPULAR, SourceTab.SEARCH), caps.browsingTabs)
        assertTrue(caps.isBrowsable)
    }

    @Test
    fun `a book source is inferred as file based without streaming`() {
        val source = object : Source {
            override val id = 2L
            override val name = "Books"
            override val lang = "en"
            override val content = SourceContent.BOOK
            override val supportsLatest = true
        }
        val caps = SourceCapabilities.infer(source)
        assertTrue(caps.files)
        assertFalse(caps.streaming)
        assertFalse(caps.subscriptions)
    }

    @Test
    fun `an explicit capability report overrides the inference`() {
        val source = object : CatalogSource, CapabilitySource {
            override val id = 3L
            override val name = "Locked"
            override val lang = "en"
            override val content = SourceContent.NOVEL
            override val supportsLatest = true
            override val capabilities = SourceCapabilities(search = false, login = true, textContent = true)
            override suspend fun getPopular(page: Int) = ItemsPage(emptyList(), false)
            override suspend fun getLatest(page: Int) = ItemsPage(emptyList(), false)
            override suspend fun search(query: String, filters: FilterList, page: Int) = ItemsPage(emptyList(), false)
        }
        assertEquals(source.capabilities, (source as CapabilitySource).capabilities)
        assertFalse(source.capabilities.isSupported(SourceTab.SEARCH))
        assertNotNull(source.capabilities.unsupportedReason(SourceTab.SEARCH))
    }

    @Test
    fun `a source that can browse nothing says so`() {
        val caps = SourceCapabilities(search = false, latest = false, popular = false, navigation = false)
        assertFalse(caps.isBrowsable)
        assertTrue(caps.browsingTabs.isEmpty())
    }
}
