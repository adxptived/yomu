package app.yomu.domain.glossary

import app.yomu.domain.model.Locator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import java.text.Normalizer
import java.util.Locale

/** What a glossary entry describes. Unknown names from older exports fall back to [TERM]. */
enum class GlossaryKind {
    CHARACTER, TERM, PLACE;

    companion object {
        fun from(name: String?): GlossaryKind = entries.firstOrNull { it.name == name?.trim()?.uppercase(Locale.ROOT) } ?: TERM
    }
}

/**
 * A character/term/place note. The id is its stable identity inside one installation; [bookId] and [locator] say
 * where it applies and are both optional (a note may describe a work as a whole). Matching uses [GlossaryIndex.keysOf],
 * never the display spelling, so a renamed or re-cased entry is still the same note.
 */
data class GlossaryEntry(
    val id: Long = 0,
    val bookId: Long? = null,
    val locator: Locator? = null,
    val term: String,
    val aliases: List<String> = emptyList(),
    val kind: GlossaryKind = GlossaryKind.TERM,
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Stored form: strings only, so an unknown kind cannot discard the rest of an imported entry. */
@Serializable
data class GlossaryEntryData(
    val id: Long = 0,
    val bookId: Long? = null,
    val locatorJson: String? = null,
    val term: String = "",
    val aliases: List<String> = emptyList(),
    val kind: String = "TERM",
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/**
 * Pure logic for glossary identity, matching and backup round trips. No Android or Room dependency: the data layer
 * maps [GlossaryEntry] to its entity and calls [merge] when importing a backup.
 */
object GlossaryIndex {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Case- and accent-insensitive, with whitespace and punctuation at the edges removed. */
    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        val plain = decomposed.replace(Regex("\\p{M}"), "")
        return plain.lowercase(Locale.ROOT).replace(Regex("[\\s\\u00A0]+"), " ").trim()
    }

    /** The names by which an entry can be found: its term plus every alias, normalised. */
    fun keysOf(term: String, aliases: List<String>): Set<String> = buildSet {
        add(normalize(term))
        aliases.forEach { add(normalize(it)) }
    }.filter { it.isNotEmpty() }.toSet()

    fun keys(entry: GlossaryEntry): Set<String> = keysOf(entry.term, entry.aliases)

    /** Two entries are the same note when they belong to the same book and share any normalised name. */
    fun sameIdentity(a: GlossaryEntry, b: GlossaryEntry): Boolean {
        if (a.bookId != b.bookId) return false
        val ka = keys(a)
        val kb = keys(b)
        if (ka.isEmpty() && kb.isEmpty()) return true
        return ka.any { it in kb }
    }

    fun matches(entry: GlossaryEntry, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return keys(entry).any { it.contains(q) } || entry.note?.contains(query, ignoreCase = true) == true
    }

    data class MergeResult(
        val entries: List<GlossaryEntry>,
        val added: Int = 0,
        val merged: Int = 0,
        val skipped: Int = 0,
    ) {
        val total: Int get() = entries.size
    }

    /**
     * Merge imported entries into the local ones without duplicates. [replace] starts from an empty list (the
     * explicit "replace" backup choice); otherwise the local row wins ties and keeps its id, so identities stay
     * stable. A newer incoming entry updates the note but inherits the local id.
     */
    fun merge(local: List<GlossaryEntry>, incoming: List<GlossaryEntry>, replace: Boolean = false): MergeResult {
        val result = ArrayList<GlossaryEntry>()
        if (!replace) result.addAll(local)
        var added = 0
        var merged = 0
        var skipped = 0
        incoming.forEach { entry ->
            val index = result.indexOfFirst { sameIdentity(it, entry) }
            if (index < 0) {
                result += entry
                added++
            } else {
                val existing = result[index]
                if (entry.updatedAt > existing.updatedAt) {
                    // Keep the local identity AND its aliases: dropping them would lose the keys that make later
                    // imports recognise the same note (the local aliases are not invalidated by a newer note).
                    // A renamed term is kept as an alias too, so the old lookup key keeps matching imports that
                    // still use it (e.g. local "Elena" updated by "Léna" must still recognise a later "Elena").
                    val carriedAliases = (listOf(existing.term) + existing.aliases + entry.aliases)
                        .distinctBy { normalize(it) }
                    result[index] = entry.copy(
                        id = existing.id,
                        aliases = carriedAliases,
                        createdAt = existing.createdAt.takeIf { it != 0L } ?: entry.createdAt,
                    )
                    merged++
                } else {
                    result[index] = existing.copy(
                        aliases = (existing.aliases + entry.aliases).distinct(),
                        note = existing.note ?: entry.note,
                    )
                    skipped++
                }
            }
        }
        return MergeResult(result, added, merged, skipped)
    }

    fun export(entries: List<GlossaryEntry>): String =
        json.encodeToString(ListSerializer(GlossaryEntryData.serializer()), entries.map { it.toData() })

    fun import(text: String): List<GlossaryEntry> {
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(GlossaryEntryData.serializer(), element) }.getOrNull()?.toDomain()
        }
    }

    private fun GlossaryEntry.toData() = GlossaryEntryData(
        id = id,
        bookId = bookId,
        locatorJson = locator?.let { json.encodeToString(Locator.serializer(), it) },
        term = term,
        aliases = aliases,
        kind = kind.name,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun GlossaryEntryData.toDomain() = GlossaryEntry(
        id = id,
        bookId = bookId,
        locator = locatorJson?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<Locator>(it) }.getOrNull() },
        term = term,
        aliases = aliases.filter { it.isNotBlank() },
        kind = GlossaryKind.from(kind),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
