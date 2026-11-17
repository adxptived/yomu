package app.yomu.domain.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadRetentionTest {
    private fun item(
        id: Long,
        title: Long = 1,
        number: Float = id.toFloat(),
        read: Boolean = false,
        bookmarked: Boolean = false,
        bytes: Long = 100,
        shelves: Set<Long> = emptySet(),
        owned: Boolean = true,
        uploadedAt: Long = id,
    ) = RetentionItem(id, title, number, uploadedAt, read, bookmarked, bytes, shelves, owned)

    @Test
    fun `nothing is deleted without a rule`() {
        val plan = DownloadRetention.plan(listOf(item(1, read = true), item(2)), RetentionSettings())
        assertTrue(plan.delete.isEmpty())
        assertEquals(200, plan.bytesTotal)
    }

    @Test
    fun `read chapters are deleted when asked`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, read = true), item(2, read = false)),
            RetentionSettings(deleteRead = true),
        )
        assertEquals(listOf(1L), plan.delete)
        assertEquals(100, plan.bytesFreed)
        // An unread chapter that no rule protects is simply not listed as an exception.
        assertTrue(plan.kept.isEmpty())
    }

    @Test
    fun `a bookmarked chapter survives the read rule`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, read = true, bookmarked = true), item(2, read = true)),
            RetentionSettings(deleteRead = true),
        )
        assertEquals(listOf(2L), plan.delete)
        assertEquals(RetentionKeep.BOOKMARKED, plan.kept[1])
    }

    @Test
    fun `a file the app does not own is never deleted`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, read = true, owned = false, bytes = 900), item(2, read = true)),
            RetentionSettings(deleteRead = true, maxBytes = 50),
        )
        assertFalse(1L in plan.delete)
        assertEquals(RetentionKeep.NOT_OWNED, plan.kept[1])
        // Even over the cap: the app cannot remove a file it did not write.
        assertFalse(999L in plan.delete)
    }

    @Test
    fun `a protected shelf keeps its downloads`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, read = true, shelves = setOf(7)), item(2, read = true, shelves = setOf(8))),
            RetentionSettings(deleteRead = true, protectedShelves = setOf(7)),
        )
        assertEquals(listOf(2L), plan.delete)
        assertEquals(RetentionKeep.PROTECTED_SHELF, plan.kept[1])
    }

    @Test
    fun `the newest chapters of a title stay`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, number = 1f, read = true), item(2, number = 2f, read = true), item(3, number = 3f, read = true)),
            RetentionSettings(deleteRead = true, keepLastPerTitle = 2),
        )
        assertEquals(listOf(1L), plan.delete)
        assertEquals(RetentionKeep.RECENT, plan.kept[2])
        assertEquals(RetentionKeep.RECENT, plan.kept[3])
    }

    @Test
    fun `keep-last counts per title`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, title = 1, read = true), item(2, title = 2, read = true)),
            RetentionSettings(deleteRead = true, keepLastPerTitle = 1),
        )
        // Both are the newest of their own title: neither is deleted by the read rule.
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `a storage cap removes the oldest unprotected chapters first`() {
        val plan = DownloadRetention.plan(
            listOf(
                item(1, bytes = 400, uploadedAt = 1),
                item(2, bytes = 400, uploadedAt = 2),
                item(3, bytes = 400, uploadedAt = 3),
            ),
            RetentionSettings(maxBytes = 900),
        )
        // 1200 > 900: the oldest one goes, not the newest.
        assertEquals(listOf(1L), plan.delete)
        assertEquals(400, plan.bytesFreed)
    }

    @Test
    fun `a cap with everything protected deletes nothing`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, bytes = 900, bookmarked = true), item(2, bytes = 900, bookmarked = true)),
            RetentionSettings(maxBytes = 500),
        )
        assertTrue(plan.delete.isEmpty())
        assertEquals(2, plan.kept.size)
    }

    @Test
    fun `the read rule and the cap are applied together without duplicates`() {
        val plan = DownloadRetention.plan(
            listOf(item(1, bytes = 500, read = true, uploadedAt = 1), item(2, bytes = 500, uploadedAt = 2)),
            RetentionSettings(deleteRead = true, maxBytes = 300),
        )
        // Chapter 1 is read (deleted), the cap then needs 200 more: chapter 2 goes too.
        assertEquals(listOf(1L, 2L), plan.delete)
        assertEquals(1000, plan.bytesFreed)
    }

    @Test
    fun `a single read chapter is judged by the same protections`() {
        val settings = RetentionSettings(deleteRead = true, protectedShelves = setOf(4))
        assertTrue(DownloadRetention.shouldDeleteNow(item(1, read = true), settings))
        assertFalse(DownloadRetention.shouldDeleteNow(item(1, read = true, bookmarked = true), settings))
        assertFalse(DownloadRetention.shouldDeleteNow(item(1, read = true, shelves = setOf(4)), settings))
        assertFalse(DownloadRetention.shouldDeleteNow(item(1, read = true, owned = false), settings))
        assertFalse(DownloadRetention.shouldDeleteNow(item(1, read = true), RetentionSettings(deleteRead = false)))
    }

    @Test
    fun `an empty library plans nothing`() {
        val plan = DownloadRetention.plan(emptyList(), RetentionSettings(deleteRead = true, maxBytes = 1))
        assertTrue(plan.delete.isEmpty())
        assertFalse(plan.isPartial)
    }
}
