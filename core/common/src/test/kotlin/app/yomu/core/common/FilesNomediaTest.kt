package app.yomu.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


/** Directories the app owns are marked as data; a folder the user picked is never touched. */
class FilesNomediaTest {
    @Test
    fun `an owned directory gets an empty marker`() {
        val dir = java.nio.file.Files.createTempDirectory("yomu-nomedia").toFile()
        try {
            Files.ensureNomedia(dir)
            val marker = File(dir, ".nomedia")
            assertTrue(marker.exists())
            assertEquals(0, marker.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the marker is written once`() {
        val dir = java.nio.file.Files.createTempDirectory("yomu-nomedia").toFile()
        try {
            Files.ensureNomedia(dir)
            File(dir, ".nomedia").writeText("kept")
            Files.ensureNomedia(dir)
            assertEquals("kept", File(dir, ".nomedia").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a missing directory is created`() {
        val base = java.nio.file.Files.createTempDirectory("yomu-nomedia").toFile()
        try {
            val nested = File(base, "chapters/2024")
            Files.ensureNomedia(nested)
            assertTrue(File(nested, ".nomedia").exists())
            // The parent is not our directory: it gets no marker of its own.
            assertFalse(File(base, ".nomedia").exists())
        } finally {
            base.deleteRecursively()
        }
    }
}
