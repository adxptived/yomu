package app.yomu.domain.catalog

import app.yomu.domain.model.ChapterInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterNavigationTest {
    private fun chapter(
        id: Long,
        number: Float,
        group: String? = "A",
        read: Boolean = false,
        sourceOrder: Int = id.toInt(),
        started: Int = 0,
    ) = ChapterInfo(
        id = id, entryId = 1, url = "/c$id", name = "Chapter $number", number = number,
        scanlator = group, uploadDate = 0, read = read, bookmarked = false, lastPageRead = started,
        downloaded = false, localPath = null, sourceOrder = sourceOrder, isNew = false,
    )

    @Test
    fun `next skips another release of the same chapter`() {
        val order = ChapterOrder.reading(listOf(chapter(1, 1f), chapter(2, 2f, group = "B"), chapter(3, 2f, group = "A"), chapter(4, 3f)))
        val next = ChapterNavigation.resolve(order, currentId = 1, ChapterIntent.NEXT)
        assertEquals(2f, next?.number)
        assertTrue(next!!.id in setOf(2L, 3L))
    }

    @Test
    fun `without duplicate skipping the very next release is the target`() {
        val order = listOf(chapter(1, 1f), chapter(2, 2f, group = "B"), chapter(3, 2f, group = "A"))
        val next = ChapterNavigation.resolve(order, 1, ChapterIntent.NEXT, ChapterNavigationPolicy(skipDuplicateReleases = false))
        assertEquals(2L, next?.id)
    }

    @Test
    fun `the previous chapter is symmetric`() {
        val order = ChapterOrder.reading(listOf(chapter(1, 1f), chapter(2, 2f), chapter(3, 3f)))
        assertEquals(2L, ChapterNavigation.resolve(order, 3, ChapterIntent.PREVIOUS)?.id)
        assertNull(ChapterNavigation.resolve(order, 1, ChapterIntent.PREVIOUS))
    }

    @Test
    fun `excluded groups are never chosen`() {
        val order = listOf(chapter(1, 1f, group = "A"), chapter(2, 2f, group = "B"), chapter(3, 3f, group = "A"))
        val policy = ChapterNavigationPolicy(excludedScanlators = setOf("B"))
        assertEquals(3L, ChapterNavigation.resolve(order, 1, ChapterIntent.NEXT, policy)?.id)
        assertEquals(1L, ChapterNavigation.resolve(order, 3, ChapterIntent.PREVIOUS, policy)?.id)
    }

    @Test
    fun `resume continues after the last read chapter`() {
        val order = ChapterOrder.reading(listOf(chapter(1, 1f, read = true), chapter(2, 2f), chapter(3, 3f)))
        assertEquals(2L, ChapterNavigation.resolve(order, null, ChapterIntent.RESUME)?.id)
        val allRead = ChapterOrder.reading(listOf(chapter(1, 1f, read = true), chapter(2, 2f, read = true)))
        // Everything read: the last chapter, as before.
        assertEquals(2L, ChapterNavigation.resolve(allRead, null, ChapterIntent.RESUME)?.id)
    }

    @Test
    fun `a started chapter is resumed before an untouched later one`() {
        val order = ChapterOrder.reading(
            listOf(chapter(1, 1f, read = true), chapter(2, 2f, started = 5), chapter(3, 3f)),
        )
        assertEquals(2L, ChapterNavigation.resolve(order, null, ChapterIntent.RESUME)?.id)
    }

    @Test
    fun `alternative releases are marked only when the numbering is reliable`() {
        val reliable = ChapterOrder.reading(listOf(chapter(1, 1f, group = "A"), chapter(2, 1f, group = "B"), chapter(3, 2f, group = "A")))
        assertEquals(listOf(2L), ChapterNavigation.alternativeReleases(reliable, 1))

        // The same number three times in one group: the numbering restarts, so an equal number is another chapter.
        val restarted = ChapterOrder.reading(
            listOf(chapter(10, 1f, group = "A"), chapter(11, 2f, group = "A"), chapter(12, 1f, group = "A"), chapter(13, 2f, group = "A")),
        )
        assertTrue(ChapterNavigation.alternativeReleases(restarted, 10).isEmpty())
    }

    @Test
    fun `an unnumbered chapter has no alternatives`() {
        val order = ChapterOrder.reading(listOf(chapter(1, -1f, group = "A"), chapter(2, -1f, group = "B")))
        assertTrue(ChapterNavigation.alternativeReleases(order, 1).isEmpty())
    }

    @Test
    fun `a reliable sequence reports the missing numbers`() {
        val order = ChapterOrder.reading(listOf(chapter(1, 1f), chapter(2, 2f), chapter(3, 5f)))
        assertEquals(listOf(3f, 4f), ChapterNavigation.missingNumbers(order))
    }

    @Test
    fun `a decimal number is not a gap`() {
        val order = ChapterOrder.reading(listOf(chapter(1, 1f), chapter(2, 1.5f), chapter(3, 2f)))
        assertEquals(emptyList<Float>(), ChapterNavigation.missingNumbers(order))
    }

    @Test
    fun `an unreliable numbering does not guess gaps`() {
        val order = ChapterOrder.reading(
            listOf(chapter(1, 1f, group = "A"), chapter(2, 3f, group = "A"), chapter(3, 1f, group = "A"), chapter(4, 3f, group = "A")),
        )
        assertNull(ChapterNavigation.missingNumbers(order))
    }
}
