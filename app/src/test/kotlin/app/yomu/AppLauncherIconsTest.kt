package app.yomu

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Picking an app icon switches the aliases at once and the pick survives a process restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLauncherIconsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The real application starts Koin; the next test in this JVM must find it stopped. */
    @Before
    fun setUp() = stopKoin()

    @After
    fun tearDown() = stopKoin()

    /** A component counts as on when it is enabled, or when it keeps the manifest default ([byDefault]). */
    private fun enabled(className: String, byDefault: Boolean = false): Boolean =
        when (context.packageManager.getComponentEnabledSetting(ComponentName(context, className))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> byDefault
            else -> false
        }

    @Test
    fun theSingleIconKeepsTheDefaultAliasAndSurvivesARestart() {
        context.getSharedPreferences("launcher_icon", Context.MODE_PRIVATE).edit().clear().commit()

        val icons = AppLauncherIcons(context)
        assertEquals(AppLauncherIcons.DEFAULT, icons.current.value)
        assertEquals(listOf(AppLauncherIcons.DEFAULT), icons.icons.map { it.id })

        // An id of one of the old colour variants is ignored instead of leaving the app without a launcher entry.
        icons.select("orange-light")
        assertEquals(AppLauncherIcons.DEFAULT, icons.current.value)
        icons.apply()
        assertTrue("still on the default alias", enabled("app.yomu.MainActivity", byDefault = true))

        val again = AppLauncherIcons(context)
        assertEquals(AppLauncherIcons.DEFAULT, again.current.value)
        assertTrue(enabled("app.yomu.MainActivity", byDefault = true))

        context.getSharedPreferences("launcher_icon", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun startupRestoresLauncherAliasDisabledByAnOldIconPick() {
        val alias = ComponentName(context, "app.yomu.MainActivity")
        context.packageManager.setComponentEnabledSetting(
            alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )

        AppLauncherIcons(context).start()

        assertTrue(enabled(alias.className, byDefault = true))
    }
}
