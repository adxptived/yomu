package app.yomu.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A batch update is reported per extension, never as one "done". */
class ExtensionUpdateReportTest {
    @Test
    fun `a fully successful batch is not partial`() {
        val report = ExtensionUpdateReport(updated = listOf("a", "b"))
        assertFalse(report.isPartial)
        assertEquals(2, report.attempted)
        assertTrue(report.failed.isEmpty())
    }

    @Test
    fun `one failure among successes is partial and names both numbers`() {
        val report = ExtensionUpdateReport(
            updated = listOf("a", "b", "c"),
            failed = listOf(
                ExtensionUpdateReport.ExtensionFailure("d", ExtensionException.Reason.SIGNATURE_MISMATCH, "d"),
                ExtensionUpdateReport.ExtensionFailure("e", ExtensionException.Reason.DOWNLOAD_FAILED, null),
            ),
        )
        assertTrue(report.isPartial)
        assertEquals(5, report.attempted)
        assertEquals(listOf("d", "e"), report.failed.map { it.name })
        assertEquals(ExtensionException.Reason.SIGNATURE_MISMATCH, report.failed.first().reason)
    }

    @Test
    fun `a batch where everything failed is not partial either`() {
        val report = ExtensionUpdateReport(failed = listOf(ExtensionUpdateReport.ExtensionFailure("a", null, "boom")))
        assertFalse(report.isPartial)
        assertEquals(1, report.attempted)
    }
}
