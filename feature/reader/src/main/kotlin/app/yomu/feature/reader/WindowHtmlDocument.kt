package app.yomu.feature.reader

import app.yomu.domain.model.TocItem
import app.yomu.feature.reader.epub.TextDocument

/**
 * A bounded document of the online chapters held in the reader window.
 *
 * The text engine navigates *sections*, while the library speaks in global chapter ids. This document is the bridge:
 * a section index maps to the chapter it came from and back, so progress, the header and the next-chapter decision
 * always name the real chapter even though only the current chapter and its neighbours are in the WebView. The whole
 * series is never concatenated into one document: the window is what the reader holds, nothing more.
 */
class WindowHtmlDocument(
    override val title: String,
    val chapters: List<Chapter>,
    override val language: String? = null,
) : TextDocument {
    data class Chapter(val id: Long, val title: String, val html: String) {
        val weight: Long get() = html.length.toLong().coerceAtLeast(1)
    }

    override val rtl: Boolean = false
    override val chapterCount: Int get() = chapters.size
    override val toc: List<TocItem> get() = chapters.mapIndexed { index, chapter -> TocItem(chapter.title, index) }

    /** The global chapter id of a section, or null when the section does not exist. */
    fun chapterId(index: Int): Long? = chapters.getOrNull(index)?.id

    /** The section of a global chapter id, or -1 when that chapter is not in the window. */
    fun indexOfChapterId(id: Long): Int = chapters.indexOfFirst { it.id == id }

    override fun chapterPath(index: Int): String =
        "chapter-${chapters.getOrNull(index)?.id ?: 0}.xhtml"

    override fun chapterIndexOf(path: String): Int {
        val name = path.substringBefore('#')
        return chapters.indices.firstOrNull { chapterPath(it) == name } ?: -1
    }

    override fun chapterTitle(index: Int): String? = chapters.getOrNull(index)?.title

    override fun weight(index: Int): Long = chapters.getOrNull(index)?.weight ?: 1

    override suspend fun chapterHtml(index: Int): String {
        val chapter = chapters.getOrNull(index) ?: return ""
        return if (chapter.html.contains("<body", ignoreCase = true)) {
            chapter.html
        } else {
            "<html><head><title>${chapter.title}</title></head><body>${chapter.html}</body></html>"
        }
    }

    override fun resource(path: String): ByteArray? = null

    override fun close() = Unit
}
