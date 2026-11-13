package app.yomu.data.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeAuthTest {
    private val session = "YSC=abc; VISITOR_INFO1_LIVE=xyz; SAPISID=SapisidValue; HSID=hsid"

    @Test
    fun sessionCookieIsRecognized() {
        assertTrue(YouTubeAuth.isSignedIn(session))
        assertTrue(YouTubeAuth.isSignedIn("__Secure-3PAPISID=three"))
        // A bare SID is a Google session but not enough to sign an InnerTube call.
        assertFalse(YouTubeAuth.isSignedIn("SID=sid; PREF=f6=400"))
        assertFalse(YouTubeAuth.isSignedIn("__Secure-3PSID=three"))
        assertFalse(YouTubeAuth.isSignedIn("YSC=abc; PREF=f6=400"))
        assertFalse(YouTubeAuth.isSignedIn(""))
        assertFalse(YouTubeAuth.isSignedIn("SAPISID=; SID=deleted"))
    }

    @Test
    fun cookiesAreParsedByExactName() {
        assertEquals("SapisidValue", YouTubeAuth.cookie(session, "SAPISID"))
        assertNull(YouTubeAuth.cookie(session, "SID"))
        assertNull(YouTubeAuth.cookie("__Secure-3PSID=x", "SID"))
    }

    @Test
    fun authorizationSignsTheSession() {
        assertEquals(
            "SAPISIDHASH 1700000000_110cc8d2a3c1b892496a1c812939bbf6a14e42b6",
            YouTubeAuth.authorization(session, 1_700_000_000),
        )
        // The secure variant is the fallback when the plain cookie is absent.
        assertEquals(
            "SAPISIDHASH 1700000000_110cc8d2a3c1b892496a1c812939bbf6a14e42b6",
            YouTubeAuth.authorization("__Secure-3PAPISID=SapisidValue", 1_700_000_000),
        )
    }

    @Test
    fun anonymousCallsCarryNoSignature() {
        assertNull(YouTubeAuth.authorization("YSC=abc", 1_700_000_000))
        assertTrue(YouTubeAuth.headers(null, null).isEmpty())
    }

    @Test
    fun headersIncludeOriginAndVisitor() {
        val headers = YouTubeAuth.headers("SAPISIDHASH 1_hash", "visitor")
        assertEquals("SAPISIDHASH 1_hash", headers["Authorization"])
        assertEquals("https://www.youtube.com", headers["X-Origin"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("visitor", headers["X-Goog-Visitor-Id"])
    }
}
