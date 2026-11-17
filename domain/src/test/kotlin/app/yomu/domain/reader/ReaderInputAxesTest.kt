package app.yomu.domain.reader

import app.yomu.domain.model.TapZoneLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderInputAxesTest {
    private fun at(x: Float, y: Float = 0.5f) = ReaderPoint(x, y)

    @Test
    fun `inverting x mirrors a left-right layout`() {
        val left = at(0.1f)
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(TapZoneLayout.LEFT_RIGHT, false, false, left, 0.25f))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.LEFT_RIGHT, invertX = true, invertY = false, rtl = false, point = left, sideFraction = 0.25f))
    }

    @Test
    fun `inverting y moves a top zone to the bottom`() {
        val top = at(0.5f, 0.1f)
        // KINDLE: a top strip opens the menu; mirrored it is neither the top nor the left third.
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.KINDLE, invertX = false, invertY = false, rtl = false, point = top))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.KINDLE, invertX = false, invertY = true, rtl = false, point = top))
    }

    @Test
    fun `both axes mirror corners without inventing a second geometry`() {
        val corner = at(0.1f, 0.1f)
        // L_SHAPE: top-left is PREV; mirrored on both axes it is bottom-right, which is NEXT.
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(TapZoneLayout.L_SHAPE, invertX = false, invertY = false, rtl = false, point = corner))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.L_SHAPE, invertX = true, invertY = true, rtl = false, point = corner))
    }

    @Test
    fun `right-to-left still flips after mirroring`() {
        val left = at(0.1f)
        // Mirrored to the right zone (NEXT), then RTL turns it back to PREV.
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(TapZoneLayout.LEFT_RIGHT, invertX = true, invertY = false, rtl = true, point = left, sideFraction = 0.25f))
    }

    @Test
    fun `the legacy switch keeps its meaning`() {
        // It resolves the zone first and swaps the action afterwards; for a left-right layout that is a mirror.
        val left = at(0.1f)
        assertEquals(
            ReaderInput.action(TapZoneLayout.LEFT_RIGHT, invertX = true, invertY = false, rtl = false, point = left, sideFraction = 0.25f),
            ReaderInput.action(TapZoneLayout.LEFT_RIGHT, inverted = true, rtl = false, point = left, sideFraction = 0.25f),
        )
        // In the middle nothing changes.
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.LEFT_RIGHT, inverted = true, rtl = false, point = at(0.5f), sideFraction = 0.25f))
    }

    @Test
    fun `a non-finite point is never a page turn`() {
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.LEFT_RIGHT, invertX = true, invertY = true, rtl = false, point = at(Float.NaN)))
    }
}
