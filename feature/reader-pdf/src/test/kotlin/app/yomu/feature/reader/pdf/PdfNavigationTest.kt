package app.yomu.feature.reader.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfNavigationTest {
    @Test
    fun `turning advances by a spread from either selected page`() {
        assertEquals(2, adjacentPdfPage(0, 5, dual = true, forward = true))
        assertEquals(2, adjacentPdfPage(1, 5, dual = true, forward = true))
        assertEquals(0, adjacentPdfPage(3, 5, dual = true, forward = false))
        assertEquals(4, adjacentPdfPage(4, 5, dual = true, forward = true))
        assertEquals(2, adjacentPdfPage(2, 4, dual = true, forward = true))
    }

    @Test
    fun `single-page turns use physical page numbers`() {
        assertEquals(3, adjacentPdfPage(2, 5, dual = false, forward = true))
        assertEquals(1, adjacentPdfPage(2, 5, dual = false, forward = false))
    }
}
