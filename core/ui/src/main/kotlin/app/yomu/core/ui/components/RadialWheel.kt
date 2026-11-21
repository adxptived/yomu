package app.yomu.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.yomu.core.ui.R
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One entry of [RadialMenu]. */
data class RadialAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    /** Icon colour for actions that carry a colour of their own (a highlight). */
    val tint: Color? = null,
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

    /** True while the actions that did not fit on the ring are shown as a list instead of the wheel. */
    var overflow by mutableStateOf(false)

    /** True while a press on the menu's own scrim steers the wheel (press, slide, lift to run). */
    var steering by mutableStateOf(false)
}

/** The popup covers the whole window; the wheel is placed in window coordinates. */
private object WheelOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/** Distance of the ring from the centre, in dp, for [count] actions. */
fun radialRadiusDp(count: Int) = when {
    count <= 5 -> 92.dp
    count <= 7 -> 106.dp
    else -> 122.dp
}

/** Size of an action button on the ring, in dp. */
val RADIAL_BUTTON_DP = 54.dp

/** Radius of the central "cancel" button, in dp. */
val RADIAL_CENTER_DP = 56.dp

/**
 * Where the ring is drawn for a finger at [anchor]: under it, unless that would push the ring off the screen —
 * then just close enough to the edge that every action stays visible. Both the drawing and the choice of the action
 * use this function, so a sector is always where it looks.
 */
