package app.yomu.data.library

import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProjectionTest {
    private fun book(
        id: Long,
        title: String,
        authors: String = "Author",
        added: Long = 0,
        opened: Long? = null,
        progress: Float = 0f,
    ) = Book(
        id = id,
        title = title,
        authors = listOf(authors),
        format = BookFormat.EPUB,
        uri = "file:///b$id.epub",
        addedAt = added,
        lastReadAt = opened,
        progress = progress,
    )

    private fun online(id: Long, title: String, author: String? = "Author", added: Long = 0, lastRead: Long = 0) = CatalogEntry(
        id = id,
        sourceId = 1,
        url = "/t$id",
        title = title,
        author = author,
        addedAt = added,
        lastReadAt = lastRead,
        unread = 0,
        chapterCount = 10,
    )

    @Test
    fun `the same number is two different rows`() {
        val local = LibraryProjectionItem.Local(book(7, "Seven"))
        val title = LibraryProjectionItem.Online(online(7, "Seven online"))
        assertNotEquals(local.identity.key, title.identity.key)
        assertEquals("book:7", local.identity.key)
        assertEquals("online:7", title.identity.key)
        assertEquals(local.identity, LibraryProjectionKey.parse(local.identity.key))
        assertEquals(title.identity, LibraryProjectionKey.parse(title.identity.key))
        assertNull(LibraryProjectionKey.parse("chapter:7"))
        assertNull(LibraryProjectionKey.parse("book:"))
    }

    @Test
    fun `title order is natural for both kinds`() {
        val local = listOf(book(1, "Vol. 10"), book(2, "Vol. 2"))
        val titles = listOf(online(3, "Vol. 1"))
        val order = LibraryProjection.order(local, titles, LibrarySort.TITLE, ascending = true).map { it.title }
        assertEquals(listOf("Vol. 1", "Vol. 2", "Vol. 10"), order)
    }

    @Test
    fun `equal sort values keep a deterministic place between loads`() {
        val local = listOf(book(2, "Same", added = 100), book(1, "Same", added = 100))
        val titles = listOf(online(9, "Same", added = 100), online(3, "Same", added = 100))
        val first = LibraryProjection.order(local, titles, LibrarySort.ADDED, ascending = true).map { it.identity.key }
        val reversedInput = LibraryProjection.order(local.reversed(), titles.reversed(), LibrarySort.ADDED, ascending = true)
            .map { it.identity.key }
        assertEquals(first, reversedInput)
        // Books before online titles, then the numeric id: the namespace is part of the tie-breaker.
        assertEquals(listOf("book:1", "book:2", "online:3", "online:9"), first)
    }

    @Test
    fun `descending order reverses the key but not the tie-breaker`() {
        val local = listOf(book(1, "A", added = 1), book(2, "B", added = 2))
        val titles = listOf(online(3, "C", added = 2))
        val descending = LibraryProjection.order(local, titles, LibrarySort.ADDED, ascending = false).map { it.identity.key }
        assertEquals(listOf("book:2", "online:3", "book:1"), descending)
    }

    @Test
    fun `last opened treats a never read title as the oldest`() {
        val local = listOf(book(1, "New", opened = null), book(2, "Read", opened = 50))
        val titles = listOf(online(3, "Online", lastRead = 10))
        val order = LibraryProjection.order(local, titles, LibrarySort.LAST_READ, ascending = false).map { it.title }
        assertEquals(listOf("Read", "Online", "New"), order)
    }

    @Test
    fun `progress of an online title counts its read chapters`() {
        val item = LibraryProjectionItem.Online(online(1, "T").copy(unread = 3, chapterCount = 12))
        assertEquals(0.75f, item.progress, 0.0001f)
        assertEquals(0f, LibraryProjectionItem.Online(online(2, "T").copy(chapterCount = 0)).progress, 0f)
        // Unread can exceed the known count while chapters are being fetched: never a negative share.
        assertEquals(0f, LibraryProjectionItem.Online(online(3, "T").copy(unread = 5, chapterCount = 2)).progress, 0f)
    }

    @Test
    fun `merge keeps both sorted streams without materialising them`() {
        val local = listOf(book(1, "A"), book(3, "C"))
        val titles = listOf(online(2, "B"), online(4, "D"))
        val merged = LibraryProjection.merge(
            local.map { LibraryProjectionItem.Local(it) }.asSequence(),
            titles.map { LibraryProjectionItem.Online(it) }.asSequence(),
            LibrarySort.TITLE,
            ascending = true,
        ).map { it.title }.toList()
        assertEquals(listOf("A", "B", "C", "D"), merged)
    }

    @Test
    fun `merge yields equal keys in the same order as the sort`() {
        val local = (1L..5L).map { book(it, "Same") }
        val titles = (6L..9L).map { online(it, "Same") }
        val sorted = LibraryProjection.order(local, titles, LibrarySort.TITLE, ascending = true)
        val merged = LibraryProjection.merge(
            local.map { LibraryProjectionItem.Local(it) }.asSequence(),
            titles.map { LibraryProjectionItem.Online(it) }.asSequence(),
            LibrarySort.TITLE,
            ascending = true,
        ).toList()
        assertEquals(sorted, merged)
    }

    @Test
    fun `a window is bounded by both totals`() {
        val w = LibraryProjection.window(offset = 10, limit = 20, localTotal = 15, onlineTotal = 10)
        assertEquals(10, w.start)
        assertEquals(25, w.end)
        assertEquals(15, w.size)
        assertEquals(25, w.total)
        assertTrue(LibraryProjection.window(offset = 25, limit = 20, localTotal = 15, onlineTotal = 10).isEmpty)
        assertEquals(0, LibraryProjection.window(offset = -5, limit = 20, localTotal = 0, onlineTotal = 0).start)
    }

    @Test
    fun `the mixed count is the sum of both queries`() {
        assertEquals(25, LibraryProjection.count(15, 10))
        assertEquals(0, LibraryProjection.count(0, 0))
        assertEquals(0, LibraryProjection.count(-1, 0))
    }
}
