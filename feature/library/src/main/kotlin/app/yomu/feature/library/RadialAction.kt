package app.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yomu.core.ui.components.rememberHaptic
import app.yomu.core.ui.theme.Dimens
import kotlin.math.cos
import kotlin.math.sin
import app.yomu.core.ui.R as CoreR

/** One entry of [RadialMenu]. */
data class RadialAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press wheel, in the spirit of a game's weapon wheel: the actions sit on a circle around the middle of the
 * screen, the scrim dismisses it, and one tap runs an action and closes the wheel.
 */
@Composable
fun RadialMenu(title: String?, actions: List<RadialAction>, onDismiss: () -> Unit) {
    val haptic = rememberHaptic()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val radius = minOf(maxWidth, maxHeight) * 0.31f
                val count = actions.size.coerceAtLeast(1)
                // One wheel: a soft disc ties the actions together.
                Box(
                    Modifier.align(Alignment.Center).size(radius * 2 + 86.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
                )
                if (title != null) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).widthIn(max = radius * 0.95f),
                    )
                }
                actions.forEachIndexed { index, action ->
                    val angle = Math.toRadians(-90.0 + 360.0 * index / count)
                    Column(
                        Modifier.align(Alignment.Center).offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            onClick = { haptic.confirm(); onDismiss(); action.onClick() },
                            shape = CircleShape,
                            color = if (action.destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(58.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    action.icon,
                                    action.label,
                                    Modifier.size(26.dp),
                                    tint = if (action.destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(action.label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Five stars for the reader's own score; the icon row is the whole control. */
@Composable
fun RatingDialog(title: String, current: Int, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.library_rating_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = Dimens.m),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (star in 1..5) {
                        IconButton(onClick = { onRate(star) }) {
                            Icon(
                                if (star <= current) Icons.Filled.Star else Icons.Filled.StarOutline,
                                stringResource(R.string.library_rate_n, star),
                                Modifier.size(32.dp),
                                tint = if (star <= current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (current > 0) {
                    TextButton(onClick = { onRate(0) }) { Text(stringResource(R.string.library_rating_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.close)) }
            }
        },
    )
}
