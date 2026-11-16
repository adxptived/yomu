package app.yomu.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Above the sector cap the ring keeps the first actions plus "More"; nothing is dropped or shrunk. */
class WheelLayoutTest {
    @Test
    fun `a small wheel shows every action directly`() {
        assertEquals(listOf(0), WheelLayout.ringIndices(1))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), WheelLayout.ringIndices(7))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), WheelLayout.ringIndices(8))
        assertEquals(-1, WheelLayout.moreIndex(7))
        assertEquals(-1, WheelLayout.moreIndex(8))
        assertTrue(WheelLayout.overflowIndices(8).isEmpty())
    }

    @Test
    fun `nine actions keep seven and add More`() {
        val ring = WheelLayout.ringIndices(9)
        assertEquals(WheelLayout.MAX_WHEEL_SECTORS, ring.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 9), ring)
        assertEquals(9, WheelLayout.moreIndex(9))
        assertTrue(WheelLayout.isMore(9, ring.lastIndex))
        assertFalse(WheelLayout.isMore(9, 0))
    }

    @Test
    fun `twenty actions stay reachable behind More`() {
        val ring = WheelLayout.ringIndices(20)
        assertEquals(WheelLayout.MAX_WHEEL_SECTORS, ring.size)
        assertEquals(listOf(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19), WheelLayout.overflowIndices(20))
        // Every real action is on the ring or in the list exactly once.
        val all = ring.filter { it < 20 } + WheelLayout.overflowIndices(20)
        assertEquals((0 until 20).toList(), all.sorted())
    }

    @Test
    fun `an empty wheel has no sectors and no More`() {
        assertTrue(WheelLayout.ringIndices(0).isEmpty())
        assertEquals(-1, WheelLayout.moreIndex(0))
        assertTrue(WheelLayout.overflowIndices(0).isEmpty())
    }

    @Test
    fun `the ring radius only grows with the visible sectors`() {
        // The radius depends on the ring size, so a wheel with More keeps the size of an eight-sector wheel.
        assertEquals(radialRadiusDp(WheelLayout.ringIndices(20).size), radialRadiusDp(WheelLayout.MAX_WHEEL_SECTORS))
        assertEquals(radialRadiusDp(4), radialRadiusDp(WheelLayout.ringIndices(4).size))
    }
}
