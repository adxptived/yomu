package app.yomu.domain.reader

/** A place in an online edition: the chapter and the physical page inside it. */
data class ReaderAnchor(val chapterId: Long, val page: Int) {
    init {
        require(page >= 0) { "page must not be negative" }
    }
}

/**
 * The chapters kept open around the place being read.
 *
 * A chapter boundary is the ordinary way to read on, so opening a neighbour must not replace the reader with a loading
 * screen. The window is the set of chapter ids the reader holds at once — the current chapter and its neighbours in
 * reading order — plus the position of the current chapter inside it. It is a pure model: page sources, network and
 * lifetimes stay in the feature layer, and the same window answers the reader, the preloader and the progress writer.
 *
 * [order] is the reading order the caller already applies elsewhere (hidden scanlation groups excluded). The window
 * never reorders it: when the caller moves to another release of the same chapter, the new id is found in the same
 * order and the window simply follows.
 */
class ReaderWindow private constructor(
    val entryId: Long,
    val order: List<Long>,
    val currentIndex: Int,
    val radius: Int,
) {
    /** First chapter index held in memory. */
    val start: Int = (currentIndex - radius).coerceAtLeast(0)

    /** Last chapter index held in memory. */
    val end: Int = (currentIndex + radius).coerceAtMost(order.lastIndex)

    /** The chapters held in memory, in reading order. */
    val ids: List<Long> get() = order.subList(start, end + 1)

    val currentId: Long get() = order[currentIndex]
    val previousId: Long? get() = order.getOrNull(currentIndex - 1)
    val nextId: Long? get() = order.getOrNull(currentIndex + 1)
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < order.lastIndex

    fun contains(chapterId: Long): Boolean = chapterId in ids

    /** Moves the current chapter [delta] steps in reading order; the window follows. */
    fun shift(delta: Int): ReaderWindow = at((currentIndex + delta).coerceIn(0, order.lastIndex))

    /** The window centred on [chapterId], or null when that chapter is not in the reading order. */
    fun moveTo(chapterId: Long): ReaderWindow? {
        val index = order.indexOf(chapterId)
        return if (index < 0) null else at(index)
    }

    private fun at(index: Int): ReaderWindow =
        if (index == currentIndex) this else ReaderWindow(entryId, order, index, radius)

    companion object {
        /** Chapters held at once: the current one plus its two neighbours. */
        const val RADIUS = 1

        /** Opens a window on [chapterId]; null when it is not in [order] or [order] is empty. */
        fun open(entryId: Long, order: List<Long>, chapterId: Long, radius: Int = RADIUS): ReaderWindow? {
            if (order.isEmpty()) return null
            val index = order.indexOf(chapterId)
            if (index < 0) return null
            return ReaderWindow(entryId, order.toList(), index, radius.coerceAtLeast(0))
        }
    }
}

/**
 * Flat page indexes over the loaded chapters of a [ReaderWindow].
 *
 * The session page source flattens chapters into one list so the pager can cross a chapter boundary without a reload;
 * this maps between the flat index, the chapter and the physical page. A chapter that failed to open is simply absent,
 * and every mapping stays correct for the chapters that did.
 */
class ReaderWindowPages(private val slices: List<Slice>) {
    /** One loaded chapter: its id, the flat index of its first page and its page count. */
    data class Slice(val chapterId: Long, val start: Int, val pages: Int) {
        init {
            require(start >= 0) { "start must not be negative" }
            require(pages > 0) { "a loaded chapter has at least one page" }
        }

        val lastIndex: Int get() = start + pages - 1
        fun contains(flatIndex: Int): Boolean = flatIndex in start..lastIndex
        fun pageAt(flatIndex: Int): Int = flatIndex - start
    }

    val total: Int = slices.sumOf { it.pages }

    val all: List<Slice> get() = slices

    /**
     * The slice at [flatIndex]. A page past the end falls back to the last page (the reader must not point at a
     * chapter that is not there); a negative index falls back to the first page.
     */
    fun sliceAt(flatIndex: Int): Slice? = when {
        slices.isEmpty() -> null
        flatIndex < 0 -> slices.first()
        flatIndex >= total -> slices.last()
        else -> slices.firstOrNull { it.contains(flatIndex) } ?: slices.last()
    }

    fun anchorOf(flatIndex: Int): ReaderAnchor? = sliceAt(flatIndex)?.let { slice ->
        ReaderAnchor(slice.chapterId, slice.pageAt(flatIndex).coerceIn(0, slice.pages - 1))
    }

    /** The flat index of [anchor], or null when its chapter is not loaded or the page is outside it. */
    fun locate(anchor: ReaderAnchor): Int? {
        val slice = slices.firstOrNull { it.chapterId == anchor.chapterId } ?: return null
        if (anchor.page !in 0 until slice.pages) return null
        return slice.start + anchor.page
    }

    /** The flat index of the chapter's first page, or null when that chapter is not loaded. */
    fun startOf(chapterId: Long): Int? = slices.firstOrNull { it.chapterId == chapterId }?.start

    /** The last page of the chapter that is loaded before [chapterId], or null. */
    fun endBefore(chapterId: Long): Int? {
        val index = slices.indexOfFirst { it.chapterId == chapterId }
        if (index <= 0) return null
        return slices[index - 1].lastIndex
    }

    fun chapterAt(flatIndex: Int): Long? = sliceAt(flatIndex)?.chapterId

    companion object {
        /** Builds the layout from (chapter, page count) pairs in reading order; chapters with no pages are dropped. */
        fun of(chapters: List<Pair<Long, Int>>): ReaderWindowPages {
            var start = 0
            val slices = chapters.filter { it.second > 0 }.map { (id, pages) ->
                Slice(id, start, pages).also { start += pages }
            }
            return ReaderWindowPages(slices)
        }
    }
}
