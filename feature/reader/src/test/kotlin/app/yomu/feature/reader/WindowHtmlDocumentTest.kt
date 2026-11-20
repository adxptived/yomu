package app.yomu.feature.reader

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** M03b: the bounded novel document bridges section indexes and library chapter ids. */
class WindowHtmlDocumentTest {

    private fun document() = WindowHtmlDocument(
        title = "A title",
        chapters = listOf(
            WindowHtmlDocument.Chapter(10, "Chapter 1", "<p>one</p>"),
            WindowHtmlDocument.Chapter(20, "Chapter 2", "<html><body><p>two</p></body></html>"),
            WindowHtmlDocument.Chapter(30, "Chapter 3", "<p>three</p>"),
        ),
    )

    @Test
    fun `sections map to global chapter ids and back`() {
        val doc = document()
        assertEquals(3, doc.chapterCount)
        assertEquals(10L, doc.chapterId(0))
        assertEquals(30L, doc.chapterId(2))
        assertNull(doc.chapterId(3))
        assertEquals(1, doc.indexOfChapterId(20))
        assertEquals(-1, doc.indexOfChapterId(99))
    }

    @Test
    fun `paths round trip through the virtual host`() {
        val doc = document()
        assertEquals("chapter-20.xhtml", doc.chapterPath(1))
        assertEquals(1, doc.chapterIndexOf(doc.chapterPath(1)))
        assertEquals(1, doc.chapterIndexOf("chapter-20.xhtml#section"))
        assertEquals(-1, doc.chapterIndexOf("missing.xhtml"))
    }

    @Test
    fun `a chapter without html is wrapped once and a full document is kept`() {
        val doc = document()
        val wrapped = runBlocking { doc.chapterHtml(0) }
        assertTrue(wrapped.contains("<body><p>one</p></body>"))
        assertEquals("<html><body><p>two</p></body></html>", runBlocking { doc.chapterHtml(1) })
    }

    @Test
    fun `titles and weights describe the loaded window`() {
        val doc = document()
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), doc.toc.map { it.title })
        assertEquals("Chapter 3", doc.chapterTitle(2))
        assertNull(doc.chapterTitle(9))
        assertTrue(doc.weight(0) >= 1)
        assertEquals(0f, doc.totalProgression(0, 0f))
        assertEquals(1f, doc.totalProgression(2, 1f))
        assertNull(doc.resource("image.png"))
    }
}
