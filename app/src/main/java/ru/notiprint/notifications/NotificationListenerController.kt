package ru.notiprint.notifications

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

object NotificationListenerController {
    /** Reconnect after an app update or when an OEM has reclaimed the listener process. */
    fun requestRebindIfEnabled(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) return

        NotificationListenerService.requestRebind(
            ComponentName(context, NotiPrintNotificationListenerService::class.java),
        )
    }
}
