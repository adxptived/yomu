package app.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yomu.core.ui.components.SheetTitle
import app.yomu.core.ui.components.YomuSheet
import app.yomu.feature.reader.manga.MangaController
import app.yomu.feature.reader.pdf.PdfController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Grid of page thumbnails for PDF and image books; a tap jumps to the page. */
@Composable
internal fun PagesSheet(
    count: Int,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** Pages with a bookmark, drawn as a mark over the thumbnail. */
    marked: Set<Int> = emptySet(),
    chapter: Long = -1,
    export: PageBatchExportState = PageBatchExportState(),
    onStartSelection: () -> Unit,
    onExport: (IntArray) -> Unit,
    thumb: @Composable (Int) -> Unit,
) {
    var onlyMarked by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable(chapter) { mutableStateOf(false) }
    var selectedPages by rememberSaveable(chapter) { mutableStateOf(intArrayOf()) }
    val exportResult = export.result.takeIf { export.chapter == chapter }
    LaunchedEffect(chapter, exportResult) {
        exportResult?.let {
            selectedPages = it.failed.toIntArray()
            selecting = it.failed.isNotEmpty()
        }
    }
    val pages = if (onlyMarked) marked.sorted() else (0 until count).toList()
    // The grid opens on the page being read, so a 300-page scan does not start at its cover.
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = current.coerceIn(0, (count - 1).coerceAtLeast(0)))
    YomuSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.reader_pages))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = !export.running,
                onClick = {
                    if (!selecting) onStartSelection()
                    selecting = !selecting
                    selectedPages = intArrayOf()
                },
            ) { Text(stringResource(if (selecting) R.string.reader_page_select_cancel else R.string.reader_page_select)) }
            if (selecting) {
                TextButton(
                    enabled = selectedPages.isNotEmpty() && !export.running,
                    onClick = { onExport(selectedPages) },
                ) { Text(stringResource(R.string.reader_page_save_selected, selectedPages.size)) }
            }
        }
        val exportStatus = when {
            export.running -> stringResource(R.string.reader_page_saving_selected)
            exportResult != null && exportResult.failed.isEmpty() ->
                stringResource(R.string.reader_page_batch_saved, exportResult.saved)
            exportResult != null -> stringResource(
                R.string.reader_page_batch_partial,
                exportResult.saved,
                exportResult.failed.take(12).joinToString { (it + 1).toString() } +
                    if (exportResult.failed.size > 12) "…" else "",
            )
            else -> null
        }
        if (exportStatus != null) {
            Text(
                exportStatus,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.reader_page_n, current + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (marked.isNotEmpty()) {
                FilterChip(
                    selected = onlyMarked,
                    onClick = { onlyMarked = !onlyMarked },
                    label = { Text(stringResource(R.string.reader_pages_marked)) },
                    leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.padding(0.dp)) },
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        ) {
            if (pages.isEmpty()) {
                item { Text(stringResource(R.string.reader_pages_marked_empty), Modifier.padding(12.dp)) }
            }
            items(pages, key = { it }) { index ->
                val selected = if (selecting) index in selectedPages else index == current
                val action = if (selecting) {
                    Modifier.selectable(selected, enabled = !export.running) {
                        selectedPages = if (selected) selectedPages.filterNot { it == index }.toIntArray() else selectedPages + index
                    }
                } else {
                    Modifier.clickable { onPick(index) }
                }
                Column(action, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            thumb(index)
                            if (index in marked) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    stringResource(R.string.reader_page_bookmarked),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Small preview of a comic page, decoded by Coil. */
@Composable
internal fun MangaPageThumb(controller: MangaController, index: Int) {
    val file by produceState<File?>(null, controller, index) {
        value = withContext(Dispatchers.IO) { runCatching { controller.file(index) }.getOrNull() }
    }
    val f = file
    if (f != null) {
        AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Small preview rendered by pdfium; cheaper than a full page because the bitmap is only 140 dp wide. */
@Composable
internal fun PdfPageThumb(controller: PdfController, index: Int) {
    val width = with(LocalDensity.current) { 140.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, controller, index, width) {
        value = runCatching { controller.bitmap(index, width) }.getOrNull()?.asImageBitmap()
    }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
