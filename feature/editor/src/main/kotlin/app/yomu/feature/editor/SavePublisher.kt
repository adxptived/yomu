package app.yomu.feature.editor

import app.yomu.domain.task.RecoveryJournal
import app.yomu.domain.task.TaskRegistry
import app.yomu.domain.task.TaskState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

/** Terminal outcome of a save publish; emitted exactly once, after the write-back actually settles. */
enum class SaveOutcome { DONE, CANCELLED, FAILED }

/**
 * Owns the save's point of no return and the lifecycle of its detached write-back.
 *
 * The write runs in [appScope] so leaving the editor cannot cut it off halfway. The task stays RUNNING while it
 * is in flight and only the write's real outcome can make it DONE or FAILED. A cancel request that arrives before
 * [TaskRegistry.beginPublish] succeeds aborts before any write (the original stays untouched); one that arrives
 * afterwards is ignored — the bytes are already committed to the app scope and are allowed to finish and report
 * DONE. This is the deterministic cancel-during-publish rule; it is the opposite of "cancel == cancelled" because
 * a request that cannot stop committed work is not a confirmation that work stopped.
 *
 * All terminal work — task state, journal, temp cleanup and [onOutcome] — runs in one [NonCancellable] block, so
 * cancellation of the calling (screen) coroutine can never relabel a running write as cancelled or report success
 * before the bytes are on disk.
 */
class SavePublisher(
    private val appScope: CoroutineScope,
    private val tasks: TaskRegistry,
    private val journal: RecoveryJournal,
    private val writeBack: suspend (File) -> Unit,
    /** Persists one journal event; the editor's implementation writes the snapshot to disk every time. */
    private val record: (taskId: String, step: String, target: String?, before: String?, after: String?) -> Unit,
    /** The atomic swap file created for the target, removed alongside the staged file on both success and failure. */
    private val swap: () -> File? = { null },
    /** Injectable so tests run on a deterministic dispatcher instead of the real IO pool. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun publish(
        taskId: String,
        staged: File,
        output: File?,
        onOutcome: suspend (SaveOutcome, Throwable?) -> Unit,
    ): SaveOutcome {
        if (!tasks.beginPublish(taskId)) {
            return withContext(NonCancellable + io) {
                try {
                    tasks.cancelled(taskId)
                    journal.markAbandoned(taskId)
                    record(taskId, "cancelled", null, null, null)
                    onOutcome(SaveOutcome.CANCELLED, null)
                    SaveOutcome.CANCELLED
                } finally {
                    cleanup(staged)
                }
            }
        }

        record(taskId, "write", staged.path, null, output?.path)
        val write = appScope.async(io) { writeBack(staged) }
        return withContext(NonCancellable + io) {
            try {
                val failure = runCatching { write.await() }.exceptionOrNull()
                if (failure == null) {
                    tasks.progress(taskId, 3, 3, "Done")
                    tasks.finish(taskId, output)
                    journal.markRecovered(taskId, TaskState.DONE)
                    record(taskId, "done", output?.path ?: staged.path, null, null)
                    onOutcome(SaveOutcome.DONE, null)
                    SaveOutcome.DONE
                } else {
                    tasks.fail(taskId, failure.message ?: failure::class.java.simpleName)
                    journal.markAbandoned(taskId)
                    record(taskId, "failed", staged.path, null, failure.message)
                    onOutcome(SaveOutcome.FAILED, failure)
                    SaveOutcome.FAILED
                }
            } finally {
                cleanup(staged)
            }
        }
    }

    private fun cleanup(staged: File) {
        staged.delete()
        swap()?.delete()
    }
}
