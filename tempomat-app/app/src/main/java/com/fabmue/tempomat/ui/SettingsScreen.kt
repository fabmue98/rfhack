package com.fabmue.tempomat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabmue.tempomat.data.TransportType
import com.fabmue.tempomat.protocol.DeviceConfig

@Composable
fun SettingsScreen(
    viewModel: CruiseViewModel,
    onBack: () -> Unit,
    onRequestBluetooth: () -> Unit,
    hasBluetoothPermission: () -> Boolean,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val s = settings ?: return

    DisposableEffect(Unit) { onDispose { viewModel.scanner.stop() } }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
                Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)
            }

            Section("Verbindung zum ESP32")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TransportType.entries.forEach { t ->
                    FilterChip(
                        selected = s.transport == t,
                        onClick = { viewModel.setTransport(t) },
                        label = {
                            Text(
                                when (t) {
                                    TransportType.BLE -> "Bluetooth LE"
                                    TransportType.TCP -> "WLAN"
                                    TransportType.DEMO -> "Demo"
                                }
                            )
                        },
                    )
                }
            }

            when (s.transport) {
                TransportType.BLE -> BleSection(
                    viewModel = viewModel,
                    selectedAddress = s.bleAddress,
                    selectedName = s.bleName,
                    onRequestBluetooth = onRequestBluetooth,
                    hasBluetoothPermission = hasBluetoothPermission,
                )
                TransportType.TCP -> TcpSection(s.tcpHost, s.tcpPort, viewModel::setTcp)
                TransportType.DEMO -> Text(
                    "Spielt eine Beispielfahrt ab. Die Anzeige ist dann mit DEMO markiert.",
                    Modifier.padding(top = 12.dp),
                )
            }

            Section("Tempomat-Einstellungen (im ESP32 gespeichert)")
            ConfigSection(
                current = model.config,
                editable = model.canEditSettings(now),
                lastAck = model.lastAck?.let { if (it.ok) "Übernommen" else "Abgelehnt: ${it.text ?: "?"}" },
                onRefresh = viewModel::refreshConfig,
                onSend = viewModel::sendConfig,
            )

            Section("Info")
            Text("Firmware: ${model.firmware ?: "–"} · Protokoll: ${model.protocolVersion ?: "–"}")
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 12.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun BleSection(
    viewModel: CruiseViewModel,
    selectedAddress: String?,
    selectedName: String?,
    onRequestBluetooth: () -> Unit,
    hasBluetoothPermission: () -> Boolean,
) {
    val devices by viewModel.scanner.devices.collectAsStateWithLifecycle()
    val scanning by viewModel.scanner.scanning.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Gewählt: ${selectedName ?: "–"} ${selectedAddress?.let { "($it)" } ?: ""}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                message = when {
                    !hasBluetoothPermission() -> { onRequestBluetooth(); "Bitte Bluetooth-Berechtigung erlauben." }
                    scanning -> { viewModel.scanner.stop(); null }
                    !viewModel.scanner.start() -> "Bluetooth ist ausgeschaltet."
                    else -> null
                }
            }) { Text(if (scanning) "Suche stoppen" else "Nach ESP32 suchen") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LaunchedEffect(scanning) {
            if (scanning) {
                kotlinx.coroutines.delay(15_000)
                viewModel.scanner.stop()
            }
        }
        devices.forEach { d ->
            Text(
                "${d.name ?: "Unbenannt"}  ${d.address}  ${d.rssi} dBm" + if (d.hasUart) "  · UART" else "",
                Modifier.fillMaxWidth().clickable { viewModel.chooseBleDevice(d.address, d.name) }.padding(vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (d.address == selectedAddress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TcpSection(host: String, port: Int, onSave: (String, Int) -> Unit) {
    var h by rememberSaveable(host) { mutableStateOf(host) }
    var p by rememberSaveable(port) { mutableStateOf(port.toString()) }
    val portValue = p.toIntOrNull()?.takeIf { it in 1..65535 }
    Row(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(h, { h = it.trim() }, label = { Text("IP / Host") }, singleLine = true, modifier = Modifier.width(260.dp))
        OutlinedTextField(
            p, { p = it.filter(Char::isDigit).take(5) },
            label = { Text("Port") },
            singleLine = true,
            isError = portValue == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        Button(onClick = { onSave(h, portValue!!) }, enabled = h.isNotBlank() && portValue != null) { Text("Speichern") }
    }
}

@Composable
private fun ConfigSection(
    current: DeviceConfig?,
    editable: Boolean,
    lastAck: String?,
    onRefresh: () -> Unit,
    onSend: (DeviceConfig) -> Boolean,
) {
    var draft by remember(current) { mutableStateOf(current ?: DeviceConfig.DEFAULT) }
    val error = draft.validationError()

    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (current == null) Text("Noch keine Einstellungen vom ESP32 empfangen.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Schrittweite", Modifier.width(160.dp))
            DeviceConfig.ALLOWED_STEPS.forEach { step ->
                FilterChip(selected = draft.stepKmh == step, onClick = { draft = draft.copy(stepKmh = step) }, label = { Text("$step km/h") })
            }
        }
        Stepper("Mindestgeschwindigkeit", draft.minKmh) { draft = draft.copy(minKmh = it) }
        Stepper("Höchstgeschwindigkeit", draft.maxKmh) { draft = draft.copy(maxKmh = it) }
        if (error != null) Text("Ungültig: Mindestwert muss unter dem Höchstwert liegen, Bereich ${DeviceConfig.MIN_LIMIT}–${DeviceConfig.MAX_LIMIT} km/h.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSend(draft) }, enabled = editable && error == null && draft != current) { Text("An ESP32 senden") }
            OutlinedButton(onClick = onRefresh) { Text("Neu laden") }
        }
        if (!editable) Text("Senden geht nur bei verbundenem ESP32 und ausgeschaltetem Tempomat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lastAck?.let { Text(it) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(220.dp))
        OutlinedButton(onClick = { onChange((value - 5).coerceAtLeast(DeviceConfig.MIN_LIMIT)) }) { Text("−5") }
        Text("$value km/h", Modifier.width(110.dp), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onChange((value + 5).coerceAtMost(DeviceConfig.MAX_LIMIT)) }) { Text("+5") }
    }
}
