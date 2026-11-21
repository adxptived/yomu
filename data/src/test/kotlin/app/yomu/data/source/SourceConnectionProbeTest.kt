package app.yomu.data.source

import app.yomu.source.ConnectionKind
import app.yomu.source.ProbeResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The request behind the connection probe. The decision table itself is covered in `ConnectionProbeTest` (source-api);
 * here each test proves the same table is fed the real response: the declared type, the deadline, the redirects and
 * the bounded body.
 */
class SourceConnectionProbeTest {
    private lateinit var server: MockWebServer
    private val probe = SourceConnectionProbe { OkHttpClient() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun url(path: String = "/opds"): String = server.url(path).toString()

    private fun answer(body: String, type: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", type).setBody(body))
    }

    private val opds = """<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>My catalogue</title>
          <entry><title>One</title></entry>
          <entry><title>Two</title></entry>
        </feed>"""

    @Test
    fun `a valid catalogue is recognised with its own title and a real sample`() = runBlocking {
        answer(opds, "application/atom+xml")
        val result = probe.probe(url()) as ProbeResult.Recognised
        assertEquals(ConnectionKind.OPDS, result.kind)
        assertEquals("My catalogue", result.title)
        assertEquals(2, result.sampleItems)
    }

    @Test
    fun `an HTML error page served as a catalogue feed is not a catalogue`() = runBlocking {
        answer("<html><body>Not here</body></html>", "application/atom+xml")
        val result = probe.probe(url())
        assertTrue("an error page was accepted: $result", result is ProbeResult.Unsupported)
        assertEquals(ConnectionKind.WEB_PAGE, (result as ProbeResult.Unsupported).detected)
    }

    @Test
    fun `a declared HTML page is unsupported`() = runBlocking {
        answer("<!doctype html><html><body>Shop</body></html>", "text/html")
        assertEquals(ConnectionKind.WEB_PAGE, (probe.probe(url()) as ProbeResult.Unsupported).detected)
    }

    @Test
    fun `a web page offers the feeds it declares when a page is supported`() = runBlocking {
        answer(
            """<!doctype html><html><head>
                <link rel="alternate" type="application/rss+xml" title="News" href="/feed.xml">
                <link rel="alternate" type="application/atom+xml" href="catalogue.opds">
                <link rel="stylesheet" href="/style.css">
            </head><body>A library</body></html>""",
            "text/html",
        )
        val result = probe.probe(url(), supported = setOf(ConnectionKind.WEB_PAGE, ConnectionKind.OPDS, ConnectionKind.RSS)) as ProbeResult.Recognised
        assertEquals(ConnectionKind.WEB_PAGE, result.kind)
        assertEquals(listOf(ConnectionKind.RSS, ConnectionKind.OPDS), result.candidates.map { it.kind })
        assertEquals("News", result.candidates.first().title)
        assertTrue(result.candidates.first().url.endsWith("/feed.xml"))
        assertTrue(result.candidates.last().url.endsWith("/catalogue.opds"))
    }

    @Test
    fun `a page without declared feeds has no candidates`() = runBlocking {
        answer("<!doctype html><html><body><a href=\"/feed.xml\">RSS</a></body></html>", "text/html")
        val result = probe.probe(url(), supported = setOf(ConnectionKind.WEB_PAGE)) as ProbeResult.Recognised
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `401 asks for a login and 403 is a browser check`() = runBlocking {
        answer("", "text/plain", 401)
        assertEquals(ProbeResult.NeedsAuth, probe.probe(url()))
        answer("", "text/plain", 403)
        assertEquals(ProbeResult.NeedsBrowser, probe.probe(url()))
    }

    @Test
    fun `credentials are sent only when a user is given`() = runBlocking {
        answer(opds, "application/atom+xml")
        probe.probe(url(), username = "reader", password = "secret")
        val auth = server.takeRequest().getHeader("Authorization")
        assertTrue("Basic credentials are missing: $auth", auth?.startsWith("Basic ") == true)
        answer(opds, "application/atom+xml")
        probe.probe(url())
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a redirect to another host receives no credentials`() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/feed").toString()))
            other.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/atom+xml").setBody(opds))
            val result = probe.probe(url(), username = "reader", password = "secret")
            assertTrue("cross-host redirect was rejected: $result", result is ProbeResult.Recognised)
            assertTrue(server.takeRequest().getHeader("Authorization")?.startsWith("Basic ") == true)
            assertEquals(null, other.takeRequest().getHeader("Authorization"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `a redirect to a catalogue is followed and recognised`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/real").toString()))
        answer(opds, "application/atom+xml")
        val result = probe.probe(url())
        assertTrue("a redirect to a catalogue was rejected: $result", result is ProbeResult.Recognised)
        // The typed address is asked first, then the address it points at.
        assertEquals("/opds", server.takeRequest().path)
        assertEquals("/real", server.takeRequest().path)
    }

    @Test
    fun `a redirect to a web page is not a catalogue`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/site").toString()))
        answer("<!doctype html><html><body>Site</body></html>", "text/html")
        val result = probe.probe(url())
        assertTrue("a redirect to a page was accepted: $result", result is ProbeResult.Unsupported)
    }

    @Test
    fun `an endless redirect chain stops`() = runBlocking {
        repeat(10) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/again").toString())) }
        assertEquals(ProbeResult.NetworkError, probe.probe(url()))
    }

    @Test
    fun `a huge document is inspected only to the probe window`() = runBlocking {
        val entries = buildString {
            append("<?xml version=\"1.0\"?><feed><title>Big</title>")
            repeat(60_000) { append("<entry><title>$it</title></entry>") }
            append("</feed>")
        }
        assertTrue(entries.length > 1_000_000)
        answer(entries, "application/atom+xml")
        val result = probe.probe(url()) as ProbeResult.Recognised
        // Only the first 64 KiB are parsed: the wizard shows a sample, not the whole document.
        assertTrue("the whole document was read: ${result.sampleItems}", result.sampleItems in 1..2000)
    }

    @Test
    fun `an unusable address is rejected before any request`() = runBlocking {
        assertTrue(probe.probe("file:///etc/hosts") is ProbeResult.InvalidUrl)
        assertTrue(probe.probe("not an address") is ProbeResult.InvalidUrl)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unreachable server is a network error, not a broken catalogue`() = runBlocking {
        val address = url()
        server.shutdown()
        assertEquals(ProbeResult.NetworkError, probe.probe(address))
    }

    @Test
    fun `the first title is read from the probe window only`() {
        assertEquals("Catalogue", probe.leadingTitle("<feed><title>Catalogue</title></feed>"))
        assertEquals(null, probe.leadingTitle("<feed></feed>"))
        assertEquals("Catalogue", probe.leadingTitle("<feed><title>  Catalogue  </title>"))
    }

    @Test
    fun `sample counts only the entries in the window`() {
        assertEquals(2, probe.sampleItems("<feed><entry/><entry></entry></feed>", ConnectionKind.OPDS))
        assertEquals(2, probe.sampleItems("<rss><item/><item></item></rss>", ConnectionKind.RSS))
        assertEquals(0, probe.sampleItems("<feed><entry/></feed>", ConnectionKind.AUDIO))
    }
}
