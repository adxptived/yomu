package app.yomu.feature.reader.pdf

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yomu.core.common.QuietErrors
import app.yomu.data.book.PdfReflow
import app.yomu.data.book.PdfReflowText
import app.yomu.domain.model.Highlight
import app.yomu.domain.model.HighlightStyle
import app.yomu.domain.model.PdfMode
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.reader.ReaderInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/** Callbacks of the PDF engine. Rects are in root coordinates (px); [onTap] is in viewport fractions. */
interface PdfListener {
    /**
     * A tap at [x], [y] as fractions 0..1 of the reader viewport. The page converts its own gesture
     * position and only calls this for a point inside the viewport, so the zone resolver never sees pixels.
     */
    fun onTap(x: Float, y: Float) {}
    /**
     * A long press that did not land on a text glyph — a scan, a margin or an empty area of a page. [page] is the
     * physical page, [windowPosition] the anchor in window coordinates for the page menu.
     */
    fun onPageLongPress(page: Int, windowPosition: Offset) {}
    fun onSelection(selection: PdfSelection?, bounds: Rect?) {}
    fun onHighlightTap(highlight: Highlight, bounds: Rect) {}
    fun onLink(uri: String) {}
    fun onPageChanged(page: Int) {}
}

/**
 * Calls [onTap] with the fractions of a root point inside [viewport]; a point outside, or a viewport that
 * was not measured yet, is ignored instead of being clamped into a zone.
 */
@Composable
fun PdfReaderView(controller: PdfController, listener: PdfListener, textSettings: TextReaderSettings, landscape: Boolean, modifier: Modifier = Modifier) {
    val settings = controller.settings
    val dual = !settings.reflow && settings.mode == PdfMode.DUAL && landscape
    SideEffect { controller.dualPageLayout = dual }
    LaunchedEffect(settings) { controller.invalidateBitmaps() }
    // The viewport is this whole reader area: page, scroll and reflow taps are measured against it, not
    // against the activity or a single page, so margins, crop, zoom and dual pages keep the same zones.
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val onTap: (Offset) -> Unit = { root ->
        val bounds = viewport
        ReaderInput.normalize(root.x, root.y, bounds.left, bounds.top, bounds.width, bounds.height)
            ?.let { listener.onTap(it.x, it.y) }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }.background(if (settings.night) Color.Black else Color(0xFF2B2B2B))) {
        when {
            settings.reflow -> ReflowView(controller, listener, textSettings, onTap)
            settings.mode == PdfMode.SCROLL -> ScrollView(controller, listener, onTap)
            else -> PagedView(controller, listener, onTap, dual)
        }
    }
}

@Composable
private fun ScrollView(controller: PdfController, listener: PdfListener, onTap: (Offset) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = controller.page)
    var zoom by remember { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    val zoomReset = controller.zoomReset
    LaunchedEffect(zoomReset) {
        zoom = 1f
        hScroll.scrollTo(0)
    }
    PdfListNavigation(state, controller, listener)
    BoxWithConstraints(Modifier.fillMaxSize().pinchToZoom { factor ->
        // Locked, the scroll view keeps the scale the user chose: only vertical scrolling remains.
        if (!controller.settings.zoomLock) zoom = (zoom * factor).coerceIn(1f, 4f)
    }) {
        val width = maxWidth * zoom
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxHeight().horizontalScroll(hScroll, enabled = zoom > 1.01f && !controller.settings.zoomLock).width(width),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(controller.pageCount, key = { it }) { index ->
                PdfPage(controller, index, listener, onTap, Modifier.fillMaxWidth(), renderScale = 1f)
            }
        }
    }
}

