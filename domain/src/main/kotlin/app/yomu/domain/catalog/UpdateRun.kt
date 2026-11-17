package app.yomu.domain.catalog

import kotlinx.serialization.Serializable

/** One title's line in a finished update run. */
@Serializable
data class UpdateRunTitle(
    val title: String,
    val newChapters: Int = 0,
    val failed: Boolean = false,
)

/**
 * The persisted outcome of one update check (U01).
 *
 * The worker kept this only in memory and a notification, so after a restart "how many titles were checked, what
 * failed" was gone. This is the durable summary the check-plan screen and the settings show. It is a report, not a
 * policy: the next check is still decided by [UpdatePolicy] from the per-title timestamps.
 */
@Serializable
data class UpdateRunReport(
    val startedAt: Long,
    val finishedAt: Long = 0,
    /** Titles actually asked. */
    val checked: Int = 0,
    /** Titles that were not due (interval, exclusions, restrictions, a missing source). */
    val skipped: Int = 0,
    val newChapters: Int = 0,
    val failed: Int = 0,
    /** Chapters queued by the auto-download rule, when it is on. */
    val queuedDownloads: Int = 0,
    val titles: List<UpdateRunTitle> = emptyList(),
) {
    val durationMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0)
    val isPartial: Boolean get() = failed > 0
    val hasAnything: Boolean get() = checked > 0 || skipped > 0 || failed > 0 || newChapters > 0
}
