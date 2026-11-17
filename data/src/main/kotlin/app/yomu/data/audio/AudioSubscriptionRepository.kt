package app.yomu.data.audio

import app.yomu.core.database.AudioSubscriptionEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.data.library.ImportOutcome
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.domain.audio.AudioSubscription
import app.yomu.domain.audio.AudioSubscriptionReconciler
import app.yomu.domain.audio.EpisodeReconciliation
import app.yomu.domain.audio.SubscriptionEpisode
import app.yomu.domain.model.AudioOrigin
import app.yomu.domain.model.AudiobookManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** What one refresh did; a 304 is a success that changed nothing. */
sealed interface RefreshResult {
    data class Updated(val added: Int, val updated: Int, val removed: Int, val retainedFiles: List<String>) : RefreshResult
    data object NotModified : RefreshResult
    data class Failed(val reason: RefreshFailure) : RefreshResult
}

/** Why a feed could not be refreshed; each one has its own advice in the UI. */
enum class RefreshFailure { NETWORK, NOT_A_FEED, MISSING_BOOK, UNKNOWN }

/**
 * Feeds the user subscribed to.
 *
 * A subscription is a row of its own, not the current track list: the address, the `ETag`/`Last-Modified` state and
 * the book it fills survive a restart and a track being dropped from the feed. A refresh updates the manifest through
 * [AudiobookStore.writeManifest], so the player, the downloader and the position keys keep working unchanged.
 */
