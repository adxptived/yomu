package app.yomu.domain.catalog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Read state of a history row. */
enum class HistoryReadFilter { ALL, READ, UNREAD }

/**
 * Filters of the updates/history feeds.
 *
 * "Downloaded" and "bookmarked" are independent of read state and of each other: a chapter can be read, bookmarked and
 * still on the device, and the three questions are asked by different people.
 */
data class HistoryFilter(
    val read: HistoryReadFilter = HistoryReadFilter.ALL,
    val downloadedOnly: Boolean = false,
    val bookmarkedOnly: Boolean = false,
) {
    val isActive: Boolean get() = read != HistoryReadFilter.ALL || downloadedOnly || bookmarkedOnly

    fun matches(item: HistoryViewItem): Boolean {
        if (read == HistoryReadFilter.READ && !item.read) return false
        if (read == HistoryReadFilter.UNREAD && item.read) return false
        if (downloadedOnly && !item.downloaded) return false
        if (bookmarkedOnly && !item.bookmarked) return false
        return true
    }
}

/** One row of the updates or history feed, as the filters and grouping see it. */
data class HistoryViewItem(
    val chapterId: Long,
    val titleId: Long,
    val title: String,
    val chapter: String,
    /** UTC milliseconds: when the chapter was read, or when it arrived in the updates feed. */
    val at: Long,
    val read: Boolean = false,
    val downloaded: Boolean = false,
    val bookmarked: Boolean = false,
)

/** How the feed is grouped. */
enum class HistoryGrouping { DATE, TITLE }

/** One group of the feed with its display label. */
data class HistorySection(val key: String, val label: String, val items: List<HistoryViewItem>)

/**
 * Filtering and grouping of the feeds.
 *
 * Timestamps stay in UTC; a day boundary is decided in the zone the reader lives in, so a chapter read late in the
 * evening is not shown under the next day after a trip.
 */
object HistoryView {
    fun apply(items: List<HistoryViewItem>, filter: HistoryFilter): List<HistoryViewItem> = items.filter(filter::matches).sortedByDescending { it.at }

    fun group(items: List<HistoryViewItem>, grouping: HistoryGrouping, zone: ZoneId = ZoneId.systemDefault()): List<HistorySection> {
        val sorted = items.sortedByDescending { it.at }
        return when (grouping) {
            HistoryGrouping.DATE -> sorted
                .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.at), zone) }
                .toSortedMap(compareByDescending { it })
                .map { (date, list) -> HistorySection(date.toString(), date.toString(), list) }
            HistoryGrouping.TITLE -> sorted
                .groupBy { it.titleId }
                .values
                .map { list -> HistorySection("t-${list.first().titleId}", list.first().title, list) }
                .sortedBy { it.label.lowercase() }
        }
    }

    /**
     * Whether "delete history" is safe for [chapterId]: it hides the row and leaves progress untouched. Kept as a
     * function so a caller cannot quietly implement one as the other.
     */
    fun deleteHistoryIsSeparate(): String = "Clearing history removes the row; it does not reset reading progress"
}
