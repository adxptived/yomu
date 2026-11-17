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

/** DL03: the download index is repaired from the stable `[chapterId]` names, and orphans are reported, never deleted. */
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
        val now = System.currentTimeMillis()
        val itemId = db.catalog().insert(
            CatalogItemEntity(
                sourceId = 1, url = "/title", title = "Title", author = null, artist = null, description = null,
                genres = "", status = 0, coverUrl = null, type = "MANGA", favorite = true, categoryId = null,
                addedAt = now, lastUpdateAt = now, initialized = true,
            ),
        )
        val chapterId = db.catalog().insertChapters(
            listOf(
                ChapterEntity(
                    itemId = itemId, url = "/chapter/1", name = "Chapter 1", number = 1f, scanlator = null,
                    uploadDate = 0, read = false, bookmarked = false, lastPageRead = 0, downloaded = false,
                    localPath = null, isNew = false, sourceOrder = 0, fetchedAt = now, lastReadAt = null,
                ),
            ),
        ).single()

        val folder = File(context.filesDir, "downloads/chapters/1/Title").apply { mkdirs() }
        val archive = File(folder, "Chapter 1 [$chapterId].cbz").apply { writeText("pages") }
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
}
