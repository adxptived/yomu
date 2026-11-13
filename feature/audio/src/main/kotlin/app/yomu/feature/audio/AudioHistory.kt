package app.yomu.feature.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One manual jump in an audiobook: from where and to where, why, and when. Written before a seek, a chapter change or
 * a jump to a bookmark, so "back to where I was" does not depend on remembering the time.
 */
@Serializable
data class AudioPositionEvent(
    val fromTrack: Int,
    val fromMs: Long,
    val toTrack: Int,
    val toMs: Long,
    /** seek, back, forward, chapter, bookmark or return. */
    val reason: String,
    val at: Long,
) {
    val isJump: Boolean get() = fromTrack != toTrack || fromMs != toMs
}

object AudioHistory {
    const val SEEK = "seek"
    const val BACK = "back"
    const val FORWARD = "forward"
    const val CHAPTER = "chapter"
    const val BOOKMARK = "bookmark"
    const val RETURN = "return"

    /** Events kept per book; the oldest are dropped first. */
    const val LIMIT = 100

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AudioPositionEvent.serializer())

    fun encode(list: List<AudioPositionEvent>): String = json.encodeToString(serializer, list)

    fun decode(raw: String?): List<AudioPositionEvent> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /**
     * [list] with [event] added: a short repeat of the same kind of jump from the same point grows the previous event
     * instead of filling the journal with every small swipe.
     */
    fun append(list: List<AudioPositionEvent>, event: AudioPositionEvent): List<AudioPositionEvent> {
        val last = list.lastOrNull()
        val merged = if (last != null && last.reason == event.reason &&
            event.at - last.at < MERGE_WINDOW_MS && last.toTrack == event.fromTrack && last.toMs == event.fromMs
        ) {
            list.dropLast(1) + event
        } else {
            list + event
        }
        return merged.takeLast(LIMIT)
    }

    private const val MERGE_WINDOW_MS = 5_000L
}

/** The journal on disk: one small JSON file per book, kept out of progress and sync. */
class AudioHistoryStore(context: Context) {
    private val dir = File(context.filesDir, "audio_history").apply { mkdirs() }

    private fun file(bookId: Long) = File(dir, "$bookId.json")

    suspend fun read(bookId: Long): List<AudioPositionEvent> = withContext(Dispatchers.IO) {
        synchronized(this@AudioHistoryStore) {
            runCatching { AudioHistory.decode(file(bookId).takeIf { it.exists() }?.readText()) }.getOrDefault(emptyList())
        }
    }

    suspend fun write(bookId: Long, list: List<AudioPositionEvent>) = withContext(Dispatchers.IO) {
        synchronized(this@AudioHistoryStore) {
            runCatching { file(bookId).writeText(AudioHistory.encode(list)) }
        }
        Unit
    }

    suspend fun clear(bookId: Long) = withContext(Dispatchers.IO) {
        synchronized(this@AudioHistoryStore) { file(bookId).delete() }
        Unit
    }
}
