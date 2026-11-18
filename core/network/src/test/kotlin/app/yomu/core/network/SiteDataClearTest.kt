package app.yomu.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** S06: clearing one site removes its cookies on every path and leaves every other sign-in alone. */
class SiteDataClearTest {

    @Test
    fun `a site loses root and nested path cookies while the other site keeps its session`() {
        val store = FakeCookies()
        store.seed("a.example", "/", "session")
        store.seed("a.example", "/nested", "token")
        store.seed("b.example", "/", "session")
        val ledger = CookieLedger().apply {
            record("a.example", "/", "session")
            record("a.example", "/nested", "token")
            record("b.example", "/", "session")
        }

        val result = SiteDataClearer(store.store, ledger).clear("https://a.example/sign-in")

        assertEquals("a.example", result.host)
        assertTrue(result.found)
        assertEquals(0, store.cookies("a.example"))
        assertEquals(1, store.cookies("b.example"))
        assertTrue(ledger.keys("a.example").isEmpty())
        assertTrue(ledger.keys("b.example").isNotEmpty())
    }

    @Test
    fun `a cookie on a path the WebView only visited is removed too`() {
        val store = FakeCookies()
        store.seed("a.example", "/forum/", "auth")
        val ledger = CookieLedger().apply { recordUrl("https://a.example/forum/threads/1?page=2") }

        val result = SiteDataClearer(store.store, ledger).clear("a.example")

        assertTrue(result.found)
        assertEquals(0, store.cookies("a.example"))
    }

    @Test
    fun `a name the WebView holds at the root is cleared without a ledger entry`() {
        val store = FakeCookies()
        store.seed("cloudflare.example", "/", "cf_clearance")

        val result = SiteDataClearer(store.store, CookieLedger()).clear("cloudflare.example")

        assertTrue(result.found)
        assertEquals(0, store.cookies("cloudflare.example"))
    }

    @Test
    fun `clearing a www site also removes the parent domain cookie`() {
        val store = FakeCookies()
        store.seed("example.org", "/", "shared")
        val ledger = CookieLedger().apply { record(".example.org", "/", "shared") }

        SiteDataClearer(store.store, ledger).clear("www.example.org")

        assertEquals(0, store.cookies("example.org"))
    }

    @Test
    fun `a host that only shares a suffix is untouched`() {
        val store = FakeCookies()
        store.seed("a.example", "/", "one")
        store.seed("not-a.example", "/", "two")
        val ledger = CookieLedger().apply {
            record("a.example", "/", "one")
            record("not-a.example", "/", "two")
        }

        SiteDataClearer(store.store, ledger).clear("a.example")

        assertEquals(0, store.cookies("a.example"))
        assertEquals(1, store.cookies("not-a.example"))
    }

    @Test
    fun `a site without cookies reports that nothing was found`() {
        val result = SiteDataClearer(FakeCookies().store, CookieLedger()).clear("empty.example")

        assertEquals("empty.example", result.host)
        assertFalse(result.found)
        assertEquals(0, result.cookies)
    }

    @Test
    fun `the ledger survives a restart without storing values`() {
        val file = File.createTempFile("cookie-scope", ".tsv").apply { deleteOnExit() }
        CookieLedger(file).apply {
            record("example.org", "/", "session")
            recordUrl("https://example.org/a/b")
        }

        val restored = CookieLedger(file)
        assertEquals(setOf("session"), restored.keys("example.org").map { it.name }.toSet())
        assertTrue(restored.paths("example.org").containsAll(setOf("/", "/a", "/a/b")))
        assertFalse(file.readText().contains("session="))
    }

    @Test
    fun `user input is reduced to a bare host`() {
        assertEquals("www.example.org", normalizeHost("https://www.example.org:443/path?q=1"))
        assertEquals("example.org", normalizeHost("  EXAMPLE.org  "))
        assertEquals("", normalizeHost("https://"))
    }

    /** In-memory cookie store with browser path matching: a cookie is sent to its path and anything below it. */
    private class FakeCookies {
        private val entries = mutableSetOf<Triple<String, String, String>>()

        fun seed(host: String, path: String, name: String) {
            entries += Triple(hostKey(host), normalizePath(path), name)
        }

        fun cookies(host: String): Int = entries.count { it.first == hostKey(host) }

        val store = object : CookieStore {
            override fun names(host: String, path: String): List<String> =
                entries.filter { (h, p, _) -> h == hostKey(host) && pathMatches(path, p) }.map { it.third }

            override fun expire(host: String, path: String, name: String): Boolean {
                val h = hostKey(host)
                val removed = entries.removeAll { (entryHost, entryPath, entryName) ->
                    (entryHost == h || entryHost == ".$h") && entryPath == normalizePath(path) && entryName == name
                }
                return removed
            }

            override fun flush() = Unit
        }

        private fun pathMatches(request: String, cookie: String): Boolean =
            request == cookie || (request.startsWith(cookie) && (cookie.endsWith("/") || request.getOrNull(cookie.length) == '/'))
    }
}
