package app.yomu.core.common

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Q04: a report names a typed stage and still masks a secret before it reaches the file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ErrorLogStageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aReportNamesItsStageAndMasksSecrets() {
        ErrorLog.install(context, "test")
        ErrorLog.clear()
        ErrorLog.handled(DiagStage.DOWNLOAD, IllegalStateException("GET https://example.org?token=abcdef123456 failed"))
        val text = ErrorLog.entries().maxByOrNull { it.name }!!.readText()
        assertTrue(text.contains("Stage: DOWNLOAD"))
        assertTrue(text.contains("Stage:"))
        assertFalse("a token must not reach the journal", text.contains("abcdef123456"))
        assertTrue(text.contains("[redacted]"))
    }

    @Test
    fun aScreenRouteSelectsTheStage() {
        ErrorLog.install(context, "test")
        ErrorLog.clear()
        ErrorLog.screen("ReaderRoute")
        ErrorLog.handled(IllegalStateException("reader boom"))
        ErrorLog.screen("LibraryRoute")
        ErrorLog.handled(IllegalStateException("library boom"))
        val texts = ErrorLog.entries().map { it.readText() }
        assertTrue(texts.any { it.contains("Stage: READER") && it.contains("reader boom") })
        assertTrue(texts.any { it.contains("Stage: LIBRARY") && it.contains("library boom") })
    }
}
