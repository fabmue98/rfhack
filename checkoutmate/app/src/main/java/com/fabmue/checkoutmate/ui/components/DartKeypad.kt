package com.fabmue.checkoutmate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.domain.Dart
import com.fabmue.checkoutmate.domain.Ring

/**
 * Per-dart keypad. A Double/Triple modifier applies to the next number tap and
 * then resets to single, which matches how darts scoreboards are entered.
 */
@Composable
fun DartKeypad(
    onDart: (Dart) -> Unit,
    modifier: Modifier = Modifier,
) {
    var multiplier by remember { mutableStateOf(Ring.SINGLE) }

    fun emit(dart: Dart) {
        onDart(dart)
        multiplier = Ring.SINGLE
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Modifier selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModifierChip(stringResource(R.string.match_single), multiplier == Ring.SINGLE, Modifier.weight(1f)) { multiplier = Ring.SINGLE }
            ModifierChip(stringResource(R.string.match_double), multiplier == Ring.DOUBLE, Modifier.weight(1f)) { multiplier = Ring.DOUBLE }
            ModifierChip(stringResource(R.string.match_triple), multiplier == Ring.TRIPLE, Modifier.weight(1f)) { multiplier = Ring.TRIPLE }
        }
        // Numbers 1..20 in a 4-column grid
        val rows = (1..20).chunked(5)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { n ->
                    NumberKey(n.toString(), Modifier.weight(1f)) {
                        emit(Dart(n, multiplier))
                    }
                }
            }
        }
        // Bull row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberKey("25", Modifier.weight(1f)) { emit(Dart.OUTER_BULL) }
            NumberKey("Bull", Modifier.weight(1f)) { emit(Dart.BULL) }
            OutlinedButton(
                onClick = { emit(Dart.MISS) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.match_miss)) }
        }
    }
}

@Composable
private fun ModifierChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(44.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(44.dp)) { Text(label) }
    }
}

@Composable
private fun NumberKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
