package app.yomu.source

import app.yomu.source.RetryAfter.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RetryAfterTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `seconds are a delay`() {
        assertEquals(30_000L, parse("30", now))
        assertEquals(0L, parse("0", now))
    }

    @Test
    fun `an http date becomes the remaining time`() {
        // now is 2023-11-14T22:13:20Z; one minute later.
        assertEquals(60_000L, parse("Tue, 14 Nov 2023 22:14:20 GMT", now))
        // A date in the past means "now", not a negative wait.
        assertEquals(0L, parse("Tue, 14 Nov 2023 22:13:20 GMT", now))
    }

    @Test
    fun `a missing or malformed value gives no advice`() {
        assertNull(parse(null, now))
        assertNull(parse("", now))
        assertNull(parse("  ", now))
        assertNull(parse("-5", now))
        assertNull(parse("soon", now))
    }

    @Test
    fun `an absurd wait is capped`() {
        assertEquals(RetryAfter.MAX_MS, parse("999999999", now))
    }
}
