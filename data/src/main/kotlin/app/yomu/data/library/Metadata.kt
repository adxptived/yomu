package app.yomu.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.yomu.core.common.Files
import app.yomu.data.audio.AudioMeta
import app.yomu.data.audio.AudiobookStore
import app.yomu.data.book.Containers
import app.yomu.data.book.Converted
import app.yomu.data.book.DocxConverter
import app.yomu.data.book.EpubParser
import app.yomu.data.book.Fb2Converter
import app.yomu.data.book.HtmlConverter
import app.yomu.data.book.MobiConverter
import app.yomu.data.book.PdfSource
import app.yomu.data.book.ResourceContainer
import app.yomu.data.book.TxtConverter
import app.yomu.data.book.firstLocal
import app.yomu.domain.model.BookFormat
import java.io.ByteArrayOutputStream
import java.io.File

data class ExtractedMeta(
    val title: String,
    val authors: List<String> = emptyList(),
    val series: String? = null,
    val seriesIndex: Float? = null,
    /** Upload date from ComicInfo, milliseconds; 0 when the file does not say. */
    val uploadDate: Long = 0,
    val language: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val subjects: List<String> = emptyList(),
    val cover: ByteArray? = null,
    val isManga: Boolean = false,
    val rtl: Boolean = false,
    /** For formats converted to EPUB at import time. */
    val converted: Converted? = null,
)

object MetadataExtractor {
    fun extract(context: Context, uri: Uri, format: BookFormat, fileName: String): ExtractedMeta {
        val fallbackTitle = fileName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { fileName }
        return when (format) {
            BookFormat.EPUB -> Containers.open(context, uri, format).use { c ->
                val pkg = EpubParser.parse(c)
                ExtractedMeta(
                    title = pkg.meta.title.ifBlank { fallbackTitle },
                    authors = pkg.meta.authors,
                    series = pkg.meta.series,
                    seriesIndex = pkg.meta.seriesIndex,
                    language = pkg.meta.language?.substringBefore('-')?.lowercase(),
                    description = pkg.meta.description,
                    publisher = pkg.meta.publisher,
                    subjects = pkg.meta.subjects,
                    cover = pkg.coverPath?.let { c.read(it) },
                    isManga = EpubParser.looksLikeComic(c, pkg),
                    rtl = pkg.rtl,
                )
            }
            BookFormat.PDF -> PdfSource.open(context, uri).use { pdf ->
                val meta = kotlinx.coroutines.runBlocking { pdf.meta() }
                val cover = kotlinx.coroutines.runBlocking { pdf.render(0, 480) }.let { toJpeg(it) }
                ExtractedMeta(
                    title = meta.title?.takeIf { it.isNotBlank() && !it.startsWith("Microsoft Word") } ?: fallbackTitle,
                    authors = listOfNotNull(meta.author?.takeIf { it.isNotBlank() }),
                    description = meta.subject?.takeIf { it.isNotBlank() },
                    subjects = meta.keywords?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    cover = cover,
                )
            }
            BookFormat.CBZ, BookFormat.CBR, BookFormat.CB7, BookFormat.FOLDER -> Containers.open(context, uri, format).use { c ->
                comicMeta(c, fallbackTitle)
            }
            BookFormat.FB2 -> fromConverted(Fb2Converter.convert(read(context, uri), fileName), fallbackTitle)
            BookFormat.TXT -> fromConverted(TxtConverter.convert(read(context, uri), fileName), fallbackTitle)
            BookFormat.HTML -> fromConverted(HtmlConverter.convert(read(context, uri), fileName), fallbackTitle)
            BookFormat.MOBI -> fromConverted(MobiConverter.convert(read(context, uri), fileName), fallbackTitle)
            BookFormat.DOCX -> Containers.open(context, uri, BookFormat.CBZ).use { c -> fromConverted(DocxConverter.convert(c, fileName), fallbackTitle) }
            BookFormat.AUDIO -> audioMeta(context, uri, fileName, fallbackTitle)
            BookFormat.UNKNOWN -> ExtractedMeta(fallbackTitle)
        }
    }

    fun convert(context: Context, uri: Uri, format: BookFormat, fileName: String): Converted? = when (format) {
        BookFormat.FB2 -> Fb2Converter.convert(read(context, uri), fileName)
        BookFormat.TXT -> TxtConverter.convert(read(context, uri), fileName)
        BookFormat.HTML -> HtmlConverter.convert(read(context, uri), fileName)
        BookFormat.MOBI -> MobiConverter.convert(read(context, uri), fileName)
        BookFormat.DOCX -> Containers.open(context, uri, BookFormat.CBZ).use { DocxConverter.convert(it, fileName) }
        else -> null
    }

