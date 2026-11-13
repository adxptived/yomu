package app.yomu.feature.annotations

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** A note that has not been saved yet: what was typed, where, and when. */
data class NoteDraft(
    val key: String,
    val bookId: Long,
    val chapter: Int,
    val offset: Int,
    val text: String,
    val updatedAt: Long,
)

/**
 * Drafts of note dialogs on disk, keyed by the book and the anchor (a selection or a page). A draft survives closing
 * the dialog, rotation and a killed process, so typed text is not lost when Save was not pressed; it is removed only
 * after the note itself was written.
 */
class NoteDrafts(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "note_drafts.json"))

    fun get(key: String): String? = read().firstOrNull { it.key == key }?.text?.takeIf { it.isNotBlank() }

    fun draft(key: String): NoteDraft? = read().firstOrNull { it.key == key }

    fun put(key: String, bookId: Long, chapter: Int, offset: Int, text: String) {
        if (text.isBlank()) {
            remove(key)
            return
        }
        val draft = NoteDraft(key, bookId, chapter, offset, text, System.currentTimeMillis())
        val now = read().filterNot { it.key == key } + draft
        write(now.takeLast(MAX_DRAFTS))
    }

    fun remove(key: String) {
        val list = read()
        if (list.none { it.key == key }) return
        write(list.filterNot { it.key == key })
    }

    fun forBook(bookId: Long): List<NoteDraft> = read().filter { it.bookId == bookId }

    private fun read(): List<NoteDraft> = synchronized(this) {
        if (!file.exists()) return emptyList()
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            Json.parseToJsonElement(file.readText()).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val key = o["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val updatedAt = o["at"]?.jsonPrimitive?.longOrNull ?: 0
                if (updatedAt in 1 until cutoff) return@mapNotNull null
                NoteDraft(
                    key = key,
                    bookId = o["book"]?.jsonPrimitive?.longOrNull ?: -1,
                    chapter = o["chapter"]?.jsonPrimitive?.intOrNull ?: 0,
                    offset = o["offset"]?.jsonPrimitive?.intOrNull ?: 0,
                    text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    updatedAt = updatedAt,
                )
            }.filter { it.text.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<NoteDraft>) = synchronized(this) {
        val arr: List<JsonElement> = list.map {
            buildJsonObject {
                put("key", it.key)
                put("book", it.bookId)
                put("chapter", it.chapter)
                put("offset", it.offset)
                put("text", it.text)
                put("at", it.updatedAt)
            }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JsonArray(arr).toString())
        }
        Unit
    }

    private companion object {
        const val MAX_DRAFTS = 50
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
