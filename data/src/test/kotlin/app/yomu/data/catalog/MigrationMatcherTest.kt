package app.yomu.data.catalog

import app.yomu.domain.model.ChapterInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** MG01: matching never turns a guess into a silent exact match. */
class MigrationMatcherTest {

    private fun chapter(id: Long, number: Float, name: String = "Chapter $number", scanlator: String? = "A", url: String = "old/$id") =
        ChapterInfo(id = id, entryId = 1, url = url, name = name, number = number, scanlator = scanlator)

    private fun target(number: Float, scanlator: String? = "A", url: String = "new/$number", name: String = "Chapter $number") =
        MigrationTargetChapter(url, name, number, scanlator)

    @Test
    fun `number and group is exact and an identical address keeps the page`() {
        val matches = MigrationMatcher.match(listOf(chapter(1, 1f, url = "same/1")), listOf(target(1f, url = "same/1")))
        assertEquals(MigrationConfidence.EXACT, matches.single().confidence)
        assertTrue(matches.single().pagePositionSafe)
    }

    @Test
    fun `a different release matches on the number but does not move the page`() {
        val matches = MigrationMatcher.match(listOf(chapter(1, 1f, scanlator = "A")), listOf(target(1f, scanlator = "B")))
        assertEquals(MigrationConfidence.LIKELY, matches.single().confidence)
        assertFalse(matches.single().pagePositionSafe)
    }

    @Test
    fun `duplicate candidates need review`() {
        val matches = MigrationMatcher.match(
            listOf(chapter(1, 1f)),
            listOf(target(1f, url = "new/a"), target(1f, url = "new/b")),
        )
        assertEquals(MigrationConfidence.REVIEW, matches.single().confidence)
    }

    @Test
    fun `an unnumbered chapter falls back to its title`() {
        val matches = MigrationMatcher.match(
            listOf(chapter(1, -1f, name = "Prologue", scanlator = null)),
            listOf(target(-1f, name = "prologue", scanlator = null)),
        )
        assertEquals(MigrationConfidence.LIKELY, matches.single().confidence)
        assertTrue(matches.single().matched)
    }

    @Test
    fun `a chapter with no counterpart is reported as missing`() {
        val matches = MigrationMatcher.match(listOf(chapter(1, 5f)), listOf(target(1f)))
        assertEquals(MigrationConfidence.NONE, matches.single().confidence)
        assertEquals(null, matches.single().target)
        assertFalse(matches.single().matched)
    }

    @Test
    fun `a negative number is never matched by number`() {
        // Two unnumbered chapters with different titles are different chapters, not one at position -1.
        val matches = MigrationMatcher.match(
            listOf(chapter(1, -1f, name = "Extra", scanlator = null)),
            listOf(target(-1f, name = "Bonus", scanlator = null)),
        )
        assertEquals(MigrationConfidence.NONE, matches.single().confidence)
    }
}
