package com.solgram.domain.portfolio

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WatchedWallet(
    val address: String,
    val chain: String,
    val label: String,
    val addedAt: Long
)

data class WalletHolding(
    val walletAddress: String,
    val tokenAddress: String,
    val chain: String,
    val balance: Double,
    val symbol: String?,
    val firstSeenAt: Long,
    val lastUpdatedAt: Long
)

data class CrossReferenceResult(
    val walletAddress: String,
    val totalHoldings: Int,
    val alreadyTracked: Int,
    val trackedBeforeBuy: Int,
    val details: List<CrossRefDetail>
)

data class CrossRefDetail(
    val tokenAddress: String,
    val firstTrackedInSignalsAt: Long?,
    val firstBoughtByWalletAt: Long?,
    val youHadItFirst: Boolean
)

/**
 * Read-only wallet polling via public RPC/indexer
 * No private key ever requested or stored
 * Polling frequency capped to respect rate limits
 */
class PortfolioWatcher(
    private val scope: CoroutineScope
) {
    private val _wallets = MutableStateFlow<List<WatchedWallet>>(emptyList())
    val wallets: StateFlow<List<WatchedWallet>> = _wallets.asStateFlow()

    private val _holdings = MutableStateFlow<Map<String, List<WalletHolding>>>(emptyMap())
    val holdings: StateFlow<Map<String, List<WalletHolding>>> = _holdings.asStateFlow()

    private var pollJob: Job? = null

    fun addWallet(address: String, chain: String, label: String = "") {
        val wallet = WatchedWallet(address, chain, label.ifBlank { address.take(8) }, System.currentTimeMillis()/1000)
        _wallets.value = _wallets.value + wallet
    }

    fun removeWallet(address: String) {
        _wallets.value = _wallets.value.filter { it.address != address }
        _holdings.value = _holdings.value.filter { it.key != address }
    }

    fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollAllWallets()
                } catch (e: Exception) {
                    println("PortfolioWatcher poll error: ${e.message}")
                }
                delay(60_000) // 1 minute cap to respect rate limits, very fast activity may be delayed
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    private suspend fun pollAllWallets() {
        val wallets = _wallets.value
        for (wallet in wallets) {
            val holdings = fetchHoldings(wallet)
            val current = _holdings.value.toMutableMap()
            current[wallet.address] = holdings
            _holdings.value = current
            delay(1000) // Rate limit between wallets
        }
    }

    private suspend fun fetchHoldings(wallet: WatchedWallet): List<WalletHolding> {
        // Real would call public RPC/indexer
        // Mock: generate random holdings
        return (1..kotlin.random.Random.nextInt(3, 12)).map {
            WalletHolding(
                walletAddress = wallet.address,
                tokenAddress = "Token${it}_${wallet.address.take(4)}",
                chain = wallet.chain,
                balance = kotlin.random.Random.nextDouble(100.0, 10000.0),
                symbol = "TKN$it",
                firstSeenAt = System.currentTimeMillis()/1000 - kotlin.random.Random.nextLong(0, 86400*7),
                lastUpdatedAt = System.currentTimeMillis()/1000
            )
        }
    }

    fun crossReference(
        walletAddress: String,
        signalsHistory: Map<String, Long> // address -> first tracked at
    ): CrossReferenceResult {
        val holdings = _holdings.value[walletAddress] ?: emptyList()
        val details = holdings.map { holding ->
            val firstTracked = signalsHistory[holding.tokenAddress]
            val youHadItFirst = if (firstTracked != null) firstTracked < holding.firstSeenAt else false
            CrossRefDetail(
                tokenAddress = holding.tokenAddress,
                firstTrackedInSignalsAt = firstTracked,
                firstBoughtByWalletAt = holding.firstSeenAt,
                youHadItFirst = youHadItFirst
            )
        }

        val alreadyTracked = details.count { it.firstTrackedInSignalsAt != null }
        val trackedBeforeBuy = details.count { it.youHadItFirst }

        return CrossReferenceResult(
            walletAddress = walletAddress,
            totalHoldings = holdings.size,
            alreadyTracked = alreadyTracked,
            trackedBeforeBuy = trackedBeforeBuy,
            details = details
        )
    }
}
