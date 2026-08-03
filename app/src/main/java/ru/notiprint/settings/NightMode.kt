package ru.notiprint.settings

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

object NightMode {
    fun isActive(settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (!settings.nightModeEnabled) return false

        val start = settings.nightStartMinutes.toLocalTime()
        val end = settings.nightEndMinutes.toLocalTime()
        val current = now.toLocalTime()

        if (start == end) return false
        return if (start < end) {
            current >= start && current < end
        } else {
            current >= start || current < end
        }
    }

    fun millisUntilEnd(settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.toLocalDate().atTime(settings.nightEndMinutes.toLocalTime()).atZone(now.zone)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(1_000)
    }

    fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
