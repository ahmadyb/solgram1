package com.solgram.domain.signals

/**
 * Crowd confidence counts DISTINCT channels within rolling window, not raw mentions
 */
data class CrowdConfidence(
    val address: String,
    val distinctChannels: Int,
    val totalMentions: Int,
    val channelNames: List<String>,
    val windowHours: Int,
    val confidenceScore: Double // 0-1
)

object CrowdConfidenceEngine {

    fun compute(
        address: String,
        calls: List<CallerCall>,
        windowHours: Int = 24
    ): CrowdConfidence {
        val now = System.currentTimeMillis()/1000
        val windowStart = now - windowHours * 3600L

        val recentCalls = calls.filter { it.timestamp >= windowStart }
        val distinctChannels = recentCalls.distinctBy { it.channelId }
        val totalMentions = recentCalls.size

        // Confidence based on distinct channels, weighted by trust
        val trustWeighted = distinctChannels.sumOf { it.trust }.toDouble() / (distinctChannels.size * 5.0).coerceAtLeast(1.0)
        val countScore = (distinctChannels.size / 10.0).coerceAtMost(1.0) // cap at 10 channels = max
        val confidence = (trustWeighted * 0.6 + countScore * 0.4).coerceIn(0.0, 1.0)

        return CrowdConfidence(
            address = address,
            distinctChannels = distinctChannels.size,
            totalMentions = totalMentions,
            channelNames = distinctChannels.map { it.channelName },
            windowHours = windowHours,
            confidenceScore = confidence
        )
    }
}
