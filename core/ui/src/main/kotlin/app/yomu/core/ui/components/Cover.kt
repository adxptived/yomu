package app.yomu.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.theme.Dimens
import coil3.compose.AsyncImage

/** Main colour of the generated placeholder for [title]; also tints pages of books without a cover image. */
fun coverPlaceholderColor(title: String): Color = Color.hsl(placeholderHue(title), 0.42f, 0.45f)

// Hues from cyan through blue, violet and pink to orange: no greens, in line with the lavender palette.
private fun placeholderHue(title: String): Float = (200f + title.hashCode().mod(180)) % 360f

/**
 * Cover with a generated placeholder behind the image: a colour derived from the title and the title itself, or its
 * initials on thumbnails too narrow for whole words (no "Smok/e" breaks in lists and history).
 */
@Composable
fun CoverImage(model: Any?, title: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val hue = placeholderHue(title)
    val c1 = coverPlaceholderColor(title)
    val c2 = Color.hsl((hue + 25f) % 360f, 0.46f, 0.30f)
    BoxWithConstraints(
        modifier
            .aspectRatio(2f / 3f)
            .clip(MaterialTheme.shapes.small)
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth < 80.dp) {
            Text(
                text = coverInitials(title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        } else {
            Text(
                text = title.take(60),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(Dimens.s),
            )
        }
        if (model != null && !LocalHideCovers.current) {
            AsyncImage(model = model, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/** "Smoke Test Book" -> "ST": first letters of the first two words. */
internal fun coverInitials(title: String): String {
    val words = title.split(' ', '-', '_', '.', ':', ',').filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
    val initials = words.take(2).joinToString("") { it.substring(0, Character.charCount(it.codePointAt(0))).uppercase() }
    return initials.ifEmpty { title.trim().take(1).uppercase() }
}

/** Grid tile: cover, title, author, progress bar and status badge. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    title: String,
    subtitle: String?,
    cover: Any?,
    progress: Float,
    modifier: Modifier = Modifier,
    badge: String? = null,
    selected: Boolean = false,
    /** Choosing several books: every cover shows a checkbox, ticked for the selected ones. */
    selectionMode: Boolean = false,
    finished: Boolean = false,
    coverModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /** Tapping the title opens the details; the cover still opens the book. */
    onInfoClick: (() -> Unit)? = null,
    /** TalkBack labels of the two actions ("Read", "Details"). */
    readLabel: String? = null,
    infoLabel: String? = null,
) {
    val border = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier
    val split = onInfoClick != null
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (split) Modifier else Modifier
                    .combinedClickable(onClickLabel = readLabel, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(title, subtitle?.takeIf { it.isNotBlank() }, badge).joinToString(", ")
                        this.selected = selected
                    },
            )
            .padding(Dimens.xs),
    ) {
        Box(
            Modifier.fillMaxWidth().then(
                if (split) Modifier.combinedClickable(onClickLabel = readLabel, role = Role.Button, onClick = onClick, onLongClick = onLongClick).semantics {
                    contentDescription = listOfNotNull(title, subtitle?.takeIf { it.isNotBlank() }).joinToString(", ")
                    this.selected = selected
                } else Modifier,
            ),
        ) {
            CoverImage(cover, title, coverModifier.fillMaxWidth().then(border))
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            if (selectionMode || finished) {
                val checked = if (selectionMode) selected else finished
                Icon(
                    if (checked) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)).size(22.dp),
                )
            }
        }
        if (progress > 0f && progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).then(
                if (onInfoClick != null) {
                    Modifier.combinedClickable(onClickLabel = infoLabel, role = Role.Button, onClick = onInfoClick, onLongClick = onLongClick).semantics(mergeDescendants = true) {}
                } else {
                    Modifier
                },
            ),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** List row variant of a book card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    title: String,
    subtitle: String?,
    cover: Any?,
    progress: Float,
    modifier: Modifier = Modifier,
    meta: String? = null,
    selected: Boolean = false,
    /** Choosing several books: every row shows a checkbox, ticked for the selected ones. */
    selectionMode: Boolean = false,
    coverModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    /** Tapping the title opens the details; the cover still opens the book. */
    onInfoClick: (() -> Unit)? = null,
    /** TalkBack labels of the two actions ("Read", "Details"). */
    readLabel: String? = null,
    infoLabel: String? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val split = onInfoClick != null
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .then(
                if (split) Modifier else Modifier
                    .combinedClickable(onClickLabel = readLabel, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(title, subtitle?.takeIf { it.isNotBlank() }, meta?.takeIf { it.isNotBlank() }).joinToString(", ")
                        this.selected = selected
                    },
            )
            .heightIn(min = Dimens.touch)
            .padding(horizontal = Dimens.l, vertical = Dimens.s),
        horizontalArrangement = Arrangement.spacedBy(Dimens.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        CoverImage(
            cover,
            title,
            coverModifier.width(56.dp).then(
                if (split) Modifier.combinedClickable(onClickLabel = readLabel, role = Role.Button, onClick = onClick, onLongClick = onLongClick).semantics {
                    contentDescription = title
                    this.selected = selected
                } else Modifier,
            ),
        )
        Column(
            Modifier.weight(1f).then(
                if (onInfoClick != null) {
                    Modifier.combinedClickable(onClickLabel = infoLabel, role = Role.Button, onClick = onInfoClick, onLongClick = onLongClick).semantics(mergeDescendants = true) {}
                } else {
                    Modifier
                },
            ),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (!meta.isNullOrBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp),
                )
            }
        }
        trailing?.invoke()
    }
}
