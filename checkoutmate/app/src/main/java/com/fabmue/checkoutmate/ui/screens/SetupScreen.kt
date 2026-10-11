package com.fabmue.checkoutmate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.MatchSerializer
import com.fabmue.checkoutmate.domain.X01Config
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    container: AppContainer,
    onStartMatch: () -> Unit,
    onResumeMatch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by container.prefs.settings.collectAsState(initial = com.fabmue.checkoutmate.data.Settings())
    val savedMatchJson by container.prefs.savedMatch.collectAsState(initial = null)
    val lastPlayers by container.prefs.lastPlayers.collectAsState(initial = emptyList())

    val players: SnapshotStateList<String> = remember(lastPlayers) {
        (if (lastPlayers.isNotEmpty()) lastPlayers else listOf("Player 1", "Player 2")).toMutableStateList()
    }
    var start by remember { mutableIntStateOf(501) }
    var legs by remember { mutableIntStateOf(3) }
    var useSets by remember { mutableStateOf(false) }
    var sets by remember { mutableIntStateOf(3) }
    var doubleIn by remember(settings.defaultDoubleIn) { mutableStateOf(settings.defaultDoubleIn) }
    var doubleOut by remember(settings.defaultDoubleOut) { mutableStateOf(settings.defaultDoubleOut) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        savedMatchJson?.let { json ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.match_resume), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            MatchSerializer.fromJson(json)?.let {
                                container.pendingResume = it
                                onResumeMatch()
                            }
                        }) { Text(stringResource(R.string.match_resume)) }
                        OutlinedButton(onClick = { scope.launch { container.prefs.setSavedMatch(null) } }) {
                            Text(stringResource(R.string.match_discard))
                        }
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.setup_players)) {
            players.forEachIndexed { index, name ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { players[index] = it },
                        label = { Text(stringResource(R.string.setup_player_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (players.size > 1) {
                        IconButton(onClick = { players.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.setup_remove))
                        }
                    }
                }
            }
            if (players.size < 4) {
                OutlinedButton(onClick = { players.add("Player ${players.size + 1}") }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  " + stringResource(R.string.setup_add_player))
                }
            }
        }

        SectionCard(stringResource(R.string.setup_game_type)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(301, 501, 701).forEach { value ->
                    ChoiceButton(value.toString(), start == value, Modifier.weight(1f)) { start = value }
                }
            }
        }

        SectionCard(stringResource(R.string.setup_format)) {
            Stepper(stringResource(R.string.setup_first_to) + " " + legs + " " + stringResource(R.string.setup_legs), { if (legs > 1) legs-- }, { if (legs < 15) legs++ })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_use_sets), Modifier.weight(1f))
                Switch(checked = useSets, onCheckedChange = { useSets = it })
            }
            if (useSets) {
                Stepper(stringResource(R.string.setup_first_to) + " " + sets + " " + stringResource(R.string.setup_sets), { if (sets > 1) sets-- }, { if (sets < 15) sets++ })
            }
        }

        SectionCard(stringResource(R.string.setup_game_type) + " · rules") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_double_in), Modifier.weight(1f))
                Switch(checked = doubleIn, onCheckedChange = { doubleIn = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_double_out), Modifier.weight(1f))
                Switch(checked = doubleOut, onCheckedChange = { doubleOut = it })
            }
        }

        val names = players.map { it.trim() }.filter { it.isNotBlank() }
        Button(
            onClick = {
                container.pendingNewConfig = X01Config(
                    players = names,
                    start = start,
                    legsToWin = legs,
                    setsToWin = if (useSets) sets else 1,
                    doubleIn = doubleIn,
                    doubleOut = doubleOut,
                )
                scope.launch { container.prefs.setLastPlayers(names) }
                onStartMatch()
            },
            enabled = names.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.setup_start_match), style = MaterialTheme.typography.titleMedium)
        }
        if (names.isEmpty()) {
            Text(stringResource(R.string.setup_need_two_players), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else FilledTonalButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "-") }
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "+") }
    }
}
