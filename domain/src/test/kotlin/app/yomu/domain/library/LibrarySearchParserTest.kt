package app.yomu.domain.library

import app.yomu.domain.library.LibrarySearchComparison.EQ
import app.yomu.domain.library.LibrarySearchComparison.GT
import app.yomu.domain.library.LibrarySearchComparison.GTE
import app.yomu.domain.library.LibrarySearchComparison.LTE
import app.yomu.domain.library.LibrarySearchExpr.And
import app.yomu.domain.library.LibrarySearchExpr.Compare
import app.yomu.domain.library.LibrarySearchExpr.FieldText
import app.yomu.domain.library.LibrarySearchExpr.Not
import app.yomu.domain.library.LibrarySearchExpr.Or
import app.yomu.domain.library.LibrarySearchExpr.Text
import app.yomu.domain.library.LibrarySearchField.ADDED
import app.yomu.domain.library.LibrarySearchField.AUTHOR
import app.yomu.domain.library.LibrarySearchField.GENRE
import app.yomu.domain.library.LibrarySearchField.LANGUAGE
import app.yomu.domain.library.LibrarySearchField.TITLE
import app.yomu.domain.library.LibrarySearchField.UNREAD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The parser is pure: every case here is a query string, a tree shape and an evaluation against a tiny field view. */
class LibrarySearchParserTest {
    private data class TestFields(
        override val searchText: String = "",
        val values: Map<LibrarySearchField, String> = emptyMap(),
        val numbers: Map<LibrarySearchField, Long> = emptyMap(),
    ) : LibrarySearchFields {
        override fun text(field: LibrarySearchField): String? = values[field]
        override fun number(field: LibrarySearchField): Long? = numbers[field]
    }

    private fun fields(text: String = "", values: Map<LibrarySearchField, String> = emptyMap(), numbers: Map<LibrarySearchField, Long> = emptyMap()) =
        TestFields(text, values, numbers)

    @Test
    fun `plain text keeps the repository path and matches case insensitively`() {
        val q = LibrarySearchQuery.parse("  Dune  ")
        assertFalse(q.usesAdvanced)
        assertEquals(Text("Dune"), q.expression)
        assertTrue(q.expression.matches(fields(text = "Frank Herbert wrote DUNE")))
        assertFalse(q.expression.matches(fields(text = "Neuromancer")))
    }

    @Test
    fun `empty input matches everything without a filter`() {
        val q = LibrarySearchQuery.parse("   ")
        assertFalse(q.usesAdvanced)
        assertTrue(q.expression.matches(fields()))
    }

    @Test
    fun `bare words are implicit AND`() {
        val q = LibrarySearchQuery.parse("dune desert")
        assertFalse(q.usesAdvanced)
        val and = q.expression as And
        assertEquals(2, and.children.size)
        assertTrue(and.matches(fields(text = "The DUNE of DESERT")))
        assertFalse(and.matches(fields(text = "Dune only")))
    }

    @Test
    fun `a hyphen inside a word is not a negation`() {
        val q = LibrarySearchQuery.parse("sci-fi")
        assertFalse(q.usesAdvanced)
        assertEquals(Text("sci-fi"), q.expression)
        assertTrue(q.expression.matches(fields(text = "A SCI-FI novel")))
    }

    @Test
    fun `explicit AND is additive to a field prefix`() {
        val q = LibrarySearchQuery.parse("title:dune && author:herbert")
        assertTrue(q.usesAdvanced)
        val and = q.expression as And
        assertEquals(2, and.children.size)
        val hit = fields(values = mapOf(TITLE to "Dune", AUTHOR to "Frank Herbert"))
        assertTrue(and.matches(hit))
        assertFalse(and.matches(fields(values = mapOf(TITLE to "Dune", AUTHOR to "Someone Else"))))
    }

    @Test
    fun `word AND is the same operator`() {
        val q = LibrarySearchQuery.parse("TITLE:dune AND AUTHOR:herbert")
        assertTrue(q.expression is And)
        assertTrue(q.expression.matches(fields(values = mapOf(TITLE to "Dune", AUTHOR to "Frank Herbert"))))
    }

    @Test
    fun `OR matches either side`() {
        val q = LibrarySearchQuery.parse("title:dune || author:herbert")
        assertTrue(q.usesAdvanced)
        val or = q.expression as Or
        assertEquals(2, or.children.size)
        assertTrue(or.matches(fields(values = mapOf(TITLE to "Dune"))))
        assertTrue(or.matches(fields(values = mapOf(AUTHOR to "Frank Herbert"))))
        assertFalse(or.matches(fields(values = mapOf(TITLE to "Other", AUTHOR to "Other"))))
    }

