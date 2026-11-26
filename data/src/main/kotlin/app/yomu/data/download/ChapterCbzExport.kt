package app.yomu.data.download

import app.yomu.core.common.Files
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs the images of one downloaded chapter into a single CBZ: pages keep their numeric order and an optional
 * ComicInfo.xml travels with them, so the archive opens the same way in another reader.
 */
object ChapterCbzExport {
    /** Writes [pages] (sorted by file name) into [out]; returns how many pages were written. */
    fun write(pages: List<File>, out: OutputStream, comicInfo: String? = null): Int {
        require(pages.isNotEmpty()) { "No pages to export" }
        val ordered = pages.sortedBy { it.name }
        ZipOutputStream(out.buffered()).use { zip ->
            ordered.forEach { page ->
                zip.putNextEntry(ZipEntry(page.name))
                page.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            if (!comicInfo.isNullOrBlank()) {
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                zip.write(comicInfo.toByteArray())
                zip.closeEntry()
            }
        }
        return ordered.size
    }

    /** A safe file name for the system "create document" dialog. */
    fun suggestedName(title: String): String {
        val base = Files.sanitize(title.trim().ifBlank { "chapter" }).removeSuffix(".cbz")
        return "$base.cbz"
    }
}
