package app.yomu.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryViewTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(date: String, time: String = "12:00") =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun item(
        id: Long,
        at: Long,
        read: Boolean = false,
        downloaded: Boolean = false,
        bookmarked: Boolean = false,
        titleId: Long = 1,
        title: String = "Title",
    ) = HistoryViewItem(id, titleId, title, "Chapter $id", at, read, downloaded, bookmarked)

    @Test
    fun `no filter shows everything in order`() {
        val items = listOf(item(1, at("2024-06-03", "09:00")), item(2, at("2024-06-04", "09:00")))
        assertEquals(listOf(2L, 1L), HistoryView.apply(items, HistoryFilter()).map { it.chapterId })
        assertFalse(HistoryFilter().isActive)
    }

    @Test
    fun `read and unread are separate choices`() {
        val items = listOf(item(1, at("2024-06-03"), read = true), item(2, at("2024-06-03")))
        assertEquals(listOf(1L), HistoryView.apply(items, HistoryFilter(read = HistoryReadFilter.READ)).map { it.chapterId })
        assertEquals(listOf(2L), HistoryView.apply(items, HistoryFilter(read = HistoryReadFilter.UNREAD)).map { it.chapterId })
        assertTrue(HistoryFilter(read = HistoryReadFilter.UNREAD).isActive)
    }

    @Test
    fun `downloaded and bookmarked combine with read state`() {
        val items = listOf(
            item(1, at("2024-06-03"), read = true, downloaded = true),
            item(2, at("2024-06-03"), read = true, bookmarked = true),
            item(3, at("2024-06-03"), downloaded = true, bookmarked = true),
        )
        val filter = HistoryFilter(read = HistoryReadFilter.READ, downloadedOnly = true)
        assertEquals(listOf(1L), HistoryView.apply(items, filter).map { it.chapterId })
        assertEquals(
            listOf(2L),
            HistoryView.apply(items, HistoryFilter(read = HistoryReadFilter.READ, bookmarkedOnly = true)).map { it.chapterId },
        )
        // Both flags together: chapter 3 is the one that has both.
        assertEquals(listOf(3L), HistoryView.apply(items, HistoryFilter(downloadedOnly = true, bookmarkedOnly = true)).map { it.chapterId })
    }

    @Test
    fun `date grouping uses the local day`() {
        val items = listOf(item(1, at("2024-06-03", "23:30")), item(2, at("2024-06-04", "00:30")))
        val groups = HistoryView.group(items, HistoryGrouping.DATE, zone)
        assertEquals(2, groups.size)
        assertEquals("2024-06-04", groups.first().key)
        assertEquals(listOf(2L), groups.first().items.map { it.chapterId })
        assertEquals("2024-06-03", groups[1].key)
    }

    @Test
    fun `title grouping keeps the newest first and names the group`() {
        val items = listOf(
            item(1, at("2024-06-03"), titleId = 2, title = "Zeta"),
            item(2, at("2024-06-05"), titleId = 1, title = "Alpha"),
            item(3, at("2024-06-04"), titleId = 1, title = "Alpha"),
        )
        val groups = HistoryView.group(items, HistoryGrouping.TITLE, zone)
        assertEquals(listOf("Alpha", "Zeta"), groups.map { it.label })
        assertEquals(listOf(2L, 3L), groups.first().items.map { it.chapterId })
    }

    @Test
    fun `clearing history is not a progress reset`() {
        assertTrue(HistoryView.deleteHistoryIsSeparate().contains("does not reset reading progress"))
    }
}
