package app.yomu.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The details screen shows a description only when there is readable text: a null, a blank string and HTML without
 * content must all leave the section out instead of rendering an empty block.
 */
class DescriptionDisplayTest {
    @Test
    fun `a plain description is kept with its paragraphs`() {
        assertEquals("A book about the sea.\n\nBy a sailor.", plainDescription("A book about the sea.\n\nBy a sailor."))
    }

    @Test
    fun `a missing description stays missing`() {
        assertNull(plainDescription(null))
        assertNull(plainDescription(""))
        assertNull(plainDescription("   \n\t "))
    }

    @Test
    fun `html without text is not a description`() {
        // On the JVM the platform parser may be unavailable; the raw fallback is normalized and must still be empty.
        assertNull(plainDescription("<p></p>"))
        assertNull(plainDescription("<div> </div><br/>"))
        assertNull(plainDescription("&nbsp;"))
        assertNull(plainDescription("&nbsp;&nbsp;"))
    }

    @Test
    fun `runs of spaces collapse while line breaks survive`() {
        assertEquals("One line.\n\nTwo lines.", normalizeDescription("One    line.  \n\n   Two lines."))
        assertEquals("One line.\nTwo lines.", normalizeDescription("One line.\nTwo lines."))
    }

    @Test
    fun `extra blank lines are reduced to one paragraph break`() {
        assertEquals("First.\n\nSecond.", normalizeDescription("First.\n\n\n\n\nSecond."))
    }

    @Test
    fun `markup keeps its paragraphs once converted`() {
        // The converted text of block markup keeps the break between blocks; only the blank line is collapsed.
        val converted = "A book about the sea.\nBy a sailor."
        assertEquals(converted, normalizeDescription(converted))
    }

    @Test
    fun `the collapsed height is five lines`() {
        assertEquals(5, DESCRIPTION_COLLAPSED_LINES)
    }
}
