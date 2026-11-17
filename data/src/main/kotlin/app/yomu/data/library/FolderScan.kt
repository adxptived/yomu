package app.yomu.data.library

import app.yomu.domain.library.ScanFolderResult

/** One folder's scan: what was imported and whether the folder could be read at all. */
data class FolderScan(
    val result: ScanFolderResult,
    val outcomes: List<ImportOutcome>,
)
