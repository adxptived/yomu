package app.yomu.data.backup

import android.content.Context
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.data.glossary.GlossaryRepository
import app.yomu.domain.glossary.GlossaryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** N04: glossary notes take part in field-complete sync with the same rules as words and annotations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlossarySyncTest {
    private val context = RuntimeEnvironment.getApplication() as Context
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private var shared: String? = null
    private var clock = 1_000_000L

    private inner class Device {
        val db: YomuDatabase = YomuDatabase.build(context, inMemory = true)
        var base: SyncBase? = null

        fun sync(glossary: Boolean = true): Int = runBlocking {
            clock += 1000
            val remote = shared?.let { SyncCodec.decode(json.parseToJsonElement(it).jsonObject) }
            val out = SyncMerger(db).merge(remote, base, "test", clock, SyncMerger.Options(glossary = glossary))
            shared = SyncCodec.encode(out.state)
            base = out.base
            out.changed
        }

        fun addBook(hash: String): Long = runBlocking {
            db.books().insert(
                BookEntity(
                    title = "Book $hash", sortTitle = "book $hash", authorsText = "", seriesName = null, seriesId = null,
                    seriesIndex = null, language = "en", description = null, publisher = null, format = "EPUB",
                    uri = "file:///$hash.epub", isLinked = false, fileSize = 1, fileHash = hash, coverPath = null,
                    status = "READING", isManga = false, addedAt = 1, lastReadAt = null, progress = 0f,
                    readerSettingsJson = null,
                ),
            )
        }

        fun save(entry: GlossaryEntry) = runBlocking { GlossaryRepository(db).save(entry) }
        fun delete(id: Long) = runBlocking { GlossaryRepository(db).delete(id) }
        fun entries() = runBlocking { GlossaryRepository(db).all() }
    }

    private val a = Device()
    private val b = Device()

    @After fun close() {
        a.db.close()
        b.db.close()
    }

    @Test
    fun `glossary is exported merged with the local book id and is idempotent`() = runBlocking {
        val bookA = a.addBook("glossary-1")
        val bookB = b.addBook("glossary-1")
        a.save(GlossaryEntry(bookId = bookA, term = "Elena", aliases = listOf("Lena"), note = "from A", updatedAt = 100))

        a.sync()
        b.sync()
        val onB = b.entries().single()
        assertEquals("from A", onB.note)
        assertEquals("Elena", onB.term)
        assertEquals(bookB, onB.bookId) // the remote book hash was remapped to B's local id
        assertEquals(listOf("Lena"), onB.aliases)

        a.sync()
        b.sync()
        assertEquals(1, b.entries().size)
        a.sync()
        assertEquals(1, a.entries().size)
        assertEquals(1, json.decodeFromString(SyncState.serializer(), shared!!).glossary.size)
    }

    @Test
    fun `newest note wins and an older edit cannot overwrite it`() = runBlocking {
        val bookA = a.addBook("glossary-2")
        val bookB = b.addBook("glossary-2")
        a.save(GlossaryEntry(bookId = bookA, term = "Romeo", note = "original", updatedAt = 100))
        a.sync(); b.sync()

        b.save(b.entries().single().copy(note = "newer from B", updatedAt = 200))
        b.sync(); a.sync()
        assertEquals("newer from B", a.entries().single().note)

        a.save(a.entries().single().copy(note = "older from A", updatedAt = 50))
        a.sync(); b.sync()
        assertEquals("newer from B", b.entries().single().note)
    }

    @Test
    fun `a glossary deletion reaches the other device and does not come back`() = runBlocking {
        val bookA = a.addBook("glossary-3")
        val bookB = b.addBook("glossary-3")
        a.save(GlossaryEntry(bookId = bookA, term = "Elsinore", note = "a place", updatedAt = 100))
        a.sync(); b.sync()
        assertEquals(1, b.entries().size)

        b.delete(b.entries().single().id)
        b.sync()
        a.sync()
        assertTrue(a.entries().isEmpty())

        b.sync()
        a.sync()
        assertTrue(b.entries().isEmpty())
        assertTrue(a.entries().isEmpty())
    }

    @Test
    fun `removing a book is not a deletion of its glossary notes`() = runBlocking {
        val bookA = a.addBook("glossary-4")
        val bookB = b.addBook("glossary-4")
        a.save(GlossaryEntry(bookId = bookA, term = "Ophelia", note = "note", updatedAt = 100))
        a.sync(); b.sync()
        assertEquals(1, b.entries().size)

        runBlocking { b.db.books().delete(listOf(bookB)) } // cascades the note away locally
        b.sync()
        a.sync()
        assertEquals(1, a.entries().size)
        assertTrue(b.entries().isEmpty())
        assertEquals(1, json.decodeFromString(SyncState.serializer(), shared!!).glossary.size)
    }

    @Test
    fun `glossary switched off neither publishes nor receives nor deletes`() = runBlocking {
        val bookA = a.addBook("glossary-5")
        val bookB = b.addBook("glossary-5")
        a.save(GlossaryEntry(bookId = bookA, term = "Private", note = "secret", updatedAt = 100))
        a.sync()
        b.sync(glossary = false)
        assertTrue(b.entries().isEmpty())

        b.save(GlossaryEntry(bookId = bookB, term = "Local", note = "mine", updatedAt = 100))
        b.sync(glossary = false)
        a.sync()
        assertEquals(1, a.entries().size)
        assertEquals("secret", a.entries().single().note)

        b.sync()
        assertEquals(2, b.entries().size)
        a.sync()
        assertEquals(2, a.entries().size)
    }

    @Test
    fun `unknown future categories survive a glossary merge`() = runBlocking {
        shared = """{"version":2,"updatedAt":1,"glossary":[{"hash":null,"locator":null,"term":"Future","normalized":"future","aliasesJson":"[]","kind":"TERM","note":"n","createdAt":1,"updatedAt":1}],"futureCategory":{"a":1}}"""
        a.sync()
        val root = json.parseToJsonElement(shared!!).jsonObject
        assertEquals(1, root["futureCategory"]?.jsonObject?.get("a")?.jsonPrimitive?.intOrNull)
        assertEquals(1, a.entries().size)
        assertEquals("Future", a.entries().single().term)
    }
}
