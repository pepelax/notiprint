package ru.notiprint.settings

import android.content.Context

data class AppSettings(
    val printerAddress: String?,
    val printerName: String?,
    val smsEnabled: Boolean,
    val missedCallsEnabled: Boolean,
    val calendarEnabled: Boolean,
    val nightModeEnabled: Boolean,
    val nightStartMinutes: Int,
    val nightEndMinutes: Int,
    val ignoreSmsFromUnknownNumbers: Boolean = false,
)

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun snapshot(): AppSettings = AppSettings(
        printerAddress = preferences.getString(KEY_PRINTER_ADDRESS, null),
        printerName = preferences.getString(KEY_PRINTER_NAME, null),
        smsEnabled = preferences.getBoolean(KEY_SMS_ENABLED, true),
        missedCallsEnabled = preferences.getBoolean(KEY_MISSED_CALLS_ENABLED, true),
        calendarEnabled = preferences.getBoolean(KEY_CALENDAR_ENABLED, true),
        nightModeEnabled = preferences.getBoolean(KEY_NIGHT_MODE_ENABLED, true),
        nightStartMinutes = preferences.getInt(KEY_NIGHT_START, 22 * 60),
        nightEndMinutes = preferences.getInt(KEY_NIGHT_END, 8 * 60),
        ignoreSmsFromUnknownNumbers = preferences.getBoolean(KEY_IGNORE_UNKNOWN_SMS, false),
    )

    fun setPrinter(address: String, name: String) {
        preferences.edit()
            .putString(KEY_PRINTER_ADDRESS, address)
            .putString(KEY_PRINTER_NAME, name)
            .apply()
    }

    fun setSmsEnabled(enabled: Boolean) = putBoolean(KEY_SMS_ENABLED, enabled)

    fun setMissedCallsEnabled(enabled: Boolean) = putBoolean(KEY_MISSED_CALLS_ENABLED, enabled)

    fun setCalendarEnabled(enabled: Boolean) = putBoolean(KEY_CALENDAR_ENABLED, enabled)

    fun setNightModeEnabled(enabled: Boolean) = putBoolean(KEY_NIGHT_MODE_ENABLED, enabled)

    fun setNightStartMinutes(minutes: Int) = putInt(KEY_NIGHT_START, minutes)

    fun setNightEndMinutes(minutes: Int) = putInt(KEY_NIGHT_END, minutes)

    fun setIgnoreSmsFromUnknownNumbers(enabled: Boolean) = putBoolean(KEY_IGNORE_UNKNOWN_SMS, enabled)

    private fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    private companion object {
        const val NAME = "notiprint_settings"
        const val KEY_PRINTER_ADDRESS = "printer_address"
        const val KEY_PRINTER_NAME = "printer_name"
        const val KEY_SMS_ENABLED = "sms_enabled"
        const val KEY_MISSED_CALLS_ENABLED = "missed_calls_enabled"
        const val KEY_CALENDAR_ENABLED = "calendar_enabled"
        const val KEY_NIGHT_MODE_ENABLED = "night_mode_enabled"
        const val KEY_NIGHT_START = "night_start"
        const val KEY_NIGHT_END = "night_end"
        const val KEY_IGNORE_UNKNOWN_SMS = "ignore_unknown_sms"
    }
}
