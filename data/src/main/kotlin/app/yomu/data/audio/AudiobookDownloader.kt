package app.yomu.data.audio

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.yomu.data.R
import app.yomu.data.download.FailureKind
import app.yomu.data.download.RangeResume
import app.yomu.data.download.RetryPolicy
import app.yomu.data.source.SourceCalls
import app.yomu.data.work.Notifications
import app.yomu.domain.model.AudioTrack
import app.yomu.domain.model.AudiobookManifest
import app.yomu.domain.model.Book
import app.yomu.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** How much of an online audiobook is kept on the device. */
data class AudioDownloadState(val downloaded: Int, val total: Int, val bytes: Long) {
    val complete: Boolean get() = total > 0 && downloaded == total
}

/** A running download: [fraction] of the book, [track] being fetched (0-based). */
data class AudioDownloadProgress(
    val running: Boolean,
    val fraction: Float,
    val track: Int,
    val total: Int,
    val error: String?,
    /** Queued for the network or for a retry after a transient error (nothing is transferred right now). */
    val waiting: Boolean = false,
)

/** An offline download of one audiobook in the queue (see [AudiobookDownloader.queue]). */
data class AudioQueueEntry(val bookId: Long, val progress: AudioDownloadProgress)

/**
 * Offline copies of online audiobooks (YouTube, podcasts, catalog streams): every track is saved next to the book's
 * manifest and recorded in [AudioTrack.file], so the player uses it instead of the network. Downloads resume where
 * they stopped.
 */
