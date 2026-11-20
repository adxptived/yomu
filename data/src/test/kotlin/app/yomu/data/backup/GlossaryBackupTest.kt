package app.yomu.data.backup

import android.content.Context
import android.content.ContextWrapper
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.data.glossary.GlossaryRepository
import app.yomu.domain.glossary.GlossaryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** N04: glossary notes are a backup category with the same merge/replace rules as words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlossaryBackupTest {
    private val app = RuntimeEnvironment.getApplication() as Context

    private fun context(root: File): Context = object : ContextWrapper(app) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    }

    @Test
    fun `glossary is counted previewed and restored with merge and replace semantics`() = runBlocking {
        val root = File(app.cacheDir, "glossary-backup-${UUID.randomUUID()}")
        val source = context(File(root, "a"))
        val dest = context(File(root, "b"))
        val a = YomuDatabase.build(source, true)
        val b = YomuDatabase.build(dest, true)
        try {
            GlossaryRepository(a).save(GlossaryEntry(term = "Elena", note = "from backup"))
            val bytes = ByteArrayOutputStream().also { BackupManager(source, a, AppSettings(source)).create(it, false) }.toByteArray()
            val preview = BackupManager.previewZip(bytes.inputStream())
            assertEquals(1, preview.categories[BackupCategory.GLOSSARY])
            assertEquals(0, preview.categories[BackupCategory.WORDS])

            GlossaryRepository(b).save(GlossaryEntry(term = "Elena", note = "local"))
            val manager = BackupManager(dest, b, AppSettings(dest))
            val onlyGlossary = BackupOptions(categories = setOf(BackupCategory.GLOSSARY))
            manager.restore(bytes.inputStream(), onlyGlossary)
            assertEquals("local", GlossaryRepository(b).all().single().note)

            manager.restore(bytes.inputStream(), BackupOptions(merge = false, categories = setOf(BackupCategory.GLOSSARY)))
            assertEquals("from backup", GlossaryRepository(b).all().single().note)
        } finally {
            a.close(); b.close(); root.deleteRecursively()
        }
    }

    @Test
    fun `an old archive without a category manifest still previews the glossary count`() = runBlocking {
        val json = """
            {"format":"yomu-backup","version":1,"createdAt":1,"filesDir":"/old","tables":{
              "glossary_entries":[{"id":1,"bookId":null,"locatorJson":null,"term":"Elena","normalized":"elena","aliasesJson":"[]","kind":"CHARACTER","note":"old","createdAt":1,"updatedAt":1}]
            }}
        """.trimIndent()
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
        val preview = BackupManager.previewZip(out.toByteArray().inputStream())
        assertEquals(1, preview.categories[BackupCategory.GLOSSARY])
        assertTrue(preview.categories.isNotEmpty())
    }
}
