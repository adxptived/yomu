package app.yomu.feature.editor

import app.yomu.domain.task.JournalOutcome
import app.yomu.domain.task.RecoveryJournal
import app.yomu.domain.task.TaskRegistry
import app.yomu.domain.task.TaskState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The save publish lifecycle: the task must stay honest about a write that outlives the screen coroutine.
 * No sleeps — the in-flight write is parked on a [CompletableDeferred] the test releases explicitly.
 */
class SavePublisherTest {
    @TempDir
    lateinit var dir: File

    private fun fixture(): Triple<File, File, File> {
        val original = File(dir, "book.epub").apply { writeText("original") }
        val staged = File(dir, "staged.epub").apply { writeText("staged") }
        val swap = File(dir, "book.epub.new")
        return Triple(original, staged, swap)
    }

    private fun publisher(
        appScope: CoroutineScope,
        tasks: TaskRegistry,
        journal: RecoveryJournal,
        writeBack: suspend (File) -> Unit,
        swap: File? = null,
        events: MutableList<String> = mutableListOf(),
    ) = SavePublisher(
        // A failing write must not cancel the test scope that owns the caller; the real AppScope is supervising too.
        appScope = CoroutineScope(appScope.coroutineContext + SupervisorJob()),
        tasks = tasks,
        journal = journal,
        writeBack = writeBack,
        record = { id, step, target, before, after ->
            journal.append(id, step, target, before, after, 0L)
            events += step
        },
        swap = { swap },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `cancel before the write starts leaves the original intact`() = runTest {
        val (original, staged, swap) = fixture()
        swap.writeText("junk") // a leftover swap from an earlier attempt must also be cleaned
        val tasks = TaskRegistry()
        val journal = RecoveryJournal()
        tasks.start("save-1", "Save")
        tasks.cancel("save-1")
        var writes = 0
        val publisher = publisher(this, tasks, journal, writeBack = { writes++ }, swap = swap)
        val outcomes = mutableListOf<SaveOutcome>()

        val result = publisher.publish("save-1", staged, original) { outcome, _ -> outcomes += outcome }

        assertEquals(SaveOutcome.CANCELLED, result)
        assertEquals(0, writes)
        assertEquals("original", original.readText())
        assertEquals(TaskState.CANCELLED, tasks.tasks.value.getValue("save-1").state)
        assertFalse(staged.exists())
        assertFalse(swap.exists())
        assertEquals(JournalOutcome.ABANDONED, journal.outcome("save-1"))
        assertTrue(journal.events("save-1").any { it.step == "cancelled" })
        assertEquals(listOf(SaveOutcome.CANCELLED), outcomes)
    }

    @Test
    fun `success publishes the staged bytes and cleans the temp files`() = runTest {
        val (original, staged, swap) = fixture()
        val tasks = TaskRegistry()
        val journal = RecoveryJournal()
        tasks.start("save-1", "Save")
        val events = mutableListOf<String>()
        val publisher = publisher(
            this, tasks, journal,
            writeBack = { src ->
                src.copyTo(swap, overwrite = true)
                swap.copyTo(original, overwrite = true)
            },
            swap = swap, events = events,
        )
        val outcomes = mutableListOf<SaveOutcome>()

        val result = publisher.publish("save-1", staged, original) { outcome, _ -> outcomes += outcome }

        assertEquals(SaveOutcome.DONE, result)
        assertEquals("staged", original.readText())
        assertEquals(TaskState.DONE, tasks.tasks.value.getValue("save-1").state)
        assertEquals(original, tasks.tasks.value.getValue("save-1").output)
        assertFalse(staged.exists())
        assertFalse(swap.exists())
        assertEquals(JournalOutcome.RECOVERED, journal.outcome("save-1"))
        assertTrue(events.containsAll(listOf("write", "done")))
        assertEquals(listOf(SaveOutcome.DONE), outcomes)
    }

    @Test
    fun `a failed write leaves the original intact and cleans the temp files`() = runTest {
        val (original, staged, swap) = fixture()
        val tasks = TaskRegistry()
        val journal = RecoveryJournal()
        tasks.start("save-1", "Save")
        val boom = java.io.IOException("Disk full")
        val publisher = publisher(this, tasks, journal, writeBack = { throw boom }, swap = swap)
        val errors = mutableListOf<Throwable?>()
        val outcomes = mutableListOf<SaveOutcome>()

        val result = publisher.publish("save-1", staged, original) { outcome, error ->
            outcomes += outcome
            errors += error
        }

        assertEquals(SaveOutcome.FAILED, result)
        assertEquals("original", original.readText())
        assertEquals(TaskState.FAILED, tasks.tasks.value.getValue("save-1").state)
        assertEquals("Disk full", tasks.tasks.value.getValue("save-1").error)
        assertFalse(staged.exists())
        assertFalse(swap.exists())
        assertEquals(JournalOutcome.ABANDONED, journal.outcome("save-1"))
        assertTrue(journal.events("save-1").any { it.step == "failed" })
        // await() rethrows the failure through the dispatcher; assert the delivered error, not its identity.
        assertEquals(1, errors.size)
        assertEquals("Disk full", errors.single()?.message)
        assertEquals(listOf(SaveOutcome.FAILED), outcomes)
    }

    @Test
    fun `cancel during the detached write reports DONE and never a false cancellation`() = runTest {
        val (original, staged, _) = fixture()
        val tasks = TaskRegistry()
        val journal = RecoveryJournal()
        tasks.start("save-1", "Save")
        val events = mutableListOf<String>()
        val started = Channel<Unit>(1)
        val release = CompletableDeferred<Unit>()
        var published = false
        val appScope: CoroutineScope = this
        val publisher = SavePublisher(
            appScope = appScope,
            tasks = tasks,
            journal = journal,
            writeBack = { src ->
                started.send(Unit)
                release.await()
                src.copyTo(original, overwrite = true)
                published = true
            },
            record = { id, step, target, before, after ->
                journal.append(id, step, target, before, after, 0L)
                events += step
            },
            io = Dispatchers.Unconfined,
        )
        val outcomes = Channel<SaveOutcome>(1)

        // The caller stands in for the ViewModel scope: it is launched separately so it can be cancelled.
        val caller = launch {
            publisher.publish("save-1", staged, original) { outcome, _ -> outcomes.send(outcome) }
        }

        started.receive() // write-back has entered the app scope and is parked before touching the original
        assertTrue(tasks.tasks.value.getValue("save-1").publishing)
        assertEquals(TaskState.RUNNING, tasks.tasks.value.getValue("save-1").state)
        assertEquals("original", original.readText())

        // A cancel request after the point of no return is ignored; so is cancelling the screen coroutine.
        tasks.cancel("save-1")
        assertEquals(TaskState.RUNNING, tasks.tasks.value.getValue("save-1").state)
        caller.cancel()
        assertTrue(caller.isCancelled)
        assertEquals("original", original.readText())

        release.complete(Unit) // let the already-committed write finish

        assertEquals(SaveOutcome.DONE, outcomes.receive())
        assertEquals(TaskState.DONE, tasks.tasks.value.getValue("save-1").state)
        assertTrue(published)
        assertEquals("staged", original.readText())
        assertFalse(staged.exists())
        assertFalse(events.contains("cancelled"))
        assertFalse(journal.events("save-1").any { it.step == "cancelled" })
        assertEquals(JournalOutcome.RECOVERED, journal.outcome("save-1"))
    }
}
