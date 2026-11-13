package app.yomu.data.audio

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import timber.log.Timber
import java.security.MessageDigest

/**
 * The YouTube session of the app's WebView. Signing in on YouTube's own page stores the session cookies in
 * [CookieManager]; the shared OkHttp client reads the same cookies (see `WebViewCookieJar`), so the player and the
 * downloader see them too. InnerTube accepts those cookies only with a `SAPISIDHASH` signature, built here.
 */
object YouTubeAuth {
    /** Google's sign-in page, returning to YouTube when it is done. */
    const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"

    private const val ORIGIN = "https://www.youtube.com"
    private const val ACCOUNTS = "https://accounts.google.com"

    /** The cookies a SAPISIDHASH can be built from; a bare `SID` is not enough for the player. */
    private val SESSION_COOKIES = listOf("SAPISID", "__Secure-3PAPISID")

    /** Assigned by the app after a response; sent with the next calls and keeps the bot check away. */
    @Volatile
    var visitorData: String? = null
        private set

    private const val PREFS = "youtube_auth"
    private const val VISITOR = "visitor_data"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Remembers YouTube's visitor id across restarts: the bot check is less likely to come back. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        if (visitorData == null) visitorData = p.getString(VISITOR, null)
    }

    fun rememberVisitor(value: String?) {
        if (value.isNullOrBlank() || value == visitorData) return
        visitorData = value
        prefs?.edit()?.putString(VISITOR, value)?.apply()
    }

    /** The cookies of the WebView session, `name=value; …`, or an empty string. */
    fun cookies(): String = runCatching { CookieManager.getInstance().getCookie(ORIGIN) }.getOrNull().orEmpty()

    fun cookie(name: String): String? = cookie(cookies(), name)

    /** Whether the WebView holds a Google session that YouTube will accept. */
    fun isSignedIn(): Boolean = isSignedIn(cookies())

    /**
     * `Authorization: SAPISIDHASH …` for InnerTube: SHA-1 over the timestamp, the SAPISID cookie and the origin.
     * Without a session, or without a WebView at all, returns null and requests stay anonymous.
     */
    fun authorization(): String? = authorization(cookies())

    /** Headers of an authenticated call: ignored by YouTube when there is no session. */
    fun headers(): Map<String, String> = headers(authorization(), visitorData)

    fun isSignedIn(raw: String): Boolean = SESSION_COOKIES.any { cookie(raw, it) != null }

    fun cookie(raw: String, name: String): String? = raw.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')?.takeIf { it.isNotBlank() && it != "deleted" }

    fun authorization(raw: String, timestamp: Long = System.currentTimeMillis() / 1000): String? {
        val sapisid = cookie(raw, "SAPISID") ?: cookie(raw, "__Secure-3PAPISID") ?: return null
        return "SAPISIDHASH ${timestamp}_${sha1Hex("$timestamp $sapisid $ORIGIN")}"
    }

    fun headers(authorization: String?, visitorData: String?): Map<String, String> = buildMap {
        authorization?.let {
            put("Authorization", it)
            put("X-Origin", ORIGIN)
            put("X-Goog-AuthUser", "0")
        }
        visitorData?.let { put("X-Goog-Visitor-Id", it) }
    }

    /** Forgets the YouTube and Google cookies held by the WebView; other sites keep theirs. */
    fun signOut() {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        listOf(ORIGIN, "https://m.youtube.com", ACCOUNTS, "https://www.google.com", "https://accounts.youtube.com").forEach { origin ->
            val raw = runCatching { manager.getCookie(origin) }.getOrNull().orEmpty()
            val domain = origin.substringAfter("://").removePrefix("www.")
            raw.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
                val name = pair.substringBefore('=')
                if (name.isBlank()) return@forEach
                val attributes = buildString {
                    append("Path=/; Max-Age=0")
                    if (name.startsWith("__Secure-") || name.startsWith("__Host-")) append("; Secure")
                }
                // Both the host-only cookie and the domain cookie Google usually writes.
                manager.setCookie(origin, "$name=; $attributes")
                manager.setCookie(origin, "$name=; $attributes; Domain=.$domain")
            }
        }
        visitorData = null
        prefs?.edit()?.remove(VISITOR)?.apply()
        runCatching { manager.flush() }.onFailure { Timber.w(it, "YouTube cookies not flushed") }
    }

    private fun sha1Hex(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
