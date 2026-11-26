package app.yomu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.yomu.AppLockController
import app.yomu.R
import app.yomu.YomuActivity
import app.yomu.core.datastore.AppSettings
import app.yomu.data.catalog.CatalogRepository
import app.yomu.domain.model.CatalogEntry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import timber.log.Timber

/**
 * The "Shelf" home-screen widget: the first titles of the shelf chosen in Settings → Library (or the whole library
 * when "All library" is chosen). Tapping a row opens the title; the same privacy rules as the other widgets apply.
 */
class ShelfWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refreshAsync(context)

    override fun onEnabled(context: Context) {
        ShelfWidgets.start(context.applicationContext)
    }

    override fun onDisabled(context: Context) = ShelfWidgets.stop()

    private fun refreshAsync(context: Context) {
        val app = context.applicationContext
        ShelfWidgets.start(app)
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        ShelfWidgets.scope.launch {
            try {
                withTimeoutOrNull(BROADCAST_BUDGET_MS) { ShelfWidgets.refresh(app) }
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        /** goAsync() leaves a receiver about ten seconds. */
        const val BROADCAST_BUDGET_MS = 8_000L
    }
}

object ShelfWidgets : KoinComponent {
    internal data class ShelfItem(
        val id: Long,
        val title: String,
        val author: String,
        val cover: String?,
        val unread: Int,
    )

    internal data class ShelfData(val header: String, val items: List<ShelfItem>)

    private const val MAX_ROWS = 3
    private const val COVER_W = 108
    private const val COVER_H = 150
    private const val HEADER_DP = 52
    private const val ROW_DP = 62
    private const val REQUESTS_PER_WIDGET = 8
    private const val CORNER_PX = 10f
    private const val COVER_CACHE = 6
    private const val DEBOUNCE_MS = 1_500L

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val rowIds = intArrayOf(R.id.widget_shelf_row_0, R.id.widget_shelf_row_1, R.id.widget_shelf_row_2)
    private val coverIds = intArrayOf(R.id.widget_shelf_cover_0, R.id.widget_shelf_cover_1, R.id.widget_shelf_cover_2)
    private val titleIds = intArrayOf(R.id.widget_shelf_title_0, R.id.widget_shelf_title_1, R.id.widget_shelf_title_2)
    private val subtitleIds = intArrayOf(R.id.widget_shelf_subtitle_0, R.id.widget_shelf_subtitle_1, R.id.widget_shelf_subtitle_2)

    internal fun widgetIds(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ShelfWidget::class.java))
    }.getOrDefault(IntArray(0))

    /** Keeps the widget current while the app process lives; only starts when a widget is on the home screen. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true || widgetIds(context).isEmpty()) return
        job = scope.launch {
            data().debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { shelf ->
                runCatching { render(context, shelf) }.onFailure { Timber.w(it, "Shelf widget update failed") }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refresh(context: Context) {
        runCatching { render(context, data().first()) }.onFailure { Timber.w(it, "Shelf widget update failed") }
    }

    private data class Gate(val shelfId: Long, val hideCovers: Boolean, val incognito: Boolean, val locked: Boolean?)

    private fun gate(): Flow<Gate> {
        val settings = get<AppSettings>()
        val lock = get<AppLockController>()
        return combine(
            settings.widgetShelfId.flow,
            settings.hideCovers.flow,
            settings.incognito.flow,
            lock.locked,
        ) { shelfId, hide, incognito, locked -> Gate(shelfId, hide, incognito, locked) }
    }

    private fun data(): Flow<ShelfData> {
        val catalog = get<CatalogRepository>()
        return combine(
            catalog.observeFavorites(),
            catalog.tabs.observe(),
            catalog.tabs.observeOnline(),
            gate(),
        ) { favorites, shelves, memberships, gate ->
            if (gate.incognito || gate.locked == true) return@combine ShelfData("", emptyList())
            val visible = if (gate.shelfId == 0L) {
                favorites
            } else {
                favorites.filter { gate.shelfId in memberships[it.id].orEmpty() }
            }
            val items = visible
                .sortedWith(compareByDescending<CatalogEntry> { it.lastReadAt }.thenByDescending { it.addedAt })
                .take(MAX_ROWS)
                .map { entry ->
                    ShelfItem(
                        id = entry.id,
                        title = entry.title,
                        author = entry.author.orEmpty(),
                        cover = entry.coverUrl.takeUnless { gate.hideCovers },
                        unread = entry.unread,
                    )
                }
            ShelfData(header = shelves.firstOrNull { it.id == gate.shelfId }?.name.orEmpty(), items = items)
        }
    }

    private suspend fun render(context: Context, data: ShelfData) {
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val covers = data.items.associate { item ->
            val url = item.cover
            val cached = url?.let { synchronized(coverCache) { coverCache[it] } }
            item.id to (cached ?: item.cover?.let { loadCover(context, it)?.also { bitmap -> synchronized(coverCache) { coverCache[it] = bitmap } } })
        }
        ids.forEach { id -> manager.updateAppWidget(id, views(context, data, covers, rowsFor(manager.getAppWidgetOptions(id)), id)) }
    }

    /** Covers already drawn (the widget is redrawn when the library changes). */
    private val coverCache = object : LinkedHashMap<String, Bitmap>(COVER_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > COVER_CACHE
    }

    /** How many rows fit the height of the widget. */
    private fun rowsFor(options: Bundle?): Int {
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)?.takeIf { it > 0 }
            ?: return MAX_ROWS
        return ((h - HEADER_DP) / ROW_DP).coerceIn(1, MAX_ROWS)
    }

    private fun views(context: Context, data: ShelfData, covers: Map<Long, Bitmap?>, rows: Int, widgetId: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_shelf)
        val openApp = PendingIntent.getActivity(
            context,
            widgetId * REQUESTS_PER_WIDGET,
            Intent(context, YomuActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.widget_shelf_header, openApp)
        v.setOnClickPendingIntent(R.id.widget_shelf_empty, openApp)
        v.setTextViewText(R.id.widget_shelf_header_text, data.header.ifBlank { context.getString(R.string.widget_shelf_label) })
        v.setViewVisibility(R.id.widget_shelf_empty, if (data.items.isEmpty()) View.VISIBLE else View.GONE)
        for (i in 0 until MAX_ROWS) {
            val item = data.items.getOrNull(i)
            if (item == null || i >= rows) {
                v.setViewVisibility(rowIds[i], View.GONE)
                continue
            }
            v.setViewVisibility(rowIds[i], View.VISIBLE)
            v.setTextViewText(titleIds[i], item.title)
            val subtitle = item.author.ifBlank {
                if (item.unread > 0) context.resources.getQuantityString(R.plurals.widget_unread_chapters, item.unread, item.unread) else ""
            }
            v.setTextViewText(subtitleIds[i], subtitle)
            val cover = covers[item.id]
            if (cover != null) v.setImageViewBitmap(coverIds[i], cover) else v.setImageViewResource(coverIds[i], R.drawable.widget_cover_placeholder)
            val intent = Intent(context, YomuActivity::class.java)
                .setAction(ContinueWidgets.ACTION_OPEN_ONLINE)
                .putExtra(ContinueWidgets.EXTRA_ID, item.id)
                // Keeps the intents of the rows apart (extras do not count when the system compares them).
                .setData(Uri.parse("yomu-shelf://${item.id}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(
                rowIds[i],
                PendingIntent.getActivity(context, widgetId * REQUESTS_PER_WIDGET + i + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return v
    }

    /** A small online cover with rounded corners (RemoteViews cannot clip). */
    private suspend fun loadCover(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(COVER_W, COVER_H).allowHardware(false).build()
        val raw = SingletonImageLoader.get(context).execute(request).image?.toBitmap() ?: return null
        rounded(raw)
    }.onFailure { Timber.w(it, "Shelf widget cover") }.getOrNull()

    private fun rounded(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(COVER_W, COVER_H, Bitmap.Config.ARGB_8888)
        val scale = maxOf(COVER_W / src.width.toFloat(), COVER_H / src.height.toFloat())
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate((COVER_W - src.width * scale) / 2f, (COVER_H - src.height * scale) / 2f)
        }
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(out).drawRoundRect(RectF(0f, 0f, COVER_W.toFloat(), COVER_H.toFloat()), CORNER_PX, CORNER_PX, paint)
        return out
    }
}
