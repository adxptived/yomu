package app.yomu.data.source

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Bundle
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An extension that was installed before this build (by Mihon, F-Droid or an older store) may have no trust entry.
 * The first scan trusts what is already on the device once, so its sources appear in the catalog again; the explicit
 * trust flow stays for packages installed later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], application = Application::class)
class ExtensionTrustMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = YomuDatabase.build(context, inMemory = true)

    @After
    fun tearDown() {
        db.close()
        runBlocking { AppSettings(context).resetAll() }
    }

    @Test
    fun anExtensionInstalledWithoutATrustEntryIsTrustedOnce() = runBlocking {
        val settings = AppSettings(context)
        settings.resetAll()
        val pkg = "eu.kanade.tachiyomi.extension.en.oldinstall"
        val signature = "old-install".toByteArray()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = pkg
                versionName = "1.4.7"
                versionCode = 7
                signatures = arrayOf(Signature(signature))
                reqFeatures = arrayOf(FeatureInfo().apply { name = "tachiyomi.extension" })
                applicationInfo = ApplicationInfo().apply {
                    packageName = pkg
                    metaData = Bundle().apply { putString("tachiyomi.extension.class", ".OldSource") }
                }
            },
        )

        val manager = ExtensionManager(context, db, settings) { OkHttpClient() }
        manager.reload()

        val entry = "$pkg:${ExtensionManager.sha256(signature)}"
        assertTrue("the installed package is trusted", entry in settings.trustedExtensions.get())
        assertTrue("the migration does not run again", settings.extensionTrustMigrated.get())
        // No compatibility layer in this test, so loading the class fails; the point is that trust no longer blocks it.
        assertFalse(manager.installed.value.single { it.pkg == pkg }.status == ExtensionStatus.UNTRUSTED)
    }

    @Test
    fun aPackageInstalledAfterTheMigrationStillNeedsTrust() = runBlocking {
        val settings = AppSettings(context)
        settings.resetAll()
        settings.extensionTrustMigrated.set(true)
        val pkg = "eu.kanade.tachiyomi.extension.en.later"
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = pkg
                versionName = "1.4.8"
                versionCode = 8
                signatures = arrayOf(Signature("later".toByteArray()))
                reqFeatures = arrayOf(FeatureInfo().apply { name = "tachiyomi.extension" })
                applicationInfo = ApplicationInfo().apply {
                    packageName = pkg
                    metaData = Bundle().apply { putString("tachiyomi.extension.class", ".LaterSource") }
                }
            },
        )

        val manager = ExtensionManager(context, db, settings) { OkHttpClient() }
        manager.reload()

        assertTrue(settings.trustedExtensions.get().isEmpty())
        assertEquals(ExtensionStatus.UNTRUSTED, manager.installed.value.single { it.pkg == pkg }.status)
    }
}
