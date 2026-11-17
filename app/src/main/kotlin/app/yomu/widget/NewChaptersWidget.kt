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
import app.yomu.data.catalog.CatalogRepository
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
 * The "New chapters" home-screen widget (U03): new unread chapters of favourite titles, from the local database.
 *
 * It never asks a source: the projection is what the last update check found, so a reboot or a lost network only means
 * "no new chapters to show", never a spinner. Incognito or a locked app hides it, like the continue-reading widget.
 */
class NewChaptersWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refreshAsync(context)

    override fun onEnabled(context: Context) {
        NewChaptersWidgets.start(context.applicationContext)
    }

    override fun onDisabled(context: Context) = NewChaptersWidgets.stop()

    private fun refreshAsync(context: Context) {
        val app = context.applicationContext
        NewChaptersWidgets.start(app)
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        NewChaptersWidgets.scope.launch {
            try {
                withTimeoutOrNull(BROADCAST_BUDGET_MS) { NewChaptersWidgets.refresh(app) }
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val BROADCAST_BUDGET_MS = 8_000L
    }
}

object NewChaptersWidgets : KoinComponent {
    const val ACTION_OPEN_CHAPTER = "app.yomu.action.WIDGET_OPEN_CHAPTER"
    const val EXTRA_ID = "app.yomu.extra.ID"
    private const val MAX_ROWS = 3
    private const val COVER_W = 108
    private const val COVER_H = 150
    private const val HEADER_DP = 52
    private const val ROW_DP = 62

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val rowIds = intArrayOf(R.id.nc_row_0, R.id.nc_row_1, R.id.nc_row_2)
    private val coverIds = intArrayOf(R.id.nc_cover_0, R.id.nc_cover_1, R.id.nc_cover_2)
    private val titleIds = intArrayOf(R.id.nc_title_0, R.id.nc_title_1, R.id.nc_title_2)
    private val subtitleIds = intArrayOf(R.id.nc_subtitle_0, R.id.nc_subtitle_1, R.id.nc_subtitle_2)

    fun widgetIds(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NewChaptersWidget::class.java))
    }.getOrDefault(IntArray(0))

    /** Keeps the widget current while the process lives; only starts when a widget is on the home screen. */
    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true || widgetIds(context).isEmpty()) return
        job = scope.launch {
            entries().debounce(DEBOUNCE_MS).distinctUntilChanged().collect { list ->
                runCatching { render(context, list) }.onFailure { Timber.w(it, "New chapters widget update failed") }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refresh(context: Context) {
        runCatching { render(context, entries().first()) }
            .onFailure { Timber.w(it, "New chapters widget update failed") }
    }

    private fun entries(): Flow<List<app.yomu.core.database.NewChapterRow>> {
        val catalog = get<CatalogRepository>()
        val settings = get<app.yomu.core.datastore.AppSettings>()
        val lock = get<AppLockController>()
        return combine(
            catalog.observeNewChapters(MAX_ROWS),
            settings.incognito.flow,
            lock.locked,
        ) { chapters, incognito, locked ->
            if (incognito || locked == true) emptyList() else chapters
        }
    }

    private suspend fun render(context: Context, list: List<app.yomu.core.database.NewChapterRow>) {
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val covers = list.associate { row ->
            row.chapterId to loadCover(context, row.coverUrl)
        }
        ids.forEach { id -> manager.updateAppWidget(id, views(context, list, covers, rowsFor(manager.getAppWidgetOptions(id)), id)) }
    }

    private fun rowsFor(options: Bundle?): Int {
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)?.takeIf { it > 0 }
            ?: return MAX_ROWS
        return ((h - HEADER_DP) / ROW_DP).coerceIn(1, MAX_ROWS)
    }

    private fun views(
        context: Context,
        list: List<app.yomu.core.database.NewChapterRow>,
        covers: Map<Long, Bitmap?>,
        rows: Int,
        widgetId: Int,
    ): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_new_chapters)
        val openApp = PendingIntent.getActivity(
            context,
            widgetId * REQUESTS_PER_WIDGET,
            Intent(context, YomuActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.nc_header, openApp)
        v.setOnClickPendingIntent(R.id.nc_empty, openApp)
        v.setViewVisibility(R.id.nc_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        for (i in 0 until MAX_ROWS) {
            val row = list.getOrNull(i)
            if (row == null || i >= rows) {
                v.setViewVisibility(rowIds[i], View.GONE)
                continue
            }
            v.setViewVisibility(rowIds[i], View.VISIBLE)
            v.setTextViewText(titleIds[i], row.title)
            v.setTextViewText(subtitleIds[i], row.chapter)
            val cover = covers[row.chapterId]
            if (cover != null) v.setImageViewBitmap(coverIds[i], cover) else v.setImageViewResource(coverIds[i], R.drawable.widget_cover_placeholder)
            val intent = Intent(context, YomuActivity::class.java)
                .setAction(ACTION_OPEN_CHAPTER)
                .putExtra(EXTRA_ID, row.chapterId)
                .setData(Uri.parse("yomu-new-chapters://${row.chapterId}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            v.setOnClickPendingIntent(
                rowIds[i],
                PendingIntent.getActivity(context, widgetId * REQUESTS_PER_WIDGET + i + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return v
    }

    private suspend fun loadCover(context: Context, url: String?): Bitmap? = runCatching {
        val src = url ?: return null
        val request = ImageRequest.Builder(context).data(src).size(COVER_W, COVER_H).allowHardware(false).build()
        val raw = SingletonImageLoader.get(context).execute(request).image?.toBitmap() ?: return null
        rounded(raw)
    }.onFailure { Timber.w(it, "New chapters widget cover") }.getOrNull()

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

    private const val DEBOUNCE_MS = 1_500L
    private const val REQUESTS_PER_WIDGET = 8
    private const val CORNER_PX = 10f
}
