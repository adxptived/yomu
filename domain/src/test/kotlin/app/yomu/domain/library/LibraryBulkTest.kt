package app.yomu.domain.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

/**
 * A mixed selection must never lose data: each command knows which kinds it can reach, a partial result is reported
 * per item, and a failure of one item does not cancel the others.
 */
class LibraryBulkTest {
    private val book = LibraryItemKey.LocalBook(1)
    private val online = LibraryItemKey.OnlineEntry(2)
    private val result = LibraryItemKey.SourceResult(sourceId = 3, url = "/x")

    @Test
    fun `owned files are deleted for local books only`() {
        assertTrue(BulkCapabilities.supports(BulkAction.DELETE_OWNED_FILES, LibraryItemKind.LOCAL_BOOK))
        assertFalse(BulkCapabilities.supports(BulkAction.DELETE_OWNED_FILES, LibraryItemKind.ONLINE_TITLE))
        assertFalse(BulkCapabilities.supports(BulkAction.DELETE_OWNED_FILES, LibraryItemKind.SOURCE_RESULT))
    }

    @Test
    fun `personal actions reach both library kinds`() {
        listOf(BulkAction.PERSONAL_STATUS, BulkAction.FAVORITE, BulkAction.RATING, BulkAction.TAGS, BulkAction.SHELVES).forEach { action ->
            assertTrue(BulkCapabilities.supports(action, LibraryItemKind.LOCAL_BOOK), "$action local")
            assertTrue(BulkCapabilities.supports(action, LibraryItemKind.ONLINE_TITLE), "$action online")
        }
    }

    @Test
    fun `a source result can only be added, not acted on`() {
        BulkAction.entries.forEach { action ->
            assertFalse(BulkCapabilities.supports(action, LibraryItemKind.SOURCE_RESULT), "$action")
        }
    }

    @Test
    fun `the applicable set counts what the command can reach`() {
        val keys = setOf(book, online, result)
        assertEquals(setOf(book, online), BulkCapabilities.applicable(BulkAction.PERSONAL_STATUS, keys))
        assertEquals(setOf(book), BulkCapabilities.applicable(BulkAction.DELETE_OWNED_FILES, keys))
        assertEquals(emptySet<LibraryItemKey>(), BulkCapabilities.applicable(BulkAction.FAVORITE, setOf(result)))
    }

    @Test
    fun `a preview says how many of the selection are reached`() {
        assertEquals("2/3", BulkExecutor.preview(BulkAction.PERSONAL_STATUS, setOf(book, online, result)))
        assertEquals("1/3", BulkExecutor.preview(BulkAction.DELETE_OWNED_FILES, setOf(book, online, result)))
    }

    @Test
    fun `a partial run reports success and unsupported per item`() = runBlocking {
        val report = BulkExecutor.run(BulkAction.DELETE_OWNED_FILES, setOf(book, online)) { BulkOutcome.Success }
        assertEquals(1, report.succeeded)
        assertEquals(1, report.unsupported)
        assertEquals(0, report.failed)
        assertTrue(report.isPartial)
        assertEquals(BulkOutcome.Success, report.outcomes[book])
        assertEquals(BulkOutcome.Unsupported, report.outcomes[online])
    }

    @Test
    fun `one failing item does not cancel the others`() = runBlocking {
        val report = BulkExecutor.run(BulkAction.DOWNLOAD, setOf(book, online)) { key ->
            if (key == book) BulkOutcome.Failed(BulkFailure.SOURCE) else BulkOutcome.Success
        }
        assertEquals(1, report.failed)
        assertEquals(1, report.succeeded)
        assertEquals(setOf(book), report.retryable)
    }

    @Test
    fun `a thrown exception becomes a per-item failure`() = runBlocking {
        val report = BulkExecutor.run(BulkAction.RATING, setOf(book)) { error("database went away") }
        assertEquals(1, report.failed)
        assertTrue(report.outcomes[book] is BulkOutcome.Failed)
    }

    @Test
    fun `an empty selection is a complete no-op`() = runBlocking {
        val report = BulkExecutor.run(BulkAction.FAVORITE, emptySet()) { BulkOutcome.Success }
        assertEquals(0, report.succeeded)
        assertFalse(report.isPartial)
    }

    @Test
    fun `a fully applicable run is not partial`() = runBlocking {
        val report = BulkExecutor.run(BulkAction.FAVORITE, setOf(book, online)) { BulkOutcome.Success }
        assertEquals(2, report.succeeded)
        assertFalse(report.isPartial)
    }
}
