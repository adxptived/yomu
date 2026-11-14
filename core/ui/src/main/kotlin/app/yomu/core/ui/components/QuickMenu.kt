package app.yomu.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/** One entry of a long-press menu. */
data class QuickMenuItem(val key: String, val icon: ImageVector, val label: String, val tint: Color? = null, val onClick: () -> Unit)

/** The popup covers the whole window; its content is placed in window coordinates. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * Long-press menu at [anchor] (window coordinates): the shared [RadialMenu] wheel for [radial], or a list next to the
 * finger. A tap outside closes it. The wheel is the same one the library, the manga pages and the audio player use.
 */
@Composable
fun QuickActionMenu(radial: Boolean, anchor: Offset, items: List<QuickMenuItem>, onDismiss: () -> Unit, title: String? = null) {
    if (items.isEmpty()) return
    if (radial) {
        val state = remember(items, anchor, title) {
            RadialState(anchor, items.map { RadialAction(it.icon, it.label, tint = it.tint) { it.onClick() } }, title)
        }
        RadialMenu(state = state, dragSelect = false, onDismiss = onDismiss)
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "quickMenu")
    Popup(popupPositionProvider = WindowOrigin, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f * progress))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            ListMenu(anchor, items, progress, title, onDismiss)
        }
    }
}

@Composable
private fun ListMenu(anchor: Offset, items: List<QuickMenuItem>, progress: Float, title: String?, onDismiss: () -> Unit) {
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(
        content = {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.widthIn(min = 200.dp, max = 300.dp).heightIn(max = 460.dp).alpha(progress).scale(0.9f + 0.1f * progress),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items.forEach { item ->
                        Surface(onClick = { onDismiss(); item.onClick() }, color = Color.Transparent) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(item.icon, null, tint = item.tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (anchor.x.roundToInt() - placeable.width / 2).coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val below = anchor.y.roundToInt() + margin
            val y = if (below + placeable.height <= constraints.maxHeight - margin) below else (anchor.y.roundToInt() - placeable.height - margin).coerceAtLeast(margin)
            placeable.place(x, y)
        }
    }
}
