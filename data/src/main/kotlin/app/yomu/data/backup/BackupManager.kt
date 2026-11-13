package app.yomu.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(val tables: Int, val rows: Int, val files: Int, val skipped: Int = 0)

/**
 * Backup = zip with backup.json (settings + database tables as JSON rows) and optionally the copied book files and covers.
 * Large derived tables (dictionary entries, caches) are not included; dictionaries must be imported again.
 */
class BackupManager(private val context: Context, private val db: YomuDatabase, private val settings: AppSettings) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun create(out: OutputStream, includeFiles: Boolean) = withContext(Dispatchers.IO) {
        val (tables, folderPositions) = db.withTransaction {
            val sqlDb = db.openHelper.readableDatabase
            val tableData = buildJsonObject {
                TABLES.forEach { t ->
                    val rows = mutableListOf<JsonElement>()
                    sqlDb.query("SELECT * FROM `$t`").use { c -> while (c.moveToNext()) rows += row(c) }
                    put(t, JsonArray(rows))
                }
            }
            val folderIds = db.books().all().filter { it.format == "FOLDER" }.mapNotNull { it.fileHash }.toSet()
            val positions = db.folderSyncBases().forTarget(FolderRevisionCommit.LOCAL).mapNotNull { base ->
                // An unknown/corrupt base must not be invented or silently trusted after restore.
                val entry = runCatching { FolderManifestCodec.decode(base.revisionJson.toByteArray()).folders.single() }.getOrNull()
                if (entry == null || entry.id != base.folderId || entry.id !in folderIds) null else JsonPrimitive(base.revisionJson)
            }
            tableData to JsonArray(positions)
        }
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", System.currentTimeMillis())
            put("filesDir", context.filesDir.absolutePath)
            put("settings", settings.exportJson())
            put("tables", tables)
            put("folderPositionRevisions", folderPositions)
        }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(root.toString().toByteArray())
            zip.closeEntry()
            if (includeFiles) {
                // audiobooks holds online-book manifests (.yaudio) and their downloaded tracks; without it a restored
                // book has a row but no manifest to open.
                listOf("books", "synced-books", "covers", "downloads/books", "fonts", "audiobooks").forEach { sub ->
                    val dir = File(context.filesDir, sub)
                    dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".part.meta") }.forEach { f ->
                        zip.putNextEntry(ZipEntry("files/" + f.relativeTo(context.filesDir).invariantSeparatorsPath))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } else {
                File(context.filesDir, "covers").walkTopDown().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry("files/" + f.relativeTo(context.filesDir).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun row(c: Cursor): JsonObject = buildJsonObject {
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> put(name, JsonNull)
                Cursor.FIELD_TYPE_INTEGER -> put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_STRING -> put(name, c.getString(i))
                else -> put(name, JsonNull)
            }
        }
    }

    suspend fun restore(input: InputStream): RestoreResult = withContext(NonCancellable + Dispatchers.IO) {
        // Extract to a private staging directory first. No existing book or cover may be
        // overwritten by a broken, conflicting or malicious archive.
        val filesRoot = context.filesDir.canonicalFile
        val staging = java.nio.file.Files.createTempDirectory(filesRoot.toPath(), ".yomu-restore-").toFile()
        try {
            var root: JsonObject? = null
            val stagedFiles = LinkedHashMap<String, File>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "backup.json" -> {
                            if (root != null || e.isDirectory) throw java.io.IOException("Duplicate or invalid backup.json")
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                if (out.size().toLong() + n > 128L * 1024 * 1024) throw java.io.IOException("Backup metadata is too large")
                                out.write(buffer, 0, n)
                            }
                            root = json.parseToJsonElement(SyncJson.text(out.toByteArray())).jsonObject
                        }
                        e.name.startsWith("files/") && !e.isDirectory -> {
                            val rel = e.name.removePrefix("files/")
                            val segments = rel.split('/')
                            val allowed = listOf(
                                "books/", "synced-books/", "covers/", "downloads/books/", "fonts/", "audiobooks/",
                            )
                            if (allowed.none { rel.startsWith(it) } || segments.any { it.isEmpty() || it == "." || it == ".." || '\\' in it || it.any { c -> c.code < 32 } })
                                throw java.io.IOException("Unsafe file path in backup")
                            if (rel in stagedFiles) throw java.io.IOException("Duplicate file path in backup: $rel")
                            val staged = File(staging, rel)
                            if (!staged.parentFile.mkdirs() && !staged.parentFile.isDirectory) throw java.io.IOException("Cannot stage backup files")
                            staged.outputStream().use { zip.copyTo(it) }
                            stagedFiles[rel] = staged
                        }
                    }
                    zip.closeEntry()
                }
            }
            val r = root ?: error("backup.json missing")
            require(r["format"]?.jsonPrimitive?.content == FORMAT) { "Not a Yomu backup" }
            val oldFiles = r["filesDir"]?.jsonPrimitive?.content
            val newFiles = context.filesDir.absolutePath
            val tables = r["tables"]?.jsonObject ?: JsonObject(emptyMap())
            // Audiobook manifests keep absolute paths to their downloaded tracks; rows are rewritten on insert, the
            // manifest files themselves must be rewritten too, or the restored book points at the old installation.
            if (oldFiles != null && oldFiles != newFiles) {
                stagedFiles.forEach { (rel, staged) ->
                    if (rel.startsWith("audiobooks/") && rel.substringBefore('?').endsWith(".${app.yomu.domain.model.AudiobookManifest.EXTENSION}", ignoreCase = true)) {
                        runCatching {
                            val text = staged.readText()
                            if (oldFiles in text) staged.writeText(text.replace(oldFiles, newFiles))
                        }
                    }
                }
            }
            // A revision qualifies local reading positions, but per-target merge bases must be reset.
            // Old backups contain no such revision; mark stable folders as unknown and fail closed.
            val restoredFolderIds = (tables["books"] as? JsonArray).orEmpty().mapNotNull { element ->
                val book = element.jsonObject
                val format = (book["format"] as? JsonPrimitive)?.content
                val hash = (book["fileHash"] as? JsonPrimitive)?.content
                hash?.takeIf { format == "FOLDER" && FolderManifestCodec.validId(it) }
            }
            require(restoredFolderIds.size == restoredFolderIds.toSet().size) { "Duplicate restored folder identity" }
            val positions = LinkedHashMap<String, String>()
            val rawPositions = r["folderPositionRevisions"]
            if (rawPositions != null && rawPositions !is JsonArray) throw java.io.IOException("Invalid folder position revisions in backup")
            for (item in (rawPositions as? JsonArray).orEmpty()) {
                val raw = (item as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw java.io.IOException("Invalid folder position revision in backup")
                val entry = FolderManifestCodec.decode(raw.toByteArray()).folders.singleOrNull()
                    ?: throw java.io.IOException("Invalid folder position revision in backup")
                if (entry.id !in restoredFolderIds || positions.put(entry.id, raw) != null)
                    throw java.io.IOException("Unexpected or duplicate folder position revision in backup")
            }
            // Validate every destination before publishing any staged file. Only files inside the
            // app's known backup directories are accepted, and symlink aliases are rejected.
            val destinations = stagedFiles.map { (rel, staged) ->
                val destination = File(filesRoot, rel).absoluteFile
                if (destination.canonicalPath != destination.path || !destination.path.startsWith(filesRoot.path + File.separator))
                    throw java.io.IOException("Unsafe backup destination: $rel")
                if (destination.exists() && (!destination.isFile || !sameFile(destination, staged)))
                    throw java.io.IOException("Backup file conflicts with an existing file: $rel")
                staged to destination
            }
            val sqlDb = db.openHelper.writableDatabase
            var rows = 0
            var skipped = 0
            // Files created by this restore. If the transaction fails, they are removed again so a failed restore never
            // leaves orphan files next to a rolled-back database.
            val published = mutableListOf<File>()
            sqlDb.beginTransaction()
            try {
                // A numeric primary key means nothing across two independent libraries: books.id = 1 is a different
                // book on another device. Keeping the local parent row and inserting the archive's child rows would
                // attach bookmarks/notes/progress to a foreign book. Refuse the archive before anything is written.
                refuseIdentityConflicts(sqlDb, tables, oldFiles, newFiles)
                // A matching book row does not imply that its current locators still use the
                // backup's page revision. Never replace an existing local baseline silently.
                for (id in restoredFolderIds) {
                    val existing = sqlDb.query("SELECT id FROM books WHERE fileHash = ?", arrayOf(id)).use { cursor ->
                        val ids = mutableListOf<Long>()
                        while (cursor.moveToNext()) ids += cursor.getLong(0)
                        ids
                    }
                    if (existing.size > 1) throw java.io.IOException("Duplicate existing folder identity")
                    if (existing.isNotEmpty()) {
                        val incomingId = (tables["books"] as JsonArray).map { it.jsonObject }
                            .single { (it["fileHash"] as? JsonPrimitive)?.content == id }["id"]?.jsonPrimitive?.longOrNull
                        if (incomingId == null || existing.single() != incomingId)
                            throw java.io.IOException("Backup folder identity conflicts with an existing book: $id")
                        val current = sqlDb.query(
                            "SELECT revisionJson FROM folder_sync_bases WHERE targetId = ? AND folderId = ?",
                            arrayOf(FolderRevisionCommit.LOCAL, id),
                        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        val incoming = positions[id]
                        val currentEntry = current?.takeIf { it != "unknown-after-restore" }?.let { raw -> runCatching { FolderManifestCodec.decode(raw.toByteArray()).folders.single() }.getOrNull() }
                        val incomingEntry = incoming?.let { raw -> FolderManifestCodec.decode(raw.toByteArray()).folders.single() }
                        if (current != null && current != "unknown-after-restore" && currentEntry == null)
                            throw java.io.IOException("Existing folder page revision is invalid: $id")
                        // The rest of the backup must not be abandoned because this device read further in one folder:
                        // keep the local page revision (local wins) and report the skipped record.
                        if (currentEntry != null && (incomingEntry == null || incomingEntry != currentEntry)) {
                            positions[id] = current!!
                            skipped++
                        }
                    }
                }
                TABLES.forEach { t ->
                    val list = tables[t] as? JsonArray ?: return@forEach
                    val columns = mutableSetOf<String>()
                    val pkColumns = mutableListOf<String>()
                    // Required columns without a default (added after the backup was made) get a neutral value.
                    val required = mutableMapOf<String, String>()
                    sqlDb.query("PRAGMA table_info(`$t`)").use { c ->
                        while (c.moveToNext()) {
                            val name = c.getString(c.getColumnIndexOrThrow("name"))
                            columns += name
                            val notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                            val hasDefault = !c.isNull(c.getColumnIndexOrThrow("dflt_value"))
                            val pk = c.getInt(c.getColumnIndexOrThrow("pk")) > 0
                            if (notNull && !hasDefault && !pk) required[name] = c.getString(c.getColumnIndexOrThrow("type")).orEmpty().uppercase()
                            if (pk) pkColumns += name
                        }
                    }
                    list.forEach { el ->
                        val obj = el.jsonObject
                        val values = ContentValues()
                        required.forEach { (k, type) ->
                            if (obj[k] == null || obj[k] is JsonNull) {
                                when {
                                    type.contains("INT") -> values.put(k, 0L)
                                    type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> values.put(k, 0.0)
                                    else -> values.put(k, "")
                                }
                            }
                        }
                        obj.forEach { (k, v) ->
                            if (k !in columns) return@forEach
                            val p = v as? JsonPrimitive
                            when {
                                p == null || v is JsonNull -> if (k !in required) values.putNull(k)
                                p.isString -> values.put(k, if (oldFiles != null && oldFiles != newFiles) p.content.replace(oldFiles, newFiles) else p.content)
                                p.longOrNull != null -> values.put(k, p.longOrNull)
                                p.doubleOrNull != null -> values.put(k, p.doubleOrNull)
                                p.booleanOrNull != null -> values.put(k, if (p.booleanOrNull == true) 1 else 0)
                            }
                        }
                        try {
                            sqlDb.insert(t, SQLiteDatabase.CONFLICT_ABORT, values)
                            rows++
                        } catch (e: android.database.sqlite.SQLiteConstraintException) {
                            // An exact pre-existing row is harmless; a row whose primary key already exists means this
                            // device is newer, so it is kept. Any other conflict must not replace a newer record or
                            // cascade-delete its dependent data.
                            val entries = values.keySet().map { key -> key to values.get(key) }
                            val where = entries.joinToString(" AND ") { "`${it.first}` IS ?" }
                            val query = androidx.sqlite.db.SimpleSQLiteQuery(
                                "SELECT 1 FROM `$t` WHERE $where LIMIT 1", entries.map { it.second }.toTypedArray(),
                            )
                            val same = sqlDb.query(query).use { it.moveToFirst() }
                            if (same) {
                                skipped++
                                return@forEach
                            }
                            if (pkColumns.isNotEmpty() && pkColumns.all { values.containsKey(it) }) {
                                val keyWhere = pkColumns.joinToString(" AND ") { "`$it` = ?" }
                                val args = pkColumns.map { values.get(it) }.toTypedArray()
                                val exists = sqlDb.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT 1 FROM `$t` WHERE $keyWhere LIMIT 1", args))
                                    .use { it.moveToFirst() }
                                if (exists) {
                                    skipped++
                                    return@forEach
                                }
                            }
                            throw java.io.IOException("Backup conflicts with existing data in $t", e)
                        }
                    }
                }
                // Publish the staged files while the rows are still in the transaction: if anything below fails, the
                // transaction is rolled back and the files created here are removed again.
                for ((staged, destination) in destinations) {
                    if (destination.canonicalPath != destination.path) throw java.io.IOException("Unsafe backup destination: ${destination.name}")
                    if (destination.exists()) {
                        if (!destination.isFile || !sameFile(destination, staged)) throw java.io.IOException("Backup file changed during restore")
                        continue
                    }
                    if (!destination.parentFile.mkdirs() && !destination.parentFile.isDirectory) throw java.io.IOException("Cannot restore backup directory")
                    if (destination.canonicalPath != destination.path) throw java.io.IOException("Unsafe backup destination: ${destination.name}")
                    // A hard link atomically installs complete staged bytes without replacing an existing
                    // file. The stage is in filesDir, so both paths are on the same filesystem.
                    try {
                        java.nio.file.Files.createLink(destination.toPath(), staged.toPath())
                        published += destination
                    } catch (e: java.nio.file.FileAlreadyExistsException) {
                        if (!destination.isFile || !sameFile(destination, staged)) throw java.io.IOException("Backup file changed during restore", e)
                    } catch (e: Exception) {
                        throw java.io.IOException("Cannot safely restore backup file: ${destination.name}", e)
                    }
                }
                // Do not reuse per-target bases. Restore only locally qualified positions; if the
                // older backup has none, an explicit unknown marker prevents guessing on next sync.
                sqlDb.execSQL("DELETE FROM folder_sync_bases WHERE targetId != ?", arrayOf(FolderRevisionCommit.LOCAL))
                for (id in restoredFolderIds) {
                    val values = ContentValues().apply {
                        put("targetId", FolderRevisionCommit.LOCAL)
                        put("folderId", id)
                        put("revisionJson", positions[id] ?: "unknown-after-restore")
                    }
                    sqlDb.insert("folder_sync_bases", SQLiteDatabase.CONFLICT_REPLACE, values)
                }
                // Backups of 1.0–1.4 carry online library categories: they become tabs, like on upgrade.
                app.yomu.core.database.YomuDatabase.categoriesToTabs(sqlDb)
                sqlDb.execSQL("INSERT INTO books_fts(books_fts) VALUES('rebuild')")
                sqlDb.setTransactionSuccessful()
            } catch (e: Throwable) {
                published.forEach { file -> runCatching { java.nio.file.Files.deleteIfExists(file.toPath()) } }
                throw e
            } finally {
                sqlDb.endTransaction()
            }
            db.invalidationTracker.refreshVersionsAsync()
            // The data is not what the last sync left: the next sync only adds and applies deletion marks.
            SyncManager.clearBase(context)
            (r["settings"] as? JsonObject)?.let { settings.importJson(it) }
            RestoreResult(tables.size, rows, stagedFiles.size, skipped)
        } finally { staging.deleteRecursively() }
    }

    /**
     * Refuses a backup whose rows collide with local rows they cannot be proven to describe. Numeric primary keys are
     * meaningless across independent libraries (books.id = 1 is a different book on another device), so keeping the
     * local parent while inserting the archive's children would attach bookmarks, notes and progress to a foreign book.
     * Runs before anything is inserted, so a refusal leaves the database and the files untouched.
     */
    private fun refuseIdentityConflicts(sqlDb: SupportSQLiteDatabase, tables: JsonObject, oldFiles: String?, newFiles: String) {
        fun field(obj: JsonObject, key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        fun identity(table: String, obj: JsonObject): String? = when (table) {
            "books" -> field(obj, "fileHash")?.takeIf { it.isNotBlank() }?.let { "h:$it" }
                ?: "u:" + field(obj, "uri").orEmpty()
            // Backups made before tabs got a kind have no value there; the entity's default is BOOK.
            "collections" -> (field(obj, "kind")?.takeIf { it.isNotBlank() } ?: "BOOK") + "\u0000" + field(obj, "name").orEmpty()
            else -> IDENTITY[table]?.joinToString("\u0000") { field(obj, it).orEmpty() }
        }

        fun localIdentities(table: String): Map<Long, String> {
            val columns = IDENTITY[table] ?: return emptyMap()
            val fields = if (table == "books") listOf("fileHash", "uri") else columns
            val out = HashMap<Long, String>()
            sqlDb.query("SELECT id, ${fields.joinToString(", ") { "`$it`" }} FROM `$table`").use { c ->
                while (c.moveToNext()) {
                    out[c.getLong(0)] = if (table == "books") {
                        val hash = c.getString(1)
                        if (!hash.isNullOrBlank()) "h:$hash" else "u:" + c.getString(2).orEmpty()
                    } else {
                        fields.indices.joinToString("\u0000") { c.getString(it + 1).orEmpty() }
                    }
                }
            }
            return out
        }

        val archive = HashMap<String, Map<Long, JsonObject>>()
        for (table in IDENTITY.keys) {
            val rows = tables[table] as? JsonArray ?: continue
            val byId = HashMap<Long, JsonObject>()
            for (el in rows) {
                val obj = el as? JsonObject ?: continue
                val id = (obj["id"] as? JsonPrimitive)?.longOrNull ?: continue
                byId[id] = obj
            }
            archive[table] = byId
        }
        val localCache = HashMap<String, Map<Long, String>>()
        fun localOf(table: String): Map<Long, String> = localCache.getOrPut(table) { localIdentities(table) }

        for ((table, byId) in archive) {
            val local = localOf(table)
            for ((id, obj) in byId) {
                val current = local[id] ?: continue
                // The archive keeps the paths of the old installation; compare them as they will be restored.
                val incoming = identity(table, obj)?.let { if (table == "books" && oldFiles != null) it.replace(oldFiles, newFiles) else it } ?: continue
                if (incoming != current) throw java.io.IOException(
                    "This backup belongs to a different library: $table id $id is a different record here. Restore it into an empty library.",
                )
            }
        }

        // A child row of the archive may not point at a local parent that the archive does not itself describe.
        val localIds = HashMap<String, Set<Long>>()
        fun localIdsOf(table: String): Set<Long> = localIds.getOrPut(table) {
            val hasId = sqlDb.query("PRAGMA table_info(`$table`)").use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == "id") found = true
                found
            }
            if (!hasId) emptySet() else HashSet<Long>().also { ids ->
                sqlDb.query("SELECT id FROM `$table`").use { c -> while (c.moveToNext()) ids += c.getLong(0) }
            }
        }
        for ((table, refs) in REFERENCES) {
            val rows = tables[table] as? JsonArray ?: continue
            for (el in rows) {
                val obj = el as? JsonObject ?: continue
                // A row whose primary key already exists locally is kept as it is; its references are not applied.
                val id = (obj["id"] as? JsonPrimitive)?.longOrNull
                if (id != null && id in localIdsOf(table)) continue
                for ((column, parent) in refs) {
                    val parentId = (obj[column] as? JsonPrimitive)?.longOrNull ?: continue
                    if (parentId !in localOf(parent)) continue
                    if (archive[parent]?.containsKey(parentId) != true)
                        throw java.io.IOException("Backup row $table references a $parent the archive does not describe; refusing to mix libraries")
                }
            }
        }
    }

    private fun sameFile(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        return a.inputStream().use { left -> b.inputStream().use { right ->
            BookFileSync.digest(left).sha256 == BookFileSync.digest(right).sha256
        } }
    }

    fun suggestedName(): String = "yomu_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".yomubackup"

    /**
     * Writes a backup into the document [uri]. A backup without book files is built in the cache first and copied
     * whole, so the document never holds a half-written zip; with files (too big to stage twice) it is written
     * directly. Either way a failed or cancelled backup removes the document instead of leaving a broken file that
     * looks like a backup (and would push an older good one out of the automatic rotation).
     */
    suspend fun createTo(uri: Uri, includeFiles: Boolean) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            if (includeFiles) {
                resolver.openOutputStream(uri)?.use { create(it, includeFiles = true) } ?: error("Cannot write the backup file")
            } else {
                val staged = File.createTempFile("backup-", ".zip", context.cacheDir)
                try {
                    staged.outputStream().use { create(it, includeFiles = false) }
                    withContext(NonCancellable) {
                        resolver.openOutputStream(uri)?.use { out -> staged.inputStream().use { it.copyTo(out) } } ?: error("Cannot write the backup file")
                    }
                } finally {
                    staged.delete()
                }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            throw e
        }
    }

    /** Writes a backup into the configured SAF folder and keeps only the newest N files. */
    suspend fun autoBackup() = withContext(Dispatchers.IO) {
        val folder = settings.backupFolder.get().takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return@withContext
        val treeDoc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val uri = DocumentsContract.createDocument(context.contentResolver, treeDoc, "application/zip", suggestedName()) ?: error("Cannot create backup file")
        createTo(uri, includeFiles = false)
        settings.lastBackupAt.set(System.currentTimeMillis())
        val keep = settings.backupKeep.get().coerceAtLeast(1)
        val files = app.yomu.data.library.SafTree.walk(context, folder, maxDepth = 0, includeDirs = false)
            .filter { it.name.startsWith("yomu_") && it.name.endsWith(".yomubackup") }
            .sortedByDescending { it.name }
        files.drop(keep).forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it.uri) } }
    }

    companion object {
        const val FORMAT = "yomu-backup"
        const val VERSION = 1

        /** Columns that identify a row across independently created libraries; null = the table has no stable key. */
        private val IDENTITY = mapOf(
            "books" to listOf("fileHash", "uri"),
            "authors" to listOf("name"),
            "series" to listOf("name"),
            "tags" to listOf("name"),
            "collections" to listOf("kind", "name"),
            "catalog_items" to listOf("sourceId", "url"),
            "words" to listOf("normalized", "lang"),
            "chapters" to listOf("itemId", "url"),
            "bookmarks" to listOf("bookId", "createdAt"),
            "highlights" to listOf("bookId", "chapter", "createdAt", "text"),
            "notes" to listOf("bookId", "createdAt"),
        )

        /** Columns that hold a primary key of another table. */
        private val REFERENCES = mapOf(
            "books" to mapOf("seriesId" to "series"),
            "book_authors" to mapOf("bookId" to "books", "authorId" to "authors"),
            "book_collections" to mapOf("bookId" to "books", "collectionId" to "collections"),
            "book_tags" to mapOf("bookId" to "books", "tagId" to "tags"),
            "catalog_collections" to mapOf("itemId" to "catalog_items", "collectionId" to "collections"),
            "reading_progress" to mapOf("bookId" to "books"),
            "chapter_progress" to mapOf("bookId" to "books"),
            "bookmarks" to mapOf("bookId" to "books"),
            "highlights" to mapOf("bookId" to "books"),
            "notes" to mapOf("bookId" to "books", "highlightId" to "highlights"),
            "words" to mapOf("bookId" to "books"),
            "reading_sessions" to mapOf("bookId" to "books", "catalogItemId" to "catalog_items"),
            "chapters" to mapOf("itemId" to "catalog_items"),
            "tracks" to mapOf("itemId" to "catalog_items", "bookId" to "books"),
            "downloads" to mapOf("chapterId" to "chapters", "itemId" to "catalog_items", "sourceId" to "sources"),
        )

        val TABLES = listOf(
            "books", "authors", "book_authors", "series", "collections", "book_collections", "tags", "book_tags",
            "reading_progress", "chapter_progress", "bookmarks", "highlights", "notes", "words", "sources", "catalog_items", "chapters",
            "catalog_collections", "reading_sessions", "extension_repos", "lookup_history", "tracks",
        )
    }
}
