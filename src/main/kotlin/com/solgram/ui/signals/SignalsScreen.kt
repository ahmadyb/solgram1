package com.solgram.ui.signals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.domain.signals.*
import com.solgram.ui.theme.SolgramTheme
import com.solgram.ui.theme.ThemeRegistry

enum class SignalsViewMode { BY_TOKEN, BY_CALL }

@Composable
fun SignalsDashboardScreen(
    viewMode: SignalsViewMode,
    onViewModeChange: (SignalsViewMode) -> Unit,
    signalsByToken: List<TokenSignal>,
    signalsByCall: List<CallSignal>,
    reputationEngine: ReputationEngine,
    velocityAlerts: List<VelocityAlert>,
    leaderboard: List<LeaderboardEntry>,
    sentimentSummaries: Map<String, SentimentSummary>,
    theme: SolgramTheme
) {
    val colors = ThemeRegistry.getColors(theme)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { onViewModeChange(SignalsViewMode.BY_TOKEN) }, enabled = viewMode != SignalsViewMode.BY_TOKEN) { Text("BY TOKEN") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onViewModeChange(SignalsViewMode.BY_CALL) }, enabled = viewMode != SignalsViewMode.BY_CALL) { Text("BY CALL") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (viewMode) {
            SignalsViewMode.BY_TOKEN -> {
                LazyColumn {
                    items(signalsByToken) { token ->
                        TokenRow(token, sentimentSummaries[token.address], theme)
                    }
                }
            }
            SignalsViewMode.BY_CALL -> {
                LazyColumn {
                    items(signalsByCall) { call ->
                        CallRow(call, theme)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Velocity Alerts (N distinct callers within M minutes):", style = MaterialTheme.typography.titleMedium, color = colors.text)
        velocityAlerts.forEach { alert ->
            Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("${alert.address} - ${alert.distinctCallers} callers in ${alert.windowMinutes}min", color = colors.text)
                    Text("Callers: ${alert.callerNames.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Leaderboard (7d/30d/all-time):", style = MaterialTheme.typography.titleMedium, color = colors.text)
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(leaderboard.take(10)) { entry ->
                Text("${entry.channelName}: avg ${"%.2f".format(entry.avgAthMultiple)}x, ${(entry.hitRate2x*100).toInt()}% hit 2x, ${entry.totalCalls} calls")
            }
        }

        // Reputation decay suggestions
        val suggestions by reputationEngine.suggestions.collectAsState()
        if (suggestions.isNotEmpty()) {
            Text("Reputation suggestions (manual approval required):", color = colors.text)
            suggestions.forEach { sug ->
                Row {
                    Text("suggested: ${sug.suggested}★ (was ${sug.was}★) - ${sug.reason}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { reputationEngine.applySuggestion(sug.channelId) }) { Text("Apply") }
                    Button(onClick = { reputationEngine.dismissSuggestion(sug.channelId) }) { Text("Dismiss") }
                }
            }
        }
    }
}

data class TokenSignal(
    val address: String,
    val chain: String,
    val callers: List<CallerInfo>,
    val crowdConfidence: CrowdConfidence,
    val firstCallAt: Long
)

data class CallerInfo(
    val order: Int,
    val channel: String,
    val trust: Int,
    val gap: String,
    val timestamp: Long
)

data class CallSignal(
    val address: String,
    val chain: String,
    val caller: String,
    val timestamp: Long,
    val trust: Int
)

@Composable
fun TokenRow(token: TokenSignal, sentiment: SentimentSummary?, theme: SolgramTheme) {
    val colors = ThemeRegistry.getColors(theme)
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                Text(token.address.take(12) + "...", color = colors.text)
                Spacer(modifier = Modifier.width(8.dp))
                Badge { Text(token.chain) }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crowd: ${token.crowdConfidence.distinctChannels} channels", style = MaterialTheme.typography.bodySmall)
                if (sentiment != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sentiment.trend, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                }
            }
            token.callers.forEach { caller ->
                Text("${caller.order}. ${caller.channel} trust=${caller.trust} gap=${caller.gap}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CallRow(call: CallSignal, theme: SolgramTheme) {
    val colors = ThemeRegistry.getColors(theme)
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Row(modifier = Modifier.padding(8.dp)) {
            Text(call.address.take(12), color = colors.text)
            Spacer(modifier = Modifier.width(8.dp))
            Text(call.caller, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(java.time.Instant.ofEpochSecond(call.timestamp).toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
