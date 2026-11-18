package app.yomu

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.ChapterEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.widget.NewChaptersWidget
import app.yomu.widget.NewChaptersWidgets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** U03: the new-chapters widget is a local projection, hides in incognito and has a real empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class NewChaptersWidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db: YomuDatabase get() = GlobalContext.get().get()
    private val settings: AppSettings get() = GlobalContext.get().get()

    /**
     * The widget object and the test must read the same settings store. A previous test class can leave its own
     * Koin/Application pair behind; starting one fresh container for this class keeps the incognito write visible to
     * the widget.
     */
    @Before
    fun setUp() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        startKoin {
            androidContext(context)
            modules(yomuModules)
        }
    }

    @After
    fun tearDown() {
        NewChaptersWidgets.stop()
        stopKoin()
    }

    @Test
    fun showsNewUnreadChaptersAndHidesInIncognito() = runBlocking {
        val now = System.currentTimeMillis()
        val older = seedTitle("Older Title", now)
        val newer = seedTitle("Newer Title", now)
        db.catalog().insertChapters(
            listOf(
                chapter(older, "Chapter 1", now - 60_000, isNew = true),
                chapter(older, "Already read", now - 50_000, isNew = true, read = true),
                chapter(newer, "Chapter 7", now - 10_000, isNew = true),
                chapter(newer, "Chapter 8", now - 9_000, isNew = false),
            ),
        )
        val manager = AppWidgetManager.getInstance(context)
        val widget = shadowOf(manager).createWidget(NewChaptersWidget::class.java, R.layout.widget_new_chapters)
        NewChaptersWidgets.refresh(context)
        shadowOf(Looper.getMainLooper()).idle()
        val view = shadowOf(manager).getViewFor(widget)
        fun text(id: Int) = view.findViewById<TextView>(id).text.toString()

        // Only new, unread chapters of favourites: the read one and the old one are not shown.
        assertEquals("Newer Title", text(R.id.nc_title_0))
        assertEquals("Chapter 7", text(R.id.nc_subtitle_0))
        assertEquals("Older Title", text(R.id.nc_title_1))
        assertEquals("Chapter 1", text(R.id.nc_subtitle_1))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.nc_row_1).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.nc_row_2).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.nc_empty).visibility)

        // Incognito hides the projection entirely.
        settings.incognito.set(true)
        NewChaptersWidgets.refresh(context)
        shadowOf(Looper.getMainLooper()).idle()
        val hidden = shadowOf(manager).getViewFor(widget)
        assertEquals(View.VISIBLE, hidden.findViewById<View>(R.id.nc_empty).visibility)
        assertEquals(View.GONE, hidden.findViewById<View>(R.id.nc_row_0).visibility)

        // Back in normal mode, reading the chapters empties the widget instead of leaving a spinner.
        settings.incognito.set(false)
        db.catalog().clearNew(older)
        db.catalog().clearNew(newer)
        NewChaptersWidgets.refresh(context)
        shadowOf(Looper.getMainLooper()).idle()
        val empty = shadowOf(manager).getViewFor(widget)
        assertEquals(View.VISIBLE, empty.findViewById<View>(R.id.nc_empty).visibility)
        assertEquals(View.GONE, empty.findViewById<View>(R.id.nc_row_0).visibility)
    }

    private suspend fun seedTitle(title: String, now: Long): Long = db.catalog().insert(
        CatalogItemEntity(
            sourceId = 42, url = "/title/$title", title = title, author = null, artist = null, description = null,
            genres = "", status = 1, coverUrl = null, type = "MANGA", favorite = true, categoryId = null,
            addedAt = now, lastUpdateAt = now, initialized = true,
        ),
    )

    private fun chapter(entryId: Long, name: String, at: Long, isNew: Boolean, read: Boolean = false) = ChapterEntity(
        itemId = entryId, url = "/chapter/$entryId/$name", name = name, number = 1f, scanlator = null,
        uploadDate = at, read = read, bookmarked = false, lastPageRead = 0, downloaded = false, localPath = null,
        isNew = isNew, sourceOrder = 0, fetchedAt = at, lastReadAt = null,
    )
}
