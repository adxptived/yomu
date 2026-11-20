package app.yomu.feature.library

import app.yomu.domain.library.LibrarySearchQuery
import app.yomu.domain.library.matches
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.LibraryFilter
import app.yomu.domain.model.LibrarySort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The adapters feed the pure expression with the fields each item type actually has. */
class LibrarySearchWiringTest {
    private fun book(
        title: String = "Dune",
        authors: List<String> = listOf("Frank Herbert"),
        tags: List<String> = listOf("scifi"),
        addedAt: Long = 1_600_000_000_000L,
    ) = Book(title = title, authors = authors, tags = tags, addedAt = addedAt, format = BookFormat.EPUB, uri = "file:///dune")

    @Test
    fun `a local book exposes title author genre and date to the expression`() {
        val b = book()
        assertTrue(LibrarySearchQuery.parse("title:dune && genre:scifi && added:>2020-01-01").expression.matches(b.toSearchFields()))
        assertFalse(LibrarySearchQuery.parse("author:asimov").expression.matches(b.toSearchFields()))
        assertTrue(LibrarySearchQuery.parse("-author:asimov").expression.matches(b.toSearchFields()))
    }

    @Test
    fun `an online title exposes artist source unread and update to the expression`() {
        val e = CatalogEntry(id = 1, sourceId = 42, url = "u", title = "Frieren", artist = "Yamada", unread = 3, lastUpdateAt = 1_700_000_000_000L)
        val fields = e.toSearchFields()
        assertTrue(LibrarySearchQuery.parse("artist:yamada && source:42 && unread:>0").expression.matches(fields))
        assertTrue(LibrarySearchQuery.parse("nextupdate:>2020-01-01").expression.matches(fields))
        assertFalse(LibrarySearchQuery.parse("unread:>5").expression.matches(fields))
    }

    @Test
    fun `online titles keep their structured filters while an advanced search runs`() {
        val keep = CatalogEntry(id = 1, sourceId = 5, url = "u", title = "Dune", author = "Herbert", unread = 3)
        val drop = CatalogEntry(id = 2, sourceId = 5, url = "u", title = "Dune", author = "Herbert", unread = 0)
        val other = CatalogEntry(id = 3, sourceId = 6, url = "u", title = "Other", author = "Nobody", unread = 3)
        val q = LibrarySearchAndSort("ignored", LibrarySearchQuery.parse("unread:>0"), LibrarySort.ADDED, false)
        val shown = listOf(keep, drop, other).forLibrary(LibraryFilter(), emptySet(), q, emptyMap())
        // Both keep (3 unread) and other (3 unread) satisfy unread:>0; drop has none.
        assertEquals(listOf(1L, 3L), shown.map { it.id })
    }

    @Test
    fun `plain online search still uses the substring path`() {
        val keep = CatalogEntry(id = 1, sourceId = 5, url = "u", title = "Dune", author = "Herbert")
        val drop = CatalogEntry(id = 2, sourceId = 5, url = "u", title = "Other", author = "Nobody")
        val q = LibrarySearchAndSort("dune", LibrarySearchQuery.parse("dune"), LibrarySort.ADDED, false)
        assertEquals(listOf(1L), listOf(keep, drop).forLibrary(LibraryFilter(), emptySet(), q, emptyMap()).map { it.id })
    }
}
