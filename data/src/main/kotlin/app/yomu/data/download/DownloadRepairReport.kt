package app.yomu.data.download

/** What a download index repair changed. Orphans are reported, never deleted: they may be files the user owns. */
data class DownloadRepairReport(
    val restored: Int,
    val missing: Int,
    val orphans: Int,
) {
    val changed: Boolean get() = restored > 0 || missing > 0
}
