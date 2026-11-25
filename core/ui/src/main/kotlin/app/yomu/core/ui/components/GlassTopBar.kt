package app.yomu.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import dev.chrisbanes.haze.HazeState

/**
 * Colours of a top bar drawn on a [barBackdrop]: the material container is transparent, so the blurred, tinted layer
 * behind the whole bar (title, actions and tabs) is what the user sees, instead of an opaque strip that hides the
 * content passing under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

/**
 * The single backdrop shared by a top app bar and the row of tabs below it: [content] is laid out over the blur, and
 * the scrolling page is captured by the [barBackdrop] haze effect.
 */
@Composable
fun GlassTopBarBackground(state: HazeState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .barBackdrop(state)
            // The bar owns touches in its gaps as well as on its controls, so a tap between two icons never reaches
            // the list scrolling underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        content = content,
    )
}
