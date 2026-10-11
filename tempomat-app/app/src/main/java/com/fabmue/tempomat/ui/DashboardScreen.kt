package com.fabmue.tempomat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabmue.tempomat.data.TransportType
import com.fabmue.tempomat.protocol.CruiseState
import com.fabmue.tempomat.protocol.CruiseStatus
import com.fabmue.tempomat.protocol.DisplayModel
import com.fabmue.tempomat.protocol.LinkState
import com.fabmue.tempomat.ui.theme.CruiseColors
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(viewModel: CruiseViewModel, onOpenSettings: () -> Unit) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status = model.visibleStatus(now)
    val demo = settings?.transport == TransportType.DEMO

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            TopBar(model, demo, linkHint(settings?.transport, settings?.bleAddress), onOpenSettings)
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SetSpeedPanel(status, Modifier.weight(1.3f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InfoTile("Aktuell", status?.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "–", Modifier.weight(1f))
                    PedalTile(status, Modifier.weight(1f))
                }
            }
            Text(
                "Nur Anzeige. Bedienung über den Lenkstockhebel; Kupplung und Bremse schalten im ESP32 ab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun linkHint(transport: TransportType?, bleAddress: String?): String? =
    if (transport == TransportType.BLE && bleAddress == null) "Kein ESP32 gewählt" else null

@Composable
private fun TopBar(model: DisplayModel, demo: Boolean, hint: String?, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val (dot, label) = when (model.link) {
            LinkState.CONNECTED -> CruiseColors.Active to "ESP32 verbunden"
            LinkState.CONNECTING -> CruiseColors.Standby to "Verbinde…"
            LinkState.DISCONNECTED -> CruiseColors.Fault to (hint ?: "Keine Verbindung")
        }
        Box(Modifier.size(14.dp).background(dot, CircleShape))
        Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium)
        if (demo) {
            Text(
                "DEMO",
                Modifier.padding(start = 16.dp)
                    .background(CruiseColors.Override, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        }
        if (model.protocolMismatch()) {
            Text(
                "Protokoll v${model.protocolVersion} – App erwartet v${com.fabmue.tempomat.protocol.Protocol.VERSION}",
                Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SetSpeedPanel(status: CruiseStatus?, modifier: Modifier) {
    val state = status?.state ?: CruiseState.UNKNOWN
    val color = stateColor(state)
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stateLabel(state),
                Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
            val set = status?.setSpeedKmh
            Text(
                set?.toString() ?: "– –",
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = if (state == CruiseState.ACTIVE) color else MaterialTheme.colorScheme.onSurface,
            )
            Text("Soll km/h", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val reason = when {
                status == null -> "Keine aktuellen Daten vom ESP32"
                state == CruiseState.FAULT -> "Fehler ${status.faultCode}" + (status.reason?.let { ": $it" } ?: "")
                else -> status.reason?.let { "Abgeschaltet: ${reasonLabel(it)}" }
            }
            if (reason != null) {
                Text(reason, Modifier.padding(top = 8.dp), color = CruiseColors.Standby, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun InfoTile(title: String, value: String, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PedalTile(status: CruiseStatus?, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PedalIndicator("Kupplung", status?.clutchPressed)
            PedalIndicator("Bremse", status?.brakePressed)
        }
    }
}

@Composable
private fun PedalIndicator(label: String, pressed: Boolean?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val color = when (pressed) {
            null -> CruiseColors.Unknown
            true -> CruiseColors.Fault
            false -> CruiseColors.Off
        }
        Box(Modifier.size(28.dp).background(color, CircleShape))
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
    }
}

private fun stateColor(state: CruiseState) = when (state) {
    CruiseState.OFF -> CruiseColors.Off
    CruiseState.STANDBY -> CruiseColors.Standby
    CruiseState.ACTIVE -> CruiseColors.Active
    CruiseState.OVERRIDE -> CruiseColors.Override
    CruiseState.FAULT -> CruiseColors.Fault
    CruiseState.UNKNOWN -> CruiseColors.Unknown
}

private fun stateLabel(state: CruiseState) = when (state) {
    CruiseState.OFF -> "AUS"
    CruiseState.STANDBY -> "BEREIT"
    CruiseState.ACTIVE -> "AKTIV"
    CruiseState.OVERRIDE -> "ÜBERSTEUERT"
    CruiseState.FAULT -> "FEHLER"
    CruiseState.UNKNOWN -> "UNBEKANNT"
}

private fun reasonLabel(code: String) = when (code.uppercase()) {
    "BRAKE" -> "Bremse"
    "CLUTCH" -> "Kupplung"
    "LEVER" -> "Hebel"
    "SPEED" -> "Geschwindigkeit außerhalb Bereich"
    else -> code
}
