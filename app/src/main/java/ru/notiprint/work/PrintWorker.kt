package ru.notiprint.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.notiprint.data.AppDatabase
import ru.notiprint.data.PrintJob
import ru.notiprint.printer.BluetoothPermissions
import ru.notiprint.printer.BluetoothPrinterClient
import ru.notiprint.printer.NotificationBitmapRenderer
import ru.notiprint.settings.AppPreferences
import ru.notiprint.settings.NightMode

class PrintWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = AppPreferences(applicationContext).snapshot()
        if (NightMode.isActive(settings)) {
            PrintScheduler.scheduleMorning(applicationContext)
            return@withContext Result.success()
        }
        if (settings.printerAddress.isNullOrBlank() || !BluetoothPermissions.hasConnectPermission(applicationContext)) {
            return@withContext Result.success()
        }

        val dao = AppDatabase.get(applicationContext).printJobDao()
        dao.recoverInterruptedPrints()
        val jobs = dao.nextJobs(limit = 50)
        if (jobs.isEmpty()) return@withContext Result.success()

        var currentJob: PrintJob? = null
        try {
            BluetoothPrinterClient(applicationContext).use { printer ->
                printer.connect(settings.printerAddress)
                jobs.forEach { job ->
                    currentJob = job
                    dao.markPrinting(job.id)
                    val bitmap = NotificationBitmapRenderer.render(job)
                    try {
                        printer.print(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    dao.markPrinted(job.id)
                    currentJob = null
                }
            }
            Result.success()
        } catch (error: Exception) {
            currentJob?.let { job ->
                dao.markForRetry(job.id, error.message ?: error.javaClass.simpleName)
            }
            Result.retry()
        }
    }
}
