package app.yomu.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UpdatePlanTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun check(id: Long, at: Long, reason: UpdateSkip? = UpdateSkip.INTERVAL) = PlannedCheck(id, "Title $id", at, reason)

    @Test
    fun `checks are grouped by local day and sorted by time`() {
        val monday = LocalDate.parse("2024-06-03")
        val checks = listOf(
            check(1, at("2024-06-04", "18:00")),
            check(2, at("2024-06-03", "09:00")),
            check(3, at("2024-06-03", "07:30")),
        )
        val week = UpdatePlan.week(checks, monday, days = 7, zone = zone)
        assertEquals(7, week.size)
        assertEquals(monday, week.first().date)
        assertEquals(listOf(3L, 2L), week.first().checks.map { it.titleId })
        assertEquals(listOf(1L), week[1].checks.map { it.titleId })
        assertTrue(week.drop(2).all { it.checks.isEmpty() })
    }

    @Test
    fun `a daylight-saving change keeps a check on its local day`() {
        // 2024-03-31: clocks jump from 02:00 to 03:00 in this zone.
        val sunday = LocalDate.parse("2024-03-31")
        val checks = listOf(
            check(1, at("2024-03-31", "01:30")),
            check(2, at("2024-03-31", "03:30")),
        )
        val day = UpdatePlan.week(checks, sunday, days = 1, zone = zone).single()
        assertEquals(listOf(1L, 2L), day.checks.map { it.titleId })
        assertEquals(sunday, UpdatePlan.dayOf(day.checks.first(), zone))
    }

    @Test
    fun `a check outside the week is left out`() {
        val monday = LocalDate.parse("2024-06-03")
        val before = at("2024-06-02", "23:59")
        val after = at("2024-06-10", "00:00")
        val week = UpdatePlan.week(listOf(check(1, before), check(2, after)), monday, days = 7, zone = zone)
        assertTrue(week.all { it.checks.isEmpty() })
    }

    @Test
    fun `an unplanned title has no day`() {
        assertNull(UpdatePlan.dayOf(check(1, 0), zone))
    }

    @Test
    fun `the note says it is a plan of checks`() {
        assertTrue(UpdatePlan.isScheduleNote().contains("plan of checks"))
        assertTrue(!UpdatePlan.isScheduleNote().contains("release date", ignoreCase = true))
    }
}
