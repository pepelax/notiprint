package ru.notiprint.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.notiprint.data.AppDatabase
import ru.notiprint.data.PrintJob
import ru.notiprint.printer.BluetoothPermissions
import ru.notiprint.printer.BluetoothPrinterClient
import ru.notiprint.printer.NotificationBitmapRenderer
import ru.notiprint.printer.PrinterConnectionGate
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
        var currentJob: PrintJob? = null
        try {
            PrinterConnectionGate.withLock {
                // Recovery and queue selection must be serialized with printing.
                // A retry/morning worker starting while another worker prints used
                // to reset its PRINTING row back to PENDING and print it again.
                dao.recoverInterruptedPrints()
                val jobs = dao.nextJobs(limit = 50)
                if (jobs.isEmpty()) return@withLock

                BluetoothPrinterClient(applicationContext).use { printer ->
                    printer.connect(settings.printerAddress)
                    jobs.forEach { job ->
                        currentJob = job
                        Log.i(TAG, "Printing job id=${job.id}, worker=$id")
                        dao.markPrinting(job.id)
                        val bitmap = NotificationBitmapRenderer.render(job)
                        try {
                            printer.print(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                        dao.markPrinted(job.id)
                        Log.i(TAG, "Printed job id=${job.id}, worker=$id")
                        currentJob = null
                    }
                }
            }
            PrintScheduler.resetRetryBackoff(applicationContext)
            Result.success()
        } catch (error: Exception) {
            currentJob?.let { job ->
                dao.markForRetry(job.id, error.message ?: error.javaClass.simpleName)
            }
            // Keep the failed row in the durable queue and retry with a capped
            // exponential delay while a printer is switched off or out of range.
            PrintScheduler.enqueueRetry(applicationContext)
            Result.success()
        }
    }

    private companion object {
        const val TAG = "NotiPrintWorker"
    }
}