class AudioSubscriptionRepository(
    private val db: YomuDatabase,
    private val books: BookRepositoryImpl,
    private val audiobooks: AudiobookStore,
    private val client: () -> OkHttpClient,
) {
    private val dao = db.audioSubscriptions()

    /** One refresh per subscription: a tab and the worker may ask at the same moment. */
    private val locks = ConcurrentHashMap<Long, Mutex>()

    fun observe(): Flow<List<AudioSubscription>> = dao.observe().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): AudioSubscription? = dao.get(id)?.toDomain()

    /**
     * Subscribes to [feedUrl]: the feed is fetched and parsed **before** anything is written, so a wrong address does
     * not create an empty book. Returns the id of an already subscribed feed instead of a second copy.
     */
    suspend fun subscribe(feedUrl: String, title: String? = null): Result<AudioSubscription> = withContext(Dispatchers.IO) {
        val address = feedUrl.trim()
        dao.byFeedUrl(address)?.let { return@withContext Result.success(it.toDomain()) }
        val response = try {
            fetch(address, null, null)
        } catch (e: IOException) {
            return@withContext Result.failure(IOException("network", e))
        }
        val body = response.body ?: return@withContext Result.failure(IOException("not a feed"))
        val manifest = PodcastFeed.parse(body, address) ?: return@withContext Result.failure(IOException("not a feed"))
        val outcome = audiobooks.add(manifest.copy(title = title?.takeIf { it.isNotBlank() } ?: manifest.title), cover = null)
        val bookId = when (outcome) {
            is ImportOutcome.Added -> outcome.bookId
            is ImportOutcome.Duplicate -> outcome.bookId
            is ImportOutcome.Failed -> return@withContext Result.failure(outcome.error)
        }
        val row = AudioSubscriptionEntity(
            feedUrl = address,
            title = title?.takeIf { it.isNotBlank() } ?: manifest.title,
            bookId = bookId,
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            lastCheckedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
        )
        val id = dao.upsert(row)
        Result.success(row.copy(id = if (id > 0) id else dao.byFeedUrl(address)?.id ?: 0).toDomain())
    }

    /**
     * Removes the subscription. The audiobook stays unless [deleteBook] is set: a subscription is the promise to
     * update, not the ownership of what was already added.
     */
    suspend fun remove(id: Long, deleteBook: Boolean = false) = withContext(Dispatchers.IO) {
        val row = dao.get(id) ?: return@withContext
        dao.delete(id)
        if (deleteBook) books.delete(listOf(row.bookId), deleteFiles = false)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    /**
     * Refreshes one subscription with a conditional request.
     *
     * The old track list is read from the manifest, reconciled by episode identity and written back: positions are
     * keyed by track index, so a feed that only appends episodes never moves a saved position.
     */
    suspend fun refresh(id: Long): RefreshResult {
        val lock = locks.getOrPut(id) { Mutex() }
        if (!lock.tryLock()) return RefreshResult.Failed(RefreshFailure.UNKNOWN)
        try {
            return withContext(Dispatchers.IO) { refreshLocked(id) }
        } finally {
            lock.unlock()
        }
    }

    private suspend fun refreshLocked(id: Long): RefreshResult {
        val row = dao.get(id) ?: return RefreshResult.Failed(RefreshFailure.MISSING_BOOK)
        val book = books.getBook(row.bookId) ?: return RefreshResult.Failed(RefreshFailure.MISSING_BOOK)
        val manifest = audiobooks.manifest(book) ?: return RefreshResult.Failed(RefreshFailure.NOT_A_FEED)
        val now = System.currentTimeMillis()
        val response = try {
            fetch(row.feedUrl, row.etag, row.lastModified)
        } catch (e: IOException) {
            Timber.w(e, "Refreshing ${row.feedUrl} failed")
            return RefreshResult.Failed(RefreshFailure.NETWORK)
        }
        // 304: the feed is unchanged. The check time is still recorded, so the next attempt waits again.
        if (response.code == 304) {
            dao.setChecked(id, row.etag, row.lastModified, now)
            return RefreshResult.NotModified
        }
        val body = response.body ?: return RefreshResult.Failed(RefreshFailure.NOT_A_FEED)
        val fetched = PodcastFeed.parse(body, row.feedUrl, AudioOrigin.PODCAST) ?: return RefreshResult.Failed(RefreshFailure.NOT_A_FEED)
        val episodes = fetched.tracks.mapNotNull { track ->
            track.guid?.let { SubscriptionEpisode(it, track.title, track.uri, track.durationMs, track.publishedAt) }
        }
        if (episodes.isEmpty()) return RefreshResult.Failed(RefreshFailure.NOT_A_FEED)
        val reconciliation: EpisodeReconciliation = AudioSubscriptionReconciler.reconcile(manifest.tracks, episodes)
        if (reconciliation.changed) {
            audiobooks.writeManifest(book, manifest.copy(tracks = reconciliation.tracks))
        }
        dao.setChecked(id, response.header("ETag"), response.header("Last-Modified"), now)
        return RefreshResult.Updated(
            added = reconciliation.added,
            updated = reconciliation.updated,
            removed = reconciliation.removed,
            retainedFiles = reconciliation.retainedFiles,
        )
    }

    /** Every enabled subscription, oldest check first; a failure of one does not stop the rest. */
    suspend fun refreshAll(): Map<Long, RefreshResult> = withContext(Dispatchers.IO) {
        dao.enabled().associate { row -> row.id to refresh(row.id) }
    }

    /**
     * A conditional GET. Headers are only sent back to the origin that issued them: a feed whose address redirects to
     * another host gets a plain request there, so a token or a cookie for one server never travels to another.
     */
    private fun fetch(url: String, etag: String?, lastModified: String?): FeedResponse {
        val http = client().newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
            .apply {
                etag?.takeIf { it.isNotBlank() }?.let { header("If-None-Match", it) }
                lastModified?.takeIf { it.isNotBlank() }?.let { header("If-Modified-Since", it) }
            }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 304) throw IOException("status ${response.code}")
            val body = if (response.code == 304) null else runCatching { response.peekBody(PodcastFeed.MAX_FEED_BYTES).string() }.getOrNull()
            return FeedResponse(response.code, body, response.headers)
        }
    }

    private class FeedResponse(val code: Int, val body: String?, private val headers: okhttp3.Headers) {
        fun header(name: String): String? = headers[name]
    }
}

private fun AudioSubscriptionEntity.toDomain() = AudioSubscription(
    id = id,
    feedUrl = feedUrl,
    title = title,
    bookId = bookId,
    etag = etag,
    lastModified = lastModified,
    lastCheckedAt = lastCheckedAt,
    createdAt = createdAt,
    enabled = enabled,
)
