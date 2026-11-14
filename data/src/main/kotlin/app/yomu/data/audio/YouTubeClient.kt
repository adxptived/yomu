package app.yomu.data.audio

import app.yomu.domain.model.AudioChapter
import app.yomu.domain.model.AudioOrigin
import app.yomu.domain.model.AudioTrack
import app.yomu.domain.model.AudiobookManifest
import app.yomu.domain.model.ChapterMarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A YouTube address: a video, a playlist, a video inside a playlist, or a channel ([channelUrl]). */
data class YouTubeRef(val videoId: String?, val playlistId: String?, val channelUrl: String? = null)

/** A playable stream of a video: progressive audio or an HLS manifest, with the headers its server expects. */
data class YouTubeStream(
    val url: String,
    val mimeType: String?,
    val isHls: Boolean,
    val headers: Map<String, String>,
    val contentLength: Long? = null,
)

/** A search result: a video or a playlist. */
data class YouTubeResult(
    val id: String,
    val isPlaylist: Boolean,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long = 0,
    val videoCount: Int? = null,
) {
    val url: String get() = if (isPlaylist) "https://www.youtube.com/playlist?list=$id" else "https://www.youtube.com/watch?v=$id"
    val ref: YouTubeRef get() = if (isPlaylist) YouTubeRef(null, id) else YouTubeRef(id, null)
}

data class YouTubeSearchPage(val items: List<YouTubeResult>, val continuation: String?)

/** What a search returns; [params] are YouTube's own encoded search filters. */
enum class YouTubeSearchFilter(val params: String) {
    /** Videos longer than 20 minutes: audiobooks, lectures, podcasts. */
    LONG_VIDEOS("EgQQARgC"),
    VIDEOS("EgIQAQ=="),
    PLAYLISTS("EgIQAw=="),
}

class YouTubeException(val reason: Reason, message: String? = null) : IOException(message ?: reason.name) {
    enum class Reason { NOT_FOUND, UNPLAYABLE, BLOCKED }
}

/**
 * A small client of YouTube's internal API (InnerTube), without signature deciphering: it asks the player of clients
 * that return direct addresses (see yt-dlp's client table) and keeps the first stream that answers. YouTube changes
 * these rules often, so [CLIENTS] is the single place to update.
 */
class YouTubeClient(private val client: () -> OkHttpClient) {

    private data class Client(val name: String, val id: Int, val version: String, val userAgent: String, val extra: Map<String, Any> = emptyMap())

    suspend fun video(videoId: String): AudiobookManifest = withContext(Dispatchers.IO) {
        val player = runCatching { player(videoId).second }.getOrNull()
        // The watch page's data: official chapters, and the title when the player asks for a sign-in.
        val next = runCatching { postMeta("next", buildJsonObject { put("videoId", videoId) }) }.onFailure { Timber.d(it, "YouTube next") }.getOrNull()
        val nextDetails = next?.let(::parseNextDetails)
        val details = player?.obj("videoDetails")
        val fallback by lazy { oembed(videoId) }
        val title = details?.str("title") ?: nextDetails?.first ?: fallback?.first ?: throw YouTubeException(YouTubeException.Reason.NOT_FOUND)
        val author = details?.str("author")
            ?: next?.let { n -> findAll(n, "videoOwnerRenderer").firstNotNullOfOrNull { text(it["title"]) } }
            ?: fallback?.second
        val durationMs = (details?.str("lengthSeconds")?.toLongOrNull() ?: 0) * 1000
        val description = details?.str("shortDescription") ?: nextDetails?.second
        val chapters = next?.let(::parseChapters).orEmpty().ifEmpty { ChapterMarks.parse(description, durationMs) }
        AudiobookManifest(
            title = title,
            authors = listOfNotNull(author),
            description = description,
            coverUrl = thumbnail(videoId),
            origin = AudioOrigin.YOUTUBE,
            sourceUrl = "https://www.youtube.com/watch?v=$videoId",
            tracks = listOf(AudioTrack(title, AudiobookManifest.youtubeUri(videoId), durationMs, chapters)),
        )
    }

