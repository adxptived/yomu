package app.yomu.feature.reader

import app.yomu.domain.catalog.ChapterIntent
import app.yomu.domain.model.ChapterInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Q01: chapter navigation and the window are owned, cancellable and restorable after rotation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterNavigatorTest {

    private fun chapters() = listOf(
        ChapterInfo(id = 1, entryId = 1, url = "a", name = "A", number = 1f, scanlator = "g1", sourceOrder = 0, downloaded = true),
        ChapterInfo(id = 2, entryId = 1, url = "b", name = "B", number = 1f, scanlator = "g2", sourceOrder = 1),
        ChapterInfo(id = 3, entryId = 1, url = "c", name = "C", number = 2f, scanlator = "g1", sourceOrder = 2),
    )

    @Test
    fun `reading order and neighbour resolution own the skip policy`() {
        val nav = ChapterNavigator(StandardTestDispatcher())
        val list = chapters()

        assertEquals(listOf(1L, 3L), nav.readingOrder(list, 1L))
        assertEquals(3L, nav.resolve(list, 1L, ChapterIntent.NEXT)?.id)
        assertNull(nav.resolve(list, 3L, ChapterIntent.NEXT))

        nav.dispose()
    }

    @Test
    fun `the window holds only the current chapter and its neighbours`() {
        val nav = ChapterNavigator(StandardTestDispatcher())
        val window = nav.openWindow(1L, listOf(10L, 20L, 30L, 40L), 20L)

        assertEquals(listOf(10L, 20L, 30L), window!!.ids)
        assertEquals(listOf(10L, 20L, 30L), nav.window!!.ids)
        nav.dispose()
    }

    @Test
    fun `a newer navigation cancels the old one and dispose stops whatever is in flight`() = runTest {
        val nav = ChapterNavigator(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<String>()

        nav.navigate { delayAndAdd(calls, "old") }
        nav.navigate { calls += "new" }
        advanceUntilIdle()
        assertEquals(listOf("new"), calls, "the older navigation must not finish")

        var afterDispose = false
        nav.navigate { kotlinx.coroutines.delay(10_000); afterDispose = true }
        nav.dispose()
        nav.dispose()
        advanceUntilIdle()
        assertFalse(afterDispose, "dispose must cancel in-flight navigation")
    }

    @Test
    fun `recreating the owner after rotation keeps the window and does not leak the old job`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = ChapterNavigator(dispatcher)
        old.openWindow(1L, listOf(10L, 20L, 30L), 20L)
        var oldJobRan = false
        old.navigate { kotlinx.coroutines.delay(10_000); oldJobRan = true }

        val snapshot = old.snapshot()!!
        old.dispose()

        val restored = ChapterNavigator(dispatcher)
        restored.restore(snapshot)
        var newRan = false
        restored.navigate { newRan = true }
        advanceUntilIdle()

        assertFalse(oldJobRan, "the disposed owner must not keep working after rotation")
        assertTrue(newRan)
        assertEquals(listOf(10L, 20L, 30L), restored.window!!.ids)
        assertEquals(20L, restored.window!!.currentId)
        restored.dispose()
    }

    private suspend fun delayAndAdd(calls: MutableList<String>, value: String) {
        kotlinx.coroutines.delay(1_000)
        calls += value
    }
}
