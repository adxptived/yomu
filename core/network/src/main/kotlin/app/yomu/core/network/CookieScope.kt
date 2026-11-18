package app.yomu.core.network

import java.io.File
import java.net.URI

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
 * One cookie reduced to what a scoped clear needs: host, path and name. A value is never stored: the settings screen
 * may show the scope without ever holding a secret.
 */
data class CookieKey(val host: String, val path: String, val name: String)

/** What a clear of one site did: the normalized host, the cookie names it found and the paths it swept. */
data class SiteClearResult(val host: String, val cookies: Int, val paths: Int) {
    val found: Boolean get() = cookies > 0
}

/** Android exposes no "list cookies of a host" call, so this is the small surface the cleaner needs from the jar. */
interface CookieStore {
    /** Names of the cookies the store would send to `https://host/path`; the path decides which cookies match. */
    fun names(host: String, path: String): List<String>

    /** Expires [name] at [path] for [host]; returns false when the platform refused the instruction. */
    fun expire(host: String, path: String, name: String): Boolean

    fun flush()
}

/**
 * An index of the cookies the app knows about, kept because the WebView cookie store cannot be enumerated by host.
 *
 * Entries come from two places: cookies written by the shared HTTP client (their path and domain are exact) and pages
 * opened in a WebView (their paths are known, the paths of the cookies they set are not). A clear sweeps every known
 * path of the host, so a cookie on a nested path is removed as well as the one on `/`. Values are never recorded.
 */
class CookieLedger(private val file: File? = null) {
    private val entries = LinkedHashSet<CookieKey>()
    private val paths = LinkedHashSet<Pair<String, String>>()

    init {
        load()
    }

    @Synchronized
    fun record(domain: String, path: String, name: String) {
        val host = hostKey(domain)
        if (host.isBlank() || name.isBlank()) return
        val p = normalizePath(path)
        val changed = entries.add(CookieKey(host, p, name))
        if (paths.add(host to p)) {
            save()
        } else if (changed) {
            save()
        }
    }

    /** Records the host and the candidate paths of a page loaded in a WebView; its cookies are read back by the jar. */
    @Synchronized
    fun recordUrl(url: String) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val host = uri.host?.let(::hostKey)?.takeIf { it.isNotBlank() } ?: return
        val path = uri.path?.takeIf { it.startsWith("/") } ?: "/"
        var changed = false
        hostAliases(host).forEach { h ->
            pathCandidates(path).forEach { p -> if (paths.add(h to p)) changed = true }
        }
        if (changed) save()
    }

    @Synchronized
    fun keys(host: String): Set<CookieKey> {
        val h = hostKey(host)
        return entries.filterTo(LinkedHashSet()) { it.host == h }
    }

    @Synchronized
    fun paths(host: String): Set<String> {
        val h = hostKey(host)
        return paths.filterTo(LinkedHashSet()) { it.first == h }.mapTo(LinkedHashSet()) { it.second }
    }

    @Synchronized
    fun forget(hosts: Collection<String>): Int {
        val wanted = hosts.map(::hostKey).toSet()
        val before = entries.size
        entries.removeAll { it.host in wanted }
        paths.removeAll { it.first in wanted }
        if (entries.size != before) save()
        return before - entries.size
    }

    @Synchronized
    fun clear() {
        entries.clear()
        paths.clear()
        save()
    }

    private fun load() {
        val f = file ?: return
        val text = runCatching { f.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        text.lineSequence().forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "C" -> if (parts.size >= 4) entries.add(CookieKey(parts[1], parts[2], parts[3]))
                "P" -> if (parts.size >= 3) paths.add(parts[1] to parts[2])
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(
                buildString {
                    entries.forEach { append("C\t").append(it.host).append('\t').append(it.path).append('\t').append(it.name).append('\n') }
                    paths.forEach { (host, path) -> append("P\t").append(host).append('\t').append(path).append('\n') }
                },
            )
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        }
    }
}

