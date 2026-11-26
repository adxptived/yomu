package app.yomu.widget

import app.yomu.domain.model.ReadingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakWidgetTest {
    private val zone = ZoneId.of("UTC")
    private val day = 86_400_000L

    @Test
    fun emptyHistoryHasNoNumbers() {
        val streak = StreakWidgets.summarize(emptyList(), now = 0, zone = zone)

        assertFalse(streak.hasSessions)
        assertEquals(0, streak.days)
        assertEquals(0, streak.todayMs)
    }

    @Test
    fun threeDaysInARowAreCountedAndTodayIsSeparate() {
        val first = LocalDate.of(2026, 11, 24).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = first + 2 * day + 12 * 3_600_000L
        val sessions = listOf(
            ReadingSession(bookId = 1, startedAt = first, durationMs = 30 * 60_000L),
            ReadingSession(bookId = 1, startedAt = first + day, durationMs = 20 * 60_000L),
            ReadingSession(bookId = 1, startedAt = first + 2 * day, durationMs = 10 * 60_000L),
        )

        val streak = StreakWidgets.summarize(sessions, now = now, zone = zone)

        assertTrue(streak.hasSessions)
        assertEquals(3, streak.days)
        assertEquals(10 * 60_000L, streak.todayMs)
    }

    @Test
    fun aGapYesterdayKeepsOnlyToday() {
        val today = LocalDate.of(2026, 11, 26).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            ReadingSession(bookId = 1, startedAt = today - 2 * day, durationMs = 5 * 60_000L),
            ReadingSession(bookId = 1, startedAt = today, durationMs = 15 * 60_000L),
        )

        val streak = StreakWidgets.summarize(sessions, now = today + 60_000, zone = zone)

        assertEquals(1, streak.days)
    }
}
