package app.yomu.domain.download

/**
 * One downloaded chapter as the retention policy sees it.
 *
 * [owned] is false for a file the user linked or copied himself: the app must never delete something it did not write,
 * whatever the policy says.
 */
data class RetentionItem(
    val chapterId: Long,
    val titleKey: Long,
    val number: Float,
    val uploadedAt: Long,
    val read: Boolean,
    val bookmarked: Boolean,
    val bytes: Long,
    val shelfIds: Set<Long> = emptySet(),
    val owned: Boolean = true,
)

/** What the user allowed the app to reclaim. Zero means "no limit". */
data class RetentionSettings(
    /** Delete a chapter's files as soon as it is marked read. */
    val deleteRead: Boolean = false,
    /** Keep this many newest downloaded chapters of every title whatever else happens. */
    val keepLastPerTitle: Int = 0,
    /** Storage cap for downloaded chapters; the oldest unprotected ones go first. */
    val maxBytes: Long = 0,
    /** Shelves whose downloads are never removed automatically. */
    val protectedShelves: Set<Long> = emptySet(),
)

/** Why one item is kept; the UI shows the exceptions instead of silently ignoring them. */
enum class RetentionKeep { BOOKMARKED, NOT_OWNED, PROTECTED_SHELF, RECENT, UNREAD }

/** The chapters to remove, with the reasons the rest stay. */
data class RetentionPlan(
    val delete: List<Long>,
    val kept: Map<Long, RetentionKeep>,
    val bytesFreed: Long,
    val bytesTotal: Long,
) {
    val isPartial: Boolean get() = kept.isNotEmpty() && delete.isNotEmpty()
}

/**
 * Decides which downloaded chapters the app may delete.
 *
 * Two guarantees are absolute and come before every other rule: a downloaded chapter of a protected shelf or a
 * bookmarked chapter is never removed automatically, and a file the app does not own is never touched. The remaining
 * order is: keep the newest [RetentionSettings.keepLastPerTitle] chapters of a title, delete read ones when asked, and
 * finally bring the total under [RetentionSettings.maxBytes] oldest first.
 */
object DownloadRetention {
    fun plan(items: List<RetentionItem>, settings: RetentionSettings): RetentionPlan {
        val kept = LinkedHashMap<Long, RetentionKeep>()
        val byTitle = items.groupBy { it.titleKey }
        val recent = HashSet<Long>()
        byTitle.values.forEach { list ->
            if (settings.keepLastPerTitle > 0) {
                list.sortedWith(compareByDescending<RetentionItem> { it.number }.thenByDescending { it.uploadedAt })
                    .take(settings.keepLastPerTitle)
                    .forEach { recent += it.chapterId }
            }
        }

        items.forEach { item ->
            val reason = when {
                !item.owned -> RetentionKeep.NOT_OWNED
                item.bookmarked -> RetentionKeep.BOOKMARKED
                item.shelfIds.any { it in settings.protectedShelves } -> RetentionKeep.PROTECTED_SHELF
                item.chapterId in recent -> RetentionKeep.RECENT
                else -> null
            }
            if (reason != null) kept[item.chapterId] = reason
        }

        val total = items.sumOf { it.bytes }
        val delete = ArrayList<RetentionItem>()
        var freed = 0L
        val deletable = items.filter { it.chapterId !in kept }

        // Read chapters go first: that is the rule the user turned on.
        if (settings.deleteRead) {
            deletable.filter { it.read }.forEach {
                delete += it
                freed += it.bytes
            }
        }
        // Then the storage cap, oldest first, over whatever is left (unread chapters included: the cap is a cap).
        if (settings.maxBytes > 0) {
            var remaining = total - freed
            val deleted = delete.mapTo(HashSet()) { it.chapterId }
            deletable.filter { it.chapterId !in deleted }
                .sortedWith(compareBy<RetentionItem> { it.uploadedAt }.thenBy { it.chapterId })
                .forEach { item ->
                    if (remaining <= settings.maxBytes) return@forEach
                    delete += item
                    freed += item.bytes
                    remaining -= item.bytes
                }
        }
        return RetentionPlan(delete.map { it.chapterId }, kept, freed, total)
    }

    /**
     * The decision for a single chapter, used when it is marked read: the same rules without a storage cap, so a
     * bookmarked or protected chapter survives the moment it is read.
     */
    fun shouldDeleteNow(item: RetentionItem, settings: RetentionSettings): Boolean =
        settings.deleteRead &&
            item.owned &&
            !item.bookmarked &&
            item.shelfIds.none { it in settings.protectedShelves }
}
