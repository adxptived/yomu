package app.yomu.data.source

import app.yomu.data.audio.PodcastFeed
import app.yomu.source.ConnectionKind
import app.yomu.source.ConnectionProbe
import app.yomu.source.LinkCandidate
import app.yomu.source.ProbeLimits
import app.yomu.source.ProbeResult
import app.yomu.source.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
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
        // Credentials belong to the server the user typed. A redirect to another scheme, host or port must not get them.
        val credentialOrigin = runCatching { origin(java.net.URI(rawUrl.trim())) }.getOrNull()
        var followedRedirects = false
        var hops = 0
        try {
            while (true) {
                val sameOrigin = runCatching { origin(java.net.URI(url)) }.getOrNull() == credentialOrigin
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/atom+xml, application/xml, text/xml, application/rss+xml, application/json;q=0.9, */*;q=0.8")
                    .apply {
                        if (!username.isNullOrBlank() && sameOrigin) {
                            header("Authorization", Credentials.basic(username, password.orEmpty()))
                        }
                    }
                    .build()
                http.newCall(request).await().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location")
                            ?: return@withContext ProbeResult.TransportFailure(ProbeResult.TransportIssue.REDIRECT)
                        if (hops++ >= ProbeLimits.MAX_REDIRECTS) return@withContext ProbeResult.TransportFailure(ProbeResult.TransportIssue.REDIRECT)
                        url = response.request.url.resolve(location)?.toString()
                            ?: return@withContext ProbeResult.TransportFailure(ProbeResult.TransportIssue.REDIRECT)
                        followedRedirects = true
                        return@use
                    }
                    val prefix = when {
                        response.code in 200..299 -> response.peekBody(ProbeLimits.MAX_BYTES).string()
                        response.code == 403 || response.code == 503 -> response.peekBody(2048).string()
                        else -> ""
                    }
                    val browserChallenge = response.header("cf-mitigated").equals("challenge", ignoreCase = true) ||
                        (response.header("Server").orEmpty().contains("cloudflare", ignoreCase = true) &&
                            prefix.contains("challenge-platform", ignoreCase = true))
                    val declaredType = response.header("Content-Type")
                    val kind = ConnectionProbe.classify(declaredType, prefix)
                    val mayBeAtom = kind == ConnectionKind.OPDS &&
                        prefix.take(2048).contains("<feed", ignoreCase = true) &&
                        !prefix.contains("http://opds-spec.org/acquisition", ignoreCase = true)
                    val audio = if (kind == ConnectionKind.RSS || mayBeAtom) PodcastFeed.parse(prefix, url) else null
                    val result = ConnectionProbe.verdict(
                        status = response.code,
                        contentType = if (audio != null && mayBeAtom) "application/rss+xml" else declaredType,
                        bodyPrefix = prefix,
                        supported = supported,
                        followedRedirects = followedRedirects,
                        browserChallenge = browserChallenge,
                    )
                    return@withContext if (result is ProbeResult.Recognised) {
                        if (result.kind == ConnectionKind.RSS && audio == null) {
                            return@withContext ProbeResult.NoAudioEpisodes
                        }
                        result.copy(
                            title = result.title ?: leadingTitle(prefix),
                            sampleItems = audio?.tracks?.size ?: sampleItems(prefix, result.kind),
                            candidates = if (result.kind == ConnectionKind.WEB_PAGE) linkCandidates(prefix, url) else emptyList(),
                        )
                    } else {
                        result
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE") ProbeResult.NetworkError
        } catch (e: IOException) {
            when (e) {
                is InterruptedIOException -> ProbeResult.TransportFailure(ProbeResult.TransportIssue.TIMEOUT)
                is UnknownHostException -> ProbeResult.TransportFailure(ProbeResult.TransportIssue.DNS)
                is ConnectException -> ProbeResult.TransportFailure(ProbeResult.TransportIssue.CONNECTION)
                is SSLException -> ProbeResult.TransportFailure(ProbeResult.TransportIssue.TLS)
                else -> ProbeResult.NetworkError
            }
        } catch (_: IllegalArgumentException) {
            ProbeResult.InvalidUrl("malformed address")
        }
    }

    /** Scheme, host and effective port: the origin that may receive the typed server's credentials. */
    private fun origin(uri: java.net.URI): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        return "$scheme://$host:$port"
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

    /**
     * Feeds and catalogues a web page advertises: `<link rel="alternate" type="application/rss+xml" href="…">` and its
     * Atom/OPDS counterparts. Only the declared type decides the kind; anchors to arbitrary pages are not proposed.
     */
    internal fun linkCandidates(body: String, baseUrl: String): List<LinkCandidate> {
        val base = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return emptyList()
        return LINK.findAll(body).mapNotNull { match ->
            val attrs = ATTRIBUTE.findAll(match.value).associate { it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val href = attrs["href"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val type = attrs["type"]?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val kind = when (type) {
                "application/rss+xml", "application/rdf+xml", "text/rss+xml" -> ConnectionKind.RSS
                "application/atom+xml", "application/opds+xml", "application/opds+json" -> ConnectionKind.OPDS
                else -> return@mapNotNull null
            }
            val url = runCatching { base.resolve(href).toString() }.getOrNull() ?: return@mapNotNull null
            LinkCandidate(url, kind, attrs["title"]?.trim()?.takeIf { it.isNotEmpty() })
        }.distinctBy { it.url }.take(6).toList()
    }

    companion object {
        /** What the catalogue wizard can save: an OPDS catalogue. Audio and RSS are handled by the audio flow. */
        val DEFAULT_SUPPORTED: Set<ConnectionKind> = setOf(ConnectionKind.OPDS)

        private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        // `<entry>`, `<entry attr=…>` and `<entry/>` are all entries; a closing tag is not.
        private val ENTRY = Regex("<entry[\\s/>]", RegexOption.IGNORE_CASE)
        private val ITEM = Regex("<item[\\s/>]", RegexOption.IGNORE_CASE)
        private val LINK = Regex("<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE))
        private val ATTRIBUTE = Regex("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    }
}
