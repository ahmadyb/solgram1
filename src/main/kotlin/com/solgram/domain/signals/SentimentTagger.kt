package com.solgram.domain.signals

/**
 * Lightweight local keyword/phrase classifier - no external LLM, nothing leaves machine
 * Heuristic signal, explicitly labeled as such - not market judgment
 */
enum class Sentiment { POSITIVE, CAUTION, NEGATIVE, NEUTRAL }

data class SentimentTag(
    val messageId: Long,
    val address: String,
    val sentiment: Sentiment,
    val keywords: List<String>,
    val timestamp: Long
)

data class SentimentSummary(
    val address: String,
    val positive: Int,
    val caution: Int,
    val negative: Int,
    val total: Int,
    val trend: String // e.g. "12 positive, 2 caution mentions since call"
)

object SentimentTagger {

    private val positiveKeywords = listOf(
        "moon", "gem", "pump", "buy", "bullish", "strong", "hodl", "accumulate",
        "breakout", "ath", "new high", "going up", "rocket", "🚀", "💎",
        "diamond hands", "to the moon", "lets go", "lfg", "based", "good call",
        "great call", "nice call", "winner", "profit", "up", "green"
    )

    private val cautionKeywords = listOf(
        "caution", "careful", "risk", "volatile", "dip", "correction", "watch",
        "maybe", "uncertain", "sideways", "consolidation", "resistance",
        "overbought", "pullback", "weak", "hesitation"
    )

    private val negativeKeywords = listOf(
        "rug", "scam", "dump", "sell", "bearish", "down", "crash", "red",
        "loss", "rekt", "dead", "exit", "liquidity pulled", "honeypot",
        "avoid", "stay away", "warning", "danger", "fake", "shitcoin",
        "dumping", "selling", "panic"
    )

    fun tag(messageText: String, messageId: Long, address: String): SentimentTag {
        val lower = messageText.lowercase()
        val foundPositive = positiveKeywords.filter { lower.contains(it.lowercase()) }
        val foundCaution = cautionKeywords.filter { lower.contains(it.lowercase()) }
        val foundNegative = negativeKeywords.filter { lower.contains(it.lowercase()) }

        val sentiment = when {
            foundNegative.size > foundPositive.size && foundNegative.size > foundCaution.size -> Sentiment.NEGATIVE
            foundPositive.size > foundNegative.size && foundPositive.size > foundCaution.size -> Sentiment.POSITIVE
            foundCaution.isNotEmpty() -> Sentiment.CAUTION
            else -> Sentiment.NEUTRAL
        }

        val keywords = when (sentiment) {
            Sentiment.POSITIVE -> foundPositive
            Sentiment.CAUTION -> foundCaution
            Sentiment.NEGATIVE -> foundNegative
            else -> emptyList()
        }

        return SentimentTag(
            messageId = messageId,
            address = address,
            sentiment = sentiment,
            keywords = keywords,
            timestamp = System.currentTimeMillis()/1000
        )
    }

    fun summarize(address: String, tags: List<SentimentTag>): SentimentSummary {
        val relevant = tags.filter { it.address.equals(address, ignoreCase = true) }
        val positive = relevant.count { it.sentiment == Sentiment.POSITIVE }
        val caution = relevant.count { it.sentiment == Sentiment.CAUTION }
        val negative = relevant.count { it.sentiment == Sentiment.NEGATIVE }

        val trend = buildString {
            if (positive > 0) append("$positive positive")
            if (caution > 0) {
                if (isNotEmpty()) append(", ")
                append("$caution caution")
            }
            if (negative > 0) {
                if (isNotEmpty()) append(", ")
                append("$negative negative")
            }
            if (isNotEmpty()) append(" mentions since call") else append("no sentiment yet")
        }

        return SentimentSummary(
            address = address,
            positive = positive,
            caution = caution,
            negative = negative,
            total = relevant.size,
            trend = trend
        )
    }
}
