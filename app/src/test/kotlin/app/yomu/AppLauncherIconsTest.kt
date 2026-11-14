package app.yomu

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun pickingSwitchesTheAliasWhenTheAppIsLeftAndSurvivesARestart() {
        context.getSharedPreferences("launcher_icon", Context.MODE_PRIVATE).edit().clear().commit()

        val icons = AppLauncherIcons(context)
        assertEquals(AppLauncherIcons.PURPLE_DARK, icons.current.value)

        icons.select("orange-light")
        assertEquals("orange-light", icons.current.value)
        // The running window was started from the default alias: switching now would close it, so the switch waits
        // for the app to be left.
        assertTrue("still on the default alias", enabled("app.yomu.MainActivity", byDefault = true))
        assertFalse(enabled("app.yomu.icon.OrangeLight"))

        icons.apply() // what happens when the app goes to the background
        assertTrue("Peach alias is enabled", enabled("app.yomu.icon.OrangeLight"))
        assertFalse("the default alias is disabled", enabled("app.yomu.MainActivity"))

        // The next start reads the stored pick, and its aliases agree with it already.
        val again = AppLauncherIcons(context)
        assertEquals("orange-light", again.current.value)
        assertTrue(enabled("app.yomu.icon.OrangeLight"))
        assertFalse(enabled("app.yomu.MainActivity"))

        again.select(AppLauncherIcons.PURPLE_DARK)
        again.apply()
        assertEquals(AppLauncherIcons.PURPLE_DARK, again.current.value)
        assertTrue(enabled("app.yomu.MainActivity", byDefault = true))
        assertFalse(enabled("app.yomu.icon.OrangeLight"))

        context.getSharedPreferences("launcher_icon", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
