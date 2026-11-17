package app.yomu.data.library

import android.content.Context
import app.yomu.core.common.AppError
import app.yomu.domain.library.ImportEntryResult
import app.yomu.domain.library.ImportFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** The batch report is written to a file so "what failed" survives the screen and the app restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportReportStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        File(context.filesDir, "import-reports").deleteRecursively()
    }

    private fun report(id: String = "one") = ImportReportStore.report(
        id = id,
        startedAt = 1,
        finishedAt = 2,
        link = false,
        uris = listOf("content://a", "content://b", "content://c"),
        outcomes = listOf(
            ImportOutcome.Added("A.epub", 10),
            ImportOutcome.Duplicate("B.epub", 11),
            ImportOutcome.Failed("C.cbr", AppError.CorruptedFile("bad zip")),
        ),
    )

    @Test
    fun `every file is named with its own result`() {
        val entries = report().entries
        assertEquals(ImportEntryResult.ADDED, entries[0].result)
        assertEquals(10L, entries[0].bookId)
        assertEquals(ImportEntryResult.DUPLICATE, entries[1].result)
        assertEquals(11L, entries[1].bookId)
        assertEquals(ImportEntryResult.FAILED, entries[2].result)
        assertEquals(ImportFailure.CORRUPT, entries[2].failure)
        assertFalse("a corrupt archive cannot be fixed by retrying", entries[2].retryable)
        assertEquals("content://c", entries[2].uri)
    }

    @Test
    fun `a device-side failure is retryable, a format is not`() {
        val value = ImportReportStore.report(
            id = "x", startedAt = 0, finishedAt = 0, link = false,
            uris = listOf("content://1", "content://2", "content://3", "content://4"),
            outcomes = listOf(
                ImportOutcome.Failed("one", AppError.NoPermission()),
                ImportOutcome.Failed("two", AppError.FileMissing("x")),
                ImportOutcome.Failed("three", AppError.UnsupportedFormat("exe")),
                ImportOutcome.Failed("four", AppError.NoSpace()),
            ),
        )
        assertEquals(listOf("one", "two", "four"), value.retryable.map { it.displayName })
        assertEquals(ImportFailure.NO_PERMISSION, value.entries[0].failure)
        assertEquals(ImportFailure.MISSING, value.entries[1].failure)
        assertEquals(ImportFailure.UNSUPPORTED, value.entries[2].failure)
        assertEquals(ImportFailure.NO_SPACE, value.entries[3].failure)
    }

    @Test
    fun `an untyped error is unknown, not guessed`() {
        assertEquals(ImportFailure.UNKNOWN, IllegalStateException("boom").toImportFailure())
    }

    @Test
    fun `the report survives a new store instance`() = runBlocking {
        val store = ImportReportStore(context)
        store.save(report())
        val again = ImportReportStore(context)
        val loaded = again.latest.first()
        assertEquals("one", loaded?.id)
        assertEquals(1, loaded?.added)
        assertEquals(1, loaded?.duplicates)
        assertEquals(1, loaded?.failed)
    }

    @Test
    fun `dismissing removes the report from the file too`() = runBlocking {
        val store = ImportReportStore(context)
        store.save(report())
        store.dismiss()
        assertNull(ImportReportStore(context).latest.first())
        assertFalse(File(context.filesDir, "import-reports/latest.json").exists())
    }

    @Test
    fun `a partial batch is partial`() {
        assertTrue(report().isPartial)
        val allFailed = ImportReportStore.report("y", 0, 0, false, listOf("u"), listOf(ImportOutcome.Failed("n", AppError.NoSpace())))
        assertFalse("a batch where everything failed is not partial", allFailed.isPartial)
    }
}
