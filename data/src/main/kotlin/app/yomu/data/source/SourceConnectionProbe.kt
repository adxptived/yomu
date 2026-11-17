package app.yomu.data.source

import app.yomu.source.ConnectionKind
import app.yomu.source.ConnectionProbe
import app.yomu.source.ProbeLimits
import app.yomu.source.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Performs the request behind [ConnectionProbe]: a bounded body, a deadline and a bounded number of redirects.
 *
 * The decision table stays pure in [ConnectionProbe]; this class only turns a response into the arguments of that
 * table. Nothing is saved until a caller has seen [ProbeResult.Recognised].
 */
class SourceConnectionProbe(private val client: () -> OkHttpClient) {

    suspend fun probe(
        rawUrl: String,
        username: String? = null,
        password: String? = null,
        supported: Set<ConnectionKind> = DEFAULT_SUPPORTED,
    ): ProbeResult = withContext(Dispatchers.IO) {
        ConnectionProbe.validateUrl(rawUrl)?.let { return@withContext it }
        // Redirects are followed here, not by OkHttp: the caller has to know that the address it typed served
        // something else, and a redirect to a web page is not a catalogue.
        val http = client().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(ProbeLimits.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        var url = rawUrl.trim()
        var followedRedirects = false
        var hops = 0
        try {
            while (true) {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/atom+xml, application/xml, text/xml, application/rss+xml, application/json;q=0.9, */*;q=0.8")
                    .apply {
                        if (!username.isNullOrBlank()) header("Authorization", Credentials.basic(username, password.orEmpty()))
                    }
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location")
                            ?: return@withContext ProbeResult.InvalidUrl("redirect without location")
                        if (hops++ >= ProbeLimits.MAX_REDIRECTS) return@withContext ProbeResult.NetworkError
                        url = response.request.url.resolve(location)?.toString()
                            ?: return@withContext ProbeResult.InvalidUrl("bad redirect")
                        followedRedirects = true
                        return@use
                    }
                    val body = response.body
                    // At most the probe window is read; a huge document is never buffered whole.
                    val prefix = if (body == null) {
                        ""
                    } else {
                        runCatching { response.peekBody(ProbeLimits.MAX_BYTES).string() }.getOrDefault("")
                    }
                    val result = ConnectionProbe.verdict(
                        status = response.code,
                        contentType = response.header("Content-Type"),
                        bodyPrefix = prefix,
                        supported = supported,
                        followedRedirects = followedRedirects,
                    )
                    return@withContext if (result is ProbeResult.Recognised) {
                        result.copy(
                            title = result.title ?: leadingTitle(prefix),
                            sampleItems = sampleItems(prefix, result.kind),
                        )
                    } else {
                        result
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE") ProbeResult.NetworkError
        } catch (e: IOException) {
            ProbeResult.NetworkError
        }
    }

    /** The first `<title>` of a feed or catalogue, trimmed; null when the document has none. */
    internal fun leadingTitle(body: String): String? {
        val match = TITLE.find(body) ?: return null
        val text = match.groupValues[1].trim()
        return text.ifEmpty { null }?.take(200)
    }

    /**
     * How many entries the probe window actually contains. This is a preview number, not a page: an OPDS document may
     * keep entries in a next page, which is exactly what the wizard tells the user.
     */
    internal fun sampleItems(body: String, kind: ConnectionKind): Int = when (kind) {
        ConnectionKind.OPDS -> ENTRY.findAll(body).count()
        ConnectionKind.RSS -> ITEM.findAll(body).count()
        else -> 0
    }

    companion object {
        /** What the catalogue wizard can save: an OPDS catalogue. Audio and RSS are handled by the audio flow. */
        val DEFAULT_SUPPORTED: Set<ConnectionKind> = setOf(ConnectionKind.OPDS)

        private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        // `<entry>`, `<entry attr=…>` and `<entry/>` are all entries; a closing tag is not.
        private val ENTRY = Regex("<entry[\\s/>]", RegexOption.IGNORE_CASE)
        private val ITEM = Regex("<item[\\s/>]", RegexOption.IGNORE_CASE)
    }
}
