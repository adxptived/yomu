package app.yomu.data.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * A description reaches the library only when the file really carries one: the platform retriever has no universal
 * description, so these fixtures are the only proof that a tag is read — and that an empty one stays empty.
 */
class AudioTagsTest {
    @TempDir
    lateinit var dir: File

    /** The parser seeks, so the bytes go through a real file rather than an in-memory stream. */
    private fun channel(bytes: ByteArray, name: String = "tag"): SeekableByteChannel {
        val file = File(dir, name)
        file.writeBytes(bytes)
        return Files.newByteChannel(file.toPath(), StandardOpenOption.READ)
    }

    /** Builds an ID3v2.4 tag with the given frames, each as id -> raw body. */
    private fun id3Tag(frames: List<Pair<String, ByteArray>>): SeekableByteChannel {
        val body = ByteArrayOutputStream()
        frames.forEach { (id, frameBody) ->
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(syncsafe(frameBody.size))
            body.write(byteArrayOf(0, 0))
            body.write(frameBody)
        }
        val bytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.ISO_8859_1))
        out.write(byteArrayOf(4, 0, 0))
        out.write(syncsafe(bytes.size))
        out.write(bytes)
        return channel(out.toByteArray(), "id3-${System.nanoTime()}")
    }

    private fun syncsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    /** A UTF-8 text frame body: encoding byte 3, then the text. */
    private fun utf8Text(text: String): ByteArray = byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)

    /** A COMM body: encoding 3, language, an empty description and its terminator, then the comment. */
    private fun utf8Comment(text: String): ByteArray = byteArrayOf(3) + "eng".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)

    /** A TXXX body: encoding 3, the description, a terminator, then the value. */
    private fun utf8UserText(description: String, text: String): ByteArray =
        byteArrayOf(3) + description.toByteArray(Charsets.UTF_8) + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)

    @Test
    fun `a subtitle frame becomes the description`() {
        val channel = id3Tag(listOf("TIT3" to utf8Text("A synopsis of the book.")))
        assertEquals("A synopsis of the book.", AudioTags.description(channel))
    }

    @Test
    fun `a comment frame is used when there is no subtitle`() {
        val channel = id3Tag(listOf("COMM" to utf8Comment("Listened twice already.")))
        assertEquals("Listened twice already.", AudioTags.description(channel))
    }

    @Test
    fun `a user text frame named description is understood`() {
        val channel = id3Tag(listOf("TXXX" to utf8UserText("DESCRIPTION", "From a user text frame.")))
        assertEquals("From a user text frame.", AudioTags.description(channel))
    }

    @Test
    fun `a subtitle wins over a comment`() {
        val channel = id3Tag(
            listOf(
                "COMM" to utf8Comment("A comment."),
                "TIT3" to utf8Text("The subtitle."),
            ),
        )
        assertEquals("The subtitle.", AudioTags.description(channel))
    }

    @Test
    fun `a file without description tags has no description`() {
        val channel = id3Tag(listOf("TIT2" to utf8Text("Just a title"), "TPE1" to utf8Text("An author")))
        assertNull(AudioTags.description(channel))
    }

    @Test
    fun `an empty tag is not a description`() {
        val channel = id3Tag(listOf("TIT3" to utf8Text("   ")))
        assertNull(AudioTags.description(channel))
    }

    @Test
    fun `a file that is not audio has no description`() {
        val channel = channel("not a media file at all".toByteArray())
        assertNull(AudioTags.description(channel))
    }

    @Test
    fun `plain text is tidied but its paragraphs are kept`() {
        assertEquals("First line.\n\nSecond line.", AudioTags.clean("  First line.\r\n\r\nSecond line.  "))
        assertEquals("One line.", AudioTags.clean("One   line.\n\n\n\nAgain".substringBefore("Again")))
    }

    @Test
    fun `html in a tag becomes readable text`() {
        assertEquals(
            "A book about the sea.\n\nBy a sailor.",
            AudioTags.clean("<p>A book about the&nbsp;sea.<br/></p><p>By a sailor.</p>"),
        )
    }

    @Test
    fun `an html-only empty value is not a description`() {
        assertNull(AudioTags.clean("<p></p><br/>"))
        assertNull(AudioTags.clean("<div> </div>"))
    }

    @Test
    fun `a very long description is cut to the stored limit`() {
        val cleaned = AudioTags.clean("x".repeat(AudioTags.MAX_DESCRIPTION_CHARS + 500))
        assertTrue(cleaned != null)
        assertEquals(AudioTags.MAX_DESCRIPTION_CHARS, cleaned!!.length)
    }

    @Test
    fun `vorbis comments are read from a second packet`() {
        // A minimal Ogg page: the comment header of Vorbis starts with its own magic.
        val comments = ByteArrayOutputStream()
        val vendor = "test".toByteArray()
        comments.write(leInt(vendor.size))
        comments.write(vendor)
        comments.write(leInt(1))
        val field = "DESCRIPTION=A story told aloud.".toByteArray()
        comments.write(leInt(field.size))
        comments.write(field)
        val payload = "\u0003vorbis".toByteArray(Charsets.ISO_8859_1) + comments.toByteArray()

        val page = ByteArrayOutputStream()
        page.write("OggS".toByteArray(Charsets.ISO_8859_1))
        // Version, header type, an 8-byte granule position, serial, sequence and CRC: 22 more bytes up to byte 26.
        page.write(ByteArray(22))
        page.write(byteArrayOf(1)) // one segment in the table
        page.write(byteArrayOf(payload.size.toByte())) // the lacing value of that segment
        page.write(payload)
        assertEquals("A story told aloud.", AudioTags.description(channel(page.toByteArray(), "vorbis")))
    }

    private fun leInt(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(), ((value shr 16) and 0xFF).toByte(), ((value shr 24) and 0xFF).toByte())
}
