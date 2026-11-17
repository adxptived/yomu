package app.yomu.data.library

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteQuery
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.domain.model.LibraryFilter
import app.yomu.domain.model.LibraryQuery
import app.yomu.domain.model.LibrarySort
import app.yomu.domain.model.ReadingStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The library query runs on every scroll, filter and sort. This test reads SQLite's own query plan instead of
 * guessing: an ORDER BY that cannot use an index adds a temp B-tree and sorts every matching row on each page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryQueryPlanTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var db: YomuDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun repository(): BookRepositoryImpl {
        db = Room.inMemoryDatabaseBuilder(context, YomuDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(*YomuDatabase.MIGRATIONS)
            .build()
        db.openHelper.writableDatabase // create the schema before reading a plan
        return BookRepositoryImpl(context, db, CoverStore(context))
    }

    private fun plan(query: SupportSQLiteQuery): List<String> {
        val rows = mutableListOf<String>()
        db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN ${query.sql}").use { c ->
            val detail = c.getColumnIndexOrThrow("detail")
            while (c.moveToNext()) rows += c.getString(detail)
        }
        return rows
    }

    private fun assertNoTempSort(label: String, query: SupportSQLiteQuery): String {
        val text = plan(query).joinToString("\n")
        assertFalse("$label still sorts every matching row:\n$text", text.contains("USE TEMP B-TREE FOR ORDER BY"))
        return text
    }

    private fun assertUses(label: String, query: SupportSQLiteQuery, index: String) {
        val text = assertNoTempSort(label, query)
        assertTrue("$label does not read $index:\n$text", text.contains(index))
    }

    @Test
    fun everySortThatCanUseAnIndexDoes() {
        val books = repository()
        // TITLE and AUTHOR order with COLLATE NOCASE. SQLite serves that order only from an index on a column
        // declared with the same collation; `books` predates that declaration and changing it needs a table rebuild
        // with every foreign key, which this package does not do (see YomuDatabase.MIGRATION_11_12). They are
        // measured here, not silently ignored: the two plans are asserted to be the known ones.
        listOf(LibrarySort.TITLE, LibrarySort.AUTHOR).forEach { sort ->
            val text = plan(books.buildQuery(LibraryQuery(sort = sort, ascending = true))).joinToString("\n")
            assertTrue("$sort now has a usable index; move it into the indexed set:\n$text", text.contains("USE TEMP B-TREE FOR ORDER BY"))
        }
        // The rest are served by the indices the `books` entity declares.
        assertUses("ADDED ASC", books.buildQuery(LibraryQuery(sort = LibrarySort.ADDED, ascending = true)), "index_books_addedAt")
        assertUses("ADDED DESC", books.buildQuery(LibraryQuery(sort = LibrarySort.ADDED, ascending = false)), "index_books_addedAt")
        assertUses("LAST_READ ASC", books.buildQuery(LibraryQuery(sort = LibrarySort.LAST_READ, ascending = true)), "index_books_lastReadAt_addedAt")
        assertUses("LAST_READ DESC", books.buildQuery(LibraryQuery(sort = LibrarySort.LAST_READ, ascending = false)), "index_books_lastReadAt_addedAt")
        assertUses("PROGRESS ASC", books.buildQuery(LibraryQuery(sort = LibrarySort.PROGRESS, ascending = true)), "index_books_progress")
        assertUses("PROGRESS DESC", books.buildQuery(LibraryQuery(sort = LibrarySort.PROGRESS, ascending = false)), "index_books_progress")
    }

    @Test
    fun theSortIndexesExist() {
        val books = repository()
        books.buildQuery(LibraryQuery(sort = LibrarySort.TITLE))
        val indices = mutableListOf<String>()
        db.openHelper.writableDatabase.query("PRAGMA index_list(books)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) indices += c.getString(name)
        }
        assertTrue("last read sort index is missing: $indices", indices.any { it == "index_books_lastReadAt_addedAt" })
        assertTrue("progress sort index is missing: $indices", indices.any { it == "index_books_progress" })
    }

    /** The visible page and the count must come from the same predicate; a mismatch shows as a wrong badge. */
    @Test
    fun theFilteredQueryReturnsTheRowItsCountPromises() = runBlocking {
        val books = repository()
        val now = 1_700_000_000_000
        db.books().insertAll(
            listOf(
                entity(1, "Alpha", "EPUB", "READING", now),
                entity(2, "Beta", "PDF", "FINISHED", now),
                entity(3, "Gamma", "EPUB", "NEW", now),
            ),
        )
        val query = LibraryQuery(filter = LibraryFilter(statuses = setOf(ReadingStatus.READING)))
        val sql = books.buildQuery(query)
        val where = sql.sql.substringBefore(" ORDER BY ")
        // The predicate has exactly one bound value (the status); the count is the same SELECT without its ORDER BY.
        val count = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM ($where)", arrayOf(ReadingStatus.READING.name)).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
        val loaded = db.openHelper.writableDatabase.query(sql).use { c ->
            var n = 0
            while (c.moveToNext()) n++
            n
        }
        assertEquals(1L, count)
        assertEquals(1, loaded)
    }

    private fun entity(id: Long, title: String, format: String, status: String, at: Long) = BookEntity(
        id = id, title = title, sortTitle = title, authorsText = "Author", seriesName = null, seriesId = null,
        seriesIndex = null, language = null, description = null, publisher = null, format = format,
        uri = "file:///$id.${format.lowercase()}", isLinked = false, fileSize = 0, fileHash = null, coverPath = null,
        status = status, isManga = false, addedAt = at, lastReadAt = null, progress = 0f,
        readerSettingsJson = null, scanFolder = null, favorite = false, rating = 0,
    )
}
