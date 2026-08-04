package ru.notiprint.printer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A printer accepts only one SPP data stream at a time.  The manual test and the
 * background queue run in the same app process, so they must share this gate.
 */
object PrinterConnectionGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
