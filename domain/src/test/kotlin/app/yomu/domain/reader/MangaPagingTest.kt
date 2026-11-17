package app.yomu.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaPagingTest {
    @Test
    fun `a cover stays on its own and the rest form pairs`() {
        val groups = MangaPaging.spreads(5).map { it.toList() }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), groups)
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), MangaPaging.spreads(5, coverAlone = true).map { it.toList() })
    }

    @Test
    fun `without a cover the pairs start at the first page`() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), MangaPaging.spreads(5, coverAlone = false).map { it.toList() })
    }

    @Test
    fun `an odd last page is alone`() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), MangaPaging.spreads(4).map { it.toList() })
        assertEquals(listOf(listOf(0)), MangaPaging.spreads(1).map { it.toList() })
        assertTrue(MangaPaging.spreads(0).isEmpty())
    }

    @Test
    fun `every page belongs to exactly one group`() {
        (1..20).forEach { count ->
            listOf(true, false).forEach { cover ->
                val pages = MangaPaging.spreads(count, cover).flatMap { it.toList() }.sorted()
                assertEquals((0 until count).toList(), pages, "count=$count cover=$cover")
            }
        }
    }

    @Test
    fun `the group of a page and its first page`() {
        assertEquals(2, MangaPaging.spreadIndexOf(5, 3))
        assertEquals(0, MangaPaging.spreadIndexOf(5, 0))
        assertEquals(-1, MangaPaging.spreadIndexOf(5, 9))
        assertEquals(3, MangaPaging.spreadFirstPage(5, 2))
        assertEquals(-1, MangaPaging.spreadFirstPage(5, 9))
    }

    @Test
    fun `a split view shows two halves per physical page in the chosen direction`() {
        val ltr = MangaPaging.virtualPages(2, PageSplit.LEFT_TO_RIGHT)
        assertEquals(listOf(MangaPage(0, 0), MangaPage(0, 1), MangaPage(1, 0), MangaPage(1, 1)), ltr)
        val rtl = MangaPaging.virtualPages(1, PageSplit.RIGHT_TO_LEFT)
        assertEquals(listOf(MangaPage(0, 1), MangaPage(0, 0)), rtl)
        assertTrue(MangaPaging.virtualPages(0, PageSplit.LEFT_TO_RIGHT).isEmpty())
    }

    @Test
    fun `without a split the slot is the page`() {
        val pages = MangaPaging.virtualPages(4, PageSplit.OFF)
        assertEquals(listOf(0, 1, 2, 3), pages.map { it.physical })
        assertEquals(2, MangaPaging.virtualIndexOf(pages, 2, PageSplit.OFF))
        assertEquals(2, MangaPaging.physicalPage(pages, 2))
    }

    @Test
    fun `a restored position opens the half its direction starts with`() {
        val ltr = MangaPaging.virtualPages(3, PageSplit.LEFT_TO_RIGHT)
        assertEquals(2, MangaPaging.virtualIndexOf(ltr, 1, PageSplit.LEFT_TO_RIGHT))
        assertEquals(1, MangaPaging.physicalPage(ltr, 2))
        val rtl = MangaPaging.virtualPages(3, PageSplit.RIGHT_TO_LEFT)
        assertEquals(2, MangaPaging.virtualIndexOf(rtl, 1, PageSplit.RIGHT_TO_LEFT))
        assertEquals(1, MangaPaging.physicalPage(rtl, 2))
    }

    @Test
    fun `an unknown page falls back to the first slot, never a crash`() {
        val pages = MangaPaging.virtualPages(2, PageSplit.LEFT_TO_RIGHT)
        assertEquals(0, MangaPaging.virtualIndexOf(pages, 99, PageSplit.LEFT_TO_RIGHT))
        assertEquals(0, MangaPaging.physicalPage(emptyList(), 4))
    }
}
