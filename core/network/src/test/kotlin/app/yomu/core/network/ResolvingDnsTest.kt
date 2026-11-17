package app.yomu.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S06: a private or literal name is never sent to a public resolver. */
class ResolvingDnsTest {
    private val dns = ResolvingDns({ DohProvider.CLOUDFLARE }, { error("not used") })

    @Test
    fun publicHostnamesUseTheChosenResolver() {
        assertTrue(dns.isPublicName("example.org"))
        assertTrue(dns.isPublicName("mangadex.org"))
        assertTrue(dns.isPublicName("a.b.c.example.co.uk"))
    }

    @Test
    fun localNamesStayOnTheSystemResolver() {
        listOf("nas.local", "printer.lan", "server.home", "router.home.arpa", "files.internal", "share.intranet").forEach {
            assertFalse(it, dns.isPublicName(it))
        }
    }

    @Test
    fun literalsAndBareNamesAreNotPublic() {
        listOf("192.168.1.10", "10.0.0.1", "127.0.0.1", "fe80::1", "mypc", "").forEach {
            assertFalse(it, dns.isPublicName(it))
        }
    }

    @Test
    fun theProviderSelectionIsTolerant() {
        assertTrue(DohProvider.of(null) == DohProvider.OFF)
        assertTrue(DohProvider.of("GONE") == DohProvider.OFF)
        assertTrue(DohProvider.of("GOOGLE") == DohProvider.GOOGLE)
    }
}