    /** A single audio file by its tags; an audiobook manifest by its own fields. */
    private fun audioMeta(context: Context, uri: Uri, fileName: String, fallbackTitle: String): ExtractedMeta {
        if (!AudiobookStore.isManifest(fileName)) return AudioMeta.extract(context, uri, fallbackTitle, multiTrack = false)
        val m = AudiobookStore.decode(read(context, uri).decodeToString())
        return ExtractedMeta(title = m.title.ifBlank { fallbackTitle }, authors = m.authors, description = m.description, language = m.language)
    }

    private fun read(context: Context, uri: Uri): ByteArray = Files.open(context, uri).use { it.readBytes() }

    private fun fromConverted(c: Converted, fallbackTitle: String): ExtractedMeta {
        val m = c.builder.meta
        return ExtractedMeta(
            title = m.title.ifBlank { fallbackTitle },
            authors = m.authors,
            series = m.series,
            seriesIndex = m.seriesIndex,
            language = m.language?.substringBefore('-')?.lowercase(),
            description = m.description,
            publisher = m.publisher,
            subjects = m.subjects,
            cover = c.cover,
            converted = c,
        )
    }

    fun comicMeta(c: ResourceContainer, fallbackTitle: String): ExtractedMeta {
        val images = Containers.imageEntries(c)
        val info = c.entries.firstOrNull { it.substringAfterLast('/').equals("ComicInfo.xml", true) }?.let { c.read(it) }
            ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
            ?.let { ComicInfoXml.parse(it) }
        fun field(value: String) = value.trim().takeIf { it.isNotEmpty() }
        val series = field(info?.series.orEmpty())
        val number = field(info?.number.orEmpty())
        val title = field(info?.title.orEmpty()) ?: series?.let { s -> number?.let { "$s #$it" } ?: s } ?: fallbackTitle
        return ExtractedMeta(
            title = title,
            authors = listOfNotNull(field(info?.writer.orEmpty()), field(info?.penciller.orEmpty()))
                .flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            series = series,
            seriesIndex = number?.toFloatOrNull(),
            uploadDate = info?.uploadDate ?: 0,
            language = field(info?.languageIso.orEmpty()),
            description = field(info?.summary.orEmpty()),
            publisher = field(info?.extra?.get("Publisher").orEmpty()),
            subjects = (info?.genre?.split(',').orEmpty() + info?.extra?.get("Tags")?.split(',').orEmpty())
                .map { it.trim() }.filter { it.isNotEmpty() },
            cover = images.firstOrNull()?.let { c.read(it) },
            isManga = true,
            rtl = info?.manga == true,
        )
    }

    fun toJpeg(bmp: Bitmap, quality: Int = 85): ByteArray = ByteArrayOutputStream().also {
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)
        bmp.recycle()
    }.toByteArray()
}

/** Stores scaled cover thumbnails in the app files dir. */
class CoverStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "covers").also { it.mkdirs() }

    fun save(key: String, bytes: ByteArray): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (bmp.height > MAX_HEIGHT) {
            Bitmap.createScaledBitmap(bmp, bmp.width * MAX_HEIGHT / bmp.height, MAX_HEIGHT, true).also { if (it !== bmp) bmp.recycle() }
        } else {
            bmp
        }
        dir.listFiles { f -> f.name.startsWith("${key}_") }?.forEach { it.delete() }
        val file = File(dir, "${key}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        scaled.recycle()
        file.absolutePath
    }.getOrNull()

    fun delete(path: String?) {
        path?.let { File(it).delete() }
    }

    fun totalSize(): Long = Files.dirSize(dir)

    /**
     * Deletes the cover files no book points at. An interrupted import or a row removed outside the repository leaves
     * a picture behind; the cache in `filesDir` must not grow forever.
     *
     * Files newer than [olderThanMs] are kept: an import saves the picture before it inserts the row, and a scan that
     * runs in between must not delete it.
     */
    fun pruneOrphans(referenced: Collection<String>, olderThanMs: Long = 3_600_000): Int {
        val keep = referenced.mapTo(HashSet()) { File(it).name }
        val cutoff = System.currentTimeMillis() - olderThanMs
        return dir.listFiles { f -> f.isFile && f.lastModified() < cutoff && f.name !in keep }?.count { it.delete() } ?: 0
    }

    companion object {
        const val MAX_HEIGHT = 720
    }
}
