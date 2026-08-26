package com.solgram.domain.detect

enum class Chain { SOLANA, EVM, BSC, BASE, ARBITRUM, POLYGON, AVAX }

data class Detection(
    val chain: Chain,
    val address: String,
    val range: IntRange,
    val confidence: Double = 1.0
)

object CaDetector {
    // Solana: base58 32-44 chars, excluding 0 O I l
    private val SOLANA = Regex("(?<![1-9A-HJ-NP-Za-km-z])([1-9A-HJ-NP-Za-km-z]{32,44})(?![1-9A-HJ-NP-Za-km-z])")
    private val EVM = Regex("(?<![0-9a-zA-Z])(0x[a-fA-F0-9]{40})(?![0-9a-zA-Z])")

    fun detect(text: String): List<Detection> {
        if (text.isBlank()) return emptyList()
        val claimed = mutableListOf<IntRange>()
        val results = mutableListOf<Detection>()

        for (m in SOLANA.findAll(text)) {
            val token = m.groups[1]?.value ?: m.value
            if (isNoise(token)) continue
            if (!looksLikeSolanaAddress(token)) continue
            val range = m.range
            claimed += range
            results += Detection(Chain.SOLANA, token, range)
        }

        for (m in EVM.findAll(text)) {
            val token = m.value
            if (claimed.any { it.overlaps(m.range) }) continue
            // basic checksum not enforced - pattern based as spec says
            results += Detection(detectEvmChain(token, text), token, m.range)
        }

        return results.distinctBy { it.address.lowercase() + it.range.first }
    }

    private fun detectEvmChain(address: String, context: String): Chain {
        val lower = context.lowercase()
        return when {
            "bsc" in lower || "bnb" in lower || "pancake" in lower -> Chain.BSC
            "base" in lower -> Chain.BASE
            "arbitrum" in lower || "arb" in lower -> Chain.ARBITRUM
            "polygon" in lower || "matic" in lower -> Chain.POLYGON
            "avax" in lower || "avalanche" in lower -> Chain.AVAX
            else -> Chain.EVM
        }
    }

    private fun isNoise(token: String): Boolean {
        if (token.startsWith("http://") || token.startsWith("https://") ||
            token.startsWith("t.me/") || token.startsWith("@")) return true
        // filter out words that are not mixed case/digit
        val classes = listOf(
            token.any { it.isUpperCase() },
            token.any { it.isLowerCase() },
            token.any { it.isDigit() }
        ).count { it }
        if (classes < 2) return true
        // filter common English words that happen to be base58
        if (token.length < 32) return true
        // filter out repeated char patterns
        if (token.toSet().size < 10) return true
        return false
    }

    private fun looksLikeSolanaAddress(token: String): Boolean {
        // Additional heuristics: Solana addresses are base58, we already matched
        // Filter out obvious non-addresses
        if (token.length < 32 || token.length > 44) return false
        // Must not be all same case
        if (token.all { it.isUpperCase() } || token.all { it.isLowerCase() }) return false
        return true
    }

    private fun IntRange.overlaps(other: IntRange): Boolean {
        return first <= other.last && other.first <= last
    }

    fun extractTickers(text: String): List<String> {
        val tickerRegex = Regex("\\\$[A-Z]{2,10}\\b")
        return tickerRegex.findAll(text).map { it.value }.toList()
    }

    fun stripNoiseForLanguageDetection(text: String): String {
        var cleaned = text
        // remove CAs
        detect(text).forEach { det ->
            cleaned = cleaned.replace(det.address, " ")
        }
        // remove URLs
        cleaned = cleaned.replace(Regex("https?://\\S+"), " ")
        // remove tickers
        cleaned = cleaned.replace(Regex("\\\$[A-Z]{2,10}\\b"), " ")
        // remove mentions
        cleaned = cleaned.replace(Regex("@\\w+"), " ")
        return cleaned.trim()
    }
}