    @Test
    fun `word OR is the same operator`() {
        val q = LibrarySearchQuery.parse("x OR y")
        assertTrue(q.expression is Or)
        assertTrue(q.expression.matches(fields(text = "x")))
        assertTrue(q.expression.matches(fields(text = "y")))
    }

    @Test
    fun `AND binds tighter than OR`() {
        val q = LibrarySearchQuery.parse("x || y && z")
        val or = q.expression as Or
        assertEquals(2, or.children.size)
        assertTrue(or.children[0] is Text)
        val and = or.children[1] as And
        assertEquals(2, and.children.size)
        assertTrue(q.expression.matches(fields(text = "x")))
        assertTrue(q.expression.matches(fields(text = "y z")))
        assertFalse(q.expression.matches(fields(text = "y only")))
        // "only" contains the letter y, so use a disjoint filler for the z-only case.
        assertFalse(q.expression.matches(fields(text = "z alpha")))
    }

    @Test
    fun `parentheses override precedence`() {
        val q = LibrarySearchQuery.parse("(x || y) && z")
        val and = q.expression as And
        assertTrue(and.children[0] is Or)
        assertTrue(q.expression.matches(fields(text = "x z")))
        assertTrue(q.expression.matches(fields(text = "y z")))
        assertFalse(q.expression.matches(fields(text = "x only")))
    }

    @Test
    fun `parentheses can nest`() {
        val q = LibrarySearchQuery.parse("(a && (b || c)) && -d")
        assertTrue(q.usesAdvanced)
        assertTrue(q.expression.matches(fields(text = "a b")))
        assertTrue(q.expression.matches(fields(text = "a c")))
        assertFalse(q.expression.matches(fields(text = "a b d")))
    }

    @Test
    fun `minus and NOT and bang all negate`() {
        val minus = LibrarySearchQuery.parse("dune -prequel")
        assertTrue(minus.usesAdvanced)
        assertTrue(minus.expression.matches(fields(text = "Dune")))
        assertFalse(minus.expression.matches(fields(text = "Dune prequel")))

        val bang = LibrarySearchQuery.parse("!prequel")
        assertTrue(bang.expression is Not)
        assertTrue(bang.expression.matches(fields(text = "Dune")))
        assertFalse(bang.expression.matches(fields(text = "DUNE PREQUEL")))

        val word = LibrarySearchQuery.parse("NOT prequel")
        assertTrue(word.expression is Not)
        assertTrue(word.expression.matches(fields(text = "Dune")))
    }

    @Test
    fun `field prefixes are matched against their own field`() {
        val q = LibrarySearchQuery.parse("title:dune author:herbert genre:scifi language:en")
        val hit = fields(
            values = mapOf(TITLE to "Dune", AUTHOR to "Frank Herbert", GENRE to "scifi, classic", LANGUAGE to "en"),
            text = "unrelated",
        )
        assertTrue(q.expression.matches(hit))
        assertFalse(q.expression.matches(hit.copy(values = hit.values + (GENRE to "romance"))))
        assertFalse(q.expression.matches(hit.copy(values = hit.values + (LANGUAGE to "fr"))))
    }

    @Test
    fun `field names are case insensitive and have aliases`() {
        val q = LibrarySearchQuery.parse("TITLE:dune lang:en tags:epic")
        assertTrue(q.expression.matches(fields(values = mapOf(TITLE to "Dune", LANGUAGE to "en", GENRE to "epic"))))
    }

    @Test
    fun `a quoted phrase stays one value`() {
        val q = LibrarySearchQuery.parse("title:\"the lord\"")
        assertTrue(q.usesAdvanced)
        val field = q.expression as FieldText
        assertEquals(TITLE, field.field)
        assertEquals("the lord", field.value)
        assertTrue(field.matches(fields(values = mapOf(TITLE to "The Lord of the Rings"))))
    }

    @Test
    fun `an unknown field is a harmless literal match`() {
        val q = LibrarySearchQuery.parse("foobar:mystery")
        assertTrue(q.expression is Text)
        assertEquals("foobar:mystery", (q.expression as Text).value)
        assertTrue(q.expression.matches(fields(text = "a foobar:mystery note")))
        assertFalse(q.expression.matches(fields(text = "nothing here")))
    }

