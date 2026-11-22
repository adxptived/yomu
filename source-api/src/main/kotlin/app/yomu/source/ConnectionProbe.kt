package app.yomu.source

/** What kind of document a connection address points at. */
enum class ConnectionKind { OPDS, RSS, AUDIO, WEB_PAGE, UNKNOWN }

/**
 * The result of checking a connection address before it is saved.
 *
 * A probe never reports a bare success: the caller learns what was recognised, and a failure says which of the very
 * different causes happened. Treating every 403 as "needs a password", or saving whatever answered 200, is what made a
 * broken connection look installed.
 */
sealed interface ProbeResult {
    /** The address answered and the content was recognised. */
    data class Recognised(
        val kind: ConnectionKind,
        /** Name reported by the document itself (an OPDS title, a feed title); null when it has none. */
        val title: String? = null,
        /** How many items the preview actually parsed, which is what the user sees before saving. */
        val sampleItems: Int = 0,
        /**
         * For a web page: the feeds and catalogues it links to. An ordinary site is not a connection, but a blog or a
         * library landing page often advertises its RSS or OPDS document explicitly; the user picks one instead of
         * hunting for the address.
         */
        val candidates: List<LinkCandidate> = emptyList(),
    ) : ProbeResult

    /** The address is valid but the document is not a type this build can read. */
    data class Unsupported(val detected: ConnectionKind) : ProbeResult

    /** The server wants a login. */
    data object NeedsAuth : ProbeResult

    /** The browser check of the site must be completed first (Cloudflare and similar). */
    data object NeedsBrowser : ProbeResult

    /** No usable address: a missing scheme, an unsupported scheme, or a malformed value. */
    data class InvalidUrl(val reason: String) : ProbeResult

    /** The request timed out or the network is unavailable. */
    data object NetworkError : ProbeResult

    data object NoAudioEpisodes : ProbeResult

    data class HttpFailure(val status: Int) : ProbeResult

    enum class TransportIssue { TIMEOUT, DNS, CONNECTION, TLS, REDIRECT }

    data class TransportFailure(val issue: TransportIssue) : ProbeResult

    /** The source API version is not one this build supports. */
    data class ApiMismatch(val found: String?) : ProbeResult
}

/**
 * Classifies a probe from what was observed, without performing any I/O.
 *
 * Keeping this pure means the whole decision table is testable without a server, and the HTTP layer only has to report
 * status, content type and a small prefix of the body.
 */
object ConnectionProbe {
    /** How much of the body is inspected: enough for the first entries of a feed, never the whole document. */
    const val MAX_INSPECT_BYTES = 64 * 1024

    /**
     * Recognises a document from its declared type and the start of its body.
     *
     * The content type alone is not trusted: OPDS is often served as `text/xml` or `application/xml`, and an HTML
     * error page arrives as `application/xml` from some servers. The body decides when the two disagree.
     */
    fun classify(contentType: String?, bodyPrefix: String): ConnectionKind {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val head = bodyPrefix.trimStart().take(2048)
        val looksXml = head.startsWith("<?xml") || head.startsWith("<feed") || head.startsWith("<rss") || head.startsWith("<opds")
        val looksHtml = head.startsWith("<!doctype html", ignoreCase = true) || head.startsWith("<html", ignoreCase = true)
        // An error page served as XML is still a web page, not a catalogue.
        if (looksHtml) return ConnectionKind.WEB_PAGE
        return when {
            type == "application/atom+xml" || type == "application/opds+xml" || type == "application/opds+json" -> ConnectionKind.OPDS
            type == "application/rss+xml" || type == "application/rdf+xml" || type == "text/rss+xml" -> ConnectionKind.RSS
            type.startsWith("audio/") || type == "application/ogg" -> ConnectionKind.AUDIO
            type == "text/html" -> if (looksHtml) ConnectionKind.WEB_PAGE else ConnectionKind.UNKNOWN
            // A declared generic XML/JSON document is told apart by its root element.
            type == "application/xml" || type == "text/xml" || type == "application/json" || looksXml -> when {
                head.contains("<opds", ignoreCase = true) || head.contains("http://opds-spec.org", ignoreCase = true) ||
                    head.contains("\"metadata\"", ignoreCase = true) -> ConnectionKind.OPDS
                head.contains("<rss", ignoreCase = true) || head.contains("<feed", ignoreCase = true) ||
                    head.contains("<rdf:RDF", ignoreCase = true) -> ConnectionKind.RSS
                else -> ConnectionKind.UNKNOWN
            }
            else -> ConnectionKind.UNKNOWN
        }
    }

    /**
     * The verdict of a probe request.
     *
     * [followedRedirects] is reported rather than hidden: a connection that only works through an unexpected final
     * address is not the address the user typed.
     */
    fun verdict(
        status: Int,
        contentType: String?,
        bodyPrefix: String,
        supported: Set<ConnectionKind>,
        followedRedirects: Boolean = false,
        browserChallenge: Boolean = false,
    ): ProbeResult {
        if (status == 401) return ProbeResult.NeedsAuth
        if (browserChallenge && (status == 403 || status == 503)) return ProbeResult.NeedsBrowser
        if (status !in 200..299) return ProbeResult.HttpFailure(status)
        val kind = classify(contentType, bodyPrefix)
        if (kind !in supported) return ProbeResult.Unsupported(kind)
        // A redirect to a web page means the address serves a site, not the document the user expected.
        if (followedRedirects && kind == ConnectionKind.WEB_PAGE) return ProbeResult.Unsupported(ConnectionKind.WEB_PAGE)
        return ProbeResult.Recognised(kind)
    }

    /** Rejects an address that cannot be used at all, before any request is made. */
    fun validateUrl(raw: String): ProbeResult? {
        val text = raw.trim()
        if (text.isEmpty()) return ProbeResult.InvalidUrl("empty")
        val scheme = text.substringBefore("://", "").lowercase()
        if (scheme.isEmpty()) return ProbeResult.InvalidUrl("missing scheme")
        // Only plain addresses: a file or javascript address is not a connection.
        if (scheme != "http" && scheme != "https") return ProbeResult.InvalidUrl("unsupported scheme $scheme")
        val host = text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) return ProbeResult.InvalidUrl("missing host")
        return null
    }
}

/** A feed or catalogue advertised by a web page, with the kind it declared. */
data class LinkCandidate(
    val url: String,
    val kind: ConnectionKind,
    val title: String? = null,
)

/** Limits of one probe request, shared by every connection wizard. */
object ProbeLimits {
    const val TIMEOUT_MS = 15_000L
    const val MAX_REDIRECTS = 5
    const val MAX_BYTES = ConnectionProbe.MAX_INSPECT_BYTES.toLong()
}
