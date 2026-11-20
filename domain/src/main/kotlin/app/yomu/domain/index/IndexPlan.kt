package app.yomu.domain.index

import app.yomu.domain.task.LongTask
import app.yomu.domain.task.TaskProgress
import app.yomu.domain.task.TaskState

/** Where an opt-in text-index operation is. [CANCELLED] and [DONE] are deliberately different outcomes. */
enum class IndexState { IDLE, ESTIMATING, RUNNING, CANCELLED, DONE, FAILED }

/** What the user is searching: a heading/title search is never advertised as full-text. */
enum class TextSearchKind { HEADING, FULLTEXT }

/** One result of a full-text search. [source] is TEXT or OCR; [sectionId] lets the caller open the passage. */
data class IndexedHit(
    val key: String,
    val bookId: Long,
    val sectionId: String,
    val text: String,
    val source: String,
    val updatedAt: Long,
)

/** What an estimate found before any work was done. Counts, not promises: a later rebuild can find less. */
data class IndexEstimate(
    val books: Int = 0,
    val sections: Int = 0,
    val chars: Long = 0,
    val ocrSections: Int = 0,
) {
    val empty: Boolean get() = sections == 0
}

/**
 * Honest view of the index, derived from a [LongTask] in the shared [app.yomu.domain.task.TaskRegistry]. The registry
 * owns the state machine; this is a read model plus pure transition helpers used by the UI and tests.
 */
data class IndexPlan(
    val state: IndexState = IndexState.IDLE,
    val estimate: IndexEstimate = IndexEstimate(),
    val progress: TaskProgress = TaskProgress(),
    val error: String? = null,
    val taskId: String? = null,
) {
    val running: Boolean get() = state == IndexState.ESTIMATING || state == IndexState.RUNNING
    val finished: Boolean get() = state == IndexState.DONE || state == IndexState.CANCELLED || state == IndexState.FAILED

    /** A cancellation is not a success. */
    val cancelled: Boolean get() = state == IndexState.CANCELLED
    val done: Boolean get() = state == IndexState.DONE

    val fraction: Float? get() = progress.fraction
    val partial: Boolean get() = progress.partial

    /** Only a completed build of a non-empty index may be called full-text search. */
    val hasFulltext: Boolean get() = done && !estimate.empty
}

object IndexPlans {
    const val TASK_ID = "fulltext-index"
    const val ESTIMATE_ID = "fulltext-index-estimate"

    /** Translates a registry task into a plan; an estimate task that finished returns to [IndexState.IDLE]. */
    fun from(task: LongTask, estimate: IndexEstimate = IndexEstimate()): IndexPlan = IndexPlan(
        state = when (task.state) {
            TaskState.PENDING, TaskState.RUNNING, TaskState.CANCELLING ->
                if (task.id == ESTIMATE_ID) IndexState.ESTIMATING else IndexState.RUNNING
            TaskState.CANCELLED -> IndexState.CANCELLED
            TaskState.FAILED -> IndexState.FAILED
            TaskState.DONE -> if (task.id == ESTIMATE_ID) IndexState.IDLE else IndexState.DONE
        },
        estimate = estimate,
        progress = task.progress,
        error = task.error,
        taskId = task.id,
    )

    fun idle(estimate: IndexEstimate = IndexEstimate()) = IndexPlan(state = IndexState.IDLE, estimate = estimate)

    fun beginEstimate(plan: IndexPlan, estimate: IndexEstimate) = plan.copy(state = IndexState.ESTIMATING, estimate = estimate)

    fun finishEstimate(plan: IndexPlan, estimate: IndexEstimate) = plan.copy(state = IndexState.IDLE, estimate = estimate)

    fun beginRebuild(plan: IndexPlan, estimate: IndexEstimate) = plan.copy(
        state = IndexState.RUNNING,
        estimate = estimate,
        progress = TaskProgress(),
        error = null,
    )

    fun progress(plan: IndexPlan, done: Int, total: Int, message: String? = null) = plan.copy(
        progress = TaskProgress(done.coerceAtLeast(0), total.coerceAtLeast(0), message),
    )

    /** A cancel request is only confirmed by the owner; like the registry, a later finish cannot turn it into DONE. */
    fun cancel(plan: IndexPlan) = if (plan.running) plan.copy(state = IndexState.CANCELLED) else plan

    fun finish(plan: IndexPlan) = if (plan.cancelled || plan.state == IndexState.FAILED) plan else plan.copy(
        state = IndexState.DONE,
        progress = plan.progress.copy(done = plan.progress.total.coerceAtLeast(plan.progress.done)),
    )

    fun fail(plan: IndexPlan, message: String) = if (plan.cancelled || plan.done) plan else plan.copy(
        state = IndexState.FAILED,
        error = message,
    )

    /** The only honest label for a search that has no completed index. */
    fun searchKind(indexEnabled: Boolean, plan: IndexPlan): TextSearchKind =
        if (indexEnabled && plan.hasFulltext) TextSearchKind.FULLTEXT else TextSearchKind.HEADING
}
