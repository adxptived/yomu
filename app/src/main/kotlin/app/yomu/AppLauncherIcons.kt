package app.yomu

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.yomu.core.ui.components.LauncherIcon
import app.yomu.core.ui.components.LauncherIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Launcher icons (1.7.0). Every icon is an `<activity-alias>` of [YomuActivity] in the manifest and exactly one is
 * enabled. The choice lives in the package manager itself, so it survives restarts and needs no setting of its own.
 * The artwork is one drawing in four colour variants, the sources are in `branding/app-icons`.
 *
 * A pick shows at once in the app, but the aliases switch only when the app leaves the screen: while it is open, some
 * launchers would redraw or even close it the moment its entry changes.
 */
class AppLauncherIcons(private val context: Context) : LauncherIcons, DefaultLifecycleObserver {
    private class Alias(val icon: LauncherIcon, val className: String, val enabledByDefault: Boolean = false)

    private val aliases = listOf(
        Alias(LauncherIcon(PURPLE_DARK, R.string.icon_purple_dark, R.drawable.ic_launcher_purple_dark_background, R.drawable.ic_launcher_purple_dark_foreground), "app.yomu.MainActivity", enabledByDefault = true),
        Alias(LauncherIcon("purple-light", R.string.icon_purple_light, R.drawable.ic_launcher_purple_light_background, R.drawable.ic_launcher_purple_light_foreground), "app.yomu.icon.PurpleLight"),
        Alias(LauncherIcon("orange-dark", R.string.icon_orange_dark, R.drawable.ic_launcher_orange_dark_background, R.drawable.ic_launcher_orange_dark_foreground), "app.yomu.icon.OrangeDark"),
        Alias(LauncherIcon("orange-light", R.string.icon_orange_light, R.drawable.ic_launcher_orange_light_background, R.drawable.ic_launcher_orange_light_foreground), "app.yomu.icon.OrangeLight"),
    )

    override val icons: List<LauncherIcon> = aliases.map { it.icon }

    /** What the launcher shows now. */
    @Volatile private var applied = read()
    private val _current = MutableStateFlow(applied)
    override val current: StateFlow<String> = _current.asStateFlow()

    /** Applies a pick whenever the whole app goes to the background. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun select(id: String) {
        if (aliases.any { it.icon.id == id }) _current.value = id
    }

    override fun onStop(owner: LifecycleOwner) {
        apply()
    }

    /** Switches the aliases to the picked icon, if it is not the one the launcher shows already. */
    fun apply() {
        val id = _current.value
        if (id == applied) return
        val target = aliases.firstOrNull { it.icon.id == id } ?: return
        val pm = context.packageManager
        runCatching {
            // The new entry first: for a moment the launcher may list two icons, but never none.
            pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            for (a in aliases) {
                if (a !== target) pm.setComponentEnabledSetting(component(a), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            applied = id
        }.onFailure { Timber.w(it, "Could not switch the launcher icon to %s", id) }
    }

    private fun read(): String = runCatching {
        val pm = context.packageManager
        fun enabled(a: Alias) = when (pm.getComponentEnabledSetting(component(a))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a.enabledByDefault
            else -> false
        }
        (aliases.firstOrNull { !it.enabledByDefault && enabled(it) } ?: aliases.first()).icon.id
    }.getOrDefault(PURPLE_DARK)

    private fun component(a: Alias) = ComponentName(context, a.className)

    companion object {
        const val PURPLE_DARK = "purple-dark"
    }
}
