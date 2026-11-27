package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.WallexMarketService
import ir.thepoo7an.chand.network.MarketValidationConfig
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

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
        val wallexPts = WallexMarketService.parseUdfPayload(wallexUdf)
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

    // -------------------------------------------------------------------------
    // 4. Adaptive Category / Range Spike Thresholds & Corroboration (PR-A)
    // -------------------------------------------------------------------------

    @Test
    fun `crypto 1M and 1Y preserve 18 pct reverting move while removing 60 pct outlier`() {
        val t0 = 1_700_000_000L
        val move18 = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L, 118.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )
        val spike60 = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L, 160.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )

        for (range in listOf(TimeRange.M1, TimeRange.Y1, TimeRange.D7)) {
            assertEquals(40.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.CRYPTO, range), 0.001)

            val keptStats = HistoryNormalizer.normalizeWithStats(move18, AssetCategory.CRYPTO, range)
            assertEquals(listOf(100.0, 118.0, 100.0), keptStats.points.map { it.priceToman })
            assertEquals(0, keptStats.removedSpikeCount)

            val strippedStats = HistoryNormalizer.normalizeWithStats(spike60, AssetCategory.CRYPTO, range)
            assertEquals(listOf(100.0, 100.0), strippedStats.points.map { it.priceToman })
            assertEquals(1, strippedStats.removedSpikeCount)
        }
    }

    @Test
    fun `crypto 24H removes 18 pct reverting spike using 15 pct threshold`() {
        assertEquals(15.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.CRYPTO, TimeRange.H24), 0.001)
        val t0 = 1_700_000_000L
        val pattern = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 3_600L, 118.0),
            AssetHistoryPoint(t0 + 7_200L, 100.0)
        )

        val cleaned = HistoryNormalizer.normalize(pattern, AssetCategory.CRYPTO, TimeRange.H24)
        assertEquals(listOf(100.0, 100.0), cleaned.map { it.priceToman })
        assertEquals(1, HistoryNormalizer.lastRemovedSpikeCount)
    }

    @Test
    fun `fiat and gold 1M remove isolated 18 pct reverting spike`() {
        val t0 = 1_700_000_000L
        val spike18 = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L, 118.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )

        for (category in listOf(AssetCategory.FIAT, AssetCategory.GOLD)) {
            assertEquals(15.0, MarketValidationConfig.spikeThresholdPct(category, TimeRange.M1), 0.001)
            val stats = HistoryNormalizer.normalizeWithStats(spike18, category, TimeRange.M1)
            assertEquals(listOf(100.0, 100.0), stats.points.map { it.priceToman })
            assertEquals(1, stats.removedSpikeCount)
        }
    }

    @Test
    fun `corroborated spike is kept when second provider matches within tolerance and half threshold`() {
        val t0 = 1_700_000_000L
        val primary = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L, 125.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )
        // Threshold = 15%, half = 7.5%; 120.0 vs 125.0 -> 4.0% <= 7.5%, and offset 1800s <= 12h (M1 tolerance)
        val matchingProvider = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L + 1_800L, 120.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )
        // Exact same primary spike WITHOUT corroboration -> removed
        val uncorroborated = HistoryNormalizer.normalizeWithStats(
            points = primary,
            category = AssetCategory.GOLD,
            range = TimeRange.M1
        )
        assertEquals(listOf(100.0, 100.0), uncorroborated.points.map { it.priceToman })
        assertEquals(1, uncorroborated.removedSpikeCount)

        // WITH matching second provider -> kept
        val corroborated = HistoryNormalizer.normalizeWithStats(
            points = primary,
            category = AssetCategory.GOLD,
            range = TimeRange.M1,
            corroborating = matchingProvider
        )
        assertEquals(listOf(100.0, 125.0, 100.0), corroborated.points.map { it.priceToman })
        assertEquals(0, corroborated.removedSpikeCount)

        // Second provider too far in time OR price deviation > half threshold (100 vs 125 = 20% > 7.5%) -> removed
        val disagreeingProvider = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 86_400L, 100.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 100.0)
        )
        val notCorroborated = HistoryNormalizer.normalizeWithStats(
            points = primary,
            category = AssetCategory.GOLD,
            range = TimeRange.M1,
            corroborating = disagreeingProvider
        )
        assertEquals(listOf(100.0, 100.0), notCorroborated.points.map { it.priceToman })
        assertEquals(1, notCorroborated.removedSpikeCount)
    }

    @Test
    fun `HistoryNormalizer property checks preserve sorting, uniqueness, endpoints, finite positive values, and under-3 series`() {
        val t0 = 1_700_000_000L

        // Fewer than 3 valid points (after filtering invalid entries) must never strip anything
        val twoValid = listOf(
            AssetHistoryPoint(t0, 100.0),
            AssetHistoryPoint(t0 + 50L, -10.0),
            AssetHistoryPoint(t0 + 60L, Double.NaN),
            AssetHistoryPoint(t0 + 100L, 500.0)
        )
        val twoStats = HistoryNormalizer.normalizeWithStats(twoValid, spikeThresholdPct = 15.0)
        assertEquals(2, twoStats.points.size)
        assertEquals(0, twoStats.removedSpikeCount)

        // First and last points are ALWAYS preserved even on extreme edge jumps
        val edgeJumps = listOf(
            AssetHistoryPoint(t0, 10.0),
            AssetHistoryPoint(t0 + 100L, 100.0),
            AssetHistoryPoint(t0 + 200L, 100.0),
            AssetHistoryPoint(t0 + 300L, 1_000.0)
        )
        val edgeResult = HistoryNormalizer.normalize(edgeJumps, spikeThresholdPct = 15.0)
        assertEquals(10.0, edgeResult.first().priceToman, 0.001)
        assertEquals(1_000.0, edgeResult.last().priceToman, 0.001)

        // Randomized property check across multiple random series
        val rng = Random(42)
        repeat(25) {
            val raw = (1..40).map { idx ->
                val ts = if (idx % 7 == 0) t0 + (idx - 1) * 100L else t0 + rng.nextLong(1, 5000)
                val price = when {
                    idx % 13 == 0 -> -5.0
                    idx % 17 == 0 -> Double.POSITIVE_INFINITY
                    else -> rng.nextDouble(50.0, 500.0)
                }
                AssetHistoryPoint(ts, price)
            }
            val expectedValid = raw
                .filter { it.timestampSec > 0L && it.priceToman.isFinite() && it.priceToman > 0.0 }
                .associateBy { it.timestampSec }
                .values
                .sortedBy { it.timestampSec }

            val out = HistoryNormalizer.normalizeWithStats(raw, AssetCategory.CRYPTO, TimeRange.M1)
            val pts = out.points

            assertTrue(pts.all { it.timestampSec > 0L && it.priceToman.isFinite() && it.priceToman > 0.0 })
            assertEquals(pts.map { it.timestampSec }.distinct().size, pts.size)
            assertEquals(pts.sortedBy { it.timestampSec }, pts)
            if (expectedValid.isNotEmpty()) {
                assertEquals(expectedValid.first(), pts.first())
                assertEquals(expectedValid.last(), pts.last())
                assertEquals(expectedValid.size - pts.size, out.removedSpikeCount)
            }
        }
    }

    @Test
    fun `MarketRemoteDataSource fetchAssetHistory threads category and range to HistoryNormalizer`() = runTest {
        val nowSec = System.currentTimeMillis() / 1000L
        val t0 = nowSec - 4 * 86_400L
        val t1 = nowSec - 3 * 86_400L
        val t2 = nowSec - 2 * 86_400L
        val t3 = nowSec - 1 * 86_400L
        val t4 = nowSec - 60L

        val wallexPayload = """
            {"s":"ok","t":[$t0,$t1,$t2,$t3,$t4],"c":["1000","1180","1000","1005","1010"]}
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val body = when {
                    req.url.host.contains("wallex.ir") -> wallexPayload
                    else -> """{"s":"no_data"}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val ds = MarketRemoteDataSource(client = client)
        val btc = MarketAsset(
            id = "BTC",
            symbol = "BTC",
            nameFa = "بیت‌کوین",
            nameEn = "Bitcoin",
            category = AssetCategory.CRYPTO,
            badgeText = "₿",
            accentHex = 0xFFF59E0B,
            priceToman = 1010.0,
            freshness = DataFreshness.LIVE
        )

        // Crypto 1M keeps the 18% move (threshold 40%)
        val m1 = ds.fetchAssetHistory(btc, TimeRange.M1)
        assertTrue(m1.isRealData)
        assertEquals(5, m1.points.size)
        assertEquals(1180.0, m1.points[1].priceToman, 0.01)

        // Crypto 24H strips the 18% reverting spike (threshold 15%), leaving 4 points
        val h24 = ds.fetchAssetHistory(btc, TimeRange.H24)
        assertTrue(h24.isRealData)
        assertEquals(listOf(1000.0, 1000.0, 1005.0, 1010.0), h24.points.map { it.priceToman })
    }
}
