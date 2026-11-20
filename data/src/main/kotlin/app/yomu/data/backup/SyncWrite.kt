package app.yomu.data.backup

import androidx.room.withTransaction
import app.yomu.core.database.YomuDatabase

/**
 * Merges with the shared state and publishes it.
 *
 * The merge runs in a database transaction; the network write runs after it has committed, so an HTTP/WebDAV request
 * never holds SQLite's only writer transaction (reading progress, downloads and chapter marks keep working while the
 * server is slow). If the conditional write fails, the merge is not lost: the sync base is only advanced after a
 * successful write, so the next cycle merges the same remote state again and publishes the local changes. A server that
 * accepted a write immediately before an interrupted response is handled the same way: the next read merges it.
 */
internal suspend fun mergeAndWrite(
    db: YomuDatabase,
    backend: SyncBackend,
    previous: SyncFile?,
    remote: SyncState?,
    base: SyncBase?,
    now: Long,
    options: SyncMerger.Options,
): SyncMerger.Outcome {
    val result = db.withTransaction { SyncMerger(db).merge(remote, base, backend.id, now, options) }
    // Unknown categories from a newer client travel with the file untouched.
    backend.write(SyncCodec.encode(result.state).toByteArray(), previous)
    return result
}
