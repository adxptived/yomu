package app.yomu.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class UpdatePolicyTest {
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 1_700_000_000_000L

    private fun title(
        status: String = "READING",
        unread: Int = 3,
        shelves: Set<Long> = emptySet(),
        lastAttempt: Long = 0,
        lastSuccess: Long = 0,
        intervalOverride: Int? = null,
        excluded: Boolean = false,
    ) = UpdateTitleState(status, unread, 10, shelves, lastAttempt, lastSuccess, intervalOverride, excluded)

    @Test
    fun `a title never checked is due`() {
        assertTrue(UpdatePolicy.decide(title(), now, intervalHours = 6).eligible)
    }

    @Test
    fun `a healthy title waits for its interval`() {
        val decision = UpdatePolicy.decide(title(lastSuccess = now - 2 * hour), now, intervalHours = 6)
        assertFalse(decision.eligible)
        assertEquals(UpdateSkip.INTERVAL, decision.reason)
        assertEquals(now + 4 * hour, decision.nextCheckAt)
    }

    @Test
    fun `an attempt that failed is retried when the interval passes, not sooner`() {
        val lastAttempt = now - 7 * hour
        assertTrue(UpdatePolicy.decide(title(lastAttempt = lastAttempt, lastSuccess = now - 30 * hour), now, intervalHours = 6).eligible)
        // The attempt counts even without a success: a failing source is not asked on every pass.
        val recent = UpdatePolicy.decide(title(lastAttempt = now - 1 * hour, lastSuccess = 0), now, intervalHours = 6)
        assertFalse(recent.eligible)
    }

    @Test
    fun `a missing source is never asked`() {
        val decision = UpdatePolicy.decide(title(), now, 6, sourceAvailable = false)
        assertEquals(UpdateSkip.NO_SOURCE, decision.reason)
        // Even a manual check cannot fix a removed source.
        assertEquals(UpdateSkip.NO_SOURCE, UpdatePolicy.decide(title(), now, 6, sourceAvailable = false, force = true).reason)
    }

    @Test
    fun `an excluded shelf wins over everything else`() {
        val decision = UpdatePolicy.decide(title(shelves = setOf(7)), now, 6, excludedShelves = setOf(7), force = true)
        assertEquals(UpdateSkip.EXCLUDED_SHELF, decision.reason)
    }

    @Test
    fun `restrictions select titles by what they are`() {
        val unreadOnly = setOf(UpdateRestriction.UNREAD)
        assertTrue(UpdatePolicy.decide(title(status = "NEW", unread = 5), now, 6, restrictions = unreadOnly).eligible)
        assertFalse(UpdatePolicy.decide(title(status = "FINISHED", unread = 0), now, 6, restrictions = unreadOnly).eligible)
        assertTrue(UpdatePolicy.decide(title(status = "FINISHED", unread = 0), now, 6, restrictions = setOf(UpdateRestriction.COMPLETED)).eligible)
        assertTrue(UpdatePolicy.decide(title(status = "ON_HOLD"), now, 6, restrictions = setOf(UpdateRestriction.STARTED)).eligible)
        assertEquals(UpdateSkip.RESTRICTION, UpdatePolicy.decide(title(status = "NEW", unread = 0), now, 6, restrictions = setOf(UpdateRestriction.STARTED)).reason)
        // No restrictions means every title.
        assertTrue(UpdatePolicy.decide(title(status = "PLAN_TO_READ", unread = 0), now, 6, restrictions = emptySet()).eligible)
    }

    @Test
    fun `a manual check ignores the interval and the restrictions`() {
        val decision = UpdatePolicy.decide(
            title(status = "FINISHED", unread = 0, lastSuccess = now - hour), now, 6,
            restrictions = setOf(UpdateRestriction.STARTED), force = true,
        )
        assertTrue(decision.eligible)
        assertNull(decision.reason)
    }

    @Test
    fun `a stable upload rhythm stretches the interval`() {
        val weekly = listOf(now - 7 * 24 * hour, now - 14 * 24 * hour, now - 21 * 24 * hour)
        // A weekly title is checked weekly, not every six hours.
        val decision = UpdatePolicy.decide(title(lastSuccess = now - 2 * 24 * hour), now, intervalHours = 6, uploadDates = weekly)
        assertFalse(decision.eligible)
        assertEquals(7 * 24 * hour, UpdatePolicy.estimateIntervalMs(weekly, now))
    }

    @Test
    fun `the configured interval is never shortened by the prediction`() {
        val daily = listOf(now - hour, now - 2 * hour, now - 3 * hour)
        // One hour between uploads does not make the app check every hour when the user asked for six.
        assertEquals(6 * hour, UpdatePolicy.effectiveIntervalMs(6, daily, now))
    }

    @Test
    fun `too few or bad dates fall back to the configured interval`() {
        assertEquals(6 * hour, UpdatePolicy.effectiveIntervalMs(6, emptyList(), now))
        assertEquals(6 * hour, UpdatePolicy.effectiveIntervalMs(6, listOf(now - hour, now - 2 * hour), now))
        assertNull(UpdatePolicy.estimateIntervalMs(listOf(now - hour), now))
        // Future dates are not evidence of a rhythm.
        assertNull(UpdatePolicy.estimateIntervalMs(listOf(now + hour, now + 2 * hour, now + 3 * hour), now))
    }

    @Test
    fun `one delayed chapter does not stretch the rhythm`() {
        // Three weekly uploads and one gap of a month: the median gap stays weekly.
        val dates = listOf(now - 30 * 24 * hour, now - 37 * 24 * hour, now - 44 * 24 * hour, now - 51 * 24 * hour)
        assertEquals(7 * 24 * hour, UpdatePolicy.estimateIntervalMs(dates, now))
    }

    @Test
    fun `a title's own interval replaces the app-wide one`() {
        val title = title(lastSuccess = now - 8 * hour, intervalOverride = 12)
        val decision = UpdatePolicy.decide(title, now, intervalHours = 6)
        assertFalse(decision.eligible)
        assertEquals(now + 4 * hour, decision.nextCheckAt)
        assertTrue(UpdatePolicy.decide(title(lastSuccess = now - 13 * hour, intervalOverride = 12), now, 6).eligible)
    }

    @Test
    fun `an explicit interval is not stretched by a predicted rhythm`() {
        val weekly = listOf(now - 7 * 24 * hour, now - 14 * 24 * hour, now - 21 * 24 * hour)
        assertEquals(2 * hour, UpdatePolicy.effectiveIntervalMs(6, weekly, now, overrideHours = 2))
    }

    @Test
    fun `a title excluded by the user is never checked automatically`() {
        val decision = UpdatePolicy.decide(title(excluded = true), now, 6, force = true)
        assertEquals(UpdateSkip.EXCLUDED_TITLE, decision.reason)
    }

    @Test
    fun `a shorter title interval pulls the worker window down`() {
        assertEquals(3, UpdatePolicy.schedulerIntervalHours(24, listOf(3, 48)))
        assertEquals(3, UpdatePolicy.schedulerIntervalHours(24, listOf(3)))
        // A longer title choice does not stretch the app-wide window.
        assertEquals(6, UpdatePolicy.schedulerIntervalHours(6, listOf(12, 48)))
        assertEquals(6, UpdatePolicy.schedulerIntervalHours(6, emptyList()))
        // Automatic checks off stays off.
        assertEquals(0, UpdatePolicy.schedulerIntervalHours(0, listOf(3)))
    }

    @Test
    fun `the screen's 3-hour title is reachable by a 24-hour app window`() {
        val title = title(lastSuccess = now - 4 * hour, intervalOverride = 3)
        // The scheduler runs every 3 hours now, and the title is due on that run; the old 24-hour window would skip it.
        assertEquals(3, UpdatePolicy.schedulerIntervalHours(24, listOf(3)))
        assertEquals(3 * hour, UpdatePolicy.effectiveIntervalMs(24, emptyList(), now, overrideHours = 3))
        assertTrue(UpdatePolicy.decide(title, now, intervalHours = 24).eligible)
    }
}
