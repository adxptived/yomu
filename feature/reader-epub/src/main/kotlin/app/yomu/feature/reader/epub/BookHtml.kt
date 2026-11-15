package app.yomu.feature.reader.epub

import android.content.Context
import android.net.Uri
import app.yomu.data.book.EpubPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.File

/**
 * The whole book as one HTML document: every chapter becomes a `<section data-yomu-chapter="i">`, so the WebView lays
 * out **all** pages at once — page turns cross chapters without loading a new document, page numbers are exact and the
 * bottom slider maps to real pages. Built once per book file and cached; a book too big for one document keeps the
 * per-chapter engine.
 *
 * Relative addresses are made absolute (`/OEBPS/text/img.png`) because every chapter now lives under one URL, and each
 * chapter's own styles are collected into the head once.
 */
object BookHtml {
    /** Replaced by the CSP, config, styles and script every time the document is served. */
    const val MARKER = "<!--__YOMU_BOOK__-->"

    /** Bump when the builder output changes: old cached documents are rebuilt. */
    private const val VERSION = 2
    private const val MAX_BYTES = 6 * 1024 * 1024
    private const val MAX_CHAPTERS = 600
    private const val KEEP_BOOKS = 8

    /** A cache, not data: Android may empty it and Settings → Storage clears it; the document is simply rebuilt. */
    private fun dir(context: Context): File = File(context.cacheDir, "book-html").apply { mkdirs() }

    private fun cacheFile(context: Context, bookId: Long): File = File(dir(context), "$bookId.html")
    private fun stampFile(context: Context, bookId: Long): File = File(dir(context), "$bookId.stamp")

    /** Identity of the file the document was built from: a rebuilt conversion or an edited book invalidates the cache. */
    fun sourceKey(context: Context, uri: Uri): String {
        val file = uri.path?.let { runCatching { File(it) }.getOrNull() }
        val size = file?.takeIf { it.isFile }?.length()
            ?: runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
            ?: -1
        val modified = file?.takeIf { it.isFile }?.lastModified() ?: 0
        return "$uri|$size|$modified"
    }