fun wheelCenter(
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
fun radialSector(count: Int, center: Offset, finger: Offset, deadZonePx: Float): Int? {
    if (count <= 0) return null
    val dx = finger.x - center.x
    val dy = finger.y - center.y
    if (dx * dx + dy * dy < deadZonePx * deadZonePx) return null
    val step = (2 * PI / count).toFloat()
    val angle = atan2(dy, dx) + PI.toFloat() / 2
    return ((angle / step).roundToInt() % count + count) % count
}

/**
 * The long-press wheel: one ring of the same actions on every screen (library books, manga pages, text selections,
 * audio covers). A drag highlights an action and lifting the finger runs it; lifting it in the middle leaves the ring
 * open, so the same actions can simply be tapped (and read by TalkBack). Without [dragSelect] the ring never follows a
 * drag — the actions are tapped directly.
 */
@Composable
fun RadialMenu(
    state: RadialState,
    dragSelect: Boolean,
    onDismiss: () -> Unit,
    onActionChosen: () -> Unit = onDismiss,
) {
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    val title = state.title
    val actions = state.actions
    val moreLabel = stringResource(R.string.more)
    // Past the sector cap the ring keeps the first actions and adds one "More" that opens the rest as a list:
    // twenty sectors cannot be hit with a thumb or described by TalkBack, and shrinking the buttons is not a fix.
    val ring = remember(actions.size) { WheelLayout.ringIndices(actions.size) }
    val ringActions = remember(actions, ring, moreLabel) {
        ring.map { index ->
            if (index < actions.size) actions[index] else RadialAction(Icons.Default.MoreHoriz, moreLabel) { state.overflow = true }
        }
    }
    val count = ring.count().coerceAtLeast(1)
    val item = 88.dp
    val base = radialRadiusDp(count)
    // While the finger that opened the wheel is still down nothing can be tapped; once it lifts, everything can.
    val tappable = !state.dragging

    // A tic per sector: the finger feels the wheel change before the eye catches it.
    LaunchedEffect(state.active) { if (state.active != null) haptic.tick() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "radialWheel")
    val cancelLabel = stringResource(R.string.close)

    Popup(
        popupPositionProvider = WheelOrigin,
        onDismissRequest = onDismiss,
        // While the finger that opened the wheel is down, the wheel must not take the touch stream away from it.
        // The window must be the whole screen: with the platform's default width the anchor (window coordinates)
        // lands outside the popup, and the ring shows up somewhere far from the finger.
        properties = PopupProperties(focusable = tappable, clippingEnabled = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress))
                // Steering: press the scrim (not a button) and slide — the sector under the finger highlights and
                // lifting the finger runs it; a plain tap on the scrim still closes the wheel. While an external
                // gesture (the long press that opened the wheel) owns the stream, this does nothing: that finger
                // is steered by the item it started on ([wheelGesture]).
                .pointerInput(state, tappable, count, base, RADIAL_BUTTON_DP) {
                    if (!tappable) return@pointerInput
                    val deadZone = 30.dp.toPx()
                    val marginPx = 10.dp.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        // A stale sector from an earlier gesture must not run on a fresh tap.
                        state.active = null
                        var steering = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    when (val index = state.active) {
                                        null -> onDismiss()
                                        else -> {
                                            if (WheelLayout.isMore(actions.size, index)) {
                                                // The ring stays open and swaps itself for the list of the rest.
                                                state.overflow = true
                                            } else {
                                                ringActions.getOrNull(index)?.let { action ->
                                                    onActionChosen()
                                                    action.onClick()
                                                } ?: onDismiss()
                                            }
                                        }
                                    }
                                    break
                                }
                                // Steering needs a deliberate slide: a jittering tap near the ring must not run a sector.
                                if (!steering) {
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (dx * dx + dy * dy < slop * slop) continue
                                }
                                val center = wheelCenter(
                                    if (state.anchor.isSpecified) state.anchor else Offset(size.width / 2f, size.height / 2f),
                                    base.toPx(), RADIAL_BUTTON_DP.toPx(), marginPx, size.width.toFloat(), size.height.toFloat(),
                                )
                                val index = radialSector(count, center, change.position, deadZone)
                                if (index != null) {
                                    steering = true
                                    state.steering = true
                                    if (state.active != index) state.active = index
                                } else if (steering) {
                                    // Slid back into the middle: the centre is the cancel button again.
                                    state.active = null
                                }
                                // A steering finger owns the stream: no child under it starts a press on the way.
                                if (steering) change.consume()
                            }
                        } finally {
                            state.steering = false
                        }
                    }
                },
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
                ringActions.forEachIndexed { index, action ->
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
                                        // "More" does not close the menu: it swaps the ring for the list of the rest.
                                        if (WheelLayout.isMore(actions.size, index)) {
                                            state.overflow = true
                                        } else {
                                            onActionChosen()
                                            action.onClick()
                                        }
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
                val over = state.active?.let(ringActions::getOrNull)
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
                // With a steering or dragging finger on the ring, the name of the action it is over is the important
                // thing; it sits under the central button, where the finger does not cover it.
                if ((dragSelect && state.dragging) || state.steering) {
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
                // "More" replaces the ring with a scrollable list of the same buttons, so every action stays one
                // tap away and TalkBack can walk them linearly. The list uses the wheel's own tokens.
                if (state.overflow) {
                    val rest = remember(actions, ring) {
                        WheelLayout.overflowIndices(actions.size).mapNotNull { actions.getOrNull(it) }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 300.dp)
                            .heightIn(max = 420.dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                            if (title != null) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            rest.forEach { action ->
                                Surface(onClick = { onActionChosen(); action.onClick() }, color = Color.Transparent) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Icon(
                                            action.icon,
                                            null,
                                            tint = action.tint ?: if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(action.label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
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
        Box(contentAlignment = Alignment.Center) {
            Icon(action.icon, null, Modifier.size(if (active) 28.dp else 24.dp), tint = action.tint ?: content)
        }
    }
}

/**
 * While the finger stays down after the long press, this steers the wheel: once the item's own gesture has opened it
 * (the same gesture, so no second long-press timer competes), the drag updates the sector under the finger and the
 * lift runs it. Nothing is consumed before that, so taps and scrolling behave as before.
 */
@Composable
fun Modifier.wheelGesture(
    key: Any,
    enabled: Boolean,
    /** True while the wheel is open and waiting for this finger, set by the long press. */
    wheelOpen: () -> Boolean,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    if (!enabled) return this
    val coords = remember(key) { AtomicReference<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { if (it.isAttached) coords.set(it) }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var tracking = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Also on a release without a move: the wheel then stays open, tappable.
                        if (tracking || wheelOpen()) onEnd()
                        break
                    }
                    if (!tracking && wheelOpen()) tracking = true
                    if (tracking) {
                        change.consume()
                        coords.get()?.takeIf { it.isAttached }?.localToWindow(change.position)?.let(onMove)
                    }
                }
            }
        }
}
