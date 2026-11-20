package app.yomu.feature.reader

import app.yomu.core.network.DataSaver
import app.yomu.domain.model.ChapterInfo
import app.yomu.domain.model.ContentType
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Q01: the reader's source/capability decisions live in one testable facade. */
class SourceConnectionResolverTest {

    @TempDir lateinit var dir: File

    // routeOf and the pure helpers need no real context or catalog; only openPages does, and it is not under test here.
    private fun resolver() = SourceConnectionResolver(mockk(relaxed = true), mockk(relaxed = true))

    private fun chapter(localPath: String?) = ChapterInfo(
        id = 5L,
        entryId = 7L,
        url = "chapter-url",
        name = "Chapter",
        localPath = localPath,
    )

    @Test
    fun `content type decides the canvas and the window in one place`() {
        for (type in listOf(ContentType.NOVEL, ContentType.BOOK, ContentType.AUDIO)) {
            assertTrue(SourceConnectionResolver.isTextContent(type), "$type is laid out as text")
            assertFalse(SourceConnectionResolver.showImageCanvas(type))
            assertEquals(SourceConnectionResolver.WindowMode.TEXT, resolver().windowMode(type))
        }
        assertFalse(SourceConnectionResolver.isTextContent(ContentType.MANGA))
        assertTrue(SourceConnectionResolver.showImageCanvas(ContentType.MANGA))
        assertEquals(SourceConnectionResolver.WindowMode.PAGES, resolver().windowMode(ContentType.MANGA))
    }

    @Test
    fun `data saver caps only the preloading of network chapters`() {
        assertEquals(DataSaver.PRELOAD_PAGES, SourceConnectionResolver.preloadLimit(true, online = true))
        assertNull(SourceConnectionResolver.preloadLimit(true, online = false))
        assertNull(SourceConnectionResolver.preloadLimit(false, online = true))
        assertNull(SourceConnectionResolver.preloadLimit(false, online = false))
    }

    @Test
    fun `a window swap requires the engine the title actually uses`() {
        val text = SourceConnectionResolver.WindowMode.TEXT
        val pages = SourceConnectionResolver.WindowMode.PAGES

        assertTrue(SourceConnectionResolver.isWindowed(text, textOpen = true, pagesOpen = false))
        assertFalse(SourceConnectionResolver.isWindowed(text, textOpen = false, pagesOpen = true))
        assertTrue(SourceConnectionResolver.isWindowed(pages, textOpen = false, pagesOpen = true))
        assertFalse(SourceConnectionResolver.isWindowed(pages, textOpen = true, pagesOpen = false))
        assertFalse(SourceConnectionResolver.isWindowed(text, textOpen = false, pagesOpen = false))
    }

    @Test
    fun `a downloaded chapter is local and a missing one goes to its source`() {
        val resolver = resolver()
        val file = File(dir, "chapter.cbz").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val folder = File(dir, "folder").apply { mkdirs() }

        assertEquals(SourceConnectionResolver.Route.DOWNLOADED, resolver.routeOf(chapter(file.absolutePath)))
        assertEquals(SourceConnectionResolver.Route.DOWNLOADED, resolver.routeOf(chapter(folder.absolutePath)))
        assertEquals(SourceConnectionResolver.Route.SOURCE, resolver.routeOf(chapter(File(dir, "gone.cbz").absolutePath)))
        assertEquals(SourceConnectionResolver.Route.SOURCE, resolver.routeOf(chapter(null)))
    }
}
