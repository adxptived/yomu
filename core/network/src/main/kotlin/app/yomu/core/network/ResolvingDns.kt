package app.yomu.core.network

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/** A DNS-over-HTTPS endpoint the user may pick; [url] is the JSON API of a resolver that supports it. */
enum class DohProvider(val url: String) {
    OFF(""),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query"),
    GOOGLE("https://dns.google/dns-query"),
    ADGUARD("https://dns.adguard-dns.com/dns-query"),
    ;

    companion object {
        fun of(name: String?): DohProvider = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * A [Dns] that resolves through the chosen [DohProvider] while it is on.
 *
 * Three things keep this from breaking a working app:
 * - the choice is read per lookup, so switching the setting needs no new client (the user-agent does the same);
 * - names that are not public — an address literal, a bare host name, `.local`/`.lan`/an internal suffix — go to the
 *   system resolver, because a public resolver cannot know them and a server on the LAN must keep working;
 * - when the endpoint itself fails, the system resolver answers instead of failing the request.
 */
class ResolvingDns(
    private val provider: () -> DohProvider,
    private val client: () -> OkHttpClient,
) : Dns {
    private var built: DnsOverHttps? = null
    private var builtFor: DohProvider? = null

    override fun lookup(hostname: String): List<InetAddress> {
        val chosen = provider()
        if (chosen == DohProvider.OFF || !isPublicName(hostname)) return Dns.SYSTEM.lookup(hostname)
        return runCatching { dnsFor(chosen).lookup(hostname) }
            .getOrElse { Dns.SYSTEM.lookup(hostname) }
    }

    @Synchronized
    private fun dnsFor(chosen: DohProvider): DnsOverHttps {
        built?.takeIf { builtFor == chosen }?.let { return it }
        val dns = DnsOverHttps.Builder()
            .client(client())
            .url(chosen.url.toHttpUrl())
            .build()
        built = dns
        builtFor = chosen
        return dns
    }

    /**
     * True only for public DNS names. An address literal, a single-label host name and anything under a private
     * suffix (a NAS, a printer, an intranet) are the user's own network: resolved locally, never sent to a third party.
     */
    fun isPublicName(hostname: String): Boolean {
        val name = hostname.trim().trimEnd('.').lowercase()
        if (name.isEmpty() || name.contains(':')) return false
        if (name.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return false
        if (!name.contains('.')) return false
        return name.split('.').none { it in PRIVATE_SUFFIXES }
    }

    private companion object {
        val PRIVATE_SUFFIXES = setOf("local", "lan", "home", "internal", "intranet", "corp", "localdomain", "arpa")
    }
}
