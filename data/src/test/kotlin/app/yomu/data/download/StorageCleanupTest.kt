package app.yomu.data.download

import app.yomu.domain.model.ChapterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageCleanupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun clearsAFolderAndCountsEveryFile() {
        val dir = tmp.newFolder("converted")
        File(dir, "one.txt").writeText("one")
        File(dir, "nested").mkdirs()
        File(dir, "nested/two.txt").writeText("two")

        assertEquals(2, StorageCleanup.clearDirectory(dir))
        assertFalse(dir.exists())
    }

    @Test
    fun aMissingFolderClearsNothing() {
        assertEquals(0, StorageCleanup.clearDirectory(File(tmp.root, "missing")))
    }

    @Test
    fun onlyDownloadedChaptersAreSelected() {
        val chapters = listOf(
            ChapterInfo(id = 1, entryId = 5, url = "/1", name = "1", downloaded = true),
            ChapterInfo(id = 2, entryId = 5, url = "/2", name = "2", downloaded = false),
            ChapterInfo(id = 3, entryId = 5, url = "/3", name = "3", downloaded = true),
        )

        assertEquals(listOf(1L, 3L), StorageCleanup.downloadedChapterIds(chapters))
    }
}
