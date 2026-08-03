package ru.notiprint.notifications

import android.app.Notification
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.notiprint.data.AppDatabase
import ru.notiprint.data.PrintJob
import ru.notiprint.settings.AppPreferences
import ru.notiprint.work.PrintScheduler

class NotiPrintNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            // Android does not replay notifications posted while this listener was temporarily disconnected.
            // Scanning the active shade closes that gap after an app update, reboot, or OEM process cleanup.
            for (notification in activeNotifications.orEmpty()) {
                saveIfRelevant(notification)
            }
            PrintScheduler.enqueueNow(applicationContext)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        serviceScope.launch {
            saveIfRelevant(sbn)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun Notification.isGroupSummary(): Boolean = flags and Notification.FLAG_GROUP_SUMMARY != 0

    private suspend fun saveIfRelevant(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.notification.isGroupSummary()) return

        val settings = AppPreferences(applicationContext).snapshot()
        val kind = NotificationClassifier.classify(applicationContext, sbn, settings)
        if (kind == null) {
            Log.d(TAG, "Ignored notification: package=${sbn.packageName}, category=${sbn.notification.category}")
            return
        }
        val title = sbn.notification.extras.readText(Notification.EXTRA_TITLE)
            .ifBlank { sbn.notification.extras.readText(Notification.EXTRA_CONVERSATION_TITLE) }
        val message = sbn.notification.extras.readText(Notification.EXTRA_BIG_TEXT)
            .ifBlank { sbn.notification.extras.readText(Notification.EXTRA_TEXT) }
            .ifBlank { sbn.notification.extras.readTextLines(Notification.EXTRA_TEXT_LINES) }

        if (title.isBlank() && message.isBlank()) {
            Log.d(TAG, "Ignored empty notification: package=${sbn.packageName}, kind=${kind.name}")
            return
        }

        val insertResult = AppDatabase.get(applicationContext).printJobDao().insert(
            PrintJob(
                // Several Android apps keep one stable notification key and merely update its content.
                // postTime changes for a new missed call/message but stays stable for repeated callbacks.
                notificationKey = "${sbn.key}:${sbn.postTime}",
                kind = kind,
                title = if (kind == ru.notiprint.data.NotificationKind.MISSED_CALL) {
                    missedCallTitle(title)
                } else {
                    title
                },
                message = message,
                packageName = sbn.packageName,
                postedAt = sbn.postTime,
            ),
        )
        if (insertResult != -1L) {
            Log.d(TAG, "Queued notification: package=${sbn.packageName}, kind=${kind.name}")
            PrintScheduler.enqueueNow(applicationContext)
        }
    }

    private fun android.os.Bundle.readText(key: String): String =
        getCharSequence(key)?.toString()?.trim().orEmpty()

    @Suppress("DEPRECATION")
    private fun android.os.Bundle.readTextLines(key: String): String =
        getCharSequenceArray(key)
            ?.joinToString("\n") { it.toString().trim() }
            ?.trim()
            .orEmpty()

    private fun missedCallTitle(notificationTitle: String): String {
        val details = MissedCallDetailsResolver.latest(applicationContext)
        val name = details?.name ?: notificationTitle.trim()
        val number = details?.number
        return when {
            number.isNullOrBlank() -> name
            name.isBlank() -> number
            name.contains(number, ignoreCase = true) -> name
            else -> "$name\n$number"
        }
    }

    private companion object {
        const val TAG = "NotiPrintListener"
    }
}
