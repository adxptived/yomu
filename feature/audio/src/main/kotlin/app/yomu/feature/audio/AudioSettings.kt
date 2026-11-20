package app.yomu.feature.audio

import app.yomu.domain.audio.AudioOffline
import app.yomu.domain.audio.AudioOfflinePlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A saved spot in an audiobook: [track] and [positionMs] within it, with an optional [note]. */
data class Bookmark(val track: Int, val positionMs: Long, val note: String = "", val createdAt: Long = 0)

/** What happens when playback resumes after a pause: nothing, the adaptive table, or a fixed number of seconds. */
enum class RewindMode { OFF, ADAPTIVE, CUSTOM }

/**
 * Per-book metadata overrides: what the reader knows better than the tags or the feed. Empty values keep the
 * library's own title, author and description, so an override never blanks a field by accident.
 */
data class AudioMetadata(
    val narrator: String? = null,
    val series: String? = null,
    val language: String? = null,
    val description: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(narrator, series, language, description).all { it.isNullOrBlank() }
}

/** Per-book player settings, kept under `"audio"` in the book's reader settings JSON. */
data class AudioSettings(
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val skipBackSec: Int = DEFAULT_SKIP_BACK,
    val skipForwardSec: Int = DEFAULT_SKIP_FORWARD,
    val bookmarks: List<Bookmark> = emptyList(),
    /** What the offline sheet downloads; the choice is the book's, so it survives reopening the sheet. */
    val offlinePlan: AudioOfflinePlan = AudioOfflinePlan.All,
    /** Adaptive is the old behaviour: an existing book keeps it after the upgrade. */
    val rewindMode: RewindMode = RewindMode.ADAPTIVE,
    val rewindSeconds: Int = DEFAULT_REWIND_SECONDS,
    /** Corrections to the card and the lock screen; the book title and author stay the library's. */
    val metadata: AudioMetadata = AudioMetadata(),
    /** Off by default: normalisation changes loudness, so it is the reader's choice. */
    val gainEnabled: Boolean = false,
) {

    /** [json] with these settings in place of the old ones; other keys stay. */
    fun merge(json: String?): String {
        val old = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())
        val audio = buildJsonObject {
            put("speed", speed)
            put("skipSilence", skipSilence)
            put("skipBack", skipBackSec)
            put("skipForward", skipForwardSec)
            put("bookmarks", encodeBookmarks(bookmarks))
            put("offline", encodePlan(offlinePlan))
            put("rewindMode", rewindMode.name.lowercase())
            put("rewindSeconds", rewindSeconds.coerceIn(REWIND_MIN, REWIND_MAX))
            put("gainEnabled", gainEnabled)
            if (!metadata.isEmpty) put("metadata", encodeMetadata(metadata))
        }
        return JsonObject(old + ("audio" to audio)).toString()
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        const val DEFAULT_SKIP_BACK = 10
        const val DEFAULT_SKIP_FORWARD = 30
        const val DEFAULT_REWIND_SECONDS = 10
        const val REWIND_MIN = 1
        const val REWIND_MAX = 120
        val SKIP_BACK_OPTIONS = listOf(5, 10, 15, 30)
        val SKIP_FORWARD_OPTIONS = listOf(10, 15, 30, 60)
        val REWIND_OPTIONS = listOf(3, 5, 10, 15, 30)

        fun of(json: String?): AudioSettings {
            val audio = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject["audio"]?.jsonObject }.getOrNull() } ?: return AudioSettings()
            return AudioSettings(
                speed = audio["speed"]?.jsonPrimitive?.floatOrNull?.coerceIn(MIN_SPEED, MAX_SPEED) ?: 1f,
                skipSilence = audio["skipSilence"]?.jsonPrimitive?.booleanOrNull ?: false,
                skipBackSec = audio["skipBack"]?.jsonPrimitive?.intOrNull?.takeIf { it in SKIP_BACK_OPTIONS } ?: DEFAULT_SKIP_BACK,
                skipForwardSec = audio["skipForward"]?.jsonPrimitive?.intOrNull?.takeIf { it in SKIP_FORWARD_OPTIONS } ?: DEFAULT_SKIP_FORWARD,
                bookmarks = decodeBookmarks(audio["bookmarks"]?.let { runCatching { it.jsonArray }.getOrNull() }),
                offlinePlan = decodePlan(audio["offline"]?.let { runCatching { it.jsonObject }.getOrNull() }),
                // Migration: a book saved before rewind modes existed keeps the adaptive behaviour it had.
                rewindMode = audio["rewindMode"]?.jsonPrimitive?.contentOrNull
                    ?.let { name -> RewindMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                    ?: RewindMode.ADAPTIVE,
                rewindSeconds = audio["rewindSeconds"]?.jsonPrimitive?.intOrNull?.coerceIn(REWIND_MIN, REWIND_MAX)
                    ?: DEFAULT_REWIND_SECONDS,
                metadata = decodeMetadata(audio["metadata"]?.let { runCatching { it.jsonObject }.getOrNull() }),
                gainEnabled = audio["gainEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

        /** Metadata overrides as their own object; empty fields are omitted instead of saved as blanks. */
        fun encodeMetadata(meta: AudioMetadata): JsonObject = buildJsonObject {
            meta.narrator?.takeIf { it.isNotBlank() }?.let { put("narrator", it.trim()) }
            meta.series?.takeIf { it.isNotBlank() }?.let { put("series", it.trim()) }
            meta.language?.takeIf { it.isNotBlank() }?.let { put("language", it.trim()) }
            meta.description?.takeIf { it.isNotBlank() }?.let { put("description", it.trim()) }
        }

        fun decodeMetadata(json: JsonObject?): AudioMetadata {
            fun value(key: String): String? =
                json?.get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            return AudioMetadata(
                narrator = value("narrator"),
                series = value("series"),
                language = value("language"),
                description = value("description"),
            )
        }

        /** The plan as a compact object of its own: an unknown mode falls back to the whole book, never to nothing. */
        fun encodePlan(plan: AudioOfflinePlan): JsonObject = buildJsonObject {
            when (plan) {
                AudioOfflinePlan.All -> put("mode", "all")
                is AudioOfflinePlan.Next -> {
                    put("mode", "next")
                    put("count", plan.count)
                }
                is AudioOfflinePlan.Selected -> {
                    put("mode", "selected")
                    put("tracks", JsonArray(plan.tracks.sorted().map { JsonPrimitive(it) }))
                }
            }
        }

        fun decodePlan(json: JsonObject?): AudioOfflinePlan {
            val mode = json?.get("mode")?.jsonPrimitive?.contentOrNull ?: return AudioOfflinePlan.All
            return when (mode) {
                "next" -> AudioOffline.next(json["count"]?.jsonPrimitive?.intOrNull ?: AudioOffline.NEXT_DEFAULT)
                "selected" -> AudioOfflinePlan.Selected(
                    json["tracks"]?.let { runCatching { it.jsonArray }.getOrNull() }
                        ?.mapNotNull { it.jsonPrimitive.intOrNull }
                        ?.filter { it >= 0 }
                        ?.toSet()
                        .orEmpty(),
                )
                else -> AudioOfflinePlan.All
            }
        }

        fun encodeBookmarks(list: List<Bookmark>): JsonArray = JsonArray(
            list.map {
                buildJsonObject {
                    put("track", it.track)
                    put("ms", it.positionMs)
                    put("note", it.note)
                    put("at", it.createdAt)
                }
            },
        )

        fun decodeBookmarks(array: JsonArray?): List<Bookmark> = array.orEmpty().mapNotNull { e ->
            val o = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val track = o["track"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val ms = o["ms"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            if (track < 0 || ms < 0) return@mapNotNull null
            Bookmark(track, ms, o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["at"]?.jsonPrimitive?.longOrNull ?: 0)
        }.sortedWith(compareBy({ it.track }, { it.positionMs }))

        fun decodeBookmarks(json: String?): List<Bookmark> =
            decodeBookmarks(json?.let { runCatching { Json.parseToJsonElement(it).jsonArray }.getOrNull() })
    }
}
