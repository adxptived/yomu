package app.yomu.feature.reader.manga

import app.yomu.data.book.PageSource
import app.yomu.domain.reader.ImageSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** M03a: the flattened pages of the loaded chapters and the lifetime of their sources. */
class SessionPageSourceTest {

    private class FakeSource(private val tag: String, override val count: Int) : PageSource {
        var closed = false
        override fun name(index: Int): String = "$tag/$index"
        override suspend fun file(index: Int): File = File("$tag-$index")
        override suspend fun size(index: Int): ImageSize = ImageSize(100 + index, 200)
        override fun close() {
            closed = true
        }
    }

    private fun session() = SessionPageSource(
        listOf(
            MangaChapter(20, "Chapter 1", FakeSource("a", 3)),
            MangaChapter(30, "Chapter 2", FakeSource("b", 2)),
            MangaChapter(40, "Chapter 3", FakeSource("c", 4)),
        ),
    )

    @Test
    fun `controller never falls back to a different chapter when resolving an anchor`() {
        val controller = MangaController(session())
        assertEquals(4, controller.globalIndex(30, 1))
        assertNull(controller.globalIndex(99, 1))
        assertNull(controller.globalIndex(30, 2))
    }

    @Test
    fun `pages of the loaded chapters are flattened in reading order`() {
        val s = session()
        assertEquals(9, s.count)
        assertEquals(listOf(0, 3, 5), s.slices.map { it.start })
        assertEquals(20L, s.chapterAt(0)?.id)
        assertEquals(30L, s.chapterAt(3)?.id)
        assertEquals(40L, s.chapterAt(8)?.id)
        assertEquals(0, s.pageAt(0))
        assertEquals(1, s.pageAt(4))
        assertEquals(3, s.pageAt(8))
    }

    @Test
    fun `a flat index delegates to the right chapter and page`() {
        val s = session()
        assertEquals("b/1", s.name(4))
        assertEquals(File("b-1"), kotlinx.coroutines.runBlocking { s.file(4) })
        assertEquals(ImageSize(101, 200), kotlinx.coroutines.runBlocking { s.size(4) })
        assertEquals(40L, s.chapterAt(7)?.id)
        assertEquals(2, s.pageAt(7))
        assertEquals(1, s.slices[1].pageAt(4))
    }

    @Test
    fun `a preview delegates only to the preview endpoint`() = kotlinx.coroutines.runBlocking {
        val cached = object : PageSource {
            override val count = 2
            override fun name(index: Int) = index.toString()
            override suspend fun file(index: Int): File = error("Preview must not fetch the page")
            override suspend fun previewFile(index: Int): File? = File("cached-1").takeIf { index == 1 }
            override fun close() = Unit
        }
        val controller = MangaController(SessionPageSource(listOf(
            MangaChapter(20, "Chapter 1", FakeSource("a", 3)),
            MangaChapter(30, "Chapter 2", cached),
        )))
        assertNull(controller.previewFile(2))
        assertNull(controller.previewFile(3))
        assertEquals(File("cached-1"), controller.previewFile(4))
        assertNull(controller.previewFile(5))
    }

    @Test
    fun `chapter starts and ends describe the pager boundaries`() {
        val s = session()
        assertEquals(listOf(3, 5), s.chapterStarts())
        assertEquals(listOf(2, 4), s.chapterEnds())
        assertEquals(3, s.startOf(30))
        assertEquals(4, s.locate(30, 1))
        assertNull(s.startOf(99))
        assertNull(s.locate(30, 2))
    }

    @Test
    fun `close releases every chapter the session owns`() {
        val s = session()
        val sources = s.chapters.map { it.source as FakeSource }
        s.close()
        assertTrue(sources.all { it.closed })
        // Closing twice is harmless (the controller may be disposed after a rebuild).
        s.close()
    }

    @Test
    fun `transfer keeps the retained chapters and releases the dropped ones`() {
        val s = session()
        val kept = s.transfer(setOf(30L, 40L))
        assertEquals(listOf(30L, 40L), kept.map { it.id })
        assertFalse((kept[0].source as FakeSource).closed)
        assertFalse((kept[1].source as FakeSource).closed)
        assertTrue((s.chapters[0].source as FakeSource).closed)
        // The old session no longer closes the sources the new one owns.
        s.close()
        assertFalse((kept[0].source as FakeSource).closed)
    }

    @Test
    fun `a page outside the session is rejected`() {
        val s = session()
        assertThrows(IndexOutOfBoundsException::class.java) {
            kotlinx.coroutines.runBlocking { s.file(9) }
        }
    }

    @Test
    fun `a chapter without pages does not shift the flat indexes`() {
        val s = SessionPageSource(
            listOf(
                MangaChapter(1, "empty", FakeSource("e", 0)),
                MangaChapter(2, "real", FakeSource("r", 2)),
            ),
        )
        assertEquals(2, s.count)
        assertEquals(2L, s.chapterAt(0)?.id)
        assertNull(s.startOf(1))
        assertEquals(0, s.startOf(2))
    }
}
