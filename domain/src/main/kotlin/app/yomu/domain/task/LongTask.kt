package app.yomu.domain.task

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Where a long operation is. Cancelling is a request; only the owner can confirm that the work stopped. */
enum class TaskState { PENDING, RUNNING, CANCELLING, CANCELLED, FAILED, DONE }

/** Progress of one task; [total] 0 means "unknown yet". */
data class TaskProgress(val done: Int = 0, val total: Int = 0, val message: String? = null) {
    val fraction: Float? get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)

    /** A result that has some errors is not a complete success; the caller shows both. */
    val partial: Boolean get() = done > 0 && total > 0 && done < total
}

/** One item that could not be processed; the task keeps going and reports them at the end. */
data class TaskError(val item: String, val message: String)

/** What a long operation reports: progress, cancellability, partial errors and where the output went. */
data class LongTask(
    val id: String,
    val title: String,
    val state: TaskState = TaskState.PENDING,
    val progress: TaskProgress = TaskProgress(),
    val errors: List<TaskError> = emptyList(),
    val output: File? = null,
    val error: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    /**
     * Point of no return: the detached write-back was started in an application-lifetime scope and cannot be
     * pulled back. The task stays [TaskState.RUNNING] until that write settles, [cancel] becomes a no-op, and
     * only the write's real outcome may make the task DONE or FAILED. This is a flag rather than a new
     * [TaskState] so the rest of the domain (index planning) keeps its exhaustive state mapping unchanged.
     */
    val publishing: Boolean = false,
) {
    val running: Boolean get() = state == TaskState.PENDING || state == TaskState.RUNNING
    val finished: Boolean get() = state == TaskState.DONE || state == TaskState.CANCELLED || state == TaskState.FAILED
}

/**
 * In-memory registry of long operations.
 *
 * The contract makes the dishonest states impossible: cancelling is a request the owner acknowledges after cleanup, a
 * cancelled task never becomes "done", and a task that produced some items and failed others stays `DONE` with
 * [LongTask.errors] rather than pretending everything succeeded. [beginPublish] marks the point of no return for an
 * operation whose output is written in a scope that outlives the screen: from then on the task stays RUNNING and a
 * late cancel request is ignored, so a still-writing task is never relabelled CANCELLED. The registry is pure and
 * testable; the editor, import and export are expected to use it as their owner.
 */
class TaskRegistry {
    private val _tasks = MutableStateFlow<Map<String, LongTask>>(emptyMap())
    val tasks: StateFlow<Map<String, LongTask>> = _tasks.asStateFlow()

    fun start(id: String, title: String, now: Long = System.currentTimeMillis()): LongTask {
        val task = LongTask(id = id, title = title, state = TaskState.RUNNING, startedAt = now)
        _tasks.update { it + (id to task) }
        return task
    }

    fun progress(id: String, done: Int, total: Int, message: String? = null) = update(id) {
        it.copy(progress = TaskProgress(done.coerceAtLeast(0), total.coerceAtLeast(0), message))
    }

    fun partialError(id: String, item: String, message: String) = update(id) {
        it.copy(errors = it.errors + TaskError(item, message))
    }

    /** A successful finish with possibly partial errors; after a cancel request it is still a cancellation. */
    fun finish(id: String, output: File? = null, now: Long = System.currentTimeMillis()) = update(id) {
        if (it.state == TaskState.CANCELLING || it.state == TaskState.CANCELLED) return@update it
        it.copy(
            state = TaskState.DONE,
            output = output,
            finishedAt = now,
            progress = it.progress.copy(done = it.progress.total.coerceAtLeast(it.progress.done)),
        )
    }

    fun fail(id: String, message: String, now: Long = System.currentTimeMillis()) = update(id) {
        if (it.state == TaskState.CANCELLING || it.state == TaskState.CANCELLED) return@update it
        it.copy(state = TaskState.FAILED, error = message, finishedAt = now)
    }

    /**
     * Claims the point of no return for a write-back that will outlive the caller. False means a cancel already
     * won and the caller must not write anything. True keeps the task RUNNING with [LongTask.publishing] set:
     * later [cancel] requests are ignored and only the write's real outcome can finish the task.
     */
    fun beginPublish(id: String): Boolean {
        var claimed = false
        _tasks.update { all ->
            val task = all[id] ?: return@update all
            if (!task.running || task.publishing) {
                all
            } else {
                claimed = true
                all + (id to task.copy(publishing = true))
            }
        }
        return claimed && _tasks.value[id]?.publishing == true
    }

    /** Asks the owner to stop; ignored once [beginPublish] claimed the write-back (a request is not a confirmation). */
    fun cancel(id: String) = update(id) {
        if (!it.running || it.publishing) it else it.copy(state = TaskState.CANCELLING)
    }

    /** The owner confirmed that nothing is left running; whatever was published is partial. */
    fun cancelled(id: String, now: Long = System.currentTimeMillis()) = update(id) {
        if (it.state == TaskState.DONE || it.state == TaskState.FAILED || it.publishing) it
        else it.copy(state = TaskState.CANCELLED, finishedAt = now)
    }