@Composable
private fun PagedView(controller: PdfController, listener: PdfListener, onTap: (Offset) -> Unit, dual: Boolean) {
    val count = if (dual) (controller.pageCount + 1) / 2 else controller.pageCount
    val pager = androidx.compose.runtime.key(controller, dual) {
        rememberPagerState(initialPage = if (dual) controller.page / 2 else controller.page) { count }
    }
    var seeking by remember(pager) { mutableStateOf(false) }
    LaunchedEffect(controller, pager) {
        controller.pageRequests.collect { page ->
            seeking = true
            try {
                pager.scrollToPage(if (dual) page / 2 else page)
                controller.page = page
                listener.onPageChanged(page)
            } finally {
                seeking = false
            }
        }
    }
    LaunchedEffect(controller, pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            if (!seeking) {
                val page = if (!dual) settled else controller.page.takeIf { it / 2 == settled } ?: settled * 2
                controller.page = page
                listener.onPageChanged(page)
            }
        }
    }
    // A locked page keeps its position: swiping must not turn the page while the zoom stays as the user set it.
    val zoomReset = controller.zoomReset
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !controller.settings.zoomLock) { p ->
        // "Fit the page" gives every page a fresh zoom state: the temporary pinch and pan are dropped, and a locked
        // page opens again at the fit scale instead of keeping the scale of the page that was reset.
        val zoomState = key(zoomReset) { rememberZoomableState(zoomSpec = ZoomSpec(maximum = ZoomLimit(factor = 5f))) }
        if (controller.settings.zoomLock) {
            // The page under the finger records its zoom; every page that comes into view opens at that level.
            LaunchedEffect(zoomState) {
                snapshotFlow { zoomState.contentTransformation.scale.scaleX }.collect { scale ->
                    if (scale > 1.01f && pager.currentPage == p) controller.lockedZoom = scale
                }
            }
            // Also keyed on the current page: the next page may already be composed with the old zoom.
            LaunchedEffect(zoomState, p, pager.currentPage) {
                val target = controller.lockedZoom
                val current = zoomState.contentTransformation.scale.scaleX
                if (target > 1.01f && current < target - 0.01f) zoomState.zoomBy(target / current)
            }
        }
        Box(Modifier.fillMaxSize().zoomable(zoomState), contentAlignment = Alignment.Center) {
            if (dual) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    PdfPage(controller, p * 2, listener, onTap, Modifier.weight(1f), renderScale = 1.5f)
                    if (p * 2 + 1 < controller.pageCount) PdfPage(controller, p * 2 + 1, listener, onTap, Modifier.weight(1f), renderScale = 1.5f)
                }
            } else if (controller.settings.fitWidth) {
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    PdfPage(controller, p, listener, onTap, Modifier.fillMaxWidth(), renderScale = 2f)
                }
            } else {
                PdfPage(controller, p, listener, onTap, Modifier, renderScale = 2f, fitHeight = true)
            }
        }
    }
}

