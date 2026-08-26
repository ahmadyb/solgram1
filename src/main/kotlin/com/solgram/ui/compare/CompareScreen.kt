package com.solgram.ui.compare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.ui.theme.SolgramTheme

data class ComparisonResult(
    val ultimateChannel: String,
    val matchChannel: String,
    val sharePct: Double,
    val sharedContracts: List<SharedContract>,
    val combinedSuccessRate: Double
)

data class SharedContract(
    val address: String,
    val ultimateTimestamp: Long,
    val matchTimestamp: Long,
    val timeDiffSeconds: Long // negative = match called first
)

@Composable
fun CompareChannelsScreen(
    ultimateChannel: String?,
    matchChannels: List<String>,
    results: List<ComparisonResult>,
    combinedSuccessRate: Double,
    matchWindow: String,
    onMatchWindowChange: (String) -> Unit,
    lookback: String,
    onLookbackChange: (String) -> Unit,
    chainFilter: String,
    onChainFilterChange: (String) -> Unit,
    theme: SolgramTheme,
    onCompare: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compare Channels", style = MaterialTheme.typography.headlineMedium)

        Text("Ultimate: ${ultimateChannel ?: "Not selected"}")
        Text("Match channels: ${matchChannels.joinToString()}")

        Row {
            listOf("1h", "6h", "24h", "3d").forEach { window ->
                FilterChip(selected = matchWindow == window, onClick = { onMatchWindowChange(window) }, label = { Text(window) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Row {
            listOf("24h", "7d", "30d").forEach { lb ->
                FilterChip(selected = lookback == lb, onClick = { onLookbackChange(lb) }, label = { Text(lb) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Row {
            listOf("All", "Solana", "EVM").forEach { chain ->
                FilterChip(selected = chainFilter == chain, onClick = { onChainFilterChange(chain) }, label = { Text(chain) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Button(onClick = onCompare) { Text("Compare") }

        if (results.isEmpty()) {
            Text("Only sees contracts already cached locally - low rate may mean not enough data", style = MaterialTheme.typography.bodySmall)
        }

        Text("Combined Success Rate: ${(combinedSuccessRate*100).toInt()}% - share of ultimate channel's tokens that AT LEAST ONE match channel also called (each token counts once)")

        LazyColumn {
            items(results) { result ->
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${result.matchChannel}: ${"%.1f".format(result.sharePct)}% share")
                        LinearProgressIndicator(progress = (result.sharePct/100).toFloat(), modifier = Modifier.fillMaxWidth())
                        result.sharedContracts.take(5).forEach { contract ->
                            val sign = if (contract.timeDiffSeconds < 0) "called first" else "called after"
                            Text("${contract.address.take(8)}... ${contract.timeDiffSeconds}s $sign", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
