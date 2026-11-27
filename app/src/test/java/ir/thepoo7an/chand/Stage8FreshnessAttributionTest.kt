package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.FinancialApiService
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.network.BitpinMarketService
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.WallexMarketService
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.network.TomanRateBasis
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage8FreshnessAttributionTest {

    @Before
    fun setUp() {
        LiveConfigRegistry.resetToDefault()
    }

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    // -------------------------------------------------------------------------
    // A. Timestamp Handling & Honest Freshness Classification
    // -------------------------------------------------------------------------

    @Test
    fun `missing source timestamp remains unknown 0L and never produces LIVE freshness`() {
        val now = 1_741_000_000_000L

        // TGJU quote missing "ts", Wallex/Bitpin missing timestamp, Nobitex missing lastUpdate, Binance closeTime=0
        val tgju = MarketRemoteDataSource.parseTgjuQuotesPayload(
            """{"current":{"price_dollar_rl":{"p":"710,000","dp":"0.5","dt":"high"}}}""",
            nowMillis = now
        )
        assertEquals(0L, tgju.getValue("price_dollar_rl").timestampMillis)

        val wallex = WallexMarketService.parseQuotesPayload(
            """{"result":{"markets":{"USDTTMN":{"baseAsset":"USDT","quoteAsset":"TMN","price":"71200"}}}}""",
            nowMillis = now
        )
        assertEquals(0L, wallex.getValue("USDT").timestampMillis)

        val bitpin = BitpinMarketService.parseQuotesPayload(
            """{"results":[{"currency1":{"code":"USDT"},"currency2":{"code":"IRT"},"price_info":{"price":"71250","change":0.2}}]}""",
            nowMillis = now
        )
        assertEquals(0L, bitpin.getValue("USDT").timestampMillis)

        val binance = MarketRemoteDataSource.parseBinanceQuotesPayload(
            """[{"symbol":"BTCUSDT","lastPrice":"90000","priceChangePercent":"1.0","closeTime":0}]""",
            nowMillis = now
        )
        assertEquals(0L, binance.getValue("BTC").timestampMillis)

        // Resolved USD from timestamp-less TGJU keeps fetchedAt separate from unknown sourceTimestamp (0L)
        val resolvedUsd = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = tgju["price_dollar_rl"],
            wallexUsdt = wallex["USDT"],
            bitpinUsdt = bitpin["USDT"],
            nobitexUsdtToman = 71_300.0,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = null,
            nowMillis = now,
            nobitexTimestampMillis = 0L
        )
        assertEquals(0L, resolvedUsd.usdSourceTimestampMillis)
        assertEquals(DataFreshness.DELAYED, resolvedUsd.usdFreshness)
        assertNotEquals(DataFreshness.LIVE, resolvedUsd.usdFreshness)

        // Unknown timestamp on Fiat or Crypto asset must yield DELAYED (never LIVE) while fetchedAtMillis = now
        val btc = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "BTC" },
            usdtToman = 71_200.0,
            usdtChangePct = 0.0,
            wallex = null,
            bitpin = null,
            binance = binance["BTC"],
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now,
            usdtBasisFreshness = DataFreshness.LIVE,
            usdtSourceTimestampMillis = now
        )!!
        assertEquals(0L, btc.sourceTimestampMillis)
        assertEquals(now, btc.fetchedAtMillis)
        assertEquals(DataFreshness.DELAYED, btc.freshness)
    }

    @Test
    fun `old source timestamp on live fetch is classified as DELAYED even when just fetched`() {
        val now = 1_741_000_000_000L
        val staleCryptoTs = now - 20L * 60_000L // 20m > 5m crypto limit
        val staleFiatTs = now - 90L * 60_000L   // 90m > 30m fiat/gold limit

        val eth = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "ETH" },
            usdtToman = 71_000.0,
            usdtChangePct = 0.0,
            wallex = MarketPriceMapper.ExchangeQuote(220_000_000.0, 3_100.0, 0.5, timestampMillis = staleCryptoTs),
            bitpin = null,
            binance = null,
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now
        )!!
        assertEquals(staleCryptoTs, eth.sourceTimestampMillis)
        assertEquals(now, eth.fetchedAtMillis)
        assertEquals(DataFreshness.DELAYED, eth.freshness)

        val eur = MarketPriceMapper.mapFiatAsset(
            spec = MarketPriceMapper.SUPPORTED_FIATS.first { it.id == "EUR" },
            usdToman = 71_000.0,
            usdChangePct = 0.0,
            tgjuQuote = MarketPriceMapper.TgjuQuote(77_000.0, null, changeToman = 0.0, changePct = 0.0, timestampMillis = staleFiatTs),
            fxPerUsd = null,
            usdtSpark = emptyList(),
            nowMillis = now
        )!!
        assertEquals(staleFiatTs, eur.sourceTimestampMillis)
        assertEquals(now, eur.fetchedAtMillis)
        assertEquals(DataFreshness.DELAYED, eur.freshness)
    }

    @Test
    fun `recently fetched cached or daily snapshot response is never automatically classified as LIVE`() = runTest {
        // 2025-03-04 14:00:00 UTC (1,741,096,800,000 ms)
        val now = 1_741_096_800_000L

        // CurrencyAPI daily snapshot dated same UTC day (2025-03-04) has hourUtc=0 -> 14h old -> DELAYED, not LIVE
        val dailyTs = FinancialApiService.parseDailyUtcTimestampMillis("2025-03-04", hourUtc = 0, nowMillis = now)
        assertEquals(1_741_046_400_000L, dailyTs)
        assertEquals(
            DataFreshness.DELAYED,
            MarketPriceMapper.deriveFreshness(
                category = AssetCategory.FIAT,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = dailyTs,
                nowMillis = now
            )
        )

        // Blank or future date never returns nowMillis
        assertEquals(0L, FinancialApiService.parseDailyUtcTimestampMillis("", hourUtc = 0, nowMillis = now))
        assertEquals(0L, FinancialApiService.parseDailyUtcTimestampMillis("2025-03-20", hourUtc = 0, nowMillis = now))

        // Cached anchor USD fetched right now remains CACHED with original cached source timestamp
        val oldCachedTs = now - 60_000L
        val resolvedCached = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = null,
            bitpinUsdt = null,
            nobitexUsdtToman = null,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = 70_000.0,
            nowMillis = now,
            cachedUsdTimestampMillis = oldCachedTs
        )
        assertEquals(TomanRateBasis.CACHED_USD_ANCHOR, resolvedCached.rateBasis)
        assertEquals(DataFreshness.CACHED, resolvedCached.usdFreshness)
        assertEquals(oldCachedTs, resolvedCached.usdSourceTimestampMillis)

        // Offline reference catalog retains 0L timestamps and UNAVAILABLE
        val offline = MarketPriceMapper.offlineReferenceCatalog(now)
        assertEquals(0L, offline.updatedAtMillis)
        assertTrue(offline.assets.all { it.sourceTimestampMillis == 0L && it.fetchedAtMillis == 0L && it.freshness == DataFreshness.UNAVAILABLE })
    }

    // -------------------------------------------------------------------------
    // B. Source Attribution & Candidate Breakdown Records
    // -------------------------------------------------------------------------

    @Test
    fun `calculated average and median labels stay linked and single provider is never misattributed`() {
        val now = 1_741_000_000_000L

        // 2 agreeing sources with different values -> Calculated Avg
        val avgConsensus = MarketPriceMapper.validateCrossSourcePrices(
            candidates = listOf(
                MarketPriceMapper.PriceCandidate("Wallex", 70_000.0, isPrimary = true, timestampMillis = now - 10_000L),
                MarketPriceMapper.PriceCandidate("Bitpin", 70_400.0, isPrimary = false, timestampMillis = now - 20_000L)
            ),
            thresholdPct = 3.0,
            nowMillis = now
        )!!
        assertEquals(70_200.0, avgConsensus.price, 0.01)
        assertTrue(avgConsensus.isCalculated)
        assertEquals("Calculated Avg (Wallex+Bitpin)", avgConsensus.sourceAttributionLabel)

        val usdtAvg = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "USDT" },
            usdtToman = 70_200.0,
            usdtChangePct = 0.0,
            wallex = MarketPriceMapper.ExchangeQuote(70_000.0, 1.0, 0.1, timestampMillis = now),
            bitpin = MarketPriceMapper.ExchangeQuote(70_400.0, 1.0, 0.1, timestampMillis = now),
            binance = null,
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now
        )!!
        assertTrue(usdtAvg.isCalculatedPrice)
        assertEquals("Calculated Avg (Wallex+Bitpin)", usdtAvg.sourceLabel)

        // 3 agreeing sources -> Calculated Median when providers differ
        val btcMedian = MarketPriceMapper.mapCryptoAsset(
            spec = MarketPriceMapper.SUPPORTED_CRYPTOS.first { it.id == "BTC" },
            usdtToman = 70_000.0,
            usdtChangePct = 0.0,
            wallex = MarketPriceMapper.ExchangeQuote(6_300_000_000.0, 90_000.0, 1.0, timestampMillis = now),
            bitpin = MarketPriceMapper.ExchangeQuote(6_320_000_000.0, 90_200.0, 1.0, timestampMillis = now),
            binance = MarketPriceMapper.ExchangeQuote(null, 90_100.0, 1.0, timestampMillis = now), // 6_307_000_000
            nobitexUsdtToman = null,
            sparkInput = emptyList(),
            nowMillis = now,
            usdtSourceTimestampMillis = now
        )!!
        assertTrue(btcMedian.isCalculatedPrice)
        assertTrue(btcMedian.sourceLabel.startsWith("Calculated Median ("))

        // 3 widely spread sources (<2 inliers) -> must select primary provider's exact price, NOT synthetic median
        val spreadConsensus = MarketPriceMapper.validateCrossSourcePrices(
            candidates = listOf(
                MarketPriceMapper.PriceCandidate("Wallex", 70_000.0, isPrimary = true, timestampMillis = now),
                MarketPriceMapper.PriceCandidate("Bitpin", 75_000.0, isPrimary = false, timestampMillis = now),
                MarketPriceMapper.PriceCandidate("Nobitex", 80_000.0, isPrimary = false, timestampMillis = now)
            ),
            thresholdPct = 3.0,
            nowMillis = now
        )!!
        assertFalse(spreadConsensus.isCalculated)
        assertTrue(spreadConsensus.disputed)
        assertEquals(70_000.0, spreadConsensus.price, 0.01)
        assertEquals("Wallex", spreadConsensus.sourceAttributionLabel)

        // Gold fallback calculated from multiple XAU spot sources must never attribute a synthetic median to one provider
        val golds = MarketPriceMapper.mapGoldAssets(
            tgju = emptyMap(),
            usdToman = 70_000.0,
            usdChangePct = 0.0,
            usdBasisFreshness = DataFreshness.LIVE,
            usdSourceTimestampMillis = now,
            goldSpotCandidates = listOf(
                MarketPriceMapper.PriceCandidate("GoldAPI", 2_800.0, isPrimary = true, timestampMillis = now),
                MarketPriceMapper.PriceCandidate("CurrencyAPI", 2_810.0, isPrimary = false, timestampMillis = now)
            ),
            paxgQuote = null,
            tgjuGoldRecent = emptyList(),
            goldSpark = emptyList(),
            usdtSpark = emptyList(),
            cachedAssetsById = emptyMap(),
            nowMillis = now
        )
        val gold18 = golds.first { it.id == "GOLD_18K" }
        assertTrue(gold18.isCalculatedPrice)
        assertEquals("Calculated Avg (GoldAPI+CurrencyAPI) + USD", gold18.sourceLabel)
    }

    @Test
    fun `included and rejected candidates record STALE, INVALID, EXCESSIVE_DISAGREEMENT, MISSING_DATA, and PROVIDER_FAILURE`() {
        val now = 1_741_000_000_000L

        val consensus = MarketPriceMapper.validateCrossSourcePrices(
            candidates = listOf(
                MarketPriceMapper.PriceCandidate("Wallex", 70_000.0, isPrimary = true, timestampMillis = now - 30_000L),
                MarketPriceMapper.PriceCandidate("Bitpin", 70_200.0, isPrimary = false, timestampMillis = now - 20_000L),
                MarketPriceMapper.PriceCandidate("StaleFeed", 70_100.0, isPrimary = false, timestampMillis = now - 15L * 60_000L),
                MarketPriceMapper.PriceCandidate("DisagreeFeed", 85_000.0, isPrimary = false, timestampMillis = now - 10_000L),
                MarketPriceMapper.PriceCandidate("ZeroFeed", -5.0, isPrimary = false, timestampMillis = now),
                MarketPriceMapper.PriceCandidate("MissingFeed", 0.0, unavailabilityReason = CandidateExclusionReason.MISSING_DATA),
                MarketPriceMapper.PriceCandidate("FailedFeed", 0.0, unavailabilityReason = CandidateExclusionReason.PROVIDER_FAILURE)
            ),
            thresholdPct = 3.0,
            nowMillis = now,
            staleMaxAgeMillis = MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
        )!!

        // 3 active candidates after dropping StaleFeed: [70_000, 70_200, 85_000] -> median is 70_200.0, DisagreeFeed rejected
        assertEquals(70_200.0, consensus.price, 0.01)
        assertEquals(listOf("Wallex", "Bitpin"), consensus.acceptedSources)

        val byName = consensus.candidateRecords.associateBy { it.sourceName }
        assertEquals(CandidateStatus.INCLUDED, byName.getValue("Wallex").status)
        assertEquals(CandidateStatus.INCLUDED, byName.getValue("Bitpin").status)
        assertEquals(CandidateStatus.REJECTED, byName.getValue("StaleFeed").status)
        assertEquals(CandidateExclusionReason.STALE, byName.getValue("StaleFeed").exclusionReason)
        assertEquals(CandidateStatus.REJECTED, byName.getValue("DisagreeFeed").status)
        assertEquals(CandidateExclusionReason.EXCESSIVE_DISAGREEMENT, byName.getValue("DisagreeFeed").exclusionReason)
        assertEquals(CandidateStatus.REJECTED, byName.getValue("ZeroFeed").status)
        assertEquals(CandidateExclusionReason.INVALID, byName.getValue("ZeroFeed").exclusionReason)
        assertEquals(CandidateStatus.REJECTED, byName.getValue("MissingFeed").status)
        assertEquals(CandidateExclusionReason.MISSING_DATA, byName.getValue("MissingFeed").exclusionReason)
        assertEquals(CandidateStatus.REJECTED, byName.getValue("FailedFeed").status)
        assertEquals(CandidateExclusionReason.PROVIDER_FAILURE, byName.getValue("FailedFeed").exclusionReason)

        // Serialization round-trip preserves candidates & isCalculatedPrice cleanly
        val sampleAsset = MarketAsset(
            id = "USDT",
            symbol = "USDT",
            nameFa = "تتر",
            nameEn = "Tether",
            category = AssetCategory.CRYPTO,
            badgeText = "₮",
            accentHex = 0xFF26A17B,
            priceToman = 70_100.0,
            sourceLabel = consensus.sourceAttributionLabel,
            freshness = DataFreshness.LIVE,
            sourceTimestampMillis = now - 30_000L,
            fetchedAtMillis = now,
            isCalculatedPrice = true,
            candidates = consensus.candidateRecords
        )
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(sampleAsset)
        val decoded = json.decodeFromString<MarketAsset>(encoded)
        assertTrue(decoded.isCalculatedPrice)
        assertEquals(consensus.candidateRecords.size, decoded.candidates.size)
        assertEquals(CandidateExclusionReason.STALE, decoded.candidates.first { it.sourceName == "StaleFeed" }.exclusionReason)
    }

    // -------------------------------------------------------------------------
    // C. History Normalization: Asset/Range Thresholds, Volatility, Duplicates & Ordering
    // -------------------------------------------------------------------------

    @Test
    fun `history normalization uses asset and timeframe aware spike thresholds without rejecting real volatility`() {
        assertEquals(25.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.FIAT, TimeRange.Y1), 0.001)
        assertEquals(25.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.GOLD, TimeRange.Y1), 0.001)
        assertEquals(15.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.FIAT, TimeRange.M1), 0.001)
        assertEquals(40.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.CRYPTO, TimeRange.M1), 0.001)

        val t0 = 1_700_000_000L
        // 22% single-interval move on 1Y Fiat chart is within 25% Y1 threshold -> preserved
        val fiatYearlyMove = listOf(
            AssetHistoryPoint(t0, 60_000.0),
            AssetHistoryPoint(t0 + 14L * 86_400L, 73_200.0), // +22%
            AssetHistoryPoint(t0 + 28L * 86_400L, 62_000.0)
        )
        val keptYearly = HistoryNormalizer.normalizeWithStats(fiatYearlyMove, AssetCategory.FIAT, TimeRange.Y1)
        assertEquals(3, keptYearly.points.size)
        assertEquals(0, keptYearly.removedSpikeCount)

        // Same 22% reverting single-tick jump on 1M Fiat chart (> 15%) -> treated as glitch spike
        val strippedMonthly = HistoryNormalizer.normalizeWithStats(fiatYearlyMove, AssetCategory.FIAT, TimeRange.M1)
        assertEquals(2, strippedMonthly.points.size)
        assertEquals(1, strippedMonthly.removedSpikeCount)

        // Valid sustained 50% Gold rally (multiple corroborating points) is NEVER discarded
        val goldRally = listOf(
            AssetHistoryPoint(t0, 4_000_000.0),
            AssetHistoryPoint(t0 + 86_400L, 6_000_000.0),
            AssetHistoryPoint(t0 + 2L * 86_400L, 6_100_000.0),
            AssetHistoryPoint(t0 + 3L * 86_400L, 5_950_000.0)
        )
        val rallyOut = HistoryNormalizer.normalizeWithStats(goldRally, AssetCategory.GOLD, TimeRange.M1)
        assertEquals(4, rallyOut.points.size)
        assertEquals(0, rallyOut.removedSpikeCount)
    }

    @Test
    fun `history normalization handles invalid timestamps, out-of-order points, and corrupt duplicates consistently`() {
        val raw = listOf(
            AssetHistoryPoint(300L, 71_000.0),
            AssetHistoryPoint(100L, 70_000.0),
            AssetHistoryPoint(100L, -50.0),                // corrupt duplicate must NOT erase earlier valid point at ts=100
            AssetHistoryPoint(0L, 70_500.0),                  // invalid zero timestamp
            AssetHistoryPoint(-100L, 70_500.0),               // invalid negative timestamp
            AssetHistoryPoint(1_741_000_000_000L, 70_500.0),  // invalid millis-as-seconds timestamp
            AssetHistoryPoint(200L, 70_600.0),                // out-of-order valid point
            AssetHistoryPoint(200L, 70_800.0)                 // newer valid duplicate at ts=200 replaces earlier valid point
        )

        val result = HistoryNormalizer.normalizeWithStats(raw, AssetCategory.FIAT, TimeRange.D7)
        assertEquals(listOf(100L, 200L, 300L), result.points.map { it.timestampSec })
        assertEquals(listOf(70_000.0, 70_800.0, 71_000.0), result.points.map { it.priceToman })
        assertEquals(4, result.invalidCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.removedSpikeCount)
    }
}
