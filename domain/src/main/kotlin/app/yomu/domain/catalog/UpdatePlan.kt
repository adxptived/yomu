package app.yomu.domain.catalog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One title in the check plan: when it will be asked and why then. */
data class PlannedCheck(
    val titleId: Long,
    val title: String,
    /** UTC milliseconds of the next planned check; 0 when the title is not checked automatically. */
    val nextCheckAt: Long,
    val reason: UpdateSkip? = null,
)

/** A day of the plan, in the display zone. */
data class PlannedDay(val date: LocalDate, val checks: List<PlannedCheck>)

/**
 * The week view of the check plan.
 *
 * Timestamps are stored and compared in UTC; the grouping is done in the zone the user reads in, so travelling or a
 * daylight-saving change moves a check to another day of the *display* without touching the stored time. The plan is a
 * plan: WorkManager decides the actual minute, and a source's release date is published separately.
 */
object UpdatePlan {
    /**
     * Days of [days] starting at [weekStart] (a local date), each with the checks due that day, sorted by time.
     * Checks outside the window are left out; titles without a planned time are not shown as a day.
     */
    fun week(checks: List<PlannedCheck>, weekStart: LocalDate, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): List<PlannedDay> {
        val start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = weekStart.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val sorted = checks.filter { it.nextCheckAt in start until end }.sortedBy { it.nextCheckAt }
        val byDay = sorted.groupBy { Instant.ofEpochMilli(it.nextCheckAt).atZone(zone).toLocalDate() }
        return (0 until days).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            PlannedDay(date, byDay[date].orEmpty())
        }
    }

    /** The day of the next check for [titleId], or null when it has no planned time. */
    fun dayOf(check: PlannedCheck, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        if (check.nextCheckAt <= 0) null else Instant.ofEpochMilli(check.nextCheckAt).atZone(zone).toLocalDate()

    /** A short, honest sentence about what the plan is. */
    fun isScheduleNote(): String = "This is a plan of checks, not a release schedule"
}
