package com.solgram.domain.rules

/**
 * Conditional rule chains - thin layer on top of RulesEngine
 * Example: If Rule A fires AND address not in DB, then fire Rule B
 */
class RuleChainEvaluator(
    private val rules: List<ForwardRule>
) {
    fun evaluateChains(
        initialFires: List<FireResult.Fire>,
        recentSends: List<RecentSend>,
        existingAddresses: Set<String>,
        now: Long = System.currentTimeMillis() / 1000
    ): List<FireResult> {
        val results = mutableListOf<FireResult>()
        results.addAll(initialFires)

        val firedRuleIds = initialFires.map { it.rule.id }.toSet()

        // For each rule that has conditions, check if its trigger fired
        for (rule in rules) {
            if (rule.conditions.isEmpty()) continue
            for (condition in rule.conditions) {
                if (condition.trigger.ruleId in firedRuleIds) {
                    // Evaluate this rule now with chain context
                    val dummyMessage = initialFires.find { it.rule.id == condition.trigger.ruleId }?.content ?: ""
                    val eval = RulesEngine.evaluate(
                        messageText = dummyMessage,
                        rule = rule,
                        trust = 5,
                        recentSends = recentSends,
                        existingAddresses = existingAddresses,
                        now = now
                    )
                    if (eval is FireResult.Fire) {
                        results.add(eval)
                    }
                }
            }
        }

        return results
    }
}
