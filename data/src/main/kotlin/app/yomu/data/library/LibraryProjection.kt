package app.yomu.data.library

import app.yomu.domain.catalog.NaturalOrder
import app.yomu.domain.model.Book
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.LibrarySort

/**
 * Stable identity of one row of the library.
 *
 * The namespace is part of the identity: a local book 7 and an online title 7 are different rows, and a lazy list key
 * built from [key] can never make one replace the other. [rank] is the tie-breaker of the merged order — it is a
 * constant per namespace, so two rows with the same sort value keep the same place between loads.
 */
sealed interface LibraryProjectionKey {
    val key: String
    val rank: Int
    val id: Long

    data class Book(override val id: Long) : LibraryProjectionKey {
        override val key: String get() = "book:$id"
        override val rank: Int get() = 0
    }

    data class Online(override val id: Long) : LibraryProjectionKey {
        override val key: String get() = "online:$id"
        override val rank: Int get() = 1
    }

    companion object {
        /** Parses a key produced by [key]; null for anything that is not one of the two namespaces. */
        fun parse(value: String): LibraryProjectionKey? {
            val kind = value.substringBefore(':', "")
            val id = value.substringAfter(':', "").toLongOrNull() ?: return null
            return when (kind) {
                "book" -> Book(id)
                "online" -> Online(id)
                else -> null
            }
        }
    }
}

/**
 * One row of the mixed library: a local book or an online title with the fields the query sorts by.
 *
 * The UI may keep the two kinds in separate sections (the labelled online strip in a tab), but the order inside each
 * section and the identity of every row come from here, so "sort by title" means the same thing for both and no row
 * changes its key when the other kind is added or removed.
 */
sealed interface LibraryProjectionItem {
    val identity: LibraryProjectionKey
    val title: String
    val authors: String
    val addedAt: Long
    val openedAt: Long
    val progress: Float

    data class Local(val book: Book) : LibraryProjectionItem {
        override val identity: LibraryProjectionKey get() = LibraryProjectionKey.Book(book.id)
        override val title: String get() = book.title
        override val authors: String get() = book.authorsText
        override val addedAt: Long get() = book.addedAt
        override val openedAt: Long get() = book.lastReadAt ?: 0
        override val progress: Float get() = book.progress
    }

    data class Online(val entry: CatalogEntry) : LibraryProjectionItem {
        override val identity: LibraryProjectionKey get() = LibraryProjectionKey.Online(entry.id)
        override val title: String get() = entry.title
        override val authors: String get() = entry.author.orEmpty()
        override val addedAt: Long get() = entry.addedAt
        override val openedAt: Long get() = entry.lastReadAt
        override val progress: Float
            get() = if (entry.chapterCount <= 0) 0f
            else (entry.chapterCount - entry.unread).coerceAtLeast(0).toFloat() / entry.chapterCount
    }
}

/**
 * The one ordering of the library.
 *
 * Every sort key is compared the same way for both kinds, then the namespace and the numeric id break ties. A tie
 * breaker is not cosmetic: SQLite and Paging may return equal-key rows in any order, so without it a refresh, a new
 * page or a second load can swap two rows and move the scroll anchor.
 */
object LibraryProjection {
    fun comparator(sort: LibrarySort, ascending: Boolean): Comparator<LibraryProjectionItem> {
        val primary: Comparator<LibraryProjectionItem> = when (sort) {
            LibrarySort.TITLE -> compareBy(NaturalOrder) { it.title }
            LibrarySort.AUTHOR -> compareBy(NaturalOrder) { it.authors }
            LibrarySort.ADDED -> compareBy { it.addedAt }
            LibrarySort.LAST_READ -> compareBy { it.openedAt }
            LibrarySort.PROGRESS -> compareBy { it.progress }
        }
        val directed = if (ascending) primary else primary.reversed()
        // Ties always resolve ascending by namespace, then id: whichever direction the main key takes.
        return directed.thenBy { it.identity.rank }.thenBy { it.identity.id }
    }

    /** Both lists sorted by the same contract. */
    fun order(
        local: List<Book>,
        online: List<CatalogEntry>,
        sort: LibrarySort,
        ascending: Boolean,
    ): List<LibraryProjectionItem> {
        val comparator = comparator(sort, ascending)
        return (local.map { LibraryProjectionItem.Local(it) } + online.map { LibraryProjectionItem.Online(it) })
            .sortedWith(comparator)
    }

    /** [entries] in the library's order, for a screen that keeps the online titles in their own section. */
    fun orderOnline(entries: List<CatalogEntry>, sort: LibrarySort, ascending: Boolean): List<CatalogEntry> {
        val comparator = comparator(sort, ascending)
        return entries
            .map { LibraryProjectionItem.Online(it) }
            .sortedWith(comparator)
            .map { (it as LibraryProjectionItem.Online).entry }
    }

    /**
     * Merges two already sorted sequences without materialising them: the caller keeps the memory of each page and
     * only the merged window is built.
     */
    fun merge(
        local: Sequence<LibraryProjectionItem>,
        online: Sequence<LibraryProjectionItem>,
        sort: LibrarySort,
        ascending: Boolean,
    ): Sequence<LibraryProjectionItem> {
        val comparator = comparator(sort, ascending)
        return sequence {
            val a = local.iterator()
            val b = online.iterator()
            var nextA = if (a.hasNext()) a.next() else null
            var nextB = if (b.hasNext()) b.next() else null
            while (nextA != null || nextB != null) {
                val takeA = when {
                    nextA == null -> false
                    nextB == null -> true
                    else -> comparator.compare(nextA, nextB) <= 0
                }
                if (takeA) {
                    yield(nextA!!)
                    nextA = if (a.hasNext()) a.next() else null
                } else {
                    yield(nextB!!)
                    nextB = if (b.hasNext()) b.next() else null
                }
            }
        }
    }

    /**
     * The rows of the merged window [offset, offset + limit). [localTotal] and [onlineTotal] bound the two sources so a
     * paging source can ask for a window without loading either list in full.
     */
    fun window(
        offset: Int,
        limit: Int,
        localTotal: Int,
        onlineTotal: Int,
    ): LibraryWindow {
        val total = localTotal + onlineTotal
        val start = offset.coerceIn(0, maxOf(total, 0))
        val end = (start + limit).coerceIn(start, maxOf(total, 0))
        return LibraryWindow(start, end, total)
    }

    data class LibraryWindow(val start: Int, val end: Int, val total: Int) {
        val size: Int get() = end - start
        val isEmpty: Boolean get() = size == 0
    }

    /** The count of a merged list; the count and the rows must be produced from the same query. */
    fun count(local: Int, online: Int): Int = local.coerceAtLeast(0) + online.coerceAtLeast(0)
}
