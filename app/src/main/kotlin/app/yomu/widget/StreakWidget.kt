package app.yomu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.yomu.AppLockController
import app.yomu.R
import app.yomu.YomuActivity
import app.yomu.core.datastore.AppSettings
import app.yomu.domain.model.ReadingSession
import app.yomu.domain.repository.StatsRepository
import app.yomu.domain.stats.StatsCalculator
import app.yomu.domain.stats.StatsPeriod
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
import java.time.ZoneId

/**
 * The small "Reading streak" home-screen widget: how many days in a row the reader has read and how much time was
 * spent today. It follows the same privacy rules as the other widgets — incognito or a locked app shows no numbers.
 */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refreshAsync(context)

    override fun onEnabled(context: Context) {
        StreakWidgets.start(context.applicationContext)
    }

    override fun onDisabled(context: Context) = StreakWidgets.stop()

    private fun refreshAsync(context: Context) {
        val app = context.applicationContext
        StreakWidgets.start(app)
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        StreakWidgets.scope.launch {
            try {
                withTimeoutOrNull(BROADCAST_BUDGET_MS) { StreakWidgets.refresh(app) }
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

object StreakWidgets : KoinComponent {
    internal data class Streak(val days: Int, val todayMs: Long, val hasSessions: Boolean)

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    internal fun widgetIds(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StreakWidget::class.java))
    }.getOrDefault(IntArray(0))

    /** Keeps the widget current while the app process lives; only starts when a widget is on the home screen. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true || widgetIds(context).isEmpty()) return
        job = scope.launch {
            data().debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { streak ->
                runCatching { render(context, streak) }.onFailure { Timber.w(it, "Streak widget update failed") }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refresh(context: Context) {
        runCatching { render(context, data().first()) }.onFailure { Timber.w(it, "Streak widget update failed") }
    }

    private fun data(): Flow<Streak> {
        val stats = get<StatsRepository>()
        val settings = get<AppSettings>()
        val lock = get<AppLockController>()
        return combine(stats.observeSessions(), settings.incognito.flow, lock.locked) { sessions, incognito, locked ->
            if (incognito || locked == true) Streak(0, 0, false) else summarize(sessions)
        }
    }

    internal fun summarize(
        sessions: List<ReadingSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Streak {
        if (sessions.isEmpty()) return Streak(0, 0, false)
        val summary = StatsCalculator.compute(sessions, StatsPeriod.ALL, now, zone)
        return Streak(summary.streakDays, summary.todayMs, true)
    }

    private suspend fun render(context: Context, streak: Streak) {
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id -> manager.updateAppWidget(id, views(context, streak, id)) }
    }

    internal fun views(context: Context, streak: Streak, widgetId: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_streak)
        val openApp = PendingIntent.getActivity(
            context,
            widgetId * REQUESTS_PER_WIDGET,
            Intent(context, YomuActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.widget_streak_root, openApp)
        v.setOnClickPendingIntent(R.id.widget_streak_empty, openApp)
        if (!streak.hasSessions) {
            v.setViewVisibility(R.id.widget_streak_body, View.GONE)
            v.setViewVisibility(R.id.widget_streak_empty, View.VISIBLE)
            v.setTextViewText(R.id.widget_streak_number, "0")
            return v
        }
        v.setViewVisibility(R.id.widget_streak_body, View.VISIBLE)
        v.setViewVisibility(R.id.widget_streak_empty, View.GONE)
        v.setTextViewText(R.id.widget_streak_number, streak.days.toString())
        val days = if (streak.days <= 0) {
            context.getString(R.string.widget_streak_days_zero)
        } else {
            context.resources.getQuantityString(R.plurals.widget_streak_days, streak.days, streak.days)
        }
        v.setTextViewText(R.id.widget_streak_days, days)
        val today = if (streak.todayMs > 0) {
            context.getString(R.string.widget_streak_today, duration(context, streak.todayMs))
        } else {
            context.getString(R.string.widget_streak_today_none)
        }
        v.setTextViewText(R.id.widget_streak_today, today)
        return v
    }

    /** "12 min" or "1 h 5 min"; a session shorter than a minute still reads as a minute. */
    internal fun duration(context: Context, ms: Long): String {
        val minutes = (ms / 60_000).toInt().coerceAtLeast(1)
        return if (minutes < 60) {
            context.getString(R.string.widget_streak_minutes, minutes)
        } else {
            context.getString(R.string.widget_streak_hours, minutes / 60, minutes % 60)
        }
    }

    private const val DEBOUNCE_MS = 1_500L
    private const val REQUESTS_PER_WIDGET = 2
}
