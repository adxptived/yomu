package app.yomu.data.library

import android.content.Context
import app.yomu.core.common.AppError
import app.yomu.domain.library.ImportEntryResult
import app.yomu.domain.library.ImportFailure
import app.yomu.domain.library.ImportReport
import app.yomu.domain.library.ImportReportEntry
import app.yomu.domain.library.ImportReportRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * The last import batch, kept in a file so the report survives the screen going away and the app restarting.
 *
 * A batch is the unit the user acted on: "these twelve files" is what the report describes, and a retry imports only
 * the entries that failed for a reason that can change.
 */
class ImportReportStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir: File get() = File(context.filesDir, "import-reports").also { it.mkdirs() }
    private val file: File get() = File(dir, "latest.json")

    private val _latest = MutableStateFlow(load())
    val latest: StateFlow<ImportReport?> = _latest.asStateFlow()

    fun save(report: ImportReport) {
        _latest.value = report
        runCatching {
            val tmp = File(dir, "latest.json.tmp")
            tmp.writeText(json.encodeToString(ImportReport.serializer(), report))
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { Timber.w(it, "Cannot save the import report") }
    }

    fun dismiss() {
        _latest.value = null
        file.delete()
    }

    private fun load(): ImportReport? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString(ImportReport.serializer(), file.readText())
    }.onFailure { Timber.w(it, "Cannot read the import report") }.getOrNull()

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        /** The report of one finished batch; URIs and outcomes are in the same order. */
        fun report(id: String, startedAt: Long, finishedAt: Long, link: Boolean, uris: List<String>, outcomes: List<ImportOutcome>): ImportReport {
            val entries = outcomes.mapIndexed { i, outcome ->
                val uri = uris.getOrNull(i).orEmpty()
                when (outcome) {
                    is ImportOutcome.Added -> ImportReportEntry(uri, outcome.name, ImportEntryResult.ADDED, outcome.bookId)
                    is ImportOutcome.Duplicate -> ImportReportEntry(uri, outcome.name, ImportEntryResult.DUPLICATE, outcome.bookId)
                    is ImportOutcome.Failed -> {
                        val failure = outcome.error.toImportFailure()
                        ImportReportEntry(
                            uri = uri,
                            displayName = outcome.name,
                            result = ImportEntryResult.FAILED,
                            failure = failure,
                            retryable = ImportReportRules.isRetryable(failure),
                        )
                    }
                }
            }
            return ImportReport.of(id, startedAt, finishedAt, link, entries)
        }
    }
}

/**
 * The cause of a failed file in the report's terms. A typed [AppError] is used when the importer produced one;
 * anything else stays [ImportFailure.UNKNOWN] rather than pretending to know.
 */
fun Throwable.toImportFailure(): ImportFailure = when (val error = AppError.from(this)) {
    is AppError.UnsupportedFormat -> ImportFailure.UNSUPPORTED
    is AppError.NoPermission -> ImportFailure.NO_PERMISSION
    is AppError.FileMissing -> ImportFailure.MISSING
    is AppError.NoSpace -> ImportFailure.NO_SPACE
    is AppError.Timeout -> ImportFailure.TIMEOUT
    is AppError.NoNetwork -> ImportFailure.NETWORK
    is AppError.CorruptedFile, is AppError.BadData, is AppError.Incompatible -> ImportFailure.CORRUPT
    else -> ImportFailure.UNKNOWN
}
