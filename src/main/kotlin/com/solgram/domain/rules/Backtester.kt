package com.solgram.domain.rules

import com.solgram.domain.telegram.Message

data class BacktestResult(
    val rule: ForwardRule,
    val totalMessages: Int,
    val fires: Int,
    val suppressions: Int,
    val skips: Int,
    val details: List<FireResult>
)

object Backtester {
    /**
     * Uses the SAME RulesEngine.evaluate() as live processing - one implementation
     */
    fun backtest(
        rule: ForwardRule,
        messages: List<Message>,
        trustMap: Map<Long, Int> = emptyMap(),
        existingAddresses: Set<String> = emptySet()
    ): BacktestResult {
        val recentSends = mutableListOf<RecentSend>()
        val details = mutableListOf<FireResult>()

        for (msg in messages) {
            val trust = trustMap[msg.chatId] ?: 0
            val result = RulesEngine.evaluate(
                messageText = msg.text,
                rule = rule,
                trust = trust,
                recentSends = recentSends,
                existingAddresses = existingAddresses,
                now = msg.date
            )
            details.add(result)
            if (result is FireResult.Fire) {
                result.detections.forEach { det ->
                    recentSends.add(RecentSend(rule.id, det.address, msg.date))
                }
                if (result.detections.isEmpty()) {
                    recentSends.add(RecentSend(rule.id, null, msg.date))
                }
            }
        }

        return BacktestResult(
            rule = rule,
            totalMessages = messages.size,
            fires = details.count { it is FireResult.Fire },
            suppressions = details.count { it is FireResult.Suppressed },
            skips = details.count { it is FireResult.Skip },
            details = details
        )
    }
}
