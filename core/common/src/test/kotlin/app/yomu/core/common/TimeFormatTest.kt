package app.yomu.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** The clock setting changes only the time of day; dates and unknown values keep the locale's behaviour. */
class TimeFormatTest {
    private val at = Instant.parse("2025-01-02T15:04:00Z").toEpochMilli()
    private val utc = ZoneOffset.UTC

    @Test
    fun `system follows the locale and unknown values fall back to it`() {
        val system = TimeFormat.format(TimeFormat.SYSTEM, Locale.US, utc, at)
        assertEquals("3:04 PM", system)
        assertEquals(system, TimeFormat.format("something-old", Locale.US, utc, at))
        assertNull(TimeFormat.clock(TimeFormat.SYSTEM, Locale.US))
    }

    @Test
    fun `forced clocks use the requested pattern`() {
        assertEquals("03:04 PM", TimeFormat.format(TimeFormat.HOUR_12, Locale.US, utc, at))
        assertEquals("15:04", TimeFormat.format(TimeFormat.HOUR_24, Locale.US, utc, at))
        // The digits of the 24-hour clock do not depend on the locale's own default.
        assertEquals("15:04", TimeFormat.format(TimeFormat.HOUR_24, Locale.forLanguageTag("ru"), utc, at))
    }

    @Test
    fun `date and time keeps the locale date and changes only the clock`() {
        val system = TimeFormat.formatDateTime(TimeFormat.SYSTEM, Locale.US, utc, at)
        val h12 = TimeFormat.formatDateTime(TimeFormat.HOUR_12, Locale.US, utc, at)
        val h24 = TimeFormat.formatDateTime(TimeFormat.HOUR_24, Locale.US, utc, at)
        assertEquals("1/2/25, 3:04 PM", system)
        assertTrue(h12.startsWith("1/2/25, "))
        assertTrue(h24.startsWith("1/2/25, "))
        assertTrue(h12.endsWith("03:04 PM"))
        assertTrue(h24.endsWith("15:04"))
    }

    @Test
    fun `am and pm marker is appended in any locale`() {
        val marker = TimeFormat.format(TimeFormat.HOUR_12, Locale.forLanguageTag("ru"), utc, at)
        assertTrue(marker.startsWith("03:04"))
        assertTrue(marker.length > "03:04".length)
    }
}
