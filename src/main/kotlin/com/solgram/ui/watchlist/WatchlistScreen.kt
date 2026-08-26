package com.solgram.ui.watchlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solgram.domain.portfolio.CrossReferenceResult
import com.solgram.domain.portfolio.WatchedWallet
import com.solgram.ui.theme.SolgramTheme

@Composable
fun WatchlistScreen(
    wallets: List<WatchedWallet>,
    crossRefs: Map<String, CrossReferenceResult>,
    onAddWallet: (String, String) -> Unit,
    onRemoveWallet: (String) -> Unit,
    theme: SolgramTheme
) {
    var addressInput by remember { mutableStateOf("") }
    var chainInput by remember { mutableStateOf("solana") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Portfolio Watchlist - Read-only wallet polling", style = MaterialTheme.typography.headlineMedium)
        Text("Paste public wallet address (Solana or EVM) and Solgram polls holdings via public RPC/indexer, cross-references against Signals history. Read-only, no private key ever requested.", style = MaterialTheme.typography.bodySmall)

        Row {
            TextField(value = addressInput, onValueChange = { addressInput = it }, placeholder = { Text("Wallet address") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = chainInput, onValueChange = { chainInput = it }, placeholder = { Text("Chain") }, modifier = Modifier.width(100.dp))
            Button(onClick = { if (addressInput.isNotBlank()) { onAddWallet(addressInput, chainInput); addressInput = "" } }) { Text("Add") }
        }

        Text("Polling capped to respect RPC rate limits, fast activity may be delayed", style = MaterialTheme.typography.bodySmall)

        LazyColumn {
            items(wallets) { wallet ->
                Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${wallet.label}: ${wallet.address.take(12)}... (${wallet.chain})")
                        val cross = crossRefs[wallet.address]
                        if (cross != null) {
                            Text("Holds ${cross.totalHoldings} tokens; you already had ${cross.alreadyTracked} tracked before; ${cross.trackedBeforeBuy} you had first", style = MaterialTheme.typography.bodySmall)
                            cross.details.take(5).forEach { detail ->
                                Text("${detail.tokenAddress.take(8)}... youHadFirst=${detail.youHadItFirst}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(onClick = { onRemoveWallet(wallet.address) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
