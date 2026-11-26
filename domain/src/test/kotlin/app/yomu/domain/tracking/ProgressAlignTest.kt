package app.yomu.domain.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** D2: 12 local / 15 AniList / 10 MAL ends as 15 everywhere comparable, and never guesses. */
class ProgressAlignTest {
    private fun p(key: String, chapters: Float, pending: Boolean = false, whole: Boolean = true) =
        ProgressParticipant(key, chapters, pending, whole)

    @Test
    fun theFurthestValueMovesTheLaggingParticipants() {
        val plan = ProgressAlign.plan(p("local", 12f), listOf(p("anilist", 15f), p("mal", 10f)))
        assertEquals(15f, plan.target)
        assertEquals(listOf("mal"), plan.changes.map { it.key })
        assertEquals(15f, plan.applyLocal)
        assertNull(plan.deferred)
    }

    @Test
    fun equalValuesWriteNothing() {
        val plan = ProgressAlign.plan(p("local", 15f), listOf(p("anilist", 15f), p("mal", 15f)))
        assertEquals(15f, plan.target)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun onlyForwards() {
        val plan = ProgressAlign.plan(p("local", 20f), listOf(p("anilist", 15f)))
        assertEquals(20f, plan.target)
        assertEquals(listOf(ProgressChange("anilist", 15f, 20f)), plan.changes)
    }

    @Test
    fun aPendingChangeIsCountedButNotSentAgain() {
        val plan = ProgressAlign.plan(p("local", 12f), listOf(p("anilist", 18f, pending = true), p("mal", 10f)))
        assertEquals(18f, plan.target)
        assertEquals(listOf("mal"), plan.changes.map { it.key })
        assertEquals(listOf(ProgressNote.PENDING), plan.notes["anilist"])
    }

    @Test
    fun aFractionIsRoundedDownForWholeChapterServices() {
        val plan = ProgressAlign.plan(p("local", 12.6f), listOf(p("mal", 10f)))
        assertEquals(12f, plan.target)
        assertEquals(12f, plan.changes.single { it.key == "mal" }.to)
        assertNull(plan.applyLocal)
        assertTrue(plan.notes["local"]?.contains(ProgressNote.FRACTIONAL) == true)
    }

    @Test
    fun aChapterTheSourceLacksIsShownButNotApplied() {
        val plan = ProgressAlign.plan(p("local", 12f), listOf(p("anilist", 15f)), localHasChapter = { it <= 12f })
        assertEquals(15f, plan.target)
        assertNull(plan.applyLocal)
        assertEquals(15f, plan.deferred)
        assertEquals(listOf(ProgressNote.NOT_IN_SOURCE), plan.notes["local"])
        // The service keeps its own 15: it is not sent backwards either.
        assertTrue(plan.changes.none { it.key == "anilist" })
    }

    @Test
    fun aNegativeOrUnknownValueIsIgnoredEntirely() {
        val plan = ProgressAlign.plan(p("local", 5f), listOf(p("anilist", -1f), p("mal", Float.NaN)))
        assertEquals(5f, plan.target)
        assertTrue(plan.changes.isEmpty())
    }

    @Test
    fun unknownNumberingChangesNothing() {
        val plan = ProgressAlign.plan(p("local", 0f), listOf(p("anilist", 0f)))
        assertEquals(0f, plan.target)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun wholeChapterServicesGetWholeValuesOnly() {
        assertEquals(14f, ProgressAlign.valueFor(p("mal", 0f), 14.9f))
        assertEquals(14.9f, ProgressAlign.valueFor(p("local", 0f, whole = false), 14.9f))
    }
}
