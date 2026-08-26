package com.solgram.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.ui.theme.BackdropType
import com.solgram.ui.theme.SolgramTheme

@Composable
fun SettingsScreen(
    currentTheme: SolgramTheme,
    onThemeChange: (SolgramTheme) -> Unit,
    backdropType: BackdropType,
    onBackdropChange: (BackdropType) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    keyboardNavEnabled: Boolean,
    onKeyboardNavToggle: (Boolean) -> Unit,
    backgroundSyncEnabled: Boolean,
    onBackgroundSyncToggle: (Boolean) -> Unit,
    dataSaver: Boolean,
    onDataSaverToggle: (Boolean) -> Unit,
    apiServerEnabled: Boolean,
    onApiServerToggle: (Boolean) -> Unit,
    bearerToken: String,
    onRegenerateToken: () -> Unit,
    theme: SolgramTheme
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Appearance - Five themes, six backdrops, per-chat override, keyboard navigation", style = MaterialTheme.typography.titleMedium)

        Text("Themes:")
        Row {
            SolgramTheme.values().forEach { th ->
                FilterChip(selected = currentTheme == th, onClick = { onThemeChange(th) }, label = { Text(th.name) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Text("Backdrops:")
        Row {
            BackdropType.values().forEach { bt ->
                FilterChip(selected = backdropType == bt, onClick = { onBackdropChange(bt) }, label = { Text(bt.name) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Text("Pattern Intensity: ${(intensity*100).toInt()}% (0-200%)")
        Slider(value = intensity, onValueChange = onIntensityChange, valueRange = 0f..2f)

        Text("Interface Scale: ${(scale*100).toInt()}% (85-130%)")
        Slider(value = scale, onValueChange = onScaleChange, valueRange = 0.85f..1.3f)

        Row {
            Text("Keyboard-First Navigation Mode")
            Switch(checked = keyboardNavEnabled, onCheckedChange = onKeyboardNavToggle)
        }
        Text("Chat-list up/down, message scroll, reply/react/copy on focused message, composer focus, quick chat search - all without mouse. Shortcuts listed and remappable, not hidden.", style = MaterialTheme.typography.bodySmall)

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Resilience - Sync Scheduler", style = MaterialTheme.typography.titleMedium)
        Row {
            Text("Incremental/Background Chat Sync Scheduler (ON/OFF, default ON)")
            Switch(checked = backgroundSyncEnabled, onCheckedChange = onBackgroundSyncToggle)
        }
        Text("When ON, proactively syncs chats not opened recently during idle time, low-priority lane. Reduces 'only sees cached data' caveat. OFF = sync only when opened.", style = MaterialTheme.typography.bodySmall)

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Data Saver Mode - thumbnails only, full file on click")
        Switch(checked = dataSaver, onCheckedChange = onDataSaverToggle)

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Local API Server - default OFF, bound to 127.0.0.1 only, bearer-token protected, read-only")
        Row {
            Text("API Server")
            Switch(checked = apiServerEnabled, onCheckedChange = onApiServerToggle)
        }
        Text("Token: ${bearerToken.take(12)}...")
        Button(onClick = onRegenerateToken) { Text("Regenerate Token") }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Storage: %APPDATA%\\Solgram\\ - unencrypted by default, SQLCipher optional")
        Text("Network: Telegram (MTProto via TDLib), translation only on Translate, trading site only on Trade, public RPC/indexer only if Portfolio Watchlist configured, 127.0.0.1 if local API running, webhook URL only if explicitly configured")
    }
}
