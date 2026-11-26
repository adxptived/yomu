package app.yomu.data.catalog

import app.yomu.domain.model.ExternalMetadata
import app.yomu.domain.model.MetadataProvider
import app.yomu.source.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * MangaBaka's public API v2 (C1). Search needs no account or key and already returns covers, titles and descriptions;
 * `schema=full` is only needed for tags, links and relations, which the card does not show yet.
 *
 * The contract follows https://mangabaka.org/developers/guides/first-request: `data` is the list of series, a series
 * has `titles` (one per language, `is_primary` marks the main spelling), `cover`, `published.start_date` and `state`
 * ("merged" records must not be linked: their `merged_with` is the surviving id).
 */
object MangaBakaClient {
    const val API = "https://api.mangabaka.org/v2"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, limit: Int, client: () -> OkHttpClient): List<ExternalMetadata> = withContext(Dispatchers.IO) {
        val url = "$API/series/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim().take(100))
            .addQueryParameter("limit", limit.coerceIn(1, 25).toString())
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Yomu").build()
        client().newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("MangaBaka HTTP ${response.code}")
            parse(response.body.string())
        }
    }

    /** Reads a search answer; a malformed record is skipped instead of failing the whole search. */
    internal fun parse(text: String): List<ExternalMetadata> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            (element as? JsonObject)?.let { record -> runCatching { record.toMetadata() }.getOrNull() }
        }
    }

    private fun JsonObject.toMetadata(): ExternalMetadata? {
        val id = this["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
        // A merged record points at the surviving series; linking it would attach the card to a tombstone.
        if (this["state"]?.jsonPrimitive?.contentOrNull == "merged") return null
        val titles = this["titles"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        val main = pickTitle(titles) ?: return null
        val native = titles.firstOrNull { it["language"]?.jsonPrimitive?.contentOrNull == "ja-Latn" }
            ?.get("title")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != main }
        val cover = this["cover"] as? JsonObject
        val type = this["type"]?.jsonPrimitive?.contentOrNull
        val start = (this["published"] as? JsonObject)?.get("start_date")?.jsonPrimitive?.contentOrNull
        return ExternalMetadata(
            provider = MetadataProvider.MANGABAKA.displayName,
            id = id,
            title = main,
            altTitle = native,
            coverUrl = cover?.let {
                it["x250"]?.jsonPrimitive?.contentOrNull
                    ?: it["x150"]?.jsonPrimitive?.contentOrNull
                    ?: it["raw"]?.jsonPrimitive?.contentOrNull
            },
            description = this["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            url = this["canonical_url"]?.jsonPrimitive?.contentOrNull,
            year = start?.take(4)?.toIntOrNull(),
            format = type?.uppercase(),
            contentType = type,
            provenance = listOfNotNull("title", "cover".takeIf { cover != null }, "description", "year", "format"),
        )
    }

    /** The English main title when there is one, then the native romaji, then any primary spelling. */
    private fun pickTitle(titles: List<JsonObject>): String? {
        fun title(t: JsonObject) = t["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        fun lang(t: JsonObject) = t["language"]?.jsonPrimitive?.contentOrNull
        fun primary(t: JsonObject) = (t["is_primary"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toBoolean() == true
        return titles.firstOrNull { lang(it) == "en" && primary(it) }?.let(::title)
            ?: titles.firstOrNull { lang(it) == "en" }?.let(::title)
            ?: titles.firstOrNull { lang(it) == "ja-Latn" && primary(it) }?.let(::title)
            ?: titles.firstOrNull { primary(it) }?.let(::title)
            ?: titles.firstOrNull()?.let(::title)
    }
}
