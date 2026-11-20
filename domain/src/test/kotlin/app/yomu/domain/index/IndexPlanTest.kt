package app.yomu.domain.index

import app.yomu.domain.task.TaskRegistry
import app.yomu.domain.task.TaskState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndexPlanTest {
    @Test
    fun `task states map to the index states honestly`() {
        val estimate = IndexEstimate(books = 4, sections = 12)
        fun plan(state: TaskState, id: String = IndexPlans.TASK_ID) =
            IndexPlans.from(app.yomu.domain.task.LongTask(id = id, title = "index", state = state), estimate)

        assertEquals(IndexState.RUNNING, plan(TaskState.PENDING).state)
        assertEquals(IndexState.RUNNING, plan(TaskState.RUNNING).state)
        assertEquals(IndexState.RUNNING, plan(TaskState.CANCELLING).state) // a request is not a confirmation
        assertEquals(IndexState.ESTIMATING, plan(TaskState.RUNNING, IndexPlans.ESTIMATE_ID).state)
        assertEquals(IndexState.IDLE, plan(TaskState.DONE, IndexPlans.ESTIMATE_ID).state)
        assertEquals(IndexState.CANCELLED, plan(TaskState.CANCELLED).state)
        assertEquals(IndexState.FAILED, plan(TaskState.FAILED).state)
        assertEquals(estimate, plan(TaskState.DONE).estimate)
    }

    @Test
    fun `cancelled is not done and an empty index is not fulltext`() {
        val cancelled = IndexPlan(state = IndexState.CANCELLED, estimate = IndexEstimate(books = 1, sections = 3))
        assertTrue(cancelled.cancelled)
        assertTrue(cancelled.finished)
        assertFalse(cancelled.done)
        assertFalse(cancelled.hasFulltext)

        val done = IndexPlan(state = IndexState.DONE)
        assertTrue(done.done)
        assertFalse(done.hasFulltext) // nothing was indexed
        assertEquals(TextSearchKind.HEADING, IndexPlans.searchKind(true, done))
        assertEquals(
            TextSearchKind.FULLTEXT,
            IndexPlans.searchKind(true, IndexPlan(IndexState.DONE, IndexEstimate(sections = 2))),
        )
        assertEquals(
            TextSearchKind.HEADING,
            IndexPlans.searchKind(false, IndexPlan(IndexState.DONE, IndexEstimate(sections = 2))),
        )
    }

    @Test
    fun `progress is shown as a fraction and a partial result is not a success`() {
        val plan = IndexPlans.progress(IndexPlan(state = IndexState.RUNNING), 5, 10, "Reading saved text")
        assertEquals(0.5f, plan.fraction!!)
        assertTrue(plan.partial)
        assertNull(IndexPlans.progress(IndexPlan(), 3, 0).fraction) // unknown total: no invented percentage
    }

    @Test
    fun `pure transitions cannot turn a cancellation into a success`() {
        val running = IndexPlans.beginRebuild(IndexPlan(), IndexEstimate(sections = 4))
        val cancelled = IndexPlans.cancel(running)
        assertEquals(IndexState.CANCELLED, IndexPlans.finish(cancelled).state)
        assertEquals(IndexState.CANCELLED, IndexPlans.fail(cancelled, "late error").state)
        assertEquals(IndexState.DONE, IndexPlans.finish(running).state)
        assertEquals(IndexState.FAILED, IndexPlans.fail(running, "disk").state)
        assertEquals(IndexState.FAILED, IndexPlans.finish(IndexPlans.fail(running, "disk")).state)
        assertEquals(IndexState.CANCELLED, IndexPlans.cancel(cancelled).state) // confirm twice is a no-op
    }

    @Test
    fun `the shared registry keeps the plan cancelled after a late finish`() {
        val registry = TaskRegistry()
        registry.start(IndexPlans.TASK_ID, "Index")
        registry.progress(IndexPlans.TASK_ID, 2, 4)
        registry.cancel(IndexPlans.TASK_ID)
        assertEquals(IndexState.RUNNING, IndexPlans.from(registry.tasks.value.getValue(IndexPlans.TASK_ID)).state)
        registry.cancelled(IndexPlans.TASK_ID)
        registry.finish(IndexPlans.TASK_ID) // too late: the registry refuses it
        val plan = IndexPlans.from(registry.tasks.value.getValue(IndexPlans.TASK_ID))
        assertEquals(IndexState.CANCELLED, plan.state)
        assertFalse(plan.done)
    }
}
