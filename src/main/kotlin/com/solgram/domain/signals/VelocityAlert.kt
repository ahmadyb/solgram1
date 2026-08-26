package com.solgram.domain.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VelocityThreshold(
    val minDistinctCallers: Int = 3,
    val withinMinutes: Int = 90,
    val enabled: Boolean = true
)

data class VelocityAlert(
    val address: String,
    val chain: String,
    val distinctCallers: Int,
    val callerNames: List<String>,
    val windowMinutes: Int,
    val firstCallAt: Long,
    val lastCallAt: Long,
    val detectedAt: Long
)

class VelocityAlertEngine {
    private var threshold = VelocityThreshold()
    private val _alerts = MutableStateFlow<List<VelocityAlert>>(emptyList())
    val alerts: StateFlow<List<VelocityAlert>> = _alerts.asStateFlow()

    fun setThreshold(threshold: VelocityThreshold) {
        this.threshold = threshold
    }

    fun getThreshold(): VelocityThreshold = threshold

    /**
     * Acceleration matters differently than raw count
     * 3 channels in 90 seconds != 3 channels over 3 days
     */
    fun evaluate(
        address: String,
        chain: String,
        calls: List<CallerCall>
    ): VelocityAlert? {
        if (!threshold.enabled) return null
        if (calls.size < threshold.minDistinctCallers) return null

        val now = System.currentTimeMillis()/1000
        val windowStart = now - threshold.withinMinutes * 60L

        // Distinct callers within window
        val recentCalls = calls.filter { it.timestamp >= windowStart }
        val distinctCallers = recentCalls.distinctBy { it.channelId }

        if (distinctCallers.size >= threshold.minDistinctCallers) {
            val alert = VelocityAlert(
                address = address,
                chain = chain,
                distinctCallers = distinctCallers.size,
                callerNames = distinctCallers.map { it.channelName },
                windowMinutes = threshold.withinMinutes,
                firstCallAt = distinctCallers.minByOrNull { it.timestamp }?.timestamp ?: now,
                lastCallAt = distinctCallers.maxByOrNull { it.timestamp }?.timestamp ?: now,
                detectedAt = now
            )
            // Add to alerts if not already present
            val current = _alerts.value.toMutableList()
            if (current.none { it.address == address && it.detectedAt > now - 3600 }) {
                current.add(0, alert)
                if (current.size > 100) current.removeAt(current.lastIndex)
                _alerts.value = current
            }
            return alert
        }
        return null
    }
}

data class CallerCall(
    val channelId: Long,
    val channelName: String,
    val timestamp: Long,
    val trust: Int
)
