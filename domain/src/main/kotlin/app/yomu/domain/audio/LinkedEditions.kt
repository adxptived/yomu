package app.yomu.domain.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A manual link between the text and the audio edition of the same work.
 *
 * The two books stay separate library entries; only a small id is kept in each book's reader settings, so backup and
 * export carry it with the book. Opening the other edition uses its own last position — no phrase-level alignment is
 * promised, because none exists.
 */
object LinkedEditions {
    private const val KEY = "linkedEdition"

    fun linkedId(json: String?): Long? {
        val root = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        return root[KEY]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
    }

    /** [json] with the link set, or removed when [id] is null; every other setting is kept. */
    fun withLink(json: String?, id: Long?): String {
        val root = json?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())
        val next: JsonObject = if (id == null || id <= 0) JsonObject(root - KEY) else JsonObject(root + (KEY to kotlinx.serialization.json.JsonPrimitive(id)))
        return next.toString()
    }

    /** The two book ids to write for a symmetric link. */
    fun pair(first: Long, second: Long): List<Pair<Long, Long>> = listOf(first to second, second to first)
}
