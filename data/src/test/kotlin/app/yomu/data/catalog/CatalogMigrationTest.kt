package app.yomu.data.catalog

import android.content.Context
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.ChapterEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.data.source.SourceManager
import app.yomu.source.CatalogSource
import app.yomu.source.SChapter
import app.yomu.source.SItem
import app.yomu.source.SourceContent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** MG01: preview, apply, idempotency, stale plans and undo against a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogMigrationTest {
    private lateinit var context: Context
    private lateinit var db: YomuDatabase
    private lateinit var source: CatalogSource
    private lateinit var catalog: CatalogRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = YomuDatabase.build(context, inMemory = true)
        source = mockk(relaxed = true) {
            every { id } returns 2L
            every { name } returns "Target"
            every { lang } returns "en"
            every { content } returns SourceContent.MANGA
            every { supportsLatest } returns false
            every { imageHeaders() } returns emptyMap()
        }
        val sources = mockk<SourceManager>()
        every { sources.get(2L) } returns source
        catalog = CatalogRepository(context, db, sources, AppSettings(context), { OkHttpClient() })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun oldEntry(url: String = "old/title"): Long {
        val id = db.catalog().insert(
            CatalogItemEntity(
                sourceId = 1,
                url = url,
                title = "Title",
                author = null,
                artist = null,
                description = null,
                genres = "",
                status = 0,
                coverUrl = null,
                type = "MANGA",
                favorite = true,
                categoryId = null,
                addedAt = 100,
                lastUpdateAt = 200,
                initialized = true,
            ),
        )
        db.catalog().insertChapters(
            listOf(
                chapter(id, url = "old/1", name = "Chapter 1", number = 1f, read = true, bookmarked = false, page = 5, downloaded = true),
                chapter(id, url = "old/2", name = "Chapter 2", number = 2f, read = false, bookmarked = true, page = 0),
            ),
        )
        return id
    }

    private fun chapter(
        itemId: Long,
        url: String,
        name: String,
        number: Float,
        read: Boolean = false,
        bookmarked: Boolean = false,
        page: Int = 0,
        downloaded: Boolean = false,
    ) = ChapterEntity(
        itemId = itemId, url = url, name = name, number = number, scanlator = "A", uploadDate = 1,
        read = read, bookmarked = bookmarked, lastPageRead = page, downloaded = downloaded,
        localPath = if (downloaded) "/downloads/${url.substringAfter('/')}" else null,
        isNew = false, sourceOrder = number.toInt(), fetchedAt = 1, lastReadAt = null,
    )

    private fun stubTarget() {
        coEvery { source.getDetailsAndChapters(any()) } returns (
            SItem(url = "target/title", title = "Title", type = SourceContent.MANGA, initialized = true) to
                listOf(
                    SChapter("new/1", "Chapter 1", 1f, scanlator = "A"),
                    SChapter("new/2", "Chapter 2", 2f, scanlator = "A"),
                )
            )
    }

    private fun targetItem() = SItem(url = "target/title", title = "Title", type = SourceContent.MANGA)

    @Test
    fun `a plan writes nothing and apply carries progress, bookmarks and the favourite`() = runBlocking(Dispatchers.IO) {
        stubTarget()
        val fromId = oldEntry()
        val plan = catalog.planMigration(fromId, 2, targetItem())
        assertEquals(2, plan.matches.size)
        assertEquals(2, plan.exact.size)
        assertTrue(plan.matched.all { !it.pagePositionSafe })
        // Preview only: the target does not exist and the old title is still a favourite.
        assertTrue(db.catalog().get(fromId)!!.favorite)
        assertTrue(db.catalog().find(2, "target/title") == null)

        val result = catalog.applyMigration(plan, setOf(MigrationCategory.PROGRESS, MigrationCategory.BOOKMARKS, MigrationCategory.FAVORITE))
        assertFalse(result.alreadyApplied)
        assertEquals(2, result.applied)
        val target = db.catalog().get(result.targetEntryId)!!
        assertTrue(target.favorite)
        assertFalse(db.catalog().get(fromId)!!.favorite)
        val chapters = db.catalog().chapters(result.targetEntryId).sortedBy { it.number }
        assertTrue(chapters[0].read)
        // Different releases: the read mark is kept, the page is not guessed.
        assertEquals(0, chapters[0].lastPageRead)
        assertTrue(chapters[1].bookmarked)
        // The old title and its downloaded chapter stay available.
        val old = db.catalog().chapters(fromId).first { it.number == 1f }
        assertTrue(old.downloaded)
        assertEquals("/downloads/1", old.localPath)
    }

    @Test
    fun `applying the same plan twice is a no-op`() = runBlocking(Dispatchers.IO) {
        stubTarget()
        val plan = catalog.planMigration(oldEntry(), 2, targetItem())
        val first = catalog.applyMigration(plan)
        val targetChapter = db.catalog().chapters(first.targetEntryId).first { it.number == 1f }
        db.catalog().saveProgress(targetChapter.id, 9, System.currentTimeMillis(), false)

        val second = catalog.applyMigration(plan)
        assertTrue(second.alreadyApplied)
        assertEquals(9, db.catalog().chapter(targetChapter.id)!!.lastPageRead)
    }

    @Test(expected = StaleMigrationPlanException::class)
    fun `a plan whose source changed is refused`() = runBlocking(Dispatchers.IO) {
        stubTarget()
        val fromId = oldEntry()
        val plan = catalog.planMigration(fromId, 2, targetItem())
        db.catalog().insertChapters(listOf(chapter(fromId, url = "old/3", name = "Chapter 3", number = 3f)))
        catalog.applyMigration(plan)
        Unit
    }

    @Test
    fun `undo restores the values an apply wrote`() = runBlocking(Dispatchers.IO) {
        stubTarget()
        val fromId = oldEntry()
        val plan = catalog.planMigration(fromId, 2, targetItem())
        val result = catalog.applyMigration(plan)
        assertTrue(result.applied > 0)

        assertTrue(catalog.undoMigration(plan.planId))
        assertTrue(db.catalog().get(fromId)!!.favorite)
        assertFalse(db.catalog().get(result.targetEntryId)!!.favorite)
        val targetChapters = db.catalog().chapters(result.targetEntryId)
        assertFalse(targetChapters.first { it.number == 1f }.read)
        assertFalse(targetChapters.first { it.number == 2f }.bookmarked)
        // A second undo has nothing to restore.
        assertFalse(catalog.undoMigration(plan.planId))
    }

    @Test
    fun `a batch reports each title separately`() = runBlocking(Dispatchers.IO) {
        stubTarget()
        val fromId = oldEntry()
        val secondFrom = oldEntry("old/title-2")
        coEvery { source.getDetailsAndChapters(match { it.url == "broken" }) } throws IllegalStateException("source failed")
        val plans = catalog.planMigrations(2, listOf(fromId to targetItem(), secondFrom to SItem(url = "broken", title = "Broken")))
        assertEquals(2, plans.size)
        assertTrue(plans[0].isSuccess)
        assertTrue(plans[1].isFailure)
        val applied = catalog.applyMigrations(listOf(plans[0].getOrThrow()))
        assertEquals(1, applied.size)
        assertTrue(applied[0].isSuccess)
    }
}
