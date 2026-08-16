package ru.notiprint.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/** A single SPP connection. It deliberately sends an entire job before closing the socket. */
class BluetoothPrinterClient(private val context: Context) : AutoCloseable {
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun connect(address: String) {
        check(BluetoothPermissions.hasConnectPermission(context)) {
            "Bluetooth permission was not granted"
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("Bluetooth is not available")
        if (!adapter.isEnabled) throw IOException("Bluetooth is disabled")

        // cancelDiscovery() requires BLUETOOTH_SCAN on Android 12+, even
        // though connecting to an already paired device only needs
        // BLUETOOTH_CONNECT. Do not make printing depend on scan access.
        if (BluetoothPermissions.canCancelDiscovery(context)) {
            adapter.cancelDiscovery()
        }
        val device = adapter.getRemoteDevice(address)
        val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            newSocket.connect()
            socket = newSocket
            output = newSocket.outputStream
            output?.write(ESC_INITIALIZE)
        } catch (error: IOException) {
            try {
                newSocket.close()
            } catch (_: IOException) {
                // The original connection error is more useful.
            }
            throw error
        }
    }

    @Throws(IOException::class)
    fun print(
        bitmap: Bitmap,
        feedLines: Int = DEFAULT_FEED_LINES,
        rasterBandHeight: Int = EscPosRasterEncoder.DEFAULT_BAND_HEIGHT,
    ) {
        require(feedLines in 0..255) { "Feed line count must fit into one byte" }
        sendBitmap(
            bitmap = bitmap,
            rasterBandHeight = rasterBandHeight,
            trailingCommand = byteArrayOf(ESC, FEED_LINES, feedLines.toByte()),
        )
    }

    /** Sends an image without advancing empty paper afterwards. */
    @Throws(IOException::class)
    fun printRaster(
        bitmap: Bitmap,
        rasterBandHeight: Int = EscPosRasterEncoder.DEFAULT_BAND_HEIGHT,
    ) {
        sendBitmap(bitmap, rasterBandHeight, trailingCommand = null)
    }

    @Throws(IOException::class)
    private fun sendBitmap(
        bitmap: Bitmap,
        rasterBandHeight: Int,
        trailingCommand: ByteArray?,
    ) {
        val stream = output ?: throw IOException("Printer is not connected")
        val commands = EscPosRasterEncoder.encode(bitmap, rasterBandHeight)
        val startedAt = SystemClock.elapsedRealtime()

        // Keep the raster stream continuous: pauses inside it caused visible
        // banding on the IMP006. Wait only after the whole image was handed off.
        commands.forEach(stream::write)
        trailingCommand?.let(stream::write)
        stream.flush()

        val elapsedSendMillis = SystemClock.elapsedRealtime() - startedAt
        waitForPrinter(PrinterDrainTiming.delayMillis(bitmap.height, elapsedSendMillis))
    }

    @Throws(IOException::class)
    private fun waitForPrinter(delayMillis: Long) {
        try {
            Thread.sleep(delayMillis)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Printing was interrupted", error)
        }
    }

    override fun close() {
        try {
            output?.close()
        } catch (_: IOException) {
        } finally {
            output = null
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val ESC_INITIALIZE = byteArrayOf(0x1B, 0x40)
        const val DEFAULT_FEED_LINES = 4
        const val ESC: Byte = 0x1B
        const val FEED_LINES: Byte = 0x64
    }
}
