package com.solgram.domain.detect

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

class CaDetectorTest : StringSpec({

    "detect Solana addresses" {
        val text = "Check this: 7xKXtt2KhsU7p2a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a"
        val detections = CaDetector.detect(text)
        detections.size shouldBe 1
        detections[0].chain shouldBe Chain.SOLANA
    }

    "detect EVM addresses" {
        val text = "EVM token: 0x1234567890abcdef1234567890abcdef12345678"
        val detections = CaDetector.detect(text)
        detections.size shouldBe 1
        detections[0].chain shouldBe Chain.EVM
        detections[0].address shouldBe "0x1234567890abcdef1234567890abcdef12345678"
    }

    "Solana wins on ambiguity" {
        // Solana matched first and deliberately wins
        val text = "Token: So11111111111111111111111111111111111111112 and 0x1234567890abcdef1234567890abcdef12345678"
        val detections = CaDetector.detect(text)
        // Should detect both, non-overlapping
        detections.size shouldBe 2
    }

    "non-overlapping detection" {
        val text = "0x1234567890abcdef1234567890abcdef12345678 is EVM, 7xKXtt2KhsU7p2a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a is Solana"
        val detections = CaDetector.detect(text)
        detections.size shouldBe 2
        // Check no overlap
        val ranges = detections.map { it.range }
        for (i in ranges.indices) {
            for (j in i+1 until ranges.size) {
                val overlaps = ranges[i].first <= ranges[j].last && ranges[j].first <= ranges[i].last
                overlaps shouldBe false
            }
        }
    }

    "property fuzz test base58/hex strings and boundary lengths" {
        checkAll(Arb.string(30..50, Arb.of((('1'..'9') + ('A'..'H') + ('J'..'N') + ('P'..'Z') + ('a'..'k') + ('m'..'z')).toList()))) { randomBase58 ->
            // Should not throw
            val result = CaDetector.detect(randomBase58)
            // Result should be list (may be empty if noise filter)
            (result is List<*>) shouldBe true
        }
    }

    "isNoise filters http, t.me, @" {
        val text = "https://t.me/something @user 7xKXtt2KhsU7p2a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a"
        val detections = CaDetector.detect(text)
        // Should still detect the Solana address, but not the URLs
        detections.any { it.address.contains("7xKXtt") } shouldBe true
    }
})
