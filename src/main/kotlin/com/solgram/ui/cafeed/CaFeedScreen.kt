package com.solgram.ui.cafeed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.automation.CaLatest
import com.solgram.ui.theme.SolgramTheme

@Composable
fun CaFeedScreen(
    detections: List<CaLatest>,
    chainFilter: String,
    textFilter: String,
    onChainFilterChange: (String) -> Unit,
    onTextFilterChange: (String) -> Unit,
    onTrade: (String, String) -> Unit,
    theme: SolgramTheme
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("CA Feed - Live stream of every detected address", style = MaterialTheme.typography.headlineMedium)

        Row {
            TextField(value = textFilter, onValueChange = onTextFilterChange, placeholder = { Text("Filter by text") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            listOf("All", "Solana", "EVM").forEach { chain ->
                FilterChip(selected = chainFilter == chain, onClick = { onChainFilterChange(chain) }, label = { Text(chain) })
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        LazyColumn {
            items(detections.filter {
                (chainFilter == "All" || it.chain.equals(chainFilter, ignoreCase = true)) &&
                (textFilter.isBlank() || it.address.contains(textFilter, ignoreCase = true) || it.source.contains(textFilter, ignoreCase = true))
            }) { det ->
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${det.chain}: ${det.address.take(12)}... from ${det.source}")
                            Text(det.relativeTime, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onTrade(det.chain, det.address) }) { Text("Trade") }
                    }
                }
            }
        }
    }
}
