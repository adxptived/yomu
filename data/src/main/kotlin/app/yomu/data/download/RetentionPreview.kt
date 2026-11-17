package app.yomu.data.download

import app.yomu.domain.download.RetentionKeep
import app.yomu.domain.download.RetentionPlan

/** One chapter the retention preview would delete, named so the confirmation is not a list of numbers. */
data class RetentionPreviewItem(
    val chapterId: Long,
    val title: String,
    val chapter: String,
    val bytes: Long,
)

/**
 * What a retention run would do right now: the chapters to remove, their names and bytes, and why the rest is kept.
 * Nothing is deleted until [DownloadRepository.applyRetention] runs, and that re-checks every protection.
 */
data class RetentionPreview(
    val plan: RetentionPlan,
    val delete: List<RetentionPreviewItem>,
    val keptReasons: Map<RetentionKeep, Int>,
) {
    val isEmpty: Boolean get() = plan.delete.isEmpty()
}
