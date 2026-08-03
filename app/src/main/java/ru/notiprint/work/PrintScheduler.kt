package ru.notiprint.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.notiprint.settings.AppPreferences
import ru.notiprint.settings.NightMode
import java.util.concurrent.TimeUnit

object PrintScheduler {
    private const val PRINT_NOW_WORK = "notiprint-print-now"
    private const val MORNING_WORK = "notiprint-morning-print"

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PrintWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PRINT_NOW_WORK,
            // If a notification arrives during a print, preserve one more worker after it.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun scheduleMorning(context: Context) {
        val settings = AppPreferences(context).snapshot()
        val request = OneTimeWorkRequestBuilder<PrintWorker>()
            .setInitialDelay(NightMode.millisUntilEnd(settings), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MORNING_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
