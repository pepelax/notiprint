package ru.notiprint.printer

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterDrainTimingTest {
    @Test
    fun `delay grows with image height and excludes time already spent sending`() {
        assertEquals(300L, PrinterDrainTiming.delayMillis(bitmapHeight = 10, elapsedSendMillis = 100))
        assertEquals(400L, PrinterDrainTiming.delayMillis(bitmapHeight = 120, elapsedSendMillis = 400))
        assertEquals(717L, PrinterDrainTiming.delayMillis(bitmapHeight = 1_000, elapsedSendMillis = 3_750))
        assertEquals(30_000L, PrinterDrainTiming.delayMillis(bitmapHeight = 10_000, elapsedSendMillis = 0))
    }
}
