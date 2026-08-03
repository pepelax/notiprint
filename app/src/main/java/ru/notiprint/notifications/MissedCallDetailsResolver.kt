package ru.notiprint.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class MissedCallDetails(
    val name: String?,
    val number: String?,
)

/** Reads the latest missed call so its contact name and phone number can be printed separately. */
object MissedCallDetailsResolver {
    fun latest(context: Context): MissedCallDetails? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ?",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC LIMIT 1",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MissedCallDetails(
                name = cursor.getString(0)?.trim()?.ifBlank { null },
                number = cursor.getString(1)?.trim()?.ifBlank { null },
            )
        }
    }
}
