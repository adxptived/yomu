package app.yomu.feature.reader.manga

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yomu.domain.model.MangaBackground
import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.ScaleType
import app.yomu.domain.model.StartZoomAnchor
import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.reader.ImageSize
import app.yomu.domain.reader.MangaFit
import app.yomu.domain.reader.MangaPage
import app.yomu.domain.reader.ReaderInput
import app.yomu.domain.reader.ReaderPoint
import app.yomu.domain.reader.ReaderTapAction
import app.yomu.domain.reader.WidePageRotation
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.ZoomFocalPoint
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File

interface MangaListener {
    fun onMenu() {}
    fun onPageChanged(page: Int) {}
    fun onLongPress(page: Int) {}

    /** A long press at [windowPosition] (window coordinates, for the long-press menu); by default [onLongPress]. */
    fun onLongPressAt(page: Int, windowPosition: Offset) = onLongPress(page)
    fun onChapterEdge(next: Boolean) {}
    fun onFinished() {}
    fun onBlockTap(page: Int, block: OverlayBlock) {}
}

/** Pages fetched at the same time by the preloader. */
private const val PRELOAD_PARALLEL = 3

/** Queued preloads this far behind the reading position are dropped (the reader jumped elsewhere). */
private const val PRELOAD_SLACK = 2

/** Pages shown by the pager: optional previous-chapter page, the images (or their halves), then an end page. */
private class Layout(
    val count: Int,
    val hasPrev: Boolean,
    val spreads: List<IntArray>?,
    val virtual: List<app.yomu.domain.reader.MangaPage>? = null,
    val split: app.yomu.domain.reader.PageSplit = app.yomu.domain.reader.PageSplit.OFF,
) {
    val offset = if (hasPrev) 1 else 0
    val imageSlots = spreads?.size ?: virtual?.size ?: count
    val total = offset + imageSlots + 1
    fun slotOf(page: Int): Int = offset + (
        spreads?.indexOfFirst { page in it }?.coerceAtLeast(0)
            ?: virtual?.let { app.yomu.domain.reader.MangaPaging.virtualIndexOf(it, page, split) }
            ?: page
        )
    fun pageOf(slot: Int): Int? {
        val i = slot - offset
        if (i < 0 || i >= imageSlots) return null
        return spreads?.get(i)?.first() ?: virtual?.getOrNull(i)?.physical ?: i
    }

    /** The half shown at [slot], or null when the slot is a whole page. */
    fun halfOf(slot: Int): app.yomu.domain.reader.MangaPage? = virtual?.getOrNull(slot - offset)
}

