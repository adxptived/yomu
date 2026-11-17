package app.yomu.feature.reader

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.yomu.domain.model.CommonReaderSettings
import app.yomu.domain.model.ReaderOrientation
import app.yomu.domain.model.ScreenTimeout
import kotlinx.coroutines.delay

/**
 * Applies reader window settings: brightness, keep-screen-on with timeout, immersive mode,
 * display cutout and orientation. Everything is restored when the reader leaves the composition.
 */
@Composable
fun ReaderWindowEffects(common: CommonReaderSettings, chromeVisible: Boolean, lastInteraction: Long) {
    val activity = LocalActivity.current ?: return
    val window = activity.window

    // LaunchedEffect, not DisposableEffect: during an edge swipe the brightness changes every frame, and disposing
    // the previous effect would reset the screen to the system brightness in between (a visible flicker).
    // With the system brightness (a negative value) the window is left alone, so adaptive brightness keeps working
    // exactly as outside the reader; it is only restored when the reader changed it itself.
    val applied = remember { booleanArrayOf(false) }
    LaunchedEffect(common.brightness) {
        if (common.brightness < 0f && !applied[0]) return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (common.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else common.brightness.coerceIn(0.01f, 1f)
        window.attributes = lp
        applied[0] = common.brightness >= 0f
    }

    DisposableEffect(common.drawUnderCutout) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (common.drawUnderCutout) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            window.attributes = lp
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = lp
            }
        }
    }

    DisposableEffect(common.fullscreen, chromeVisible) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (common.fullscreen && !chromeVisible) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Split-screen and freeform windows own their size: the platform refuses a requested orientation there and a
    // rotation would resize the other app too. The reader therefore keeps the window's orientation and only applies
    // the setting when it owns the whole screen. The listener keeps this true when the window mode changes mid-read.
    val multiWindow = remember { mutableStateOf(activity.isInMultiWindowMode) }
    DisposableEffect(activity) {
        val owner = activity as? ComponentActivity
        val listener = Consumer<Configuration> { multiWindow.value = owner?.isInMultiWindowMode == true }
        owner?.addOnConfigurationChangedListener(listener)
        onDispose {
            owner?.removeOnConfigurationChangedListener(listener)
            multiWindow.value = activity.isInMultiWindowMode
        }
    }
    DisposableEffect(common.orientation, multiWindow.value) {
        val requested = if (multiWindow.value) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            when (common.orientation) {
                ReaderOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ReaderOrientation.LOCKED -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        }
        activity.requestedOrientation = requested
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Keep the screen on while reading; release the flag after the configured idle time.
    LaunchedEffect(common.screenTimeout, lastInteraction) {
        when (common.screenTimeout) {
            ScreenTimeout.SYSTEM -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ScreenTimeout.ALWAYS -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                delay(common.screenTimeout.minutes * 60_000L)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }
}
