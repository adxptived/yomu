package app.yomu.ui

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.AppLockController
import app.yomu.MainViewModel
import app.yomu.core.ui.components.HintStore
import app.yomu.core.ui.components.LauncherIcons
import app.yomu.core.ui.components.LocalHapticsEnabled
import app.yomu.core.ui.components.LocalHideCovers
import app.yomu.core.ui.components.LocalHintStore
import app.yomu.core.ui.components.LocalLauncherIcons
import app.yomu.core.ui.components.LocalReduceMotion
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.feature.settings.AppLockScreen
import app.yomu.navigation.YomuNavigation

/** Theme, global locals, onboarding, the navigation shell and the app lock overlay. */
@Composable
fun YomuRoot(viewModel: MainViewModel, lock: AppLockController, hints: HintStore, launcherIcons: LauncherIcons? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked by lock.locked.collectAsStateWithLifecycle()
    val whatsNew by viewModel.whatsNew.collectAsStateWithLifecycle()
    val crash by viewModel.crash.collectAsStateWithLifecycle()
    val hideCovers by viewModel.hideCovers.collectAsStateWithLifecycle()
    // Android's own "Remove animations" (animator duration scale 0) is respected next to the app setting.
    val systemReduceMotion = rememberSystemReduceMotion()
    val s = state ?: return
    val reduceMotion = s.reduceMotion || systemReduceMotion
    YomuTheme(s.theme) {
        val view = LocalView.current
        val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightBackground
            }
        }
        CompositionLocalProvider(
            LocalHapticsEnabled provides s.haptics,
            LocalHideCovers provides hideCovers,
            LocalHintStore provides hints,
            LocalLauncherIcons provides launcherIcons,
            LocalReduceMotion provides reduceMotion,
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    if (s.onboardingDone) {
                        YomuNavigation(viewModel, reduceMotion, s.navLabels, s.navTabs)
                    } else {
                        OnboardingScreen(viewModel.settings, onDone = viewModel::finishOnboarding)
                    }
                    when (locked) {
                        true -> Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            },
                        ) {
                            AppLockScreen(viewModel.settings, onUnlocked = lock::unlock)
                        }
                        // Settings not read yet: cover the content instead of flashing it.
                        null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                        false -> Unit
                    }
                }
                val report = crash
                when {
                    !s.onboardingDone || locked != false -> Unit
                    // The crash first: the list of news waits until the next start or until the dialog is closed.
                    report != null -> CrashReportDialog(report, onDismiss = viewModel::dismissCrash)
                    whatsNew.isNotEmpty() -> WhatsNewSheet(whatsNew, onDismiss = viewModel::dismissWhatsNew)
                }
            }
        }
    }
}

/**
 * Android's animator duration scale, observed while the app runs: when it is 0 (Developer options → "Remove
 * animations", or a device that turns them off in battery saver) decorative motion is skipped exactly like the app's
 * own "Reduce motion" setting. The value can change without leaving the screen.
 */
@Composable
private fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readSystemReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readSystemReduceMotion(context)
            }
        }
        val resolver = context.contentResolver
        runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduce
}

private fun readSystemReduceMotion(context: android.content.Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
