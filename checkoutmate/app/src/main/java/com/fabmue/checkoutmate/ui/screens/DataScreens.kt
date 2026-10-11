package com.fabmue.checkoutmate.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.data.PlayerStats
import com.fabmue.checkoutmate.data.local.MatchEntity
import com.fabmue.checkoutmate.data.local.MatchPlayerEntity
import com.fabmue.checkoutmate.ui.components.LockedFeatureCard
import com.fabmue.checkoutmate.ui.components.MiniLineChart
import com.fabmue.checkoutmate.ui.components.StatTile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(container: AppContainer, onOpenStore: () -> Unit) {
    val entitlements by container.entitlements.entitlements.collectAsState()
    val unlocked = entitlements.contains(Feature.STATS)
    val stats by container.history.stats.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!unlocked) {
            LockedFeatureCard(
                message = stringResource(R.string.stats_locked_msg),
                ctaText = stringResource(R.string.common_unlock),
                onUnlock = onOpenStore,
            )
            return@Column
        }
        if (stats.isEmpty()) {
            Text(stringResource(R.string.stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Button(
            onClick = {
                scope.launch {
                    val csv = container.history.buildCsv()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "CheckoutMate stats")
                        putExtra(Intent.EXTRA_TEXT, csv)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.stats_export_csv)) }

        stats.forEach { PlayerStatsCard(it) }
    }
}

@Composable
private fun PlayerStatsCard(s: PlayerStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val tiles = listOf(
                stringResource(R.string.stats_avg) to String.format(Locale.US, "%.1f", s.average),
                stringResource(R.string.stats_checkout_pct) to String.format(Locale.US, "%.0f%%", s.checkoutPercent),
                stringResource(R.string.stats_first9) to String.format(Locale.US, "%.1f", s.first9Average),
                stringResource(R.string.stats_count180) to s.count180.toString(),
                stringResource(R.string.stats_highest_finish) to s.highestFinish.toString(),
                stringResource(R.string.stats_best_leg) to (s.bestLegDarts?.toString() ?: "–"),
                stringResource(R.string.stats_matches) to s.matches.toString(),
                stringResource(R.string.stats_legs_won) to s.legsWon.toString(),
            )
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, value) ->
                        StatTile(label, value, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (s.averageSeries.size >= 2) {
                Text(stringResource(R.string.stats_avg_over_time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiniLineChart(s.averageSeries)
            }
        }
    }
}

@Composable
fun HistoryScreen(container: AppContainer, onOpenStore: () -> Unit) {
    val entitlements by container.entitlements.entitlements.collectAsState()
    val unlocked = entitlements.contains(Feature.STATS)
    val matches by container.history.matches.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<MatchEntity?>(null) }
    var detail by remember { mutableStateOf<MatchEntity?>(null) }
    var detailPlayers by remember { mutableStateOf<List<MatchPlayerEntity>>(emptyList()) }

    if (!unlocked) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            LockedFeatureCard(
                message = stringResource(R.string.history_locked_msg),
                ctaText = stringResource(R.string.common_unlock),
                onUnlock = onOpenStore,
            )
        }
        return
    }

    if (matches.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        items(matches, key = { it.id }) { m ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Text(m.playersCsv, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${m.gameStart} • ${stringResource(R.string.history_vs)} ${m.winnerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(dateFmt.format(Date(m.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            detailPlayers = container.history.playersForMatch(m.id)
                            detail = m
                        }
                    }) { Text(stringResource(R.string.stats_matches)) }
                    IconButton(onClick = { toDelete = m }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.history_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.history.deleteMatch(m.id) }
                    toDelete = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    detail?.let { m ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(m.playersCsv) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detailPlayers.forEach { p ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(p.name + (if (p.won) " 🏆" else ""), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "%.1f", p.average))
                            Spacer(Modifier.width(12.dp))
                            Text("${p.count180}×180")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text(stringResource(R.string.common_close)) } },
        )
    }
}
