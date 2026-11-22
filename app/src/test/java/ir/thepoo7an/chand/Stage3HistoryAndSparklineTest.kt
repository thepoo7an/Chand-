package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.MarketValidationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage3HistoryAndSparklineTest {

    // -------------------------------------------------------------------------
    // 1. Removal of fabricated sparklines
    // -------------------------------------------------------------------------

    @Test
    fun `missing real series returns empty sparkline instead of synthetic 3-point diagonal`() {
        val now = 1_741_000_000_000L

        val eur = MarketPriceMapper.mapFiatAsset(
            spec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "EUR" },
            usdToman = 71_000.0,
            usdChangePct = 2.4,
            tgjuQuote = null,
            fxPerUsd = 0.92,
            usdtSpark = emptyList(),
            nowMillis = now,
            currencySpark = emptyList()
        )
        assertNotNull(eur)
        assertTrue("Expected empty sparkline when no real history exists", eur!!.sparkline.isEmpty())

        val sol = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "SOL" },
            usdtToman = 71_000.0,
            usdtChangePct = 1.0,
            wallex = MarketPriceMapper.ExchangeQuote(priceToman = 11_000_000.0, priceUsd = 155.0, changePct = 4.5, timestampMillis = now),
            bitpin = null,
            binance = null,
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now
        )
        assertNotNull(sol)
        assertTrue("Expected empty sparkline when crypto 24h series is absent", sol!!.sparkline.isEmpty())

        val golds = MarketPriceMapper.mapGoldAssets(
            tgju = mapOf(
                "geram18" to MarketPriceMapper.TgjuQuote(
                    priceToman = 5_800_000.0,
                    openToman = 5_700_000.0,
                    changeToman = 100_000.0,
                    changePct = 1.75,
                    timestampMillis = now
                )
            ),
            usdToman = 71_000.0,
            usdChangePct = 0.0,
            usdBasisFreshness = DataFreshness.LIVE,
            usdSourceTimestampMillis = now,
            goldSpotCandidates = emptyList(),
            paxgQuote = null,
            tgjuGoldRecent = emptyList(),
            goldSpark = emptyList(),
            usdtSpark = emptyList(),
            cachedAssetsById = emptyMap(),
            nowMillis = now
        )
        assertTrue(golds.first { it.id == "GOLD_18K" }.sparkline.isEmpty())

        val offline = MarketPriceMapper.offlineReferenceCatalog(now)
        assertTrue(
            "Offline reference assets must never synthesize fake sparklines",
            offline.assets.all { it.sparkline.isEmpty() }
        )

        assertTrue(MarketPriceMapper.scaleSeriesToCurrent(listOf(100.0), 120.0).isEmpty())
        assertTrue(MarketPriceMapper.scaleSeriesToCurrent(listOf(-5.0, Double.NaN, 10.0), 120.0).isEmpty())
    }

    // -------------------------------------------------------------------------
    // 2. Deterministic TGJU unit scale normalization (no ratio guessing)
    // -------------------------------------------------------------------------

    @Test
    fun `parseTgjuTablePayload uses deterministic slug units for standard Rial, 100-JPY, and 1000-IQD`() {
        // Simulates >3x appreciation in standard Rial slug where ratio heuristics would break
        val usdAppreciatedPayload = """
            {
              "data": [
                ["300,000", "295,000", "305,000", "300,000", "", "", "2024/01/01", ""],
                ["950,000", "940,000", "960,000", "950,000", "", "", "2025/01/01", ""]
              ]
            }
        """.trimIndent()
        val usdPts = MarketRemoteDataSource.parseTgjuTablePayload(usdAppreciatedPayload, slug = "price_dollar_rl")
        assertEquals(2, usdPts.size)
        assertEquals(30_000.0, usdPts[0].priceToman, 0.01)
        assertEquals(95_000.0, usdPts[1].priceToman, 0.01)

        // JPY is quoted in Rials per 100 units -> scale 0.001
        val jpyPayload = """
            {
              "data": [
                ["466,000", "460,000", "468,000", "466,000", "", "", "2025/03/01", ""]
              ]
            }
        """.trimIndent()
        val jpyPts = MarketRemoteDataSource.parseTgjuTablePayload(jpyPayload, slug = "price_jpy")
        assertEquals(1, jpyPts.size)
        assertEquals(466.0, jpyPts[0].priceToman, 0.01)

        // IQD is quoted in Rials per 1000 units -> scale 0.0001
        val iqdPayload = """
            {
              "data": [
                ["534,000", "530,000", "535,000", "534,000", "", "", "2025/03/01", ""]
              ]
            }
        """.trimIndent()
        val iqdPts = MarketRemoteDataSource.parseTgjuTablePayload(iqdPayload, slug = "price_iqd")
        assertEquals(1, iqdPts.size)
        assertEquals(53.4, iqdPts[0].priceToman, 0.01)
    }

    // -------------------------------------------------------------------------
    // 3. HistoryNormalizer: sort, dedup, invalid filtering, & 15% spike filter
    // -------------------------------------------------------------------------

    @Test
    fun `HistoryNormalizer sorts chronologically, deduplicates timestamps keeping latest, and strips reverting over 15 pct spikes`() {
        val raw = listOf(
            AssetHistoryPoint(300L, 70_400.0),
            AssetHistoryPoint(100L, 69_000.0),
            AssetHistoryPoint(0L, 70_000.0),          // invalid timestamp
            AssetHistoryPoint(150L, -10.0),           // invalid negative price
            AssetHistoryPoint(100L, 70_000.0),        // duplicate ts=100 (latest 70_000 wins)
            AssetHistoryPoint(200L, 86_000.0),        // +22.8% spike that immediately reverts to 70_400
            AssetHistoryPoint(400L, 52_000.0),        // -26.1% downward flash glitch that reverts to 70_600
            AssetHistoryPoint(500L, 70_600.0),
            AssetHistoryPoint(600L, 78_000.0),        // +10.5% normal swing <= 15% -> kept
            AssetHistoryPoint(700L, 71_000.0)
        )

        val cleaned = HistoryNormalizer.normalize(
            points = raw,
            spikeThresholdPct = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        )

        assertEquals(listOf(100L, 300L, 500L, 600L, 700L), cleaned.map { it.timestampSec })
        assertEquals(70_000.0, cleaned.first().priceToman, 0.01)
        assertEquals(78_000.0, cleaned[3].priceToman, 0.01)
    }

    @Test
    fun `HistoryNormalizer preserves legitimate sustained rally or step change over 15 pct`() {
        val rally = listOf(
            AssetHistoryPoint(100L, 70_000.0),
            AssetHistoryPoint(200L, 85_000.0), // +21.4% jump
            AssetHistoryPoint(300L, 86_500.0), // continues upward -> keep
            AssetHistoryPoint(400L, 85_900.0)  // stays near elevated plateau -> keep
        )
        val cleaned = HistoryNormalizer.normalize(rally)
        assertEquals(4, cleaned.size)
        assertEquals(listOf(70_000.0, 85_000.0, 86_500.0, 85_900.0), cleaned.map { it.priceToman })
    }

    @Test
    fun `Wallex, Nobitex, Binance, and CoinGecko history parsers all sort, deduplicate, and strip over 15 pct spikes`() {
        // Wallex UDF unsorted + duplicate + upward glitch
        val wallexUdf = """
            {"s":"ok","t":[300,100,200,100],"c":["70500","69000","95000","70000"]}
        """.trimIndent()
        val wallexPts = MarketRemoteDataSource.parseWallexUdfPayload(wallexUdf)
        assertEquals(listOf(100L, 300L), wallexPts.map { it.timestampSec })
        assertEquals(listOf(70_000.0, 70_500.0), wallexPts.map { it.priceToman })

        // Nobitex UDF Rials->Toman + unsorted + duplicate + downward flash glitch
        val nobitexUdf = """
            {"s":"ok","t":[300,100,200,300],"c":["702000","700000","450000","704000"]}
        """.trimIndent()
        val nobitexPts = MarketRemoteDataSource.parseNobitexUdfPayload(nobitexUdf)
        assertEquals(listOf(100L, 300L), nobitexPts.map { it.timestampSec })
        assertEquals(listOf(70_000.0, 70_400.0), nobitexPts.map { it.priceToman })

        // Binance klines unsorted + duplicate + >15% glitch
        val binanceKlines = """
            [
              [0,"90000","91000","89000","90200","10",300000],
              [0,"90000","91000","89000","90000","10",100000],
              [0,"90000","120000","89000","115000","10",200000],
              [0,"90000","91000","89000","90500","10",300000]
            ]
        """.trimIndent()
        val binancePts = MarketRemoteDataSource.parseBinanceKlinesPayload(binanceKlines)
        assertEquals(listOf(100L, 300L), binancePts.map { it.timestampSec })
        assertEquals(listOf(90_000.0, 90_500.0), binancePts.map { it.priceToman })

        // CoinGecko downsampling preserves chronological order & strips glitches before sampling
        val pricesJson = (1..250).joinToString(",") { i ->
            val p = if (i == 125) 180.0 else 100.0 + (i * 0.05)
            "[${i * 1000L}, $p]"
        }
        val cgJson = """{"prices":[$pricesJson]}"""
        val cgPts = MarketRemoteDataSource.parseCoinGeckoHistoryPayload(cgJson)
        assertTrue(cgPts.size in 100..125)
        assertTrue("Expected spike 180.0 stripped before downsampling", cgPts.none { it.priceToman > 130.0 })
        assertEquals(250L, cgPts.last().timestampSec)
    }
}
