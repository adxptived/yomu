package app.yomu.domain.library

import kotlinx.serialization.Serializable

/**
 * Why one file of an import failed, in the terms the user can act on.
 *
 * A batch used to report only "2 failed"; the difference between "this format is not supported", "the archive is
 * corrupt" and "the folder permission was revoked" decides what the user does next.
 */
@Serializable
enum class ImportFailure {
    UNSUPPORTED,
    PASSWORD,
    CORRUPT,
    NO_PERMISSION,
    MISSING,
    NO_SPACE,
    TIMEOUT,
    NETWORK,
    UNKNOWN,
}

/** What happened to one file of a batch. */
@Serializable
enum class ImportEntryResult { ADDED, DUPLICATE, FAILED }

/** One file of an import, kept after the batch ends so the screen can show it again after a restart. */
@Serializable
data class ImportReportEntry(
    /** The document URI the file came from; the only way to retry exactly this file. */
    val uri: String,
    val displayName: String,
    val result: ImportEntryResult,
    val bookId: Long? = null,
    val failure: ImportFailure? = null,
    /** A failure the user can fix (permission, network, space, a missing card) is offered for a retry. */
    val retryable: Boolean = false,
)

/** One import batch: a stable id, when it ran, and what happened to every file. */
@Serializable
data class ImportReport(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    /** Copy (false) or link (true); the report says which promise the library made for these files. */
    val link: Boolean,
    val entries: List<ImportReportEntry>,
) {
    val added: Int get() = entries.count { it.result == ImportEntryResult.ADDED }
    val duplicates: Int get() = entries.count { it.result == ImportEntryResult.DUPLICATE }
    val failed: Int get() = entries.count { it.result == ImportEntryResult.FAILED }
    val retryable: List<ImportReportEntry> get() = entries.filter { it.result == ImportEntryResult.FAILED && it.retryable }
    val isPartial: Boolean get() = failed in 1 until entries.size

    companion object {
        fun of(id: String, startedAt: Long, finishedAt: Long, link: Boolean, entries: List<ImportReportEntry>) =
            ImportReport(id, startedAt, finishedAt, link, entries)
    }
}

/** Rules shared by the importer and the report UI. */
object ImportReportRules {
    /**
     * A file that failed because of the device (no permission, no card, no space, a network hiccup) can succeed when
     * the cause is fixed. A wrong format, a password or a corrupt archive cannot: retrying it would just fail again.
     */
    fun isRetryable(failure: ImportFailure): Boolean = when (failure) {
        ImportFailure.NO_PERMISSION, ImportFailure.MISSING, ImportFailure.NO_SPACE,
        ImportFailure.TIMEOUT, ImportFailure.NETWORK -> true
        ImportFailure.UNSUPPORTED, ImportFailure.PASSWORD, ImportFailure.CORRUPT, ImportFailure.UNKNOWN -> false
    }
}
