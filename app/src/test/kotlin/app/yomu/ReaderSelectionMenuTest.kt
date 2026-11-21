package app.yomu

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import app.yomu.domain.model.QuickMenuStyle
import app.yomu.domain.model.SelectionAction
import app.yomu.domain.model.SelectionActionConfig
import app.yomu.feature.annotations.SelectionMenu
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ReaderSelectionMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listCommandReceivesActiveSelection() = assertHighlightUsesActiveSelection(QuickMenuStyle.LIST)

    @Test
    fun radialCommandReceivesActiveSelection() = assertHighlightUsesActiveSelection(QuickMenuStyle.RADIAL)

    private fun assertHighlightUsesActiveSelection(style: QuickMenuStyle) {
        var selection: String? = "Selected passage"
        var highlighted: String? = null
        compose.setContent {
            SelectionMenu(
                style = style,
                anchor = Rect(160f, 170f, 250f, 200f),
                actions = listOf(SelectionActionConfig(SelectionAction.HIGHLIGHT, enabled = true)),
                customActions = emptyList(),
                currentColor = null,
                onHighlight = { _, _ -> highlighted = selection },
                onAction = {},
                onCustom = {},
                onDelete = null,
                onDismiss = { selection = null },
            )
        }
        compose.onNode(hasText("Highlight") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals("Selected passage", highlighted) }
    }
}
