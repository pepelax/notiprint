package ru.notiprint.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.notiprint.printer.BluetoothPermissions
import ru.notiprint.service.NotiPrintForegroundService
import ru.notiprint.work.PrintScheduler

/** Restores foreground operation after reboot and retries the queue when Bluetooth returns. */
class PrintRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BluetoothPermissions.hasConnectPermission(context)) {
            NotiPrintForegroundService.start(context.applicationContext)
        }
        PrintScheduler.enqueueNow(context.applicationContext)
    }
}
