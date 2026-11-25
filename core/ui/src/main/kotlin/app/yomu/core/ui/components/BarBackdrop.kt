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
 * the theme's own surface so icons and labels keep their contrast over any cover. The tint is translucent: the bar
 * only darkens (or milks) what passes under it instead of drawing a solid strip of its own.
 *
 * Devices without a usable blur keep a denser fill ([fallbackTint]), so the bars never turn into unreadable
 * see-through strips.
 */
@Composable
fun Modifier.barBackdrop(state: HazeState): Modifier {
    val dark = isDarkScheme()
    // A lifted container tone rather than `surface`: on AMOLED it is visible against pure black, and on a white page
    // the slightly deeper tint still marks the bar without turning it into an opaque strip.
    val colors = MaterialTheme.colorScheme
    val tint = colors.surfaceContainerHigh
    return hazeEffect(state) {
        // Fill gaps behind the blurred capture so the original text cannot show through the blur.
        backgroundColor = colors.background
        blurRadius = 28.dp
        tints = listOf(
            HazeTint(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = if (dark) 0.26f else 0.28f),
                    1f to tint.copy(alpha = if (dark) 0.14f else 0.16f),
                ),
            ),
        )
        // Even where the GPU cannot blur, the bar stays translucent: a solid strip was the complaint the glass bars
        // were meant to answer.
        fallbackTint = HazeTint(tint.copy(alpha = if (dark) 0.48f else 0.56f))
        noiseFactor = 0f
    }
}
