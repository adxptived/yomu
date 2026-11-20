package app.yomu.data.glossary

import android.content.Context
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.domain.glossary.GlossaryEntry
import app.yomu.domain.glossary.GlossaryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlossaryRepositoryTest {
    private val app = RuntimeEnvironment.getApplication() as Context

    private suspend fun book(db: YomuDatabase, id: Long) = db.books().insert(
        BookEntity(
            id = id, title = "Book $id", sortTitle = "Book $id", authorsText = "", seriesName = null, seriesId = null,
            seriesIndex = null, language = "en", description = null, publisher = null, format = "EPUB",
            uri = "file:///book/$id", isLinked = false, fileSize = 1, fileHash = null, coverPath = null,
            status = "READING", isManga = false, addedAt = 1, lastReadAt = null, progress = 0f,
            readerSettingsJson = null,
        ),
    )

    @Test
    fun `notes are observed by book and keep a stable identity across import`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = GlossaryRepository(db)
        try {
            book(db, 1)
            val id = repo.save(
                GlossaryEntry(term = "Elena", aliases = listOf("Lena"), kind = GlossaryKind.CHARACTER, bookId = 1, note = "local"),
            )
            assertTrue(id > 0)
            assertEquals("elena", db.glossary().get(id)!!.normalized)
            assertEquals(1, repo.observe(1).first().size)
            assertEquals(0, repo.observe(2).first().size)
            assertEquals(1, repo.observe(null).first().size)

            val exported = repo.exportJson()
            val again = repo.import(exported)
            assertEquals(1, again.total)
            assertEquals(1, repo.all().size)
            assertEquals(id, repo.all().single().id)

            val replaced = repo.import(exported, replace = true)
            assertEquals(1, replaced.total)
            assertEquals("local", repo.all().single().note)

            repo.delete(repo.all().single().id)
            assertTrue(repo.all().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `the book filter follows entries and a book-bound note is deleted with its book`() = runBlocking {
        val db = YomuDatabase.build(app, true)
        val repo = GlossaryRepository(db)
        try {
            book(db, 7)
            repo.save(GlossaryEntry(term = "Romeo", bookId = 7))
            repo.save(GlossaryEntry(term = "General"))
            assertEquals(listOf(7L to "Book 7"), repo.booksWithEntries())
            assertEquals(1, repo.books().size) // only book 7 exists; the unbound note has no book
            db.books().delete(listOf(7L))
            assertEquals(listOf<GlossaryEntry>(), repo.observe(7).first())
            assertEquals(1, repo.all().size) // the unbound note stays
        } finally {
            db.close()
        }
    }
}
