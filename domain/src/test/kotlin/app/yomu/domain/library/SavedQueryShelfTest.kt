package app.yomu.domain.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A saved shelf survives an unknown value in one field instead of losing the whole spec. */
class SavedQueryShelfTest {
    @Test
    fun `a spec round trips through its stored form`() {
        val spec = LibraryQuerySpec(
            search = "sea",
            types = setOf(ContentType.PDF, ContentType.TEXT),
            origins = setOf(ContentOrigin.LOCAL),
            availability = setOf(Availability.FULLY_OFFLINE),
            read = Tri.NO,
            started = Tri.YES,
            downloaded = Tri.NO,
            favoritesOnly = true,
            tags = setOf("fantasy"),
            languages = setOf("en"),
            collectionId = 4,
            sort = LibrarySortKey.TITLE,
            ascending = true,
        )
        val restored = LibraryQuerySpecData.from(spec).toSpec()
        assertEquals(spec.copy(randomSeed = null), restored)
    }

    @Test
    fun `an unknown enum name falls back for that field only`() {
        val data = LibraryQuerySpecData(
            types = listOf("PDF", "HOLOGRAM"),
            read = "SOMETIMES",
            sort = "MOON_PHASE",
            tags = listOf("kept"),
        )
        val spec = data.toSpec()
        assertEquals(setOf(ContentType.PDF), spec.types)
        assertEquals(Tri.ANY, spec.read)
        assertEquals(LibrarySortKey.LAST_OPENED, spec.sort)
        assertEquals(setOf("kept"), spec.tags)
    }

    @Test
    fun `a spec with every field unknown still yields a usable default`() {
        val spec = LibraryQuerySpecData(types = listOf("X"), read = "X", sort = "X").toSpec()
        assertFalse(spec.isFiltered)
        assertEquals(LibraryQuerySpec.DEFAULT.sort, spec.sort)
    }

    @Test
    fun `an empty stored spec is the default query`() {
        assertEquals(LibraryQuerySpec.DEFAULT, LibraryQuerySpecData().toSpec())
    }

    @Test
    fun `a shelf's identity is its id, not its name`() {
        val a = SavedQueryShelf(1, "Unread", LibraryQuerySpecData(read = Tri.NO.name))
        val b = a.copy(name = "To read")
        assertEquals(a.id, b.id)
        assertTrue(a.spec.toSpec().isFiltered)
    }

    @Test
    fun `a display override is optional`() {
        val shelf = SavedQueryShelf(2, "Grid", LibraryQuerySpecData(), grid = true)
        assertTrue(shelf.grid == true)
        assertEquals(null, shelf.sortOverride)
    }
}
