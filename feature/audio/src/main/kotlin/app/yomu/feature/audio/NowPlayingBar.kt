package app.yomu.feature.audio

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.components.CoverImage
import java.io.File

/**
 * The mini player above the navigation bar while an audiobook is loaded: cover, title, chapter, play/pause, stop and
 * a progress line that can be dragged. Tapping the card opens the full player.
 */
@Composable
fun NowPlayingBar(
    state: PlayerSnapshot,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs
    var dragging by remember(state.bookId, state.track) { mutableStateOf(false) }
    var value by remember(state.bookId, state.track) { mutableFloatStateOf(0f) }
    val actual = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    LaunchedEffect(actual, dragging) { if (!dragging) value = actual }
    val artwork = remember(state.artworkUri) {
        state.artworkUri?.let { uri -> runCatching { File(Uri.parse(uri).path.orEmpty()) }.getOrNull() }?.takeIf { it.path.isNotBlank() }
    }

    Surface(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 10.dp, top = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(artwork, state.bookTitle, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        state.bookTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.trackTitle.ifBlank { state.bookTitle },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggle) {
                    if (state.playWhenReady) Icon(Icons.Default.Pause, stringResource(R.string.audio_pause))
                    else Icon(Icons.Default.PlayArrow, stringResource(R.string.audio_play))
                }
                IconButton(onClick = onStop) { Icon(Icons.Default.Close, stringResource(R.string.audio_stop)) }
            }
            val seekLabel = stringResource(R.string.audio_seek)
            Slider(
                value = value,
                onValueChange = { dragging = true; value = it },
                onValueChangeFinished = {
                    dragging = false
                    if (duration > 0) onSeek((value * duration).toLong())
                },
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp)
                    .semantics { contentDescription = seekLabel },
            )
        }
    }
}
