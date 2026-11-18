package app.yomu.feature.reader.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The whole-book document shares one head: every chapter's CSS must stay inside its own section. */
class ChapterCssTest {
    private val scope = "section[data-yomu-chapter=\"1\"]"

    @Test
    fun `a class rule is confined to the chapter and keeps its body`() {
        val css = ChapterCss.scope(".note { display: none }", 1)
        assertTrue(css.startsWith("$scope .note{"), css)
        assertTrue(css.contains("display: none"), css)
    }

    @Test
    fun `html body and root become the section itself`() {
        assertTrue(ChapterCss.scope("body { margin: 0 }", 1).startsWith("$scope{ margin: 0 }"))
        assertTrue(ChapterCss.scope(":root { --ink: #111 }", 1).startsWith("$scope{ --ink: #111 }"))
        // One section, not a section inside a section; body classes are copied onto the section.
        assertTrue(ChapterCss.scope("html body.dark p { color: red }", 1).startsWith("$scope.dark p{ color: red }"))
        assertTrue(ChapterCss.scope("body > .note { color: red }", 1).startsWith("$scope >.note{ color: red }"))
    }

    @Test
    fun `a type selector that only starts like body is not remapped`() {
        assertTrue(ChapterCss.scope("bodyguard { color: red }", 1).startsWith("$scope bodyguard{"))
    }

    @Test
    fun `a selector list is scoped entry by entry`() {
        val css = ChapterCss.scope("h1, .note:hover > a::before { color: red }", 1)
        assertTrue(css.startsWith("$scope h1, $scope .note:hover >a::before{"), css)
    }

    @Test
    fun `conditional groups keep their own at-rule and scope their contents`() {
        val css = ChapterCss.scope("@media (min-width: 600px) { .note { display: none } }", 1)
        assertTrue(css.startsWith("@media (min-width: 600px)"), css)
        assertTrue(css.contains("$scope .note{"), css)
    }

    @Test
    fun `font faces and keyframes stay global`() {
        val font = "@font-face{ font-family: Book; src: url(\"/fonts/book.woff\") }"
        assertEquals(font, ChapterCss.scope(font, 1))
        val frames = "@keyframes fade{ from { opacity: 0 } to { opacity: 1 } }"
        assertEquals(frames, ChapterCss.scope(frames, 1))
    }

    @Test
    fun `a comment cannot break the scanner`() {
        val css = ChapterCss.scope("/* .a { color: red } */ .note { color: blue }", 1)
        assertTrue(css.contains("$scope .note{ color: blue }"), css)
        assertFalse(css.contains("$scope .a"), css)
    }
}
