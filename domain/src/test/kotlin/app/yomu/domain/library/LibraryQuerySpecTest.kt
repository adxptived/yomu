package app.yomu.domain.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Type, origin and availability are independent; "any" is not "no"; a partially downloaded title is not offline.
 */
class LibraryQuerySpecTest {
    private fun item(
        type: ContentType = ContentType.TEXT,
        origin: ContentOrigin = ContentOrigin.LOCAL,
        availability: Availability = Availability.FULLY_OFFLINE,
        read: Boolean = false,
        downloaded: Boolean = false,
        searchText: String = "Book",
        unread: Int? = null,
    ) = LibraryQueryItem(
        searchText = searchText,
        type = type,
        origin = origin,
        availability = availability,
        read = read,
        downloaded = downloaded,
        unreadChapters = unread,
    )

    @Test
    fun `an empty query matches everything and is not filtered`() {
        val spec = LibraryQuerySpec()
        assertTrue(spec.matches(item()))
        assertFalse(spec.isFiltered)
        assertEquals(0, spec.activeFilterCount)
    }

    @Test
    fun `tri state distinguishes any from no`() {
        val any = LibraryQuerySpec(read = Tri.ANY)
        val unreadOnly = LibraryQuerySpec(read = Tri.NO)
        val readOnly = LibraryQuerySpec(read = Tri.YES)

        assertTrue(any.matches(item(read = false)))
        assertTrue(any.matches(item(read = true)))

        assertTrue(unreadOnly.matches(item(read = false)))
        assertFalse(unreadOnly.matches(item(read = true)))

        assertTrue(readOnly.matches(item(read = true)))
        assertFalse(readOnly.matches(item(read = false)))
    }

    @Test
    fun `a tri state cycles any yes no`() {
        assertEquals(Tri.YES, Tri.ANY.next())
        assertEquals(Tri.NO, Tri.YES.next())
        assertEquals(Tri.ANY, Tri.NO.next())
    }

    @Test
    fun `type and origin are independent axes`() {
        val pdfFromCatalog = item(type = ContentType.PDF, origin = ContentOrigin.CATALOG)
        assertTrue(LibraryQuerySpec(types = setOf(ContentType.PDF)).matches(pdfFromCatalog))
        assertFalse(LibraryQuerySpec(types = setOf(ContentType.TEXT)).matches(pdfFromCatalog))
        assertTrue(LibraryQuerySpec(origins = setOf(ContentOrigin.CATALOG)).matches(pdfFromCatalog))
        // Filtering by type must not imply a local-only origin.
        assertTrue(LibraryQuerySpec(types = setOf(ContentType.PDF), origins = setOf(ContentOrigin.CATALOG, ContentOrigin.LOCAL)).matches(pdfFromCatalog))
    }

    @Test
    fun `partial availability is not the same as fully offline`() {
        val partial = item(availability = Availability.PARTIAL)
        assertTrue(LibraryQuerySpec(availability = setOf(Availability.PARTIAL)).matches(partial))
        assertFalse(LibraryQuerySpec(availability = setOf(Availability.FULLY_OFFLINE)).matches(partial))
    }

    @Test
    fun `a downloaded filter uses the downloaded axis not just the availability`() {
        val downloadedItem = item(downloaded = true, availability = Availability.FULLY_OFFLINE)
        assertTrue(LibraryQuerySpec(downloaded = Tri.YES).matches(downloadedItem))
        assertFalse(LibraryQuerySpec(downloaded = Tri.NO).matches(downloadedItem))
    }

    @Test
    fun `search is case insensitive and ignores surrounding spaces`() {
        assertTrue(LibraryQuerySpec(search = "  book ").matches(item(searchText = "A Book of the Sea")))
        assertFalse(LibraryQuerySpec(search = "poem").matches(item(searchText = "A Book of the Sea")))
    }

    @Test
    fun `the active count is per condition not per selected option`() {
        val spec = LibraryQuerySpec(
            types = setOf(ContentType.TEXT, ContentType.PDF, ContentType.AUDIO),
            read = Tri.NO,
            favoritesOnly = true,
        )
        assertEquals(3, spec.activeFilterCount)
    }

    @Test
    fun `an identity changes with any narrowing and is stable otherwise`() {
        val a = LibraryQuerySpec(read = Tri.NO, types = setOf(ContentType.PDF, ContentType.TEXT))
        val b = LibraryQuerySpec(read = Tri.NO, types = setOf(ContentType.TEXT, ContentType.PDF))
        assertEquals(a.identity, b.identity)
        assertTrue(a.identity != a.copy(read = Tri.YES).identity)
        assertTrue(a.identity != a.copy(sort = LibrarySortKey.TITLE).identity)
    }

    @Test
    fun `an unread sort does not apply to an item without chapters`() {
        val spec = LibraryQuerySpec(sort = LibrarySortKey.UNREAD)
        assertFalse(spec.sortAppliesTo(item(type = ContentType.PDF, unread = null)))
        assertTrue(spec.sortAppliesTo(item(type = ContentType.MANGA, unread = 3)))
    }

    @Test
    fun `a shelf filter matches an item in that shelf only`() {
        val inShelf = LibraryQueryItem(
            searchText = "x", type = ContentType.TEXT, origin = ContentOrigin.LOCAL,
            availability = Availability.FULLY_OFFLINE, collections = setOf(4L, 7L),
        )
        assertTrue(LibraryQuerySpec(collectionId = 4).matches(inShelf))
        assertFalse(LibraryQuerySpec(collectionId = 9).matches(inShelf))
    }

    @Test
    fun `tags match any of the selected tags`() {
        val tagged = LibraryQueryItem(
            searchText = "x", type = ContentType.TEXT, origin = ContentOrigin.LOCAL,
            availability = Availability.FULLY_OFFLINE, tags = setOf("fantasy", "epic"),
        )
        assertTrue(LibraryQuerySpec(tags = setOf("epic", "scifi")).matches(tagged))
        assertFalse(LibraryQuerySpec(tags = setOf("scifi")).matches(tagged))
    }
}
