package com.fabmue.tempomat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabmue.tempomat.data.LinkSettings
import com.fabmue.tempomat.data.LinkSettingsStore
import com.fabmue.tempomat.data.TransportType
import com.fabmue.tempomat.link.BleNusLink
import com.fabmue.tempomat.link.BleScanner
import com.fabmue.tempomat.link.CruiseLink
import com.fabmue.tempomat.link.DemoLink
import com.fabmue.tempomat.link.TcpLink
import com.fabmue.tempomat.protocol.DeviceConfig
import com.fabmue.tempomat.protocol.DisplayModel
import com.fabmue.tempomat.protocol.LinkState
import com.fabmue.tempomat.protocol.Protocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CruiseViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LinkSettingsStore(app)
    val scanner = BleScanner(app)

    val settings: StateFlow<LinkSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _model = MutableStateFlow(DisplayModel())
    val model: StateFlow<DisplayModel> = _model.asStateFlow()

    /** Wall clock that ticks so staleness is re-evaluated without new data. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _bluetoothPermitted = MutableStateFlow(false)

    private var link: CruiseLink? = null
    private var linkJobs: List<Job> = emptyList()

    init {
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(250)
            }
        }
        viewModelScope.launch {
            combine(store.settings, _bluetoothPermitted) { s, p -> s to p }
                .distinctUntilChanged()
                .collect { (s, permitted) -> switchLink(s, permitted) }
        }
    }

    fun onBluetoothPermission(granted: Boolean) {
        _bluetoothPermitted.value = granted
    }

    private fun switchLink(s: LinkSettings, bluetoothPermitted: Boolean) {
        link?.stop()
        linkJobs.forEach { it.cancel() }
        _model.value = DisplayModel()

        val next: CruiseLink? = when (s.transport) {
            TransportType.DEMO -> DemoLink()
            TransportType.TCP -> TcpLink(s.tcpHost, s.tcpPort)
            TransportType.BLE ->
                if (bluetoothPermitted && BleScanner.isValidAddress(s.bleAddress)) BleNusLink(getApplication(), s.bleAddress!!)
                else null
        }
        link = next ?: return

        linkJobs = listOf(
            viewModelScope.launch {
                next.state.collect { st ->
                    _model.update { it.withLink(st) }
                    if (st == LinkState.CONNECTED) next.send(Protocol.requestConfig())
                }
            },
            viewModelScope.launch {
                next.lines.collect { line ->
                    val msg = Protocol.parse(line) ?: return@collect
                    _model.update { it.reduce(msg, System.currentTimeMillis()) }
                }
            },
        )
        next.start(viewModelScope)
    }

    fun setTransport(type: TransportType) = viewModelScope.launch { store.setTransport(type) }

    fun chooseBleDevice(address: String, name: String?) = viewModelScope.launch {
        scanner.stop()
        store.setBleDevice(address, name)
        store.setTransport(TransportType.BLE)
    }

    fun setTcp(host: String, port: Int) = viewModelScope.launch { store.setTcp(host, port) }

    /** Sends new comfort settings; the ESP32 validates and answers with ACK + CFG. */
    fun sendConfig(cfg: DeviceConfig): Boolean {
        if (cfg.validationError() != null) return false
        if (!_model.value.canEditSettings(System.currentTimeMillis())) return false
        return link?.send(Protocol.setConfig(cfg)) == true
    }

    fun refreshConfig() {
        link?.send(Protocol.requestConfig())
    }

    override fun onCleared() {
        scanner.stop()
        link?.stop()
    }
}
