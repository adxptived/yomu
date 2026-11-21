package app.yomu.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderSeekTest {
    @Test
    fun `the closest page is used including both endpoints`() {
        assertEquals(0, pageAtSeekFraction(0f, 8))
        assertEquals(4, pageAtSeekFraction(0.56f, 8))
        assertEquals(7, pageAtSeekFraction(1f, 8))
        assertEquals(0, pageAtSeekFraction(1f, 1))
    }

    @Test
    fun `each page survives a round trip via the progress track`() {
        for (page in 0..63) {
            assertEquals(page, pageAtSeekFraction(pageSeekFraction(page, 64), 64))
        }
    }

    @Test
    fun `search results step and wrap in both directions`() {
        assertEquals(0, nextHitIndex(null, 12, 1))
        assertEquals(11, nextHitIndex(null, 12, -1))
        assertEquals(3, nextHitIndex(2, 12, 1))
        assertEquals(1, nextHitIndex(2, 12, -1))
        assertEquals(0, nextHitIndex(11, 12, 1))
        assertEquals(11, nextHitIndex(0, 12, -1))
        assertEquals(0, nextHitIndex(0, 1, 1))
        assertEquals(null, nextHitIndex(null, 0, 1))
    }
}
