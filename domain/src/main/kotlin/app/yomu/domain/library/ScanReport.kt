package app.yomu.domain.library

import kotlinx.serialization.Serializable

/** One scan folder in a finished library scan. */
@Serializable
data class ScanFolderResult(
    val uri: String,
    val name: String,
    val added: Int = 0,
    /** The tree could not be read: a revoked grant, an unplugged card or a provider that is temporarily away. */
    val unavailable: Boolean = false,
    val reason: String? = null,
)

/**
 * The outcome of the last library scan (DL03). A folder that cannot be read is reported as *temporarily unavailable*
 * and nothing is deleted: no local book leaves the library because a card was not mounted for a minute.
 */
@Serializable
data class ScanReport(
    val finishedAt: Long,
    val folders: List<ScanFolderResult> = emptyList(),
) {
    val added: Int get() = folders.sumOf { it.added }
    val unavailable: List<ScanFolderResult> get() = folders.filter { it.unavailable }
    val isPartial: Boolean get() = unavailable.isNotEmpty()
}
