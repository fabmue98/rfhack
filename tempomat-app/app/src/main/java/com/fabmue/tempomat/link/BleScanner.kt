package com.fabmue.tempomat.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FoundDevice(val address: String, val name: String?, val rssi: Int, val hasUart: Boolean)

/**
 * Lists nearby BLE devices so the user can pick the ESP32 once. Devices that
 * advertise the Nordic UART Service are sorted first.
 */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {
    private val adapter: BluetoothAdapter? =
        context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val isBluetoothOn: Boolean get() = adapter?.isEnabled == true

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val uart = result.scanRecord?.serviceUuids?.contains(ParcelUuid(BleNusLink.NUS_SERVICE)) == true
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name == null && !uart) return
            val found = FoundDevice(result.device.address, name, result.rssi, uart)
            _devices.value = (_devices.value.filterNot { it.address == found.address } + found)
                .sortedWith(compareByDescending<FoundDevice> { it.hasUart }.thenByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    fun start(): Boolean {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        _devices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        _scanning.value = true
        return true
    }

    fun stop() {
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _scanning.value = false
    }

    companion object {
        fun isValidAddress(address: String?): Boolean =
            address != null && BluetoothAdapter.checkBluetoothAddress(address)
    }
}
