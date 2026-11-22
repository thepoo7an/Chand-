package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.TomanRateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceMappingTest {

    @Test
    fun `computeUsdtMedianToman rejects outlier and filters invalid values`() {
        val median = MarketPriceMapper.computeUsdtMedianToman(
            wallexUsdtToman = 70_200.0,
            bitpinUsdtToman = 70_350.0,
            nobitexUsdtToman = 99_000.0
        )
        assertEquals(70_350.0, median ?: 0.0, 0.01)

        val avgTwo = MarketPriceMapper.computeUsdtMedianToman(
            wallexUsdtToman = 70_000.0,
            bitpinUsdtToman = 70_500.0,
            nobitexUsdtToman = null
        )
        assertEquals(70_250.0, avgTwo ?: 0.0, 0.01)

        val invalid = MarketPriceMapper.computeUsdtMedianToman(0.0, 500.0, null)
        assertNull(invalid)
    }

    @Test
    fun `resolveFreeMarketUsd prioritizes Tehran free market over USDT median and cached anchor`() {
        val tgjuQuote = MarketPriceMapper.TgjuQuote(
            priceToman = 71_400.0,
            openToman = 70_900.0,
            highToman = 71_600.0,
            lowToman = 70_800.0,
            changeToman = 500.0,
            changePct = 0.71
        )
        val wallexUsdt = MarketPriceMapper.ExchangeQuote(priceToman = 71_900.0, priceUsd = 1.0, changePct = 0.85)
        val bitpinUsdt = MarketPriceMapper.ExchangeQuote(priceToman = 72_100.0, priceUsd = 1.0, changePct = 0.90)

        val tier1 = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = tgjuQuote,
            wallexUsdt = wallexUsdt,
            bitpinUsdt = bitpinUsdt,
            nobitexUsdtToman = 72_000.0,
            usdtSpark = listOf(71_500.0, 72_000.0),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = 69_000.0
        )
        assertEquals(71_400.0, tier1.usdToman, 0.01)
        assertEquals(72_000.0, tier1.usdtToman, 0.01)
        assertEquals(TomanRateBasis.TEHRAN_FREE_MARKET, tier1.rateBasis)

        val tier2 = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = wallexUsdt,
            bitpinUsdt = bitpinUsdt,
            nobitexUsdtToman = 72_000.0,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = 69_000.0
        )
        assertEquals(72_000.0, tier2.usdToman, 0.01)
        assertEquals(TomanRateBasis.USDT_FREE_MARKET_MEDIAN, tier2.rateBasis)

        val tier3 = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = null,
            bitpinUsdt = null,
            nobitexUsdtToman = null,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = 69_000.0
        )
        assertEquals(69_000.0, tier3.usdToman, 0.01)
        assertEquals(TomanRateBasis.CACHED_USD_ANCHOR, tier3.rateBasis)

        val tier4 = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = null,
            bitpinUsdt = null,
            nobitexUsdtToman = null,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = null
        )
        assertEquals(MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN, tier4.usdToman, 0.01)
        assertEquals(TomanRateBasis.OFFLINE_REFERENCE, tier4.rateBasis)
    }

    @Test
    fun `mapFiatAsset converts FX cross rates and normalizes 100-unit JPY and 1000-unit IQD`() {
        val usdToman = 70_000.0
        val eurSpec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "EUR" }

        val crossEur = MarketPriceMapper.mapFiatAsset(
            spec = eurSpec,
            usdToman = usdToman,
            usdChangePct = 1.2,
            tgjuQuote = null,
            fxPerUsd = 0.92,
            usdtSpark = listOf(69_000.0, 69_500.0, 69_800.0, 70_000.0),
            nowMillis = 123_456L
        )
        assertNotNull(crossEur)
        assertEquals(70_000.0 / 0.92, crossEur!!.priceToman, 0.5)
        assertEquals("FX + USD", crossEur.sourceLabel)
        assertEquals(AssetCategory.FIAT, crossEur.category)
        assertNull(crossEur.high24hToman)
        assertNull(crossEur.low24hToman)

        val jpySpec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "JPY" }
        val normalizedJpy = MarketPriceMapper.mapFiatAsset(
            spec = jpySpec,
            usdToman = usdToman,
            usdChangePct = 0.5,
            tgjuQuote = MarketPriceMapper.TgjuQuote(
                priceToman = 46_600.0,
                openToman = 46_000.0,
                highToman = 46_800.0,
                lowToman = 45_900.0,
                changeToman = 600.0,
                changePct = 1.3
            ),
            fxPerUsd = 150.0,
            usdtSpark = emptyList(),
            nowMillis = 123_456L
        )
        assertNotNull(normalizedJpy)
        assertEquals(466.0, normalizedJpy!!.priceToman, 0.1)
        assertEquals(468.0, normalizedJpy.high24hToman ?: 0.0, 0.1)
        assertEquals(459.0, normalizedJpy.low24hToman ?: 0.0, 0.1)

        val iqdSpec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "IQD" }
        val normalizedIqd = MarketPriceMapper.mapFiatAsset(
            spec = iqdSpec,
            usdToman = usdToman,
            usdChangePct = 0.0,
            tgjuQuote = MarketPriceMapper.TgjuQuote(
                priceToman = 53_400.0,
                openToman = 53_400.0,
                highToman = 53_500.0,
                lowToman = 53_300.0,
                changeToman = 0.0,
                changePct = 0.0
            ),
            fxPerUsd = 1310.0,
            usdtSpark = emptyList(),
            nowMillis = 123_456L
        )
        assertNotNull(normalizedIqd)
        assertEquals(53.4, normalizedIqd!!.priceToman, 0.1)
    }

    @Test
    fun `mapCryptoAsset multiplies Binance USD by USDT Toman when local TMN markets are absent`() {
        val btcSpec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "BTC" }
        val asset = MarketPriceMapper.mapCryptoAsset(
            spec = btcSpec,
            usdtToman = 72_000.0,
            usdtChangePct = 0.5,
            wallex = null,
            bitpin = null,
            binance = MarketPriceMapper.ExchangeQuote(priceToman = null, priceUsd = 90_000.0, changePct = 2.5),
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = 999L
        )
        assertNotNull(asset)
        assertEquals(90_000.0 * 72_000.0, asset!!.priceToman, 1.0)
        assertEquals(90_000.0, asset.priceUsd ?: 0.0, 0.01)
        assertEquals("Binance", asset.sourceLabel)
        assertEquals(AssetCategory.CRYPTO, asset.category)
        assertNull(asset.high24hToman)
        assertNull(asset.low24hToman)
    }

    @Test
    fun `TomanRateBasis localizes summary text cleanly for Persian UI`() {
        val en = "Free Market (Tehran) • TGJU • Wallex"
        val fa = TomanRateBasis.localizeSummary(en, isFa = true)
        assertTrue(fa.contains("نرخ آزاد تهران"))
        assertTrue(fa.contains("TGJU"))
        assertEquals(en, TomanRateBasis.localizeSummary(en, isFa = false))
    }

    @Test
    fun `mapFiatAsset uses currency-specific sparkline when available instead of cloning USDT curve`() {
        val gbpSpec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "GBP" }
        val usdtRising = listOf(69_000.0, 70_000.0, 71_000.0, 72_000.0)
        val gbpFalling = listOf(92_000.0, 91_000.0, 90_000.0, 88_000.0)

        val asset = MarketPriceMapper.mapFiatAsset(
            spec = gbpSpec,
            usdToman = 72_000.0,
            usdChangePct = 1.0,
            tgjuQuote = null,
            fxPerUsd = 0.80,
            usdtSpark = usdtRising,
            nowMillis = 123_456L,
            currencySpark = gbpFalling
        )
        assertNotNull(asset)
        assertTrue(
            "Expected GBP sparkline to reflect its own downward trajectory rather than USDT rising curve",
            asset!!.sparkline.first() > asset.sparkline.last()
        )
        assertEquals(asset.priceToman, asset.sparkline.last(), 0.5)
    }
}
