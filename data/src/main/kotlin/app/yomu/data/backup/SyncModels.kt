package app.yomu.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

// The shared sync file (yomu-sync.json). Version 1 (1.x) had books, annotations and words; version 2 (2.0.0) adds the
// online library, tabs and deletions. Every field added in version 2 has a default, so 1.x reads the file (and simply
// ignores the rest) and 2.0 reads files written by 1.x.

@Serializable
data class SyncBook(
    val hash: String,
    val title: String,
    val locator: String? = null,
    val progress: Float,
    val updatedAt: Long,
    val status: String,
    /** Names of the tabs of the book; null = not known (written by 1.x). */
    val tabs: List<String>? = null,
    /** Last position inside each chapter of a text book, newest wins per chapter. */
    val chapterProgress: List<SyncChapterProgress> = emptyList(),
    /** The book's reader and audio settings, rating and favourite; credentials never live here. */
    val settingsJson: String? = null,
    /** When the fields above changed on the device that wrote them. */
    val metaUpdatedAt: Long = 0,
    val rating: Int = 0,
    val favorite: Boolean = false,
)

@Serializable
data class SyncChapterProgress(val chapter: Int, val locator: String, val updatedAt: Long)

@Serializable
data class SyncHighlight(val hash: String, val locator: String, val chapter: Int, val text: String, val color: String, val style: String, val isQuote: Boolean, val tags: String, val note: String?, val createdAt: Long, val updatedAt: Long)

@Serializable
data class SyncNote(val hash: String, val locator: String?, val text: String, val tags: String, val createdAt: Long, val updatedAt: Long)

@Serializable
data class SyncBookmark(val hash: String, val locator: String, val title: String, val progress: Float, val createdAt: Long)

@Serializable
data class SyncWord(
    val term: String, val normalized: String, val lang: String, val translation: String?, val definition: String?, val context: String?,
    val tags: String, val createdAt: Long, val level: Int, val ease: Double, val intervalDays: Int, val repetitions: Int, val dueAt: Long,
    val lapses: Int, val lastReviewedAt: Long?,
)

/**
 * A glossary note. Its book travels by file hash (not by the local id, which differs between devices); a null hash
 * describes a work as a whole. Identity is (book hash, normalised term, kind); the most recently updated copy wins.
 */
@Serializable
data class SyncGlossaryEntry(
    val hash: String? = null,
    val locator: String? = null,
    val term: String = "",
    val normalized: String = "",
    /** JSON array of display aliases, carried verbatim like [SyncBook.settingsJson]. */
    val aliasesJson: String = "[]",
    /** Raw kind name; an unknown future kind is preserved instead of being folded into TERM. */
    val kind: String = "TERM",
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** A chapter of an online title with a mark: read, bookmarked or opened. */
@Serializable
data class SyncChapter(
    val url: String,
    val name: String = "",
    val number: Float = -1f,
    val order: Int = 0,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val page: Int = 0,
    /** When the chapter was last opened (0 = never). */
    val at: Long = 0,
)

/** A title of the online library, matched by source and address. */
@Serializable
data class SyncTitle(
    val sourceId: Long,
    val url: String,
    val title: String,
    val type: String = "MANGA",
    val author: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val description: String? = null,
    val genres: String = "",
    val status: Int = 0,
    val addedAt: Long = 0,
    val tabs: List<String> = emptyList(),
    val chapters: List<SyncChapter> = emptyList(),
)

@Serializable
data class SyncTab(val name: String, val order: Int = 0)

/** A podcast/audiobook feed subscription; its book travels with the normal book sync by hash. */
@Serializable
data class SyncSubscription(
    val feedUrl: String,
    val title: String,
    val bookHash: String,
    val enabled: Boolean = true,
    val createdAt: Long = 0,
    val lastCheckedAt: Long = 0,
    val updatedAt: Long = 0,
    val etag: String? = null,
    val lastModified: String? = null,
)

@Serializable
data class SyncState(
    val version: Int = 1,
    val updatedAt: Long = 0,
    /** Device that wrote the file last (shown in the settings). */
    val device: String = "",
    val books: List<SyncBook> = emptyList(),
    val highlights: List<SyncHighlight> = emptyList(),
    val notes: List<SyncNote> = emptyList(),
    val bookmarks: List<SyncBookmark> = emptyList(),
    val words: List<SyncWord> = emptyList(),
    /** Glossary notes (N04); older clients ignore this category and [SyncCodec] carries it through a merge. */
    val glossary: List<SyncGlossaryEntry> = emptyList(),
    val titles: List<SyncTitle> = emptyList(),
    val tabs: List<SyncTab> = emptyList(),
    /** Podcast/audiobook feed subscriptions (B02), matched by feed address. */
    val subscriptions: List<SyncSubscription> = emptyList(),
    /** Deleted records: key (see [SyncKeys]) → time of the deletion. */
    val deleted: Map<String, Long> = emptyMap(),
    /** Expected shared annotation revision for a deletion. Older clients must not share this sync target. */
    val annotationDeletionProofs: Map<String, String> = emptyMap(),
    /**
     * Categories a newer client wrote that this build does not know. They are carried through a merge untouched, so a
     * newer device's data never looks like a deletion to an older one.
     */
    val extensions: Map<String, JsonElement> = emptyMap(),
)

/**
 * Reads and writes [SyncState] while keeping unknown top-level categories.
 *
 * Kotlinx decoding ignores unknown keys and encoding writes only the model, so without this step an older device would
 * drop a newer category the moment it merged. The encoder puts the preserved keys back at the top level; the decoder
 * captures anything this build did not produce.
 */
object SyncCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    /** Every field this build knows; anything else at the top level belongs to a newer client. */
    private val knownKeys: Set<String> = SyncState.serializer().descriptor.elementNames.toSet()

    fun decode(root: JsonObject): SyncState {
        val state = json.decodeFromJsonElement(SyncState.serializer(), root)
        return state.copy(extensions = root.filterKeys { it !in knownKeys && it != "extensions" })
    }

    fun encodeElement(state: SyncState): JsonObject {
        val base = json.encodeToJsonElement(SyncState.serializer(), state.copy(extensions = emptyMap())).jsonObject
        val merged = base.toMutableMap()
        state.extensions.forEach { (key, value) -> if (key !in base) merged[key] = value }
        return JsonObject(merged)
    }

    fun encode(state: SyncState): String = encodeElement(state).toString()
}

