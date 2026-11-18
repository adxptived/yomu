package app.yomu.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** S06: the scope dialog says what will be removed before anything is. */
class CookieScopeTest {

    @Test
    fun cookiesAreGroupedByHostWithTheirCount() {
        val sites = CookieScope.sites(
            listOf(
                "example.org" to "a",
                "EXAMPLE.org" to "b",
                ".example.org" to "c",
                "mangadex.org" to "d",
                "" to "ignored",
            ),
        )
        assertEquals(listOf(CookieHostSite("example.org", 3), CookieHostSite("mangadex.org", 1)), sites)
    }

    @Test
    fun anEmptyJarIsAnEmptyList() {
        assertTrue(CookieScope.sites(emptyList()).isEmpty())
    }
}
