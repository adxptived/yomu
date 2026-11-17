package app.yomu.data.audio

import android.content.Context
import androidx.room.Room
import app.yomu.core.database.YomuDatabase
import app.yomu.data.library.CoverStore
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.domain.model.AudioTrack
import app.yomu.domain.model.AudiobookManifest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A feed keeps its own row and updates one audiobook. The tests cover what a listener would notice: a second start
 * does not duplicate the book, a 304 changes nothing, a new episode appears, and a removed episode does not take a
 * downloaded copy or the place of the others with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioSubscriptionRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var db: YomuDatabase
    private lateinit var books: BookRepositoryImpl
    private lateinit var store: AudiobookStore
    private lateinit var repository: AudioSubscriptionRepository

    private fun feed(episodes: String, etag: String? = "\"v1\"", lastModified: String? = null): MockResponse {
        val body = """<?xml version="1.0"?><rss version="2.0"><channel><title>Test cast</title>
            <itunes:author xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">Someone</itunes:author>
            $episodes
        </channel></rss>"""
        return MockResponse()
            .setBody(body)
            .setHeader("Content-Type", "application/rss+xml")
            .apply {
                etag?.let { setHeader("ETag", it) }
                lastModified?.let { setHeader("Last-Modified", it) }
            }
    }

    private fun episode(guid: String, title: String = "Episode $guid", url: String = "https://cdn.example/$guid.mp3", date: String = "Mon, 02 Jan 2023 10:00:00 GMT") =
        """<item><title>$title</title><guid>$guid</guid><enclosure url="$url" type="audio/mpeg"/><pubDate>$date</pubDate><itunes:duration xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">600</itunes:duration></item>"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(context, YomuDatabase::class.java).allowMainThreadQueries().build()
        books = BookRepositoryImpl(context, db, CoverStore(context))
        store = AudiobookStore(context, books, { OkHttpClient() }, YouTubeClient { OkHttpClient() })
        repository = AudioSubscriptionRepository(db, books, store, { OkHttpClient() })
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun url() = server.url("/feed.xml").toString()

    private fun tracksOf(bookId: Long) = runBlocking {
        store.manifest(books.getBook(bookId)!!)?.tracks.orEmpty()
    }

    @Test
    fun `subscribing adds one book and one row, and a second start does not duplicate either`() = runBlocking {
        server.enqueue(feed(episode("a") + episode("b")))
        val first = repository.subscribe(url()).getOrThrow()
        assertEquals("Test cast", first.title)
        assertTrue(first.etag?.isNotBlank() == true)
        assertEquals(1, db.audioSubscriptions().enabled().size)
        assertEquals(2, tracksOf(first.bookId).size)

        server.enqueue(feed(episode("a") + episode("b")))
        val again = repository.subscribe(url()).getOrThrow()
        assertEquals(first.id, again.id)
        assertEquals(1, db.audioSubscriptions().enabled().size)
        // The second call did not even ask the server: the address is already subscribed.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a feed that is not a feed is not saved`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>Not a feed</body></html>").setHeader("Content-Type", "text/html"))
        val result = repository.subscribe(url())
        assertTrue(result.isFailure)
        assertTrue(db.audioSubscriptions().enabled().isEmpty())
        assertEquals(0, db.books().all().size)
    }

    @Test
    fun `a 304 records the check and changes nothing`() = runBlocking {
        server.enqueue(feed(episode("a")))
        val sub = repository.subscribe(url()).getOrThrow()
        val before = tracksOf(sub.bookId)

        server.enqueue(MockResponse().setResponseCode(304))
        val result = repository.refresh(sub.id)
        assertEquals(RefreshResult.NotModified, result)
        assertEquals(before, tracksOf(sub.bookId))
        // The check time moved: the next attempt is not immediate.
        val row = db.audioSubscriptions().get(sub.id)!!
        assertTrue(row.lastCheckedAt >= sub.lastCheckedAt)
    }

    @Test
    fun `the conditional request carries the etag of the first answer`() = runBlocking {
        server.enqueue(feed(episode("a"), etag = "\"abc\""))
        val sub = repository.subscribe(url()).getOrThrow()
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh(sub.id)
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `a new episode is appended without moving the existing ones`() = runBlocking {
        server.enqueue(feed(episode("a", date = "Mon, 02 Jan 2023 10:00:00 GMT") + episode("b", date = "Tue, 03 Jan 2023 10:00:00 GMT")))
        val sub = repository.subscribe(url()).getOrThrow()
        assertEquals(listOf("a", "b"), tracksOf(sub.bookId).map { it.guid })

        server.enqueue(feed(episode("a", date = "Mon, 02 Jan 2023 10:00:00 GMT") + episode("b", date = "Tue, 03 Jan 2023 10:00:00 GMT") + episode("c", date = "Wed, 04 Jan 2023 10:00:00 GMT"), etag = "\"v2\""))
        val result = repository.refresh(sub.id) as RefreshResult.Updated
        assertEquals(1, result.added)
        assertEquals(0, result.removed)
        assertEquals(listOf("a", "b", "c"), tracksOf(sub.bookId).map { it.guid })
    }

    @Test
    fun `a removed episode leaves the list and its downloaded copy is reported, not deleted`() = runBlocking {
        server.enqueue(feed(episode("a") + episode("b", date = "Tue, 03 Jan 2023 10:00:00 GMT")))
        val sub = repository.subscribe(url()).getOrThrow()
        val book = books.getBook(sub.bookId)!!
        val manifest = store.manifest(book)!!
        val downloaded = java.io.File(context.filesDir, "downloaded-a.mp3").apply { writeText("x") }
        store.writeManifest(book, manifest.copy(tracks = manifest.tracks.map { if (it.guid == "a") it.copy(file = downloaded.path) else it }))

        server.enqueue(feed(episode("b", date = "Tue, 03 Jan 2023 10:00:00 GMT"), etag = "\"v2\""))
        val result = repository.refresh(sub.id) as RefreshResult.Updated
        assertEquals(1, result.removed)
        assertEquals(listOf(downloaded.path), result.retainedFiles)
        assertTrue("a downloaded copy was deleted", downloaded.exists())
        assertEquals(listOf("b"), tracksOf(sub.bookId).map { it.guid })
    }

    @Test
    fun `the episode identity keeps a track when only the address changed`() = runBlocking {
        server.enqueue(feed(episode("a", url = "https://cdn.example/old.mp3")))
        val sub = repository.subscribe(url()).getOrThrow()
        server.enqueue(feed(episode("a", url = "https://cdn.example/new.mp3"), etag = "\"v2\""))
        val result = repository.refresh(sub.id) as RefreshResult.Updated
        assertEquals(1, result.updated)
        assertEquals(0, result.added)
        assertEquals("https://cdn.example/new.mp3", tracksOf(sub.bookId).single().uri)
    }

    @Test
    fun `a failing feed reports the cause and keeps what was there`() = runBlocking {
        server.enqueue(feed(episode("a")))
        val sub = repository.subscribe(url()).getOrThrow()
        server.enqueue(MockResponse().setResponseCode(500))
        val result = repository.refresh(sub.id)
        assertTrue("a 500 was reported as $result", result is RefreshResult.Failed)
        assertEquals(listOf("a"), tracksOf(sub.bookId).map { it.guid })
    }

    @Test
    fun `removing the subscription keeps the book unless asked`() = runBlocking {
        server.enqueue(feed(episode("a")))
        val sub = repository.subscribe(url()).getOrThrow()
        repository.remove(sub.id)
        assertTrue(db.audioSubscriptions().enabled().isEmpty())
        assertNotNull(books.getBook(sub.bookId))
        assertNull(db.audioSubscriptions().byFeedUrl(url()))

        server.enqueue(feed(episode("b"), etag = "\"other\""))
        val again = repository.subscribe(url()).getOrThrow()
        // The feed is new again, but the audiobook is the same address: no second book is created.
        assertEquals(sub.bookId, again.bookId)
    }

    @Test
    fun `the manifest keeps the feed guid on every track`() = runBlocking {
        server.enqueue(feed(episode("guid-1")))
        val sub = repository.subscribe(url()).getOrThrow()
        val track: AudioTrack = tracksOf(sub.bookId).single()
        assertEquals("guid-1", track.guid)
        assertTrue(track.publishedAt > 0)
        val manifest: AudiobookManifest? = store.manifest(books.getBook(sub.bookId)!!)
        assertEquals(url(), manifest?.sourceUrl)
    }
}
