package app.yomu

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.components.PillTab
import app.yomu.core.ui.components.PillTabRow
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PillTabRowTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val screenshots = TemporaryFolder()

    @Test
    fun selectedTabIsPaintedWhenMoreTabsOverflow() {
        val accent = Color(0xff663399)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent)) {
                Box(Modifier.width(200.dp).background(Color.White)) {
                    PillTabRow(
                        tabs = listOf(PillTab("all", "All")) + (1..6).map { PillTab("$it", "Collection $it") },
                        selected = "all",
                        onSelect = {},
                    )
                }
            }
        }

        val screenshot = screenshots.newFile("selected-tab.png")
        compose.onNodeWithText("All").captureRoboImage(screenshot, RoborazziOptions(taskType = RoborazziTaskType.Record))
        val image = checkNotNull(BitmapFactory.decodeFile(screenshot.absolutePath))
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        assertTrue("The selected tab is present in semantics but its fill is missing on screen", pixels.count { it == accent.toArgb() } > pixels.size / 4)
    }

    @Test
    fun selectedTabStartsVisibleWhenItIsAtTheEndOfTheRow() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(200.dp)) {
                    PillTabRow(
                        tabs = (1..6).map { PillTab("$it", "Collection $it") } + PillTab("last", "Last"),
                        selected = "last",
                        onSelect = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Last").assertIsDisplayed()
    }
}
