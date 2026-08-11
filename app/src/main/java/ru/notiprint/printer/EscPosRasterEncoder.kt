package ru.notiprint.printer

import android.graphics.Bitmap
import kotlin.math.min

/** Encodes a 384-dot bitmap using ESC/POS GS v 0 raster commands. */
object EscPosRasterEncoder {
    const val PRINT_WIDTH_DOTS = 384
    const val DEFAULT_BAND_HEIGHT = 128
    private const val BYTES_PER_ROW = PRINT_WIDTH_DOTS / 8
    private const val MAX_COMMAND_HEIGHT = 0xFFFF

    fun encode(bitmap: Bitmap, bandHeight: Int = DEFAULT_BAND_HEIGHT): List<ByteArray> {
        require(bitmap.width == PRINT_WIDTH_DOTS) {
            "Expected a ${PRINT_WIDTH_DOTS}px-wide bitmap, got ${bitmap.width}px"
        }
        require(bandHeight in 1..MAX_COMMAND_HEIGHT) {
            "Raster command height must be between 1 and $MAX_COMMAND_HEIGHT rows"
        }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        return buildList {
            var bandTop = 0
            while (bandTop < bitmap.height) {
                val height = min(bandHeight, bitmap.height - bandTop)
                val command = ByteArray(8 + BYTES_PER_ROW * height)
                command[0] = 0x1D
                command[1] = 0x76
                command[2] = 0x30
                command[3] = 0x00 // Normal density in both directions.
                command[4] = BYTES_PER_ROW.toByte()
                command[5] = 0x00
                command[6] = (height and 0xFF).toByte()
                command[7] = ((height shr 8) and 0xFF).toByte()

                for (y in 0 until height) {
                    val sourceOffset = (bandTop + y) * bitmap.width
                    val destinationOffset = 8 + y * BYTES_PER_ROW
                    for (x in 0 until PRINT_WIDTH_DOTS) {
                        if (isBlack(pixels[sourceOffset + x])) {
                            val byteIndex = destinationOffset + x / 8
                            command[byteIndex] = (command[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
                        }
                    }
                }

                add(command)
                bandTop += height
            }
        }
    }

    private fun isBlack(color: Int): Boolean {
        val alpha = color ushr 24 and 0xFF
        if (alpha < 128) return false
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        val luminance = (red * 299 + green * 587 + blue * 114) / 1000
        return luminance < 160
    }
}
