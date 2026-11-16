package app.yomu.domain.reader

/** The engine that owns a page, so page actions never have to pretend one engine is another. */
enum class PageEngine { TEXT, PDF, MANGA }

/**
 * What can be done with one page. A command that is not supported must not be offered: the user
 * should never press a button that silently does nothing.
 */
data class PageCapabilities(
    /** The page can be written to a file and shared: a manga page file or a rendered PDF page. */
    val exportable: Boolean,
    /** The page can become the cover of the book it belongs to. */
    val coverable: Boolean,
    /** Real, selectable text exists without running OCR. */
    val hasTextLayer: Boolean,
    /** A picture can be recognised on demand (OCR). */
    val ocr: Boolean,
    val bookmarkable: Boolean,
    /** A note can be attached: only for pages stored as a book in the library. */
    val notable: Boolean,
    /** The page can be replaced by an OCR overlay of translated blocks. */
    val translatable: Boolean,
)

/**
 * Identity of one physical page inside a session.
 *
 * [page] is always the physical page of the engine — not an index of a virtual half after a spread split and not
 * a window slot — so saved progress, a cover and a bookmark all point at the same page the reader shows.
 * [bookId] is 0 for online catalogue entries, which have their own annotations model.
 */
data class PageContext(
    val engine: PageEngine,
    val bookId: Long,
    /** Catalogue (online) entry, 0 for a local book. */
    val entryId: Long,
    val chapterId: Long,
    val page: Int,
    val pageCount: Int,
    val capabilities: PageCapabilities,
) {
    val isValid: Boolean get() = page in 0 until pageCount && pageCount > 0

    /** A note needs a library book; an online entry keeps its notes in the catalogue instead. */
    val acceptsNote: Boolean get() = capabilities.notable && bookId > 0
}

/**
 * Builds the capabilities of a page for each engine.
 *
 * Manga pages are cached files, so they can be exported, shared, used as a cover and recognised by OCR. Text
 * pages are drawn by the EPUB runtime and have no file of their own. PDF pages are rendered on demand: they can be
 * exported within a pixel budget and show a text layer only when the document has one — a scan needs OCR.
 */
object PageCapabilitiesOf {
    fun text(bookId: Long, hasTextLayer: Boolean): PageCapabilities = PageCapabilities(
        exportable = false,
        coverable = false,
        hasTextLayer = hasTextLayer,
        ocr = false,
        bookmarkable = true,
        notable = bookId > 0,
        translatable = true,
    )

    fun pdf(bookId: Long, hasTextLayer: Boolean): PageCapabilities = PageCapabilities(
        exportable = true,
        coverable = bookId > 0,
        hasTextLayer = hasTextLayer,
        ocr = !hasTextLayer,
        bookmarkable = true,
        notable = bookId > 0,
        translatable = true,
    )

    fun manga(bookId: Long): PageCapabilities = PageCapabilities(
        exportable = true,
        coverable = bookId > 0,
        hasTextLayer = false,
        ocr = true,
        bookmarkable = true,
        notable = bookId > 0,
        translatable = true,
    )
}
