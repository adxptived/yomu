package app.yomu.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import app.yomu.domain.reader.NavigatorSeek
import app.yomu.domain.reader.NavigatorSide

/**
 * Optional vertical page slider on one side of the reader.
 *
 * It is a committed seek control, not a live one: dragging only moves the local thumb, and the position is applied
 * when the finger is lifted. A reading position that arrives during the drag therefore cannot pull the thumb back,
 * which is what made the bottom slider jump while images loaded. TalkBack treats it as a progress bar and can set it.
 */
@Composable
internal fun VerticalNavigator(
    side: NavigatorSide,
    progress: Float,
    current: Int,
    total: Int,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (side == NavigatorSide.OFF) return
    var seek by remember { mutableStateOf(NavigatorSeek()) }
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.reader_navigator_position, current.coerceAtLeast(1), total.coerceAtLeast(1))
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .width(48.dp)
            .height(240.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(seek.thumb(progress), 0f..1f)
                setProgress { value ->
                    onCommit(value)
                    true
                }
            }
            .pointerInput(side) {
                detectDragGestures(
                    onDragStart = { seek = seek.start(progress) },
                    onDrag = { change, drag ->
                        change.consume()
                        val height = size.height.coerceAtLeast(1).toFloat()
                        // The track starts at the top: dragging down moves forward in the book.
                        seek = seek.drag(seek.thumb(progress) + drag.y / height)
                    },
                    onDragEnd = {
                        val released = seek.release()
                        seek = released.state
                        released.commit?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCommit(it)
                        }
                    },
                    onDragCancel = { seek = seek.cancel() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            val shown = seek.thumb(progress).coerceIn(0f, 1f)
            val cx = size.width / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(cx - 2f, 0f),
                size = Size(4f, size.height),
                cornerRadius = CornerRadius(2f, 2f),
            )
            val y = shown * (size.height - 20f) + 10f
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 12f, center = Offset(cx, y))
            drawCircle(accent, radius = 9f, center = Offset(cx, y))
            drawCircle(Color.White, radius = 3f, center = Offset(cx, y))
        }
    }
}

/** Alignment of the navigator for its [side]. */
internal fun navigatorAlignment(side: NavigatorSide): Alignment =
    if (side == NavigatorSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd

/** Page number a navigator commit should open, for a 0..1 fraction and the book's page count. */
internal fun navigatorPage(fraction: Float, pages: Int): Int = app.yomu.domain.reader.ReaderNavigator.pageAt(fraction, pages)
