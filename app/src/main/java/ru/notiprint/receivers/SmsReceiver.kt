package ru.notiprint.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.notiprint.data.AppDatabase
import ru.notiprint.data.SenderIdentifier
import ru.notiprint.data.NotificationKind
import ru.notiprint.data.PrintJob
import ru.notiprint.settings.AppPreferences
import ru.notiprint.work.PrintScheduler

/** Receives carrier SMS directly, independently of the notification style used by the SMS application. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = AppPreferences(context).snapshot()
                if (!settings.smsEnabled) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return@launch

                val sender = messages.first().originatingAddress?.trim().orEmpty().ifBlank { "Неизвестный отправитель" }
                val text = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
                if (text.isBlank()) return@launch
                val database = AppDatabase.get(context)
                if (
                    SenderIdentifier.normalize(sender)
                        ?.let { database.blockedSenderDao().isBlocked(it) } == true
                ) {
                    return@launch
                }
                val contactName = findContactName(context, sender)
                // Sender IDs such as "МЧС" or "SBERBANK" are not phone numbers.
                // They must not be suppressed by an option that explicitly applies
                // to unfamiliar *numbers*.
                if (
                        settings.ignoreSmsFromUnknownNumbers &&
                        contactName == null &&
                        SenderIdentifier.isNumericAddress(sender)
                ) {
                    return@launch
                }

                val receivedAt = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                val sourceKey = "sms:$sender:$receivedAt:${text.hashCode()}"
                val inserted = database.printJobDao().insert(
                    PrintJob(
                        notificationKey = sourceKey,
                        kind = NotificationKind.SMS,
                        // The title is rendered in large type. Always retain the number even when a contact is found.
                        title = if (contactName == null) sender else "$contactName\n$sender",
                        message = text,
                        packageName = "android.provider.Telephony",
                        postedAt = receivedAt,
                    ),
                )
                if (inserted != -1L) PrintScheduler.enqueueNow(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun findContactName(context: Context, phoneNumber: String): String? {
        if (
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        return context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.ifBlank { null } else null
        }
    }

}
