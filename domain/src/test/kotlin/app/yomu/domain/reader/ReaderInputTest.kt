package app.yomu.domain.reader

import app.yomu.domain.model.TapZoneLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderInputTest {
    private val layout = TapZoneLayout.LEFT_RIGHT
    private val side = 0.25f

    /** Resolves a root px point of a 1080x1920 viewport without an origin offset. */
    private fun tapAt(x: Float, y: Float, originLeft: Float = 0f, originTop: Float = 0f): ReaderTapAction? {
        val point = ReaderInput.normalize(x, y, originLeft, originTop, 1080f, 1920f) ?: return null
        return ReaderInput.action(layout, inverted = false, rtl = false, point, sideFraction = side)
    }

    @Test
    fun `centre of the viewport opens the menu`() {
        assertEquals(ReaderTapAction.MENU, tapAt(540f, 960f))
    }

    @Test
    fun `left edge turns back and right edge turns forward`() {
        assertEquals(ReaderTapAction.PREV, tapAt(0f, 960f))
        assertEquals(ReaderTapAction.PREV, tapAt(269f, 960f))
        assertEquals(ReaderTapAction.NEXT, tapAt(811f, 960f))
        assertEquals(ReaderTapAction.NEXT, tapAt(1080f, 960f))
    }

    @Test
    fun `zone boundaries are inclusive on the outer side and menued inside`() {
        // 0.25 * 1080 = 270 is the first centre pixel; 0.75 * 1080 = 810 the first next pixel.
        assertEquals(ReaderTapAction.PREV, tapAt(269f, 960f))
        assertEquals(ReaderTapAction.MENU, tapAt(270f, 960f))
        assertEquals(ReaderTapAction.MENU, tapAt(810f, 960f))
        assertEquals(ReaderTapAction.NEXT, tapAt(811f, 960f))
    }

    @Test
    fun `a viewport origin is subtracted before normalizing`() {
        val centred = ReaderInput.normalize(620f, 1080f, left = 80f, top = 120f, width = 1080f, height = 1920f)
        assertNotNull(centred)
        assertEquals(0.5f, centred!!.x, 0.0001f)
        assertEquals(0.5f, centred.y, 0.0001f)
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(layout, false, false, centred, side))
    }

    @Test
    fun `a zero or negative viewport cannot resolve a zone`() {
        assertNull(ReaderInput.normalize(540f, 960f, 0f, 0f, 0f, 1920f))
        assertNull(ReaderInput.normalize(540f, 960f, 0f, 0f, 1080f, 0f))
        assertNull(ReaderInput.normalize(540f, 960f, 0f, 0f, -1080f, 1920f))
    }

    @Test
    fun `non-finite input cannot resolve a zone`() {
        assertNull(ReaderInput.normalize(Float.NaN, 960f, 0f, 0f, 1080f, 1920f))
        assertNull(ReaderInput.normalize(540f, Float.POSITIVE_INFINITY, 0f, 0f, 1080f, 1920f))
        assertNull(ReaderInput.normalize(540f, 960f, 0f, 0f, Float.NaN, 1920f))
    }

    @Test
    fun `a touch outside the viewport is ignored instead of clamped into a zone`() {
        assertNull(ReaderInput.normalize(-1f, 960f, 0f, 0f, 1080f, 1920f))
        assertNull(ReaderInput.normalize(1081f, 960f, 0f, 0f, 1080f, 1920f))
        assertNull(ReaderInput.normalize(540f, -0.5f, 0f, 0f, 1080f, 1920f))
        assertNull(ReaderInput.normalize(540f, 1920.5f, 0f, 0f, 1080f, 1920f))
    }

    @Test
    fun `every layout keeps its existing zone semantics`() {
        val centre = ReaderPoint(0.5f, 0.5f)
        val topLeft = ReaderPoint(0.1f, 0.1f)
        val bottomRight = ReaderPoint(0.9f, 0.9f)

        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.DISABLED, false, false, topLeft))
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.EDGES, false, false, centre))
        // Kindle has no middle zone: only its top band opens the menu, everything else turns a page.
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.KINDLE, false, false, ReaderPoint(0.5f, 0.1f)))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.KINDLE, false, false, centre))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.KINDLE, false, false, bottomRight))
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(TapZoneLayout.L_SHAPE, false, false, topLeft))
        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(TapZoneLayout.L_SHAPE, false, false, bottomRight))
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(TapZoneLayout.L_SHAPE, false, false, centre))
    }

    @Test
    fun `inversion swaps previous and next but never the menu`() {
        val left = ReaderPoint(0.1f, 0.5f)
        val right = ReaderPoint(0.9f, 0.5f)
        val centre = ReaderPoint(0.5f, 0.5f)

        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(layout, true, false, left, side))
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(layout, true, false, right, side))
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(layout, true, false, centre, side))
    }

    @Test
    fun `right to left reading swaps sides except for the edges layout`() {
        val left = ReaderPoint(0.1f, 0.5f)
        val right = ReaderPoint(0.9f, 0.5f)

        assertEquals(ReaderTapAction.NEXT, ReaderInput.action(layout, false, true, left, side))
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(layout, false, true, right, side))

        // EDGES is not a left/right layout, so RTL must not change what it already decided.
        val edgeRight = ReaderPoint(0.9f, 0.5f)
        assertEquals(
            ReaderInput.action(TapZoneLayout.EDGES, false, false, edgeRight),
            ReaderInput.action(TapZoneLayout.EDGES, false, true, edgeRight),
        )
    }

    @Test
    fun `inversion and right to left cancel each other out`() {
        val left = ReaderPoint(0.1f, 0.5f)
        assertEquals(ReaderTapAction.PREV, ReaderInput.action(layout, true, true, left, side))
    }

    @Test
    fun `a non-finite point resolves to the menu instead of a page turn`() {
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(layout, false, false, ReaderPoint(Float.NaN, 0.5f), side))
        assertEquals(ReaderTapAction.MENU, ReaderInput.action(layout, false, false, ReaderPoint(0.5f, Float.NaN), side))
    }
}
