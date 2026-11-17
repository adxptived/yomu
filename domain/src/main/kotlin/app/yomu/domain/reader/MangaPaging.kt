package app.yomu.domain.reader

import kotlinx.serialization.Serializable

/**
 * How a tall or wide scan is divided into what the screen shows. The split is a *view* transform: the physical page a
 * position belongs to never changes, so a saved position and a tracker page stay correct whatever the split does.
 */
@Serializable
enum class PageSplit { OFF, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

/** One thing on screen: a whole physical page, or one half of it (0 = the first half in reading order). */
data class MangaPage(val physical: Int, val half: Int = 0) {
    val isSecondHalf: Boolean get() = half != 0
}

/**
 * The pages and spreads of the manga reader.
 *
 * Dual pages are a property of the *window* (a landscape phone, a tablet), not of the book: [spreads] produces the
 * groups the pager shows, while [MangaPage] produces the physical pages when a scan is split. Both keep the first
 * visible item and the physical page number of every position, which is what the saved locator stores.
 */
object MangaPaging {
    /**
     * Page groups for a dual-page window.
     *
     * A cover is usually a page of its own (it is not the left half of a two-page spread); [coverAlone] keeps that,
     * and turning it off makes the first pair start at page 0 — a separate choice from "dual pages", as the plan
     * requires.
     */
    fun spreads(pageCount: Int, coverAlone: Boolean = true): List<IntArray> {
        if (pageCount <= 0) return emptyList()
        val out = mutableListOf<IntArray>()
        var i = 0
        if (coverAlone) {
            out += intArrayOf(0)
            i = 1
        }
        while (i < pageCount) {
            out += if (i + 1 < pageCount) intArrayOf(i, i + 1) else intArrayOf(i)
            i += 2
        }
        return out
    }

    /** The group index that holds [page], or -1 when the page is outside the range. */
    fun spreadIndexOf(pageCount: Int, page: Int, coverAlone: Boolean = true): Int {
        if (page !in 0 until pageCount) return -1
        return spreads(pageCount, coverAlone).indexOfFirst { page in it }
    }

    /** The first physical page of group [index]; -1 for a missing group. */
    fun spreadFirstPage(pageCount: Int, index: Int, coverAlone: Boolean = true): Int =
        spreads(pageCount, coverAlone).getOrNull(index)?.firstOrNull() ?: -1

    /**
     * What the pager shows when a scan is split: two entries per physical page instead of one. A split is visual only —
     * [physicalPage] maps back, so progress, a tracker page and a bookmark are still about the real page.
     */
    fun virtualPages(pageCount: Int, split: PageSplit): List<MangaPage> {
        if (pageCount <= 0) return emptyList()
        if (split == PageSplit.OFF) return (0 until pageCount).map { MangaPage(it) }
        return (0 until pageCount).flatMap { page ->
            if (split == PageSplit.RIGHT_TO_LEFT) listOf(MangaPage(page, 1), MangaPage(page, 0))
            else listOf(MangaPage(page, 0), MangaPage(page, 1))
        }
    }

    /**
     * The slot of [physicalPage] in the split view: the first half for a left-to-right split, the second half for a
     * right-to-left one, so a restored position opens the half a reader of that direction starts with.
     */
    fun virtualIndexOf(pages: List<MangaPage>, physicalPage: Int, split: PageSplit): Int {
        if (pages.isEmpty()) return 0
        return pages.indexOfFirst { it.physical == physicalPage && it.half == (if (split == PageSplit.RIGHT_TO_LEFT) 1 else 0) }
            .takeIf { it >= 0 }
            ?: pages.indexOfFirst { it.physical == physicalPage }.takeIf { it >= 0 }
            ?: 0
    }

    /** The physical page shown at a split slot; 0 when the list is empty. */
    fun physicalPage(pages: List<MangaPage>, index: Int): Int = pages.getOrNull(index)?.physical ?: 0
}