    /** Name of the signed-in Google account (best effort; null when signed out or the answer has no name). */
    suspend fun accountName(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = post("account/account_menu", WEB, buildJsonObject {})
            findAll(response, "accountName").firstNotNullOfOrNull { text(it) }
        }.onFailure { Timber.d(it, "YouTube account menu") }.getOrNull()
    }

    /** Videos (long ones first in relevance, as YouTube ranks them) and playlists for [query]. */
    suspend fun search(query: String): List<YouTubeResult> = withContext(Dispatchers.IO) {
        val locale = Locale.getDefault()
        parseSearch(postMeta("search", buildJsonObject { put("query", query) }, locale))
    }

    /**
     * The videos of a playlist (at most [limit]); a playlist's videos are its chapters. The visionOS client pages
     * through the whole list; the web client's continuations stop after the first page (2026-11).
     */
    suspend fun playlist(playlistId: String, limit: Int = MAX_PLAYLIST, reverse: Boolean = false): AudiobookManifest = withContext(Dispatchers.IO) {
        val list = try {
            readPlaylist(playlistId, limit)
        } catch (e: YouTubeException) {
            throw e
        } catch (e: IOException) {
            throw YouTubeException(YouTubeException.Reason.NOT_FOUND, e.message)
        }
        if (list.videos.isEmpty()) throw YouTubeException(YouTubeException.Reason.NOT_FOUND)
        val tracks = if (reverse) list.videos.reversed() else list.videos
        AudiobookManifest(
            title = list.title,
            authors = listOfNotNull(list.author),
            description = list.description,
            coverUrl = tracks.firstNotNullOfOrNull { AudiobookManifest.youtubeId(it.uri) }?.let(::thumbnail),
            origin = AudioOrigin.YOUTUBE,
            sourceUrl = "https://www.youtube.com/playlist?list=$playlistId",
            tracks = tracks,
        )
    }

    private class PlaylistPage(val title: String, val author: String?, val description: String?, val videos: List<AudioTrack>)

    private fun readPlaylist(playlistId: String, limit: Int): PlaylistPage {
        val first = postMeta("browse", buildJsonObject { put("browseId", "VL$playlistId") })
        val title = playlistTitle(first) ?: throw YouTubeException(YouTubeException.Reason.NOT_FOUND)
        val videos = LinkedHashMap<String, AudioTrack>()
        var page: JsonElement = first
        for (n in 0 until MAX_PAGES) {
            parsePlaylistVideos(page).forEach { videos.putIfAbsent(AudiobookManifest.youtubeId(it.uri)!!, it) }
            if (videos.size >= limit) break
            val token = continuation(page) ?: break
            page = postMeta("browse", buildJsonObject { put("continuation", token) })
        }
        val description = findAll(first, "playlistMetadataRenderer").firstNotNullOfOrNull { it.str("description") }?.takeIf { it.isNotBlank() }
        return PlaylistPage(title, playlistAuthor(first), description, videos.values.take(limit))
    }

    /**
     * The uploads of a channel (`@handle`, `/channel/…`, `/c/…`, `/user/…` links) in the order they were published,
     * like a podcast.
     */
    suspend fun channel(url: String, limit: Int = MAX_PLAYLIST): AudiobookManifest = withContext(Dispatchers.IO) {
        val resolved = runCatching { postMeta("navigation/resolve_url", buildJsonObject { put("url", url) }) }.getOrNull()
        val channelId = resolved?.let { findAll(it, "browseEndpoint").firstNotNullOfOrNull { e -> e.str("browseId") } }
            ?.takeIf { it.startsWith("UC") && it.length > 2 }
            ?: throw YouTubeException(YouTubeException.Reason.NOT_FOUND)
        val uploads = playlist("UU" + channelId.drop(2), limit, reverse = true)
        uploads.copy(
            title = uploads.title.removePrefix("Uploads from ").trim(),
            authors = uploads.authors.ifEmpty { listOf(uploads.title.removePrefix("Uploads from ").trim()) },
            sourceUrl = "https://www.youtube.com/channel/$channelId",
        )
    }

    /** One page of search results; [continuation] comes from the previous page. */
    suspend fun search(query: String, filter: YouTubeSearchFilter, continuation: String? = null): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query)
                put("params", filter.params)
            }
        }
        val page = postMeta("search", body)
        YouTubeSearchPage(parseSearch(page), continuation(page))
    }

    private val streams = ConcurrentHashMap<Pair<String, Boolean>, YouTubeStream>()

    /**
     * The shared client without its cookie jar: the metadata fallback when YouTube refuses the account session, and
     * the player requests of every client that is not the web player.
     */
    private val anonymous: OkHttpClient by lazy { client().newBuilder().cookieJar(CookieJar.NO_COOKIES).build() }

    /**
     * Only the web player accepts a Google session. The JS-less clients answer a request that carries the account
     * cookies and the `SAPISIDHASH` with a bot check or "the page needs to be reloaded" — even for a signed-in user —
     * while the very same request anonymous plays. So the session follows the client, not the app.
     */
    private fun httpFor(c: Client): OkHttpClient = if (c === WEB && YouTubeAuth.isSignedIn()) client() else anonymous

    private fun withSession(c: Client): Boolean = c === WEB && YouTubeAuth.isSignedIn()

    /**
     * A stream address of [videoId] (valid for a few hours); [progressive] skips HLS, for downloading a single file.
     * An address that still has a while to live is reused, so reopening a book does not ask YouTube again; [fresh]
     * skips it (an address the server refused).
     */
    suspend fun stream(videoId: String, progressive: Boolean = false, fresh: Boolean = false): YouTubeStream = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val key = videoId to progressive
        if (!fresh) streams[key]?.takeIf { expiresAt(it.url) > now + CACHE_MARGIN_MS }?.let { return@withContext it }
        streams.entries.removeIf { expiresAt(it.value.url) <= now }
        var lastError: YouTubeException? = null
        var networkError: IOException? = null
        var answered = false
        val failures = mutableListOf<String>()
        for (c in clients()) {
            val response = try {
                postPlayer(c, videoId)
            } catch (e: IOException) {
                networkError = e
                failures += "${c.name}: ${e.message ?: "network error"}"
                continue
            } catch (e: kotlinx.serialization.SerializationException) {
                failures += "${c.name}: unexpected answer"
                continue
            } catch (e: IllegalArgumentException) {
                failures += "${c.name}: unexpected answer"
                continue
            }
            val status = response.obj("playabilityStatus")?.str("status")
            answered = true
            if (status != "OK") {
                val reason = response.obj("playabilityStatus")?.str("reason")
                lastError = YouTubeException(
                    if (status == "LOGIN_REQUIRED") YouTubeException.Reason.BLOCKED else YouTubeException.Reason.UNPLAYABLE,
                    reason,
                )
                failures += "${c.name}: ${reason ?: status}"
                continue
            }
            // A Referer and Origin make the media server treat the request as coming from a player page.
            val headers = mapOf(
                "User-Agent" to c.userAgent,
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com",
            )
            val candidates = candidates(response, headers).filter { !progressive || !it.isHls }
            if (candidates.isEmpty()) {
                // Every format needs a signature that this client does not hand out (the iOS one often does).
                failures += "${c.name}: no direct stream"
                continue
            }
            for (candidate in candidates) {
                if (reachable(candidate)) return@withContext candidate.also { streams[key] = it }
            }
            failures += "${c.name}: stream not reachable"
        }
        // Without any answer the network is the problem, not YouTube: callers retry those later.
        if (!answered && networkError != null) throw networkError
        // Which client said what: a single reason from the wrong client ("sign in" while already signed in) sent
        // readers hunting for a session they had; the whole list shows what really happened.
        val detail = failures.joinToString(" · ").take(240).ifBlank { null }
        throw YouTubeException(lastError?.reason ?: YouTubeException.Reason.BLOCKED, detail ?: lastError?.message)
    }

    /**
     * The regular clients, plus the web player when a Google session is present: it is the most likely one to accept
     * the session's SAPISIDHASH (and the visitor id) when the JS-less clients hit a bot check.
     */
    private fun clients(): List<Client> = if (YouTubeAuth.isSignedIn()) CLIENTS + WEB else CLIENTS

    private fun player(videoId: String): Pair<Client, JsonObject> {
        var last: Throwable? = null
        for (c in clients()) {
            val r = runCatching { postPlayer(c, videoId) }.onFailure { last = it }.getOrNull() ?: continue
            if (r.obj("videoDetails") != null) return c to r
        }
        throw last ?: YouTubeException(YouTubeException.Reason.NOT_FOUND)
    }

    private fun postPlayer(c: Client, videoId: String): JsonObject = post(
        "player", c,
        buildJsonObject {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        },
        http = httpFor(c),
        auth = withSession(c),
    ).jsonObject

    /**
     * A metadata call (search, browse, next): every client in turn, first with the account session, then without it.
     * YouTube often answers one client or one session with 400 (a changed client, an account it will not take for that
     * client), and losing the catalog to that is not acceptable while another client answers fine.
     */
    private fun postMeta(endpoint: String, body: JsonObject, locale: Locale? = null): JsonElement {
        val jars = if (YouTubeAuth.isSignedIn()) listOf(client(), anonymous) else listOf(client())
        var last: Throwable? = null
        for (c in CLIENTS + WEB) {
            for (http in jars) {
                try {
                    return post(endpoint, c, body, locale, http, auth = http !== anonymous)
                } catch (e: IOException) {
                    last = e
                } catch (e: kotlinx.serialization.SerializationException) {
                    last = e
                }
            }
        }
        throw last as? IOException ?: IOException("YouTube $endpoint failed", last)
    }

    /** The server's own sentence from a failed answer ("Precondition check failed"), for the error screen. */
    private fun serverReason(body: String): String =
        Regex("\"(?:reason|message|status)\"\\s*:\\s*\"([^\"]{1,160})\"")
            .find(body)?.groupValues?.getOrNull(1)?.let { ": $it" }.orEmpty()

    private fun post(
        endpoint: String,
        c: Client,
        body: JsonObject,
        locale: Locale? = null,
        http: OkHttpClient = client(),
        auth: Boolean = true,
    ): JsonElement {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", c.name)
                    put("clientVersion", c.version)
                    put("hl", locale?.language?.ifBlank { null } ?: "en")
                    put("gl", locale?.country?.ifBlank { null } ?: "US")
                    c.extra.forEach { (k, v) -> if (v is Int) put(k, v) else put(k, v.toString()) }
                }
            }
            body.forEach { (k, v) -> put(k, v) }
        }
        val builder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", c.userAgent)
            .header("X-YouTube-Client-Name", c.id.toString())
            .header("X-YouTube-Client-Version", c.version)
            .header("Origin", "https://www.youtube.com")
            .post(Json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
        // A Google session from the in-app WebView (Settings → YouTube): the cookies come from the shared cookie
        // jar, the signature from here. Without a session, and for the anonymous fallback, the call stays anonymous;
        // the visitor id is still sent, it keeps the bot check away without pretending to be an account.
        if (auth) YouTubeAuth.headers().forEach { (k, v) -> builder.header(k, v) }
        else YouTubeAuth.visitorHeaders().forEach { (k, v) -> builder.header(k, v) }
        return http.newCall(builder.build()).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw IOException("YouTube $endpoint: HTTP ${r.code}${serverReason(text)}")
            val element = json.parseToJsonElement(text)
            rememberVisitor(element)
            element
        }
    }

    /** YouTube's visitor id identifies the device between calls; sending it back keeps the bot check away. */
    private fun rememberVisitor(root: JsonElement) {
        val context = (root as? JsonObject)?.get("responseContext") as? JsonObject ?: return
        YouTubeAuth.rememberVisitor(context.str("visitorData"))
    }

    private fun reachable(s: YouTubeStream): Boolean = runCatching {
        val request = Request.Builder().url(s.url).apply {
            s.headers.forEach { (k, v) -> header(k, v) }
            if (!s.isHls) header("Range", "bytes=0-1")
        }.build()
        // Anonymous on purpose: a media address needs no account, and the cookie jar must not be sent there.
        anonymous.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun oembed(videoId: String): Pair<String, String?>? = runCatching {
        val url = "https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v=$videoId"
        client().newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val o = json.parseToJsonElement(r.body.string()).jsonObject
            (o.str("title") ?: return null) to o.str("author_name")
        }
    }.getOrNull()

    companion object {
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private const val MAX_PLAYLIST = 500

        // Playlists come 20 videos per page.
        private const val MAX_PAGES = 30
        private const val CACHE_MARGIN_MS = 30 * 60_000L

        private val WEB = Client(
            "WEB", 1, "2.20260708.01.00",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
        )

        private val VISIONOS = Client(
            "VISIONOS", 101, "1.02",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
        )

        // Clients without a JavaScript player, newest working first (yt-dlp's table, checked against the live API
        // 2026-11: visionOS, Android VR and Android hand out direct audio addresses without a PO token).
        // Metadata calls try them in this order and then the web client.
        private val CLIENTS = listOf(
            VISIONOS,
            Client(
                "ANDROID_VR", 28, "1.61.48",
                "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L"),
            ),
            Client(
                "ANDROID", 3, "20.10.38",
                "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
                mapOf("androidSdkVersion" to 34, "osName" to "Android", "osVersion" to "14"),
            ),
            Client(
                "IOS", 5, "21.26.4",
                "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"),
            ),
        )

        fun thumbnail(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        /** Recognizes youtube.com/watch, youtu.be, shorts, live, music.youtube.com, playlist and channel links. */
        fun parseUrl(text: String): YouTubeRef? {
            val url = text.trim().let { if (it.startsWith("http")) it else "https://$it" }.toHttpUrlOrNull() ?: return null
            val host = url.host.removePrefix("www.").removePrefix("m.").removePrefix("music.")
            val list = url.queryParameter("list")?.takeIf { ID.matches(it) }
            val first = url.pathSegments.firstOrNull().orEmpty()
            val video = when (host) {
                "youtu.be" -> first
                "youtube.com", "youtube-nocookie.com" -> when {
                    first == "watch" -> url.queryParameter("v")
                    first in setOf("shorts", "live", "embed", "v") -> url.pathSegments.getOrNull(1)
                    first == "playlist" -> null
                    list == null && (first.startsWith("@") || first in setOf("channel", "c", "user")) -> {
                        val name = if (first.startsWith("@")) first else url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                        val path = if (first.startsWith("@")) name else "$first/$name"
                        return YouTubeRef(null, null, "https://www.youtube.com/$path")
                    }
                    else -> return null
                }
                else -> return null
            }?.takeIf { ID.matches(it) }
            if (video == null && list == null) return null
            return YouTubeRef(video, list)
        }

        internal fun playlistTitle(page: JsonElement): String? =
            findAll(page, "playlistHeaderRenderer").firstNotNullOfOrNull { text(it["title"]) }
                ?: findAll(page, "playlistMetadataRenderer").firstNotNullOfOrNull { it.str("title") }
                ?: findAll(page, "pageHeaderViewModel").firstNotNullOfOrNull { it.obj("title")?.obj("dynamicTextViewModel")?.obj("text")?.str("content") }

        private fun playlistAuthor(page: JsonElement): String? =
            findAll(page, "playlistHeaderRenderer").firstNotNullOfOrNull { text(it["ownerText"]) }
                ?: findAll(page, "pageHeaderViewModel").firstNotNullOfOrNull { header ->
                    findAll(header, "avatarStackViewModel").firstNotNullOfOrNull { it.obj("text")?.str("content") }?.removePrefix("by ")?.trim()
                }
                ?: findAll(page, "videoOwnerRenderer").firstNotNullOfOrNull { text(it["title"]) }

        /** The token of the next page, in the newer (`continuationCommand`) or older (`nextContinuationData`) form. */
        internal fun continuation(page: JsonElement): String? =
            findAll(page, "continuationCommand").firstNotNullOfOrNull { it.str("token") }
                ?: findAll(page, "nextContinuationData").firstNotNullOfOrNull { it.str("continuation") }

        private fun bestThumbnail(o: JsonObject): String? =
            ((o["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
                ?.let { if (it.startsWith("//")) "https:$it" else it }

        private val ID = Regex("[A-Za-z0-9_-]{6,64}")

        /** Playable streams of a player response, best first: audio-only (AAC, then Opus), the 360p mp4 with sound, HLS. */
        internal fun candidates(response: JsonObject, headers: Map<String, String>): List<YouTubeStream> {
            val streaming = response.obj("streamingData") ?: return emptyList()
            fun formats(key: String) = (streaming[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.str("url") != null }
            val audio = originalLanguage(formats("adaptiveFormats").filter { it.str("mimeType")?.startsWith("audio/") == true })
                // DRC tracks are quieter copies; ExoPlayer plays mp4 everywhere, webm/opus since Android 5.
                .filter { it.str("xtags")?.contains("drc") != true }
                .sortedWith(compareByDescending<JsonObject> { it.str("mimeType")!!.startsWith("audio/mp4") }.thenByDescending { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 })
                .map { YouTubeStream(it.str("url")!!, it.str("mimeType")?.substringBefore(';'), false, headers, it.str("contentLength")?.toLongOrNull()) }
            val muxed = formats("formats").filter { it["itag"]?.jsonPrimitive?.intOrNull == 18 }
                .map { YouTubeStream(it.str("url")!!, "video/mp4", false, headers) }
            val hls =
                listOfNotNull(streaming.str("hlsManifestUrl")?.let { YouTubeStream(it, "application/x-mpegURL", true, headers) })
            return audio + muxed + hls
        }

        /**
         * Videos with several audio languages (uploaded or automatic dubs) list each as its own formats; the default
         * one is the original voice, which an audiobook wants rather than a dub.
         */
        private fun originalLanguage(formats: List<JsonObject>): List<JsonObject> {
            if (formats.none { it.obj("audioTrack") != null }) return formats
            fun flag(f: JsonObject) = f.obj("audioTrack")?.get("audioIsDefault")?.jsonPrimitive?.booleanOrNull == true
            fun original(f: JsonObject) = f.obj("audioTrack")?.str("displayName")?.contains("original", ignoreCase = true) == true
            return formats.filter(::flag).ifEmpty { formats.filter(::original) }.ifEmpty { formats }
        }

        /**
         * The chapters YouTube shows on the progress bar (written by the uploader or generated), from a `next`
         * response: the player bar's markers map, else the chapters panel.
         */
        internal fun parseChapters(next: JsonElement): List<AudioChapter> {
            val fromBar = findAll(next, "multiMarkersPlayerBarRenderer").flatMap { bar ->
                (bar["markersMap"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .filter { it.str("key") in CHAPTER_KEYS }
                    .flatMap { findAll(it, "chapterRenderer") }
                    .mapNotNull { c ->
                        val start = c["timeRangeStartMillis"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                        AudioChapter(text(c["title"]) ?: return@mapNotNull null, start)
                    }
            }
            val chapters = fromBar.ifEmpty {
                findAll(next, "engagementPanelSectionListRenderer")
                    .filter { p -> (p.str("panelIdentifier") ?: p.str("targetId")).orEmpty().let { "description-chapters" in it || "auto-chapters" in it } }
                    .flatMap { findAll(it, "macroMarkersListItemRenderer") }
                    .mapNotNull { m ->
                        val start = findAll(m, "watchEndpoint").firstNotNullOfOrNull { it["startTimeSeconds"]?.jsonPrimitive?.longOrNull }?.times(1000)
                            ?: text(m["timeDescription"])?.let(ChapterMarks::parseTime) ?: return@mapNotNull null
                        AudioChapter(text(m["title"]) ?: return@mapNotNull null, start)
                    }
            }
            val sorted = chapters.distinctBy { it.startMs }.sortedBy { it.startMs }
            return if (sorted.size >= 2 && sorted.first().startMs == 0L) sorted else emptyList()
        }

        /** Title and description of a video from a `next` response (when the player refused to answer). */
        internal fun parseNextDetails(next: JsonElement): Pair<String?, String?> {
            val title = findAll(next, "videoPrimaryInfoRenderer").firstNotNullOfOrNull { text(it["title"]) }
            val description = findAll(next, "videoSecondaryInfoRenderer").firstNotNullOfOrNull { it.obj("attributedDescription")?.str("content") }
            return title to description
        }

        private val CHAPTER_KEYS = setOf("DESCRIPTION_CHAPTERS", "AUTO_CHAPTERS")

        internal fun parsePlaylistVideos(page: JsonElement): List<AudioTrack> {
            val classic = findAll(page, "playlistVideoRenderer").mapNotNull { r ->
                val id = r.str("videoId") ?: return@mapNotNull null
                if (r["isPlayable"]?.jsonPrimitive?.contentOrNull == "false") return@mapNotNull null
                val length = r.str("lengthSeconds")?.toLongOrNull()
                val title = text(r["title"]) ?: id
                // "[Deleted video]" and "[Private video]" entries have no length and cannot play.
                if (length == null && title.startsWith("[") && title.endsWith("]")) return@mapNotNull null
                AudioTrack(title, AudiobookManifest.youtubeUri(id), (length ?: 0) * 1000)
            }
            val lockups = findAll(page, "lockupViewModel").mapNotNull { r ->
                if (r.str("contentType")?.contains("VIDEO") != true) return@mapNotNull null
                val id = r.str("contentId") ?: return@mapNotNull null
                val title = r.obj("metadata")?.obj("lockupMetadataViewModel")?.obj("title")?.str("content") ?: id
                // The length is only written on the thumbnail's badge ("12:34").
                val length = findAll(r, "thumbnailBadgeViewModel").firstNotNullOfOrNull { b -> b.str("text")?.trim()?.let(ChapterMarks::parseTime) } ?: 0
                AudioTrack(title, AudiobookManifest.youtubeUri(id), length)
            }
            return classic + lockups
        }

        /**
         * Videos and playlists of a search page in page order: compact renderers (visionOS client), full ones and the
         * newer lockups (web). Live streams (no length), Shorts and entries without a title are left out.
         */
        internal fun parseSearch(page: JsonElement): List<YouTubeResult> {
            val out = LinkedHashMap<Pair<Boolean, String>, YouTubeResult>()
            walk(page, setOf("videoRenderer", "compactVideoRenderer", "playlistRenderer", "compactPlaylistRenderer", "lockupViewModel")) { key, o ->
                val r = when (key) {
                    "videoRenderer", "compactVideoRenderer" -> o.str("videoId")?.let { id ->
                        val length = text(o["lengthText"])?.let(ChapterMarks::parseTime) ?: return@let null
                        YouTubeResult(
                            id = id, isPlaylist = false, title = text(o["title"]) ?: return@let null,
                            author = text(o["shortBylineText"]) ?: text(o["longBylineText"]) ?: text(o["ownerText"]),
                            thumbnailUrl = thumbnail(id), durationMs = length,
                        )
                    }
                    "playlistRenderer", "compactPlaylistRenderer" -> o.str("playlistId")?.let { id ->
                        val firstVideo = findAll(o, "watchEndpoint").firstNotNullOfOrNull { it.str("videoId") }
                        YouTubeResult(
                            id = id, isPlaylist = true, title = text(o["title"]) ?: return@let null,
                            author = text(o["shortBylineText"]) ?: text(o["longBylineText"]),
                            thumbnailUrl = bestThumbnail(o) ?: findAll(o, "thumbnailRenderer").firstNotNullOfOrNull(::bestThumbnail) ?: firstVideo?.let(::thumbnail),
                            videoCount = (text(o["videoCountShortText"]) ?: o.str("videoCount"))?.filter(Char::isDigit)?.toIntOrNull(),
                        )
                    }
                    else -> o.str("contentId")?.let { id ->
                        val type = o.str("contentType").orEmpty()
                        if ("PLAYLIST" !in type && "VIDEO" !in type) return@let null
                        val meta = o.obj("metadata")?.obj("lockupMetadataViewModel")
                        val title = meta?.obj("title")?.str("content") ?: return@let null
                        val author = meta.let { findAll(it, "contentMetadataViewModel").firstOrNull() }
                            ?.let { (it["metadataRows"] as? JsonArray)?.firstOrNull() as? JsonObject }
                            ?.let { (it["metadataParts"] as? JsonArray)?.firstOrNull() as? JsonObject }
                            ?.obj("text")?.str("content")
                        val badge = findAll(o, "thumbnailBadgeViewModel").firstNotNullOfOrNull { it.str("text") }
                        if ("PLAYLIST" in type) {
                            val image = findAll(o, "thumbnailViewModel").firstNotNullOfOrNull { t ->
                                ((t.obj("image")?.get("sources") as? JsonArray)?.firstOrNull() as? JsonObject)?.str("url")
                            }
                            YouTubeResult(id, true, title, author, image, videoCount = badge?.filter(Char::isDigit)?.toIntOrNull())
                        } else {
                            val length = badge?.trim()?.let(ChapterMarks::parseTime) ?: return@let null
                            YouTubeResult(id, false, title, author, thumbnail(id), durationMs = length)
                        }
                    }
                }
                if (r != null) out.putIfAbsent(r.isPlaylist to r.id, r)
            }
            return out.values.toList()
        }

        /** When a stream address stops working (its `expire` parameter), or 0 when it does not say. */
        fun expiresAt(url: String): Long {
            val http = url.toHttpUrlOrNull() ?: return 0
            val seconds = http.queryParameter("expire")?.toLongOrNull()
                ?: http.pathSegments.indexOf("expire").takeIf { it >= 0 }?.let { http.pathSegments.getOrNull(it + 1)?.toLongOrNull() }
            return (seconds ?: 0) * 1000
        }

        private fun walk(root: JsonElement, keys: Set<String>, onMatch: (String, JsonObject) -> Unit) {
            when (root) {
                is JsonObject -> root.forEach { (k, v) ->
                    if (k in keys && v is JsonObject) onMatch(k, v) else walk(v, keys, onMatch)
                }
                is JsonArray -> root.forEach { walk(it, keys, onMatch) }
                else -> Unit
            }
        }

        /** Every object stored under [key] anywhere in [root]. */
        internal fun findAll(root: JsonElement, key: String): List<JsonObject> {
            val out = mutableListOf<JsonObject>()
            fun walk(e: JsonElement) {
                when (e) {
                    is JsonObject -> e.forEach { (k, v) ->
                        if (k == key && v is JsonObject) out += v
                        walk(v)
                    }
                    is JsonArray -> e.forEach(::walk)
                    is JsonPrimitive -> Unit
                }
            }
            walk(root)
            return out
        }

        /** Text of a `{simpleText}` or `{runs: [{text}]}` object. */
        private fun text(e: JsonElement?): String? {
            val o = e as? JsonObject ?: return null
            return o.str("simpleText") ?: (o["runs"] as? JsonArray)?.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }?.takeIf { it.isNotBlank() }
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    }
}
