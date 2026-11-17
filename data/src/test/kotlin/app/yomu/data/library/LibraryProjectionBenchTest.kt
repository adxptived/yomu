package app.yomu.data.library

import android.content.Context
import androidx.room.Room
import app.yomu.core.database.BookEntity
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.LibraryQuery
import app.yomu.domain.model.LibrarySort
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The library's scale target: 1000 local books and 10 000 online titles.
 *
 * This is not the instrumentation report of Q03 (a device, a release build and frame timings); it is a guard that
 * the query and the merged ordering stay linear and do not materialise the whole library on every step. The measured
 * numbers are written to `data/build/library-bench.txt` so a slower pass is visible in a diff of the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryProjectionBenchTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private var db: YomuDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    @Test
    fun thousandBooksTenThousandTitles() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, YomuDatabase::class.java).allowMainThreadQueries().build()
        val database = db!!
        val repository = BookRepositoryImpl(context, database, CoverStore(context))
        val now = 1_700_000_000_000L
        val local = (1L..1000L).map { id -> bookEntity(id, "Book ${id % 250}", now - id) }
        database.books().insertAll(local)
        val items = (1L..10_000L).map { id ->
            CatalogItemEntity(
                id = id, sourceId = 1, url = "/title/$id", title = "Title ${id % 500}", author = "Author ${id % 97}",
                artist = null, description = null, genres = "Action", status = 1, coverUrl = null, type = "MANGA",
                favorite = true, categoryId = null, addedAt = now - id, lastUpdateAt = now - id, initialized = true,
            )
        }
        database.catalog().insertAll(items)

        val sql = repository.buildQuery(LibraryQuery(sort = LibrarySort.LAST_READ))
        val page = measure(repeat = 5) {
            database.openHelper.writableDatabase.query(sql).use { c ->
                var n = 0
                while (c.moveToNext()) n++
                n
            }
        }
        // One page request is what the grid asks for; Paging adds LIMIT/OFFSET around the same query.
        val sort = LibrarySort.TITLE
        val online = items.map { it.toEntry() }
        val books = local.map { it.toBook() }
        val order = measure(repeat = 3) { LibraryProjection.order(books, online, sort, ascending = true).size }
        val merge = measure(repeat = 3) {
            LibraryProjection.merge(
                books.map { LibraryProjectionItem.Local(it) }.asSequence(),
                online.map { LibraryProjectionItem.Online(it) }.asSequence(),
                sort,
                ascending = true,
            ).count()
        }

        val report = buildString {
            appendLine("library bench — 1000 local books, 10000 online titles")
            appendLine("full book query, all rows: ${page.first} rows, ${page.second} ms/run")
            appendLine("merge of both ordered lists: ${order.first} rows, ${order.second} ms/run")
            appendLine("lazy merge (sequences): ${merge.first} rows, ${merge.second} ms/run")
        }
        File("build/library-bench.txt").writeText(report)
        assertTrue("the book query became quadratic: $report", page.second < 5_000)
        assertTrue("the merged ordering became quadratic: $report", order.second < 5_000)
        assertTrue(merge.first == 11_000 && order.first == 11_000)
    }

    private fun measure(repeat: Int, block: () -> Int): Pair<Int, Long> {
        var result = 0
        repeat(2) { block() }
        var best = Long.MAX_VALUE
        repeat(repeat) {
            val started = System.nanoTime()
            result = block()
            best = minOf(best, (System.nanoTime() - started) / 1_000_000)
        }
        return result to best
    }

    private fun bookEntity(id: Long, title: String, at: Long) = BookEntity(
        id = id, title = title, sortTitle = title, authorsText = "Author ${id % 97}", seriesName = null, seriesId = null,
        seriesIndex = null, language = "en", description = null, publisher = null, format = "EPUB",
        uri = "file:///book$id.epub", isLinked = false, fileSize = 1024, fileHash = null, coverPath = null,
        status = "READING", isManga = false, addedAt = at, lastReadAt = at + 1, progress = (id % 100) / 100f,
        readerSettingsJson = null, scanFolder = null, favorite = false, rating = 0,
    )

    private fun BookEntity.toBook() = Book(
        id = id, title = title, authors = listOf(authorsText), format = BookFormat.EPUB, uri = uri,
        addedAt = addedAt, lastReadAt = lastReadAt, progress = progress,
    )

    private fun CatalogItemEntity.toEntry() = CatalogEntry(
        id = id, sourceId = sourceId, url = url, title = title, author = author, addedAt = addedAt,
        lastUpdateAt = lastUpdateAt, unread = 0, chapterCount = 0,
    )
}