/** One rendered page with highlight, search and selection overlays. */
@Composable
private fun PdfPage(controller: PdfController, index: Int, listener: PdfListener, onTap: (Offset) -> Unit, modifier: Modifier, renderScale: Float, fitHeight: Boolean = false) {
    val scope = rememberCoroutineScope()
    var ratio by remember(index) { mutableFloatStateOf(1.414f) }
    var bitmap by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var overlay by remember(index) { mutableStateOf<List<Pair<RectF, Color>>>(emptyList()) }
    var hlRects by remember(index) { mutableStateOf<List<Pair<Highlight, List<RectF>>>>(emptyList()) }
    val settings = controller.settings
    val imageFilter = remember(settings.contrast, settings.grayscale) {
        if (settings.contrast == 1f && !settings.grayscale) {
            null
        } else {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    if (settings.grayscale) setToSaturation(0f)
                    val c = settings.contrast
                    if (c != 1f) {
                        val t = (1f - c) * 127.5f
                        timesAssign(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
                    }
                },
            )
        }
    }
    LaunchedEffect(index, settings) { ratio = controller.ratio(index) }
    LaunchedEffect(index, widthPx, settings) {
        if (widthPx > 0) bitmap = runCatching { controller.bitmap(index, (widthPx * renderScale).toInt()).asImageBitmap() }.getOrNull()
    }
    val selection = controller.selection
    val hit = controller.currentHit
    val tts = controller.ttsMark
    LaunchedEffect(index, selection, hit, tts, widthPx, settings) {
        val list = mutableListOf<Pair<RectF, Color>>()
        if (selection != null && selection.page == index) selection.rects.forEach { list += it to Color(0x553F51B5) }
        if (hit != null && hit.page == index) runCatching { controller.pdf.rects(index, hit.start, hit.length) }.getOrDefault(emptyList()).forEach { list += it to Color(0x66FFB300) }
        if (tts != null && tts.first == index) runCatching { controller.pdf.rects(index, tts.second, tts.third - tts.second) }.getOrDefault(emptyList()).forEach { list += it to Color(0x446750A4) }
        overlay = list
    }
    val highlights = controller.highlights
    LaunchedEffect(index, highlights) {
        hlRects = highlights.filter { it.locator.chapter == index && it.locator.offset != null }.map { h ->
            val start = h.locator.offset ?: 0
            val end = h.locator.endOffset ?: (start + h.text.length)
            h to runCatching { controller.pdf.rects(index, start, end - start) }.getOrDefault(emptyList())
        }
    }
    var viewRects by remember(index) { mutableStateOf<List<Triple<RectF, Color, HighlightStyle?>>>(emptyList()) }
    LaunchedEffect(overlay, hlRects, widthPx, ratio) {
        if (widthPx <= 0) return@LaunchedEffect
        val w = widthPx.toFloat()
        val h = w * ratio
        val out = mutableListOf<Triple<RectF, Color, HighlightStyle?>>()
        hlRects.forEach { (hl, rects) -> rects.forEach { out += Triple(controller.toView(index, it, w, h), Color(hl.color.argb).copy(alpha = 0.4f), hl.style) } }
        overlay.forEach { (r, c) -> out += Triple(controller.toView(index, r, w, h), c, null) }
        viewRects = out
    }
    val base = if (fitHeight) modifier.aspectRatio(1f / ratio, matchHeightConstraintsFirst = ratio > 1.3f) else modifier.aspectRatio(1f / ratio)
    Box(
        base
            .background(if (settings.night) Color.Black else Color.White)
            .onGloballyPositioned { coords = it; widthPx = it.size.width }
            .pointerInput(index) {
                detectTapGestures { pos ->
                    scope.launch(QuietErrors) {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val (px, py) = controller.toPage(index, pos.x, pos.y, w, h)
                        controller.links(index).firstOrNull { it.bounds.contains(px, py) }?.let { link ->
                            when {
                                link.page != null -> controller.goTo(link.page!!)
                                link.uri != null -> listener.onLink(link.uri!!)
                            }
                            return@launch
                        }
                        hlRects.firstOrNull { (_, rects) -> rects.any { it.contains(px, py) } }?.let { (hl, _) ->
                            val point = coords?.takeIf { it.isAttached }?.localToWindow(pos) ?: return@launch
                            listener.onHighlightTap(hl, Rect(point, point))
                            return@launch
                        }
                        if (controller.selection != null) {
                            controller.selection = null
                            listener.onSelection(null, null)
                            return@launch
                        }
                        // Only the viewport fractions go to the zone resolver: the gesture position here is
                        // local to the page, which is not the navigation area once the page is zoomed, cropped
                        // or shown beside another page. A drag or pinch never reaches this tap handler.
                        val coordinates = coords?.takeIf { it.isAttached } ?: return@launch
                        onTap(coordinates.localToRoot(pos))
                    }
                }
            }
            .pointerInput(index) {
                var anchor = -1
                var pressWindow = Offset.Unspecified
                var dragged = false
                var hitJob: Job? = null
                var dragJob: Job? = null
                var releaseJob: Job? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        hitJob?.cancel()
                        dragJob?.cancel()
                        releaseJob?.cancel()
                        controller.selection = null
                        dragged = false
                        anchor = -1
                        pressWindow = coords?.takeIf { it.isAttached }?.localToWindow(pos) ?: Offset.Unspecified
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        hitJob = scope.launch(QuietErrors) {
                            val (px, py) = controller.toPage(index, pos.x, pos.y, w, h)
                            // A miss selects nothing and leaves the press to the page menu after the gesture ends.
                            anchor = controller.pdf.charIndexAt(index, px, py, 12f)
                            if (anchor >= 0) controller.selectWord(index, anchor)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val moved = kotlin.math.abs(dragAmount.x) + kotlin.math.abs(dragAmount.y)
                        if (moved > 0f) dragged = true
                        val pos = change.position
                        dragJob?.cancel()
                        dragJob = scope.launch(QuietErrors) {
                            hitJob?.join()
                            if (anchor < 0) return@launch
                            val (px, py) = controller.toPage(index, pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                            val ci = controller.pdf.charIndexAt(index, px, py, 12f)
                            if (ci >= 0) controller.select(index, minOf(anchor, ci), maxOf(anchor, ci) + 1)
                        }
                    },
                    onDragEnd = {
                        releaseJob = scope.launch(QuietErrors) {
                            hitJob?.join()
                            dragJob?.join()
                            val sel = controller.selection
                            if (sel != null && sel.page == index) {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val rects = sel.rects.map { controller.toView(index, it, w, h) }
                                val coordinates = coords?.takeIf { it.isAttached } ?: return@launch
                                val union = rects.fold<RectF, RectF?>(null) { acc, r -> acc?.apply { union(r) } ?: RectF(r) } ?: RectF()
                                val start = coordinates.localToWindow(Offset(union.left, union.top))
                                val end = coordinates.localToWindow(Offset(union.right, union.bottom))
                                listener.onSelection(sel, Rect(start, end))
                                return@launch
                            }
                            // Nothing was selected under the finger: a scan or an empty margin. Only a stationary
                            // press opens the menu — a drag that simply ended on blank space is not a menu gesture.
                            if (dragged || !pressWindow.isSpecified) return@launch
                            listener.onPageLongPress(index, pressWindow)
                        }
                    },
                    onDragCancel = {
                        hitJob?.cancel()
                        dragJob?.cancel()
                        releaseJob?.cancel()
                        controller.selection = null
                    },
                )
            },
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.FillBounds, colorFilter = imageFilter, modifier = Modifier.fillMaxSize())
        } else {
            Text("${index + 1}", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
        }
        if (viewRects.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                viewRects.forEach { (r, c, style) ->
                    when (style) {
                        HighlightStyle.UNDERLINE -> drawRect(c.copy(alpha = 0.9f), Offset(r.left, r.bottom - 3f), Size(r.width(), 3f))
                        HighlightStyle.STRIKETHROUGH -> drawRect(c.copy(alpha = 0.9f), Offset(r.left, r.centerY() - 1.5f), Size(r.width(), 3f))
                        else -> drawRect(c, Offset(r.left, r.top), Size(r.width(), r.height()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflowView(controller: PdfController, listener: PdfListener, s: TextReaderSettings, onTap: (Offset) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = controller.page)
    PdfListNavigation(state, controller, listener)
    val bg = Color(s.background)
    val fg = Color(s.foreground)
    val family = when (s.fontFamily) {
        "sans-serif", "roboto" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().background(bg),
    ) {
        items(controller.pageCount, key = { it }) { index ->
            var pageCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var paragraphs by remember(index) { mutableStateOf<List<String>?>(null) }
            var showPage by remember(index) { mutableStateOf(false) }
            var textError by remember(index) { mutableStateOf(false) }
            LaunchedEffect(index) {
                try {
                    // Geometric reflow from the page's text lines; a scanned page (no text layer)
                    // falls back to the plain-text reconstruction.
                    val pageLines = controller.pdf.lines(index)
                    paragraphs = if (pageLines.isEmpty()) {
                        PdfReflowText.paragraphs(controller.pdf.text(index).orEmpty())
                    } else {
                        val (width, _) = controller.pdf.pageSize(index)
                        PdfReflow.paragraphs(pageLines, width.toFloat())
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { textError = true; paragraphs = emptyList() }
            }
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { pageCoordinates = it }
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { point ->
                                pageCoordinates?.takeIf { it.isAttached }?.localToRoot(point)?.let(onTap)
                            },
                            onLongPress = { point ->
                                pageCoordinates?.takeIf { it.isAttached }?.localToWindow(point)
                                    ?.let { listener.onPageLongPress(index, it) }
                            },
                        )
                    }
                    .padding(horizontal = s.marginHorizontal.dp, vertical = 8.dp),
            ) {
                Text("— ${index + 1} —", color = fg.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                paragraphs?.forEach { p ->
                    Text(
                        p,
                        color = fg,
                        fontFamily = family,
                        fontSize = (16 * s.fontSizePercent / 100f).sp,
                        lineHeight = (16 * s.fontSizePercent / 100f * s.lineHeight).sp,
                        modifier = Modifier.padding(bottom = (s.paragraphSpacing * 16).dp),
                    )
                }
                if (paragraphs != null) {
                    if (paragraphs.orEmpty().isEmpty()) {
                        Text(stringResource(if (textError) R.string.pdf_reflow_text_error else R.string.pdf_reflow_no_text), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        PdfPage(controller, index, listener, onTap, Modifier.fillMaxWidth(), renderScale = 1f)
                    } else {
                        TextButton(onClick = { showPage = !showPage }) {
                            Text(stringResource(if (showPage) R.string.pdf_reflow_hide_page else R.string.pdf_reflow_show_page))
                        }
                        if (showPage) PdfPage(controller, index, listener, onTap, Modifier.fillMaxWidth(), renderScale = 1f)
                    }
                }
            }
        }
    }
}

/** Two-finger pinch detector that leaves single-finger scrolling to the content. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var previous = 0f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val distance = (a - b).getDistance()
                if (previous > 0f && distance > 0f) onZoom(distance / previous)
                previous = distance
                event.changes.forEach { it.consume() }
            } else {
                previous = 0f
            }
        }
    }
}

@Composable
private fun PdfListNavigation(state: LazyListState, controller: PdfController, listener: PdfListener) {
    LaunchedEffect(controller, state) {
        var seekAnchor: Pair<Int, Int>? = null
        var seeking = false
        fun report(page: Int) {
            controller.page = page
            listener.onPageChanged(page)
        }
        launch {
            controller.pageRequests.collect { page ->
                seeking = true
                try {
                    state.scrollToItem(page)
                    seekAnchor = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
                    report(page)
                } finally {
                    seeking = false
                }
            }
        }
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { anchor ->
            if (!seeking && anchor != seekAnchor) {
                seekAnchor = null
                val page = if (!state.canScrollForward && state.canScrollBackward) {
                    state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: anchor.first
                } else {
                    anchor.first
                }
                if (page != controller.page) report(page)
            }
        }
    }
}
