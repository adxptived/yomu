package app.yomu.data.download

import app.yomu.data.catalog.NotAnImageException
import app.yomu.source.CatalogSource
import app.yomu.source.FilterList
import app.yomu.source.ItemsPage
import app.yomu.source.Page
import app.yomu.source.SourceContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.Base64

/** Range resume of chapter page images against a fake image host (the same fail-closed rules as audio downloads). */
class ResumablePageFetcherTest {
    private val client = OkHttpClient()
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    @Volatile private var answer: (RecordedRequest) -> MockResponse = { MockResponse.Builder().code(404).build() }

    @Before
    fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("resumable-page").toFile()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return answer(request)
                }
            }
            start()
        }
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
        dir.deleteRecursively()
    }

    @Test
    fun `resumes a truncated page when a 206 continues at our byte with the same validator`() = runBlocking {
        val full = png()
        val half = full.size / 2
        val base = "000.img"
        seed(base, full.copyOf(half), length = full.size.toLong())
        answer = { req ->
            val ranged = req.headers["Range"] != null
            MockResponse.Builder()
                .code(if (ranged) 206 else 200)
                .setHeader("Content-Type", "image/png")
                .setHeader("ETag", "\"v1\"")
                .apply { if (ranged) setHeader("Content-Range", "bytes $half-${full.size - 1}/${full.size}") }
                .body(Buffer().write(if (ranged) full.copyOfRange(half, full.size) else full))
                .build()
        }

        val out = File(dir, base)
        assertEquals("png", fetch(out))
        assertArrayEquals(full, out.readBytes())
        assertFalse(File(dir, "$base.part").exists())
        assertFalse(File(dir, "$base.part.meta").exists())
        assertEquals(1, requests.size)
        assertEquals("bytes=$half-", requests[0].headers["Range"])
        assertEquals("\"v1\"", requests[0].headers["If-Range"])
    }

    @Test
    fun `a 200 to a ranged request restarts from zero instead of appending`() = runBlocking {
        val full = png()
        val half = full.size / 2
        val base = "001.img"
        seed(base, full.copyOf(half), length = full.size.toLong())
        answer = { _ ->
            MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").setHeader("ETag", "\"v1\"")
                .body(Buffer().write(full)).build()
        }

        val out = File(dir, base)
        assertEquals("png", fetch(out))
        assertArrayEquals(full, out.readBytes())
        assertEquals("bytes=$half-", requests.single().headers["Range"])
    }

    @Test
    fun `a 206 with a changed ETag restarts and never appends the new version`() = runBlocking {
        val full = png()
        val half = full.size / 2
        val base = "002.img"
        seed(base, full.copyOf(half), validator = "\"v1\"", length = full.size.toLong())
        answer = { req ->
            if (req.headers["Range"] != null) {
                MockResponse.Builder().code(206).setHeader("Content-Type", "image/png").setHeader("ETag", "\"v2\"")
                    .setHeader("Content-Range", "bytes $half-${full.size - 1}/${full.size}")
                    .body(Buffer().write(full.copyOfRange(half, full.size))).build()
            } else {
                MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").setHeader("ETag", "\"v2\"")
                    .body(Buffer().write(full)).build()
            }
        }

        val out = File(dir, base)
        assertEquals("png", fetch(out))
        assertArrayEquals(full, out.readBytes())
        assertEquals(2, requests.size)
        assertNotNull(requests[0].headers["Range"])
        assertNull("the restart must not claim a Range", requests[1].headers["Range"])
    }

    @Test
    fun `a 206 from another position restarts and reports no resume`() = runBlocking {
        val full = png()
        val half = full.size / 2
        val base = "003.img"
        seed(base, full.copyOf(half), length = full.size.toLong())
        answer = { req ->
            if (req.headers["Range"] != null) {
                // Wrong start: the bytes on disk cannot be continued with this response.
                MockResponse.Builder().code(206).setHeader("Content-Type", "image/png").setHeader("ETag", "\"v1\"")
                    .setHeader("Content-Range", "bytes 0-${full.size - 1}/${full.size}")
                    .body(Buffer().write(full)).build()
            } else {
                MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").setHeader("ETag", "\"v1\"")
                    .body(Buffer().write(full)).build()
            }
        }

        val out = File(dir, base)
        assertEquals("png", fetch(out))
        assertArrayEquals(full, out.readBytes())
        assertEquals(2, requests.size)
    }

    @Test
    fun `cancelling a page drops its part but keeps completed pages`() = runBlocking {
        val base = "004.img"
        seed(base, ByteArray(32), length = 128)
        val complete = File(dir, "000.png").apply { writeBytes(png()) }
        val started = CompletableDeferred<Unit>()
        val source = source { started.complete(Unit); delay(Long.MAX_VALUE) }
        val out = File(dir, base)

        val job = launch(Dispatchers.IO) { ResumablePageFetcher.fetch(null, client, source, page(), out, attempts = 1) }
        started.await()
        job.cancel()
        job.join()

        assertFalse(File(dir, "$base.part").exists())
        assertFalse(File(dir, "$base.part.meta").exists())
        assertFalse(out.exists())
        assertTrue(complete.exists())
    }

    @Test
    fun `no space drops the part and keeps completed pages`() = runBlocking {
        val base = "005.img"
        seed(base, ByteArray(32), length = 128)
        val complete = File(dir, "000.png").apply { writeBytes(png()) }
        val source = source { throw IOException("write failed: ENOSPC (No space left on device)") }
        val out = File(dir, base)

        var thrown: Throwable? = null
        try {
            ResumablePageFetcher.fetch(null, client, source, page(), out, attempts = 1)
        } catch (e: Throwable) {
            thrown = e
        }

        assertNotNull(thrown)
        assertFalse(File(dir, "$base.part").exists())
        assertFalse(File(dir, "$base.part.meta").exists())
        assertFalse(out.exists())
        assertTrue(complete.exists())
    }

    @Test
    fun `an error page is never published to the final path`() = runBlocking {
        val base = "006.img"
        val out = File(dir, base)
        answer = { _ ->
            MockResponse.Builder().code(200).setHeader("Content-Type", "text/html")
                .body(Buffer().write("<html>checking your browser</html>".toByteArray())).build()
        }

        var thrown: Throwable? = null
        try {
            fetch(out)
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue("expected NotAnImageException, got $thrown", thrown is NotAnImageException)
        assertFalse(out.exists())
        assertFalse(File(dir, "$base.part").exists())
    }

    @Test
    fun `the validated image replaces a stale final file atomically`() = runBlocking {
        val full = png()
        val base = "007.img"
        val out = File(dir, base).apply { writeBytes(ByteArray(9)) }
        answer = { _ -> MockResponse.Builder().code(200).setHeader("Content-Type", "image/png").body(Buffer().write(full)).build() }

        assertEquals("png", fetch(out))
        assertArrayEquals(full, out.readBytes())
        assertFalse(File(dir, "$base.part").exists())
        assertFalse(File(dir, "$base.part.meta").exists())
    }

    @Test
    fun `a truncated response is rejected and leaves no partial page`() = runBlocking {
        val full = png()
        val base = "008.img"
        answer = { _ ->
            MockResponse.Builder().code(206).setHeader("Content-Type", "image/png")
                .setHeader("Content-Range", "bytes 0-${full.size - 1}/${full.size}")
                .body(Buffer().write(full.copyOf(full.size / 2))).build()
        }
        val out = File(dir, base)

        var thrown: Throwable? = null
        try {
            fetch(out)
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue("expected IOException, got $thrown", thrown is IOException)
        assertFalse(out.exists())
        assertFalse(File(dir, "$base.part").exists())
        assertFalse(File(dir, "$base.part.meta").exists())
    }

    private suspend fun fetch(out: File, attempts: Int = 1) =
        ResumablePageFetcher.fetch(null, client, source(), page(), out, attempts)

    private fun source(onFetchImage: suspend () -> Unit = {}) = object : CatalogSource {
        override val id = 1L
        override val name = "Fake"
        override val lang = "en"
        override val content = SourceContent.MANGA
        override val supportsLatest = false
        override suspend fun getPopular(page: Int) = ItemsPage(emptyList(), false)
        override suspend fun getLatest(page: Int) = ItemsPage(emptyList(), false)
        override suspend fun search(query: String, filters: FilterList, page: Int) = ItemsPage(emptyList(), false)
        override suspend fun fetchImage(page: Page, imageUrl: String): okhttp3.Response? {
            onFetchImage()
            return null
        }
    }

    private fun page() = Page(0, imageUrl = server.url("/img/page.png").toString())

    private fun seed(base: String, bytes: ByteArray, validator: String? = "\"v1\"", length: Long?) {
        File(dir, "$base.part").writeBytes(bytes)
        File(dir, "$base.part.meta").writeText(metaJson.encodeToString(Meta(validator, length, null)))
    }

    @Serializable
    private data class Meta(val validator: String? = null, val length: Long? = null, val mime: String? = null)

    private companion object {
        val metaJson = Json { encodeDefaults = false; explicitNulls = false; ignoreUnknownKeys = true }

        /** A 1x1 PNG. */
        fun png(): ByteArray = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
    }
}
