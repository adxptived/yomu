package app.yomu.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderNavigatorTest {

    @Test
    fun `the thumb follows the reading progress when idle`() {
        assertEquals(0.4f, NavigatorSeek().thumb(0.4f), 0.0001f)
        assertEquals(1f, NavigatorSeek().thumb(1.4f), 0.0001f)
        assertEquals(0f, NavigatorSeek().thumb(-0.2f), 0.0001f)
    }

    @Test
    fun `an incoming old progress does not pull the dragged thumb back`() {
        val drag = NavigatorSeek().start(0.2f).drag(0.7f)
        assertEquals(0.7f, drag.thumb(0.1f), 0.0001f)
        assertEquals(0.7f, drag.thumb(0.9f), 0.0001f)
    }

    @Test
    fun `release commits the dragged value exactly once`() {
        val drag = NavigatorSeek().start(0.2f).drag(0.75f)
        val released = drag.release()
        assertEquals(0.75f, released.commit!!, 0.0001f)
        assertTrue(!released.state.dragging)
        assertNull(released.state.release().commit)
    }

    @Test
    fun `releasing without a drag commits nothing`() {
        assertNull(NavigatorSeek().release().commit)
    }

    @Test
    fun `a cancelled drag keeps the reading position`() {
        val cancelled = NavigatorSeek().start(0.2f).drag(0.9f).cancel()
        assertEquals(0.3f, cancelled.thumb(0.3f), 0.0001f)
        assertNull(cancelled.release().commit)
    }

    @Test
    fun `a move outside a drag is ignored`() {
        assertEquals(0.5f, NavigatorSeek().drag(0.9f).thumb(0.5f), 0.0001f)
    }

    @Test
    fun `fractions map to one-based pages`() {
        assertEquals(1, ReaderNavigator.pageAt(0f, 10))
        assertEquals(10, ReaderNavigator.pageAt(1f, 10))
        assertEquals(6, ReaderNavigator.pageAt(0.5f, 10))
        assertEquals(1, ReaderNavigator.pageAt(0.9f, 1))
        assertEquals(1, ReaderNavigator.pageAt(0.9f, 0))
    }

    @Test
    fun `pages map back to fractions`() {
        assertEquals(0f, ReaderNavigator.fractionOf(1, 10), 0.0001f)
        assertEquals(1f, ReaderNavigator.fractionOf(10, 10), 0.0001f)
        assertEquals(0f, ReaderNavigator.fractionOf(1, 1), 0.0001f)
    }

    @Test
    fun `an unknown fraction is not a navigation target`() {
        assertEquals(0f, ReaderNavigator.fractionOf(1, 10).also { assertTrue(it.isFinite()) }, 0.0001f)
        assertEquals(1, ReaderNavigator.pageAt(Float.NaN, 10))
    }
}