@Composable
fun MangaReaderView(
    controller: MangaController,
    listener: MangaListener,
    landscape: Boolean,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    modifier: Modifier = Modifier,
    /** Multiplier for page-turn animations from the common reader settings. */
    animationSpeed: Float = 1f,
) {
    val s = controller.settings
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    // Preloads the next pages, a few at a time and nearest first. Turning a page does not restart fetches in flight;
    // pages left far behind are skipped, failed ones are tried again when the reader gets near them.
    LaunchedEffect(controller) {
        val loader = context.imageLoader
        val gate = Semaphore(PRELOAD_PARALLEL)
        val started = HashSet<Int>()
        snapshotFlow { controller.page to controller.preloadAhead }.collect { (page, ahead) ->
            for (i in page + 1..(page + ahead).coerceAtMost(controller.count - 1)) {
                if (!started.add(i)) continue
                launch {
                    val ok = gate.withPermit {
                        val now = controller.page
                        if (i <= now || i > now + controller.preloadAhead + PRELOAD_SLACK) return@withPermit false
                        val file = controller.file(i)
                        // A tall page is preloaded under the same decode budget as the one on screen.
                        val target = readPageSize(file)?.let {
                            MangaFit.decodeSize(it.width, it.height, metrics.widthPixels, metrics.heightPixels, controller.settings.scale)
                        }
                        runCatching { loader.enqueue(request(context, file, controller.settings.cropBorders, target = target)) }.isSuccess
                    }
                    if (!ok) started.remove(i)
                }
            }
        }
    }
    var zonesVisible by remember { mutableStateOf(false) }
    LaunchedEffect(controller.tapZonesHint) {
        if (controller.tapZonesHint > 0) {
            zonesVisible = true
            delay(3_000)
            zonesVisible = false
        }
    }
    Box(modifier.fillMaxSize().background(Color(s.background.argb))) {
        if (s.direction == MangaDirection.WEBTOON) {
            Webtoon(controller, listener, hasPrevChapter, hasNextChapter, animationSpeed)
        } else {
            val useSpreads = landscape && s.dualPageLandscape && s.direction != MangaDirection.VERTICAL
            val spreads = if (useSpreads) app.yomu.domain.reader.MangaPaging.spreads(controller.count, s.coverAlone) else null
            // Split pages and spreads are alternatives: a spread already divides the screen, and a half of a page
            // inside a spread would be unreadable.
            val split = if (!useSpreads) s.splitPages else app.yomu.domain.reader.PageSplit.OFF
            val virtual = if (split != app.yomu.domain.reader.PageSplit.OFF) {
                app.yomu.domain.reader.MangaPaging.virtualPages(controller.count, split)
            } else {
                null
            }
            Paged(controller, listener, Layout(controller.count, hasPrevChapter, spreads, virtual, split), hasNextChapter, animationSpeed)
        }
        AnimatedVisibility(zonesVisible && s.tapZones != TapZoneLayout.DISABLED, enter = fadeIn(), exit = fadeOut()) {
            TapZonesOverlay(s.tapZones, s.zonesInvertedX, s.zonesInvertedY, rtl = s.direction == MangaDirection.RTL)
        }
        if (s.showPageNumber && controller.count > 0) {
            Text(
                "${controller.page + 1} / ${controller.count}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Text colour that stays readable on the chosen page background. */
private fun onBackground(s: MangaSettings): Color = if (s.background == MangaBackground.WHITE) Color(0xFF202020) else Color.White

internal fun request(
    context: android.content.Context,
    file: File,
    crop: Boolean,
    half: MangaPage? = null,
    rotation: WidePageRotation = WidePageRotation.NONE,
    target: ImageSize? = null,
): ImageRequest =
    ImageRequest.Builder(context).data(file).apply {
        val transforms = buildList {
            if (crop) add(CropBordersTransformation())
            if (half != null) add(SplitPageTransformation(secondHalf = half.isSecondHalf))
            if (half == null && rotation != WidePageRotation.NONE) add(RotateWideTransformation(rotation))
        }
        if (transforms.isNotEmpty()) transformations(transforms)
        // A very tall strip is subsampled to the budget; without a known image size Coil keeps its own sizing.
        if (target != null) size(Size(target.width, target.height))
    }.build()

@Composable
private fun Paged(controller: MangaController, listener: MangaListener, layout: Layout, hasNext: Boolean, animationSpeed: Float) {
    val s = controller.settings
    val rtl = s.direction == MangaDirection.RTL
    val pager = rememberPagerState(initialPage = layout.slotOf(controller.page)) { layout.total }
    val scope = rememberCoroutineScope()
    val animate by rememberUpdatedState(s.pageAnimation)
    val speed by rememberUpdatedState(animationSpeed.coerceIn(0.25f, 3f))
    val tween = tween<Float>((320 / speed).toInt().coerceIn(60, 1_200))
    suspend fun go(slot: Int) = if (animate) pager.animateScrollToPage(slot, animationSpec = tween) else pager.scrollToPage(slot)
    LaunchedEffect(layout.spreads?.size, layout.virtual?.size, layout.hasPrev) { pager.scrollToPage(layout.slotOf(controller.page)) }
    LaunchedEffect(Unit) {
        controller.pageRequests.collect { target ->
            when {
                target < 0 -> if (layout.hasPrev) go(0) else listener.onChapterEdge(false)
                target >= controller.count -> go(layout.total - 1)
                else -> {
                    val slot = layout.slotOf(target)
                    if (slot == pager.currentPage && layout.spreads != null) {
                        val next = if (target > controller.page) pager.currentPage + 1 else pager.currentPage - 1
                        go(next.coerceIn(0, layout.total - 1))
                    } else if (kotlin.math.abs(slot - pager.currentPage) > 1) {
                        // Long jumps (slider, table of contents) never animate through every page.
                        pager.scrollToPage(slot)
                    } else {
                        go(slot)
                    }
                }
            }
        }
    }
    var endReported by remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { slot ->
            layout.pageOf(slot)?.let { controller.page = it; listener.onPageChanged(it) }
            if (slot == layout.total - 1) {
                // The end slot is not a page: report the last page so the saved position is not one behind.
                val last = (controller.count - 1).coerceAtLeast(0)
                if (controller.page != last) {
                    controller.page = last
                    listener.onPageChanged(last)
                }
                if (!hasNext && !endReported) {
                    endReported = true
                    listener.onFinished()
                }
            } else {
                endReported = false
            }
        }
    }
    val onTap: (Offset, IntSize) -> Unit = { pos, size ->
        val point = ReaderPoint(pos.x / size.width, pos.y / size.height)
        val action = ReaderInput.action(s.tapZones, s.zonesInvertedX, s.zonesInvertedY, rtl, point)
        // A locked page is view-only: taps open the menu instead of leaving it, and the pager below does not swipe.
        when (if (s.zoomLock) ReaderTapAction.MENU else action) {
            ReaderTapAction.MENU -> listener.onMenu()
            ReaderTapAction.NEXT -> scope.launch { go((pager.currentPage + 1).coerceAtMost(layout.total - 1)) }
            ReaderTapAction.PREV -> scope.launch {
                if (pager.currentPage == 0 && !layout.hasPrev) listener.onChapterEdge(false) else go((pager.currentPage - 1).coerceAtLeast(0))
            }
        }
    }
    val content: @Composable (Int) -> Unit = { slot ->
        when {
            layout.hasPrev && slot == 0 -> EdgePage(next = false, available = true, onGo = { listener.onChapterEdge(false) }, onMenu = listener::onMenu)
            slot == layout.total - 1 -> EdgePage(next = true, available = hasNext, onGo = { listener.onChapterEdge(true) }, onMenu = listener::onMenu)
            else -> {
                val i = slot - layout.offset
                val half = layout.halfOf(slot)
                val group = layout.spreads?.get(i) ?: intArrayOf(i)
                when {
                    half != null -> PageImage(controller, listener, half.physical, Modifier.fillMaxSize(), onTap, half = half)
                    group.size == 2 -> {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            val order = if (rtl) group.reversedArray() else group
                            order.forEach { p -> PageImage(controller, listener, p, Modifier.weight(1f).fillMaxHeight(), onTap) }
                        }
                    }
                    else -> PageImage(controller, listener, group[0], Modifier.fillMaxSize(), onTap)
                }
            }
        }
    }
    // A locked page keeps its position: swiping must not turn the page while the zoom stays as the user set it.
    val swipe = !s.zoomLock
    if (s.direction == MangaDirection.VERTICAL) {
        VerticalPager(
            state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1,
            userScrollEnabled = swipe, key = { it },
        ) { content(it) }
    } else {
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(), reverseLayout = rtl, beyondViewportPageCount = 1,
            userScrollEnabled = swipe, key = { it },
        ) { content(it) }
    }
}

@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun PageImage(
    controller: MangaController,
    listener: MangaListener,
    index: Int,
    modifier: Modifier,
    onTap: (Offset, IntSize) -> Unit,
    half: app.yomu.domain.reader.MangaPage? = null,
) {
    val s = controller.settings
    val context = LocalContext.current
    var file by remember(index) { mutableStateOf<File?>(null) }
    var pageSize by remember(index) { mutableStateOf<ImageSize?>(null) }
    var error by remember(index) { mutableStateOf<Throwable?>(null) }
    var attempt by remember(index) { mutableIntStateOf(0) }
    LaunchedEffect(index, attempt) {
        error = null
        loadPage(controller, index, onFile = { file = it; pageSize = readPageSize(it) }, onError = { error = it })
    }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier.onGloballyPositioned { coords = it }, contentAlignment = Alignment.Center) {
        val boxSize = IntSize(constraints.maxWidth.coerceAtLeast(1), constraints.maxHeight.coerceAtLeast(1))
        size = boxSize
        val f = file
        when {
            error != null -> PageError(error, onBackground(s), onRetry = { attempt++ }, onMenu = listener::onMenu)
            f == null -> CircularProgressIndicator(color = onBackground(s).copy(alpha = 0.7f))
            else -> {
                val rtl = s.direction == MangaDirection.RTL
                val rotation = MangaFit.widePageRotation(
                    pageSize?.width ?: 0,
                    pageSize?.height ?: 0,
                    s.rotateWidePages,
                    rtl,
                )
                // A split page is half as wide, and the decode budget follows the viewport the rotated page occupies.
                val image = pageSize?.let { if (half != null) ImageSize(it.width / 2, it.height) else it }
                val viewport = if (rotation == WidePageRotation.NONE) boxSize else IntSize(boxSize.height, boxSize.width)
                val target = image?.let { MangaFit.decodeSize(it.width, it.height, viewport.width, viewport.height, s.scale) }
                val zoomable = rememberZoomableState(zoomSpec = ZoomSpec(maximum = ZoomLimit(factor = 6f)))
                val zoomState = rememberZoomableImageState(zoomable)
                if (s.zoomLock) {
                    // Keep the zoom of the page being read; a page that comes into view opens at the same level.
                    LaunchedEffect(zoomable, index) {
                        snapshotFlow { zoomable.contentTransformation.scale.scaleX }.collect { scale ->
                            if (scale > 1.01f) controller.lockedZoom = scale
                        }
                    }
                    LaunchedEffect(zoomable, index) {
                        val target = controller.lockedZoom
                        val current = zoomable.contentTransformation.scale.scaleX
                        if (target > 1.01f && current < target - 0.01f) zoomable.zoomBy(target / current)
                    }
                } else {
                    // The start zoom is applied once when the page appears. A pinch, a pan or turning the page and
                    // coming back never has the setting fight the reader's own gesture.
                    var opened by remember(index, half) { mutableStateOf(false) }
                    LaunchedEffect(zoomable, image, boxSize, s.startZoom, s.smartFit, s.startZoomAnchor) {
                        if (opened || image == null) return@LaunchedEffect
                        val smart = if (s.smartFit && s.scale == ScaleType.FIT_SCREEN) {
                            MangaFit.smartZoom(image.width, image.height, boxSize.width, boxSize.height)
                        } else {
                            1f
                        }
                        val zoom = maxOf(s.startZoom, smart).coerceIn(1f, MangaFit.MAX_SMART_ZOOM)
                        if (zoom > 1.01f) {
                            // Wait one frame so the image is laid out and the zoom has a size to work with.
                            withFrameNanos { }
                            zoomable.zoomTo(zoom, ZoomFocalPoint.viewportCenter())
                            val travel = boxSize.width * (zoom - 1f) / 2f
                            val startSign = if (rtl) -1f else 1f
                            when (s.startZoomAnchor) {
                                StartZoomAnchor.START -> zoomable.panBy(Offset(startSign * travel, 0f))
                                StartZoomAnchor.END -> zoomable.panBy(Offset(-startSign * travel, 0f))
                                StartZoomAnchor.CENTER -> Unit
                            }
                        }
                        opened = true
                    }
                }
                val filter = ImageFilters.matrix(s)?.let { ColorFilter.colorMatrix(it) }
                ZoomableAsyncImage(
                    model = request(context, f, s.cropBorders, half, rotation, target),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    state = zoomState,
                    colorFilter = filter,
                    contentScale = when (s.scale) {
                        ScaleType.FIT_SCREEN -> ContentScale.Fit
                        ScaleType.FIT_WIDTH -> ContentScale.FillWidth
                        ScaleType.FIT_HEIGHT -> ContentScale.FillHeight
                        ScaleType.ORIGINAL -> ContentScale.None
                    },
                    onClick = { pos -> onTap(pos, size) },
                    onLongClick = { pos -> listener.onLongPressAt(index, coords?.takeIf { it.isAttached }?.localToWindow(pos) ?: pos) },
                )
                val blocks = controller.overlays[index]
                if (controller.overlayVisible && !blocks.isNullOrEmpty()) {
                    // The replacement (coordinateSystem.contentBounds) is still experimental in telephoto 0.17.
                    @Suppress("DEPRECATION")
                    val bounds = zoomState.zoomableState.transformedContentBounds
                    val density = LocalDensity.current
                    if (bounds.width > 0f) {
                        Box(Modifier.fillMaxSize()) {
                            blocks.forEach { b ->
                                val x = bounds.left + b.left * bounds.width
                                val y = bounds.top + b.top * bounds.height
                                val w = (b.right - b.left) * bounds.width
                                val h = (b.bottom - b.top) * bounds.height
                                with(density) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.93f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.offset(x.toDp(), y.toDp()).size(w.toDp().coerceAtLeast(24.dp), h.toDp().coerceAtLeast(16.dp))
                                            .clickable { listener.onBlockTap(index, b) },
                                    ) {
                                        Text(
                                            b.translated ?: b.original,
                                            color = Color.Black,
                                            fontSize = fitFont(b.translated ?: b.original, w, h, density.density).sp,
                                            lineHeight = (fitFont(b.translated ?: b.original, w, h, density.density) * 1.1f).sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(2.dp),
                                        )
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

/** Rough font size that fits [text] into a box of [w]x[h] px. */
private fun fitFont(text: String, w: Float, h: Float, density: Float): Float {
    val area = (w * h) / (density * density)
    val chars = text.length.coerceAtLeast(1)
    val size = kotlin.math.sqrt(area / chars / 0.6f)
    return size.coerceIn(7f, 18f)
}

/** Real pixel size of a page, read from its header without decoding the bitmap. */
private suspend fun readPageSize(file: File): ImageSize? = withContext(Dispatchers.IO) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    if (options.outWidth > 0 && options.outHeight > 0) ImageSize(options.outWidth, options.outHeight) else null
}

/** Loads the file of page [index]; leaving the page (cancellation) is not reported as a page error. */
private suspend fun loadPage(controller: MangaController, index: Int, onFile: suspend (File) -> Unit, onError: (Throwable) -> Unit) {
    try {
        onFile(controller.file(index))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError(e)
    }
}

@Composable
private fun PageError(error: Throwable?, color: Color, onRetry: () -> Unit, onMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onMenu() } }.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.BrokenImage, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.manga_page_error),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        val detail = error?.let { app.yomu.core.ui.components.errorMessage(it) }
        if (!detail.isNullOrBlank()) {
            Text(detail, color = color.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Refresh, null, tint = color, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(app.yomu.core.ui.R.string.retry), color = color)
        }
    }
}

@Composable
private fun EdgePage(next: Boolean, available: Boolean, onGo: () -> Unit, onMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF121212)).pointerInput(Unit) { detectTapGestures { onMenu() } }.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (next) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.manga_chapter_finished), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            if (available) {
                Button(onClick = onGo, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manga_next_chapter))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(stringResource(R.string.manga_no_next), color = Color.LightGray, textAlign = TextAlign.Center)
            }
        } else {
            OutlinedButton(onClick = onGo, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.manga_prev_chapter), color = Color.White)
            }
        }
    }
}

