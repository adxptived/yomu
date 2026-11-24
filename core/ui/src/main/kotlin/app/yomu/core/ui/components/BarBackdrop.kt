package app.yomu.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.theme.isDarkScheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The blurred surface under a floating bar (the navigation pill, the top bars of the reader and downloads): the
 * scrolling page shows through, blurred and washed, with a touch more coverage at the top edge so the icons keep
 * their contrast over bright covers. Devices without the blur get a dense fill instead (the fallback tint).
 */
@Composable
fun Modifier.barBackdrop(state: HazeState): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return hazeEffect(state) {
        backgroundColor = surface
        blurRadius = 24.dp
        tints = listOf(
            HazeTint(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.72f),
                    1f to surface.copy(alpha = 0.46f),
                ),
            ),
        )
        fallbackTint = HazeTint(surface.copy(alpha = 0.96f))
        noiseFactor = 0f
    }
}

/**
 * The specular edge of the glass: a light stroke at the top of the capsule fading into the outline and a soft shade
 * at the bottom. It sells the glass without a drop shadow, which would smear the content passing underneath.
 */
@Composable
internal fun glassEdge(): Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = if (isDarkScheme()) 0.34f else 0.72f),
    0.5f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
    1f to Color.Black.copy(alpha = 0.08f),
)

/** A faint sheen over the blur, under the icons: the top of the glass catches the light. */
internal fun Modifier.glassSheen(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.55f to Color.Transparent))
}
