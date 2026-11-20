package app.yomu.data.glossary

import androidx.room.withTransaction
import app.yomu.core.database.GlossaryEntryEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.domain.glossary.GlossaryEntry
import app.yomu.domain.glossary.GlossaryIndex
import app.yomu.domain.glossary.GlossaryKind
import app.yomu.domain.model.Locator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Room-backed glossary notes. All identity and merge rules live in [GlossaryIndex]. */
class GlossaryRepository(private val db: YomuDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(bookId: Long? = null): Flow<List<GlossaryEntry>> =
        db.glossary().observe(bookId).map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<GlossaryEntry> = db.glossary().all().map { it.toDomain() }

    suspend fun get(id: Long): GlossaryEntry? = db.glossary().get(id)?.toDomain()

    /** Books that have at least one note, for the "filter by book" control. */
    suspend fun booksWithEntries(): List<Pair<Long, String>> {
        val titles = db.books().all().associate { it.id to it.title }
        return db.glossary().all().mapNotNull { row -> row.bookId?.let { id -> id to (titles[id] ?: "#$id") } }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    /** All books, for choosing which book a new note belongs to. */
    suspend fun books(): List<Pair<Long, String>> =
        db.books().all().map { it.id to it.title }.sortedBy { it.second.lowercase() }

    suspend fun save(entry: GlossaryEntry): Long {
        val entity = entry.toEntity()
        return if (entry.id == 0L) db.glossary().insert(entity) else {
            db.glossary().update(entity)
            entry.id
        }
    }

    suspend fun delete(id: Long) = db.glossary().delete(id)

    suspend fun exportJson(): String = GlossaryIndex.export(all())

    /**
     * Imports notes from an export string. Merge keeps local rows and their ids; replace clears the notes first.
     * Both paths deduplicate through [GlossaryIndex.merge].
     */
    suspend fun import(text: String, replace: Boolean = false): GlossaryIndex.MergeResult {
        val incoming = GlossaryIndex.import(text)
        val result = GlossaryIndex.merge(all(), incoming, replace)
        db.withTransaction {
            if (replace) db.glossary().clear()
            result.entries.forEach { entry ->
                // Match on identity, never on the incoming id: a foreign id may collide with a different local note.
                val existing = if (replace) null else db.glossary().all().firstOrNull { row ->
                    GlossaryIndex.sameIdentity(row.toDomain(), entry)
                }
                if (existing != null) db.glossary().update(entry.copy(id = existing.id).toEntity())
                else db.glossary().insert(entry.copy(id = 0).toEntity())
            }
        }
        return result
    }

    private fun GlossaryEntryEntity.toDomain() = GlossaryEntry(
        id = id,
        bookId = bookId,
        locator = locatorJson?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<Locator>(it) }.getOrNull() },
        term = term,
        aliases = runCatching { json.decodeFromJsonElement(ListSerializer(String.serializer()), json.parseToJsonElement(aliasesJson)) }
            .getOrDefault(emptyList()).filter { it.isNotBlank() },
        kind = GlossaryKind.from(kind),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun GlossaryEntry.toEntity() = GlossaryEntryEntity(
        id = id,
        bookId = bookId,
        locatorJson = locator?.let { json.encodeToString(Locator.serializer(), it) },
        term = term,
        normalized = GlossaryIndex.normalize(term),
        aliasesJson = json.encodeToString(ListSerializer(String.serializer()), aliases.filter { it.isNotBlank() }),
        kind = kind.name,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
