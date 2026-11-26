package app.yomu.data.backup

import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.source.SourceManager
import app.yomu.domain.model.ContentType
import kotlinx.coroutines.flow.first
import java.io.OutputStream

/** What an export wrote, and what it deliberately left out. */
data class MihonExportResult(
    val titles: Int,
    val chapters: Int,
    val categories: Int,
    /** Local books and audiobooks: Mihon has no such entries. */
    val skippedLocal: Int,
    /** Online titles without a usable address. */
    val skippedUnreachable: Int,
) {
    val skipped: Int get() = skippedLocal + skippedUnreachable
    val hasAnything: Boolean get() = titles > 0
}

/**
 * Writes Yomu's online library into a Mihon/Tachiyomi backup (`.tachibk`): titles, chapters with read marks and
 * bookmarks, shelves as categories and the installed source names. Source ids follow the extension ids Mihon uses, so
 * a restored library opens once the same extension is installed there. Local books, audiobooks and downloaded files
 * are not part of the format; they stay in Yomu's own backup and are counted as skipped here.
 */
class MihonExporter(
    private val catalog: CatalogRepository,
    private val sources: SourceManager,
) {
    suspend fun export(out: OutputStream): MihonExportResult {
        val shelves = catalog.shelves()
        // A title stores category *orders*, not ids: the position in the user's shelf order is the stable link.
        val orderByShelf = shelves.mapIndexed { index, shelf -> shelf.id to index.toLong() }.toMap()
        val categories = shelves.mapIndexed { index, shelf -> MihonCategory(name = shelf.name, order = index.toLong()) }

        val favorites = catalog.observeFavorites().first()
        val titles = ArrayList<MihonManga>(favorites.size)
        val sourceNames = LinkedHashMap<Long, String>()
        var chapters = 0
        var skippedLocal = 0
        var skippedUnreachable = 0

        favorites.forEach { entry ->
            if (entry.type == ContentType.BOOK || entry.type == ContentType.AUDIO) {
                skippedLocal++
                return@forEach
            }
            if (entry.url.isBlank()) {
                skippedUnreachable++
                return@forEach
            }
            val known = catalog.chapters(entry.id)
            chapters += known.size
            sourceNames[entry.sourceId] = entry.sourceId.let { id -> sources.get(id)?.name } ?: "Source ${entry.sourceId}"
            val shelfOrders = catalog.tabs.onlineTabsOf(entry.id).mapNotNull(orderByShelf::get).sorted()
            titles += MihonManga(
                source = entry.sourceId,
                url = entry.url,
                title = entry.title,
                artist = entry.artist,
                author = entry.author,
                description = entry.description,
                genre = entry.genres,
                status = entry.status,
                thumbnailUrl = entry.coverUrl,
                dateAdded = entry.addedAt,
                chapters = known.map { chapter ->
                    MihonChapter(
                        url = chapter.url,
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        read = chapter.read,
                        bookmark = chapter.bookmarked,
                        lastPageRead = chapter.lastPageRead.toLong(),
                        dateUpload = chapter.uploadDate,
                        chapterNumber = chapter.number,
                        sourceOrder = chapter.sourceOrder.toLong(),
                    )
                },
                categories = shelfOrders,
                favorite = true,
            )
        }

        val backup = MihonBackup(
            backupManga = titles,
            backupCategories = categories,
            backupSources = sourceNames.map { (id, name) -> MihonSource(name = name, sourceId = id) },
        )
        out.write(MihonImporter.encode(backup))
        return MihonExportResult(
            titles = titles.size,
            chapters = chapters,
            categories = categories.size,
            skippedLocal = skippedLocal,
            skippedUnreachable = skippedUnreachable,
        )
    }
}
