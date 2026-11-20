package app.yomu.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.yomu.R
import app.yomu.YomuActivity
import app.yomu.feature.audio.AudioPlaybackService
import app.yomu.feature.audio.AudioWidgetContract
import app.yomu.feature.audio.AudioWidgetState

/**
 * The audiobook transport widget (A03c).
 *
 * It shows the book and track the service last published and sends three explicit service intents: play/pause, back
 * and forward. The service owns the player, so a widget tap, the notification and a headset all take the same path;
 * after a reboot a tap starts the service, which has no book loaded and therefore stays silent.
 */
class AudioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AudioWidgetContract.ACTION_UPDATE) render(context)
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AudioWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val state = AudioWidgetContract.read(context)
        ids.forEach { id -> manager.updateAppWidget(id, views(context, state, id)) }
    }

    private fun views(context: Context, state: AudioWidgetState?, widgetId: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_audio)
        val open = PendingIntent.getActivity(
            context,
            widgetId * REQUESTS_PER_WIDGET,
            Intent(context, YomuActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.widget_audio_open, open)
        if (state == null) {
            v.setTextViewText(R.id.widget_audio_title, context.getString(R.string.widget_audio_label))
            v.setTextViewText(R.id.widget_audio_subtitle, context.getString(R.string.widget_audio_empty))
            v.setViewVisibility(R.id.widget_audio_toggle, View.GONE)
            v.setViewVisibility(R.id.widget_audio_back, View.GONE)
            v.setViewVisibility(R.id.widget_audio_forward, View.GONE)
            return v
        }
        v.setTextViewText(R.id.widget_audio_title, state.bookTitle)
        v.setTextViewText(
            R.id.widget_audio_subtitle,
            listOfNotNull(state.trackTitle.takeIf { it.isNotBlank() }, position(state)).joinToString(" · "),
        )
        v.setImageViewResource(
            R.id.widget_audio_toggle,
            if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        v.setOnClickPendingIntent(R.id.widget_audio_toggle, serviceIntent(context, AudioWidgetContract.ACTION_TOGGLE, widgetId * REQUESTS_PER_WIDGET + 1))
        v.setOnClickPendingIntent(R.id.widget_audio_back, serviceIntent(context, AudioWidgetContract.ACTION_BACK, widgetId * REQUESTS_PER_WIDGET + 2))
        v.setOnClickPendingIntent(R.id.widget_audio_forward, serviceIntent(context, AudioWidgetContract.ACTION_FORWARD, widgetId * REQUESTS_PER_WIDGET + 3))
        return v
    }

    private fun serviceIntent(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            context,
            request,
            Intent(context, AudioPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun position(state: AudioWidgetState): String? {
        if (state.positionMs <= 0) return null
        val seconds = state.positionMs / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private companion object {
        /** Room for four pending intents per widget instance. */
        const val REQUESTS_PER_WIDGET = 4
    }
}
