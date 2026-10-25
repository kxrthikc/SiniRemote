package com.example.sinilinkcontrol

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class AmpState(
    val isConnected: Boolean = false,
    val statusText: String = "Disconnected",
    val volume: Int = 15,
    val mode: String = "Bluetooth",
    val eq: String = "Normal",
    val isPlaying: Boolean = false,
    val logs: List<String> = emptyList(),
    val connectionKilled: Boolean = false,
    val pcAudioBug: Boolean = false,
    val scanWarnings: Boolean = false
)

@SuppressLint("MissingPermission", "ObsoleteSdkInt")
class SinilinkBleManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val _state = MutableStateFlow(AmpState())
    val state: StateFlow<AmpState> = _state
    
    private var scanAttempts = 0
    private var isSyncingAndPlaying = false
    var pendingAutoEq: String = "Disable"

    companion object {
        @Volatile
        private var INSTANCE: SinilinkBleManager? = null

        fun getInstance(context: Context): SinilinkBleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SinilinkBleManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        val WRITE_UUID: UUID = UUID.fromString("0000ae10-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ae04-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEVICE_NAME_PART = "sinilink"
    }

    private fun addLog(msg: String) {
        val currentLogs = _state.value.logs.toMutableList()
        if (currentLogs.size > 100) currentLogs.removeAt(0)
        currentLogs.add(msg)
        _state.value = _state.value.copy(logs = currentLogs)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val advName = result.scanRecord?.deviceName ?: ""
            val name = (device.name ?: advName).lowercase()

            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val hasSinilinkUuid = uuids.contains(NOTIFY_UUID) || uuids.contains(WRITE_UUID) || uuids.any { it.toString().startsWith("0000ae") }

            if (name.contains(DEVICE_NAME_PART) || hasSinilinkUuid) {
                try {
                    scanner?.stopScan(this)
                } catch (_: Exception) {}
                addLog("Found Sinilink device: ${device.name ?: advName} (${device.address})")
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("BLE Scan failed with error code: $errorCode")
            _state.value = _state.value.copy(statusText = "Scan Failed")
        }
    }

    fun startScan() {
        if (_state.value.isConnected) {
            addLog("Already connected to Sinilink. Skipping scan.")
            return
        }

        val currentAdapter = adapter
        if (currentAdapter == null || !currentAdapter.isEnabled) {
            _state.value = _state.value.copy(statusText = "Bluetooth Disabled")
            addLog("Error: Bluetooth is disabled.")
            return
        }

        scanAttempts++
        if (scanAttempts >= 3) {
            _state.value = _state.value.copy(scanWarnings = true)
        }

        _state.value = _state.value.copy(connectionKilled = false)

        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        // Check for bonded devices first to prevent "no device found" if OS already paired it securely
        val bonded = currentAdapter.bondedDevices?.firstOrNull {
            (it.name ?: "").lowercase().contains(DEVICE_NAME_PART)
        }
        if (bonded != null && scanAttempts > 1) {
            addLog("Trying bonded device fallback: ${bonded.address}")
            connectToDevice(bonded)
            return
        }

        _state.value = _state.value.copy(statusText = "Scanning...", isConnected = false)
        addLog("Starting BLE scan for Sinilink...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            addLog("Scan start exception: ${e.message}")
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: Exception) {}

            if (!_state.value.isConnected) {
                _state.value = _state.value.copy(statusText = "Not Found")
                addLog("Scan timeout: Sinilink device not found.")
            }
        }, 10000)
    }

    fun clearWarnings() {
        _state.value = _state.value.copy(connectionKilled = false, scanWarnings = false, pcAudioBug = false)
    }

    fun disconnect() {
        addLog("Explicit disconnect requested by user.")
        _state.value = _state.value.copy(connectionKilled = true)
        val g = gatt
        if (g != null) {
            try {
                g.disconnect()
            } catch (e: Exception) {
                closeGattConnection()
            }
        } else {
            _state.value = _state.value.copy(isConnected = false, statusText = "Disconnected")
        }
    }

    fun syncAndPlay() {
        isSyncingAndPlaying = true
        val cmd = byteArrayOf(0x7E, 0x05, 0x01, 0x00)
        sendCommand(cmd)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun close() {
        addLog("Closing SinilinkBleManager...")
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        closeGattConnection()
    }

    private fun closeGattConnection() {
        try {
            gatt?.disconnect()
        } catch (_: Exception) {}
        try {
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        writeCharacteristic = null
        _state.value = _state.value.copy(isConnected = false, statusText = "Disconnected")
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (_state.value.isConnected && gatt != null) {
            addLog("Already connected to ${device.address}.")
            return
        }

        scanAttempts = 0
        _state.value = _state.value.copy(statusText = "Connecting...", scanWarnings = false)
        addLog("Connecting GATT to ${device.address}...")
        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                addLog("GATT status error: $status (newState=$newState)")
                closeGattConnection()
                _state.value = _state.value.copy(isConnected = false, statusText = "Connection Failed")
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = _state.value.copy(isConnected = true, statusText = "Connected")
                addLog("GATT Connected. Discovering services...")
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        addLog("Error discovering services: ${e.message}")
                    }
                }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                addLog("GATT Disconnected.")
                closeGattConnection()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Services discovered successfully (${gatt.services.size} services found).")
                var notifyChar: BluetoothGattCharacteristic? = null
                var writeChar: BluetoothGattCharacteristic? = null

                // Search ALL services for Notify and Write characteristics independently
                for (service in gatt.services) {
                    if (notifyChar == null) {
                        notifyChar = service.getCharacteristic(NOTIFY_UUID)
                    }
                    if (writeChar == null) {
                        writeChar = service.getCharacteristic(WRITE_UUID)
                    }
                }

                if (writeChar != null) {
                    writeCharacteristic = writeChar
                    addLog("Mapped Write Characteristic ($WRITE_UUID).")

                    // Auto Apply EQ if configured
                    if (pendingAutoEq != "Disable") {
                        val eqCmds = mapOf(
                            "Normal" to byteArrayOf(0x7E, 0x05, 0x09, 0x00),
                            "Rock" to byteArrayOf(0x7E, 0x05, 0x0A, 0x00),
                            "Pop" to byteArrayOf(0x7E, 0x05, 0x0B, 0x00),
                            "Classic" to byteArrayOf(0x7E, 0x05, 0x0C, 0x00),
                            "Jazz" to byteArrayOf(0x7E, 0x05, 0x0D, 0x00),
                            "Country" to byteArrayOf(0x7E, 0x05, 0x0E, 0x00)
                        )
                        val cmd = eqCmds[pendingAutoEq]
                        if (cmd != null) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                sendCommand(cmd)
                                addLog("Auto applied EQ: $pendingAutoEq")
                            }, 500)
                        }
                    }
                } else {
                    addLog("ERROR: Could not find Write Characteristic ($WRITE_UUID) in any service!")
                }

                if (notifyChar != null) {
                    addLog("Mapped Notify Characteristic ($NOTIFY_UUID).")
                    gatt.setCharacteristicNotification(notifyChar, true)
                    val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                        addLog("Notifications enabled.")
                    } else {
                        addLog("CCCD Descriptor not found for notifications.")
                    }
                } else {
                    addLog("ERROR: Could not find Notify Characteristic ($NOTIFY_UUID) in any service!")
                }
            } else {
                addLog("Service discovery failed with status: $status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_UUID) {
                parseNotification(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == NOTIFY_UUID) {
                parseNotification(value)
            }
        }
    }

    private fun parseNotification(data: ByteArray) {
        val packetHex = data.joinToString(" ") { String.format("%02X", it) }

        if (data.size >= 15 && data[0] == 0x7E.toByte() && data[2] == 0xC3.toByte() && data[3] == 0xE3.toByte()) {
            val modeByte = data[4].toInt() and 0xFF
            val playByte = data[5].toInt() and 0xFF
            val volVal = data[6].toInt() and 0xFF
            val eqByte = data[8].toInt() and 0xFF

            val modeName = when (modeByte) {
                0x14 -> "Bluetooth"
                0x16 -> "AUX"
                0x04 -> "USB"
                0x03 -> "TF Card"
                0x15, 0x17 -> "PC Audio"
                else -> "Bluetooth"
            }
            val eqName = when (eqByte) {
                0x09 -> "Normal"
                0x0A -> "Rock"
                0x0B -> "Pop"
                0x0C -> "Classic"
                0x0D -> "Jazz"
                0x0E -> "Country"
                else -> "Normal"
            }
            val isPlaying = playByte == 0x02

            if (isSyncingAndPlaying) {
                isSyncingAndPlaying = false
                if (!isPlaying) {
                    // Send play again immediately
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendCommand(byteArrayOf(0x7E, 0x05, 0x01, 0x00))
                    }, 50)
                }
                // Sync volume back
                Handler(Looper.getMainLooper()).postDelayed({
                    val syncVolCmd = byteArrayOf(0x7E, 0x0F, 0x1D, _state.value.volume.toByte()) + ByteArray(10)
                    sendCommand(syncVolCmd)
                }, 200)
            }

            var pcAudioBugDetected = _state.value.pcAudioBug
            if (_state.value.mode == "PC Audio" && modeName == "Bluetooth") {
                pcAudioBugDetected = true
            } else if (modeName != "Bluetooth") {
                pcAudioBugDetected = false
            }

            addLog("RX State: $packetHex [Mode=$modeName, Vol=$volVal, EQ=$eqName]")
            _state.value = _state.value.copy(
                volume = volVal,
                mode = modeName,
                eq = eqName,
                isPlaying = isPlaying,
                pcAudioBug = pcAudioBugDetected
            )
        } else if (data.size >= 16 && data[0] == 0x7E.toByte() && data[1] == 0x10.toByte() && data[2] == 0x1F.toByte()) {
            val volVal = data[5].toInt() and 0xFF
            addLog("RX Knob: $packetHex -> Vol $volVal")
            _state.value = _state.value.copy(volume = volVal)
        } else {
            addLog("RX: $packetHex")
        }
    }

    @Suppress("DEPRECATION")
    fun sendCommand(command: ByteArray) {
        val char = writeCharacteristic
        val g = gatt
        if (char == null || g == null) {
            addLog("TX ERROR: Cannot send - Not connected or write characteristic is null!")
            return
        }

        val packet = addChecksum(command)
        val hexStr = packet.joinToString(" ") { String.format("%02X", it) }

        val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val res = g.writeCharacteristic(char, packet, writeType)
            addLog("TX ($hexStr) -> Code $res")
        } else {
            char.value = packet
            char.writeType = writeType
            val ok = g.writeCharacteristic(char)
            addLog("TX ($hexStr) -> OK $ok")
        }
    }

    private fun addChecksum(command: ByteArray): ByteArray {
        var sum = 0
        for (b in command) {
            sum += (b.toInt() and 0xFF)
        }
        val checksum = (sum and 0xFF).toByte()
        return command + checksum
    }
}