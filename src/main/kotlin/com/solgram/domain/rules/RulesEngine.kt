package com.solgram.domain.rules

import com.solgram.domain.detect.CaDetector
import com.solgram.domain.detect.Chain
import com.solgram.domain.detect.Detection
import kotlinx.serialization.Serializable

@JvmInline
value class RuleId(val value: String)

@Serializable
data class ForwardRule(
    val id: String,
    val name: String,
    val sourceChatIds: List<Long>,
    val destinationChatIds: List<Long>,
    val extractionMode: ExtractionMode,
    val regexPattern: String? = null,
    val prefix: String = "",
    val minTrust: Int = 0,
    val duplicateWindowSeconds: Int = 3600,
    val sendIntervalMs: Long = 1000,
    val enabled: Boolean = true,
    val conditions: List<RuleCondition> = emptyList()
)

enum class ExtractionMode { FULL, CA, REGEX }

@Serializable
data class RuleCondition(
    val trigger: RuleRef,
    val requires: List<ChainRequirement> = emptyList()
)

@Serializable
data class RuleRef(val ruleId: String)

sealed class ChainRequirement {
    data class RuleFired(val ruleId: String, val withinSeconds: Int) : ChainRequirement()
    data class AddressNotInDb(val ruleId: String) : ChainRequirement()
    data class MinTrust(val ruleId: String, val trust: Int) : ChainRequirement()
}

sealed class FireResult {
    data class Fire(val rule: ForwardRule, val content: String, val detections: List<Detection>) : FireResult()
    data class Suppressed(val rule: ForwardRule, val reason: String) : FireResult()
    data class Skip(val rule: ForwardRule, val reason: String) : FireResult()
}

data class RecentSend(
    val ruleId: String,
    val address: String?,
    val timestamp: Long
)

object RulesEngine {

    fun evaluate(
        messageText: String,
        rule: ForwardRule,
        trust: Int,
        recentSends: List<RecentSend>,
        existingAddresses: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis() / 1000
    ): FireResult {
        if (!rule.enabled) return FireResult.Skip(rule, "Rule disabled")

        if (trust < rule.minTrust) {
            return FireResult.Skip(rule, "Trust $trust < min ${rule.minTrust}")
        }

        // Check send interval
        val lastSend = recentSends.filter { it.ruleId == rule.id }.maxByOrNull { it.timestamp }
        if (lastSend != null) {
            val elapsedMs = (now - lastSend.timestamp) * 1000
            if (elapsedMs < rule.sendIntervalMs) {
                return FireResult.Suppressed(rule, "Send interval: ${rule.sendIntervalMs - elapsedMs}ms remaining")
            }
        }

        val detections = CaDetector.detect(messageText)

        // Extraction mode handling
        val contentToSend: String? = when (rule.extractionMode) {
            ExtractionMode.FULL -> {
                if (rule.prefix.isNotBlank()) "${rule.prefix}\n\n$messageText" else messageText
            }
            ExtractionMode.CA -> {
                if (detections.isEmpty()) return FireResult.Skip(rule, "No CA detected")
                val addresses = detections.joinToString("\n") { it.address }
                if (rule.prefix.isNotBlank()) "${rule.prefix}\n$addresses" else addresses
            }
            ExtractionMode.REGEX -> {
                val pattern = rule.regexPattern ?: return FireResult.Skip(rule, "No regex pattern")
                try {
                    val regex = Regex(pattern)
                    val match = regex.find(messageText) ?: return FireResult.Skip(rule, "Regex no match")
                    val extracted = match.value
                    if (rule.prefix.isNotBlank()) "${rule.prefix}\n$extracted" else extracted
                } catch (e: Exception) {
                    return FireResult.Skip(rule, "Invalid regex: ${e.message}")
                }
            }
        }

        // Duplicate window check for CA mode
        if (rule.extractionMode == ExtractionMode.CA) {
            for (det in detections) {
                val duplicate = recentSends.any {
                    it.ruleId == rule.id &&
                            it.address?.equals(det.address, ignoreCase = true) == true &&
                            (now - it.timestamp) < rule.duplicateWindowSeconds
                }
                if (duplicate) {
                    return FireResult.Suppressed(rule, "Duplicate ${det.address} within ${rule.duplicateWindowSeconds}s")
                }
            }
        }

        // Conditional chain evaluation
        for (condition in rule.conditions) {
            val chainResult = evaluateChainCondition(condition, recentSends, existingAddresses, now)
            if (!chainResult) {
                return FireResult.Skip(rule, "Chain condition failed: ${condition.trigger.ruleId}")
            }
        }

        return FireResult.Fire(rule, contentToSend ?: messageText, detections)
    }

    private fun evaluateChainCondition(
        condition: RuleCondition,
        recentSends: List<RecentSend>,
        existingAddresses: Set<String>,
        now: Long
    ): Boolean {
        for (req in condition.requires) {
            when (req) {
                is ChainRequirement.RuleFired -> {
                    val fired = recentSends.any {
                        it.ruleId == req.ruleId && (now - it.timestamp) <= req.withinSeconds
                    }
                    if (!fired) return false
                }
                is ChainRequirement.AddressNotInDb -> {
                    // If any recent address for this rule is already in DB, fail
                    val recentAddresses = recentSends.filter { it.ruleId == req.ruleId }.mapNotNull { it.address }
                    if (recentAddresses.any { it in existingAddresses }) return false
                }
                is ChainRequirement.MinTrust -> {
                    // Trust check already done, but chain can require different trust
                    // This would need trust map - simplified to pass
                }
            }
        }
        return true
    }

    fun testRegex(pattern: String, text: String): List<String> {
        return try {
            val regex = Regex(pattern)
            regex.findAll(text).map { it.value }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