    fun remove(id: String) = _tasks.update { it - id }

    private fun update(id: String, transform: (LongTask) -> LongTask) = _tasks.update { all ->
        val task = all[id] ?: return@update all
        all + (id to transform(task))
    }
}

// ---------------------------------------------------------------- recovery journal (N05)

/** One durable event of a long operation, written around a step so a kill can be recovered. */
data class JournalEvent(
    val taskId: String,
    val step: String,
    val target: String? = null,
    /** Snapshot reference of the target before the step; its presence makes the step reversible. */
    val before: String? = null,
    val after: String? = null,
    val timestamp: Long,
)

enum class JournalOutcome { PENDING, RECOVERED, ABANDONED }

/** Serializable, pure snapshot of a journal (no file or coroutine concerns). */
data class RecoveryJournalSnapshot(
    val events: List<JournalEvent>,
    val outcomes: Map<String, JournalOutcome>,
)

/** A single deterministic action of a recovery plan. */
data class RecoveryAction(val target: String, val snapshot: String?, val restore: Boolean)

/**
 * What to do with a task that was killed mid-work. [done] is true only for a task that actually reached
 * [TaskState.DONE]; a cancelled or failed task is always rolled back and is never reported as done.
 */
data class RecoveryPlan(
    val taskId: String,
    val state: TaskState,
    val actions: List<RecoveryAction>,
    val done: Boolean,
) {
    val needsRollback: Boolean get() = actions.isNotEmpty()
}

/**
 * Pure recovery-plan builder. Every step that recorded a "before" snapshot is restored in reverse order.
 * Cancellation and failure are not completion — they always require rollback and are never `done`.
 */
fun buildRecoveryPlan(taskId: String, events: List<JournalEvent>, state: TaskState): RecoveryPlan {
    val done = state == TaskState.DONE
    val actions = if (done) {
        emptyList()
    } else {
        events.asReversed().filter { it.before != null }.map { RecoveryAction(it.target ?: it.step, it.before, true) }
    }
    return RecoveryPlan(taskId, state, actions, done)
}

/** In-memory, pure journal. The owner persists [snapshot] and rehydrates with [restore] across process death. */
class RecoveryJournal {
    private val events = LinkedHashMap<String, MutableList<JournalEvent>>()
    private val outcomes = LinkedHashMap<String, JournalOutcome>()

    fun append(event: JournalEvent): JournalEvent {
        events.getOrPut(event.taskId) { mutableListOf() } += event
        outcomes.putIfAbsent(event.taskId, JournalOutcome.PENDING)
        return event
    }

    fun append(taskId: String, step: String, target: String?, before: String?, after: String?, timestamp: Long): JournalEvent =
        append(JournalEvent(taskId, step, target, before, after, timestamp))

    fun events(taskId: String): List<JournalEvent> = events[taskId].orEmpty().toList()

    fun taskIds(): List<String> = events.keys.toList()

    fun outcome(taskId: String): JournalOutcome = outcomes[taskId] ?: JournalOutcome.PENDING

    /** Marks a plan as applied. A cancelled/failed task is recorded as abandoned, never recovered-as-done. */
    fun markRecovered(taskId: String, state: TaskState? = null) {
        outcomes[taskId] = if (state == TaskState.CANCELLED || state == TaskState.CANCELLING || state == TaskState.FAILED) {
            JournalOutcome.ABANDONED
        } else {
            JournalOutcome.RECOVERED
        }
    }

    fun markAbandoned(taskId: String) {
        outcomes[taskId] = JournalOutcome.ABANDONED
    }

    fun plan(taskId: String, state: TaskState): RecoveryPlan = buildRecoveryPlan(taskId, events(taskId), state)

    fun snapshot(): RecoveryJournalSnapshot = RecoveryJournalSnapshot(events.values.flatten().toList(), outcomes.toMap())

    fun restore(snapshot: RecoveryJournalSnapshot) {
        events.clear()
        outcomes.clear()
        snapshot.events.forEach { append(it) }
        snapshot.outcomes.forEach { (id, outcome) -> outcomes[id] = outcome }
    }

    fun clear(taskId: String) {
        events -= taskId
        outcomes -= taskId
    }
}

/** Pure guard used before a staged artifact is swapped over the original. */
data class PublishPlan(
    val staged: File,
    val target: File,
    val temp: File,
    val canPublish: Boolean,
    val reason: String? = null,
) {
    val blocked: Boolean get() = !canPublish
}

/** A cancelled/failed task or any validation error forbids publication; the original stays untouched. */
fun publishPlan(staged: File, target: File, temp: File, state: TaskState, validationError: String?): PublishPlan =
    PublishPlan(
        staged = staged,
        target = target,
        temp = temp,
        canPublish = state != TaskState.CANCELLING && state != TaskState.CANCELLED &&
            state != TaskState.FAILED && validationError == null,
        reason = when {
            state == TaskState.CANCELLED || state == TaskState.CANCELLING -> "Task was cancelled"
            state == TaskState.FAILED -> "Task failed"
            validationError != null -> validationError
            else -> null
        },
    )
