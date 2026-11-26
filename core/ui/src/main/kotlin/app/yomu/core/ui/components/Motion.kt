package app.yomu.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

/** The "Reduce motion" setting: decorative animations are skipped when it is on (provided by the app root). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The "Hide covers" setting: covers show only their generated placeholder (provided by the app root). */
val LocalHideCovers = androidx.compose.runtime.compositionLocalOf { false }

/** Animates insertions, removals and moves of a list row, unless motion is reduced. */
@Composable
fun LazyItemScope.motionItem(): Modifier = if (LocalReduceMotion.current) Modifier else Modifier.animateItem()

/** Animates insertions, removals and moves of a grid cell, unless motion is reduced. */
@Composable
fun LazyGridItemScope.motionItem(): Modifier = if (LocalReduceMotion.current) Modifier else Modifier.animateItem()

/** [spec], or an instant change when motion is reduced. */
@Composable
fun <T> motionSpec(spec: FiniteAnimationSpec<T> = spring()): FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec

/** Float animation that jumps straight to the target when motion is reduced (progress bars, rotations). */
@Composable
fun animateMotionFloat(target: Float, label: String = "motion"): State<Float> =
    animateFloatAsState(target, motionSpec(tween(durationMillis = 350)), label = label)

/** Shows [content] with an expand-and-fade (sections, filter groups); without animation when motion is reduced. */
@Composable
fun ExpandVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduce) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = if (reduce) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        content = content,
    )
}

/** Cross-fades (with a slight zoom) between states such as an icon of a download state; instant when motion is reduced. */
@Composable
fun <S> MotionContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String = "content",
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith fadeOut(tween(120))
            }
        },
        contentAlignment = Alignment.Center,
        label = label,
        content = content,
    )
}

/**
 * A target that answers a press with a short, local change of tone (and an optional tiny scale) that returns when the
 * finger lifts or the gesture is cancelled (B3). Only the pressed target changes: no layout moves, focus indication and
 * the Material ripple stay, and reduced motion keeps an instant tone without scaling. Long press stays available for TalkBack.
 */
@Composable
fun PulseClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    shape: Shape = RectangleShape,
    /** A press also shrinks the target a little; off for rows and other full-width actions. */
    scaleOnPress: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressPulse(interaction, enabled = enabled, scaleOnPress = scaleOnPress, pulseShape = shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = role,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        content = { content() },
    )
}

/**
 * The same press tone for a target that keeps its own gesture (`selectable`, a custom clickable): pass the interaction
 * source the gesture uses. Draws behind the content, so text and icons stay crisp.
 */
@Composable
fun Modifier.pressPulse(
    interaction: InteractionSource,
    enabled: Boolean = true,
    scaleOnPress: Boolean = false,
    pulseShape: Shape = RectangleShape,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val active = pressed && enabled
    val duration = if (active) 75 else 150
    val tone by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = motionSpec(tween(durationMillis = duration)),
        label = "press-pulse-tone",
    )
    val factor by animateFloatAsState(
        targetValue = if (active && scaleOnPress && !reduce) 0.97f else 1f,
        animationSpec = motionSpec(tween(durationMillis = duration)),
        label = "press-pulse-scale",
    )
    return this
        .graphicsLayer {
            scaleX = factor
            scaleY = factor
            shape = pulseShape
            clip = pulseShape != RectangleShape
        }
        .background(tone)
}

/**
 * Icon of an on/off state (a heart, a bookmark) that pops when it turns on: a short scale bounce and a colour fade.
 * Plain when motion is reduced.
 */
@Composable
fun PopIcon(
    on: Boolean,
    onIcon: ImageVector,
    offIcon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTint: Color = LocalContentColor.current,
    offTint: Color = LocalContentColor.current,
) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    // Only a change pops: the first composition (and returning to the screen) shows the icon as it is.
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(on) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (reduce || !on) { scale.snapTo(1f); return@LaunchedEffect }
        scale.snapTo(0.6f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
    }
    val tint by animateColorAsState(if (on) onTint else offTint, motionSpec(tween(250)), label = "pop-tint")
    Icon(if (on) onIcon else offIcon, contentDescription, modifier.scale(scale.value), tint = tint)
}
