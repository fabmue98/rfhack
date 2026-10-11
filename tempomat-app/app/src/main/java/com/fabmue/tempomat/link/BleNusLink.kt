package com.fabmue.tempomat.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.fabmue.tempomat.protocol.LineAssembler
import com.fabmue.tempomat.protocol.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID

/**
 * Bluetooth LE transport using the Nordic UART Service (NUS), which ESP32
 * Arduino/ESP-IDF examples provide out of the box. The ESP32 notifies on TX
 * and the app writes to RX.
 *
 * Callers must hold BLUETOOTH_CONNECT (Android 12+) before [start].
 */
@SuppressLint("MissingPermission")
class BleNusLink(
    context: Context,
    private val address: String,
) : CruiseLink {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val lines = _lines.asSharedFlow()

    private val assembler = LineAssembler()
    private val lock = Any()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var payloadSize = 20

    private var scope: CoroutineScope? = null
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var reconnectJob: Job? = null
    @Volatile private var running = false

    override fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        this.scope = scope
        connect()
    }

    override fun stop() {
        running = false
        reconnectJob?.cancel()
        closeGatt()
        _state.value = LinkState.DISCONNECTED
    }

    override fun send(line: String): Boolean {
        if (_state.value != LinkState.CONNECTED) return false
        val bytes = (line + "\n").toByteArray(Charsets.US_ASCII)
        synchronized(lock) {
            bytes.asList().chunked(payloadSize).forEach { writeQueue.add(it.toByteArray()) }
        }
        pumpWrites()
        return true
    }

    private fun connect() {
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled || !BleScanner.isValidAddress(address)) {
            scheduleReconnect()
            return
        }
        _state.value = LinkState.CONNECTING
        assembler.reset()
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleReconnect() {
        _state.value = LinkState.DISCONNECTED
        if (!running) return
        reconnectJob?.cancel()
        reconnectJob = scope?.launch {
            delay(CruiseLink.RECONNECT_DELAY_MS)
            if (running) connect()
        }
    }

    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        rx = null
        synchronized(lock) {
            writeQueue.clear()
            writeInFlight = false
        }
    }

    private fun pumpWrites() {
        val g = gatt ?: return
        val c = rx ?: return
        val chunk: ByteArray
        synchronized(lock) {
            if (writeInFlight) return
            chunk = writeQueue.poll() ?: return
            writeInFlight = true
        }
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                c.value = chunk
                g.writeCharacteristic(c)
            }
        }
        if (!ok) synchronized(lock) { writeInFlight = false }
    }

    private fun onData(bytes: ByteArray) {
        val lines = synchronized(lock) { assembler.feed(String(bytes, Charsets.US_ASCII)) }
        for (line in lines) _lines.tryEmit(line)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.requestMtu(REQUESTED_MTU)
            } else {
                closeGatt()
                scheduleReconnect()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) payloadSize = (mtu - 3).coerceIn(20, 512)
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(NUS_SERVICE)
            val tx = service?.getCharacteristic(NUS_TX)
            rx = service?.getCharacteristic(NUS_RX)
            if (status != BluetoothGatt.GATT_SUCCESS || tx == null || rx == null) {
                closeGatt()
                scheduleReconnect()
                return
            }
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD) ?: run {
                _state.value = LinkState.CONNECTED
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid == CCCD) _state.value = LinkState.CONNECTED
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(lock) { writeInFlight = false }
            pumpWrites()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (c.uuid == NUS_TX) onData(value)
        }

        @Deprecated("Called on Android 12 and older")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && c.uuid == NUS_TX) {
                c.value?.let(::onData)
            }
        }
    }

    companion object {
        val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val REQUESTED_MTU = 185
    }
}