class AudiobookDownloader(
    private val context: Context,
    private val store: AudiobookStore,
    private val client: () -> OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    /** Null when [book] is not an online audiobook (its files are already on the device). */
    fun state(book: Book): AudioDownloadState? {
        val m = runCatching { store.manifest(book) }.getOrNull() ?: return null
        if (store.downloadDir(book) == null || m.tracks.none { isRemote(it.uri) }) return null
        val remote = m.tracks.filter { isRemote(it.uri) }
        val files = remote.mapNotNull { t -> AudiobookStore.playableUri(t).takeIf { it != t.uri }?.let { File(it.removePrefix("file://")) } }
        return AudioDownloadState(files.size, remote.size, files.sumOf { it.length() })
    }

    /** Downloads the missing tracks of [book]; [onProgress] gets the book fraction and the track index. */
    suspend fun download(book: Book, onProgress: suspend (Float, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val dir = store.downloadDir(book) ?: error("Not an online audiobook")
        dir.mkdirs()
        var manifest = store.manifest(book) ?: error("Not an online audiobook")
        val todo = manifest.tracks.indices.filter { isRemote(manifest.tracks[it].uri) }
        todo.forEachIndexed { n, i ->
            val track = manifest.tracks[i]
            if (AudiobookStore.playableUri(track) != track.uri) return@forEachIndexed
            onProgress(n.toFloat() / todo.size, i, manifest.tracks.size)
            val file = fetch(track, dir, i) { f -> onProgress((n + f) / todo.size, i, manifest.tracks.size) }
            // Saved after every track: a stopped download keeps what it has.
            manifest = manifest.copy(tracks = manifest.tracks.toMutableList().also { it[i] = track.copy(file = "file://" + file.absolutePath) })
            store.writeManifest(book, manifest)
        }
        onProgress(1f, manifest.tracks.lastIndex, manifest.tracks.size)
    }

    /** Deletes the offline copies; the book streams again. */
    suspend fun remove(book: Book) = withContext(Dispatchers.IO) {
        val m = store.manifest(book) ?: return@withContext
        store.writeManifest(book, m.copy(tracks = m.tracks.map { it.copy(file = null) }))
        store.downloadDir(book)?.deleteRecursively()
    }

    private suspend fun fetch(track: AudioTrack, dir: File, index: Int, progress: suspend (Float) -> Unit): File {
        val youtubeId = AudiobookManifest.youtubeId(track.uri)
        val (url, headers, type, knownLength) = if (youtubeId != null) {
            val s = store.youtube.stream(youtubeId, progressive = true)
            Source(s.url, s.headers, s.mimeType, s.contentLength)
        } else {
            Source(track.uri, emptyMap(), null, null)
        }
        val base = "%04d".format(index + 1)
        val part = File(dir, "$base.part")
        val meta = File(dir, "$base.part.meta")
        val itag = itagOf(url)
        var state = readPartial(meta)
        // Another representation of the same video (a different itag or container) is a different file: the bytes
        // already on disk cannot be continued with it.
        if (part.exists() && (state.itag != itag || (type != null && state.mime != null && state.mime != type))) {
            part.delete()
            meta.delete()
            state = Partial()
        }
        var length = state.length ?: knownLength
        var mime = type ?: state.mime
        // YouTube slows down long single requests: it is fetched in 10 MB ranges, like its own players do.
        val chunk = if (youtubeId != null) YOUTUBE_CHUNK else Long.MAX_VALUE
        RandomAccessFile(part, "rw").use { out ->
            var pos = out.length()
            var restarts = 0
            // Continuing without a validator cannot prove that the remote file is still the one on disk.
            if (pos > 0 && state.validator == null) {
                out.setLength(0)
                pos = 0
            }
            while (length.let { it == null || pos < it }) {
                coroutineContext.ensureActive()
                // A failed request (network switch, VPN, 5xx) is retried from the bytes already on disk. A response
                // that cannot be continued (another range, another file, 416) restarts the file instead.
                val restart = SourceCalls.retrying(REQUEST_ATTEMPTS, baseDelayMs = RETRY_BASE_MS) {
                    pos = out.length()
                    val end = if (chunk == Long.MAX_VALUE) "" else ((length?.let { minOf(it, pos + chunk) } ?: (pos + chunk)) - 1).toString()
                    val request = Request.Builder().url(url).apply {
                        headers.forEach { (k, v) -> header(k, v) }
                        if (pos > 0 || end.isNotEmpty()) header("Range", "bytes=$pos-$end")
                        if (pos > 0) state.validator?.let { header("If-Range", it) }
                    }.build()
                    client().newCall(request).execute().use { r ->
                        if (r.code == 416) {
                            // Everything is already here, or the partial file belongs to another version of the file.
                            if (length != null && pos == length) return@use false
                            out.setLength(0)
                            state = Partial()
                            meta.delete()
                            length = knownLength
                            return@use true
                        }
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        if (r.code == 206) {
                            val start = contentRangeStart(r.header("Content-Range"))
                            val total = totalOf(r)
                            if (pos == 0L || start == null || start != pos || (total != null && length != null && total != length)) {
                                // The server did not continue at our position, or the file changed since the first bytes.
                                out.setLength(0)
                                state = Partial()
                                meta.delete()
                                length = knownLength
                                return@use true
                            }
                        } else if (pos > 0) {
                            // The range was ignored (or If-Range matched another file): the body is the whole file.
                            out.setLength(0)
                            pos = 0
                        }
                        mime = mime ?: r.body.contentType()?.let { "${it.type}/${it.subtype}" }
                        length = length ?: totalOf(r) ?: if (r.code == 200) r.body.contentLength().takeIf { it >= 0 } else null
                        // Saved before the body is written: a crash leaves a file that can still be checked.
                        state = Partial(
                            validator = RangeResume.ifRange(r.header("ETag"), r.header("Last-Modified")) ?: state.validator,
                            length = length,
                            itag = itag,
                            mime = mime,
                        )
                        runCatching { meta.writeText(json.encodeToString(Partial.serializer(), state)) }
                        out.seek(pos)
                        val buffer = ByteArray(64 * 1024)
                        r.body.byteStream().use { input ->
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                pos += n
                                val now = System.currentTimeMillis()
                                if (now - lastReport > REPORT_MS) {
                                    coroutineContext.ensureActive()
                                    lastReport = now
                                    length?.let { progress((pos.toFloat() / it).coerceIn(0f, 1f)) }
                                }
                            }
                        }
                        // Without a known length, one whole response is the file.
                        if (length == null) length = pos
                        false
                    }
                }
                if (restart) {
                    if (++restarts > MAX_RESTARTS) throw IOException("The server keeps answering from another position")
                    pos = 0
                    continue
                }
            }
            // A server that sent more bytes than the file has (an overlapping range) must not leave a longer file.
            length?.let { if (out.length() > it) out.setLength(it) }
        }
        val file = File(dir, "$base.${extension(mime, track.uri)}")
        if (!part.renameTo(file)) throw IOException("Cannot save ${file.name}")
        meta.delete()
        return file
    }

    /** Resume state of a partial track: enough to prove that the remote bytes still match the local ones. */
    @kotlinx.serialization.Serializable
    private data class Partial(
        val validator: String? = null,
        val length: Long? = null,
        val itag: String? = null,
        val mime: String? = null,
    )

    private fun readPartial(file: File): Partial = runCatching {
        if (file.isFile) json.decodeFromString(Partial.serializer(), file.readText()) else Partial()
    }.getOrDefault(Partial())

    private data class Source(val url: String, val headers: Map<String, String>, val mimeType: String?, val length: Long?)

    // ---------------------------------------------------------------- work

    fun start(bookId: Long, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<AudiobookDownloadWorker>()
            .setInputData(workDataOf(AudiobookDownloadWorker.KEY_BOOK to bookId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORK_BACKOFF_MS, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .addTag(BOOK_TAG + bookId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(bookId), ExistingWorkPolicy.KEEP, request)
    }

    /** Forgets finished (failed) downloads so they leave the queue list. */
    fun dismissFinished() {
        WorkManager.getInstance(context).pruneWork()
    }

    fun cancel(bookId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(bookId))
    }

    /** The download of [bookId] while it is queued or running, then its last result (null when there is none). */
    fun progress(bookId: Long): Flow<AudioDownloadProgress?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(bookId)).map { infos ->
            infos.lastOrNull()?.let(::toProgress)
        }

    /** Every audiobook download that is queued, running or failed, in the order WorkManager lists them. */
    fun queue(): Flow<List<AudioQueueEntry>> = flow {
        // WorkManager is touched when collected, so building the flow works before it is initialised.
        emitAll(WorkManager.getInstance(context).getWorkInfosByTagFlow(TAG))
    }.map { infos ->
        infos.filter { it.state != WorkInfo.State.CANCELLED && it.state != WorkInfo.State.SUCCEEDED }.mapNotNull { info ->
            val id = info.tags.firstNotNullOfOrNull { it.removePrefix(BOOK_TAG).takeIf { s -> s != it }?.toLongOrNull() }
            id?.let { AudioQueueEntry(it, toProgress(info)) }
        }
    }.catch { e -> if (e is IllegalStateException) emit(emptyList()) else throw e }

    private fun toProgress(info: WorkInfo) = AudioDownloadProgress(
        running = !info.state.isFinished,
        fraction = info.progress.getFloat(AudiobookDownloadWorker.KEY_PROGRESS, 0f),
        track = info.progress.getInt(AudiobookDownloadWorker.KEY_TRACK, 0),
        total = info.progress.getInt(AudiobookDownloadWorker.KEY_TOTAL, 0),
        error = if (info.state == WorkInfo.State.FAILED) info.outputData.getString(AudiobookDownloadWorker.KEY_ERROR) ?: "" else null,
        waiting = info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED,
    )

    companion object {
        private const val YOUTUBE_CHUNK = 10L * 1024 * 1024
        private const val REPORT_MS = 500L

        /** How many times a restart from byte 0 is accepted before the download is reported as broken. */
        private const val MAX_RESTARTS = 4

        private const val TAG = "audiobook_download"
        private const val BOOK_TAG = "audiobook_book:"
        private const val REQUEST_ATTEMPTS = 4
        private const val RETRY_BASE_MS = 2_000L
        private const val WORK_BACKOFF_MS = 30_000L

        /** Runs of the worker that retry transient errors before the download is reported as failed. */
        const val MAX_RUNS = 6

        fun workName(bookId: Long) = "audiobook_download_$bookId"

        fun isRemote(uri: String) = uri.startsWith("http://") || uri.startsWith("https://") || AudiobookManifest.youtubeId(uri) != null

        /** The total size from `Content-Range: bytes 0-99/1234`. */
        internal fun totalOf(r: okhttp3.Response): Long? = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()

        /** The first byte position from `Content-Range: bytes 100-199/1234`. */
        internal fun contentRangeStart(value: String?): Long? =
            value?.trim()?.removePrefix("bytes")?.trim()?.substringBefore('-')?.trim()?.toLongOrNull()

        /** YouTube's representation id (`itag`) from a stream address. */
        internal fun itagOf(url: String): String? =
            url.substringAfter('?', "").substringBefore('#').split('&').firstOrNull { it.startsWith("itag=") }?.substringAfter('=')

        internal fun extension(mime: String?, uri: String): String {
            val fromUri = uri.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
            return when (mime?.substringBefore(';')?.trim()?.lowercase()) {
                "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
                "audio/webm" -> "webm"
                "video/mp4" -> "mp4"
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/ogg" -> "ogg"
                "audio/opus" -> "opus"
                "audio/aac" -> "aac"
                "audio/flac" -> "flac"
                else -> fromUri.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "audio"
            }
        }
    }
}

class AudiobookDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val books: BookRepository by inject()
    private val downloader: AudiobookDownloader by inject()

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong(KEY_BOOK, -1)
        val book = books.getBook(bookId) ?: return Result.failure()
        val title = applicationContext.getString(R.string.notif_audio_downloading, book.title)
        runCatching { setForeground(info(title, 0f)) }
        var lastShown = 0L
        return runCatching {
            downloader.download(book) { fraction, track, total ->
                setProgress(workDataOf(KEY_PROGRESS to fraction, KEY_TRACK to track, KEY_TOTAL to total))
                val now = System.currentTimeMillis()
                if (now - lastShown >= NOTIFY_MS) {
                    lastShown = now
                    runCatching { setForeground(info(title, fraction, applicationContext.getString(R.string.notif_audio_track, track + 1, total))) }
                }
            }
        }.fold(
            onSuccess = {
                Notifications.notify(
                    applicationContext, Notifications.ID_AUDIO_DOWNLOAD_DONE,
                    Notifications.builder(applicationContext, Notifications.DOWNLOADS)
                        .setContentTitle(applicationContext.getString(R.string.notif_audio_downloaded, book.title)).setAutoCancel(true),
                )
                Result.success()
            },
            onFailure = { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Network trouble (also a VPN switch) is retried later with WorkManager's backoff instead of failing the book.
                if (RetryPolicy.classify(e) == FailureKind.TRANSIENT && runAttemptCount + 1 < AudiobookDownloader.MAX_RUNS) return Result.retry()
                timber.log.Timber.w(e, "Audiobook download failed")
                Notifications.notify(
                    applicationContext, Notifications.ID_AUDIO_DOWNLOAD_DONE,
                    Notifications.builder(applicationContext, Notifications.DOWNLOADS)
                        .setContentTitle(applicationContext.getString(R.string.notif_audio_download_failed, book.title))
                        .setContentText(e.message).setAutoCancel(true),
                )
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
            },
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(applicationContext.getString(R.string.notif_downloading), 0f)

    private fun info(title: String, fraction: Float, text: String? = null) =
        Notifications.foreground(applicationContext, Notifications.ID_AUDIO_DOWNLOAD, Notifications.DOWNLOADS, title, fraction, text)

    companion object {
        const val KEY_BOOK = "book"
        const val KEY_PROGRESS = "progress"
        const val KEY_TRACK = "track"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val NOTIFY_MS = 1_000L
    }
}
