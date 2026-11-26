package app.yomu.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ChapterCbzExportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pagesArePackedInNameOrderWithComicInfo() {
        val dir = tmp.newFolder()
        val page10 = File(dir, "010.png").apply { writeBytes(byteArrayOf(10)) }
        val page2 = File(dir, "002.png").apply { writeBytes(byteArrayOf(2)) }
        val page1 = File(dir, "001.png").apply { writeBytes(byteArrayOf(1)) }
        val out = ByteArrayOutputStream()

        val count = ChapterCbzExport.write(listOf(page10, page2, page1), out, "<ComicInfo/>")

        assertEquals(3, count)
        val entries = readEntries(out.toByteArray())
        assertEquals(listOf("001.png", "002.png", "010.png", "ComicInfo.xml"), entries.map { it.first })
        assertArrayEquals(byteArrayOf(1), entries[0].second)
        assertEquals("<ComicInfo/>", String(entries[3].second))
    }

    @Test
    fun suggestedNameIsSafe() {
        assertEquals("Chapter 5.cbz", ChapterCbzExport.suggestedName("Chapter 5"))
        val unsafe = ChapterCbzExport.suggestedName("a/b:c")
        assertTrue(unsafe.endsWith(".cbz"))
        assertFalse(unsafe.contains('/'))
        assertFalse(unsafe.contains(':'))
        assertEquals("chapter.cbz", ChapterCbzExport.suggestedName("   "))
    }

    private fun readEntries(bytes: ByteArray): List<Pair<String, ByteArray>> = buildList {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                add(entry.name to zip.readBytes())
                zip.closeEntry()
            }
        }
    }
}
