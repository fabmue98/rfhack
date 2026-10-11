package com.fabmue.checkoutmate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.BuildConfig
import com.fabmue.checkoutmate.R
import com.fabmue.checkoutmate.data.Feature
import com.fabmue.checkoutmate.data.Settings
import com.fabmue.checkoutmate.ui.theme.BoardThemes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenStore: () -> Unit,
    onRecreate: () -> Unit,
) {
    val settings by container.prefs.settings.collectAsState(initial = Settings())
    val entitlements by container.entitlements.entitlements.collectAsState()
    val scope = rememberCoroutineScope()

    var showLanguage by remember { mutableStateOf(false) }
    var showPrefDouble by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Group(stringResource(R.string.settings_language)) {
            RowItem(stringResource(R.string.settings_language), languageLabel(settings.language)) { showLanguage = true }
        }

        Group("Checkout") {
            RowItem(stringResource(R.string.settings_pref_double), prefDoubleLabel(settings.prefDouble)) { showPrefDouble = true }
            SwitchItem(stringResource(R.string.settings_default_double_out), settings.defaultDoubleOut) {
                scope.launch { container.prefs.setDefaultDoubleOut(it) }
            }
            SwitchItem(stringResource(R.string.settings_default_double_in), settings.defaultDoubleIn) {
                scope.launch { container.prefs.setDefaultDoubleIn(it) }
            }
        }

        Group(stringResource(R.string.settings_theme)) {
            RowItem(stringResource(R.string.settings_theme), themeLabel(settings.theme)) {
                if (entitlements.contains(Feature.THEMES)) showTheme = true else onOpenStore()
            }
        }

        Group(stringResource(R.string.nav_play)) {
            SwitchItem(stringResource(R.string.settings_voice), settings.voice) {
                if (entitlements.contains(Feature.VOICE)) scope.launch { container.prefs.setVoice(it) } else onOpenStore()
            }
            SwitchItem(stringResource(R.string.settings_haptics), settings.haptics) {
                scope.launch { container.prefs.setHaptics(it) }
            }
            SwitchItem(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOn) {
                scope.launch { container.prefs.setKeepScreenOn(it) }
            }
        }

        if (BuildConfig.DEBUG) {
            Group("Debug") {
                SwitchItem(stringResource(R.string.settings_dev_unlock), settings.devUnlock) {
                    scope.launch { container.prefs.setDevUnlock(it) }
                }
            }
        }

        Group(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenStore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.store_title))
            }
        }
    }

    if (showLanguage) {
        OptionDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                "system" to stringResource(R.string.settings_language_system),
                "en" to stringResource(R.string.settings_language_en),
                "de" to stringResource(R.string.settings_language_de),
            ),
            selected = settings.language,
            onSelect = {
                scope.launch {
                    container.prefs.setLanguage(it)
                    onRecreate()
                }
                showLanguage = false
            },
            onDismiss = { showLanguage = false },
        )
    }

    if (showPrefDouble) {
        OptionDialog(
            title = stringResource(R.string.settings_pref_double),
            options = listOf(
                0 to stringResource(R.string.settings_pref_double_none),
                20 to "D20", 16 to "D16", 8 to "D8", 4 to "D4", 25 to "Bull",
            ),
            selected = settings.prefDouble,
            onSelect = {
                scope.launch { container.prefs.setPrefDouble(it) }
                showPrefDouble = false
            },
            onDismiss = { showPrefDouble = false },
        )
    }

    if (showTheme) {
        OptionDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                BoardThemes.DARK to stringResource(R.string.settings_theme_dark),
                BoardThemes.AMOLED to stringResource(R.string.settings_theme_amoled),
                BoardThemes.FELT to stringResource(R.string.settings_theme_felt),
            ),
            selected = settings.theme,
            onSelect = {
                scope.launch { container.prefs.setTheme(it) }
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }
}

@Composable
private fun languageLabel(code: String) = when (code) {
    "en" -> stringResource(R.string.settings_language_en)
    "de" -> stringResource(R.string.settings_language_de)
    else -> stringResource(R.string.settings_language_system)
}

@Composable
private fun prefDoubleLabel(value: Int) = when (value) {
    0 -> stringResource(R.string.settings_pref_double_none)
    25 -> "Bull"
    else -> "D$value"
}

@Composable
private fun themeLabel(theme: String) = when (theme) {
    BoardThemes.AMOLED -> stringResource(R.string.settings_theme_amoled)
    BoardThemes.FELT -> stringResource(R.string.settings_theme_felt)
    else -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            content()
        }
    }
}

@Composable
private fun RowItem(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = value == selected, onClick = { onSelect(value) }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
