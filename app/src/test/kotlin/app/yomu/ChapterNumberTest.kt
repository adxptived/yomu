package app.yomu

import app.yomu.domain.model.ChapterInfo
import app.yomu.feature.catalog.numberLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The chapter badge shows the source's own number, not the row's position in the filtered chapter list. */
class ChapterNumberTest {
    private fun chapter(number: Float) = ChapterInfo(id = 1, entryId = 2, url = "u", name = "Chapter", number = number)

    @Test
    fun `whole numbers lose the decimal tail`() {
        assertEquals("39", chapter(39f).numberLabel())
        assertEquals("1", chapter(1f).numberLabel())
    }

    @Test
    fun `fractional and split chapters keep their own number`() {
        assertEquals("39.1", chapter(39.1f).numberLabel())
        assertEquals("12.5", chapter(12.5f).numberLabel())
    }

    @Test
    fun `an unknown number stays empty instead of inventing one`() {
        assertNull(chapter(-1f).numberLabel())
        assertNull(chapter(0f).numberLabel())
    }
}