/**
 * What this device knew after its last sync with one sync target: it tells a record deleted here (in [keys], gone now)
 * from a record that is new on another device, and a chapter marked unread here from one marked read elsewhere.
 */
@Serializable
data class SyncBase(
    /** The sync target this base belongs to (a folder or a WebDAV address). */
    val syncId: String,
    /** Keys of all records that were on this device after the last sync. */
    val keys: Set<String> = emptySet(),
    /** Deletions known to this device (kept here too, so a file rewritten by 1.x does not bring deleted records back). */
    val tombstones: Map<String, Long> = emptyMap(),
    /** Title key → hashes of the read chapters' addresses. */
    val read: Map<String, List<Int>> = emptyMap(),
    /** Title key → hashes of the bookmarked chapters' addresses. */
    val marked: Map<String, List<Int>> = emptyMap(),
    /** Title key → names of its tabs. */
    val titleTabs: Map<String, List<String>> = emptyMap(),
    /** Book hash → names of its tabs. */
    val bookTabs: Map<String, List<String>> = emptyMap(),
    /** Book hash → status it had after the last sync; lets a status changed on one device win without timestamps. */
    val bookStatus: Map<String, String> = emptyMap(),
    /** Last acknowledged shared revisions of locally present annotations. */
    val annotationRevisions: Map<String, String> = emptyMap(),
    /** Local-only state such as favorites and linked private notes; never published. */
    val privateAnnotationRevisions: Map<String, String> = emptyMap(),
    val annotationDeletionProofs: Map<String, String> = emptyMap(),
)

/** Keys of the synced records; a deletion is stored under the key of the deleted record. */
object SyncKeys {
    fun highlight(hash: String, createdAt: Long, text: String) = "h|$hash|$createdAt|${Integer.toHexString(text.hashCode())}"
    fun note(hash: String, createdAt: Long) = "n|$hash|$createdAt"
    fun bookmark(hash: String, createdAt: Long) = "b|$hash|$createdAt"
    fun word(lang: String, normalized: String) = "w|$lang|$normalized"
    /**
     * Identity of a glossary note. The book hash is written first but parsed from the right in [bookOfGlossary],
     * so a folder identity containing '|' cannot be mistaken for another note's book.
     */
    fun glossary(hash: String?, normalized: String, kind: String) =
        "g|${hash.orEmpty()}|$kind|${Integer.toHexString(normalized.hashCode())}"

    /** The book hash of a glossary key, null for a note that belongs to no book. */
    fun bookOfGlossary(key: String): String? {
        if (key.length < 6 || key[0] != 'g' || key[1] != '|') return null
        val last = key.lastIndexOf('|')
        val kindSeparator = if (last > 1) key.lastIndexOf('|', last - 1) else -1
        if (kindSeparator < 2 || last <= kindSeparator + 1) return null
        if (key.substring(last + 1).any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return key.substring(2, kindSeparator).ifEmpty { null }
    }

    fun title(sourceId: Long, url: String) = "c|$sourceId|$url"
    fun subscription(feedUrl: String) = "s|${feedUrl.trim().lowercase()}"
    fun tab(name: String) = "t|${name.trim().lowercase()}"

    /** The book of an annotation key, null for other keys. */
    fun bookOf(key: String): String? {
        if (key.length < 4 || key[1] != '|' || key[0] !in "hnb") return null
        // Hashes for legacy image folders contain URIs, which can themselves contain '|'.
        // Parse the fixed timestamp (and highlight text hash) from the right instead.
        val last = key.lastIndexOf('|')
        val timeSeparator = if (key[0] == 'h') key.lastIndexOf('|', last - 1) else last
        if (timeSeparator <= 2 || last < timeSeparator || key.substring(timeSeparator + 1, if (key[0] == 'h') last else key.length).toLongOrNull() == null)
            return null
        if (key[0] == 'h' && last == key.lastIndex) return null
        return key.substring(2, timeSeparator)
    }
}

/** Three-way merge of one flag: the side that changed since [base] wins; without a base either side setting it wins. */
internal fun merge3(local: Boolean, remote: Boolean?, base: Boolean?): Boolean = when {
    remote == null || local == remote -> local
    base == null -> local || remote
    local != base -> local
    else -> remote
}

/** Three-way merge of a set (tabs of a title, for example), element by element. */
internal fun <T> merge3(local: Set<T>, remote: Set<T>?, base: Set<T>?): Set<T> {
    if (remote == null) return local
    return (local + remote).filterTo(LinkedHashSet()) { e -> merge3(e in local, e in remote, base?.let { e in it }) }
}
