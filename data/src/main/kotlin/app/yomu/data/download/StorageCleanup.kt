package app.yomu.data.download

import app.yomu.domain.model.ChapterInfo
import java.io.File

/** The pieces of the storage screen that can be removed without losing a title or its progress. */
object StorageCleanup {
    /** Deletes [dir] with everything in it and returns how many files were removed. A missing folder removes nothing. */
    fun clearDirectory(dir: File): Int {
        if (!dir.exists()) return 0
        val files = dir.walkTopDown().count { it.isFile }
        dir.deleteRecursively()
        return files
    }

    /** Chapters whose files can be deleted: only ones the app downloaded and still marks as downloaded. */
    fun downloadedChapterIds(chapters: List<ChapterInfo>): List<Long> =
        chapters.filter { it.downloaded }.map { it.id }
}
