package com.fabmue.checkoutmate.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.domain.MatchEvent
import com.fabmue.checkoutmate.domain.X01Player
import com.fabmue.checkoutmate.ui.CheckoutInfo
import com.fabmue.checkoutmate.ui.MatchViewModel
import com.fabmue.checkoutmate.ui.components.DartKeypad
import com.fabmue.checkoutmate.ui.components.Dartboard
import com.fabmue.checkoutmate.ui.containerViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MatchScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onOpenStore: () -> Unit,
) {
    val vm = containerViewModel { MatchViewModel(it) }
    val match by vm.match.collectAsState()
    val pending by vm.pending.collectAsState()
    val checkout by vm.checkoutInfo.collectAsState()
    val settings by vm.settings.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val entitlements by vm.entitlements.collectAsState()

    var useBoard by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val voiceEnabled = settings.voice && entitlements.contains(Feature.VOICE)
    val speak = rememberSpeaker(voiceEnabled)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            when (ev) {
                is MatchEvent.VisitScored -> {
                    if (ev.is180) {
                        celebrate = true
                        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (ev.scored > 0) speak(context.getString(R.string.voice_scored, ev.scored))
                }
                is MatchEvent.Bust -> speak(context.getString(R.string.voice_bust))
                is MatchEvent.LegWon ->
                    speak(context.getString(R.string.voice_leg_shot, match.config.players.getOrNull(ev.player) ?: ""))
                is MatchEvent.SetWon ->
                    speak(context.getString(R.string.voice_leg_shot, match.config.players.getOrNull(ev.player) ?: ""))
                is MatchEvent.MatchWon ->
                    speak(context.getString(R.string.voice_game_shot, match.config.players.getOrNull(ev.player) ?: ""))
            }
        }
    }

    if (celebrate) {
        LaunchedEffect(Unit) { delay(1600); celebrate = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                // Header
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.match_exit))
                    }
                    val header = if (match.config.setsToWin > 1) {
                        stringResource(R.string.match_leg_set, match.legNumber, match.setNumber)
                    } else {
                        stringResource(R.string.match_leg_only, match.legNumber)
                    }
                    Text(header, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { useBoard = !useBoard }) {
                        Icon(if (useBoard) Icons.Filled.GridOn else Icons.Filled.Apps, contentDescription = null)
                    }
                    IconButton(onClick = { vm.undo() }, enabled = canUndo || pending.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.match_undo))
                    }
                }

                // Scoreboard
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    match.players.forEachIndexed { index, player ->
                        PlayerCard(
                            player = player,
                            isCurrent = index == match.current && !match.finished,
                            showSets = match.config.setsToWin > 1,
                        )
                    }
                    CheckoutLine(checkout, onUnlock = onOpenStore)
                    VisitRow(pending.joinToString("  ") { it.label }, pending.sumOf { it.value })
                }

                // Quick scores
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(26, 41, 45, 60, 85, 100, 140, 180).forEach { total ->
                        AssistChip(onClick = { vm.commitTotal(total) }, label = { Text("$total") })
                    }
                }

                // Input
                if (useBoard) {
                    Dartboard(onDart = { vm.addDart(it) }, modifier = Modifier.padding(bottom = 8.dp))
                } else {
                    DartKeypad(onDart = { vm.addDart(it) }, modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            if (celebrate) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("180!", fontSize = 96.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }

    if (match.finished) {
        val winnerName = match.winner?.let { match.config.players.getOrNull(it) } ?: ""
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.match_match_won, winnerName)) },
            text = { Text("") },
            confirmButton = { TextButton(onClick = { vm.rematch() }) { Text(stringResource(R.string.match_rematch)) } },
            dismissButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.match_exit)) } },
        )
    }
}

@Composable
private fun PlayerCard(player: X01Player, isCurrent: Boolean, showSets: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                val legsSets = if (showSets) "${player.sets} · ${player.legs}" else "${player.legs}"
                Text(
                    stringResource(R.string.match_avg) + " " + String.format(Locale.US, "%.1f", player.threeDartAverage) +
                        "   •   " + (if (showSets) "S·L " else "L ") + legsSets,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (player.doubleInNeeded) {
                    Text(stringResource(R.string.match_requires_double), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                player.remaining.toString(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CheckoutLine(info: CheckoutInfo?, onUnlock: () -> Unit) {
    if (info == null) return
    when {
        info.bogey -> AssistChip(onClick = { }, label = { Text("${info.score}: " + stringResource(R.string.checkout_no_finish)) })
        info.locked -> AssistChip(
            onClick = onUnlock,
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            label = { Text(stringResource(R.string.checkout_label)) },
        )
        info.path != null -> AssistChip(
            onClick = { },
            label = {
                Text(
                    "${info.score}: " + info.path.joinToString(" "),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            },
        )
    }
}

@Composable
private fun VisitRow(dartsText: String, sum: Int) {
    if (dartsText.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.match_this_visit) + ": ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(dartsText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("= $sum", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun rememberSpeaker(enabled: Boolean): (String) -> Unit {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(enabled) {
        var tts: TextToSpeech? = null
        if (enabled) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                }
            }
            holder[0] = tts
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
            holder[0] = null
        }
    }
    return { text ->
        if (enabled) holder[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cm")
    }
}
