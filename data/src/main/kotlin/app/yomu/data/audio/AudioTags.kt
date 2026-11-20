package app.yomu.data.audio

import app.yomu.domain.audio.AudioGainTag
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * Description and comment tags of a local audio file.
 *
 * [android.media.MediaMetadataRetriever] exposes no universal description: an ID3 `TIT3`/`COMM` or a Vorbis
 * `DESCRIPTION` is not reachable through it, so a file that carries a synopsis shows no description at all. This
 * reader takes only the few text frames that can hold one, and only up to [MAX_TAG_BYTES]: the cover art in the same
 * tag can be megabytes.
 *
 * When the file has nothing usable the result is null — an audio file without a description never gets invented text.
 */
object AudioTags {
    /** Longest description kept: the details screen shows a collapsed preview and this is a synopsis, not a book. */
    const val MAX_DESCRIPTION_CHARS = 4000

    /** How much of the tag is read. ID3v2 with a cover picture is far larger, but its text frames come first. */
    private const val MAX_TAG_BYTES = 512L * 1024

    private const val MAX_ID3_TAG = 32L * 1024 * 1024

    /** Description of [file], or null when it has none. Never throws: an unreadable tag is simply no description. */
    fun description(file: File): String? = runCatching {
        if (!file.isFile) return null
        Files.newByteChannel(file.toPath(), StandardOpenOption.READ).use { channel -> description(channel) }
    }.getOrNull()

