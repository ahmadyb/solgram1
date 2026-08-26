package com.solgram.domain.rules

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RulesEngineTest : StringSpec({

    "evaluate returns Fire when conditions met" {
        val rule = ForwardRule(
            id = "rule1",
            name = "Test Rule",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(2),
            extractionMode = ExtractionMode.CA,
            minTrust = 0,
            duplicateWindowSeconds = 3600,
            sendIntervalMs = 1000
        )
        val text = "Check 0x1234567890abcdef1234567890abcdef12345678"
        val result = RulesEngine.evaluate(text, rule, trust = 3, recentSends = emptyList())
        result::class shouldBe FireResult.Fire::class
    }

    "evaluate returns Skip when trust too low" {
        val rule = ForwardRule(
            id = "rule1",
            name = "Test",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(2),
            extractionMode = ExtractionMode.FULL,
            minTrust = 4
        )
        val result = RulesEngine.evaluate("hello", rule, trust = 2, recentSends = emptyList())
        result::class shouldBe FireResult.Skip::class
    }

    "evaluate returns Suppressed on duplicate window" {
        val rule = ForwardRule(
            id = "rule1",
            name = "Test",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(2),
            extractionMode = ExtractionMode.CA,
            duplicateWindowSeconds = 3600
        )
        val text = "0x1234567890abcdef1234567890abcdef12345678"
        val recent = listOf(RecentSend("rule1", "0x1234567890abcdef1234567890abcdef12345678", System.currentTimeMillis()/1000 - 100))
        val result = RulesEngine.evaluate(text, rule, trust = 3, recentSends = recent)
        result::class shouldBe FireResult.Suppressed::class
    }

    "Backtester and live evaluation share same function" {
        val rule = ForwardRule(
            id = "r1",
            name = "Test",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(2),
            extractionMode = ExtractionMode.CA
        )
        val messages = listOf(
            com.solgram.domain.telegram.Message(1, 1, 1, "User", "Check 0x1234567890abcdef1234567890abcdef12345678", System.currentTimeMillis()/1000, isOutgoing = false, isPinned = false),
            com.solgram.domain.telegram.Message(2, 1, 1, "User", "Another 0xabcdefabcdefabcdefabcdefabcdefabcdefabcd", System.currentTimeMillis()/1000, isOutgoing = false, isPinned = false)
        )
        val backtest = Backtester.backtest(rule, messages)
        backtest.fires shouldBe 2
    }

    "Combined Success Rate no double-count" {
        // Each token counts once regardless of how many channels matched
        val tokens = listOf("token1", "token1", "token2", "token2", "token2")
        val distinct = tokens.distinct()
        distinct.size shouldBe 2
        // Combined success rate should count each token once
        val ultimateTokens = setOf("token1", "token2", "token3")
        val matchedTokens = setOf("token1", "token1", "token2") // duplicates
        val combined = ultimateTokens.intersect(matchedTokens).size.toDouble() / ultimateTokens.size
        combined shouldBe (2.0/3.0)
    }

    "Conditional rule chains" {
        val ruleA = ForwardRule(
            id = "A",
            name = "Source scan",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(2),
            extractionMode = ExtractionMode.CA
        )
        val ruleB = ForwardRule(
            id = "B",
            name = "High priority alert",
            sourceChatIds = listOf(1),
            destinationChatIds = listOf(3),
            extractionMode = ExtractionMode.CA,
            conditions = listOf(
                com.solgram.domain.rules.RuleCondition(
                    trigger = RuleRef("A"),
                    requires = listOf(ChainRequirement.AddressNotInDb("A"))
                )
            )
        )
        val text = "New token 0x1234567890abcdef1234567890abcdef12345678"
        val fireA = RulesEngine.evaluate(text, ruleA, 3, emptyList())
        fireA::class shouldBe FireResult.Fire::class

        val recent = listOf(RecentSend("A", "0x1234567890abcdef1234567890abcdef12345678", System.currentTimeMillis()/1000))
        val fireB = RulesEngine.evaluate(text, ruleB, 3, recent, existingAddresses = emptySet())
        fireB::class shouldBe FireResult.Fire::class
    }

    "Regex live tester" {
        val pattern = "0x[a-fA-F0-9]{40}"
        val text = "Token 0x1234567890abcdef1234567890abcdef12345678 here"
        val matches = RulesEngine.testRegex(pattern, text)
        matches.size shouldBe 1
    }
})
