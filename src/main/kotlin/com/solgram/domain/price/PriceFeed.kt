package com.solgram.domain.price

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class PriceSample(
    val address: String,
    val chain: String,
    val priceUsd: Double,
    val marketCap: Double?,
    val liquidity: Double?,
    val timestamp: Long,
    val athPrice: Double? = null
)

data class CallPerformance(
    val address: String,
    val firstCallPrice: Double?,
    val currentPrice: Double?,
    val athPrice: Double?,
    val athMultiple: Double?,
    val firstCallAt: Long,
    val hit2x: Boolean = false,
    val hit5x: Boolean = false,
    val hit10x: Boolean = false
)

class PriceFeed(
    private val scope: CoroutineScope
) {
    private val _prices = MutableStateFlow<Map<String, PriceSample>>(emptyMap())
    val prices: StateFlow<Map<String, PriceSample>> = _prices.asStateFlow()

    private val priceHistory = ConcurrentHashMap<String, MutableList<PriceSample>>()
    private var pollingJob: Job? = null

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pollAll()
                } catch (e: Exception) {
                    println("PriceFeed poll error: ${e.message}")
                }
                // Decaying frequency: recent tokens polled more often
                delay(30_000) // 30s base, real would decay
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    suspend fun trackAddress(address: String, chain: String) {
        // Add to tracking set
        val current = _prices.value.toMutableMap()
        if (!current.containsKey(address)) {
            val sample = fetchPrice(address, chain)
            current[address] = sample
            _prices.value = current
            priceHistory.getOrPut(address) { mutableListOf() }.add(sample)
        }
    }

    private suspend fun pollAll() {
        val current = _prices.value
        if (current.isEmpty()) return
        val updated = mutableMapOf<String, PriceSample>()
        for ((addr, old) in current) {
            val fresh = fetchPrice(addr, old.chain)
            updated[addr] = fresh
            priceHistory.getOrPut(addr) { mutableListOf() }.add(fresh)
            // Keep only last 1000 samples per token to bound memory
            val history = priceHistory[addr]!!
            if (history.size > 1000) {
                history.subList(0, history.size - 1000).clear()
            }
        }
        _prices.value = updated
    }

    private suspend fun fetchPrice(address: String, chain: String): PriceSample {
        // In real implementation, would call DexScreener, Birdeye, etc.
        // Here we simulate with random walk for development
        val existing = priceHistory[address]?.lastOrNull()
        val basePrice = existing?.priceUsd ?: Random.nextDouble(0.000001, 0.01)
        // Random walk +/- 10%
        val change = Random.nextDouble(0.9, 1.1)
        val newPrice = (basePrice * change).coerceAtLeast(0.0000001)
        val ath = maxOf(existing?.athPrice ?: newPrice, newPrice)

        return PriceSample(
            address = address,
            chain = chain,
            priceUsd = newPrice,
            marketCap = newPrice * Random.nextDouble(1_000_000.0, 100_000_000.0),
            liquidity = Random.nextDouble(1000.0, 100_000.0),
            timestamp = System.currentTimeMillis() / 1000,
            athPrice = ath
        )
    }

    fun getHistory(address: String): List<PriceSample> {
        return priceHistory[address]?.toList() ?: emptyList()
    }

    fun getPerformance(address: String, firstCallAt: Long, firstCallPrice: Double?): CallPerformance? {
        val history = priceHistory[address] ?: return null
        val current = history.lastOrNull() ?: return null
        val ath = history.maxByOrNull { it.priceUsd }?.priceUsd ?: current.priceUsd
        val firstPrice = firstCallPrice ?: history.firstOrNull()?.priceUsd
        val multiple = if (firstPrice != null && firstPrice > 0) ath / firstPrice else null

        return CallPerformance(
            address = address,
            firstCallPrice = firstPrice,
            currentPrice = current.priceUsd,
            athPrice = ath,
            athMultiple = multiple,
            firstCallAt = firstCallAt,
            hit2x = (multiple ?: 0.0) >= 2.0,
            hit5x = (multiple ?: 0.0) >= 5.0,
            hit10x = (multiple ?: 0.0) >= 10.0
        )
    }
}
