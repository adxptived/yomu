package app.yomu.feature.reader.manga

import app.yomu.data.book.PageSource
import app.yomu.domain.reader.ReaderAnchor
import app.yomu.domain.reader.ReaderWindowPages
import java.io.File

/** One chapter loaded into the open reading window. */
class MangaChapter(
    val id: Long,
    val title: String,
    val source: PageSource,
) {
    val count: Int get() = source.count
}

/**
 * The pages of every chapter held in the window, flattened into one [PageSource].
 *
 * Flattening is what lets the pager cross a chapter boundary without closing the reader: the next chapter's first
 * page is just the following index. Chapter identity is kept on the side, so progress, the page chip and a tracker
 * page always describe the physical page of a real chapter, while [close] releases every chapter this session owns.
 *
 * A session is immutable: moving the window builds a new one, and the old session's [close] releases its sources. The
 * loaded chapters are always a bounded set (the [app.yomu.domain.reader.ReaderWindow] radius), never the whole series.
 */
class SessionPageSource(val chapters: List<MangaChapter>) : PageSource {
    private val layout = ReaderWindowPages.of(chapters.map { it.id to it.count })
    private var closed = false

    override val count: Int get() = layout.total

    val slices: List<ReaderWindowPages.Slice> get() = layout.all

    /** The chapter of a flat page, or null when the session is empty. */
    fun chapterAt(index: Int): MangaChapter? {
        val id = layout.chapterAt(index) ?: return null
        return chapters.firstOrNull { it.id == id }
    }

    /** The flat index of [chapterId]'s first page, or null when the chapter is not loaded. */
    fun startOf(chapterId: Long): Int? = layout.startOf(chapterId)

    /** Flat index of a page inside a loaded chapter; null when the chapter is absent or the page is outside it. */
    fun locate(chapterId: Long, page: Int): Int? = layout.locate(ReaderAnchor(chapterId, page))

    /** The page inside its chapter for a flat index. */
    fun pageAt(index: Int): Int = layout.all.firstOrNull { it.contains(index.coerceIn(0, (count - 1).coerceAtLeast(0))) }?.pageAt(index) ?: index

    /** The first flat page of each loaded chapter after the previous one: the chapter separators of the pager. */
    fun chapterStarts(): List<Int> = slices.drop(1).map { it.start }

    /** Flat page index of the last page of every chapter except the last one. */
    fun chapterEnds(): List<Int> = slices.dropLast(1).map { it.lastIndex }

    private fun sliceAt(index: Int): ReaderWindowPages.Slice? = layout.sliceAt(index)

    private fun sourceOf(index: Int): Pair<PageSource, Int>? {
        val slice = sliceAt(index) ?: return null
        val chapter = chapters.firstOrNull { it.id == slice.chapterId } ?: return null
        return chapter.source to (index - slice.start).coerceIn(0, (chapter.count - 1).coerceAtLeast(0))
    }

    override fun name(index: Int): String = sourceOf(index)?.let { (source, local) -> source.name(local) } ?: "${index + 1}"

    override suspend fun file(index: Int): File {
        if (index !in 0 until count) throw IndexOutOfBoundsException("Page $index is not in the reading window")
        return sourceOf(index)?.let { (source, local) -> source.file(local) }
            ?: throw IndexOutOfBoundsException("Page $index is not in the reading window")
    }

    override suspend fun size(index: Int): app.yomu.domain.reader.ImageSize? =
        if (index !in 0 until count) null else sourceOf(index)?.let { (source, local) -> source.size(local) }

    /**
     * Hands the chapters named in [keep] to the window that replaces this one and releases the rest. The new session
     * then owns them; this one no longer closes anything, so a rebuilt pager never closes a file the next one shows.
     */
    fun transfer(keep: Set<Long>): List<MangaChapter> {
        val kept = chapters.filter { it.id in keep }
        chapters.filter { it.id !in keep }.forEach { runCatching { it.source.close() } }
        closed = true
        return kept
    }

    override fun close() {
        if (closed) return
        closed = true
        chapters.forEach { runCatching { it.source.close() } }
    }
}
