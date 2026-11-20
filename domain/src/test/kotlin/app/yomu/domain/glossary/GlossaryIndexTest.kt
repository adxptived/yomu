package app.yomu.domain.glossary

import app.yomu.domain.model.Locator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlossaryIndexTest {
    @Test
    fun `normalization ignores case accents and extra whitespace`() {
        assertEquals("elena", GlossaryIndex.normalize("  ÉLENA  "))
        assertEquals("der zauberer", GlossaryIndex.normalize("Der\tZauberer"))
        assertEquals("霧島", GlossaryIndex.normalize("霧島"))
    }

    @Test
    fun `identity is any normalized name inside one book`() {
        val a = GlossaryEntry(id = 1, bookId = 7, term = "Elena", aliases = listOf("Lena"))
        val b = GlossaryEntry(id = 2, bookId = 7, term = "Léna")
        val otherBook = b.copy(bookId = 8)
        assertTrue(GlossaryIndex.sameIdentity(a, b))
        assertFalse(GlossaryIndex.sameIdentity(a, otherBook))
        assertEquals(setOf("elena", "lena"), GlossaryIndex.keys(a))
    }

    @Test
    fun `merge keeps stable identities and never duplicates an alias`() {
        val local = listOf(
            GlossaryEntry(id = 10, bookId = 7, term = "Elena", aliases = listOf("Lena"), note = "local", updatedAt = 100),
        )
        val incoming = listOf(
            GlossaryEntry(id = 900, bookId = 7, term = "Léna", note = "imported", updatedAt = 200),
            GlossaryEntry(id = 901, bookId = 7, term = "Elena", note = "older", updatedAt = 50),
            GlossaryEntry(id = 902, bookId = 8, term = "Elena", note = "another book", updatedAt = 300),
        )
        val result = GlossaryIndex.merge(local, incoming)
        assertEquals(2, result.total)
        assertEquals(1, result.merged)
        assertEquals(1, result.skipped)
        val merged = result.entries.first { it.bookId == 7L }
        assertEquals(10L, merged.id) // stable local identity survives an update
        assertEquals("imported", merged.note)
        assertEquals("another book", result.entries.first { it.bookId == 8L }.note)
    }

    @Test
    fun `replace starts empty and still deduplicates the incoming list`() {
        val incoming = listOf(
            GlossaryEntry(id = 1, bookId = 3, term = "Romeo", updatedAt = 1),
            GlossaryEntry(id = 2, bookId = 3, term = "  Romeo ", updatedAt = 2),
        )
        val result = GlossaryIndex.merge(listOf(GlossaryEntry(id = 99, term = "old")), incoming, replace = true)
        assertEquals(1, result.total)
        assertEquals(1L, result.entries.single().id) // the first identity is kept; the newer note content wins
        assertEquals(2L, result.entries.single().updatedAt)
    }

    @Test
    fun `export and import round trip every field including the locator`() {
        val entries = listOf(
            GlossaryEntry(
                id = 5, bookId = 11, locator = Locator(chapter = 3, progression = 0.25f, text = "here"),
                term = "Hamlet", aliases = listOf("Prince"), kind = GlossaryKind.CHARACTER,
                note = "The Danish prince", createdAt = 10, updatedAt = 20,
            ),
            GlossaryEntry(id = 6, term = "Elsinore", kind = GlossaryKind.PLACE),
        )
        val restored = GlossaryIndex.import(GlossaryIndex.export(entries))
        assertEquals(entries, restored)
    }

    @Test
    fun `a broken element does not discard the whole import and an unknown kind falls back`() {
        val json = """[{"id":1,"term":"A","kind":"CREATURE"},42,{"id":2,"term":"B","kind":"PLACE"}]"""
        val restored = GlossaryIndex.import(json)
        assertEquals(2, restored.size)
        assertEquals(GlossaryKind.TERM, restored[0].kind)
        assertEquals(GlossaryKind.PLACE, restored[1].kind)
        assertTrue(GlossaryIndex.import("not json").isEmpty())
    }

    @Test
    fun `matching searches aliases and notes`() {
        val entry = GlossaryEntry(term = "Elena", aliases = listOf("Lena"), note = "The queen")
        assertTrue(GlossaryIndex.matches(entry, "léna"))
        assertTrue(GlossaryIndex.matches(entry, "queen"))
        assertFalse(GlossaryIndex.matches(entry, "king"))
    }
}
