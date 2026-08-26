package com.solgram.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToastOverlay(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        Card(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.padding(8.dp)) {
                Text(message, modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun HealthDashboard(
    connectionState: String,
    apiServerStatus: String,
    ruleCounts: Map<String, Int>,
    lastMessageTime: Long?,
    lastDetectionTime: Long?,
    dbSize: String
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Health Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("MTProto: $connectionState")
        Text("Local API: $apiServerStatus")
        Text("Rules: ${ruleCounts.values.sum()} total, fires: ${ruleCounts["fires"] ?: 0}")
        Text("Last message: ${lastMessageTime?.let { java.time.Instant.ofEpochSecond(it) } ?: "never"}")
        Text("Last detection: ${lastDetectionTime?.let { java.time.Instant.ofEpochSecond(it) } ?: "never"}")
        Text("DB Size: $dbSize")
    }
}
