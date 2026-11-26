package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.network.FinancialApiService
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.network.TomanRateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage2MarketReliabilityTest {

    // -------------------------------------------------------------------------
    // A. Characterization Tests for Extracted Parsers & History Parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parseTgjuQuotesPayload converts rials to tomans, preserves nullable high low, and applies dt sign`() {
        val now = 1_741_000_000_000L
        val payload = """
            {
              "current": {
                "price_dollar_rl": {
                  "p": "715,000",
                  "o": "710,000",
                  "h": "718,000",
                  "l": "709,000",
                  "d": "5,000",
                  "dp": "0.70",
                  "dt": "high",
                  "ts": "2025-03-03 15:30:00"
                },
                "price_eur": {
                  "p": "770,000",
                  "d": "2,000",
                  "dp": "0.26",
                  "dt": "low"
                }
              }
            }
        """.trimIndent()

        val parsed = MarketRemoteDataSource.parseTgjuQuotesPayload(payload, nowMillis = now)
        val usd = parsed["price_dollar_rl"]
        assertNotNull(usd)
        assertEquals(71_500.0, usd!!.priceToman, 0.01)
        assertEquals(71_800.0, usd.highToman ?: 0.0, 0.01)
        assertEquals(70_900.0, usd.lowToman ?: 0.0, 0.01)
        assertEquals(500.0, usd.changeToman, 0.01)
        assertEquals(0.70, usd.changePct, 0.01)

        val eur = parsed["price_eur"]
        assertNotNull(eur)
        assertEquals(77_000.0, eur!!.priceToman, 0.01)
        // Must remain null rather than fabricating price*1.01 when 'h'/'l' absent
        assertNull(eur.highToman)
        assertNull(eur.lowToman)
        assertEquals(-200.0, eur.changeToman, 0.01)
        assertEquals(-0.26, eur.changePct, 0.01)
        // Missing source timestamp must remain unknown (0L), never fabricated as nowMillis
        assertEquals(0L, eur.timestampMillis)
    }

    @Test
    fun `parseTgjuTablePayload sorts chronologically, deduplicates timestamps, and normalizes unit scales`() {
        val payload = """
            {
              "data": [
                ["710,000", "708,000", "712,000", "711,000", "", "", "2025/03/02", "1403/12/12"],
                ["700,000", "698,000", "705,000", "702,000", "", "", "2025/03/01", "1403/12/11"],
                ["710,000", "708,000", "712,000", "711,000", "", "", "2025/03/02", "1403/12/12"]
              ]
            }
        """.trimIndent()

        val points = MarketRemoteDataSource.parseTgjuTablePayload(payload, slug = "price_dollar_rl")
        assertEquals(2, points.size)
        assertTrue(points[0].timestampSec < points[1].timestampSec)
        assertEquals(70_200.0, points[0].priceToman, 0.01)
        assertEquals(71_100.0, points[1].priceToman, 0.01)
    }

    @Test
    fun `parseWallex Bitpin Binance and history parsers extract quotes and series cleanly`() {
        val now = 1_741_000_000_000L
        val wallexJson = """
            {
              "result": {
                "markets": {
                  "USDTTMN": {
                    "baseAsset": "USDT",
                    "quoteAsset": "TMN",
                    "price": "71250",
                    "change_24h": 0.65,
                    "stats": { "24h_highPrice": "71600", "24h_lowPrice": "70800" }
                  }
                }
              }
            }
        """.trimIndent()
        val wallex = MarketRemoteDataSource.parseWallexQuotesPayload(wallexJson, now)
        assertEquals(71_250.0, wallex["USDT"]?.priceToman ?: 0.0, 0.01)
        assertEquals(71_600.0, wallex["USDT"]?.highToman ?: 0.0, 0.01)
        assertEquals(70_800.0, wallex["USDT"]?.lowToman ?: 0.0, 0.01)

        val bitpinJson = """
            {
              "results": [
                {
                  "currency1": { "code": "USDT" },
                  "currency2": { "code": "IRT" },
                  "price_info": { "price": "71300", "change": 0.7 }
                }
              ]
            }
        """.trimIndent()
        val bitpin = MarketRemoteDataSource.parseBitpinQuotesPayload(bitpinJson, now)
        assertEquals(71_300.0, bitpin["USDT"]?.priceToman ?: 0.0, 0.01)

        val binanceJson = """
            [
              {
                "symbol": "BTCUSDT",
                "lastPrice": "91000.0",
                "priceChangePercent": "1.8",
                "highPrice": "92000.0",
                "lowPrice": "89500.0",
                "closeTime": ${now - 60_000L}
              }
            ]
        """.trimIndent()
        val binance = MarketRemoteDataSource.parseBinanceQuotesPayload(binanceJson, now)
        assertEquals(91_000.0, binance["BTC"]?.priceUsd ?: 0.0, 0.01)
        assertEquals(now - 60_000L, binance["BTC"]?.timestampMillis)

        val udfJson = """{"s":"ok","t":[100,200],"c":["710000","715000"]}"""
        val nobitexPts = MarketRemoteDataSource.parseNobitexUdfPayload(udfJson)
        assertEquals(2, nobitexPts.size)
        assertEquals(71_000.0, nobitexPts[0].priceToman, 0.01)
    }

    // -------------------------------------------------------------------------
    // B. MarketValidationConfig Defaults & Commonly Cited Coin Specs
    // -------------------------------------------------------------------------

    @Test
    fun `MarketValidationConfig holds single-source documented defaults and commonly cited coin specs`() {
        assertEquals(3.0, MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT, 0.001)
        assertEquals(3.0, MarketValidationConfig.FIAT_DISAGREEMENT_THRESHOLD_PCT, 0.001)
        assertEquals(15.0, MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT, 0.001)
        assertEquals(5L * 60_000L, MarketValidationConfig.CRYPTO_LIVE_MAX_AGE_MILLIS)
        assertEquals(30L * 60_000L, MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS)

        val emami = MarketValidationConfig.COIN_SPECS.getValue("COIN_EMAMI")
        assertEquals(8.133, emami.weightGrams, 0.0001)
        assertEquals(900, emami.finenessPerMille)
        assertEquals(7.3197, emami.pureGoldGrams, 0.0001)

        assertEquals(4.066, MarketValidationConfig.COIN_SPECS.getValue("COIN_HALF").weightGrams, 0.0001)
        assertEquals(2.033, MarketValidationConfig.COIN_SPECS.getValue("COIN_QUARTER").weightGrams, 0.0001)
        assertEquals(1.01, MarketValidationConfig.COIN_SPECS.getValue("COIN_GERAMI").weightGrams, 0.0001)
    }

    // -------------------------------------------------------------------------
    // C. Cross-Source Validation (1 / 2 / 3+ sources, Crypto & Fiat)
    // -------------------------------------------------------------------------

    @Test
    fun `cross-source validation uses median and excludes outliers with 3+ sources`() {
        val now = 1_741_000_000_000L
        val consensus = MarketPriceMapper.validateCrossSourcePrices(
            candidates = listOf(
                MarketPriceMapper.PriceCandidate("Wallex", 71_000.0, isPrimary = true, timestampMillis = now),
                MarketPriceMapper.PriceCandidate("Bitpin", 71_400.0, isPrimary = false, timestampMillis = now - 30_000L),
                MarketPriceMapper.PriceCandidate("Nobitex", 89_500.0, isPrimary = false, timestampMillis = now - 10_000L)
            ),
            thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = now
        )
        assertNotNull(consensus)
        assertEquals(71_400.0, consensus!!.price, 0.01)
        assertFalse(consensus.disputed)
        assertFalse(consensus.singleSource)
        assertEquals(listOf("Wallex", "Bitpin"), consensus.acceptedSources)
        assertEquals(listOf("Nobitex"), consensus.excludedSources)
    }

    @Test
    fun `two disagreeing crypto or fiat sources choose primary without blind averaging and set disputed`() {
        val now = 1_741_000_000_000L
        val btcSpec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "BTC" }
        val btcDisputed = MarketPriceMapper.mapCryptoAsset(
            spec = btcSpec,
            usdtToman = 71_000.0,
            usdtChangePct = 0.0,
            wallex = MarketPriceMapper.ExchangeQuote(priceToman = 6_400_000_000.0, priceUsd = 90_000.0, changePct = 1.0, timestampMillis = now),
            bitpin = MarketPriceMapper.ExchangeQuote(priceToman = 7_100_000_000.0, priceUsd = 90_000.0, changePct = 1.0, timestampMillis = now),
            binance = null,
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now
        )
        assertNotNull(btcDisputed)
        // Never average 6.4B and 7.1B -> 6.75B; must select primary Wallex and mark disputed
        assertEquals(6_400_000_000.0, btcDisputed!!.priceToman, 1.0)
        assertTrue(btcDisputed.disputed)
        assertFalse(btcDisputed.singleSource)
        assertEquals("Wallex", btcDisputed.sourceLabel)

        val eurSpec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "EUR" }
        val eurDisputed = MarketPriceMapper.mapFiatAsset(
            spec = eurSpec,
            usdToman = 70_000.0,
            usdChangePct = 0.0,
            tgjuQuote = MarketPriceMapper.TgjuQuote(
                priceToman = 82_000.0, // >3% spread vs 70000/0.92 = 76086.95
                openToman = null,
                changeToman = 0.0,
                changePct = 0.0,
                timestampMillis = now
            ),
            fxPerUsd = 0.92,
            usdtSpark = emptyList(),
            nowMillis = now
        )
        assertNotNull(eurDisputed)
        assertEquals(82_000.0, eurDisputed!!.priceToman, 0.01)
        assertTrue(eurDisputed.disputed)
        assertFalse(eurDisputed.singleSource)
    }

    @Test
    fun `single source is accepted and marked singleSource`() {
        val now = 1_741_000_000_000L
        val solSpec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "SOL" }
        val sole = MarketPriceMapper.mapCryptoAsset(
            spec = solSpec,
            usdtToman = 70_000.0,
            usdtChangePct = 0.0,
            wallex = MarketPriceMapper.ExchangeQuote(priceToman = 10_500_000.0, priceUsd = 150.0, changePct = 2.0, timestampMillis = now),
            bitpin = null,
            binance = null,
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now
        )
        assertNotNull(sole)
        assertTrue(sole!!.singleSource)
        assertFalse(sole.disputed)
    }

    // -------------------------------------------------------------------------
    // D & E. Freshness States, Stalest-Input Rule & Cached Anchor Non-LIVE
    // -------------------------------------------------------------------------

    @Test
    fun `deriveFreshness enforces 5m crypto and 30m fiat gold cutoffs and stalest input wins`() {
        val now = 1_741_000_000_000L
        assertEquals(
            DataFreshness.LIVE,
            MarketPriceMapper.deriveFreshness(AssetCategory.CRYPTO, true, true, false, now - 4 * 60_000L, now)
        )
        assertEquals(
            DataFreshness.DELAYED,
            MarketPriceMapper.deriveFreshness(AssetCategory.CRYPTO, true, true, false, now - 6 * 60_000L, now)
        )
        assertEquals(
            DataFreshness.LIVE,
            MarketPriceMapper.deriveFreshness(AssetCategory.FIAT, true, true, false, now - 25 * 60_000L, now)
        )
        assertEquals(
            DataFreshness.DELAYED,
            MarketPriceMapper.deriveFreshness(AssetCategory.GOLD, true, true, false, now - 35 * 60_000L, now)
        )

        assertEquals(DataFreshness.DELAYED, DataFreshness.stalest(DataFreshness.LIVE, DataFreshness.DELAYED))
        assertEquals(DataFreshness.CACHED, DataFreshness.stalest(DataFreshness.LIVE, DataFreshness.CACHED))
        assertEquals(DataFreshness.UNAVAILABLE, DataFreshness.stalest(DataFreshness.CACHED, DataFreshness.UNAVAILABLE))
    }

    @Test
    fun `cached USD anchor propagates CACHED freshness to all derived fiat and crypto prices`() {
        val now = 1_741_000_000_000L
        val resolved = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = null,
            bitpinUsdt = null,
            nobitexUsdtToman = null,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = 69_000.0,
            nowMillis = now,
            cachedUsdTimestampMillis = now - 3_600_000L
        )
        assertEquals(TomanRateBasis.CACHED_USD_ANCHOR, resolved.rateBasis)
        assertEquals(DataFreshness.CACHED, resolved.usdFreshness)
        assertEquals(DataFreshness.CACHED, resolved.usdtFreshness)

        val btc = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "BTC" },
            usdtToman = resolved.usdtToman,
            usdtChangePct = resolved.usdtChangePct,
            wallex = null,
            bitpin = null,
            binance = MarketPriceMapper.ExchangeQuote(priceToman = null, priceUsd = 90_000.0, changePct = 1.0, timestampMillis = now),
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now,
            usdtBasisFreshness = resolved.usdtFreshness,
            usdtSourceTimestampMillis = resolved.usdtSourceTimestampMillis
        )
        assertNotNull(btc)
        // Binance quote is LIVE (now), but usdtToman is CACHED -> stalest input wins!
        assertEquals(DataFreshness.CACHED, btc!!.freshness)

        val eur = MarketPriceMapper.mapFiatAsset(
            spec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "EUR" },
            usdToman = resolved.usdToman,
            usdChangePct = resolved.usdChangePct,
            tgjuQuote = null,
            fxPerUsd = 0.92,
            usdtSpark = emptyList(),
            nowMillis = now,
            usdBasisFreshness = resolved.usdFreshness,
            usdSourceTimestampMillis = resolved.usdSourceTimestampMillis
        )
        assertEquals(DataFreshness.CACHED, eur!!.freshness)
    }

    @Test
    fun `offlineReferenceCatalog is never labeled LIVE`() {
        val snap = MarketPriceMapper.offlineReferenceCatalog(1_741_000_000_000L)
        assertEquals(DataFreshness.UNAVAILABLE, snap.freshness)
        assertTrue(snap.assets.all { it.freshness == DataFreshness.UNAVAILABLE })
        assertTrue(snap.assets.all { it.high24hToman == null && it.low24hToman == null })
    }

    // -------------------------------------------------------------------------
    // F & G. Nullable 24h High/Low + Coin Intrinsic Value & Bubble + Missing Quote Fallback
    // -------------------------------------------------------------------------

    @Test
    fun `mapGoldAssets computes coin intrinsic value and bubble from CBI specs without multiplier fallback`() {
        val now = 1_741_000_000_000L
        val usdToman = 70_000.0
        val onsUsd = 2_800.0
        val gram24 = (onsUsd / 31.1035) * usdToman
        val expectedEmamiIntrinsic = (8.133 * 0.900) * gram24
        val emamiMarket = 75_000_000.0

        val cachedHalf = MarketAsset(
            id = "COIN_HALF",
            symbol = "HALF",
            nameFa = "نیم سکه",
            nameEn = "1/2 Azadi",
            category = AssetCategory.GOLD,
            badgeText = "🥇",
            accentHex = 0xFFF59E0B,
            priceToman = 42_000_000.0,
            sourceLabel = "TGJU",
            freshness = DataFreshness.LIVE
        )

        val goldAssets = MarketPriceMapper.mapGoldAssets(
            tgju = mapOf(
                "sekee" to MarketPriceMapper.TgjuQuote(
                    priceToman = emamiMarket,
                    openToman = 74_000_000.0,
                    highToman = null,
                    lowToman = null,
                    changeToman = 1_000_000.0,
                    changePct = 1.35,
                    timestampMillis = now - 60_000L
                )
            ),
            usdToman = usdToman,
            usdChangePct = 0.0,
            usdBasisFreshness = DataFreshness.LIVE,
            usdSourceTimestampMillis = now,
            goldSpotCandidates = listOf(
                MarketPriceMapper.PriceCandidate("GoldAPI", onsUsd, isPrimary = true, timestampMillis = now - 30_000L)
            ),
            paxgQuote = null,
            tgjuGoldRecent = emptyList(),
            goldSpark = emptyList(),
            usdtSpark = emptyList(),
            cachedAssetsById = mapOf("COIN_HALF" to cachedHalf),
            nowMillis = now
        )

        val emami = goldAssets.first { it.id == "COIN_EMAMI" }
        assertEquals(DataFreshness.LIVE, emami.freshness)
        assertEquals(emamiMarket, emami.priceToman, 1.0)
        assertNull(emami.high24hToman)
        assertNull(emami.low24hToman)
        assertEquals(expectedEmamiIntrinsic, emami.intrinsicValueToman ?: 0.0, 1.0)
        assertEquals(emamiMarket - expectedEmamiIntrinsic, emami.bubbleToman ?: 0.0, 1.0)
        assertEquals(((emamiMarket - expectedEmamiIntrinsic) / expectedEmamiIntrinsic) * 100.0, emami.bubblePercent ?: 0.0, 0.01)

        // Half coin missing from TGJU falls back to cached value with CACHED freshness & fresh bubble
        val half = goldAssets.first { it.id == "COIN_HALF" }
        assertEquals(DataFreshness.CACHED, half.freshness)
        assertEquals(42_000_000.0, half.priceToman, 1.0)
        val expectedHalfIntrinsic = (4.066 * 0.900) * gram24
        assertEquals(expectedHalfIntrinsic, half.intrinsicValueToman ?: 0.0, 1.0)

        // Quarter coin missing from TGJU and cache -> UNAVAILABLE (0.0, never gram18 * 3.55!)
        val quarter = goldAssets.first { it.id == "COIN_QUARTER" }
        assertEquals(DataFreshness.UNAVAILABLE, quarter.freshness)
        assertEquals(0.0, quarter.priceToman, 0.001)
        assertNull(quarter.bubbleToman)
        assertNotNull(quarter.intrinsicValueToman)
        assertTrue(abs((quarter.intrinsicValueToman ?: 0.0) - (2.033 * 0.900) * gram24) < 1.0)
    }

    @Test
    fun `FinancialApiService timestamp parsers handle ISO-8601 and UTC date strings`() {
        val isoTs = FinancialApiService.parseIsoTimestampMillis("2025-03-04T12:00:00Z")
        assertNotNull(isoTs)
        assertTrue(isoTs!! > 1_740_000_000_000L)

        val now = 1_741_090_000_000L // 2025-03-04 UTC
        val staleDateTs = FinancialApiService.parseDailyUtcTimestampMillis("2025-03-02", hourUtc = 15, nowMillis = now)
        assertTrue(now - staleDateTs > MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS)
    }
}
