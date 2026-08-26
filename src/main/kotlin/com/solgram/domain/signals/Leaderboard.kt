package com.solgram.domain.signals

import com.solgram.domain.price.CallPerformance

data class LeaderboardEntry(
    val channelId: Long,
    val channelName: String,
    val totalCalls: Int,
    val avgAthMultiple: Double,
    val hitRate2x: Double,
    val hitRate5x: Double,
    val hitRate10x: Double,
    val bestCallMultiple: Double,
    val bestCallAddress: String
)

enum class LeaderboardWindow { D7, D30, ALL }

class LeaderboardEngine {

    fun buildLeaderboard(
        performancesByChannel: Map<Long, List<CallPerformance>>,
        channelNames: Map<Long, String>,
        window: LeaderboardWindow
    ): List<LeaderboardEntry> {
        val now = System.currentTimeMillis()/1000
        val windowStart = when (window) {
            LeaderboardWindow.D7 -> now - 7 * 86400L
            LeaderboardWindow.D30 -> now - 30 * 86400L
            LeaderboardWindow.ALL -> 0L
        }

        return performancesByChannel.mapNotNull { (channelId, perfs) ->
            val filtered = if (window == LeaderboardWindow.ALL) perfs else perfs.filter { it.firstCallAt >= windowStart }
            if (filtered.isEmpty()) return@mapNotNull null

            val avgMultiple = filtered.mapNotNull { it.athMultiple }.average().takeIf { !it.isNaN() } ?: 0.0
            val hit2x = filtered.count { it.hit2x }.toDouble() / filtered.size
            val hit5x = filtered.count { it.hit5x }.toDouble() / filtered.size
            val hit10x = filtered.count { it.hit10x }.toDouble() / filtered.size
            val best = filtered.maxByOrNull { it.athMultiple ?: 0.0 }

            LeaderboardEntry(
                channelId = channelId,
                channelName = channelNames[channelId] ?: "Channel $channelId",
                totalCalls = filtered.size,
                avgAthMultiple = avgMultiple,
                hitRate2x = hit2x,
                hitRate5x = hit5x,
                hitRate10x = hit10x,
                bestCallMultiple = best?.athMultiple ?: 0.0,
                bestCallAddress = best?.address ?: ""
            )
        }.sortedWith(
            compareByDescending<LeaderboardEntry> { it.avgAthMultiple }
                .thenByDescending { it.hitRate2x }
                .thenByDescending { it.totalCalls }
        )
    }
}