    @Test
    fun `numeric comparisons support all operators`() {
        val gt = LibrarySearchQuery.parse("unread:>2").expression as Compare
        assertEquals(UNREAD, gt.field)
        assertEquals(GT, gt.op)
        assertEquals(2L, gt.value)
        assertTrue(gt.matches(fields(numbers = mapOf(UNREAD to 3))))
        assertFalse(gt.matches(fields(numbers = mapOf(UNREAD to 2))))

        val gte = LibrarySearchQuery.parse("unread>=2").expression as Compare
        assertEquals(GTE, gte.op)
        assertTrue(gte.matches(fields(numbers = mapOf(UNREAD to 2))))

        val lte = LibrarySearchQuery.parse("unread<=1").expression as Compare
        assertEquals(LTE, lte.op)
        assertTrue(lte.matches(fields(numbers = mapOf(UNREAD to 1))))
        assertFalse(lte.matches(fields(numbers = mapOf(UNREAD to 2))))

        val eq = LibrarySearchQuery.parse("unread:5").expression as Compare
        assertEquals(EQ, eq.op)
        assertTrue(eq.matches(fields(numbers = mapOf(UNREAD to 5))))
    }

    @Test
    fun `date comparisons accept a yyyy dash mm dash dd value`() {
        val epoch = 1_577_836_800_000L // 2020-01-01 UTC
        val q = LibrarySearchQuery.parse("added:>2020-01-01")
        val cmp = q.expression as Compare
        assertEquals(ADDED, cmp.field)
        assertEquals(epoch, cmp.value)
        assertTrue(cmp.matches(fields(numbers = mapOf(ADDED to epoch + 1))))
        assertFalse(cmp.matches(fields(numbers = mapOf(ADDED to epoch))))
    }

    @Test
    fun `a comparison on a text field degrades to a literal`() {
        val q = LibrarySearchQuery.parse("title:>abc")
        assertTrue(q.expression is Text)
        assertEquals("title:>abc", (q.expression as Text).value)
    }

    @Test
    fun `an unparsable comparison value degrades to a literal`() {
        val q = LibrarySearchQuery.parse("unread:>many")
        assertTrue(q.expression is Text)
        assertEquals("unread:>many", (q.expression as Text).value)
    }

    @Test
    fun `malformed input falls back to plain substring search`() {
        val broken = listOf("(", ")", "foo &&", "&& foo", "title:", "(foo", "foo)", "foo & bar", "foo (bar", ">", "foo ||")
        broken.forEach { raw ->
            val q = LibrarySearchQuery.parse(raw)
            assertFalse(q.usesAdvanced, raw)
            assertTrue(q.expression is Text, raw)
            assertEquals(raw, (q.expression as Text).value, raw)
        }
    }

    @Test
    fun `a fallback still finds the raw text`() {
        val q = LibrarySearchQuery.parse("(dune")
        assertFalse(q.usesAdvanced)
        assertTrue(q.expression.matches(fields(text = "search for (dune today")))
    }

    @Test
    fun `deep nesting falls back instead of recursing further`() {
        val deep = "(".repeat(14) + "x" + ")".repeat(14)
        val q = LibrarySearchQuery.parse(deep)
        assertFalse(q.usesAdvanced)
        assertEquals(deep, (q.expression as Text).value)
    }

    @Test
    fun `an oversized query falls back`() {
        val long = "a".repeat(600)
        val q = LibrarySearchQuery.parse(long)
        assertFalse(q.usesAdvanced)
        assertEquals(long, (q.expression as Text).value)
    }

    @Test
    fun `the domain item adapter exposes its search text and unread count`() {
        val item = LibraryQueryItem(
            searchText = "Dune by Frank Herbert",
            type = ContentType.TEXT,
            origin = ContentOrigin.LOCAL,
            availability = Availability.FULLY_OFFLINE,
            unreadChapters = 4,
            language = "en",
        )
        assertTrue(LibrarySearchQuery.parse("dune unread:>3").expression.matches(item.toSearchFields()))
        assertFalse(LibrarySearchQuery.parse("dune unread:>5").expression.matches(item.toSearchFields()))
    }

    @Test
    fun `parsing is deterministic for the same string`() {
        val raw = "title:dune && (author:herbert || -genre:scifi)"
        assertEquals(LibrarySearchQuery.parse(raw), LibrarySearchQuery.parse(raw))
    }
}
