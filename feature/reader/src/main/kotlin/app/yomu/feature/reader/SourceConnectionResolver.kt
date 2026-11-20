package app.yomu.feature.reader

import android.content.Context
import android.net.Uri
import app.yomu.core.network.DataSaver
import app.yomu.data.book.PageSource
import app.yomu.data.book.PageSources
import app.yomu.data.catalog.CatalogRepository
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.ChapterInfo
import app.yomu.domain.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The reader's source-connection facade: one place decides how an online chapter is reached, what its content can
 * render and how much may be preloaded. These are the decisions that used to be repeated at every online open site in
 * [ReaderViewModel]; this class only reorganises them, it adds no source and no new dependency (the context and the
 * catalog are already injected into the ViewModel).
 */
class SourceConnectionResolver(
    private val context: Context,
    private val catalog: CatalogRepository,
) {
    enum class Route { DOWNLOADED, SOURCE }
    enum class WindowMode { TEXT, PAGES }

    /** A downloaded copy wins over the network; a missing file simply means the source must be asked. */
    fun routeOf(chapter: ChapterInfo): Route =
        if (chapter.localPath?.let(::File)?.takeIf { it.exists() } != null) Route.DOWNLOADED else Route.SOURCE

    /** Opens the page source chosen by [routeOf], with the same cache key the reader already uses. */
    suspend fun openPages(chapter: ChapterInfo): PageSource {
        val local = chapter.localPath?.let(::File)?.takeIf { it.exists() }
        if (local != null) {
            val format = if (local.isDirectory) BookFormat.FOLDER else BookFormat.CBZ
            val fake = Book(id = -chapter.id, title = chapter.name, format = format, uri = Uri.fromFile(local).toString())
            return withContext(Dispatchers.IO) { PageSources.open(context, fake, Uri.fromFile(local)) }
        }
        val (source, pages) = catalog.pages(chapter.id)
        return catalog.onlinePages(source, pages, "ch_${chapter.id}")
    }

    /** Which kind of window an online title opens: bounded text document or bounded manga pages. */
    fun windowMode(type: ContentType): WindowMode = if (isTextContent(type)) WindowMode.TEXT else WindowMode.PAGES

    companion object {
        /** Novels, books and audiobooks are laid out as text; everything else is drawn as pages. */
        fun isTextContent(type: ContentType): Boolean =
            type == ContentType.NOVEL || type == ContentType.BOOK || type == ContentType.AUDIO

        fun showImageCanvas(type: ContentType): Boolean = !isTextContent(type)

        /** Data saver caps preloading of network chapters only; local books are already on the device. */
        fun preloadLimit(dataSaverActive: Boolean, online: Boolean): Int? =
            if (online && dataSaverActive) DataSaver.PRELOAD_PAGES else null

        /** True when the content already on screen can be swapped into the new window without a loading state. */
        fun isWindowed(mode: WindowMode, textOpen: Boolean, pagesOpen: Boolean): Boolean =
            (mode == WindowMode.TEXT && textOpen) || (mode == WindowMode.PAGES && pagesOpen)
    }
}
