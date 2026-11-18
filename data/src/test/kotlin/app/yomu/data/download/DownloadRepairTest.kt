package app.yomu.data.download

import android.content.Context
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.ChapterEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.core.network.NetworkClients
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.library.ImportService
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** DL03: the download index is repaired only from valid downloads of the chapter's own title. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRepairTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: YomuDatabase
    private lateinit var downloads: DownloadRepository

    @Before
    fun setUp() = runBlocking {
        db = YomuDatabase.build(context, inMemory = true)
        val settings = AppSettings(context).apply { resetAll() }
        downloads = DownloadRepository(context, db, settings, NetworkClients(context), mockk<CatalogRepository>(relaxed = true), mockk<ImportService>(relaxed = true))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun restoresMissingFlagsAndReportsOrphansWithoutDeletingThem() = runBlocking {
        val itemId = entry("Title")
        val chapterId = chapter(itemId, "Chapter 1")

        val folder = chapterFolder("Title").apply { mkdirs() }
        val archive = validCbz(File(folder, "Chapter 1 [$chapterId].cbz"))
        val orphan = File(folder, "Removed title [999999].cbz").apply { writeText("gone") }

        // The file is on disk but the row says "not downloaded": the scan re-attaches it.
        val restored = downloads.repairIndex()
        assertEquals(1, restored.restored)
        assertEquals(1, restored.orphans)
        val chapter = db.catalog().chapter(chapterId)!!
        assertTrue(chapter.downloaded)
        assertEquals(archive.path, chapter.localPath)
        assertTrue("an orphan must never be deleted by a scan", orphan.exists())

        // A deleted file is marked as missing, not left with a stale path.
        archive.delete()
        val missing = downloads.repairIndex()
        assertEquals(1, missing.missing)
        val after = db.catalog().chapter(chapterId)!!
        assertFalse(after.downloaded)
        assertNull(after.localPath)
        assertTrue(orphan.exists())
    }

    @Test
    fun restoresAnImageFolderAndRejectsAnEmptyOne() = runBlocking {
        val itemId = entry("Title")
        val good = chapter(itemId, "Chapter 1")
        val empty = chapter(itemId, "Chapter 2")
        val onlyNoMedia = chapter(itemId, "Chapter 3")

        imageFolder("Title", "Chapter 1 [$good]")
        File(chapterFolder("Title"), "Chapter 2 [$empty]").mkdirs()
        File(chapterFolder("Title"), "Chapter 3 [$onlyNoMedia]").apply { mkdirs(); File(this, ".nomedia").writeText("") }

        val report = downloads.repairIndex()

        assertEquals(1, report.restored)
        assertEquals(2, report.orphans)
        assertTrue(db.catalog().chapter(good)!!.downloaded)
        assertFalse(db.catalog().chapter(empty)!!.downloaded)
        assertFalse(db.catalog().chapter(onlyNoMedia)!!.downloaded)
    }

    @Test
    fun rejectsATruncatedArchiveAnotherFileTypeAndAnArchiveWithoutImages() = runBlocking {
        val itemId = entry("Title")
        val truncated = chapter(itemId, "Chapter 1")
        val wrongType = chapter(itemId, "Chapter 2")
        val noImages = chapter(itemId, "Chapter 3")
        val folder = chapterFolder("Title").apply { mkdirs() }

        File(folder, "Chapter 1 [$truncated].cbz").writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        File(folder, "Chapter 2 [$wrongType].txt").writeText("not a chapter")
        zip(File(folder, "Chapter 3 [$noImages].cbz")) {
            putNextEntry(ZipEntry("ComicInfo.xml"))
            write("<ComicInfo/>".toByteArray())
        }

        val report = downloads.repairIndex()

        assertEquals(0, report.restored)
        assertEquals(3, report.orphans)
        listOf(truncated, wrongType, noImages).forEach { assertFalse(db.catalog().chapter(it)!!.downloaded) }
    }

    @Test
    fun rejectsANameWithSeveralChapterIds() = runBlocking {
        val itemId = entry("Title")
        val id = chapter(itemId, "Chapter 1")
        val folder = chapterFolder("Title").apply { mkdirs() }

        validCbz(File(folder, "Chapter 1 [$id] [123456].cbz"))

        val report = downloads.repairIndex()

        assertEquals(0, report.restored)
        assertEquals(1, report.orphans)
        assertFalse(db.catalog().chapter(id)!!.downloaded)
    }

    @Test
    fun doesNotAttachAFileFromAnotherTitlesFolder() = runBlocking {
        val alpha = entry("Alpha")
        val beta = entry("Beta")
        val alphaChapter = chapter(alpha, "Chapter 1")
        chapter(beta, "Chapter 1")
        val betaFolder = chapterFolder("Beta").apply { mkdirs() }

        validCbz(File(betaFolder, "Chapter 1 [$alphaChapter].cbz"))

        val report = downloads.repairIndex()

        assertEquals(0, report.restored)
        assertEquals(1, report.orphans)
        assertFalse(db.catalog().chapter(alphaChapter)!!.downloaded)
    }

    @Test
    fun repairsThePathOfAChapterThatWasAlreadyMarkedDownloaded() = runBlocking {
        val itemId = entry("Title")
        val id = chapter(itemId, "Chapter 1")
        db.catalog().setDownloaded(id, true, "/missing/Chapter 1 [$id].cbz")
        val folder = chapterFolder("Title").apply { mkdirs() }
        val archive = validCbz(File(folder, "Chapter 1 [$id].cbz"))

        val report = downloads.repairIndex()

        assertEquals(1, report.restored)
        val chapter = db.catalog().chapter(id)!!
        assertTrue(chapter.downloaded)
        assertEquals(archive.path, chapter.localPath)
    }

    @Test
    fun restoresANovelHtmlDownloadButNotArbitraryHtmlElsewhere() = runBlocking {
        val novel = entry("Novel", type = "NOVEL")
        val id = chapter(novel, "Chapter 1")
        val folder = chapterFolder("Novel").apply { mkdirs() }
        File(folder, "Chapter 1 [$id].html").writeText("<!DOCTYPE html><html><body>Text</body></html>")

        val report = downloads.repairIndex()

        assertEquals(1, report.restored)
        assertTrue(db.catalog().chapter(id)!!.downloaded)
    }

    private suspend fun entry(title: String, type: String = "MANGA"): Long {
        val now = System.currentTimeMillis()
        return db.catalog().insert(
            CatalogItemEntity(
                sourceId = 1, url = "/$title", title = title, author = null, artist = null, description = null,
                genres = "", status = 0, coverUrl = null, type = type, favorite = true, categoryId = null,
                addedAt = now, lastUpdateAt = now, initialized = true,
            ),
        )
    }

    private suspend fun chapter(itemId: Long, name: String, downloaded: Boolean = false, localPath: String? = null): Long {
        val now = System.currentTimeMillis()
        return db.catalog().insertChapters(
            listOf(
                ChapterEntity(
                    itemId = itemId, url = "/$name", name = name, number = 1f, scanlator = null,
                    uploadDate = 0, read = false, bookmarked = false, lastPageRead = 0, downloaded = downloaded,
                    localPath = localPath, isNew = false, sourceOrder = 0, fetchedAt = now, lastReadAt = null,
                ),
            ),
        ).single()
    }

    private fun chapterFolder(title: String): File = File(context.filesDir, "downloads/chapters/1/$title")

    private fun imageFolder(title: String, chapter: String): File {
        val dir = File(chapterFolder(title), chapter).apply { mkdirs() }
        File(dir, "000.jpg").writeBytes(jpeg)
        return dir
    }

    private fun validCbz(file: File): File {
        file.parentFile?.mkdirs()
        zip(file) {
            putNextEntry(ZipEntry("000.jpg"))
            write(jpeg)
            putNextEntry(ZipEntry("ComicInfo.xml"))
            write("<ComicInfo/>".toByteArray())
        }
        return file
    }

    private fun zip(file: File, block: ZipOutputStream.() -> Unit) {
        ZipOutputStream(file.outputStream().buffered()).use { zip -> zip.block() }
    }

    private companion object {
        /** First bytes of a JPEG, enough for the image sniffer. */
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46)
    }
}
