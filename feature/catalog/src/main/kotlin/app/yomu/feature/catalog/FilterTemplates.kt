package app.yomu.feature.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A named snapshot of one source's filters. [state] is the extension's own saved state (the same JSON the screen
 * writes when it leaves); applying it restores the choices the user made under this name.
 */
data class FilterTemplate(val name: String, val state: String)

/** The on-disk format of a source's templates: a small JSON array that survives a damaged file by yielding none. */
object FilterTemplateCodec {
    fun encode(templates: List<FilterTemplate>): String = buildJsonArray {
        templates.forEach { template ->
            add(
                buildJsonObject {
                    put("name", template.name)
                    put("state", template.state)
                },
            )
        }
    }.toString()

    fun decode(text: String): List<FilterTemplate> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val state = obj["state"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            FilterTemplate(name, state)
        }
    }.getOrDefault(emptyList())
}
