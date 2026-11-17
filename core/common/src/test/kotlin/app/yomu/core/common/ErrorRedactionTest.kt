package app.yomu.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Secrets never reach the local error journal, whatever a request or an answer put into a message. */
class ErrorRedactionTest {
    private fun redacted(text: String) = ErrorRedaction.redact(text)

    @Test
    fun `an authorization header is masked with its scheme`() {
        val text = "Request{Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secret.payload}"
        val out = redacted(text)
        assertFalse(out.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(out.contains("Authorization: [redacted]"))
    }

    @Test
    fun `basic credentials are masked`() {
        val out = redacted("Authorization: Basic dXNlcjpwYXNzd29yZA==")
        assertFalse(out.contains("dXNlcjpwYXNzd29yZA"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun `cookies are masked`() {
        val out = redacted("Cookie: session=abc123; token=xyz\nSet-Cookie: auth=zzz")
        assertFalse(out.contains("abc123") || out.contains("xyz") || out.contains("zzz"))
        assertTrue(out.contains("Cookie: [redacted]"))
        assertTrue(out.contains("Set-Cookie: [redacted]"))
    }

    @Test
    fun `query credentials are masked and the rest of the address stays`() {
        val out = redacted("GET https://example.org/opds?page=2&token=supersecret&api_key=k3y")
        assertFalse(out.contains("supersecret"))
        assertFalse(out.contains("k3y"))
        assertTrue(out.contains("page=2"))
        assertTrue(out.contains("token=[redacted]"))
        assertTrue(out.contains("api_key=[redacted]"))
    }

    @Test
    fun `form and JSON credentials are masked`() {
        val out = redacted("""{"username":"reader","password":"hunter2"} access_token=abc.def&refresh_token=zzz""")
        assertFalse(out.contains("hunter2"))
        assertFalse(out.contains("abc.def"))
        assertFalse(out.contains("zzz"))
        assertTrue(out.contains("\"password\":\"[redacted]\""))
        assertTrue(out.contains("access_token=[redacted]"))
    }

    @Test
    fun `a bearer token without a header is masked`() {
        val out = redacted("token was sent as bearer abcdef0123456789")
        assertFalse(out.contains("abcdef0123456789"))
        assertTrue(out.contains("bearer [redacted]"))
    }

    @Test
    fun `ordinary text is left alone`() {
        val text = "Update check of Solo Leveling failed: HTTP 503 after 2 attempts"
        assertEquals(text, redacted(text))
    }

    @Test
    fun `an empty message stays empty`() {
        assertEquals("", redacted(""))
    }
}
