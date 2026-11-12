package app.yomu.feature.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps read-aloud alive with the screen off and puts play/pause/stop into the notification shade and on the lock
 * screen. The text-to-speech engine stays in [ReaderViewModel]: it needs the open book to highlight the sentence being
 * spoken, so the service only owns the foreground state and the media session.
 */
class ReaderTtsService : Service() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "YomuTts").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = TtsBus.controls?.play() ?: Unit
                    override fun onPause() = TtsBus.controls?.pause() ?: Unit
                    override fun onStop() {
                        TtsBus.controls?.stop()
                        stopSelf()
                    }
                },
            )
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        scope.launch { TtsBus.status.collect { update(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> TtsBus.controls?.play()
            ACTION_PAUSE -> TtsBus.controls?.pause()
            ACTION_STOP -> {
                TtsBus.controls?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (TtsBus.controls == null && intent?.action != null) {
            // The reader is gone; there is nothing left to control.
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, build(TtsBus.status.value))
        return START_NOT_STICKY
    }

    private fun update(status: TtsStatus) {
        if (!status.active) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, build(status))
        updateSession(status)
    }

    private fun build(status: TtsStatus): Notification {
        val playing = status.playing
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, ReaderTtsService::class.java).setAction(if (playing) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, ReaderTtsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleIcon = Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        val toggleLabel = getString(if (playing) R.string.tts_pause else R.string.tts_play)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(status.title ?: getString(R.string.reader_tts))
            .setContentText(status.subtitle ?: getString(R.string.tts_channel))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(toggleIcon, toggleLabel, toggle).build())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.tts_stop),
                    stop,
                ).build(),
            )
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1))
        runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return builder.build()
    }

    private fun updateSession(status: TtsStatus) {
        val playing = status.playing
        session?.apply {
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, status.title ?: getString(R.string.reader_tts))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.tts_channel))
                    .build(),
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                    .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build(),
            )
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, getString(R.string.tts_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.tts_channel)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "reader_tts"
        private const val NOTIFICATION_ID = 0x5453
        private const val ACTION_PLAY = "app.yomu.reader.tts.PLAY"
        private const val ACTION_PAUSE = "app.yomu.reader.tts.PAUSE"
        private const val ACTION_STOP = "app.yomu.reader.tts.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ReaderTtsService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ReaderTtsService::class.java)) }
        }
    }
}
