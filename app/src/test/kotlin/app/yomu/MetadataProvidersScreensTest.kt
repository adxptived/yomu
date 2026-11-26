package app.yomu

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.domain.model.ExternalMetadata
import app.yomu.domain.model.MetadataAvailability
import app.yomu.domain.model.MetadataProvider
import app.yomu.domain.model.MetadataProviderState
import app.yomu.feature.catalog.MetadataMatchDialog
import app.yomu.feature.catalog.TitleViewModel
import app.yomu.feature.settings.AppUpdates
import app.yomu.feature.settings.SettingsLinks
import app.yomu.feature.settings.SettingsScreen
import app.yomu.feature.settings.SettingsSection
import app.yomu.feature.settings.SettingsViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C1 screens: the provider row of the match dialog, the preview before "Apply", the MyAnimeList setup message and the
 * provider preference in the catalog settings. Robolectric renders them; the services themselves are not called.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class MetadataProvidersScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings: AppSettings get() = GlobalContext.get().get()

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun providers(malConfigured: Boolean = false) = listOf(
        MetadataProviderState(MetadataProvider.ANILIST, MetadataAvailability.READY),
        MetadataProviderState(
            MetadataProvider.MYANIMELIST,
            if (malConfigured) MetadataAvailability.READY else MetadataAvailability.NEEDS_CLIENT_ID,
        ),
        MetadataProviderState(MetadataProvider.MANGABAKA, MetadataAvailability.READY),
    )

    private val results = listOf(
        ExternalMetadata(
            provider = "AniList", id = 118586, title = "Sousou no Frieren",
            altTitle = "Frieren: Beyond Journey's End", year = 2020, format = "MANGA",
            description = "Эльфийская волшебница Фрирен переживает своих спутников и отправляется в новое путешествие.",
            provenance = listOf("title", "description", "year", "format"),
        ),
        ExternalMetadata(
            provider = "AniList", id = 30002, title = "Frieren: Beyond Journey's End",
            year = 2021, format = "NOVEL",
            description = "Ранобэ-адаптация той же истории.",
            provenance = listOf("title", "year", "format"),
        ),
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shotScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    private fun dialog(
        state: TitleViewModel.MetadataSearchState,
        provider: MetadataProvider = MetadataProvider.ANILIST,
    ) = compose.setContent {
        YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
            // A real screen always sits behind the dialog; a dialog-only composition is not a realistic host and can
            // keep the test window from settling.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                MetadataMatchDialog(
                    state = state,
                    initialQuery = "Sousou no Frieren",
                    providers = providers(),
                    provider = provider,
                    onProvider = { _, _ -> },
                    onSearch = {},
                    onPick = {},
                    onUseSource = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun theProviderRowAndThePreview() {
        dialog(TitleViewModel.MetadataSearchState.Ready(MetadataProvider.ANILIST, results))
        awaitText("Внешние сведения")
        shotScreen("c1_metadata_dialog")
        // Looking at a result is not applying it: the preview names the fields the provider supplies.
        compose.onNodeWithText("AniList · 2020 · MANGA").performClick()
        awaitText("Предпросмотр")
        shotScreen("c1_metadata_preview")
    }

    @Test
    fun unavailableMyAnimeListExplainsWhereToConfigureIt() {
        dialog(TitleViewModel.MetadataSearchState.Idle, provider = MetadataProvider.MYANIMELIST)
        awaitText("Для поиска в MyAnimeList нужен client ID")
        awaitText("Укажите client ID MyAnimeList в настройках трекеров")
        shotScreen("c1_metadata_needs_client_id")
    }

    @Test
    fun theProviderPreferenceIsInTheCatalogSettings() {
        val koin = GlobalContext.get()
        val vm = SettingsViewModel(
            context, settings, koin.get(), koin.get(), koin.get(), koin.get(),
            AppUpdates(context, settings, fetch = { null }, installer = {}),
            koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(),
        )
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                SettingsScreen(vm, SettingsLinks(), initialSection = SettingsSection.CATALOG)
            }
        }
        awaitText("Сведения на карточке тайтла")
        shotScreen("c1_settings_metadata_provider")
    }
}
