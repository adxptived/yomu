package app.yomu.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.theme.isDarkScheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The frosted surface under a floating bar or a top bar: the content scrolls on underneath, blurred and tinted with
 * the theme's own surface so icons and labels keep their contrast over any cover. Dark themes use a lifted container
 * tone (visible even on AMOLED black), light themes a milky surface; both stay translucent enough for the colours of
 * the covers to come through.
 *
 * Devices without a usable blur keep a denser fill ([fallbackTint]), so the bars never turn into unreadable
 * see-through strips.
 */
@Composable
fun Modifier.barBackdrop(state: HazeState): Modifier {
    val dark = isDarkScheme()
    // A lifted container tone rather than `surface`: on AMOLED it is visible against pure black, and on a white page
    // the slightly deeper tint still marks the bar without turning it into an opaque strip.
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    return hazeEffect(state) {
        backgroundColor = tint
        blurRadius = 22.dp
        tints = listOf(
            HazeTint(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = if (dark) 0.62f else 0.60f),
                    1f to tint.copy(alpha = if (dark) 0.50f else 0.50f),
                ),
            ),
        )
        fallbackTint = HazeTint(tint.copy(alpha = if (dark) 0.94f else 0.92f))
        noiseFactor = 0f
    }
}
