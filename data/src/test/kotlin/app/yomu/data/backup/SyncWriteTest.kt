package app.yomu.data.backup

import app.yomu.core.database.YomuDatabase
import app.yomu.core.database.BookEntity
import app.yomu.core.database.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWriteTest {
    private val db = YomuDatabase.build(RuntimeEnvironment.getApplication(), inMemory = true)
    @After fun close() = db.close()
    private val remote = SyncState(words = listOf(SyncWord("word", "word", "en", "translation", null, null, "", 1, 0, 2.5, 0, 0, 0, 0, null)))
    private class Backend(var error: Exception? = null, var loseResponse: Boolean = false) : SyncBackend {
        override val id = "fake"
        var written: ByteArray? = null
        override suspend fun read(): SyncFile? = null
        override suspend fun check() = Unit
        override suspend fun write(bytes: ByteArray, previous: SyncFile?) {
            error?.let { throw it }
            written = bytes
            if (loseResponse) { loseResponse = false; throw IOException("response lost after server accepted write") }
        }
    }
    private suspend fun insertBookAndNote(): Long {
        val id = db.books().insert(BookEntity(title = "Book", sortTitle = "book", authorsText = "", seriesName = null, seriesId = null, seriesIndex = null,
            language = "en", description = null, publisher = null, format = "EPUB", uri = "file:///book.epub", isLinked = false,
            fileSize = 1, fileHash = "book", coverPath = null, status = "READING", isManga = false, addedAt = 1, lastReadAt = null,
            progress = 0f, readerSettingsJson = null))
        db.annotations().insertNote(NoteEntity(0, id, null, "{}", "original", "", 100, 100))
        return id
    }

    @Test fun acceptedRemoteNoteEditWithLostResponseCanBeRetried() = runBlocking {
        insertBookAndNote()
        val initial = SyncMerger(db).merge(null, null, "fake", 1000, SyncMerger.Options())
        val edited = initial.state.copy(notes = initial.state.notes.map { it.copy(text = "remote edit", updatedAt = 1) })
        val backend = Backend(loseResponse = true)
        assertTrue(runCatching { mergeAndWrite(db, backend, null, edited, initial.base, 2000, SyncMerger.Options()) }.exceptionOrNull() is IOException)
        // The merge is committed locally before the write: the base stays at the old value, so the retry publishes it.
        assertEquals("remote edit", db.annotations().allNotes().single().text)
        val accepted = SyncManager.decodeRemote(backend.written!!)
        val retry = mergeAndWrite(db, backend, null, accepted, initial.base, 3000, SyncMerger.Options())
        assertEquals("remote edit", db.annotations().allNotes().single().text)
        assertEquals("remote edit", retry.state.notes.single().text)
    }

    @Test fun acceptedNoteDeletionWithLostResponseCanBeRetried() = runBlocking {
        insertBookAndNote()
        val initial = SyncMerger(db).merge(null, null, "fake", 1000, SyncMerger.Options())
        db.annotations().deleteNote(db.annotations().allNotes().single().id)
        val backend = Backend(loseResponse = true)
        assertTrue(runCatching { mergeAndWrite(db, backend, null, initial.state, initial.base, 2000, SyncMerger.Options()) }.exceptionOrNull() is IOException)
        val accepted = SyncManager.decodeRemote(backend.written!!)
        assertTrue(accepted.notes.isEmpty())
        assertEquals(1, accepted.annotationDeletionProofs.size)
        val retry = mergeAndWrite(db, backend, null, accepted, initial.base, 3000, SyncMerger.Options())
        assertTrue(db.annotations().allNotes().isEmpty())
        assertEquals(accepted.annotationDeletionProofs, retry.state.annotationDeletionProofs)
    }

    @Test fun aRefusedConditionalWriteKeepsTheMergedLocalStateForTheNextCycle() = runBlocking {
        val backend = Backend(SyncConflictException())
        val e = runCatching { mergeAndWrite(db, backend, null, remote, null, 100, SyncMerger.Options()) }.exceptionOrNull()
        assertTrue(e is SyncConflictException)
        assertNull(backend.written)
        // The write runs outside the merge transaction: a refused write must not hold or lose the local merge.
        assertEquals("word", db.words().all().single().term)
    }
    @Test fun aFailedNetworkWriteKeepsTheMergedLocalStateForTheNextCycle() = runBlocking {
        val backend = Backend(IOException("offline"))
        assertTrue(runCatching { mergeAndWrite(db, backend, null, remote, null, 100, SyncMerger.Options()) }.exceptionOrNull() is IOException)
        assertEquals("word", db.words().all().single().term)
    }
    @Test fun acceptedWriteCommitsLocalMerge() = runBlocking {
        val backend = Backend()
        val outcome = mergeAndWrite(db, backend, null, remote, null, 100, SyncMerger.Options())
        assertEquals(1, outcome.changed)
        assertEquals("word", db.words().all().single().term)
        assertNotNull(backend.written)
    }
}
