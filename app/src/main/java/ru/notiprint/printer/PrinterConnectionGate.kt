package ru.notiprint.printer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A printer accepts only one SPP data stream at a time. The manual test and every
 * WorkManager chain share this gate. Queue recovery and selection must also happen
 * inside it so two workers cannot claim and print the same database row.
 */
object PrinterConnectionGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
