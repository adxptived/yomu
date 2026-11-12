package app.yomu.data.tracking

import android.content.Context
import app.yomu.domain.tracking.TrackEntry
import app.yomu.domain.tracking.TrackSearchResult
import app.yomu.domain.tracking.TrackService
import app.yomu.source.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** OAuth client ids built into the app (gradle properties YOMU_*_CLIENT_ID, see docs/TRACKING.md); empty = not configured. */
data class TrackerKeys(
    val anilistClientId: String = "",
    val malClientId: String = "",
    val shikimoriClientId: String = "",
    val shikimoriClientSecret: String = "",
)

/** An error of a tracking service; [code] is the HTTP status (0 when there was no answer to read it from). */
class TrackerException(message: String, val unauthorized: Boolean = false, cause: Throwable? = null, val code: Int = 0) : IOException(message, cause) {
    /** The service refused a refresh token (or the app's keys): only signing in again helps. */
    val refused: Boolean get() = unauthorized || code in 400..403
}

/**
 * Accounts of the tracking services: tokens in private preferences of the app (never in backups or sync files) and the
 * client ids the user entered instead of the built-in ones.
 */
class TrackerStore(context: Context) {
    private val prefs = context.getSharedPreferences("trackers", Context.MODE_PRIVATE)
    private val secrets = TrackerSecrets()
    private val _version = MutableStateFlow(0)

    /** Changes on every sign-in, sign-out and client id edit. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(s: TrackService, name: String) = "${s.id}.$name"

    private fun readString(name: String): String? = prefs.getString(name, null)?.let { secrets.decode(it) }

    private fun writeString(editor: android.content.SharedPreferences.Editor, name: String, value: String?) {
        if (value == null) editor.remove(name) else editor.putString(name, secrets.encode(value) ?: value)
    }

    fun token(s: TrackService): String? = readString(key(s, "token"))?.takeIf { it.isNotBlank() }
    fun refreshToken(s: TrackService): String? = readString(key(s, "refresh"))?.takeIf { it.isNotBlank() }
    fun expiresAt(s: TrackService): Long = prefs.getLong(key(s, "expires"), 0)
    fun userId(s: TrackService): Long = prefs.getLong(key(s, "user_id"), 0)
    fun userName(s: TrackService): String? = readString(key(s, "user_name"))
    fun extra(s: TrackService, name: String): String? = readString(key(s, "x.$name"))

    fun saveTokens(s: TrackService, token: String, refresh: String?, expiresAt: Long) {
        prefs.edit().also {
            writeString(it, key(s, "token"), token)
            writeString(it, key(s, "refresh"), refresh)
        }.putLong(key(s, "expires"), expiresAt).apply()
        _version.value++
    }

    /** Persist a new account and its tokens together; observers never see a mixed account. */
    fun saveAccount(s: TrackService, token: String, refresh: String?, expiresAt: Long, id: Long, name: String?) {
        prefs.edit().also {
            writeString(it, key(s, "token"), token)
            writeString(it, key(s, "refresh"), refresh)
            writeString(it, key(s, "user_name"), name)
        }.putLong(key(s, "expires"), expiresAt).putLong(key(s, "user_id"), id).apply()
        _version.value++
    }

    fun saveUser(s: TrackService, id: Long, name: String?) {
        prefs.edit().also { writeString(it, key(s, "user_name"), name) }.putLong(key(s, "user_id"), id).apply()
        _version.value++
    }

    fun setExtra(s: TrackService, name: String, value: String?) {
        prefs.edit().also { writeString(it, key(s, "x.$name"), value) }.apply()
    }

    fun logout(s: TrackService) {
        prefs.edit().remove(key(s, "token")).remove(key(s, "refresh")).remove(key(s, "expires")).remove(key(s, "user_id")).remove(key(s, "user_name")).apply()
        _version.value++
    }

    fun clientIdOverride(s: TrackService): String = readString(key(s, "client_id")).orEmpty()
    fun clientSecretOverride(s: TrackService): String = readString(key(s, "client_secret")).orEmpty()

    fun setClient(s: TrackService, id: String, secret: String?) {
        prefs.edit().also {
            writeString(it, key(s, "client_id"), id.trim())
            if (secret != null) writeString(it, key(s, "client_secret"), secret.trim())
        }.apply()
        _version.value++
    }
}

