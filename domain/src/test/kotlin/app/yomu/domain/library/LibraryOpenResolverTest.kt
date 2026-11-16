package app.yomu.domain.library

import app.yomu.domain.model.EngineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * One table for "what does this tap open": a tile opens details, Resume opens the saved position, and an item that
 * cannot be opened reports why instead of starting an empty reader.
 */
class LibraryOpenResolverTest {
    private fun local(engine: EngineType = EngineType.TEXT, saved: Boolean = true, available: Boolean = true, finished: Boolean = false) =
        LibraryItemState(LibraryItemKind.LOCAL_BOOK, engine, saved, available, finished)

    private fun online(engine: EngineType? = null, available: Boolean = true) =
        LibraryItemState(LibraryItemKind.ONLINE_TITLE, engine, hasSavedPosition = true, available = available)

    @Test
    fun `a local tile opens details for every engine`() {
        assertEquals(
            LibraryDestination.BookDetails,
            LibraryOpenResolver.resolve(LibraryIntent.Details, local(EngineType.TEXT)),
        )
        assertEquals(
            LibraryDestination.BookDetails,
            LibraryOpenResolver.resolve(LibraryIntent.Details, local(EngineType.PDF)),
        )
        assertEquals(
            LibraryDestination.BookDetails,
            LibraryOpenResolver.resolve(LibraryIntent.Details, local(EngineType.MANGA)),
        )
        assertEquals(
            LibraryDestination.BookDetails,
            LibraryOpenResolver.resolve(LibraryIntent.Details, local(EngineType.AUDIO)),
        )
    }

    @Test
    fun `an online tile opens its title screen`() {
        assertEquals(LibraryDestination.TitleDetails, LibraryOpenResolver.resolve(LibraryIntent.Details, online()))
    }

    @Test
    fun `a source result opens the title screen as well`() {
        val result = LibraryItemState(LibraryItemKind.SOURCE_RESULT, null, hasSavedPosition = false, available = true)
        assertEquals(LibraryDestination.TitleDetails, LibraryOpenResolver.resolve(LibraryIntent.Details, result))
    }

    @Test
    fun `resume opens the reader at the saved place`() {
        assertEquals(LibraryDestination.Reader, LibraryOpenResolver.resolve(LibraryIntent.Resume, local()))
    }

    @Test
    fun `resume of an audiobook opens the player not the reader`() {
        assertEquals(
            LibraryDestination.AudioPlayer,
            LibraryOpenResolver.resolve(LibraryIntent.Resume, local(EngineType.AUDIO)),
        )
    }

    @Test
    fun `resume without a saved position still opens the reader`() {
        assertEquals(
            LibraryDestination.Reader,
            LibraryOpenResolver.resolve(LibraryIntent.Resume, local(saved = false)),
        )
    }

    @Test
    fun `a finished book resumes from the beginning instead of failing`() {
        assertEquals(
            LibraryDestination.Reader,
            LibraryOpenResolver.resolve(LibraryIntent.Resume, local(saved = true, finished = true)),
        )
    }

    @Test
    fun `a missing local file reports missing content`() {
        assertEquals(
            LibraryDestination.Unavailable(LibraryUnavailable.MISSING_CONTENT),
            LibraryOpenResolver.resolve(LibraryIntent.Resume, local(available = false)),
        )
    }

    @Test
    fun `a removed source reports the source not the file`() {
        assertEquals(
            LibraryDestination.Unavailable(LibraryUnavailable.SOURCE_UNAVAILABLE),
            LibraryOpenResolver.resolve(LibraryIntent.Resume, online(available = false)),
        )
    }

    @Test
    fun `an online title resumes through its title screen`() {
        assertEquals(LibraryDestination.TitleDetails, LibraryOpenResolver.resolve(LibraryIntent.Resume, online()))
    }

    @Test
    fun `a specific chapter opens that chapter online and the details locally`() {
        assertEquals(
            LibraryDestination.ReaderChapter(42),
            LibraryOpenResolver.resolve(LibraryIntent.Chapter(42), online()),
        )
        // A local book has no catalogue chapter: the details screen lists its own table of contents.
        assertEquals(
            LibraryDestination.BookDetails,
            LibraryOpenResolver.resolve(LibraryIntent.Chapter(42), local()),
        )
    }

    @Test
    fun `a chapter of an unavailable source reports unavailable`() {
        assertEquals(
            LibraryDestination.Unavailable(LibraryUnavailable.SOURCE_UNAVAILABLE),
            LibraryOpenResolver.resolve(LibraryIntent.Chapter(7), online(available = false)),
        )
    }

    @Test
    fun `resume is offered only for reachable items with a place`() {
        assertTrue(LibraryOpenResolver.offersResume(local(saved = true)))
        assertTrue(LibraryOpenResolver.offersResume(local(saved = false, finished = true)))
        assertFalse(LibraryOpenResolver.offersResume(local(saved = false, finished = false)))
        assertFalse(LibraryOpenResolver.offersResume(local(saved = true, available = false)))
    }
}