    /**
     * A cached document for the whole book, or `null` when the book must stay chapter by chapter: fixed layout,
     * a single chapter (its page numbers are exact already) or over the size/chapter limits.
     */
    suspend fun prepare(
        context: Context,
        bookId: Long,
        sourceKey: String,
        doc: TextDocument,
        onProgress: (Float) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        if (bookId <= 0 || doc.chapterCount <= 1 || doc.chapterCount > MAX_CHAPTERS) return@withContext null
        // A fixed-layout page sizes itself to the screen; it cannot join a reflowed document.
        if ((0 until doc.chapterCount).any { doc.isFixedLayout(it) }) return@withContext null

        val file = cacheFile(context, bookId)
        val stamp = stampFile(context, bookId)
        val wanted = "$VERSION|$sourceKey"
        if (file.isFile && stamp.isFile && runCatching { stamp.readText() }.getOrNull() == wanted) return@withContext file

        val built = runCatching { build(doc, onProgress) }.onFailure { Timber.w(it, "Cannot build the book document") }.getOrNull()
        if (built == null) {
            file.delete()
            stamp.delete()
            return@withContext null
        }
        val tmp = File(dir(context), "$bookId.html.tmp")
        tmp.writeText(built)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.delete()
            return@withContext null
        }
        stamp.writeText(wanted)
        prune(context)
        file
    }

    internal suspend fun build(doc: TextDocument, onProgress: (Float) -> Unit = {}): String? {
        val sections = StringBuilder(1 shl 18)
        val styles = LinkedHashSet<String>()
        val sheets = LinkedHashSet<String>()
        for (i in 0 until doc.chapterCount) {
            val path = doc.chapterPath(i)
            val parsed = Jsoup.parse(doc.chapterHtml(i), "https://book.yomu/$path")
            parsed.outputSettings().prettyPrint(false)
            sanitizeChapter(parsed)
            absolutize(parsed, path)
            val body = parsed.body()
            // Both are already absolute virtual paths: the head loads each sheet once for the whole book.
            styles += parsed.head().select("style").map { it.data() }.filter { it.isNotBlank() }
            sheets += parsed.head().select("link[rel=stylesheet][href]").map { it.attr("href") }
                .filter { it.startsWith("/") && !it.contains("__yomu/") && !it.contains("reader.css") }

            sections.append("<section data-yomu-chapter=\"").append(i).append('"')
            // Attribute values come from the book: escape them or a class name could break out of the section tag.
            body.className().takeIf { it.isNotBlank() }?.let { sections.append(" class=\"").append(escape(it)).append('"') }
            body.attr("style").takeIf { it.isNotBlank() }?.let { sections.append(" style=\"").append(escape(it)).append('"') }
            body.attr("dir").takeIf { it.isNotBlank() }?.let { sections.append(" dir=\"").append(escape(it)).append('"') }
            sections.append('>')
            body.childNodes().forEach { sections.append(it.outerHtml()) }
            sections.append("</section>")
            if (sections.length > MAX_BYTES) return null
            onProgress((i + 1f) / doc.chapterCount)
        }
        return buildString(sections.length + 4096) {
            append("<!DOCTYPE html><html")
            doc.language?.takeIf { it.isNotBlank() }?.let { append(" lang=\"").append(escape(it)).append('"') }
            append("><head><meta charset=\"utf-8\"><title>").append(escape(doc.title)).append("</title>")
            sheets.forEach { append("<link rel=\"stylesheet\" href=\"").append(it).append("\">") }
            styles.forEach { append("<style>").append(it).append("</style>") }
            // The reader's styles go after the book's own: their rules are the last word, as in the per-chapter engine.
            append(MARKER)
            append("</head><body>")
            append(sections)
            append("</body></html>")
        }
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private const val HEX = "0123456789ABCDEF"
    private const val SAFE = "-._~!$&'()*+,;=:@/"

    /** Percent-encodes only what a URL path cannot carry; `@`, `+` and the other sub-delimiters stay readable. */
    private fun encodePath(path: String): String = buildString(path.length) {
        for (byte in path.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 128 && (char.isLetterOrDigit() || char in SAFE)) {
                append(char)
            } else {
                append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
    }

    /**
     * Makes every address in [doc] absolute on the virtual host. A chapter is now a section of one document, so a
     * relative `../images/a.png` would otherwise resolve against the book document's own folder.
     */
    private fun absolutize(doc: Document, base: String) {
        for (el in doc.allElements) {
            for (attr in el.attributes()) {
                when (attr.key.lowercase()) {
                    "src", "poster" -> absolute(attr.value, base)?.let { attr.setValue(it) }
                    "href" -> {
                        if (el.tagName() == "link") {
                            val value = attr.value.trim()
                            if (value.isNotEmpty() && !external(value)) {
                                attr.setValue("/" + encodePath(EpubPaths.resolve(base, value)))
                            }
                        } else {
                            // Fragments stay inside this document; external links are left for the app to open.
                            absolute(attr.value, base)?.let { attr.setValue(it) }
                        }
                    }
                    "xlink:href" -> absolute(attr.value, base)?.let { attr.setValue(it) }
                    "srcset" -> attr.setValue(srcset(attr.value, base))
                    "style" -> attr.setValue(cssUrls(attr.value, base))
                }
            }
            if (el.tagName() == "style") {
                val css = el.data()
                if (css.isNotEmpty()) {
                    el.empty()
                    el.appendChild(DataNode(cssUrls(css, base)))
                }
            }
        }
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** A fragment, an absolute address or another document (data:, mailto:, http: …): not a file of this book. */
    private fun external(value: String): Boolean = value.startsWith("//") || SCHEME.containsMatchIn(value)

    private fun absolute(raw: String, base: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.startsWith("#") || external(value)) return null
        val hash = value.indexOf('#')
        val fragment = if (hash >= 0) value.substring(hash) else ""
        return "/" + encodePath(EpubPaths.resolve(base, value)) + fragment
    }

    private fun srcset(raw: String, base: String): String =
        raw.split(',').joinToString(",") { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) {
                part
            } else {
                val url = trimmed.substringBefore(' ')
                val rest = trimmed.substring(url.length)
                (absolute(url, base) ?: url) + rest
            }
        }

    private val CSS_URL = Regex("""url\(\s*(['"]?)([^'")]+)\1\s*\)""")

    private fun cssUrls(css: String, base: String): String =
        if (!css.contains("url(")) {
            css
        } else {
            CSS_URL.replace(css) { m ->
                val value = m.groupValues[2].trim()
                "url(\"${absolute(value, base) ?: value}\")"
            }
        }

    private fun prune(context: Context) {
        val all = dir(context).listFiles()?.filter { it.name.endsWith(".html") && !it.name.endsWith(".tmp") }.orEmpty()
        all.sortedByDescending { it.lastModified() }.drop(KEEP_BOOKS).forEach {
            it.delete()
            File(dir(context), it.name.removeSuffix(".html") + ".stamp").delete()
        }
    }
}
