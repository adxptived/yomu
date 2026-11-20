package app.yomu.data.download

import android.content.Context
import androidx.core.net.toUri
import app.yomu.data.catalog.ImageSniffer
import app.yomu.data.catalog.NotAnImageException
import app.yomu.data.source.SourceCalls
import app.yomu.source.CatalogSource
import app.yomu.source.HttpStatusException
import app.yomu.source.Page
import app.yomu.source.RetryAfter
import app.yomu.source.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 * Downloads one chapter page image with HTTP Range resume, with the same fail-closed rules as audio downloads.
 *
 * Bytes are always written to a sibling `.part` file; the final path [out] is only touched after the whole image has
 * been received and recognised. A 206 that continues at the exact byte we already have (and whose ETag/Last-Modified
 * still matches) is appended, a 200 or a changed validator truncates and restarts, and a cancelled or failed transfer
 * deletes the `.part` without touching pages that are already complete. When the source owns the HTTP client (cookies,
 * signed URLs, descrambling interceptors) or the server ignores `Range`, the image is restarted from byte 0 — we never
 * claim a resume we cannot prove.
 */
object ResumablePageFetcher {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    /**
     * Saves the image of [page] to [out] and returns its file extension. Transient failures are retried [attempts]
     * times; a truncated `.part` left by an earlier run is continued when the server proves it is still the same file.
     */
    suspend fun fetch(
        context: Context?,
        client: OkHttpClient,
        source: CatalogSource,
        page: Page,
        out: File,
        attempts: Int = 3,
    ): String = withContext(Dispatchers.IO) {
        val part = File(out.parentFile, out.name + ".part")
        val meta = File(out.parentFile, out.name + ".part.meta")
        var state = readMeta(meta)

        // A stale final file must never hold partial bytes; a previous run publishes [out] only when it is complete.
        if (out.exists() && !out.delete()) throw IOException("Cannot remove stale ${out.name}")

        val raf = RandomAccessFile(part, "rw")
        var published = false
        try {
            var pos = raf.length()
            // Without a validator there is no way to prove the bytes on disk match the remote file: fail closed.
            if (pos > 0 && state.validator == null) {
                raf.setLength(0)
                pos = 0
                state = Partial()
                meta.delete()
            }
            var length = state.length
            var attempt = 0
            var restarts = 0

            while (length == null || pos < length) {
                coroutineContext.ensureActive()
                // Signed image addresses are short lived: ask the source again for every attempt.
                val url = page.imageUrl ?: source.getImageUrl(page)
                try {
                    when (val step = fetchOnce(context, client, source, page, url, raf, pos, state, length, meta)) {
                        is Outcome.Restart -> {
                            if (++restarts > MAX_RESTARTS) throw NoResumeException("The server does not support resuming this image")
                            Timber.i("Page image cannot be resumed, restarting from byte 0")
                            raf.setLength(0)
                            pos = 0
                            length = null
                            state = Partial()
                            meta.delete()
                            attempt = 0
                        }
                        is Outcome.Progress -> {
                            state = Partial(validator = step.validator, length = step.length, mime = step.mime)
                            length = step.length
                            saveMeta(meta, state)
                            pos = raf.length()
                            attempt = 0
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NotAnImageException) {
                    // An error page is a permanent answer, not a reason to spend the retry budget.
                    throw e
                } catch (e: NoResumeException) {
                    // The server has already been given MAX_RESTARTS chances; do not loop through them again.
                    throw e
                } catch (e: Throwable) {
                    if (!SourceCalls.isTransient(e) || attempt + 1 >= attempts) throw e
                    Timber.d(e, "Page image attempt ${attempt + 1} failed, retrying")
                    delay(SourceCalls.backoff(RETRY_BASE_MS, attempt))
                    attempt++
                    // A dropped connection may have happened mid-body; fetchOnce saved the validator before writing.
                    state = readMeta(meta)
                    pos = raf.length()
                }
            }

            // Full validation before the bytes are allowed near the final page path.
            if (raf.length() == 0L) throw NotAnImageException("Empty image")
            if (length != null && raf.length() < length) throw IOException("Truncated image: ${raf.length()} of $length bytes")
            if (length != null && raf.length() > length) raf.setLength(length!!)
            raf.close()
            val ext = validate(part, state.mime)
            publish(part, out)
            published = true
            meta.delete()
            return@withContext ext
        } finally {
            runCatching { raf.close() }
            if (!published) {
                // The page being fetched is dropped; pages that finished earlier stay where they are.
                part.delete()
                meta.delete()
            }
        }
    }

    private suspend fun fetchOnce(
        context: Context?,
        client: OkHttpClient,
        source: CatalogSource,
        page: Page,
        url: String,
        raf: RandomAccessFile,
        pos: Long,
        state: Partial,
        knownLength: Long?,
        meta: File,
    ): Outcome {
        if (url.startsWith("content://") || url.startsWith("file://")) {
            // A content provider or a local file has no Range semantics we control: copy it whole.
            val input = context?.contentResolver?.openInputStream(url.toUri()) ?: throw IOException("Cannot open $url")
            var total = 0L
            input.use { src ->
                raf.setLength(0)
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    total += n
                }
            }
            return Outcome.Progress(total, null, null)
        }

        val request = Request.Builder().url(url).apply {
            source.imageHeaders().forEach { (k, v) -> header(k, v) }
            if (pos > 0) {
                header("Range", "bytes=$pos-")
                state.validator?.let { header("If-Range", it) }
            }
        }.build()
        val sourceResponse = source.fetchImage(page, url)
        val response = sourceResponse ?: client.newCall(request).await()
        return response.use { r ->
            if (r.code == 416) {
                // Either everything is already here or the partial belongs to another version of the file.
                if (knownLength != null && pos == knownLength) Outcome.Progress(knownLength, state.validator, state.mime) else Outcome.Restart
            } else {
                if (!r.isSuccessful) throw HttpStatusException(r.code, url, RetryAfter.parse(r.header("Retry-After"), System.currentTimeMillis()))
                val managed = sourceResponse != null
                val append = !managed && pos > 0 && r.code == 206 &&
                    rangeStartMatches(r.header("Content-Range"), pos, knownLength) && !validatorChanged(r, state.validator)
                if (!managed && pos > 0 && r.code == 206 && !append) {
                    // A 206 from another position or another version: never append.
                    Timber.i("Page image 206 cannot be appended (range=${r.header("Content-Range")}), restarting")
                    Outcome.Restart
                } else if (managed && pos > 0) {
                    // A source-owned client cannot send our Range: its body is the whole image, so rewrite from 0.
                    Timber.i("Page image is fetched by the source, restarting from byte 0")
                    writeBody(r, raf, 0L, meta, state)
                } else {
                    writeBody(r, raf, if (append) pos else 0L, meta, state)
                }
            }
        }
    }

    /** Writes a response body at [base], recording the validator before the first byte in case the transfer drops. */
    private suspend fun writeBody(r: Response, raf: RandomAccessFile, base: Long, meta: File, state: Partial): Outcome {
        if (base == 0L) raf.setLength(0)
        val declared = r.body.contentLength().takeIf { it >= 0 }
        val total = contentRangeTotal(r.header("Content-Range"))
            ?: if (r.code == 200) declared?.let { base + it } else null
        val newMime = r.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: state.mime
        val newValidator = RangeResume.ifRange(r.header("ETag"), r.header("Last-Modified")) ?: state.validator
        saveMeta(meta, Partial(newValidator, total, newMime))

        raf.seek(base)
        val buf = ByteArray(BUFFER)
        var written = base
        r.body.byteStream().use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                raf.write(buf, 0, n)
                written += n
            }
        }
        if (r.code == 200 && declared != null && written - base < declared) {
            throw IOException("Connection closed after ${written - base} of $declared bytes")
        }
        if (r.code == 206 && total != null && written < total) {
            throw IOException("Connection closed after $written of $total bytes")
        }
        return Outcome.Progress(total, newValidator, newMime)
    }

    private fun validate(part: File, mime: String?): String {
        if (part.length() == 0L) throw NotAnImageException("Empty image")
        val head = ImageSniffer.head(part)
        return ImageSniffer.extension(head)
            ?: if (ImageSniffer.looksLikeText(head) || mime?.startsWith("text/") == true || mime?.contains("json") == true) {
                throw NotAnImageException("Not an image (${mime ?: "text"})")
            } else {
                when (mime?.substringAfter('/')) {
                    "png" -> "png"
                    "webp" -> "webp"
                    "gif" -> "gif"
                    "avif" -> "avif"
                    "heic" -> "heic"
                    else -> "jpg"
                }
            }
    }

    private fun publish(part: File, out: File) {
        if (out.exists() && !out.delete()) throw IOException("Cannot replace ${out.name}")
        if (!part.renameTo(out)) {
            try {
                part.copyTo(out, overwrite = true)
            } catch (e: Throwable) {
                out.delete() // a failed copy must not leave partial bytes in the final page path
                throw e
            }
            if (!part.delete()) throw IOException("Cannot remove ${part.name}")
        }
    }

    private fun readMeta(file: File): Partial = runCatching {
        if (file.isFile) json.decodeFromString(Partial.serializer(), file.readText()) else Partial()
    }.getOrDefault(Partial())

    private fun saveMeta(file: File, state: Partial) {
        runCatching { file.writeText(json.encodeToString(Partial.serializer(), state)) }
    }

    /** A 206 is only appended when it starts where we stopped and describes the same total file. */
    private fun rangeStartMatches(contentRange: String?, pos: Long, knownLength: Long?): Boolean {
        val value = contentRange?.trim()?.removePrefix("bytes")?.trim() ?: return false
        val start = value.substringBefore('-').trim().toLongOrNull() ?: return false
        if (start != pos) return false
        val total = value.substringAfterLast('/').trim().toLongOrNull() ?: return true
        return knownLength == null || total == knownLength
    }

    private fun contentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()

    /** Refuses to append when the answering server advertises a different version than the one we started with. */
    private fun validatorChanged(r: Response, stored: String?): Boolean {
        if (stored == null) return false
        val current = RangeResume.ifRange(r.header("ETag"), r.header("Last-Modified")) ?: return false
        return current != stored
    }

    /** Resume state of one partial page: enough to prove the remaining bytes belong to the ones on disk. */
    @Serializable
    private data class Partial(
        val validator: String? = null,
        val length: Long? = null,
        val mime: String? = null,
    )

    private sealed class Outcome {
        data class Progress(val length: Long?, val validator: String?, val mime: String?) : Outcome()
        object Restart : Outcome()
    }

    /** The server cannot resume and keeps answering from another position: stop instead of retrying forever. */
    private class NoResumeException(message: String) : IOException(message)

    private const val BUFFER = 64 * 1024
    private const val RETRY_BASE_MS = 800L
    private const val MAX_RESTARTS = 4
}
