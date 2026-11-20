package app.yomu.domain.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** N05: durable task events and a recovery plan that never reports a cancelled task as done. */
class RecoveryJournalTest {

    private fun event(seq: Int, taskId: String = "save-1") = JournalEvent(
        taskId = taskId,
        step = "step-$seq",
        target = "text/ch$seq.xhtml",
        before = "before/$seq",
        after = "after/$seq",
        timestamp = seq.toLong(),
    )

    @Test
    fun `events are appended and queried per task`() {
        val journal = RecoveryJournal()
        journal.append(event(1))
        journal.append(event(2))
        journal.append(event(1, taskId = "other"))

        assertEquals(2, journal.events("save-1").size)
        assertEquals(listOf("other"), journal.taskIds().filter { it == "other" })
        assertEquals(1, journal.events("other").size)
        assertTrue(journal.events("missing").isEmpty())
        assertEquals(JournalOutcome.PENDING, journal.outcome("save-1"))
        assertEquals(JournalOutcome.PENDING, journal.outcome("missing"))
    }

    @Test
    fun `recovery plan restores reversible steps in reverse order`() {
        val journal = RecoveryJournal()
        journal.append(event(1))
        journal.append(event(2))
        journal.append(JournalEvent("save-1", "no-snapshot", "notes.txt", null, "after", 3))

        val plan = journal.plan("save-1", TaskState.RUNNING)
        assertFalse(plan.done)
        assertTrue(plan.needsRollback)
        assertEquals(2, plan.actions.size)
        assertEquals("text/ch2.xhtml", plan.actions[0].target)
        assertEquals("before/2", plan.actions[0].snapshot)
        assertTrue(plan.actions[0].restore)
        assertEquals("text/ch1.xhtml", plan.actions[1].target)
    }

    @Test
    fun `a cancelled or failed task is never reported done`() {
        val journal = RecoveryJournal()
        journal.append(event(1))

        val cancelled = journal.plan("save-1", TaskState.CANCELLED)
        assertFalse(cancelled.done)
        assertTrue(cancelled.needsRollback)

        val cancelling = journal.plan("save-1", TaskState.CANCELLING)
        assertFalse(cancelling.done)

        val failed = journal.plan("save-1", TaskState.FAILED)
        assertFalse(failed.done)
        assertTrue(failed.needsRollback)
    }

    @Test
    fun `a done task has no rollback actions`() {
        val journal = RecoveryJournal()
        journal.append(event(1))
        val plan = buildRecoveryPlan("save-1", journal.events("save-1"), TaskState.DONE)
        assertTrue(plan.done)
        assertFalse(plan.needsRollback)
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `markRecovered on a cancelled task records abandoned`() {
        val journal = RecoveryJournal()
        journal.append(event(1))
        journal.markRecovered("save-1", TaskState.DONE)
        assertEquals(JournalOutcome.RECOVERED, journal.outcome("save-1"))

        journal.markRecovered("save-1", TaskState.CANCELLED)
        assertEquals(JournalOutcome.ABANDONED, journal.outcome("save-1"))

        journal.markRecovered("save-1", TaskState.FAILED)
        assertEquals(JournalOutcome.ABANDONED, journal.outcome("save-1"))

        journal.append(event(1, taskId = "x"))
        journal.markAbandoned("x")
        assertEquals(JournalOutcome.ABANDONED, journal.outcome("x"))
    }

    @Test
    fun `snapshot round trip preserves events and outcomes`() {
        val journal = RecoveryJournal()
        journal.append(event(1))
        journal.append(event(2))
        journal.markAbandoned("save-1")
        val snapshot = journal.snapshot()

        val restored = RecoveryJournal()
        restored.restore(snapshot)
        assertEquals(2, restored.events("save-1").size)
        assertEquals(JournalOutcome.ABANDONED, restored.outcome("save-1"))
        assertEquals(snapshot.events, restored.snapshot().events)

        restored.clear("save-1")
        assertTrue(restored.events("save-1").isEmpty())
        assertEquals(JournalOutcome.PENDING, restored.outcome("save-1"))
    }

    @Test
    fun `publish plan blocks cancelled failed and invalid output`() {
        val staged = File("staged.epub")
        val target = File("book.epub")
        val temp = File("book.epub.new")

        assertTrue(publishPlan(staged, target, temp, TaskState.RUNNING, null).canPublish)
        assertFalse(publishPlan(staged, target, temp, TaskState.CANCELLING, null).canPublish)
        assertFalse(publishPlan(staged, target, temp, TaskState.CANCELLED, null).canPublish)
        assertFalse(publishPlan(staged, target, temp, TaskState.FAILED, null).canPublish)
        assertFalse(publishPlan(staged, target, temp, TaskState.RUNNING, "bad markup").canPublish)

        val blocked = publishPlan(staged, target, temp, TaskState.CANCELLED, "bad markup")
        assertTrue(blocked.blocked)
        assertEquals("Task was cancelled", blocked.reason)
        assertNull(publishPlan(staged, target, temp, TaskState.DONE, null).reason)
    }
}