    /**
     * Description of a track behind [uri]: a plain file is read directly, a storage-framework document through the
     * content resolver, which is the only way to open it.
     */
    fun description(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
        if (uri.scheme == "file") return description(File(uri.path ?: return null))
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).channel.use { channel -> description(channel) }
        }
    }.getOrNull()

    fun description(channel: SeekableByteChannel): String? {
        val fields = fields(channel) ?: return null
        val raw = DESCRIPTION_KEYS.firstNotNullOfOrNull { fields[it] } ?: return null
        return clean(raw)
    }

    /** ReplayGain of a local track, or null when it declares none. Never throws. */
    fun gain(file: File): AudioGainTag? = runCatching {
        if (!file.isFile) return null
        Files.newByteChannel(file.toPath(), StandardOpenOption.READ).use { channel -> gain(channel) }
    }.getOrNull()

    fun gain(context: android.content.Context, uri: android.net.Uri): AudioGainTag? = runCatching {
        if (uri.scheme == "file") return gain(File(uri.path ?: return null))
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).channel.use { channel -> gain(channel) }
        }
    }.getOrNull()

    fun gain(channel: SeekableByteChannel): AudioGainTag? {
        val fields = fields(channel) ?: return null
        fun value(name: String): String? = fields.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val gainDb = parseGain(value("REPLAYGAIN_TRACK_GAIN") ?: value("TXXX:REPLAYGAIN_TRACK_GAIN")) ?: return null
        val peak = parseGain(value("REPLAYGAIN_TRACK_PEAK") ?: value("TXXX:REPLAYGAIN_TRACK_PEAK"))?.takeIf { it > 0f }
        return AudioGainTag(gainDb, peak)
    }

    /** A gain tag is text like "-3.21 dB"; only the number is meaningful here. */
    internal fun parseGain(text: String?): Float? =
        text?.let { GAIN_NUMBER.find(it)?.value?.toFloatOrNull() }?.takeIf { it.isFinite() }

    /** Description and gain tags of a file: the few text frames this reader understands. */
    private fun fields(channel: SeekableByteChannel): Map<String, String>? {
        val head = readAt(channel, 0, 12) ?: return null
        return when {
            head.ascii(0, 3) == "ID3" -> id3(channel, head)
            head.ascii(4, 4) == "ftyp" -> mp4(channel)?.let { mapOf("DESCRIPTION" to it) }
            head.ascii(0, 4) == "OggS" -> vorbis(channel, head)
            else -> null
        }
    }

    /** One line of plain text: paragraph breaks kept, other runs of whitespace collapsed, HTML tags dropped. */
    internal fun clean(raw: String): String? {
        var text = raw.replace("\u0000", "").trim()
        if (text.isEmpty()) return null
        // A description written as HTML is common in tags; the details screen shows plain text.
        if (text.contains('<') && text.contains('>')) {
            text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p\\s*>"), "\n\n")
                .replace(Regex("<[^>]{1,200}>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
        }
        text = text.split('\n').joinToString("\n") { line -> line.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (text.isEmpty()) return null
        return if (text.length > MAX_DESCRIPTION_CHARS) text.take(MAX_DESCRIPTION_CHARS).trimEnd() else text
    }

    // ---------------------------------------------------------------- ID3v2

    /** Frames that carry a synopsis, most specific first. */
    private val DESCRIPTION_KEYS = listOf("TIT3", "COMM", "TXXX:DESCRIPTION", "TXXX:COMMENT", "DESCRIPTION", "COMMENT", "SUMMARY")

    private val GAIN_NUMBER = Regex("[-+]?\\d+(?:\\.\\d+)?")

    private fun id3(channel: SeekableByteChannel, head: ByteBuffer): Map<String, String>? {
        val major = head.get(3).toInt()
        val flags = head.get(5).toInt()
        if (major !in 3..4 || flags and 0x80 != 0) return null
        val size = syncsafe(head, 6).toLong()
        if (size <= 0 || size > MAX_ID3_TAG) return null
        val end = minOf(10 + size, channel.size())
        var pos = 10L
        if (flags and 0x40 != 0) {
            val ext = readAt(channel, pos, 4) ?: return null
            pos += if (major == 4) syncsafe(ext, 0).toLong() else (ext.getInt(0).toLong() and 0xFFFFFFFFL) + 4
        }
        val wanted = LinkedHashMap<String, String>()
        // Frame headers are read one by one: only the text frames wanted here are loaded.
        while (pos + 10 <= end && pos <= MAX_TAG_BYTES) {
            val f = readAt(channel, pos, 10) ?: break
            if (f.get(0).toInt() == 0) break
            val id = f.ascii(0, 4)
            val frameSize = if (major == 4) syncsafe(f, 4).toLong() else f.getInt(4).toLong() and 0xFFFFFFFFL
            if (frameSize < 0 || pos + 10 + frameSize > end) break
            if (id in setOf("TIT3", "COMM", "TXXX") && frameSize in 2..MAX_TAG_BYTES) {
                val body = readAt(channel, pos + 10, frameSize.toInt())
                if (body != null) {
                    // A TXXX frame holds a description, a terminator and then its value; the other two are all text.
                    val text = if (id == "TXXX") userTextValue(body) else if (id == "COMM") commentText(body) else textFrame(body)
                    if (!text.isNullOrBlank()) {
                        val key = if (id == "TXXX") "TXXX:" + (descriptionField(body) ?: "") else id
                        wanted.putIfAbsent(key, text)
                    }
                }
            }
            pos += 10 + frameSize
        }
        return wanted
    }

    /** A text frame body: an encoding byte, then the text. */
    private fun textFrame(body: ByteBuffer): String? {
        if (body.limit() < 2) return null
        val charset = when (body.get(0).toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return body.text(1, body.limit() - 1, charset).trimEnd('\u0000').trim()
    }

    /** A `COMM` body: encoding, a 3-byte language, a short description, then the comment text. */
    private fun commentText(body: ByteBuffer): String? {
        if (body.limit() < 6) return null
        val charset = when (body.get(0).toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        var p = 4
        // The description is terminators in the frame's own encoding: one 0 byte, or two for UTF-16.
        while (p < body.limit()) {
            val zero = body.get(p).toInt() == 0
            if (zero && (charset != Charsets.UTF_16 || p + 1 >= body.limit() || body.get(p + 1).toInt() == 0)) {
                p += if (charset == Charsets.UTF_16) 2 else 1
                break
            }
            p++
        }
        if (p >= body.limit()) return null
        return body.text(p, body.limit() - p, charset).trimEnd('\u0000').trim()
    }

    /** The description field of a `TXXX` frame: encoding, description, 0, value. */
    private fun descriptionField(body: ByteBuffer): String? {
        if (body.limit() < 3) return null
        val charset = if (body.get(0).toInt() == 1) Charsets.UTF_16 else Charsets.UTF_8
        var p = 1
        val start = p
        while (p < body.limit() && body.get(p).toInt() != 0) p++
        if (p >= body.limit()) return null
        return body.text(start, p - start, charset).trim()
    }

    /** The value of a `TXXX` frame: whatever follows the description and its terminator. */
    private fun userTextValue(body: ByteBuffer): String? {
        if (body.limit() < 3) return null
        val charset = if (body.get(0).toInt() == 1) Charsets.UTF_16 else Charsets.UTF_8
        var p = 1
        while (p < body.limit() && body.get(p).toInt() != 0) p++
        // Skip the terminator: one byte, or two for UTF-16, where every other byte is zero anyway.
        p += if (charset == Charsets.UTF_16) 2 else 1
        if (p >= body.limit()) return null
        return body.text(p, body.limit() - p, charset).trimEnd('\u0000').trim()
    }

    // ---------------------------------------------------------------- MP4

    /** MP4 keeps a synopsis in a `©cmt` or `desc` tag of the movie atom: ISO-639 packed with the text. */
    private fun mp4(channel: SeekableByteChannel): String? {
        val boxes = boxes(channel, 0, channel.size()) ?: return null
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return null
        val kids = boxes(channel, moov.bodyStart, moov.end) ?: return null
        val udta = kids.firstOrNull { it.type == "udta" } ?: return null
        val atoms = boxes(channel, udta.bodyStart, udta.end) ?: return null
        val tags = atoms.filter { it.type in setOf("©cmt", "©des", "desc", "ldes") }
        for (tag in tags) {
            val raw = readAt(channel, tag.bodyStart, (tag.end - tag.bodyStart).toInt().coerceAtMost(MAX_TAG_BYTES.toInt())) ?: continue
            // A data atom: size, "data", a 4-byte type, a 4-byte locale, then the text.
            if (raw.limit() < 16 || raw.ascii(4, 4) != "data") continue
            val text = String(ByteArray(raw.limit() - 16).also { raw.position(16); raw.get(it) }, Charsets.UTF_8).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    // ---------------------------------------------------------------- Vorbis

    /**
     * The comment header of an Ogg Vorbis/Opus stream: the second packet of the first page. It is small and near the
     * start of the file; the audio pages that follow are never read.
     */
    private fun vorbis(channel: SeekableByteChannel, head: ByteBuffer): Map<String, String>? {
        // Only the first page is looked at, and never more than the file holds: a short file is not an error.
        val window = minOf(64L * 1024, channel.size()).toInt()
        val page = readAt(channel, 0, window) ?: return null
        // "OggS", 0, a header type, an 8-byte granule, serial, sequence, CRC, then the segment table.
        if (page.limit() < 27 || page.ascii(0, 4) != "OggS") return null
        val segments = page.get(26).toInt() and 0xFF
        var body = 27 + segments
        if (body >= page.limit()) return null
        // Find the comment packet: it starts with the magic of the codec's comment header.
        val region = ByteArray(page.limit() - body).also { page.position(body); page.get(it) }
        val magic = listOf("\u0003vorbis", "OpusTags").firstOrNull { m -> String(region, Charsets.ISO_8859_1).contains(m) } ?: return null
        val start = String(region, Charsets.ISO_8859_1).indexOf(magic) + magic.length
        if (magic == "\u0003vorbis") {
            // Vendor string, then the comment count, then the comments.
            if (start + 4 > region.size) return null
            val vendorLength = leInt(region, start)
            if (vendorLength < 0 || start + 4 + vendorLength + 4 > region.size) return null
            return vorbisComments(region, start + 4 + vendorLength)
        }
        return vorbisComments(region, start)
    }

    private fun vorbisComments(region: ByteArray, at: Int): Map<String, String>? {
        if (at + 4 > region.size) return null
        val count = leInt(region, at)
        if (count < 0 || count > 4096) return null
        var p = at + 4
        val fields = LinkedHashMap<String, String>()
        repeat(count) {
            if (p + 4 > region.size) return@repeat
            val length = leInt(region, p)
            p += 4
            if (length < 0 || p + length > region.size) return@repeat
            val line = String(region, p, length, Charsets.UTF_8)
            p += length
            val key = line.substringBefore('=', "").uppercase()
            val value = line.substringAfter('=', "").trim()
            if (key.isNotEmpty() && value.isNotEmpty()) fields.putIfAbsent(key, value)
        }
        return fields
    }

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

    // ---------------------------------------------------------------- bytes

    private class FileBox(val type: String, val bodyStart: Long, val end: Long)

    private fun boxes(channel: SeekableByteChannel, from: Long, to: Long): List<FileBox>? {
        val out = mutableListOf<FileBox>()
        var pos = from
        while (pos + 8 <= to && out.size < 4096) {
            val h = readAt(channel, pos, minOf(16L, to - pos).toInt()) ?: break
            var size = h.getInt(0).toLong() and 0xFFFFFFFFL
            var header = 8
            if (size == 1L) {
                if (h.limit() < 16) break
                size = h.getLong(8)
                header = 16
            }
            if (size == 0L) size = to - pos
            if (size < header || pos + size > to) break
            out += FileBox(h.ascii(4, 4), pos + header, pos + size)
            pos += size
        }
        return out
    }

    private fun readAt(channel: SeekableByteChannel, position: Long, length: Int): ByteBuffer? {
        if (length < 0 || position < 0 || position + length > channel.size()) return null
        val buffer = ByteBuffer.allocate(length)
        channel.position(position)
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) return null
        buffer.flip()
        return buffer
    }

    private fun syncsafe(b: ByteBuffer, at: Int): Int = (0 until 4).fold(0) { acc, i -> (acc shl 7) or (b.get(at + i).toInt() and 0x7F) }

    private fun ByteBuffer.ascii(at: Int, length: Int): String = text(at, length, Charsets.ISO_8859_1)

    private fun ByteBuffer.text(at: Int, length: Int, charset: Charset): String {
        val bytes = ByteArray(length.coerceIn(0, limit() - at))
        for (i in bytes.indices) bytes[i] = get(at + i)
        return String(bytes, charset)
    }
}
