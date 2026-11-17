package app.yomu.core.common

/**
 * Removes secrets from anything written to the local error journal.
 *
 * A crash report may contain a request, a stack trace with a URL, a source's answer or a form body. Credentials do not
 * belong in a file that a user may later share, so the same masking is applied to messages and traces before they are
 * written — not only on export.
 *
 * Over-redaction is deliberate: a value after `token=` is replaced even when it happened to be harmless.
 */
object ErrorRedaction {
    private val header = Regex("(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*:\\s*[^\\r\\n]+")
    private val scheme = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9+/=_.~-]{8,}")
    private val urlParam = Regex(
        "(?i)([?&;])((?:access_token|refresh_token|id_token|token|api_key|apikey|api-key|key|password|passwd|secret|client_secret|sig|signature|auth|session|sessionid|code)=)[^&#\\s\"']+",
    )
    private val formField = Regex(
        "(?i)\\b(access_token|refresh_token|id_token|token|api_key|apikey|api-key|password|passwd|secret|client_secret|session|sessionid)=([^&\\s\"']+)",
    )
    // Response bodies are logged too; only the whole key is masked, so "cacheKey" or "countryCode" stay readable.
    private val jsonField = Regex(
        "(?i)\"(access_token|refresh_token|id_token|token|password|passwd|secret|client_secret|api_key|apikey|auth|session|sessionid)\"\\s*:\\s*\"[^\"]*\"",
    )

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = header.replace(text) { "${it.groupValues[1]}: [redacted]" }
        out = scheme.replace(out) { "${it.groupValues[1]} [redacted]" }
        out = urlParam.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
        out = jsonField.replace(out) { "\"${it.groupValues[1]}\":\"[redacted]\"" }
        out = formField.replace(out) { "${it.groupValues[1]}=[redacted]" }
        return out
    }
}
