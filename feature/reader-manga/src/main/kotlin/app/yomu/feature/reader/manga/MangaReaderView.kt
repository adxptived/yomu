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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** One item of the pager: an image (or a spread) or the boundary between two loaded chapters. */
internal sealed interface MangaSlot {
    /** [presentation] indexes the spreads, the split pages or the flat pages, whichever drives this layout. */
    data class Image(val presentation: Int) : MangaSlot

    /** A chapter boundary after [after]; [last] is the end of the loaded window rather than a chapter inside it. */
    data class Break(val after: Int, val nextTitle: String?, val last: Boolean) : MangaSlot
}

/**
 * Pages shown by the pager: optional previous-chapter page, the images (or their halves), but never a load between
 * the chapters of the open window — an internal chapter boundary is a slot of its own, not a reload.
 */
internal class Layout(
    val count: Int,
    val hasPrev: Boolean,
    val spreads: List<IntArray>?,
    val virtual: List<app.yomu.domain.reader.MangaPage>? = null,
    val split: app.yomu.domain.reader.PageSplit = app.yomu.domain.reader.PageSplit.OFF,
    /** Last flat page of a loaded chapter, mapped to the title of the chapter after it. */
    val breaks: Map<Int, String> = emptyMap(),
    /** Whether the end page is shown after the last page. */
    val end: Boolean = true,
) {
    val offset = if (hasPrev) 1 else 0
    private val presentations = spreads?.size ?: virtual?.size ?: count

    private val pagesOfPresentation: List<IntArray> = List(presentations) { p ->
        when {
            spreads != null -> spreads[p]
            virtual != null -> intArrayOf(virtual[p].physical)
            else -> intArrayOf(p)
        }
    }

    /**
     * The presentation after which each chapter boundary belongs. A split page has two slots for one physical page,
     * so the break follows the *last* of them; keying it to the first would show the boundary between the halves.
     */
    private val breakByPresentation: Map<Int, Int> = breaks.keys.mapNotNull { page ->
        pagesOfPresentation.indices.lastOrNull { pagesOfPresentation[it].contains(page) }?.let { it to page }
    }.toMap()

    val slots: List<MangaSlot> = buildList {
        pagesOfPresentation.forEachIndexed { p, _ ->
            add(MangaSlot.Image(p))
            breakByPresentation[p]?.let { after -> add(MangaSlot.Break(after, breaks[after], last = false)) }
        }
        if (end) add(MangaSlot.Break((count - 1).coerceAtLeast(0), null, last = true))
    }

    val total: Int get() = offset + slots.size

    private fun presentationOf(page: Int): Int = when {
        spreads != null -> spreads.indexOfFirst { page in it }.coerceAtLeast(0)
        virtual != null -> app.yomu.domain.reader.MangaPaging.virtualIndexOf(virtual, page, split)
        else -> page.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    /** The pager slot showing [page], counting the chapter boundaries before it. */
    fun slotOf(page: Int): Int {
        val target = presentationOf(page)
        return offset + target + breakByPresentation.keys.count { it < target }
    }

    /** The page shown at [slot] when it is an image slot; null for a chapter boundary. */
    fun pageOf(slot: Int): Int? {
        val s = slots.getOrNull(slot - offset) as? MangaSlot.Image ?: return null
        return pagesOfPresentation[s.presentation].first()
    }

    fun breakOf(slot: Int): MangaSlot.Break? = slots.getOrNull(slot - offset) as? MangaSlot.Break

    fun imageOf(slot: Int): Int? = (slots.getOrNull(slot - offset) as? MangaSlot.Image)?.presentation

    /** The half shown at [slot], or null when the slot is a whole page (a portrait scan is never cut). */
    fun halfOf(slot: Int): app.yomu.domain.reader.MangaPage? {
        val p = imageOf(slot) ?: return null
        return virtual?.getOrNull(p)?.takeIf { it.split }
    }
}

/** Where a page request should move the pager, or that the reader is at a chapter edge. */
internal sealed interface SlotMove {
    data class To(val slot: Int, val animate: Boolean = true) : SlotMove
    data object ChapterEdge : SlotMove
}

/**
 * The pager's answer to a physical page request (a hardware key, a tap, the slider, a typed number).
 *
 * A turn is one *slot*, not one physical page: in split mode the first half's next step is the second half of the
 * same page, and the last page's next step is the end page rather than the next chapter. Only a request that leaves
 * page 0 backwards, with nothing before it, is a chapter edge. A jump of more than one slot is never animated.
 */
internal fun slotMoveFor(request: Int, page: Int, currentSlot: Int, layout: Layout): SlotMove {
    val last = layout.total - 1
    return when {
        request < 0 -> when {
            currentSlot > 0 -> SlotMove.To(currentSlot - 1)
            layout.hasPrev -> SlotMove.To(0)
            else -> SlotMove.ChapterEdge
        }
        request >= layout.count -> SlotMove.To((currentSlot + 1).coerceAtMost(last))
        request == page + 1 -> SlotMove.To((currentSlot + 1).coerceAtMost(last))
        request == page - 1 -> SlotMove.To((currentSlot - 1).coerceAtLeast(0))
        else -> {
            val slot = layout.slotOf(request)
            SlotMove.To(slot, animate = kotlin.math.abs(slot - currentSlot) <= 1)
        }
    }
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
    /** Whether pages ahead of the current one may be fetched; the visible page is always loaded on any network. */
    preloadAllowed: () -> Boolean = { true },
    /** Opt-in vertical chapter rail for long-strip (webtoon) reading. */
    verticalChapterNavigator: Boolean = false,
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
            // Wi-Fi only: skip the speculative work entirely while metered; turning a page re-evaluates the network.
            if (!preloadAllowed()) return@collect
            for (i in page + 1..(page + ahead).coerceAtMost(controller.count - 1)) {
                if (!started.add(i)) continue
                launch {
                    val ok = gate.withPermit {
                        val now = controller.page
                        if (i <= now || i > now + controller.preloadAhead + PRELOAD_SLACK) return@withPermit false
                        val file = controller.file(i)
                        // A tall page is preloaded under the same decode budget as the one on screen.
                        val size = readPageSize(file)
                        controller.noteSize(i, size)
                        val target = size?.let {
                            MangaFit.decodeSize(it.width, it.height, metrics.widthPixels, metrics.heightPixels, controller.settings.scale)
                        }
                        runCatching { loader.enqueue(request(context, file, controller.settings.cropBorders, target = target)) }.isSuccess
                    }
                    if (!ok) started.remove(i)
                }
            }
        }
    }
    // Split mode cuts only wide scans. A local archive answers from an image header without unpacking every page; a
    // page of an online chapter is measured once it is already on the device, which the preloader above fills in.
    LaunchedEffect(controller, s.splitPages) {
        if (s.splitPages == app.yomu.domain.reader.PageSplit.OFF) return@LaunchedEffect
        val gate = Semaphore(PRELOAD_PARALLEL)
        for (i in 0 until controller.count) {
            if (controller.pageSizes.containsKey(i)) continue
            launch {
                gate.withPermit {
                    controller.noteSize(i, runCatching { controller.size(i) }.getOrNull())
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
        // A window already holds the neighbouring chapters: there is no page to load at the edge, only a chapter
        // boundary to show. Only a local publication keeps the external previous-chapter page.
        val session = controller.session
        val externalPrev = hasPrevChapter && session == null
        if (s.direction == MangaDirection.WEBTOON) {
            Webtoon(controller, listener, externalPrev, controller.endLeadsSomewhere(hasNextChapter), animationSpeed)
        } else {
            val useSpreads = landscape && s.dualPageLandscape && s.direction != MangaDirection.VERTICAL
            // A spread never mixes two chapters: pairing is computed per chapter and shifted to the flat index, so a
            // chapter boundary stays a boundary whatever the page counts are.
            val spreads = if (useSpreads) controller.spreadGroups(s.coverAlone) else null
            // Split pages and spreads are alternatives: a spread already divides the screen, and a half of a page
            // inside a spread would be unreadable.
            val split = if (!useSpreads) s.splitPages else app.yomu.domain.reader.PageSplit.OFF
            val virtual = if (split != app.yomu.domain.reader.PageSplit.OFF) controller.splitPages(split) else null
            val layout = Layout(
                count = controller.count,
                hasPrev = externalPrev,
                spreads = spreads,
                virtual = virtual,
                split = split,
                breaks = controller.chapterBreaks(),
            )
            Paged(controller, listener, layout, controller.endLeadsSomewhere(hasNextChapter), animationSpeed)
        }
        // Long-strip chapters can be far apart; the opt-in rail jumps straight to one of the loaded window's chapters.
        if (s.direction == MangaDirection.WEBTOON && verticalChapterNavigator) {
            val chapters = controller.session?.chapters.orEmpty()
            if (chapters.size > 1) {
                VerticalChapterNavigator(
                    chapters = chapters,
                    currentId = controller.chapter?.id,
                    onJump = { chapter -> controller.goTo(controller.globalIndex(chapter.id, 0) ?: 0) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                )
            }
        }
        AnimatedVisibility(zonesVisible && s.tapZones != TapZoneLayout.DISABLED, enter = fadeIn(), exit = fadeOut()) {
            TapZonesOverlay(s.tapZones, s.zonesInvertedX, s.zonesInvertedY, rtl = s.direction == MangaDirection.RTL)
        }
        if (s.showPageNumber && controller.count > 0) {
            Text(
                "${controller.localPage + 1} / ${controller.localCount}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        // The chapter being read is named on its first page: with three chapters in the pager, the page number alone
        // no longer says where the reader is.
        val chapter = controller.chapter
        if (chapter != null && controller.localPage == 0) {
            Text(
                chapter.title,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** True when the end page of the pager leads to a chapter: the one after the window, or the external next chapter. */
private fun MangaController.endLeadsSomewhere(hasNextChapter: Boolean): Boolean =
    if (session != null) hasNextAfterWindow else hasNextChapter

/** Spreads per loaded chapter, shifted to flat page indexes: a spread never mixes two chapters. */
private fun MangaController.spreadGroups(coverAlone: Boolean): List<IntArray> {
    val s = session ?: return app.yomu.domain.reader.MangaPaging.spreads(count, coverAlone)
    return s.slices.flatMap { slice ->
        app.yomu.domain.reader.MangaPaging.spreads(slice.pages, coverAlone).map { group ->
            IntArray(group.size) { group[it] + slice.start }
        }
    }
}

/** Split halves per loaded chapter, shifted to flat page indexes; the width is measured on the flat page. */
private fun MangaController.splitPages(split: app.yomu.domain.reader.PageSplit): List<MangaPage> {
    val s = session ?: return app.yomu.domain.reader.MangaPaging.virtualPages(count, split, ::isWide)
    return s.slices.flatMap { slice ->
        app.yomu.domain.reader.MangaPaging.virtualPages(slice.pages, split) { local -> isWide(slice.start + local) }
            .map { it.copy(physical = it.physical + slice.start) }
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
    // The effects below outlive one composition; a layout change (single pages ↔ split ↔ spreads) must be read from
    // the current one instead of the copy captured when the effect started.
    val layoutNow by rememberUpdatedState(layout)
    suspend fun go(slot: Int) = if (animate) pager.animateScrollToPage(slot, animationSpec = tween) else pager.scrollToPage(slot)
    LaunchedEffect(layout.spreads?.map { it.toList() }, layout.virtual, layout.hasPrev, layout.breaks) {
        // A new slot can appear while a page is being preloaded; only a slot that actually moved needs a correction,
        // otherwise a page turn in flight would be snapped back to the page being left.
        val target = layoutNow.slotOf(controller.page)
        if (target != pager.currentPage) pager.scrollToPage(target)
    }
    LaunchedEffect(Unit) {
        controller.pageRequests.collect { target ->
            val move = slotMoveFor(target, controller.page, pager.currentPage, layoutNow)
            when (move) {
                is SlotMove.To -> if (move.animate) go(move.slot) else pager.scrollToPage(move.slot)
                SlotMove.ChapterEdge -> listener.onChapterEdge(false)
            }
        }
    }
    var endReported by remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { slot ->
            // A chapter boundary is not a page: the position stays on the page that was shown before it.
            val boundary = layoutNow.breakOf(slot)
            if (boundary != null) {
                if (boundary.last && !hasNext && !endReported) {
                    endReported = true
                    listener.onFinished()
                }
                return@collect
            }
            endReported = false
            layoutNow.pageOf(slot)?.let { controller.page = it; listener.onPageChanged(it) }
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
        val boundary = layout.breakOf(slot)
        when {
            boundary != null -> EdgePage(
                next = true,
                available = if (boundary.last) hasNext else true,
                title = boundary.nextTitle,
                onGo = {
                    if (boundary.last) listener.onChapterEdge(true) else scope.launch { go((slot + 1).coerceAtMost(layout.total - 1)) }
                },
                onMenu = listener::onMenu,
            )
            layout.hasPrev && slot == 0 -> EdgePage(next = false, available = true, onGo = { listener.onChapterEdge(false) }, onMenu = listener::onMenu)
            else -> {
                val i = checkNotNull(layout.imageOf(slot)) { "slot $slot is not an image" }
                val half = layout.halfOf(slot)
                val group = layout.spreads?.get(i) ?: intArrayOf(layout.virtual?.getOrNull(i)?.physical ?: i)
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
        loadPage(controller, index, onFile = {
            file = it
            val size = readPageSize(it)
            pageSize = size
            controller.noteSize(index, size)
        }, onError = { error = it })
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
private fun EdgePage(next: Boolean, available: Boolean, onGo: () -> Unit, onMenu: () -> Unit, title: String? = null) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF121212)).pointerInput(Unit) { detectTapGestures { onMenu() } }.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (next) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.manga_chapter_finished), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
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
    // The strip is a list of slots too: page images with a chapter boundary between the loaded chapters.
    val layout = Layout(
        count = controller.count,
        hasPrev = hasPrev,
        spreads = null,
        breaks = controller.chapterBreaks(),
    )
    val layoutNow by rememberUpdatedState(layout)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = layout.slotOf(controller.page))
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
            val current = layoutNow.slotOf(controller.page)
            when {
                target < 0 -> listener.onChapterEdge(false)
                target >= controller.count -> state.animateScrollToItem(layoutNow.total - 1)
                target == controller.page + 1 -> state.animateScrollToItem((current + 1).coerceAtMost(layoutNow.total - 1))
                target == controller.page - 1 -> state.animateScrollToItem((current - 1).coerceAtLeast(0))
                else -> state.scrollToItem(layoutNow.slotOf(target.coerceIn(0, (controller.count - 1).coerceAtLeast(0))))
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val slot = i.coerceIn(0, (layoutNow.total - 1).coerceAtLeast(0))
            // A chapter boundary keeps the position on the page before it.
            if (layoutNow.breakOf(slot) == null) {
                layoutNow.pageOf(slot)?.let { p ->
                    controller.page = p
                    listener.onPageChanged(p)
                }
            }
            val atEnd = slot >= layoutNow.total - 1 && !state.canScrollForward
            if (atEnd && !hasNext) {
                if (!finishedReported) {
                    finishedReported = true
                    listener.onFinished()
                }
            } else if (finishedReported && !atEnd) {
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
            items(layout.total, key = { it }) { slot ->
                val boundary = layout.breakOf(slot)
                val index = layout.pageOf(slot)
                when {
                    hasPrev && slot == 0 -> Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
                        EdgePage(false, true, { listener.onChapterEdge(false) }, listener::onMenu)
                    }
                    boundary != null -> Box(Modifier.fillMaxWidth().heightIn(min = if (boundary.last) 360.dp else 240.dp)) {
                        EdgePage(
                            next = true,
                            available = if (boundary.last) hasNext else true,
                            title = boundary.nextTitle,
                            onGo = {
                                if (boundary.last) listener.onChapterEdge(true)
                                else scope.launch { state.animateScrollToItem((slot + 1).coerceAtMost(layout.total - 1)) }
                            },
                            onMenu = listener::onMenu,
                        )
                    }
                    index != null -> {
                        var file by remember(index) { mutableStateOf<File?>(null) }
                        var pageSize by remember(index) { mutableStateOf<ImageSize?>(null) }
                        var error by remember(index) { mutableStateOf<Throwable?>(null) }
                        var attempt by remember(index) { mutableIntStateOf(0) }
                        LaunchedEffect(index, attempt) {
                            error = null
                            loadPage(controller, index, onFile = {
                                file = it
                                val size = readPageSize(it)
                                pageSize = size
                                controller.noteSize(index, size)
                            }, onError = { error = it })
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
                }
            }
        }
    }
}

/** Short number shown on a rail marker, taken from the chapter title (a dot when it has none). */
private fun chapterMarker(title: String): String = Regex("\\d+").findAll(title).lastOrNull()?.value ?: "•"

/** A slim vertical chapter rail for long strips: one 48dp target per chapter of the currently loaded window. */
@Composable
private fun VerticalChapterNavigator(
    chapters: List<MangaChapter>,
    currentId: Long?,
    onJump: (MangaChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        chapters.forEach { chapter ->
            val current = chapter.id == currentId
            val label = stringResource(R.string.mr_chapter_navigator_jump, chapter.title)
            Box(
                Modifier.size(48.dp)
                    .background(
                        if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.Transparent,
                        RoundedCornerShape(24.dp),
                    )
                    .clickable(role = Role.Button) { onJump(chapter) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(chapterMarker(chapter.title), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
