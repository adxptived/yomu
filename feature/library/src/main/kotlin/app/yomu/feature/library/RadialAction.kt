package app.yomu.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.yomu.core.ui.components.rememberHaptic
import app.yomu.core.ui.theme.Dimens
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
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
 * An open long-press wheel: where the finger went down, the actions and which one the finger is over now.
 * [active] and [dragging] are written by the gesture that opened the wheel: while the finger is down the ring
 * follows it, and after a release in the middle the ring stays open, tappable.
 */
class RadialState(val anchor: Offset, val actions: List<RadialAction>, val title: String? = null) {
    var active by mutableStateOf<Int?>(null)
    var dragging by mutableStateOf(false)
}

/** The popup covers the whole window; the wheel is placed in window coordinates. */
private object WheelOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/** Distance of the ring from the centre, in dp, for [count] actions. */
internal fun radialRadiusDp(count: Int) = when {
    count <= 5 -> 92.dp
    count <= 7 -> 106.dp
    else -> 122.dp
}

/** Size of an action button on the ring, in dp. */
internal val RADIAL_BUTTON_DP = 54.dp

/** Radius of the central "cancel" button, in dp. */
internal val RADIAL_CENTER_DP = 56.dp

/**
 * Where the ring is drawn for a finger at [anchor]: under it, unless that would push the ring off the screen —
 * then just close enough to the edge that every action stays visible. Both the drawing and the choice of the action
 * use this function, so a sector is always where it looks.
 */
internal fun wheelCenter(
    anchor: Offset,
    radiusPx: Float,
    buttonPx: Float,
    marginPx: Float,
    widthPx: Float,
    heightPx: Float,
): Offset {
    val reach = radiusPx + buttonPx / 2 + marginPx
    val x = if (widthPx <= reach * 2f) widthPx / 2f else anchor.x.coerceIn(reach, widthPx - reach)
    val y = if (heightPx <= reach * 2f) heightPx / 2f else anchor.y.coerceIn(reach, heightPx - reach)
    return Offset(x, y)
}

/**
 * The sector of the wheel under [finger], or null in the dead zone around the central button (lifting the finger
 * there cancels). Sectors start at the top and go clockwise, like the buttons are drawn — any direction outside the
 * dead zone is a choice, no need to hit the button exactly.
 */
internal fun radialSector(count: Int, center: Offset, finger: Offset, deadZonePx: Float): Int? {
    if (count <= 0) return null
    val dx = finger.x - center.x
    val dy = finger.y - center.y
    if (dx * dx + dy * dy < deadZonePx * deadZonePx) return null
    val step = (2 * PI / count).toFloat()
    val angle = atan2(dy, dx) + PI.toFloat() / 2
    return ((angle / step).roundToInt() % count + count) % count
}

/**
 * The long-press wheel under the finger. A drag highlights an action and lifting the finger runs it; lifting it in
 * the middle leaves the ring open, so the same actions can simply be tapped (and read by TalkBack). Without
 * [dragSelect] the ring never follows a drag — the old "one tap per action" style.
 */
