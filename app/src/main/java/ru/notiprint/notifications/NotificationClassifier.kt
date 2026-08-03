package ru.notiprint.notifications

import android.app.Notification
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import android.service.notification.StatusBarNotification
import ru.notiprint.data.NotificationKind
import ru.notiprint.settings.AppSettings

object NotificationClassifier {
    private val missedCallPackages = setOf(
        "com.android.contacts",
        "com.android.server.telecom",
        "com.android.phone",
        "com.huawei.contacts",
    )
    private val missedCallChannels = setOf(
        "NC_ID_MISSEDCALL",
        "TelecomMissedCalls",
        "notification_channel_missed_calls",
    )
    private val knownSmsPackages = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
    )
    private val knownCalendarPackages = setOf(
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.android.calendar",
    )

    fun classify(context: Context, notification: StatusBarNotification, settings: AppSettings): NotificationKind? {
        val category = notification.notification.category
        val packageName = notification.packageName

        return when {
            category == Notification.CATEGORY_MISSED_CALL && settings.missedCallsEnabled -> {
                NotificationKind.MISSED_CALL
            }

            settings.missedCallsEnabled && isKnownMissedCall(notification) -> {
                NotificationKind.MISSED_CALL
            }

            category == Notification.CATEGORY_EVENT && settings.calendarEnabled -> {
                NotificationKind.CALENDAR
            }

            category == Notification.CATEGORY_MESSAGE &&
                packageName in smsPackages(context) && settings.smsEnabled && !canReceiveSmsDirectly(context) -> {
                NotificationKind.SMS
            }

            packageName in smsPackages(context) &&
                notification.notification.extras.containsKey(Notification.EXTRA_MESSAGES) &&
                settings.smsEnabled && !canReceiveSmsDirectly(context) -> {
                NotificationKind.SMS
            }

            else -> null
        }
    }

    private fun smsPackages(context: Context): Set<String> {
        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
        return if (defaultPackage == null) knownSmsPackages else knownSmsPackages + defaultPackage
    }

    private fun canReceiveSmsDirectly(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun isKnownMissedCall(notification: StatusBarNotification): Boolean {
        if (notification.packageName !in missedCallPackages) return false
        return notification.tag == "MissedCallNotifier" ||
            notification.notification.channelId in missedCallChannels
    }
}
