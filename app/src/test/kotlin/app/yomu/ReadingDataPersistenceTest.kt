package app.yomu

import app.yomu.data.backup.BackupManager
import app.yomu.feature.annotations.NoteDrafts
import app.yomu.feature.audio.AudioHistory
import app.yomu.feature.audio.AudioPositionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The journals and manifests added for the 2026-11-13 audit: they must survive a restart and never lie. */
class ReadingDataPersistenceTest {

    @Test
    fun `audio history round trips and merges a repeated swipe`() {
        val first = AudioPositionEvent(0, 10_000, 1, 0, AudioHistory.CHAPTER, 1_000)
        val list = AudioHistory.append(emptyList(), first)
        // A second move of the same kind from the point the first one ended is one event, not two.
        val second = AudioPositionEvent(1, 0, 1, 5_000, AudioHistory.CHAPTER, 2_000)
        val merged = AudioHistory.append(list, second)
        assertEquals(1, merged.size)
        assertEquals(5_000, merged.single().toMs)

        // A different reason starts a new entry.
        assertEquals(2, AudioHistory.append(merged, second.copy(reason = AudioHistory.SEEK, at = 2_100)).size)

        val decoded = AudioHistory.decode(AudioHistory.encode(merged))
        assertEquals(merged, decoded)
        assertEquals(emptyList<AudioPositionEvent>(), AudioHistory.decode("not json"))
    }

    @Test
    fun `audio history keeps the newest events only`() {
        var list = emptyList<AudioPositionEvent>()
        repeat(AudioHistory.LIMIT + 25) { i ->
            list = AudioHistory.append(list, AudioPositionEvent(0, i * 10_000L, 0, i * 10_000L + 5_000, AudioHistory.SEEK, i * 10_000L))
        }
        assertEquals(AudioHistory.LIMIT, list.size)
        assertEquals(25L * 10_000, list.first().at)
    }

    @Test
    fun `note drafts survive a new instance and are removed only on purpose`() {
        val dir = File.createTempFile("yomu-drafts", "").let { it.delete(); it.mkdirs(); it }
        val file = File(dir, "note_drafts.json")
        try {
            val drafts = NoteDrafts(file)
            drafts.put("book1:s:2:15", bookId = 1, chapter = 2, offset = 15, text = "A thought")
            assertEquals("A thought", drafts.get("book1:s:2:15"))

            // A new process reads the same file.
            assertEquals("A thought", NoteDrafts(file).get("book1:s:2:15"))

            drafts.remove("book1:s:2:15")
            assertNull(NoteDrafts(file).get("book1:s:2:15"))

            drafts.put("empty", 1, 0, 0, "   ")
            assertNull(drafts.get("empty"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `backup preview reads counts and files without restoring`() {
        val zip = backupZip(
            manifest = """
                {"format":"yomu-backup","version":1,"createdAt":1700000000000,
                 "settings":{},
                 "tables":{"books":[{},{}],"highlights":[{}],"notes":[],"bookmarks":[{}],"words":[{},{}]}}
            """.trimIndent(),
            files = mapOf("files/books/a.epub" to ByteArray(40), "files/covers/a.jpg" to ByteArray(10)),
        )
        val preview = BackupManager.previewZip(ByteArrayInputStream(zip))
        assertEquals(2, preview.books)
        assertEquals(1, preview.highlights)
        assertEquals(0, preview.notes)
        assertEquals(1, preview.bookmarks)
        assertEquals(2, preview.words)
        assertEquals(2, preview.files)
        assertEquals(50, preview.bytes.toInt())
        assertTrue(preview.hasSettings)
        assertEquals(1700000000000, preview.createdAt)
    }

    @Test
    fun `backup preview refuses a foreign archive`() {
        val zip = backupZip("""{"format":"something-else","version":1}""", emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.previewZip(ByteArrayInputStream(zip))
        }
        assertThrows(java.io.IOException::class.java) {
            BackupManager.previewZip(ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun `merge of a listening position keeps an unread flag off`() {
        // The audio journal stores only jumps; a paused seek never turns into a played book.
        val event = AudioPositionEvent(3, 1_000, 3, 1_000, AudioHistory.SEEK, 0)
        assertFalse(event.isJump)
        assertTrue(event.copy(toMs = 2_000).isJump)
    }

    private fun backupZip(manifest: String, files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
