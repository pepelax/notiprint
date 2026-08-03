package ru.notiprint.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
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

        adapter.cancelDiscovery()
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
    fun print(bitmap: Bitmap) {
        val stream = output ?: throw IOException("Printer is not connected")
        EscPosRasterEncoder.encode(bitmap).forEach(stream::write)
        stream.write(ESC_FEED_4_LINES)
        // One flush per whole receipt prevents the gaps produced by line-by-line printing.
        stream.flush()
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
        val ESC_FEED_4_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    }
}