/** One tracking service: OAuth sign-in in the browser, search, and the user's list entries. */
abstract class Tracker(
    val service: TrackService,
    protected val store: TrackerStore,
    private val keys: TrackerKeys,
    protected val client: () -> OkHttpClient,
) {
    protected val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** `yomu://<host>`: must be registered as the redirect URI of the OAuth application. */
    abstract val redirectHost: String
    val redirectUri: String get() = "yomu://$redirectHost"

    /** Shikimori also needs the application's secret. */
    open val needsSecret: Boolean = false
    open val passwordLogin: Boolean = false
    open suspend fun loginWithPassword(username: String, password: String) { throw TrackerException("Password sign-in is unsupported") }

    fun clientId(): String = store.clientIdOverride(service).ifBlank {
        when (service) {
            TrackService.ANILIST -> keys.anilistClientId
            TrackService.MYANIMELIST -> keys.malClientId
            TrackService.SHIKIMORI -> keys.shikimoriClientId
            TrackService.KITSU -> ""
            TrackService.MANGAUPDATES -> ""
        }
    }

    fun clientSecret(): String = store.clientSecretOverride(service).ifBlank { if (service == TrackService.SHIKIMORI) keys.shikimoriClientSecret else "" }

    /** A client id (and secret) is known, so signing in can work. */
    fun configured(): Boolean = passwordLogin || (clientId().isNotBlank() && (!needsSecret || clientSecret().isNotBlank()))

    fun loggedIn(): Boolean = store.token(service) != null

    fun userName(): String? = store.userName(service)

    /** Page to open in the browser; it comes back to [redirectUri]. [state] must be echoed by the service. */
    abstract fun authUrl(state: String): String

    /**
     * Starts a sign-in: generates a single-use [state] (and, for services that support it, a PKCE verifier) and
     * returns the consent page. A redirect is accepted only if it carries the state of a sign-in this app started.
     */
    fun startAuth(): String {
        val state = newToken()
        store.setExtra(service, "state", state)
        return authUrl(state)
    }

    /** Rejects a redirect that does not belong to a sign-in started from this app (custom-scheme URLs are public). */
    protected fun requireRedirectState(uri: String) {
        val expected = store.extra(service, "state")
        // Single use: a replayed redirect must not sign the user in again.
        store.setExtra(service, "state", null)
        val got = redirectParams(uri)["state"]
        if (expected == null || got == null ||
            !java.security.MessageDigest.isEqual(expected.toByteArray(), got.toByteArray())
        ) {
            throw TrackerException("${service.displayName}: sign-in was not started from Yomu or has expired")
        }
    }

    /** Stores and returns a fresh PKCE verifier for the next authorization request. */
    protected fun newPkceVerifier(): String = newToken().also { store.setExtra(service, "verifier", it) }

    /** PKCE S256 challenge of [verifier]. */
    protected fun codeChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Finishes signing in with the redirect the browser opened. */
    abstract suspend fun finishLogin(redirect: String)

    open fun logout() = store.logout(service)

    abstract suspend fun search(query: String): List<TrackSearchResult>

    /** The user's entry for the title, null if it is not in their list. */
    abstract suspend fun find(remoteId: Long): TrackEntry?

    /** Adds or updates the entry; returns it as the service stored it. */
    abstract suspend fun save(entry: TrackEntry): TrackEntry

    abstract suspend fun delete(entry: TrackEntry)

    protected fun token(): String = store.token(service) ?: throw TrackerException("${service.displayName}: not signed in", unauthorized = true)

    private val refreshLock = Mutex()

    /**
     * Runs a token refresh once for requests made at the same time: refresh tokens are single-use (Shikimori), so a second
     * refresh with the same one would fail. Whoever waited for the lock finds a token other than [staleToken] and skips it.
     */
    protected suspend fun refreshOnce(staleToken: String?, refresh: suspend () -> Unit) = refreshLock.withLock {
        if (staleToken != null && store.token(service) != staleToken) return@withLock
        refresh()
    }

    /** Runs the request; HTTP errors become [TrackerException] (401: [TrackerException.unauthorized]). */
    protected suspend fun execute(request: Request, allowNotFound: Boolean = false): String? {
        val response = client().newCall(request).await()
        response.use { r ->
            val body = r.body.string()
            return when {
                r.isSuccessful -> body
                allowNotFound && r.code == 404 -> null
                r.code == 401 -> throw TrackerException("${service.displayName}: sign in again", unauthorized = true, code = 401)
                else -> throw TrackerException("${service.displayName}: HTTP ${r.code} ${body.take(200)}", code = r.code)
            }
        }
    }

    companion object {
        /** 32 random bytes as URL-safe base64: enough for OAuth state and PKCE verifiers. */
        internal fun newToken(): String {
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /** Query and fragment parameters of a redirect URI. */
        fun redirectParams(uri: String): Map<String, String> {
            val parts =
                listOfNotNull(uri.substringAfter('?', "").substringBefore('#').ifEmpty { null }, uri.substringAfter('#', "").ifEmpty { null })
            return parts.flatMap { it.split('&') }.filter { '=' in it }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
        }

        fun dayOf(millis: Long): LocalDate? = if (millis <= 0) null else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        fun millisOf(date: LocalDate?): Long = date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0
        fun millisOf(iso: String?): Long = iso?.takeIf { it.length >= 10 }?.let { runCatching { millisOf(LocalDate.parse(it.take(10))) }.getOrNull() } ?: 0
    }
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull
internal fun JsonElement?.float(): Float? = (this as? JsonPrimitive)?.floatOrNull
internal fun JsonElement?.list(): List<JsonElement> = (this as? kotlinx.serialization.json.JsonArray).orEmpty()
