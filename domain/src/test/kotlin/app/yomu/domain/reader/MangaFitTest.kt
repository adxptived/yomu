package app.yomu.domain.reader

import app.yomu.domain.model.ScaleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaFitTest {

    @Test
    fun `only a meaningfully wider page is a wide scan`() {
        assertTrue(MangaFit.isWide(2000, 1000))
        assertTrue(!MangaFit.isWide(1000, 2000))
        assertTrue(!MangaFit.isWide(1000, 900))
        assertTrue(!MangaFit.isWide(0, 1000))
        assertTrue(!MangaFit.isWide(1000, 0))
    }

    @Test
    fun `a wide scan turns left for a left to right book`() {
        assertEquals(WidePageRotation.LEFT, MangaFit.widePageRotation(2000, 1000, enabled = true, rightToLeft = false))
    }

    @Test
    fun `a right to left book turns the other way`() {
        assertEquals(WidePageRotation.RIGHT, MangaFit.widePageRotation(2000, 1000, enabled = true, rightToLeft = true))
    }

    @Test
    fun `a portrait page or the option off never rotates`() {
        assertEquals(WidePageRotation.NONE, MangaFit.widePageRotation(1000, 2000, enabled = true, rightToLeft = false))
        assertEquals(WidePageRotation.NONE, MangaFit.widePageRotation(1000, 900, enabled = true, rightToLeft = false))
        assertEquals(WidePageRotation.NONE, MangaFit.widePageRotation(2000, 1000, enabled = false, rightToLeft = false))
        assertEquals(WidePageRotation.NONE, MangaFit.widePageRotation(0, 0, enabled = true, rightToLeft = false))
    }

    @Test
    fun `a tall page gets a zoom that fills the width`() {
        // 800x3000 fitted into 1080x2400 renders 640 px wide; filling the width is 1080/640.
        assertEquals(1.6875f, MangaFit.smartZoom(800, 3000, 1080, 2400), 0.001f)
    }

    @Test
    fun `a page that already fills the width keeps its fit`() {
        assertEquals(1f, MangaFit.smartZoom(1000, 2000, 1080, 2400), 0.001f)
    }

    @Test
    fun `smart fit is capped and refuses an unusable size`() {
        assertEquals(MangaFit.MAX_SMART_ZOOM, MangaFit.smartZoom(100, 5000, 1080, 2400), 0.001f)
        assertEquals(1f, MangaFit.smartZoom(0, 100, 1080, 2400), 0.001f)
        assertEquals(1f, MangaFit.smartZoom(100, 100, 0, 0), 0.001f)
    }

    @Test
    fun `a normal page decodes at the viewport fit`() {
        val size = MangaFit.decodeSize(2000, 3000, 1080, 2400, ScaleType.FIT_SCREEN)
        assertEquals(ImageSize(1080, 1620), size)
    }

    @Test
    fun `fit width follows the viewport width`() {
        val size = MangaFit.decodeSize(1000, 4000, 1080, 2400, ScaleType.FIT_WIDTH)
        assertEquals(ImageSize(1000, 4000), size)
    }

    @Test
    fun `a very tall strip stays inside the pixel budget`() {
        val size = MangaFit.decodeSize(800, 30_000, 1080, 2400, ScaleType.FIT_WIDTH)
        assertTrue(size != null)
        size!!
        assertTrue(size.width.toLong() * size.height <= MangaFit.MAX_DECODE_PIXELS)
        assertEquals(0.70710677f, size.width / 800f, 0.001f)
    }

    @Test
    fun `an original page is still bounded by the budget`() {
        val size = MangaFit.decodeSize(4000, 6000, 1080, 2400, ScaleType.ORIGINAL)
        assertTrue(size != null)
        size!!
        assertTrue(size.width.toLong() * size.height <= MangaFit.MAX_DECODE_PIXELS)
        assertEquals(2f / 3f, size.width.toFloat() / size.height, 0.01f)
    }

    @Test
    fun `decode never upscales a small image`() {
        val size = MangaFit.decodeSize(320, 480, 1080, 2400, ScaleType.FIT_SCREEN)
        assertEquals(ImageSize(320, 480), size)
    }

    @Test
    fun `an unknown image size has no decode size`() {
        assertNull(MangaFit.decodeSize(0, 100, 1080, 2400, ScaleType.FIT_SCREEN))
        assertNull(MangaFit.decodeSize(100, -1, 1080, 2400, ScaleType.FIT_SCREEN))
    }
}
