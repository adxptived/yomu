package app.yomu.feature.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.ui.graphics.vector.ImageVector
import app.yomu.domain.model.ReaderBarAction

/** The icon a configurable bottom-bar command shows for the current state. */
internal fun ReaderBarAction.icon(bookmarked: Boolean, autoScroll: Boolean): ImageVector = when (this) {
    ReaderBarAction.CHAPTERS -> Icons.AutoMirrored.Filled.FormatListBulleted
    ReaderBarAction.SEARCH -> Icons.Default.Search
    ReaderBarAction.DISPLAY -> Icons.Default.Settings
    ReaderBarAction.BOOKMARK -> if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder
    ReaderBarAction.AUTOSCROLL -> if (autoScroll) Icons.Default.Pause else Icons.Default.SlowMotionVideo
}

/** The label a configurable bottom-bar command shows for the current state. */
internal fun ReaderBarAction.label(bookmarked: Boolean, autoScroll: Boolean): Int = when (this) {
    ReaderBarAction.CHAPTERS -> R.string.reader_toc
    ReaderBarAction.SEARCH -> R.string.reader_search
    ReaderBarAction.DISPLAY -> R.string.reader_quick_display
    ReaderBarAction.BOOKMARK -> if (bookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
    ReaderBarAction.AUTOSCROLL -> if (autoScroll) R.string.reader_autoscroll_stop else R.string.reader_autoscroll
}
