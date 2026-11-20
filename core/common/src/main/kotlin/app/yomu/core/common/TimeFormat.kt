package app.yomu.core.common

import android.content.Context
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The clock preference (Mihon parity 0.21.3). SYSTEM keeps the locale's own clock, HOUR_12 and HOUR_24 force
 * `hh:mm a` / `HH:mm` with the device locale. Dates stay locale-based: only the time of day follows the setting.
 * The stored values are the names of [SYSTEM], [HOUR_12] and [HOUR_24]; anything else (an old or corrupt value)
 * falls back to the system behaviour.
 */
object TimeFormat {
    const val SYSTEM = "SYSTEM"
    const val HOUR_12 = "HOUR_12"
    const val HOUR_24 = "HOUR_24"

    private const val PATTERN_12 = "hh:mm a"
    private const val PATTERN_24 = "HH:mm"

    /** The forced clock formatter, or null when [setting] means "use the device locale". */
    fun clock(setting: String, locale: Locale): DateTimeFormatter? = when (setting) {
        HOUR_12 -> DateTimeFormatter.ofPattern(PATTERN_12, locale)
        HOUR_24 -> DateTimeFormatter.ofPattern(PATTERN_24, locale)
        else -> null
    }

    fun clock(context: Context, setting: String): DateTimeFormatter? = clock(setting, localeOf(context))

    /** Time of day only ("14:30" / "2:30 PM"), with explicit locale and zone for pure tests. */
    fun format(setting: String, locale: Locale, zone: ZoneId, epochMillis: Long): String {
        val forced = clock(setting, locale)
        return if (forced == null) {
            dateFormat(DateFormat.getTimeInstance(DateFormat.SHORT, locale), zone).format(Date(epochMillis))
        } else {
            forced.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
        }
    }

    fun format(context: Context, setting: String, epochMillis: Long): String =
        format(setting, localeOf(context), ZoneId.systemDefault(), epochMillis)

    /**
     * Date and time ("26.11.25, 14:30"). The date part always follows the locale; [systemTimeStyle] is the time
     * style used when the setting is SYSTEM, for call sites that showed seconds before the clock preference existed.
     */
    fun formatDateTime(
        setting: String,
        locale: Locale,
        zone: ZoneId,
        epochMillis: Long,
        systemTimeStyle: Int = DateFormat.SHORT,
    ): String {
        val forced = clock(setting, locale)
        return if (forced == null) {
            dateFormat(DateFormat.getDateTimeInstance(DateFormat.SHORT, systemTimeStyle, locale), zone)
                .format(Date(epochMillis))
        } else {
            val date = dateFormat(DateFormat.getDateInstance(DateFormat.SHORT, locale), zone).format(Date(epochMillis))
            val time = forced.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
            "$date, $time"
        }
    }

    fun formatDateTime(
        context: Context,
        setting: String,
        epochMillis: Long,
        systemTimeStyle: Int = DateFormat.SHORT,
    ): String = formatDateTime(setting, localeOf(context), ZoneId.systemDefault(), epochMillis, systemTimeStyle)

    /** The locale the app actually draws in, including an in-app language change. */
    fun localeOf(context: Context): Locale =
        context.resources.configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()

    private fun dateFormat(f: DateFormat, zone: ZoneId): DateFormat = f.apply { timeZone = TimeZone.getTimeZone(zone) }
}
