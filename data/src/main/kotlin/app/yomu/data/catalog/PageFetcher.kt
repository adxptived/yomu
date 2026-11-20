package app.yomu.data.catalog

import android.content.Context
import app.yomu.source.CatalogSource
import app.yomu.source.Page
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/** The server answered with something that is not an image (an error or verification page, an empty body). */
class NotAnImageException(message: String) : IOException(message)

/** Recognises images by their first bytes, so that error pages are not saved as manga pages. */
object ImageSniffer {
    const val HEAD_SIZE = 32

    /** File extension of the image in [head], or null when the bytes are not a known image format. */
    fun extension(head: ByteArray): String? {
        fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "jpg"
            at(0) == 0x89 && ascii(1, "PNG") -> "png"
            ascii(0, "GIF8") -> "gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
            ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> "avif"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1")) -> "heic"
            (at(0) == 0xFF && at(1) == 0x0A) || (ascii(4, "JXL ")) -> "jxl"
            ascii(0, "BM") -> "bmp"
            else -> null
        }
    }

    /** Whether [head] starts like a text document (HTML, XML or JSON), ignoring a BOM and white space. */
    fun looksLikeText(head: ByteArray): Boolean {
        var i = 0
        if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) i = 3
        while (i < head.size && head[i].toInt().toChar().isWhitespace()) i++
        if (i >= head.size) return false
        return head[i].toInt().toChar() in "<{["
    }

    fun head(file: File): ByteArray = file.inputStream().use { input ->
        val buf = ByteArray(HEAD_SIZE)
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        buf.copyOf(n)
    }
}

/**
 * Downloads page images with retries and validation, shared by the online reader cache and the chapter downloader.
 * Both paths now use [ResumablePageFetcher], so a truncated page can continue with Range when the server proves it is
 * still the same image, and a restarted transfer never leaves partial bytes in the final path.
 */
object PageFetcher {
    suspend fun fetch(
        context: Context,
        client: OkHttpClient,
        source: CatalogSource,
        page: Page,
        out: File,
        attempts: Int = 3,
    ): String = app.yomu.data.download.ResumablePageFetcher.fetch(context, client, source, page, out, attempts)
}
