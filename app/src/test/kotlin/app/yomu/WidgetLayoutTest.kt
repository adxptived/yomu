package app.yomu

import app.yomu.widget.ContinueWidgets
import app.yomu.widget.NewChaptersWidgets
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * B4: a small widget shows one useful item, a wide one shows the list. The rows depend on both the width and the
 * height, so a "4×1" tile does not try to draw three rows in 60 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLayoutTest {
    @Test
    fun aSmallTileShowsOneItem() {
        assertEquals(1, NewChaptersWidgets.rowsForTest(150, 110))
        assertEquals(1, ContinueWidgets.rowsForTest(150, 110))
        assertEquals(1, NewChaptersWidgets.rowsForTest(180, 200))
    }

    @Test
    fun aWideWidgetShowsUpToThree() {
        assertEquals(3, NewChaptersWidgets.rowsForTest(340, 240))
        assertEquals(3, ContinueWidgets.rowsForTest(340, 240))
        assertEquals(2, NewChaptersWidgets.rowsForTest(250, 240))
    }

    @Test
    fun theHeightStillLimitsTheRows() {
        assertEquals(1, NewChaptersWidgets.rowsForTest(340, 120))
        assertEquals(2, NewChaptersWidgets.rowsForTest(340, 180))
    }
}
