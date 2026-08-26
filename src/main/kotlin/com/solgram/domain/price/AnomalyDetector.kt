package com.solgram.domain.price

/**
 * Detects PRICE PATTERNS that often accompany a rug, after they begin to happen
 * - not predictive, legitimate token having rough day can trigger same rules
 * - one more signal, never verdict
 */
enum class AnomalyType {
    LIQUIDITY_DROP,
    LARGE_SELL_CLUSTER,
    PRICE_COLLAPSE,
    VOLUME_SPIKE
}

data class AnomalyFlag(
    val address: String,
    val type: AnomalyType,
    val severity: Int, // 1-5
    val description: String,
    val detectedAt: Long,
    val isHeuristic: Boolean = true
)

object AnomalyDetector {

    fun detect(history: List<PriceSample>): List<AnomalyFlag> {
        if (history.size < 10) return emptyList()
        val flags = mutableListOf<AnomalyFlag>()

        // 1. Sudden near-total liquidity withdrawal
        detectLiquidityDrop(history)?.let { flags.add(it) }

        // 2. Single wallet large share of sell volume (simulated via price/volume pattern)
        detectSellCluster(history)?.let { flags.add(it) }

        // 3. Price collapse disproportionate to market
        detectPriceCollapse(history)?.let { flags.add(it) }

        // 4. Volume spike
        detectVolumeSpike(history)?.let { flags.add(it) }

        return flags
    }

    private fun detectLiquidityDrop(history: List<PriceSample>): AnomalyFlag? {
        if (history.size < 5) return null
        val recent = history.takeLast(5)
        val older = history.takeLast(20).take(15)
        if (older.isEmpty() || recent.isEmpty()) return null

        val avgOldLiquidity = older.mapNotNull { it.liquidity }.average().takeIf { !it.isNaN() } ?: return null
        val avgRecentLiquidity = recent.mapNotNull { it.liquidity }.average().takeIf { !it.isNaN() } ?: return null

        if (avgOldLiquidity > 0 && avgRecentLiquidity / avgOldLiquidity < 0.2) {
            // 80%+ drop
            return AnomalyFlag(
                address = recent.last().address,
                type = AnomalyType.LIQUIDITY_DROP,
                severity = 5,
                description = "Liquidity dropped ${(100 - (avgRecentLiquidity/avgOldLiquidity*100)).toInt()}% - possible rug pattern (heuristic, not verdict)",
                detectedAt = System.currentTimeMillis()/1000
            )
        }
        return null
    }

    private fun detectSellCluster(history: List<PriceSample>): AnomalyFlag? {
        if (history.size < 10) return null
        // Detect rapid consecutive price drops
        val recent = history.takeLast(10)
        var consecutiveDrops = 0
        for (i in 1 until recent.size) {
            if (recent[i].priceUsd < recent[i-1].priceUsd * 0.95) {
                consecutiveDrops++
            } else {
                consecutiveDrops = 0
            }
            if (consecutiveDrops >= 5) {
                return AnomalyFlag(
                    address = recent.last().address,
                    type = AnomalyType.LARGE_SELL_CLUSTER,
                    severity = 4,
                    description = "5+ consecutive >5% drops - large sell pressure pattern (heuristic)",
                    detectedAt = System.currentTimeMillis()/1000
                )
            }
        }
        return null
    }

    private fun detectPriceCollapse(history: List<PriceSample>): AnomalyFlag? {
        if (history.size < 20) return null
        val maxPrice = history.maxByOrNull { it.priceUsd }?.priceUsd ?: return null
        val current = history.last().priceUsd
        if (maxPrice > 0 && current / maxPrice < 0.1) {
            // 90% collapse from ATH
            return AnomalyFlag(
                address = history.last().address,
                type = AnomalyType.PRICE_COLLAPSE,
                severity = 5,
                description = "Price collapsed 90%+ from ATH - possible rug or market event (heuristic, verify independently)",
                detectedAt = System.currentTimeMillis()/1000
            )
        }
        return null
    }

    private fun detectVolumeSpike(history: List<PriceSample>): AnomalyFlag? {
        // Simplified - would need volume data
        return null
    }
}
