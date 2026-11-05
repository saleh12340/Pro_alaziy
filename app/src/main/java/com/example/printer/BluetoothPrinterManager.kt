package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    object Connecting : PrinterConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
}

class BluetoothPrinterManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = PrinterConnectionState.Connecting
        try {
            disconnect()
            bluetoothAdapter?.cancelDiscovery()

            var connectedSocket: BluetoothSocket? = null
            var lastError: Exception? = null

            // Fallback strategy 1: Standard SPP RFCOMM
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                connectedSocket = s
            } catch (e: Exception) {
                lastError = e
            }

            // Fallback strategy 2: Insecure SPP RFCOMM
            if (connectedSocket == null || !connectedSocket.isConnected) {
                try {
                    val s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    s.connect()
                    connectedSocket = s
                } catch (e: Exception) {
                    lastError = e
                }
            }

            // Fallback strategy 3: Reflection on RFCOMM Channel 1 (Standard POS thermal printer channel)
            if (connectedSocket == null || !connectedSocket.isConnected) {
                try {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val s = m.invoke(device, 1) as BluetoothSocket
                    s.connect()
                    connectedSocket = s
                } catch (e: Exception) {
                    lastError = e
                }
            }

            // Fallback strategy 4: Reflection insecure on RFCOMM Channel 1
            if (connectedSocket == null || !connectedSocket.isConnected) {
                try {
                    val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    val s = m.invoke(device, 1) as BluetoothSocket
                    s.connect()
                    connectedSocket = s
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (connectedSocket != null && connectedSocket.isConnected) {
                socket = connectedSocket
                outputStream = connectedSocket.outputStream
                _connectionState.value = PrinterConnectionState.Connected(
                    deviceName = device.name ?: "طابعة حرارية",
                    deviceAddress = device.address
                )
                true
            } else {
                disconnect()
                _connectionState.value = PrinterConnectionState.Error("تعذر الاتصال بالطابعة: ${lastError?.localizedMessage ?: "تأكد من تشغيل الطابعة وتأكيد الاقتران"}")
                false
            }
        } catch (e: Exception) {
            disconnect()
            _connectionState.value = PrinterConnectionState.Error("تعذر الاتصال بالطابعة: ${e.localizedMessage ?: "تأكد من تشغيل الطابعة وتأكيد الاقتران"}")
            false
        }
    }

    suspend fun printReceiptBitmap(
        originalBitmap: Bitmap,
        printerWidthDots: Int = 384 // 384px for 58mm, 576px for 80mm
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (socket?.isConnected != true || stream == null) {
            return@withContext Result.failure(Exception("الطابعة غير متصلة. يرجى الاتصال بالطابعة أولاً."))
        }

        try {
            // 1. Process bitmap into 1-bit thermal image
            val thermalBitmap = ThermalImageConverter.prepareForThermalPrinter(
                original = originalBitmap,
                targetWidthDots = printerWidthDots
            )

            // 2. Decode bitmap to ESC/POS raster commands
            val printBytes = ThermalImageConverter.decodeBitmapToEscPosBytes(thermalBitmap)

            // 3. Send command stream
            stream.write(printBytes)
            stream.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("حدث خطأ أثناء الطباعة: ${e.localizedMessage}"))
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
        _connectionState.value = PrinterConnectionState.Disconnected
    }
}
