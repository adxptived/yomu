package app.yomu.domain.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** N05: a cancelled task is never done, and partial errors are not a silent success. */
class LongTaskTest {

    @Test
    fun `progress is a fraction only when the total is known`() {
        assertNull(TaskProgress(1, 0).fraction)
        assertEquals(0.5f, TaskProgress(5, 10).fraction)
        assertEquals(1f, TaskProgress(12, 10).fraction)
        assertTrue(TaskProgress(5, 10).partial)
        assertFalse(TaskProgress(10, 10).partial)
    }

    @Test
    fun `a cancelled task cannot become done`() {
        val registry = TaskRegistry()
        registry.start("export", "Export")
        registry.progress("export", 2, 10)
        registry.cancel("export")
        assertEquals(TaskState.CANCELLING, registry.tasks.value.getValue("export").state)
        registry.finish("export", File("out.cbz"))
        assertEquals(TaskState.CANCELLING, registry.tasks.value.getValue("export").state)
        registry.cancelled("export")
        val task = registry.tasks.value.getValue("export")
        assertEquals(TaskState.CANCELLED, task.state)
        assertNull(task.output)
        assertTrue(task.finished)
    }

    @Test
    fun `partial errors stay visible after a successful finish`() {
        val registry = TaskRegistry()
        registry.start("import", "Import")
        registry.progress("import", 1, 3)
        registry.partialError("import", "broken.epub", "Corrupted file")
        registry.finish("import", File("out.cbz"))
        val task = registry.tasks.value.getValue("import")
        assertEquals(TaskState.DONE, task.state)
        assertEquals(listOf(TaskError("broken.epub", "Corrupted file")), task.errors)
        assertEquals(File("out.cbz"), task.output)
    }

    @Test
    fun `a failure is final and an unknown task is ignored`() {
        val registry = TaskRegistry()
        registry.start("edit", "Edit")
        registry.fail("edit", "No space")
        assertEquals(TaskState.FAILED, registry.tasks.value.getValue("edit").state)
        registry.progress("missing", 1, 2)
        assertFalse("missing" in registry.tasks.value)
        registry.remove("edit")
        assertTrue(registry.tasks.value.isEmpty())
    }

    @Test
    fun `beginPublish claims the write so a late cancel cannot make it cancelled`() {
        val registry = TaskRegistry()
        registry.start("save-1", "Save")
        assertTrue(registry.beginPublish("save-1"))
        val claimed = registry.tasks.value.getValue("save-1")
        assertEquals(TaskState.RUNNING, claimed.state)
        assertTrue(claimed.publishing)

        // A cancel request during the detached write is ignored: the bytes are already committed.
        registry.cancel("save-1")
        assertEquals(TaskState.RUNNING, registry.tasks.value.getValue("save-1").state)
        assertFalse(registry.beginPublish("save-1")) // a second publish cannot be claimed
        registry.cancelled("save-1")
        assertEquals(TaskState.RUNNING, registry.tasks.value.getValue("save-1").state)

        // The real write outcome alone decides the terminal state.
        registry.finish("save-1", File("book.epub"))
        val done = registry.tasks.value.getValue("save-1")
        assertEquals(TaskState.DONE, done.state)
        assertEquals(File("book.epub"), done.output)
        assertTrue(done.finished)
    }

    @Test
    fun `a cancel before beginPublish aborts and a failure after publish stays failed`() {
        val registry = TaskRegistry()
        registry.start("save-2", "Save")
        registry.cancel("save-2")
        assertEquals(TaskState.CANCELLING, registry.tasks.value.getValue("save-2").state)
        assertFalse(registry.beginPublish("save-2"))
        registry.cancelled("save-2")
        assertEquals(TaskState.CANCELLED, registry.tasks.value.getValue("save-2").state)

        registry.start("save-3", "Save")
        assertTrue(registry.beginPublish("save-3"))
        registry.fail("save-3", "Disk full")
        assertEquals(TaskState.FAILED, registry.tasks.value.getValue("save-3").state)
        registry.cancel("save-3") // a request after the failure changes nothing
        assertEquals(TaskState.FAILED, registry.tasks.value.getValue("save-3").state)
    }
}
