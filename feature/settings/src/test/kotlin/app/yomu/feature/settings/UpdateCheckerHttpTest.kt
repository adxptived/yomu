package app.yomu.feature.settings

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class UpdateCheckerHttpTest {
    @Test
    fun `forbidden and rate-limited API responses fall back to the public release`() {
        for (status in listOf(403, 429)) {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(status).build())
                server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "${ProjectLinks.RELEASES_URL}/tag/v2.18.0").build())
                val outcome = runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }
                val release = requireNotNull(outcome.release)
                assertEquals("2.18.0", release.version)
                assertEquals("${ProjectLinks.RELEASES_URL}/tag/v2.18.0", release.pageUrl)
                assertNull(release.apkUrl)
                assertTrue(release.metadataUnavailable)
                assertEquals(ApkAvailability.METADATA_UNAVAILABLE, release.apkAvailability)
            }
        }
    }

    @Test
    fun `a redirect outside Yomu releases preserves the update check error`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(403).build())
            server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "https://github.com/example/other/releases/tag/v2.18.0").build())
            assertThrows(UpdateCheckAccessException::class.java) {
                runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }
            }
        }
    }

    @Test
    fun `a redirect to a tag that is not a version is not trusted`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(403).build())
            server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "${ProjectLinks.RELEASES_URL}/tag/v-next").build())
            assertThrows(UpdateCheckAccessException::class.java) {
                runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }
            }
        }
    }

    @Test
    fun `an API response without a release stays empty`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(404).build())
            assertNull(runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }.release)
        }
    }

    @Test
    fun `a server error is a check failure, never an empty answer`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(500).build())
            assertThrows(UpdateCheckAccessException::class.java) {
                runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }
            }
        }
    }

    @Test
    fun `a timeout is an IOException within the deadline`() {
        // A non-routable address: the connection attempt runs into the connect timeout instead of an answer.
        val started = System.currentTimeMillis()
        assertThrows(IOException::class.java) {
            runBlocking { UpdateChecker.check("http://10.255.255.1/api", "http://10.255.255.1/latest", listOf("arm64-v8a"), timeoutMs = 400) }
        }
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test
    fun `rate limit headers become the retry hint`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(403).setHeader("Retry-After", "7").build())
            server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "${ProjectLinks.RELEASES_URL}/tag/v2.18.0").build())
            val outcome = runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("arm64-v8a")) }
            assertEquals(7_000L, outcome.retryAfterMs)
        }
    }

    @Test
    fun `a release object is read with the APK of this device`() {
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(200).body(releaseJson()).build())
            val release = requireNotNull(
                runBlocking { UpdateChecker.check(server.url("/api").toString(), server.url("/latest").toString(), listOf("x86_64", "arm64-v8a")) }.release,
            )
            assertEquals("2.18.0", release.version)
            assertTrue(release.apkUrl!!.endsWith("yomu-v2.18.0-x86_64.apk"))
            assertEquals(1234L, release.apkSize)
            assertTrue(release.checksumsUrl!!.endsWith("SHA256SUMS.txt"))
            assertEquals(ApkAvailability.READY, release.apkAvailability)
        }
    }

    @Test
    fun `a universal APK stands in when there is no build for the device`() {
        val release = UpdateChecker.parse(releaseJson(), listOf("mips"))
        assertTrue(release.apkUrl!!.endsWith("yomu-v2.18.0-universal.apk"))
    }

    @Test
    fun `a release without any build for the device says so`() {
        val release = UpdateChecker.parse(releaseJson(universal = false), listOf("mips"))
        assertNull(release.apkUrl)
        assertEquals(ApkAvailability.NO_APK_FOR_DEVICE, release.apkAvailability)
    }

    @Test
    fun `a single APK for another architecture is not offered`() {
        val release = UpdateChecker.parse(
            """{"tag_name":"v2.18.0","assets":[{"name":"yomu-v2.18.0-arm64-v8a.apk","browser_download_url":"https://example.org/yomu-v2.18.0-arm64-v8a.apk"}]}""",
            listOf("x86_64"),
        )
        assertNull(release.apkUrl)
        assertEquals(ApkAvailability.NO_APK_FOR_DEVICE, release.apkAvailability)
    }

    @Test
    fun `a broken tag, a draft and a prerelease are refused`() {
        assertThrows(UpdateReleaseFormatException::class.java) { UpdateChecker.parse(releaseJson(tag = "nightly")) }
        assertThrows(UpdateReleaseFormatException::class.java) { UpdateChecker.parse(releaseJson(draft = true)) }
        assertThrows(UpdateReleaseFormatException::class.java) { UpdateChecker.parse(releaseJson(prerelease = true)) }
    }

    @Test
    fun `stable and prerelease versions of the same number compare in one direction`() {
        assertTrue(UpdateChecker.isNewer("1.7.0", "1.7.0-beta.2"))
        assertTrue(!UpdateChecker.isNewer("1.7.0-beta.2", "1.7.0"))
        assertTrue(!UpdateChecker.isNewer("1.7.0-debug", "1.7.0"))
        assertTrue(!UpdateChecker.isNewer("1.7.0", "1.7.0-debug"))
        assertTrue(UpdateChecker.isNewer("1.8.0", "1.7.9"))
        assertTrue(!UpdateChecker.isNewer("1.6.9", "1.7.0"))
        assertTrue(!UpdateChecker.isNewer("1.7.0", "1.7.0"))
    }

    private fun releaseJson(
        tag: String = "v2.18.0",
        universal: Boolean = true,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String = buildString {
        append("""{"tag_name":"$tag","html_url":"https://github.com/adxptived/yomu/releases/tag/$tag",""")
        append(""""body":"## 2.18.0\n\n- A fix","draft":$draft,"prerelease":$prerelease,"assets":[""")
        append("""{"name":"yomu-v2.18.0-x86_64.apk","browser_download_url":"https://example.com/yomu-v2.18.0-x86_64.apk","size":1234},""")
        append("""{"name":"yomu-v2.18.0-arm64-v8a.apk","browser_download_url":"https://example.com/yomu-v2.18.0-arm64-v8a.apk","size":1234}""")
        if (universal) append(""",{"name":"yomu-v2.18.0-universal.apk","browser_download_url":"https://example.com/yomu-v2.18.0-universal.apk","size":2345}""")
        append(""",{"name":"SHA256SUMS.txt","browser_download_url":"https://example.com/SHA256SUMS.txt","size":300}""")
        append("]}")
    }

    private fun withServer(test: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            test(server)
        }
    }
}