@Composable
internal fun RadialMenu(
    state: RadialState,
    dragSelect: Boolean,
    onDismiss: () -> Unit,
) {
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    val title = state.title
    val actions = state.actions
    val count = actions.size.coerceAtLeast(1)
    val item = 88.dp
    val base = radialRadiusDp(count)
    // While the finger that opened the wheel is still down nothing can be tapped; once it lifts, everything can.
    val tappable = !state.dragging

    // A tic per sector: the finger feels the wheel change before the eye catches it.
    LaunchedEffect(state.active) { if (state.active != null) haptic.tick() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "radialWheel")
    val cancelLabel = stringResource(CoreR.string.close)

    Popup(
        popupPositionProvider = WheelOrigin,
        onDismissRequest = onDismiss,
        // While the finger is down the wheel must not take the touch stream away from it.
        properties = PopupProperties(focusable = tappable, clippingEnabled = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress))
                .pointerInput(tappable) { if (tappable) detectTapGestures { onDismiss() } },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val radiusPx = with(density) { base.toPx() }
                val buttonPx = with(density) { RADIAL_BUTTON_DP.toPx() }
                val marginPx = with(density) { 10.dp.toPx() }
                // Under the finger, or as close to it as the screen permits: the same point the sectors use.
                val center = wheelCenter(
                    if (state.anchor.isSpecified) state.anchor else Offset(widthPx / 2f, heightPx / 2f),
                    radiusPx, buttonPx, marginPx, widthPx, heightPx,
                )
                val disc = radiusPx + buttonPx / 2 + marginPx
                val itemW = with(density) { item.toPx() }
                val centerPx = with(density) { RADIAL_CENTER_DP.toPx() }

                Box(
                    Modifier.offset { IntOffset((center.x - disc).roundToInt(), (center.y - disc).roundToInt()) }
                        .size(with(density) { disc.toDp() } * 2)
                        .alpha(progress)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
                )
                actions.forEachIndexed { index, action ->
                    val angle = -PI / 2 + 2 * PI * index / count
                    val x = center.x + radiusPx * cos(angle).toFloat()
                    val y = center.y + radiusPx * sin(angle).toFloat()
                    val active = state.active == index
                    Column(
                        Modifier
                            .offset { IntOffset((x - itemW / 2).roundToInt(), (y - buttonPx / 2).roundToInt()) }
                            .width(item)
                            .alpha(progress * if (state.active == null || active) 1f else 0.55f)
                            .scale(if (active) 1.1f else 1f)
                            // The whole item (button and label) is the touch target once the finger is up, so a tap
                            // on the name works as well as a tap on the icon — and TalkBack sees one button.
                            .then(
                                if (tappable) {
                                    Modifier.clickable(role = Role.Button) {
                                        onDismiss()
                                        action.onClick()
                                    }
                                } else {
                                    Modifier.semantics(mergeDescendants = true) { contentDescription = action.label }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WheelButton(action, active)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            action.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = item),
                        )
                    }
                }
                // The central button: "cancel", or the icon of the action the finger is over.
                val over = state.active?.let(actions::getOrNull)
                Surface(
                    shape = CircleShape,
                    color = if (over == null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                    contentColor = if (over == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .offset { IntOffset((center.x - centerPx / 2).roundToInt(), (center.y - centerPx / 2).roundToInt()) }
                        .size(RADIAL_CENTER_DP)
                        .alpha(progress)
                        .then(if (tappable) Modifier.clickable { onDismiss() } else Modifier)
                        .semantics { contentDescription = if (over != null) over.label else cancelLabel },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(over?.icon ?: Icons.Filled.Close, null, Modifier.size(if (over != null) 26.dp else 22.dp))
                    }
                }
                // With a finger on the ring the name of the action it is over is the important thing; it sits under
                // the central button, where the finger does not cover it.
                if (dragSelect && state.dragging) {
                    val label = over?.label ?: title
                    if (label != null) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (center.x - with(density) { 180.dp.toPx() } / 2).roundToInt(),
                                        (center.y + centerPx / 2 + with(density) { 10.dp.toPx() }).roundToInt(),
                                    )
                                }
                                .widthIn(max = 180.dp)
                                .alpha(progress),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                } else if (title != null) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (center.x - with(density) { 220.dp.toPx() } / 2).roundToInt(),
                                    (center.y - disc - with(density) { 46.dp.toPx() }).coerceAtLeast(0f).roundToInt(),
                                )
                            }
                            .width(220.dp)
                            .alpha(progress),
                    )
                }
            }
        }
    }
}

@Composable
private fun WheelButton(action: RadialAction, active: Boolean) {
    val color = when {
        action.destructive -> MaterialTheme.colorScheme.errorContainer
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        action.destructive -> MaterialTheme.colorScheme.onErrorContainer
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = CircleShape,
        color = color,
        contentColor = content,
        shadowElevation = if (active) 12.dp else 6.dp,
        modifier = Modifier.size(if (active) 60.dp else RADIAL_BUTTON_DP),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(action.icon, null, Modifier.size(if (active) 28.dp else 24.dp)) }
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
