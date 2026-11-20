package app.yomu.data.backup

import android.content.Context
import android.content.ContextWrapper
import app.yomu.core.database.WordEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
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
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** B01: the v2 manifest and the merge/replace choice; an archive from a newer app is refused. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCategoriesTest {
    private val app = RuntimeEnvironment.getApplication() as Context

    private fun context(root: File): Context = object : ContextWrapper(app) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    }

    private suspend fun word(db: YomuDatabase, term: String, translation: String) = db.words().insert(
        WordEntity(
            term = term, normalized = term.lowercase(), lang = "en", translation = translation, definition = null,
            context = null, bookId = null, bookTitle = null, locatorJson = null, tags = "", createdAt = 1,
            level = 0, ease = 2.5, intervalDays = 0, repetitions = 0, dueAt = 0, lapses = 0, lastReviewedAt = null,
        ),
    )

    private suspend fun archive(source: Context, db: YomuDatabase): ByteArray =
        ByteArrayOutputStream().also { BackupManager(source, db, AppSettings(source)).create(it, includeFiles = false) }.toByteArray()

    @Test
    fun `a backup names its categories and a restore can take only words`() = runBlocking {
        val root = File(app.cacheDir, "backup-cat-${UUID.randomUUID()}")
        val source = context(File(root, "a"))
        val dest = context(File(root, "b"))
        val a = YomuDatabase.build(source, true)
        val b = YomuDatabase.build(dest, true)
        try {
            word(a, "apple", "яблоко")
            val bytes = archive(source, a)
            val preview = BackupManager.previewZip(bytes.inputStream())
            assertEquals(BackupManager.VERSION, preview.version)
            assertEquals(1, preview.categories[BackupCategory.WORDS])
            assertEquals(0, preview.categories[BackupCategory.BOOKS])

            val result = BackupManager(dest, b, AppSettings(dest)).restore(
                bytes.inputStream(),
                BackupOptions(merge = true, categories = setOf(BackupCategory.WORDS)),
            )
            assertEquals(1, result.rows)
            assertEquals(listOf("apple"), b.words().all().map { it.term })
        } finally {
            a.close(); b.close(); root.deleteRecursively()
        }
    }

    @Test
    fun `replace clears the chosen category and merge keeps the local word`() = runBlocking {
        val root = File(app.cacheDir, "backup-cat-${UUID.randomUUID()}")
        val source = context(File(root, "a"))
        val dest = context(File(root, "b"))
        val a = YomuDatabase.build(source, true)
        val b = YomuDatabase.build(dest, true)
        try {
            word(a, "apple", "from backup")
            val bytes = archive(source, a)
            word(b, "apple", "local")
            // Merge: the local row wins, the archive's identical word is skipped.
            BackupManager(dest, b, AppSettings(dest)).restore(
                bytes.inputStream(),
                BackupOptions(merge = true, categories = setOf(BackupCategory.WORDS)),
            )
            assertEquals("local", b.words().all().single().translation)
            // Replace: the category is emptied first, so the archive's value arrives.
            BackupManager(dest, b, AppSettings(dest)).restore(
                bytes.inputStream(),
                BackupOptions(merge = false, categories = setOf(BackupCategory.WORDS)),
            )
            assertEquals("from backup", b.words().all().single().translation)
        } finally {
            a.close(); b.close(); root.deleteRecursively()
        }
    }

    @Test
    fun `a newer archive is refused`() = runBlocking {
        val root = File(app.cacheDir, "backup-cat-${UUID.randomUUID()}")
        val dest = context(root)
        val db = YomuDatabase.build(dest, true)
        try {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("backup.json"))
                z.write("{\"format\":\"yomu-backup\",\"version\":99,\"tables\":{}}".toByteArray())
                z.closeEntry()
            }
            val failure = runCatching {
                BackupManager(dest, db, AppSettings(dest)).restore(out.toByteArray().inputStream())
            }.exceptionOrNull()
            assertTrue(failure is IOException)
        } finally {
            db.close(); root.deleteRecursively()
        }
    }
}
