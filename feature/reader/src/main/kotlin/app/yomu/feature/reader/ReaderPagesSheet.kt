package app.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
    thumb: @Composable (Int) -> Unit,
) {
    YomuSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.reader_pages))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        ) {
            items(count, key = { it }) { index ->
                val selected = index == current
                Column(Modifier.clickable { onPick(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { thumb(index) }
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
