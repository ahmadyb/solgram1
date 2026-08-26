package com.solgram.domain.signals

import com.solgram.domain.price.CallPerformance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChannelTrust(
    val channelId: Long,
    val channelName: String,
    val currentTrust: Int, // 1-5 stars
    val suggestedTrust: Int? = null,
    val suggestionReason: String? = null,
    val lastManualSetAt: Long = 0,
    val totalCalls: Int = 0,
    val hitRate2x: Double = 0.0,
    val avgAthMultiple: Double = 0.0
)

data class TrustSuggestion(
    val channelId: Long,
    val was: Int,
    val suggested: Int,
    val reason: String,
    val windowDays: Int = 30
)

class ReputationEngine {
    private val _trustMap = MutableStateFlow<Map<Long, ChannelTrust>>(emptyMap())
    val trustMap: StateFlow<Map<Long, ChannelTrust>> = _trustMap.asStateFlow()

    private val _suggestions = MutableStateFlow<List<TrustSuggestion>>(emptyList())
    val suggestions: StateFlow<List<TrustSuggestion>> = _suggestions.asStateFlow()

    fun getTrust(channelId: Long): Int {
        return _trustMap.value[channelId]?.currentTrust ?: 3
    }

    fun setTrust(channelId: Long, trust: Int, channelName: String = "Unknown") {
        val current = _trustMap.value.toMutableMap()
        val existing = current[channelId]
        current[channelId] = ChannelTrust(
            channelId = channelId,
            channelName = channelName,
            currentTrust = trust.coerceIn(1, 5),
            suggestedTrust = existing?.suggestedTrust,
            suggestionReason = existing?.suggestionReason,
            lastManualSetAt = System.currentTimeMillis()/1000,
            totalCalls = existing?.totalCalls ?: 0,
            hitRate2x = existing?.hitRate2x ?: 0.0,
            avgAthMultiple = existing?.avgAthMultiple ?: 0.0
        )
        _trustMap.value = current
        // Clear suggestion if recently manually set
        if (existing?.suggestedTrust != null) {
            _suggestions.value = _suggestions.value.filter { it.channelId != channelId }
        }
    }

    /**
     * Computes suggested trust adjustment from realized call-performance over rolling window
     * Suggestions appear as hint, nothing changes automatically
     */
    fun computeSuggestions(
        performancesByChannel: Map<Long, List<CallPerformance>>,
        windowDays: Int = 30
    ): List<TrustSuggestion> {
        val now = System.currentTimeMillis()/1000
        val windowStart = now - windowDays * 86400L
        val suggestions = mutableListOf<TrustSuggestion>()

        for ((channelId, perfs) in performancesByChannel) {
            val recent = perfs.filter { it.firstCallAt >= windowStart }
            if (recent.size < 5) continue // Need minimum data

            val hitRate = recent.count { it.hit2x }.toDouble() / recent.size
            val avgMultiple = recent.mapNotNull { it.athMultiple }.average().takeIf { !it.isNaN() } ?: 0.0

            val currentTrust = getTrust(channelId)
            val suggested = when {
                hitRate >= 0.4 && avgMultiple >= 3.0 -> (currentTrust + 1).coerceAtMost(5)
                hitRate <= 0.1 && avgMultiple < 1.2 && recent.size >= 10 -> (currentTrust - 1).coerceAtLeast(1)
                else -> currentTrust
            }

            if (suggested != currentTrust) {
                val reason = "Last $windowDays days: ${(hitRate*100).toInt()}% hit 2x, avg ${"%.2f".format(avgMultiple)}x ATH, ${recent.size} calls"
                suggestions.add(TrustSuggestion(channelId, currentTrust, suggested, reason, windowDays))
            }

            // Update trust map with stats
            val currentMap = _trustMap.value.toMutableMap()
            val existing = currentMap[channelId]
            if (existing != null) {
                currentMap[channelId] = existing.copy(
                    suggestedTrust = if (suggested != currentTrust) suggested else null,
                    suggestionReason = if (suggested != currentTrust) "suggested: $suggested★ (was $currentTrust★) - $reason" else null,
                    totalCalls = recent.size,
                    hitRate2x = hitRate,
                    avgAthMultiple = avgMultiple
                )
            } else {
                currentMap[channelId] = ChannelTrust(
                    channelId = channelId,
                    channelName = "Channel $channelId",
                    currentTrust = currentTrust,
                    suggestedTrust = if (suggested != currentTrust) suggested else null,
                    suggestionReason = if (suggested != currentTrust) "suggested: $suggested★ (was $currentTrust★)" else null,
                    totalCalls = recent.size,
                    hitRate2x = hitRate,
                    avgAthMultiple = avgMultiple
                )
            }
            _trustMap.value = currentMap
        }

        _suggestions.value = suggestions
        return suggestions
    }

    fun applySuggestion(channelId: Long) {
        val suggestion = _suggestions.value.find { it.channelId == channelId } ?: return
        setTrust(channelId, suggestion.suggested, _trustMap.value[channelId]?.channelName ?: "Unknown")
        _suggestions.value = _suggestions.value.filter { it.channelId != channelId }
    }

    fun dismissSuggestion(channelId: Long) {
        _suggestions.value = _suggestions.value.filter { it.channelId != channelId }
        val current = _trustMap.value.toMutableMap()
        current[channelId]?.let {
            current[channelId] = it.copy(suggestedTrust = null, suggestionReason = null)
        }
        _trustMap.value = current
    }
}
