package app.yomu.data.work

import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.SettingsJson
import app.yomu.domain.catalog.UpdateRunReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores the last finished update check (U01) so the result survives a restart.
 *
 * The update worker used to keep its counts in memory and in a notification only; once the notification was dismissed
 * there was no way to tell whether anything had been asked, what changed or what failed.
 */
class UpdateRunStore(private val settings: AppSettings) {
    val last: Flow<UpdateRunReport?> = settings.updateLastRun.flow.map { json ->
        if (json.isBlank()) null
        else runCatching { SettingsJson.decodeFromString(UpdateRunReport.serializer(), json) }.getOrNull()
    }

    suspend fun record(report: UpdateRunReport) {
        settings.updateLastRun.set(SettingsJson.encodeToString(UpdateRunReport.serializer(), report))
    }

    suspend fun clear() {
        settings.updateLastRun.set("")
    }
}
