package app.yomu.data.library

import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.SettingsJson
import app.yomu.domain.library.ScanReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the last library scan so "this folder is temporarily unavailable" survives the worker and is visible in the
 * settings. A missing folder never removes library entries by itself.
 */
class ScanReportStore(private val settings: AppSettings) {
    val last: Flow<ScanReport?> = settings.scanLastReport.flow.map { json ->
        if (json.isBlank()) null else runCatching { SettingsJson.decodeFromString(ScanReport.serializer(), json) }.getOrNull()
    }

    suspend fun record(report: ScanReport) {
        settings.scanLastReport.set(SettingsJson.encodeToString(ScanReport.serializer(), report))
    }

    suspend fun clear() {
        settings.scanLastReport.set("")
    }
}
