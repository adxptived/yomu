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
 * enabled. The pick is kept in [PREFS] as well as in the package manager: the package manager alone forgets it when
 * the app dies before the switch. The switch itself runs when the app goes to the background — disabling the alias
 * that the running window was started from closes that window, so it must not happen while the screen is open.
 * The artwork is one drawing in four colour variants; the sources are in `branding/app-icons`.
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

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The pick, kept here so a process that dies before the switch does not lose it. */
    private var picked: String? = prefs.getString(KEY, null)?.takeIf { id -> aliases.any { it.icon.id == id } }

    /** What the launcher shows now. */
    @Volatile private var applied = read()
    private val _current = MutableStateFlow(picked ?: applied)
    override val current: StateFlow<String> = _current.asStateFlow()

    /** Applies a stored pick whenever the app goes to the background (the switch there never closes a screen). */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun select(id: String) {
        if (aliases.none { it.icon.id == id }) return
        picked = id
        prefs.edit().putString(KEY, id).apply()
        _current.value = id
    }

    override fun onStop(owner: LifecycleOwner) {
        apply()
    }

    /** Switches the aliases to the picked icon; also cleans up a state with several aliases enabled. */
    fun apply() {
        val id = picked ?: _current.value
        val target = aliases.firstOrNull { it.icon.id == id } ?: return
        val pm = context.packageManager
        val enabled = aliases.filter { isEnabled(pm, it) }
        if (enabled.size == 1 && enabled.first() === target) {
            applied = id
            return
        }
        runCatching {
            // The new entry first: for a moment the launcher may list two icons, but never none.
            pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            aliases.filter { it !== target }.forEach {
                pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            applied = id
        }.onFailure { Timber.w(it, "Could not switch the launcher icon to %s", id) }
    }

    private fun isEnabled(pm: PackageManager, a: Alias): Boolean = when (pm.getComponentEnabledSetting(component(a))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a.enabledByDefault
        else -> false
    }

    private fun read(): String = runCatching {
        val pm = context.packageManager
        (aliases.firstOrNull { !it.enabledByDefault && isEnabled(pm, it) } ?: aliases.first()).icon.id
    }.getOrDefault(PURPLE_DARK)

    private fun component(a: Alias) = ComponentName(context, a.className)

    companion object {
        const val PURPLE_DARK = "purple-dark"
        private const val PREFS = "launcher_icon"
        private const val KEY = "picked"
    }
}
