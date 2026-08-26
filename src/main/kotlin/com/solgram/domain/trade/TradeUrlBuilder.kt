package com.solgram.domain.trade

import com.solgram.domain.detect.Chain

object TradeUrlBuilder {

    fun buildUrls(chain: Chain, address: String): List<TradePlatform> {
        return when (chain) {
            Chain.SOLANA -> solanaUrls(address)
            Chain.EVM, Chain.BSC, Chain.BASE, Chain.ARBITRUM, Chain.POLYGON, Chain.AVAX -> evmUrls(chain, address)
        }
    }

    private fun solanaUrls(address: String): List<TradePlatform> = listOf(
        TradePlatform("Jupiter", "https://jup.ag/swap/SOL-$address", "jupiter"),
        TradePlatform("Photon", "https://photon-sol.tinyastro.io/en/lp/$address", "photon"),
        TradePlatform("BullX", "https://bullx.io/terminal?chainId=1399811149&address=$address", "bullx"),
        TradePlatform("DexScreener", "https://dexscreener.com/solana/$address", "dexscreener"),
        TradePlatform("Birdeye", "https://birdeye.so/token/$address?chain=solana", "birdeye"),
        TradePlatform("Raydium", "https://raydium.io/swap/?inputCurrency=sol&outputCurrency=$address&fixed=in", "raydium"),
        TradePlatform("GMGN", "https://gmgn.ai/sol/token/${address}", "gmgn", chainSlug = "sol"),
        TradePlatform("GeckoTerminal", "https://www.geckoterminal.com/solana/pools/$address", "gecko", chainSlug = "sol")
    )

    private fun evmUrls(chain: Chain, address: String): List<TradePlatform> {
        val (gmgnSlug, geckoSlug, chainName) = when (chain) {
            Chain.BSC -> Triple("bsc", "bsc", "BSC")
            Chain.BASE -> Triple("base", "base", "Base")
            Chain.ARBITRUM -> Triple("arb", "arbitrum", "Arbitrum")
            Chain.POLYGON -> Triple("polygon", "polygon", "Polygon")
            Chain.AVAX -> Triple("avax", "avax", "Avax")
            else -> Triple("eth", "eth", "ETH")
        }
        return listOf(
            TradePlatform("Uniswap", "https://app.uniswap.org/#/swap?outputCurrency=$address&chain=${chainName.lowercase()}", "uniswap"),
            TradePlatform("PancakeSwap", "https://pancakeswap.finance/swap?outputCurrency=$address", "pancake"),
            TradePlatform("DEXTools", "https://www.dextools.io/app/en/${chainName.lowercase()}/pair-explorer/$address", "dextools"),
            TradePlatform("DexScreener", "https://dexscreener.com/${chainName.lowercase()}/$address", "dexscreener"),
            TradePlatform("BullX", "https://bullx.io/terminal?chainId=${chainIdFor(chain)}&address=$address", "bullx"),
            TradePlatform("Maestro", "https://maestrobots.co/", "maestro"),
            TradePlatform("GMGN", "https://gmgn.ai/$gmgnSlug/token/$address", "gmgn", chainSlug = gmgnSlug),
            TradePlatform("GeckoTerminal", "https://www.geckoterminal.com/$geckoSlug/pools/$address", "gecko", chainSlug = geckoSlug)
        )
    }

    private fun chainIdFor(chain: Chain): Int = when (chain) {
        Chain.EVM -> 1
        Chain.BSC -> 56
        Chain.BASE -> 8453
        Chain.ARBITRUM -> 42161
        Chain.POLYGON -> 137
        Chain.AVAX -> 43114
        else -> 1
    }

    fun openUrl(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("Only http/https URLs allowed")
        }
        try {
            val desktop = java.awt.Desktop.getDesktop()
            desktop.browse(java.net.URI(url))
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

data class TradePlatform(
    val name: String,
    val url: String,
    val id: String,
    val chainSlug: String? = null
)
