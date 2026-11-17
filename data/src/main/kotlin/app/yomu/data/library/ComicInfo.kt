package app.yomu.data.library

import app.yomu.data.book.Xhtml
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * The ComicInfo.xml fields Yomu reads and writes. The download writer, the importer and the editor used to have three
 * different implementations, so a download → import → export round trip dropped fields. This is the one parser/writer
 * for the download and import paths; unknown elements are preserved in [extra].
 *
 * [uploadDate] is not part of the Anansi schema: it is written as `<UploadDate>` (milliseconds) so a chapter's upload
 * date survives a download and a later import. Other readers ignore it.
 */
data class ComicInfo(
    val title: String = "",
    val series: String = "",
    val number: String = "",
    val volume: String = "",
    val writer: String = "",
    val penciller: String = "",
    val genre: String = "",
    val languageIso: String = "",
    val summary: String = "",
    val year: String = "",
    val manga: Boolean = false,
    /** The raw `<Pages>` subtree, preserved so a save does not drop per-page metadata. */
    val pagesXml: String = "",
    val extra: Map<String, String> = emptyMap(),
    val uploadDate: Long = 0,
) {
    val isEmpty: Boolean get() = this == ComicInfo()
}

object ComicInfoXml {
    private const val UPLOAD_DATE = "UploadDate"
    private val KNOWN =
        setOf("Title", "Series", "Number", "Volume", "Writer", "Penciller", "Genre", "LanguageISO", "Summary", "Year", "Manga", "PageCount", "Pages", UPLOAD_DATE)

    fun parse(xml: String): ComicInfo {
        val doc = runCatching { Jsoup.parse(xml, "", Parser.xmlParser()) }.getOrNull() ?: return ComicInfo()
        val root = doc.children().firstOrNull { it.tagName().equals("ComicInfo", true) } ?: return ComicInfo()
        fun v(name: String) = root.children().firstOrNull { it.tagName().equals(name, true) }?.wholeText()?.trim().orEmpty()
        val extra = root.children()
            .filter { el -> KNOWN.none { it.equals(el.tagName(), true) } && el.children().isEmpty() }
            .associate { it.tagName() to it.wholeText().trim() }
        val mangaValue = v("Manga")
        return ComicInfo(
            title = v("Title"),
            series = v("Series"),
            number = v("Number"),
            volume = v("Volume"),
            writer = v("Writer"),
            penciller = v("Penciller"),
            genre = v("Genre"),
            languageIso = v("LanguageISO"),
            summary = v("Summary"),
            year = v("Year"),
            manga = mangaValue.equals("Yes", true) || mangaValue.equals("YesAndRightToLeft", true) || mangaValue.equals("true", true),
            pagesXml = root.children().firstOrNull { it.tagName().equals("Pages", true) }?.html()?.trim().orEmpty(),
            extra = extra,
            uploadDate = v(UPLOAD_DATE).toLongOrNull() ?: 0,
        )
    }

    fun write(info: ComicInfo, pageCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<ComicInfo xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
        fun el(name: String, value: String) {
            if (value.isNotBlank()) append("  <").append(name).append('>').append(Xhtml.escape(value.trim())).append("</").append(name).append(">\n")
        }
        el("Title", info.title)
        el("Series", info.series)
        el("Number", info.number)
        el("Volume", info.volume)
        el("Summary", info.summary)
        el("Year", info.year)
        el("Writer", info.writer)
        el("Penciller", info.penciller)
        el("Genre", info.genre)
        el("LanguageISO", info.languageIso)
        info.extra.forEach { (k, v) -> if (k.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) el(k, v) }
        if (info.uploadDate > 0) el(UPLOAD_DATE, info.uploadDate.toString())
        if (info.pagesXml.isNotBlank()) append("  <Pages>").append(info.pagesXml).append("</Pages>\n")
        el("PageCount", pageCount.toString())
        el("Manga", if (info.manga) "YesAndRightToLeft" else "No")
        append("</ComicInfo>\n")
    }
}
