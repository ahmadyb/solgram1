package com.solgram.domain.rug

import com.solgram.domain.detect.Chain

enum class RugRisk { LOW, MEDIUM, HIGH, CRITICAL }

data class RugCheck(
    val address: String,
    val chain: Chain,
    val risk: RugRisk,
    val reasons: List<String>,
    val checkedAt: Long,
    val isHeuristic: Boolean = true
)

object RugHeuristics {

    /**
     * On-chain heuristic checks - not predictive, informational only
     */
    suspend fun check(address: String, chain: Chain): RugCheck {
        val reasons = mutableListOf<String>()
        var riskScore = 0

        // Simulated checks - real would query chain
        // 1. Liquidity lock
        val hasLockedLiquidity = checkLiquidityLock(address, chain)
        if (!hasLockedLiquidity) {
            reasons.add("Liquidity not locked")
            riskScore += 2
        }

        // 2. Mint authority
        val hasMintAuthority = checkMintAuthority(address, chain)
        if (hasMintAuthority) {
            reasons.add("Mint authority still enabled")
            riskScore += 3
        }

        // 3. Top holders concentration
        val topHolderPct = checkTopHolders(address, chain)
        if (topHolderPct > 0.5) {
            reasons.add("Top 10 holders own ${(topHolderPct*100).toInt()}%")
            riskScore += 2
        }

        // 4. Honeypot check
        val isHoneypot = checkHoneypot(address, chain)
        if (isHoneypot) {
            reasons.add("Honeypot detected - cannot sell")
            riskScore += 5
        }

        val risk = when {
            riskScore >= 5 -> RugRisk.CRITICAL
            riskScore >= 3 -> RugRisk.HIGH
            riskScore >= 2 -> RugRisk.MEDIUM
            else -> RugRisk.LOW
        }

        if (reasons.isEmpty()) {
            reasons.add("No obvious red flags (heuristic only, not audit)")
        }

        return RugCheck(
            address = address,
            chain = chain,
            risk = risk,
            reasons = reasons,
            checkedAt = System.currentTimeMillis()/1000
        )
    }

    private suspend fun checkLiquidityLock(address: String, chain: Chain): Boolean {
        // Mock - real would check lockers
        return (0..1).random() == 1
    }

    private suspend fun checkMintAuthority(address: String, chain: Chain): Boolean {
        return (0..2).random() == 0
    }

    private suspend fun checkTopHolders(address: String, chain: Chain): Double {
        return kotlin.random.Random.nextDouble(0.1, 0.8)
    }

    private suspend fun checkHoneypot(address: String, chain: Chain): Boolean {
        return kotlin.random.Random.nextDouble() < 0.05
    }
}
