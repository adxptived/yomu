package app.yomu.data.index

import androidx.sqlite.db.SimpleSQLiteQuery
import app.yomu.core.database.IndexedTextEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.domain.index.IndexEstimate
import app.yomu.domain.index.IndexedHit
import app.yomu.domain.index.IndexPlan
import app.yomu.domain.index.IndexPlans
import app.yomu.domain.task.LongTask
import app.yomu.domain.task.TaskRegistry
import app.yomu.domain.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Opt-in index over text the app actually has: saved highlights, notes and word contexts (source TEXT), plus text a
 * reader attaches to a book — chapter text (source TEXT) or OCR (source OCR). It is deliberately not a claim about
 * unextracted book pages; a heading/title search stays a heading search until this index is built.
 *
 * The work runs through the shared [TaskRegistry], so a cancel request is only confirmed after the last batch, and a
 * cancelled build can never be reported as done. Rows are keyed per source and upserted only when their content
 * changed (incremental); Room's foreign key removes them with the book.
 */
class FulltextIndexRepository(
    private val db: YomuDatabase,
    private val registry: TaskRegistry = TaskRegistry(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _estimate = MutableStateFlow(IndexEstimate())
    val estimate: StateFlow<IndexEstimate> get() = _estimate

    val tasks: StateFlow<Map<String, LongTask>> get() = registry.tasks

    val plan: StateFlow<IndexPlan> = combine(registry.tasks, _estimate) { tasks, estimate ->
        val task = tasks[IndexPlans.TASK_ID] ?: tasks[IndexPlans.ESTIMATE_ID]
        if (task == null) IndexPlans.idle(estimate) else IndexPlans.from(task, estimate)
        // Eagerly, so plan.value is truthful for non-UI callers too; the repository is a singleton for the app scope.
    }.stateIn(scope, SharingStarted.Eagerly, IndexPlan())

    @Volatile
    private var cancelRequested = false

    val indexedCount: Flow<Int> get() = db.indexedText().observeCount()

    /** Counts what a rebuild would visit. Runs as its own short task so the UI can show an honest estimate phase. */
    suspend fun estimate(): IndexEstimate = withContext(Dispatchers.IO) {
        registry.remove(IndexPlans.TASK_ID)
        registry.start(IndexPlans.ESTIMATE_ID, "Estimating text index")
        try {
            val found = computeEstimate()
            _estimate.value = found
            registry.progress(IndexPlans.ESTIMATE_ID, found.sections, found.sections, "Estimated ${found.sections} pieces")
            registry.finish(IndexPlans.ESTIMATE_ID)
            found
        } catch (e: Throwable) {
            registry.fail(IndexPlans.ESTIMATE_ID, e.message ?: "Estimate failed")
            IndexEstimate()
        }
    }

    /** Asks the owner to stop; the rebuild confirms at a batch boundary. */
    fun cancel() {
        cancelRequested = true
        registry.cancel(IndexPlans.TASK_ID)
        registry.cancel(IndexPlans.ESTIMATE_ID)
    }

    suspend fun rebuild(): Boolean = withContext(Dispatchers.IO) {
        cancelRequested = false
        registry.remove(IndexPlans.ESTIMATE_ID)
        registry.start(IndexPlans.TASK_ID, "Indexing saved text")
        try {
            val rows = collectSources()
            // Rows attached by the reader are re-read from the table, never regenerated from annotations, so a
            // rebuild keeps them and their stamps (and only upserts what actually changed).
            val external = db.indexedText().all().filter { isExternalKey(it.key) }
            val ocr = external.filter { it.source == SOURCE_OCR }
            val all = rows + external
            val found = IndexEstimate(
                books = all.map { it.bookId }.toSet().size,
                sections = all.size,
                chars = all.sumOf { it.text.length.toLong() },
                ocrSections = ocr.size,
            )
            _estimate.value = found
            registry.progress(IndexPlans.TASK_ID, 0, found.sections, "Reading saved text")

            val existing = db.indexedText().stamps().associate { it.key to it.updatedAt }
            val freshKeys = all.map { it.key }.toSet()
            val stale = existing.keys - freshKeys
            if (stale.isNotEmpty()) db.indexedText().deleteKeys(stale)

            val changed = all.filter { existing[it.key] == null || (existing[it.key] ?: 0L) < it.updatedAt }
            val total = changed.size.coerceAtLeast(1)
            for ((i, batch) in changed.chunked(BATCH_SIZE).withIndex()) {
                if (cancelRequested || registry.tasks.value[IndexPlans.TASK_ID]?.state == TaskState.CANCELLING) {
                    registry.cancelled(IndexPlans.TASK_ID)
                    return@withContext false
                }
                db.indexedText().upsertAll(batch)
                registry.progress(IndexPlans.TASK_ID, ((i + 1) * BATCH_SIZE).coerceAtMost(changed.size), total, null)
            }
            if (cancelRequested) {
                registry.cancelled(IndexPlans.TASK_ID)
                false
            } else {
                registry.finish(IndexPlans.TASK_ID)
                true
            }
        } catch (e: Throwable) {
            if (cancelRequested || registry.tasks.value[IndexPlans.TASK_ID]?.state == TaskState.CANCELLING) {
                registry.cancelled(IndexPlans.TASK_ID)
            } else {
                registry.fail(IndexPlans.TASK_ID, e.message ?: "Indexing failed")
            }
            false
        }
    }

    /**
     * Indexes a piece of text the caller already has (a reader chapter, an OCR page, ...). The key is stable per
     * book/section/source, so re-indexing the same section upserts its row and refreshes the stamp instead of adding
     * a duplicate; [rebuild] keeps such rows because they are not derivable from saved annotations. Blank text is
     * ignored. Text is only indexable once attached to a book so Room's foreign key can delete it with the book.
     */
    suspend fun indexText(
        bookId: Long,
        sectionId: String,
        text: String,
        updatedAt: Long = System.currentTimeMillis(),
        source: String = SOURCE_TEXT,
    ) {
        require(source == SOURCE_TEXT || source == SOURCE_OCR) { "Unsupported index source: $source" }
        if (text.isBlank()) return
        val prefix = if (source == SOURCE_OCR) "O:" else "T:"
        withContext(Dispatchers.IO) {
            db.indexedText().upsertAll(
                listOf(IndexedTextEntity("$prefix$bookId:$sectionId", bookId, sectionId, text, source, updatedAt)),
            )
            _estimate.value = computeEstimate()
        }
    }

    /** OCR text is only indexed once it is attached to a book; without a book it cannot be deleted with one. */
    suspend fun addOcrText(bookId: Long, sectionId: String, text: String, updatedAt: Long = System.currentTimeMillis()) =
        indexText(bookId, sectionId, text, updatedAt, SOURCE_OCR)

    /** FTS expression: every word is a prefix, joined by AND. Quotes and operators from the user cannot get through. */
    suspend fun search(query: String, bookId: Long? = null): List<IndexedHit> {
        val terms = query.trim().split(Regex("\\s+"))
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }.take(8).map { "$it*" }
        if (terms.isEmpty()) return emptyList()
        val sql =
            "SELECT c.* FROM indexed_text c JOIN indexed_text_fts f ON f.docid = c.rowid " +
                "WHERE indexed_text_fts MATCH ? AND (? IS NULL OR c.bookId = ?) " +
                "ORDER BY c.updatedAt DESC LIMIT 100"
        val args = arrayOf<Any?>(terms.joinToString(" AND "), bookId, bookId)
        return db.indexedText().search(SimpleSQLiteQuery(sql, args)).map { row ->
            IndexedHit(row.key, row.bookId, row.sectionId, row.text, row.source, row.updatedAt)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.indexedText().clear()
        registry.remove(IndexPlans.TASK_ID)
        registry.remove(IndexPlans.ESTIMATE_ID)
        _estimate.value = IndexEstimate()
    }

    private suspend fun computeEstimate(): IndexEstimate {
        val external = db.indexedText().all().filter { isExternalKey(it.key) }
        val ocr = external.filter { it.source == SOURCE_OCR }
        val all = collectSources() + external
        return IndexEstimate(
            books = all.map { it.bookId }.toSet().size,
            sections = all.size,
            chars = all.sumOf { it.text.length.toLong() },
            ocrSections = ocr.size,
        )
    }

    private suspend fun collectSources(): List<IndexedTextEntity> = buildList {
        db.annotations().allHighlights().forEach { h ->
            if (h.text.isNotBlank()) add(IndexedTextEntity("H:${h.id}", h.bookId, "chapter:${h.chapter}", h.text, SOURCE_TEXT, h.updatedAt))
        }
        db.annotations().allNotes().forEach { n ->
            if (n.text.isNotBlank()) add(IndexedTextEntity("N:${n.id}", n.bookId, n.locatorJson ?: "note", n.text, SOURCE_TEXT, n.updatedAt))
        }
        db.words().all().forEach { w ->
            val bookId = w.bookId
            val context = w.context
            if (bookId != null && !context.isNullOrBlank()) {
                add(IndexedTextEntity("W:${w.id}", bookId, "word:${w.id}", context, SOURCE_TEXT, w.createdAt))
            }
        }
    }

    companion object {
        const val SOURCE_TEXT = "TEXT"
        const val SOURCE_OCR = "OCR"
        private const val BATCH_SIZE = 64

        /** Keys written by [indexText] (and the legacy [addOcrText]); all others are regenerated on rebuild. */
        private fun isExternalKey(key: String) = key.startsWith("O:") || key.startsWith("T:")
    }
}
