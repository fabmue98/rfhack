package com.fabmue.checkoutmate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.domain.training.TrainingType
import com.fabmue.checkoutmate.ui.TrainingViewModel
import com.fabmue.checkoutmate.ui.components.DartKeypad
import com.fabmue.checkoutmate.ui.components.Dartboard
import com.fabmue.checkoutmate.ui.components.LockedFeatureCard
import com.fabmue.checkoutmate.ui.containerViewModel

private data class GameMeta(val type: TrainingType, val titleRes: Int, val descRes: Int)

private val GAMES = listOf(
    GameMeta(TrainingType.CRICKET, R.string.game_cricket, R.string.game_cricket_desc),
    GameMeta(TrainingType.BOBS27, R.string.game_bobs27, R.string.game_bobs27_desc),
    GameMeta(TrainingType.ATC, R.string.game_atc, R.string.game_atc_desc),
    GameMeta(TrainingType.SHANGHAI, R.string.game_shanghai, R.string.game_shanghai_desc),
    GameMeta(TrainingType.DOUBLES, R.string.game_doubles, R.string.game_doubles_desc),
)

@Composable
fun TrainingListScreen(
    container: AppContainer,
    onOpenGame: (TrainingType) -> Unit,
    onOpenStore: () -> Unit,
) {
    val entitlements by container.entitlements.entitlements.collectAsState()
    val unlocked = entitlements.contains(Feature.TRAINING)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!unlocked) {
            LockedFeatureCard(
                message = stringResource(R.string.training_locked_msg),
                ctaText = stringResource(R.string.common_unlock),
                onUnlock = onOpenStore,
            )
        }
        GAMES.forEach { meta ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(meta.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(meta.descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { if (unlocked) onOpenGame(meta.type) else onOpenStore() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (unlocked) stringResource(R.string.game_restart) else stringResource(R.string.common_unlock))
                    }
                }
            }
        }
    }
}

@Composable
fun TrainingGameScreen(
    container: AppContainer,
    type: TrainingType,
    onExit: () -> Unit,
) {
    val vm = containerViewModel { TrainingViewModel(it, type) }
    val game by vm.game.collectAsState()
    val view = game.view()
    var useBoard by remember { mutableStateOf(false) }

    val titleRes = GAMES.first { it.type == type }.titleRes

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Text(stringResource(titleRes), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { useBoard = !useBoard }) {
                    Icon(if (useBoard) Icons.Filled.GridOn else Icons.Filled.Apps, contentDescription = null)
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.game_target), style = MaterialTheme.typography.labelMedium)
                        Text(view.targetLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (view.steps > 0) {
                            Text("${view.step}/${view.steps}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.game_score), style = MaterialTheme.typography.labelMedium)
                        Text("${view.score}", fontSize = 40.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            if (view.finished) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (view.shanghai) "Shanghai! " + stringResource(R.string.game_finished)
                            else if (view.busted) stringResource(R.string.match_bust)
                            else stringResource(R.string.game_finished),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(onClick = { vm.restart() }) { Text(stringResource(R.string.game_restart)) }
                        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.common_back)) }
                    }
                }
            } else {
                OutlinedButton(onClick = { vm.onMiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.game_miss))
                }
                if (useBoard) {
                    Dartboard(onDart = { vm.onDart(it) })
                } else {
                    DartKeypad(onDart = { vm.onDart(it) })
                }
            }
        }
    }
}