@Composable
private fun Webtoon(controller: MangaController, listener: MangaListener, hasPrev: Boolean, hasNext: Boolean, animationSpeed: Float) {
    val s = controller.settings
    val context = LocalContext.current
    val offset = if (hasPrev) 1 else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = controller.page + offset)
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var finishedReported by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val animate by rememberUpdatedState(s.pageAnimation)
    val speed by rememberUpdatedState(animationSpeed.coerceIn(0.25f, 3f))
    suspend fun scrollBy(px: Float) {
        if (animate) state.animateScrollBy(px, tween((300 / speed).toInt().coerceIn(60, 1_200))) else state.scrollBy(px)
    }
    LaunchedEffect(Unit) {
        controller.pageRequests.collect { target ->
            when {
                target < 0 -> listener.onChapterEdge(false)
                target >= controller.count -> state.animateScrollToItem(controller.count + offset)
                target == controller.page + 1 -> scrollBy(state.layoutInfo.viewportSize.height * 0.85f)
                target == controller.page - 1 -> scrollBy(-state.layoutInfo.viewportSize.height * 0.85f)
                else -> state.scrollToItem(target + offset)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val p = (i - offset).coerceIn(0, (controller.count - 1).coerceAtLeast(0))
            controller.page = p
            listener.onPageChanged(p)
            if (i >= controller.count + offset - 1 && !state.canScrollForward) {
                if (!finishedReported) {
                    finishedReported = true
                    listener.onFinished()
                }
            } else if (finishedReported && i < controller.count + offset - 1) {
                finishedReported = false
            }
        }
    }
    val filter = ImageFilters.matrix(s)?.let { ColorFilter.colorMatrix(it) }
    var stripCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { stripCoords = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var previous = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val d = (pressed[0].position - pressed[1].position).getDistance()
                            // Locked, the strip keeps the scale the user chose: no pinch changes it.
                            if (!s.zoomLock && previous > 0f) zoom = (zoom * d / previous).coerceIn(1f, 3f)
                            previous = d
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(s.tapZones, s.zonesInvertedX, s.zonesInvertedY, s.webtoonDoubleTapZoom, zoom > 1.01f, s.zoomLock) {
                detectTapGestures(
                    onTap = { pos ->
                        val point = ReaderPoint(pos.x / size.width, pos.y / size.height)
                        val action = ReaderInput.action(s.tapZones, s.zonesInvertedX, s.zonesInvertedY, false, point)
                        // Locked, the strip only scrolls: a tap opens the menu instead of jumping a screen.
                        when (if (s.zoomLock) ReaderTapAction.MENU else action) {
                            ReaderTapAction.MENU -> listener.onMenu()
                            ReaderTapAction.NEXT -> scope.launch { scrollBy(size.height * 0.8f) }
                            ReaderTapAction.PREV -> scope.launch { scrollBy(-size.height * 0.8f) }
                        }
                    },
                    // Without a double-tap handler single taps fire at once; zoomed in, a double tap always zooms back out.
                    onDoubleTap = if (!s.zoomLock && (s.webtoonDoubleTapZoom || zoom > 1.01f)) { _ -> zoom = if (zoom > 1.01f) 1f else 2f } else null,
                    onLongPress = { pos -> listener.onLongPressAt(controller.page, stripCoords?.takeIf { it.isAttached }?.localToWindow(pos) ?: pos) },
                )
            },
    ) {
        val sidePadding = maxWidth * (s.webtoonSidePadding.coerceIn(0, 30) / 100f)
        LazyColumn(
            state = state,
            // Locked, the strip only moves up and down at the scale already chosen: no sideways panning.
            modifier = Modifier.fillMaxHeight().horizontalScroll(hScroll, enabled = zoom > 1.01f && !s.zoomLock).width(maxWidth * zoom),
            contentPadding = PaddingValues(horizontal = sidePadding * zoom),
            verticalArrangement = Arrangement.spacedBy(if (s.webtoonGaps) 10.dp else 0.dp),
        ) {
            if (hasPrev) item(key = "prev") { Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) { EdgePage(false, true, { listener.onChapterEdge(false) }, listener::onMenu) } }
            items(controller.count, key = { it }) { index ->
                var file by remember(index) { mutableStateOf<File?>(null) }
                var pageSize by remember(index) { mutableStateOf<ImageSize?>(null) }
                var error by remember(index) { mutableStateOf<Throwable?>(null) }
                var attempt by remember(index) { mutableIntStateOf(0) }
                LaunchedEffect(index, attempt) {
                    error = null
                    loadPage(controller, index, onFile = { file = it; pageSize = readPageSize(it) }, onError = { error = it })
                }
                val f = file
                if (error != null) {
                    // A failed page keeps its place in the strip and can be retried (it used to spin forever).
                    Box(Modifier.fillMaxWidth().heightIn(min = 400.dp), contentAlignment = Alignment.Center) {
                        PageError(error, onBackground(s), onRetry = { attempt++ }, onMenu = listener::onMenu)
                    }
                } else if (f == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 400.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = onBackground(s).copy(alpha = 0.7f))
                    }
                } else {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    val target = pageSize?.let {
                        MangaFit.decodeSize(it.width, it.height, widthPx, 0, ScaleType.FIT_WIDTH)
                    }
                    AsyncImage(
                        model = request(context, f, s.cropBorders, target = target),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        colorFilter = filter,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    )
                }
            }
            item(key = "end") { Box(Modifier.fillMaxWidth().heightIn(min = 360.dp)) { EdgePage(true, hasNext, { listener.onChapterEdge(true) }, listener::onMenu) } }
        }
    }
}
