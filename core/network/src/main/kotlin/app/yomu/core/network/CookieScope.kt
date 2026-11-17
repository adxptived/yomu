package app.yomu.core.network

/**
 * Where a "clear site data" action applies. The old call sites cleared everything or a fixed list of Google cookies;
 * this is the model the UI and the clearing share.
 */
enum class ClearScope {
    /** Only [host]: the cookies that belong to it. */
    HOST,

    /** Every site the app opened: all cookies of the shared jar, plus the WebView form data. */
    ALL,
}

/** A host that has at least one cookie in the shared jar, with the number of cookies it holds. */
data class CookieHostSite(val host: String, val cookies: Int)

/**
 * Reads the shared cookie jar without running JavaScript: names and hosts only (never a value), so a settings screen
 * can say what "clear" means before anything is removed.
 */
object CookieScope {
    /** Splits the cookies by host; an unnamed host is grouped under the empty name and never shown. */
    fun sites(cookies: List<Pair<String, String>>): List<CookieHostSite> = cookies
        .filter { it.first.isNotBlank() }
        .groupingBy { it.first.lowercase().removePrefix(".") }
        .eachCount()
        .map { (host, count) -> CookieHostSite(host, count) }
        .sortedWith(compareByDescending<CookieHostSite> { it.cookies }.thenBy { it.host })

    /** What a clear of [scope] removes, in a sentence the dialog can show as-is. */
    fun description(scope: ClearScope, host: String? = null): String = when (scope) {
        ClearScope.HOST -> host?.takeIf { it.isNotBlank() }?.let { "cookies of $it" } ?: "cookies of one site"
        ClearScope.ALL -> "all cookies, WebView form data and sessions of every site"
    }
}
