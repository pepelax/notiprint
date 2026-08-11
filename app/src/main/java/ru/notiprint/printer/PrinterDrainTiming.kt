package ru.notiprint.printer

/** Estimates when the printer has consumed the final raster bytes sent over SPP. */
internal object PrinterDrainTiming {
    private const val PRINT_ROWS_PER_SECOND = 240L
    private const val SAFETY_MARGIN_MS = 300L
    private const val MIN_DRAIN_MS = 300L
    private const val MAX_DRAIN_MS = 30_000L

    fun delayMillis(bitmapHeight: Int, elapsedSendMillis: Long): Long {
        require(bitmapHeight > 0) { "Bitmap height must be positive" }
        val expectedPrintMillis =
            (bitmapHeight.toLong() * 1_000L + PRINT_ROWS_PER_SECOND - 1) / PRINT_ROWS_PER_SECOND
        return (expectedPrintMillis - elapsedSendMillis.coerceAtLeast(0L) + SAFETY_MARGIN_MS)
            .coerceIn(MIN_DRAIN_MS, MAX_DRAIN_MS)
    }
}
