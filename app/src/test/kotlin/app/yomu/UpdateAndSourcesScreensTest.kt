package app.yomu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.database.BookEntity
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.ChapterEntity
import app.yomu.core.database.ReadingSessionEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.components.LocalReduceMotion
import app.yomu.core.ui.components.pressPulse
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.feature.catalog.CatalogHomeViewModel
import app.yomu.feature.catalog.CatalogScreen
import app.yomu.feature.settings.AppUpdates
import app.yomu.feature.settings.CatalogUpdateBanner
import app.yomu.feature.settings.ReleaseInfo
import app.yomu.feature.settings.SettingsLinks
import app.yomu.feature.settings.SettingsScreen
import app.yomu.feature.settings.SettingsSection
import app.yomu.feature.settings.SettingsViewModel
import app.yomu.feature.settings.UpdateState
import app.yomu.feature.audio.AudioWidgetContract
import app.yomu.feature.audio.AudioWidgetState
import app.yomu.widget.AudioWidgetProvider
import app.yomu.widget.ContinueReadingWidget
import app.yomu.widget.ContinueWidgets
import app.yomu.widget.NewChaptersWidget
import app.yomu.widget.NewChaptersWidgets
import app.yomu.widget.ShelfWidget
import app.yomu.widget.ShelfWidgets
import app.yomu.widget.StreakWidget
import app.yomu.widget.StreakWidgets
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Screenshots for the November 2026 UI work: the update banner and dialog (A1), the source grid (B1),
 * the settings root and appearance previews (B2), a pressed tile (B3) and the improved widgets (B4).
 * Images go to `app/build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class UpdateAndSourcesScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings: AppSettings get() = GlobalContext.get().get()
    private val db: YomuDatabase get() = GlobalContext.get().get()

    /** Update objects of this test: their scopes are stopped before Koin goes away, so no late write outlives it. */
    private val createdUpdates = mutableListOf<AppUpdates>()

    private fun track(updates: AppUpdates): AppUpdates = updates.also { createdUpdates += it }

    /** RU would otherwise default to Russian sources only; every screenshot shows all languages. */
    @Before
    fun prepare() {
        runBlocking {
            settings.catalogLanguages.set(setOf(app.yomu.data.source.SourceManager.ALL_LANGUAGES))
            // Other test classes share the settings file of the JVM: start from a quiet, unskipped state.
            settings.incognito.set(false)
            settings.skipUpdateVersion.set("")
            settings.updateDismissedVersion.set("")
            settings.updateNextAttemptAt.set(0)
        }
        ContinueWidgets.stop()
        NewChaptersWidgets.stop()
    }

    @After
    fun tearDown() {
        createdUpdates.forEach { it.shutdown() }
        createdUpdates.clear()
        ContinueWidgets.stop()
        NewChaptersWidgets.stop()
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    /** Any composed text at all: enough for a screen whose labels are all localized. */
    @OptIn(ExperimentalTestApi::class)
    private fun awaitAnyText() = compose.waitUntil(30_000) {
        compose.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().size > 2
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shotScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun catalog(updates: AppUpdates? = null) = compose.setContent {
        YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
            CatalogScreen(
                viewModel = koinViewModel<CatalogHomeViewModel>(),
                onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                updateBanner = updates?.let { u -> { CatalogUpdateBanner(u) } },
            )
        }
    }

    private fun settingsModel(updates: AppUpdates): SettingsViewModel {
        val koin = GlobalContext.get()
        return SettingsViewModel(
            context, settings, koin.get(), koin.get(), koin.get(), koin.get(), updates,
            koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(),
        )
    }

    /** The RU device would otherwise default to Russian sources only; the screenshots want every language. */
    private fun release() = ReleaseInfo(
        version = "9.9.0",
        pageUrl = "https://github.com/adxptived/yomu/releases/tag/v9.9.0",
        apkUrl = "https://github.com/adxptived/yomu/releases/download/v9.9.0/yomu-v9.9.0-arm64-v8a.apk",
        apkSize = 41_500_000,
        checksumsUrl = "https://github.com/adxptived/yomu/releases/download/v9.9.0/SHA256SUMS.txt",
        notes = """
            ### Что нового
            - Баннер обновления под поиском каталога
            - Сетка источников в каталоге
            - Мини-превью темы в настройках
        """.trimIndent(),
    )

    @Test
    fun stage1BannerAndDialog() {
        runBlocking {
            settings.updateLatest.set("9.9.0")
            settings.catalogSourcesGrid.set(false)
        }
        val updates = track(AppUpdates(context, settings, fetch = { release() }, installer = {}))
        catalog(updates)
        awaitText("Internet Archive")
        // The banner: version, one action and a cross.
        shot("s1_catalog_update_banner")

        // The same result opens the existing details: notes and "Download and install".
        // A real check first, so the dialog does not depend on a settings value another live test could rewrite.
        updates.check()
        runBlocking { kotlinx.coroutines.withTimeout(60_000) { updates.state.first { it is UpdateState.Available } } }
        updates.openDetails()
        // The dialog is its own window: waiting on the state keeps the check independent of window queries.
        compose.waitUntil(30_000) { updates.dialog.value != null }
        compose.waitForIdle()
        shotScreen("s1_update_dialog")
        updates.dismissDialog()
        runBlocking { settings.updateLatest.set("") }
    }

    @Test
    fun stage1CheckFailureKeepsTheLastResult() {
        runBlocking {
            settings.updateLatest.set("")
            settings.updateCheckedAt.set(0)
            settings.updateLastSuccessAt.set(0)
            settings.updateNextAttemptAt.set(0)
        }
        var clock = System.currentTimeMillis()
        var broken = false
        val updates = track(AppUpdates(
            context, settings,
            fetch = { if (broken) throw IOException("Нет соединения") else release() },
            installer = {},
            now = { clock },
        ))
        updates.check()
        runBlocking { kotlinx.coroutines.withTimeout(10_000) { updates.state.first { it is UpdateState.Available } } }
        // A later check, after the success interval and with no network: the release found before stays visible.
        broken = true
        clock += app.yomu.feature.settings.AppUpdates.CHECK_EVERY_MS
        updates.check()
        runBlocking { kotlinx.coroutines.withTimeout(10_000) { updates.state.first { it is UpdateState.Failed } } }
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                SettingsScreen(settingsModel(updates), SettingsLinks(), initialSection = SettingsSection.ABOUT)
            }
        }
        awaitText("Yomu")
        shot("s1_settings_update_failed")
        runBlocking {
            settings.updateLatest.set("")
            settings.updateCheckedAt.set(0)
            settings.updateLastSuccessAt.set(0)
            settings.updateNextAttemptAt.set(0)
        }
    }

    @Test
    fun stage2SourceList() {
        runBlocking { settings.catalogSourcesGrid.set(false) }
        catalog()
        awaitText("Internet Archive")
        shot("s2_catalog_sources_list")
    }

    @Test
    fun stage2SourceGrid() {
        runBlocking { settings.catalogSourcesGrid.set(true) }
        catalog()
        awaitText("Internet Archive")
        shot("s2_catalog_sources_grid")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
    fun stage2GridSurvivesLargeTextAndNarrowScreen() {
        RuntimeEnvironment.setFontScale(1.8f)
        assertEquals(1.8f, compose.activity.resources.configuration.fontScale)
        runBlocking { settings.catalogSourcesGrid.set(true) }
        catalog()
        awaitText("Добавить источник")
        shot("s2_catalog_large_text_toolbar")
        // With 180% text the toolbar fills the screen: scroll the vertical list until the first tiles are composed.
        val vertical = androidx.compose.ui.test.hasScrollAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        compose.onAllNodes(vertical)[0].performScrollToNode(hasText("Internet Archive"))
        awaitText("Internet Archive")
        shot("s2_catalog_grid_large_text")
    }

    @Test
    fun stage2SettingsRootAndAppearance() {
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                SettingsScreen(settingsModel(track(AppUpdates(context, settings, fetch = { null }, installer = {}))), SettingsLinks())
            }
        }
        awaitAnyText()
        shot("s2_settings_root")
    }

    @Test
    fun stage2AppearancePreviews() {
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                SettingsScreen(
                    settingsModel(track(AppUpdates(context, settings, fetch = { null }, installer = {}))),
                    SettingsLinks(),
                    initialSection = SettingsSection.APPEARANCE,
                )
            }
        }
        awaitAnyText()
        compose.mainClock.advanceTimeBy(600)
        shot("s2_settings_appearance")
    }

    @Test
    fun stage2PressedSourceTile() {
        runBlocking { settings.catalogSourcesGrid.set(true) }
        catalog()
        awaitText("Internet Archive")
        val tile = compose.onAllNodesWithText("Internet Archive")[0]
        shot("s2_source_tile_idle")
        tile.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(150)
        shot("s2_source_tile_pressed")
        tile.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(200)
        shot("s2_source_tile_cancelled")
        tile.assertExists()
    }

    @Test
    fun pulseReturnsAfterReleaseAndCancel() = verifyPulse(reducedMotion = false)

    @Test
    fun reducedMotionKeepsThePressTargetStill() = verifyPulse(reducedMotion = true)

    private fun verifyPulse(reducedMotion: Boolean) {
        val interactions = MutableInteractionSource()
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                CompositionLocalProvider(LocalReduceMotion provides reducedMotion) {
                    Box(Modifier.size(180.dp, 84.dp).background(Color.White), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(160.dp, 64.dp).pressPulse(interactions, scaleOnPress = true).background(Color.Red))
                    }
                }
            }
        }
        fun redWidth(): Int = compose.runOnIdle {
            val content = compose.activity.findViewById<View>(android.R.id.content)
            val height = minOf(content.height, 240)
            val bitmap = Bitmap.createBitmap(content.width, height, Bitmap.Config.ARGB_8888)
            try {
                content.draw(Canvas(bitmap))
                val pixels = IntArray(bitmap.width)
                val red = (0 until height).maxOf { y ->
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                    pixels.count { it == android.graphics.Color.RED }
                }
                check(red > 0) { "Press target was not drawn" }
                red
            } finally {
                bitmap.recycle()
            }
        }
        fun dispatch(interaction: androidx.compose.foundation.interaction.Interaction) {
            compose.runOnIdle { check(interactions.tryEmit(interaction)) }
            compose.mainClock.advanceTimeBy(250)
        }

        val idle = redWidth()
        val first = PressInteraction.Press(Offset.Zero)
        dispatch(first)
        val pressed = redWidth()
        check(if (reducedMotion) pressed == idle else pressed < idle) { "idle=$idle, pressed=$pressed" }
        dispatch(PressInteraction.Release(first))
        check(redWidth() == idle)

        val second = PressInteraction.Press(Offset.Zero)
        dispatch(second)
        dispatch(PressInteraction.Cancel(second))
        check(redWidth() == idle)
    }

    @Test
    fun stage4Widgets() = widgetGallery("s4_widget")

    /** The same gallery in the night palette: the widget colours are resources, so the system theme switches them. */
    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-night-xxhdpi")
    fun stage4WidgetsDark() = widgetGallery("s4_widget_night")

    private fun widgetGallery(prefix: String) {
        seedReading()
        seedNewChapters()
        seedSessions()
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val continueId = shadowOf(manager).createWidget(ContinueReadingWidget::class.java, R.layout.widget_continue)
        val newId = shadowOf(manager).createWidget(NewChaptersWidget::class.java, R.layout.widget_new_chapters)
        val smallId = shadowOf(manager).createWidget(NewChaptersWidget::class.java, R.layout.widget_new_chapters)
        val tinyId = shadowOf(manager).createWidget(NewChaptersWidget::class.java, R.layout.widget_new_chapters)
        val shelfId = shadowOf(manager).createWidget(ShelfWidget::class.java, R.layout.widget_shelf)
        val streakId = shadowOf(manager).createWidget(StreakWidget::class.java, R.layout.widget_streak)
        val audioId = shadowOf(manager).createWidget(AudioWidgetProvider::class.java, R.layout.widget_audio)
        fun size(id: Int, width: Int, height: Int) = manager.updateAppWidgetOptions(
            id,
            android.os.Bundle().apply {
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            },
        )
        size(continueId, 340, 250)
        size(newId, 300, 200)
        size(smallId, 160, 120)
        size(tinyId, 80, 80)
        size(shelfId, 340, 250)
        size(streakId, 160, 160)
        size(audioId, 340, 110)
        runBlocking {
            ContinueWidgets.refresh(context)
            NewChaptersWidgets.refresh(context)
            ShelfWidgets.refresh(context)
            StreakWidgets.refresh(context)
        }
        AudioWidgetContract.save(
            context,
            AudioWidgetState(
                bookId = 7,
                bookTitle = "Тихий океан",
                trackTitle = "Глава 4 · Море",
                playing = true,
                positionMs = 1_245_000,
                durationMs = 4_320_000,
            ),
        )
        AudioWidgetProvider().onReceive(context, AudioWidgetContract.updateIntent(context))
        shadowOf(Looper.getMainLooper()).idle()
        // The count of every new chapter, not only the visible rows, sits in the header.
        val newView = shadowOf(manager).getViewFor(newId)
        val total = runBlocking {
            GlobalContext.get().get<app.yomu.data.catalog.CatalogRepository>().observeNewCount().first()
        }
        assertEquals("stored new chapters", 3, total)
        assertEquals("3 новых", newView.findViewById<TextView>(R.id.nc_count).text.toString())
        val continueView = shadowOf(manager).getViewFor(continueId)
        captureWidget(continueView, 340, 250, "${prefix}_continue")
        captureWidget(newView, 300, 200, "${prefix}_new_chapters")
        captureWidget(shadowOf(manager).getViewFor(smallId), 160, 120, "${prefix}_new_chapters_small")
        val tinyView = shadowOf(manager).getViewFor(tinyId)
        assertEquals("3", tinyView.findViewById<TextView>(R.id.nc_count).text.toString())
        captureWidget(tinyView, 80, 80, "${prefix}_new_chapters_tiny")
        // The same card style on the shelf, the streak tile of the stats screen and the mini player.
        val shelfView = shadowOf(manager).getViewFor(shelfId)
        assertEquals("Полка", shelfView.findViewById<TextView>(R.id.widget_shelf_header_text).text.toString())
        assertTrue(shelfView.findViewById<TextView>(R.id.widget_shelf_title_0).text.toString().isNotBlank())
        captureWidget(shelfView, 340, 250, "${prefix}_shelf")
        val streakView = shadowOf(manager).getViewFor(streakId)
        assertEquals("5", streakView.findViewById<TextView>(R.id.widget_streak_number).text.toString())
        captureWidget(streakView, 160, 160, "${prefix}_streak")
        val audioView = shadowOf(manager).getViewFor(audioId)
        assertEquals("Тихий океан", audioView.findViewById<TextView>(R.id.widget_audio_title).text.toString())
        assertEquals(View.VISIBLE, audioView.findViewById<View>(R.id.widget_audio_toggle).visibility)
        assertEquals(View.VISIBLE, audioView.findViewById<View>(R.id.widget_audio_progress).visibility)
        captureWidget(audioView, 340, 110, "${prefix}_audio")
    }

    /** E2: the last check report names the rule that skipped a title instead of only counting it. */
    @Test
    fun stage5SkippedTitlesExplainThemselves() {
        val json = """
            {"startedAt":1700000000000,"finishedAt":1700000060000,"checked":4,"skipped":3,"newChapters":6,
             "failed":1,"queuedDownloads":2,"titles":[
               {"title":"Сказание о сакуре","newChapters":4},
               {"title":"Летний дождь","newChapters":2},
               {"title":"Тихий океан","failed":true},
               {"title":"Песнь странника","skip":"INTERVAL"},
               {"title":"Дом у моря","skip":"RESTRICTION"},
               {"title":"Северный ветер","skip":"NO_SOURCE"}
             ]}
        """.trimIndent()
        runBlocking { settings.updateLastRun.set(json) }
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                app.yomu.feature.catalog.UpcomingScreen(
                    viewModel = koinViewModel<app.yomu.feature.catalog.UpcomingViewModel>(),
                    onBack = {},
                    onOpenTitle = {},
                )
            }
        }
        awaitText("пропущено 3")
        compose.onAllNodesWithText("Результаты проверки")[0].performClick()
        awaitText("Песнь странника")
        shot("s5_updates_report_skips")
    }

    private fun seedReading() = runBlocking {
        val now = System.currentTimeMillis()
        listOf(
            Triple("Тихий океан", 0.42f, "Хару Мицуки"),
            Triple("Песнь странника", 0.18f, "Ая Кобаяси"),
            Triple("Дом у моря", 0.71f, "Ирина Волкова"),
        ).forEachIndexed { i, (title, progress, author) ->
            db.books().insert(
                BookEntity(
                    title = title, sortTitle = title, authorsText = author, seriesName = null, seriesId = null,
                    seriesIndex = null, language = "ru", description = null, publisher = null, format = "EPUB",
                    uri = "file:///books/$i.epub", isLinked = false, fileSize = 120_000, fileHash = null,
                    coverPath = null, status = "READING", isManga = false, addedAt = now - 5 * 86_400_000,
                    lastReadAt = now - i * 3_600_000L, progress = progress, readerSettingsJson = null,
                ),
            )
        }
    }

    /** Five days of reading in a row, twenty minutes a day: the streak tile shows a readable number and a pill. */
    private fun seedSessions() = runBlocking {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (i in 0 until 5) {
            val start = today.minusDays(i.toLong()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            db.sessions().insert(
                ReadingSessionEntity(
                    bookId = null,
                    startedAt = start,
                    durationMs = 20 * 60_000L,
                    pages = 12,
                    chars = 2_400,
                ),
            )
        }
    }

    private fun seedNewChapters() = runBlocking {
        val now = System.currentTimeMillis()
        val first = db.catalog().insert(
            CatalogItemEntity(
                sourceId = 4242, url = "/title/ds-first", title = "Сказание о сакуре", author = null, artist = null,
                description = null, genres = "", status = 1, coverUrl = null, type = "MANGA", favorite = true,
                categoryId = null, addedAt = now, lastUpdateAt = now, initialized = true,
            ),
        )
        val second = db.catalog().insert(
            CatalogItemEntity(
                sourceId = 4242, url = "/title/ds-second", title = "Летний дождь", author = null, artist = null,
                description = null, genres = "", status = 1, coverUrl = null, type = "MANGA", favorite = true,
                categoryId = null, addedAt = now, lastUpdateAt = now, initialized = true,
            ),
        )
        db.catalog().insertChapters(
            listOf(
                ChapterEntity(
                    itemId = first, url = "/ds/1", name = "Глава 12", number = 12f, scanlator = null,
                    uploadDate = now - 10_000, read = false, bookmarked = false, lastPageRead = 0, downloaded = false,
                    localPath = null, isNew = true, sourceOrder = 0, fetchedAt = now, lastReadAt = null,
                ),
                ChapterEntity(
                    itemId = second, url = "/ds/2", name = "Глава 5", number = 5f, scanlator = null,
                    uploadDate = now - 20_000, read = false, bookmarked = false, lastPageRead = 0, downloaded = false,
                    localPath = null, isNew = true, sourceOrder = 0, fetchedAt = now, lastReadAt = null,
                ),
                ChapterEntity(
                    itemId = second, url = "/ds/3", name = "Глава 6", number = 6f, scanlator = null,
                    uploadDate = now - 5_000, read = false, bookmarked = false, lastPageRead = 0, downloaded = false,
                    localPath = null, isNew = true, sourceOrder = 0, fetchedAt = now, lastReadAt = null,
                ),
            ),
        )
    }

    /** Draws a widget view at a realistic size into a PNG (a widget is not a Compose scene). */
    private fun captureWidget(view: View, widthDp: Int, heightDp: Int, name: String) {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/roborazzi/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
