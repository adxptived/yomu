package app.yomu.feature.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Q01: the chapter window survives process death, and stale saved state never redirects the reader. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterWindowStateTest {

    private fun store(): SnapshotStore = object : SnapshotStore {
        private val values = HashMap<String, Any?>()
        override fun get(key: String): Any? = values[key]
        override fun set(key: String, value: Any?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test
    fun `a saved window round-trips with its current chapter and neighbours`() {
        val store = store()
        val snapshot = ChapterNavigator.Snapshot(currentId = 20L, entryId = 7L, ids = listOf(10L, 20L, 30L))

        ChapterWindowState.save(store, snapshot)

        assertEquals(snapshot, ChapterWindowState.restore(store))
    }

    @Test
    fun `a restored snapshot rebuilds the navigator window after recreation`() {
        val store = store()
        ChapterWindowState.save(store, ChapterNavigator.Snapshot(20L, 7L, listOf(10L, 20L, 30L)))
        val navigator = ChapterNavigator(StandardTestDispatcher())

        ChapterWindowState.restore(store)?.let(navigator::restore)

        assertEquals(listOf(10L, 20L, 30L), navigator.window?.ids)
        assertEquals(20L, navigator.window?.currentId)
        navigator.dispose()
    }

    @Test
    fun `an empty or partially written snapshot is ignored`() {
        val store = store()
        assertNull(ChapterWindowState.restore(store))

        store[ChapterWindowState.KEY_CURRENT] = 20L
        assertNull(ChapterWindowState.restore(store))
        store[ChapterWindowState.KEY_ENTRY] = 7L
        assertNull(ChapterWindowState.restore(store))
        store[ChapterWindowState.KEY_IDS] = ArrayList<Long>()
        assertNull(ChapterWindowState.restore(store))
    }

    @Test
    fun `malformed types never crash the restore`() {
        val store = store()
        store[ChapterWindowState.KEY_CURRENT] = 20 // Int instead of Long
        store[ChapterWindowState.KEY_ENTRY] = "7"
        store[ChapterWindowState.KEY_IDS] = arrayOf(10L, 20L) // array, not a List

        assertNull(ChapterWindowState.restore(store))

        store[ChapterWindowState.KEY_CURRENT] = 20L
        store[ChapterWindowState.KEY_ENTRY] = 7L
        store[ChapterWindowState.KEY_IDS] = listOf(10L, "twenty", null, 20L, 30L)
        assertEquals(
            ChapterNavigator.Snapshot(20L, 7L, listOf(10L, 20L, 30L)),
            ChapterWindowState.restore(store),
        )
    }

    @Test
    fun `a snapshot whose chapter left the window is stale`() {
        val store = store()
        ChapterWindowState.save(store, ChapterNavigator.Snapshot(99L, 7L, listOf(10L, 20L, 30L)))
        assertNull(ChapterWindowState.restore(store), "ReaderWindow.open would refuse a chapter outside its order")
    }

    @Test
    fun `clearing removes every trace of the window`() {
        val store = store()
        ChapterWindowState.save(store, ChapterNavigator.Snapshot(20L, 7L, listOf(10L, 20L, 30L)))

        ChapterWindowState.clear(store)

        assertNull(ChapterWindowState.restore(store))
    }

    @Test
    fun `only a reader without content resumes a saved window`() {
        assertTrue(ChapterWindowState.shouldRestore(ReaderArgs()))
        assertFalse(ChapterWindowState.shouldRestore(ReaderArgs(chapterId = 12L)))
        assertFalse(ChapterWindowState.shouldRestore(ReaderArgs(bookId = 3L)))
        assertFalse(ChapterWindowState.shouldRestore(ReaderArgs(bookId = 3L, locatorJson = "{}")))
        assertFalse(ChapterWindowState.shouldRestore(ReaderArgs(bookId = 3L, positionReference = "ref")))
    }
}