/**
 * Removes the cookies of one host. The WebView store has no per-host remove, so every known name is expired at every
 * known path — both as a host-only cookie and as a domain cookie, because those are different entries. Other sites are
 * never touched, and the result says what was actually found instead of assuming success.
 */
class SiteDataClearer(private val store: CookieStore, private val ledger: CookieLedger) {
    fun clear(rawHost: String): SiteClearResult {
        val host = normalizeHost(rawHost)
        if (host.isBlank()) return SiteClearResult("", 0, 0)
        val hosts = hostAliases(host)
        val keys = hosts.flatMap { ledger.keys(it) }
        val paths = sortedSetOf("/")
        keys.mapTo(paths) { it.path }
        hosts.forEach { paths += ledger.paths(it) }
        val names = sortedSetOf<String>()
        keys.mapTo(names) { it.name }
        // Names the WebView holds at the root are invisible to the ledger until a page records them; read them back.
        hosts.forEach { h -> paths.forEach { p -> store.names(h, p).forEach { names += it } } }
        names.forEach { name ->
            hosts.forEach { h ->
                paths.forEach { p -> store.expire(h, p, name) }
            }
        }
        store.flush()
        ledger.forget(hosts)
        return SiteClearResult(host, names.size, paths.size)
    }
}

/**
 * The platform cookie store behind [WebViewCookieJar]. A cookie is identified by name, domain and path, so the same
 * name is expired at the host itself and as a `Domain=` cookie: both spellings are stored separately by a browser.
 */
class WebViewCookieStore(private val manager: android.webkit.CookieManager?) : CookieStore {
    override fun names(host: String, path: String): List<String> {
        val raw = manager?.getCookie("https://$host$path") ?: return emptyList()
        return raw.split(";").mapNotNull { part ->
            part.trim().substringBefore('=').takeIf { it.isNotBlank() }
        }
    }

    override fun expire(host: String, path: String, name: String): Boolean {
        val m = manager ?: return false
        val url = "https://$host$path"
        val base = "$name=; Max-Age=0; Path=$path"
        runCatching { m.setCookie(url, base) }
        // The host-only and the domain cookie are separate entries with the same name and path.
        runCatching { m.setCookie(url, "$base; Domain=$host") }
        runCatching { m.setCookie(url, "$base; Domain=.$host") }
        return true
    }

    override fun flush() {
        runCatching { manager?.flush() }
    }
}

/** Normalizes user input (`https://example.org/path`, `www.example.org:443`) to a bare lower-case host. */
fun normalizeHost(raw: String): String {
    val trimmed = raw.trim()
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    return hostKey(host)
}

/** The key a host is stored under: lower case, no leading dot. */
fun hostKey(host: String): String = host.trim().trim('.').lowercase()

/** A site's host and the parent domain a `www.` page usually shares cookies with. */
fun hostAliases(host: String): Set<String> {
    val h = hostKey(host)
    if (h.isBlank()) return emptySet()
    val out = linkedSetOf(h)
    if (h.startsWith("www.")) out += h.removePrefix("www.")
    return out
}

/** A cookie path always starts with `/`; an empty or relative path is the root. */
fun normalizePath(path: String): String = path.trim().takeIf { it.startsWith("/") } ?: "/"

/**
 * Paths a page URL may have set cookies on: the root, every directory and the full path. Expiring a name at a path
 * where it does not exist is harmless, so a clear can be thorough without the browser telling which path it used.
 */
fun pathCandidates(path: String): Set<String> {
    val clean = normalizePath(path).substringBefore('?').substringBefore('#')
    val out = linkedSetOf("/")
    var i = 1
    while (i < clean.length) {
        val slash = clean.indexOf('/', i)
        val end = if (slash < 0) clean.length else slash
        if (end > 1) {
            out += clean.substring(0, end)
            out += clean.substring(0, end) + "/"
        }
        if (slash < 0) break
        i = slash + 1
    }
    out += clean
    if (!clean.endsWith("/")) out += "$clean/"
    return out
}

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
