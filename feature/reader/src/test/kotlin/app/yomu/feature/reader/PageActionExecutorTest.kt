package app.yomu.feature.reader

import app.yomu.domain.model.PageAction
import app.yomu.domain.reader.PageCapabilities
import app.yomu.domain.reader.PageContext
import app.yomu.domain.reader.PageEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Q01: page actions share one cancellation owner and one capability decision. */
@OptIn(ExperimentalCoroutinesApi::class)
class PageActionExecutorTest {

    private fun context(bookId: Long, ocr: Boolean) = PageContext(
        engine = PageEngine.PDF,
        bookId = bookId,
        entryId = 0,
        chapterId = 0,
        page = 0,
        pageCount = 1,
        capabilities = PageCapabilities(
            exportable = true,
            coverable = true,
            hasTextLayer = false,
            ocr = ocr,
            bookmarkable = true,
            notable = true,
            translatable = true,
        ),
    )

    @Test
    fun `capability resolver never offers an action the page cannot run`() {
        val executor = PageActionExecutor(UnconfinedTestDispatcher())
        val online = context(bookId = 0, ocr = false)

        assertTrue(executor.isSupported(PageAction.SHARE, online))
        assertTrue(executor.isSupported(PageAction.COPY_TEXT, context(bookId = 1, ocr = true)))
        assertFalse(executor.isSupported(PageAction.COPY_TEXT, online))
        assertFalse(executor.isSupported(PageAction.NOTE, online))
        assertEquals(PageAction.SHARE, executor.defaultAction(online, listOf(PageAction.NOTE, PageAction.SHARE)))
        assertEquals(PageAction.TRANSLATE, executor.defaultAction(online, listOf(PageAction.NOTE)))
        executor.dispose()
    }

    @Test
    fun `an action runs and completes while the owner is alive`() = runTest {
        val executor = PageActionExecutor(StandardTestDispatcher(testScheduler))
        var ran = false
        executor.execute { ran = true }
        advanceUntilIdle()

        assertTrue(ran)
        executor.dispose()
    }

    @Test
    fun `dispose cancels pending work and is idempotent`() = runTest {
        val executor = PageActionExecutor(StandardTestDispatcher(testScheduler))
        var ran = false
        executor.execute { delay(1_000); ran = true }

        executor.dispose()
        executor.dispose()
        advanceUntilIdle()

        assertFalse(ran, "a cancelled action must not finish")
        assertTrue(executor.isDisposed)
        executor.execute { ran = true }
        advanceUntilIdle()
        assertFalse(ran, "a disposed owner must not run new actions")
    }

    @Test
    fun `recreating the owner after rotation does not leak the old job`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = PageActionExecutor(dispatcher)
        var oldRan = false
        old.execute { delay(10_000); oldRan = true }
        old.dispose()

        val fresh = PageActionExecutor(dispatcher)
        var newRan = false
        fresh.execute { newRan = true }
        advanceUntilIdle()

        assertFalse(oldRan, "the disposed owner must not keep working after rotation")
        assertTrue(newRan)
        fresh.dispose()
    }
}
