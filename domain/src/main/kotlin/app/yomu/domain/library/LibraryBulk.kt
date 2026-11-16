package app.yomu.domain.library

import app.yomu.domain.model.ReadingStatus

/** A bulk command the user can run over a selection. */
enum class BulkAction {
    /** Add/remove the item from user shelves (categories). */
    SHELVES,

    /** The reader's own reading status. Never the publication status a source reports. */
    PERSONAL_STATUS,

    FAVORITE,
    RATING,
    TAGS,

    /** Queue the remaining chapters for download. */
    DOWNLOAD,

    /** Delete downloaded copies; the item and its progress stay. */
    DELETE_DOWNLOADS,

    /** Remove the item from the device library; the catalogue record is kept for online titles. */
    REMOVE_FROM_LIBRARY,

    /** Delete files Yomu itself created. Never a file the user supplied or linked. */
    DELETE_OWNED_FILES,
}

/**
 * Which commands make sense for which kind of item.
 *
 * A command that does not apply must not be offered: running "remove from library" on a catalogue entry, or deleting
 * files of anything but a local book, is how a mixed selection loses data. The matrix is data, not conditionals
 * scattered through the UI, so the availability shown before the action is the same one the executor enforces.
 */
object BulkCapabilities {
    fun supports(action: BulkAction, kind: LibraryItemKind): Boolean = when (kind) {
        LibraryItemKind.LOCAL_BOOK -> when (action) {
            BulkAction.SHELVES, BulkAction.PERSONAL_STATUS, BulkAction.FAVORITE, BulkAction.RATING, BulkAction.TAGS -> true
            BulkAction.DOWNLOAD -> true
            BulkAction.DELETE_DOWNLOADS -> true
            BulkAction.REMOVE_FROM_LIBRARY -> true
            BulkAction.DELETE_OWNED_FILES -> true
        }
        LibraryItemKind.ONLINE_TITLE -> when (action) {
            // The catalogue keeps its own personal status, rating and shelves for a followed title.
            BulkAction.SHELVES, BulkAction.PERSONAL_STATUS, BulkAction.FAVORITE, BulkAction.RATING, BulkAction.TAGS -> true
            BulkAction.DOWNLOAD -> true
            BulkAction.DELETE_DOWNLOADS -> true
            // Removing a followed title is its own command and does not touch local files.
            BulkAction.REMOVE_FROM_LIBRARY -> true
            BulkAction.DELETE_OWNED_FILES -> false
        }
        // A source result is not in the library yet: it can only be added.
        LibraryItemKind.SOURCE_RESULT -> false
    }

    /** Keys of [keys] the command would actually reach. The count is what the UI shows before running it. */
    fun applicable(action: BulkAction, keys: Set<LibraryItemKey>): Set<LibraryItemKey> = keys.filterTo(mutableSetOf()) { key ->
        supports(action, kindOf(key))
    }

    fun kindOf(key: LibraryItemKey): LibraryItemKind = when (key) {
        is LibraryItemKey.LocalBook -> LibraryItemKind.LOCAL_BOOK
        is LibraryItemKey.OnlineEntry -> LibraryItemKind.ONLINE_TITLE
        is LibraryItemKey.SourceResult -> LibraryItemKind.SOURCE_RESULT
    }
}

/** One result per key: nothing is skipped silently, so a partial failure is visible item by item. */
sealed interface BulkOutcome {
    data object Success : BulkOutcome

    /** The item is valid but the command does not apply to it. */
    data object Unsupported : BulkOutcome

    /** The command failed for this item; the rest of the batch still runs. */
    data class Failed(val reason: BulkFailure) : BulkOutcome
}

enum class BulkFailure {
    /** A network/source failure: retrying the same item may work. */
    SOURCE,

    /** The file is gone or the grant was revoked. */
    MISSING_FILE,

    /** A file Yomu does not own: it must not be deleted. */
    NOT_OWNED,

    STORAGE,
}

/**
 * The report of one bulk command. A command that reached 8 of 12 items reports both numbers rather than claiming
 * success, so the UI never says "done" for a partial result.
 */
data class BulkReport(
    val action: BulkAction,
    val outcomes: Map<LibraryItemKey, BulkOutcome>,
) {
    val succeeded: Int get() = outcomes.count { it.value is BulkOutcome.Success }
    val unsupported: Int get() = outcomes.count { it.value is BulkOutcome.Unsupported }
    val failed: Int get() = outcomes.count { it.value is BulkOutcome.Failed }

    val isPartial: Boolean get() = failed > 0 || unsupported > 0

    /** Keys whose failure may succeed on retry, so "retry failed" can name them. */
    val retryable: Set<LibraryItemKey> get() = outcomes.filterValues { it is BulkOutcome.Failed }.keys
}

/**
 * Runs a bulk command over a selection.
 *
 * The applicable set is computed first and unsupported keys are recorded as [BulkOutcome.Unsupported] instead of
 * being dropped; [run] receives only the keys the command can reach, one item at a time, so a failure of one item never
 * cancels the others. The executor performs no database transaction around the whole set: long network work must not
 * hold one.
 */
object BulkExecutor {
    /**
     * [perform] returns the outcome for one key. It is called for every applicable key and its failure is captured,
     * not rethrown.
     */
    suspend fun run(
        action: BulkAction,
        keys: Set<LibraryItemKey>,
        perform: suspend (LibraryItemKey) -> BulkOutcome,
    ): BulkReport {
        val outcomes = LinkedHashMap<LibraryItemKey, BulkOutcome>(keys.size)
        for (key in keys) {
            if (!BulkCapabilities.supports(action, BulkCapabilities.kindOf(key))) {
                outcomes[key] = BulkOutcome.Unsupported
                continue
            }
            outcomes[key] = runCatching { perform(key) }
                .getOrElse { BulkOutcome.Failed(BulkFailure.SOURCE) }
        }
        return BulkReport(action, outcomes)
    }

    /** "Apply to 8 of 12": the numbers the UI shows before a command runs. */
    fun preview(action: BulkAction, keys: Set<LibraryItemKey>): String {
        val applicable = BulkCapabilities.applicable(action, keys).size
        return "$applicable/${keys.size}"
    }
}
