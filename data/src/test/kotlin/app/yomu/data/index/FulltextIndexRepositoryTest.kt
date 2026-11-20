package app.yomu.data.index

import android.content.Context
import app.yomu.core.database.BookEntity
import app.yomu.core.database.HighlightEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.domain.index.IndexPlans
import app.yomu.domain.index.IndexState
import app.yomu.domain.task.TaskRegistry
import app.yomu.domain.task.TaskState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FulltextIndexRepositoryTest {
    private val app = RuntimeEnvironment.getApplication() as Context

    private suspend fun book(db: YomuDatabase, id: Long) = db.books().insert(
        BookEntity(
            id = id, title = "Book", sortTitle = "Book", authorsText = "", seriesName = null, seriesId = null,
            seriesIndex = null, language = "en", description = null, publisher = null, format = "EPUB",
            uri = "file:///index-book-$id", isLinked = false, fileSize = 1, fileHash = null, coverPath = null,
            status = "READING", isManga = false, addedAt = 1, lastReadAt = null, progress = 0f,
            readerSettingsJson = null,
        ),
    )

    private suspend fun highlight(db: YomuDatabase, id: Long, text: String, updatedAt: Long = 2L) = db.annotations().insertHighlight(
        HighlightEntity(
            id = id, bookId = 1, locatorJson = "{}", chapter = 0, text = text, color = "YELLOW",
            style = "HIGHLIGHT", isQuote = false, isFavorite = false, tags = "", note = null,
            createdAt = 1, updatedAt = updatedAt,
        ),
    )

    @Test
    fun `estimate rebuild search and delete with the book`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = FulltextIndexRepository(db)
        try {
            book(db, 1)
            highlight(db, 1, "the queen Elena waits")
            val estimate = repo.estimate()
            assertEquals(1, estimate.books)
            assertEquals(1, estimate.sections)

            assertTrue(repo.rebuild())
            // stateIn emits on its own dispatcher: await the published state instead of racing .value.
            assertTrue(kotlinx.coroutines.withTimeout(2_000) { repo.plan.first { it.done } }.done)
            assertFalse(repo.plan.value.cancelled)
            assertEquals(1, repo.indexedCount.first())

            val hits = repo.search("quee")
            assertEquals(1, hits.size)
            assertEquals(FulltextIndexRepository.SOURCE_TEXT, hits.single().source)
            assertTrue(repo.search("\"").isEmpty()) // operators are stripped, not passed to FTS

            // Incremental: nothing changed, so a second rebuild adds no row.
            assertTrue(repo.rebuild())
            assertEquals(1, repo.indexedCount.first())

            db.books().delete(listOf(1L))
            assertEquals(0, repo.indexedCount.first())
            assertTrue(repo.search("quee").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `OCR text is attached to a book and is searchable`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = FulltextIndexRepository(db)
        try {
            book(db, 1)
            repo.addOcrText(1, "page:3", "OCR recognised words")
            assertEquals(1, repo.estimate().ocrSections)
            assertTrue(repo.rebuild())
            assertEquals("OCR", repo.search("recognised").single().source)
            db.books().delete(listOf(1L))
            assertEquals(0, repo.indexedCount.first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `generic text indexing is incremental survives a rebuild and dies with the book`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = FulltextIndexRepository(db)
        try {
            book(db, 1)
            repo.indexText(1, "chapter:4", "the king returns")
            assertEquals(1, repo.estimate().sections)
            assertTrue(repo.rebuild())
            assertEquals("the king returns", repo.search("retur").single().text)
            assertEquals(FulltextIndexRepository.SOURCE_TEXT, repo.search("retur").single().source)

            // Re-indexing the same section upserts one row with a fresh stamp, never a duplicate.
            repo.indexText(1, "chapter:4", "the king returns home", updatedAt = 20L)
            assertEquals(1, repo.indexedCount.first())
            assertTrue(repo.rebuild())
            assertEquals(1, repo.indexedCount.first())
            assertEquals("the king returns home", repo.search("hom").single().text)

            // The same section from another source is a separate row; both survive a rebuild.
            repo.addOcrText(1, "chapter:4", "scanned banner", updatedAt = 30L)
            assertEquals(2, repo.indexedCount.first())
            assertTrue(repo.rebuild())
            assertEquals(2, repo.indexedCount.first())
            assertEquals("OCR", repo.search("banner").single().source)

            db.books().delete(listOf(1L))
            assertEquals(0, repo.indexedCount.first())
            assertTrue(repo.search("retur").isEmpty())
            assertTrue(repo.search("banner").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `an unsupported index source is refused`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = FulltextIndexRepository(db)
        try {
            book(db, 1)
            assertTrue(runCatching { repo.indexText(1, "chapter:1", "text", source = "BOGUS") }.exceptionOrNull() != null)
            assertEquals(0, repo.indexedCount.first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `cancel is a request tracked by the shared registry`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val registry = TaskRegistry()
        val repo = FulltextIndexRepository(db, registry)
        try {
            registry.start(IndexPlans.TASK_ID, "Index")
            repo.cancel()
            assertEquals(TaskState.CANCELLING, registry.tasks.value.getValue(IndexPlans.TASK_ID).state)
            // A cancel request is still shown as running until the owner confirms it.
            assertEquals(IndexState.RUNNING, IndexPlans.from(registry.tasks.value.getValue(IndexPlans.TASK_ID)).state)
        } finally {
            db.close()
        }
    }
}
