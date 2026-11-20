package app.yomu.feature.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** N05: the cleaning brush is a reversible layer over a staging copy. */
class CleaningLayerTest {

    @Test
    fun `strokes are staged and undoable without touching the original`() {
        val original = "<p>Hello</p>"
        val layer = CleaningLayer()
        layer.stage("a.xhtml", original)

        assertTrue(layer.apply("a.xhtml", "brush") { it.replace("Hello", "Hi") })
        assertEquals("<p>Hi</p>", layer.text("a.xhtml"))
        assertEquals(original, layer.changes().getValue("a.xhtml").first)
        assertEquals("<p>Hi</p>", layer.changes().getValue("a.xhtml").second)
        assertTrue(layer.canUndo)

        val undone = layer.undo()
        assertNotNull(undone)
        assertEquals("a.xhtml", undone!!.path)
        assertEquals(original, layer.text("a.xhtml"))
        assertTrue(layer.changes().isEmpty())
        assertFalse(layer.canUndo)
        assertTrue(layer.canRedo)

        assertNotNull(layer.redo())
        assertEquals("<p>Hi</p>", layer.text("a.xhtml"))
    }

    @Test
    fun `a no-op stroke is not recorded and a new stroke clears redo`() {
        val layer = CleaningLayer()
        layer.stage("a.xhtml", "same")
        assertFalse(layer.apply("a.xhtml", "no-op") { it })
        assertFalse(layer.canUndo)

        layer.apply("a.xhtml", "one") { it + "!" }
        layer.undo()
        assertTrue(layer.canRedo)
        layer.apply("a.xhtml", "two") { it + "?" }
        assertFalse(layer.canRedo)
        assertEquals("same?", layer.text("a.xhtml"))
    }

    @Test
    fun `discard restores every staged document`() {
        val layer = CleaningLayer()
        layer.stage("a.xhtml", "a")
        layer.stage("b.xhtml", "b")
        layer.apply("a.xhtml", "x") { "changed-a" }
        layer.apply("b.xhtml", "x") { "changed-b" }
        assertEquals(2, layer.changes().size)

        layer.discard()
        assertEquals("a", layer.text("a.xhtml"))
        assertEquals("b", layer.text("b.xhtml"))
        assertTrue(layer.changes().isEmpty())
        assertNull(layer.undo())
    }

    @Test
    fun `cleanXhtml edits text nodes but keeps markup`() {
        val html = "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
            "<p>Hello  world -- again</p><pre>keep  spacing</pre></body></html>"
        val cleaned = CleaningLayer.cleanXhtml(html, CleanupOptions(emptyParagraphs = false, spaces = true, dashes = true), "en")

        assertTrue(cleaned.contains("Hello world"), cleaned)
        assertTrue(cleaned.contains("<pre>keep  spacing</pre>"), cleaned)
        assertTrue(cleaned.contains("<p>"), cleaned)
        assertFalse(cleaned.contains("--"), cleaned)
    }
}
