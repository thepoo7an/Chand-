package ir.thepoo7an.chand

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.OilApiService
import ir.thepoo7an.chand.network.OilPriceMapper
import ir.thepoo7an.chand.network.RemoteConfigService
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownBuilder
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownSheet
import ir.thepoo7an.chand.ui.components.SourceBreakdownStatus
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class, qualifiers = "w412dp-h915dp")
class Stage3CrudeOilTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedNowMillis = 1_795_176_000_000L // 2026-11-20 12:00:00 UTC
    private val fixedNowSec = fixedNowMillis / 1000L

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    // -------------------------------------------------------------------------
    // 1. Provider Payload Parsing (Yahoo Finance, CNBC, U.S. EIA)
    // -------------------------------------------------------------------------

    @Test
    fun `yahoo v8 parser extracts valid Brent USD per barrel quote, 24h change, and candles`() {
        val raw = """
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "currency": "USD",
                      "symbol": "BZ=F",
                      "regularMarketPrice": 74.85,
                      "regularMarketPreviousClose": 73.60,
                      "regularMarketDayHigh": 75.40,
                      "regularMarketDayLow": 73.20,
                      "regularMarketTime": ${fixedNowSec - 120}
                    },
                    "timestamp": [${fixedNowSec - 7200}, ${fixedNowSec - 3600}, ${fixedNowSec - 120}],
                    "indicators": {
                      "quote": [
                        { "close": [73.95, 74.30, 74.85] }
                      ]
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()

        val snap = OilApiService.parseYahooChartQuotePayload(raw, assetId = "BRENT", nowMillis = fixedNowMillis)
        assertNotNull(snap)
        snap!!
        assertEquals("BRENT", snap.assetId)
        assertEquals("USD", snap.currency)
        assertEquals(OilApiService.UNIT_USD_PER_BARREL, snap.unit)
        assertEquals(74.85, snap.priceUsd!!, 0.0001)
        assertEquals(1.25, snap.change24hUsd!!, 0.0001)
        assertEquals((1.25 / 73.60) * 100.0, snap.change24hPct!!, 0.001)
        assertEquals(75.40, snap.high24hUsd!!, 0.0001)
        assertEquals(73.20, snap.low24hUsd!!, 0.0001)
        assertEquals((fixedNowSec - 120) * 1000L, snap.sourceTimestampMillis)
        assertEquals(3, snap.sparklineUsd.size)
        assertNull(snap.failureReason)
    }

    @Test
    fun `yahoo v8 parser rejects non-USD currency and out-of-range barrel prices`() {
        val nonUsd = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "EUR",
                    "regularMarketPrice": 69.50,
                    "regularMarketTime": ${fixedNowSec - 60}
                  }
                }],
                "error": null
              }
            }
        """.trimIndent()
        val nonUsdSnap = OilApiService.parseYahooChartQuotePayload(nonUsd, "BRENT", fixedNowMillis)
        assertNotNull(nonUsdSnap)
        assertNull(nonUsdSnap!!.priceUsd)
        assertEquals(CandidateExclusionReason.INVALID, nonUsdSnap.failureReason)

        val absurdHigh = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "regularMarketPrice": 99999.0,
                    "regularMarketTime": ${fixedNowSec - 60}
                  }
                }],
                "error": null
              }
            }
        """.trimIndent()
        val absurdSnap = OilApiService.parseYahooChartQuotePayload(absurdHigh, "WTI", fixedNowMillis)
        assertNotNull(absurdSnap)
        assertEquals(CandidateExclusionReason.INVALID, absurdSnap!!.failureReason)
    }

    @Test
    fun `yahoo v8 parser returns null 24h change when previous close and 24h candle are missing`() {
        val raw = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "regularMarketPrice": 70.12,
                    "regularMarketTime": ${fixedNowSec - 60}
                  },
                  "timestamp": [${fixedNowSec - 60}],
                  "indicators": { "quote": [{ "close": [70.12] }] }
                }],
                "error": null
              }
            }
        """.trimIndent()

        val snap = OilApiService.parseYahooChartQuotePayload(raw, "WTI", fixedNowMillis)!!
        assertEquals(70.12, snap.priceUsd!!, 0.0001)
        assertNull(snap.change24hPct)
        assertNull(snap.change24hUsd)
    }

    @Test
    fun `cnbc parser handles valid Brent and WTI batch and marks missing 24h change explicitly`() {
        val raw = """
            {
              "FormattedQuoteResult": {
                "FormattedQuote": [
                  {
                    "symbol": "@LCO.1",
                    "currency": "USD",
                    "last": "74.90",
                    "change": "+1.10",
                    "change_pct": "+1.49%",
                    "high": "75.30",
                    "low": "73.40",
                    "last_time": "2026-11-20T11:58:00+0000"
                  },
                  {
                    "symbol": "@CL.1",
                    "currency": "USD",
                    "last": "70.45",
                    "change": "--",
                    "change_pct": "--",
                    "last_time": "2026-11-20T11:57:00+0000"
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = OilApiService.parseCnbcQuotesPayload(raw, nowMillis = fixedNowMillis)
        assertEquals(2, parsed.size)

        val brent = parsed["BRENT"]!!
        assertEquals(74.90, brent.priceUsd!!, 0.0001)
        assertEquals(1.49, brent.change24hPct!!, 0.0001)
        assertEquals(1.10, brent.change24hUsd!!, 0.0001)
        assertTrue(brent.sourceTimestampMillis > 0L)
        assertNull(brent.failureReason)

        val wti = parsed["WTI"]!!
        assertEquals(70.45, wti.priceUsd!!, 0.0001)
        assertNull(wti.change24hPct)
        assertNull(wti.change24hUsd)
        assertNull(wti.failureReason)
    }

    @Test
    fun `eia parser validates barrel unit, daily timestamp, and rejects non-barrel units`() {
        val raw = """
            {
              "response": {
                "data": [
                  { "period": "2026-11-19", "series": "RBRTE", "value": 74.20, "units": "${'$'}/BBL" },
                  { "period": "2026-11-18", "series": "RBRTE", "value": 73.10, "units": "${'$'}/BBL" },
                  { "period": "2026-11-19", "series": "RWTC", "value": 2.15, "units": "dollars per gallon" }
                ]
              }
            }
        """.trimIndent()

        val parsed = OilApiService.parseEiaSpotQuotesPayload(raw, nowMillis = fixedNowMillis)
        val brent = parsed["BRENT"]
        assertNotNull(brent)
        assertEquals(74.20, brent!!.priceUsd!!, 0.0001)
        assertEquals(1.10, brent.change24hUsd!!, 0.0001)
        assertTrue(brent.sourceTimestampMillis > 0L)
        // Non-barrel WTI row is ignored cleanly
        assertNull(parsed["WTI"])
    }

    @Test
    fun `yahoo oil history parser returns normalized USD per barrel series`() {
        val raw = """
            {
              "chart": {
                "result": [{
                  "meta": { "currency": "USD" },
                  "timestamp": [${fixedNowSec - 7200}, ${fixedNowSec - 5400}, ${fixedNowSec - 3600}, ${fixedNowSec - 1800}],
                  "indicators": {
                    "quote": [{ "close": [73.5, 74.1, 74.4, 74.8] }]
                  }
                }],
                "error": null
              }
            }
        """.trimIndent()

        val pts = OilApiService.parseYahooChartHistoryPayload(raw, TimeRange.H24, fixedNowMillis)
        assertEquals(4, pts.size)
        assertEquals(73.5, pts.first().priceToman, 0.001)
        assertEquals(74.8, pts.last().priceToman, 0.001)
    }

    // -------------------------------------------------------------------------
    // 2. Consensus, Freshness, Failure Fallbacks & USD/bbl Unit Isolation
    // -------------------------------------------------------------------------

    @Test
    fun `valid multi-source consensus maps Brent primary and WTI secondary in USD per barrel with labeled Toman estimate`() {
        val batch = OilApiService.OilMarketBatch(
            quotesByProvider = mapOf(
                OilApiService.SOURCE_YAHOO to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_YAHOO,
                        priceUsd = 74.80,
                        currency = "USD",
                        change24hPct = 1.40,
                        change24hUsd = 1.03,
                        high24hUsd = 75.20,
                        low24hUsd = 73.50,
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = true
                    ),
                    "WTI" to OilApiService.OilQuoteSnapshot(
                        assetId = "WTI",
                        sourceName = OilApiService.SOURCE_YAHOO,
                        priceUsd = 70.20,
                        currency = "USD",
                        change24hPct = 1.15,
                        change24hUsd = 0.80,
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = true
                    )
                ),
                OilApiService.SOURCE_CNBC to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_CNBC,
                        priceUsd = 75.00,
                        currency = "USD",
                        change24hPct = 1.60,
                        change24hUsd = 1.18,
                        sourceTimestampMillis = fixedNowMillis - 90_000L,
                        isPrimary = false
                    ),
                    "WTI" to OilApiService.OilQuoteSnapshot(
                        assetId = "WTI",
                        sourceName = OilApiService.SOURCE_CNBC,
                        priceUsd = 70.40,
                        currency = "USD",
                        change24hPct = 1.35,
                        change24hUsd = 0.94,
                        sourceTimestampMillis = fixedNowMillis - 90_000L,
                        isPrimary = false
                    )
                )
            ),
            failedProviders = emptySet(),
            configuredProviders = listOf(OilApiService.SOURCE_YAHOO, OilApiService.SOURCE_CNBC)
        )

        val mapped = MarketPriceMapper.mapOilAssets(
            batch = batch,
            usdToman = 110_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        )

        assertEquals(2, mapped.size)
        val brent = mapped[0]
        val wti = mapped[1]

        assertEquals("BRENT", brent.id)
        assertEquals("WTI", wti.id)
        assertEquals(AssetCategory.OIL, brent.category)
        assertEquals(PriceDisplayUnit.USD_PER_BARREL, brent.primaryUnit)
        assertTrue(brent.isUsdPerBarrel)
        assertEquals(74.90, brent.priceUsd!!, 0.0001)
        assertEquals(74.90, brent.primaryPriceValue, 0.0001)
        assertEquals(74.90 * 110_000.0, brent.priceToman, 0.1)
        assertEquals(DataFreshness.LIVE, brent.freshness)
        assertTrue(brent.isCalculatedPrice)
        assertTrue(brent.isChange24hAvailable)
        assertEquals(1.50, brent.change24hPercent, 0.001)
        assertEquals(2, brent.candidates.count { it.status == CandidateStatus.INCLUDED })

        assertEquals(PriceDisplayUnit.USD_PER_BARREL, wti.primaryUnit)
        assertEquals(70.30, wti.priceUsd!!, 0.0001)
        assertEquals(70.30 * 110_000.0, wti.priceToman, 0.1)
    }

    @Test
    fun `oil USD per barrel stays LIVE even when domestic USD Toman rate is UNAVAILABLE`() {
        val batch = OilApiService.OilMarketBatch(
            quotesByProvider = mapOf(
                OilApiService.SOURCE_YAHOO to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_YAHOO,
                        priceUsd = 75.00,
                        currency = "USD",
                        change24hPct = null,
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = true
                    )
                )
            ),
            failedProviders = setOf(OilApiService.SOURCE_CNBC),
            configuredProviders = listOf(OilApiService.SOURCE_YAHOO, OilApiService.SOURCE_CNBC)
        )

        val mapped = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = batch,
            usdToman = 0.0,
            usdBasisFreshness = DataFreshness.UNAVAILABLE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        )

        val brent = mapped.single()
        assertEquals(PriceDisplayUnit.USD_PER_BARREL, brent.primaryUnit)
        assertEquals(75.00, brent.priceUsd!!, 0.0001)
        assertEquals(0.0, brent.priceToman, 0.0)
        assertTrue(brent.hasUsablePrimaryPrice)
        assertEquals(DataFreshness.LIVE, brent.freshness)
        assertFalse("Missing 24h change must remain explicitly unavailable", brent.isChange24hAvailable)
        assertEquals(0.0, brent.change24hPercent, 0.0)
        assertNull(brent.change24hUsd)

        val cnbcCandidate = brent.candidates.first { it.sourceName == OilApiService.SOURCE_CNBC }
        assertEquals(CandidateStatus.REJECTED, cnbcCandidate.status)
        assertEquals(CandidateExclusionReason.PROVIDER_FAILURE, cnbcCandidate.exclusionReason)
    }

    @Test
    fun `stale oil provider quote is marked DELAYED when alone and STALE rejected alongside fresh feeds`() {
        val staleTimestamp = fixedNowMillis - 2L * 3600_000L // 2 hours old (> 30m oil max age)
        val staleOnlyBatch = OilApiService.OilMarketBatch(
            quotesByProvider = mapOf(
                OilApiService.SOURCE_EIA to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_EIA,
                        priceUsd = 74.10,
                        currency = "USD",
                        sourceTimestampMillis = staleTimestamp,
                        isPrimary = false
                    )
                )
            ),
            failedProviders = emptySet(),
            configuredProviders = listOf(OilApiService.SOURCE_EIA)
        )

        val delayedAsset = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = staleOnlyBatch,
            usdToman = 100_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        ).single()
        assertEquals(DataFreshness.DELAYED, delayedAsset.freshness)

        // Alongside fresh Yahoo + CNBC feeds, the 2h-old EIA quote is rejected as STALE
        val mixedBatch = OilApiService.OilMarketBatch(
            quotesByProvider = mapOf(
                OilApiService.SOURCE_YAHOO to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_YAHOO,
                        priceUsd = 75.00,
                        currency = "USD",
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = true
                    )
                ),
                OilApiService.SOURCE_CNBC to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_CNBC,
                        priceUsd = 75.20,
                        currency = "USD",
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = false
                    )
                ),
                OilApiService.SOURCE_EIA to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_EIA,
                        priceUsd = 73.90,
                        currency = "USD",
                        sourceTimestampMillis = staleTimestamp,
                        isPrimary = false
                    )
                )
            ),
            failedProviders = emptySet(),
            configuredProviders = listOf(
                OilApiService.SOURCE_YAHOO,
                OilApiService.SOURCE_CNBC,
                OilApiService.SOURCE_EIA
            )
        )

        val consensusWithStaleRejected = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = mixedBatch,
            usdToman = 100_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        ).single()

        assertEquals(DataFreshness.LIVE, consensusWithStaleRejected.freshness)
        assertEquals(75.10, consensusWithStaleRejected.priceUsd!!, 0.001)
        val eiaRec = consensusWithStaleRejected.candidates.first { it.sourceName == OilApiService.SOURCE_EIA }
        assertEquals(CandidateStatus.REJECTED, eiaRec.status)
        assertEquals(CandidateExclusionReason.STALE, eiaRec.exclusionReason)
    }

    @Test
    fun `provider failures fall back to CACHED USD per barrel or UNAVAILABLE when cache absent`() {
        val failBatch = OilApiService.OilMarketBatch(
            quotesByProvider = emptyMap(),
            failedProviders = setOf(OilApiService.SOURCE_YAHOO, OilApiService.SOURCE_CNBC),
            configuredProviders = listOf(OilApiService.SOURCE_YAHOO, OilApiService.SOURCE_CNBC)
        )

        val noCache = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = failBatch,
            usdToman = 100_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        ).single()

        assertEquals(DataFreshness.UNAVAILABLE, noCache.freshness)
        assertNull(noCache.priceUsd)
        assertEquals(0.0, noCache.priceToman, 0.0)
        assertFalse(noCache.hasUsablePrimaryPrice)
        assertEquals(2, noCache.candidates.size)
        assertTrue(noCache.candidates.all { it.exclusionReason == CandidateExclusionReason.PROVIDER_FAILURE })

        val cachedBrent = MarketAsset(
            id = "BRENT",
            symbol = "BRENT",
            nameFa = "نفت خام برنت",
            nameEn = "Brent Crude",
            category = AssetCategory.OIL,
            badgeText = "BRENT",
            accentHex = 0xFF1F2937,
            priceToman = 0.0,
            priceUsd = 74.50,
            freshness = DataFreshness.LIVE,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
            isChange24hAvailable = false
        )

        val withCache = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = failBatch,
            usdToman = 110_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = mapOf("BRENT" to cachedBrent),
            nowMillis = fixedNowMillis
        ).single()

        assertEquals(DataFreshness.CACHED, withCache.freshness)
        assertEquals(74.50, withCache.priceUsd!!, 0.0001)
        assertEquals(74.50 * 110_000.0, withCache.priceToman, 0.1)
        assertEquals(PriceDisplayUnit.USD_PER_BARREL, withCache.primaryUnit)
    }

    @Test
    fun `offlineReferenceCatalog includes Brent primary and WTI secondary in USD per barrel as UNAVAILABLE`() {
        val snap = MarketPriceMapper.offlineReferenceCatalog(fixedNowMillis)
        val oils = snap.assets.filter { it.category == AssetCategory.OIL }
        assertEquals(listOf("BRENT", "WTI"), oils.map { it.id })
        oils.forEach { oil ->
            assertEquals(PriceDisplayUnit.USD_PER_BARREL, oil.primaryUnit)
            assertEquals(DataFreshness.UNAVAILABLE, oil.freshness)
            assertFalse(oil.isChange24hAvailable)
            assertFalse(oil.hasUsablePrimaryPrice)
            assertNull(oil.priceUsd)
            assertEquals(0.0, oil.priceToman, 0.0)
        }
    }

    @Test
    fun `live config sanitizes oil HTTPS endpoints and rejects plain HTTP overrides`() {
        val raw = """
            {
              "configVersion": 9,
              "endpoints": {
                "oilYahooChartUrlTemplates": [
                  "http://insecure.example.com/{symbol}",
                  "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}"
                ],
                "oilCnbcQuotesUrl": "http://insecure.cnbc.com/quote",
                "oilEiaSpotUrl": "https://api.eia.gov/v2/petroleum/pri/spt/data/?frequency=daily"
              }
            }
        """.trimIndent()

        val rawParsed = RemoteConfigService().parseConfigJson(raw)!!
        assertTrue(LiveConfigRegistry.applyConfig(rawParsed))
        val parsed = LiveConfigRegistry.currentConfig()
        assertEquals(1, parsed.endpoints.oilYahooChartUrlTemplates.size)
        assertTrue(parsed.endpoints.oilYahooChartUrlTemplates.single().startsWith("https://"))
        assertEquals("", parsed.endpoints.oilCnbcQuotesUrl)
        assertTrue(parsed.endpoints.oilEiaSpotUrl.startsWith("https://api.eia.gov/"))
    }

    // -------------------------------------------------------------------------
    // 3. Source Breakdown Builder & Bilingual Compose UI (FA RTL & EN LTR)
    // -------------------------------------------------------------------------

    @Test
    fun `source breakdown builder shows USD per barrel primary price and optional labeled Toman estimate`() {
        val batch = OilApiService.OilMarketBatch(
            quotesByProvider = mapOf(
                OilApiService.SOURCE_YAHOO to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_YAHOO,
                        priceUsd = 75.25,
                        currency = "USD",
                        change24hPct = 1.2,
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = true
                    )
                ),
                OilApiService.SOURCE_CNBC to mapOf(
                    "BRENT" to OilApiService.OilQuoteSnapshot(
                        assetId = "BRENT",
                        sourceName = OilApiService.SOURCE_CNBC,
                        priceUsd = 82.00,
                        currency = "USD",
                        sourceTimestampMillis = fixedNowMillis - 60_000L,
                        isPrimary = false
                    )
                )
            ),
            failedProviders = emptySet(),
            configuredProviders = listOf(OilApiService.SOURCE_YAHOO, OilApiService.SOURCE_CNBC)
        )

        val brent = MarketPriceMapper.mapOilAssets(
            specs = listOf(OilPriceMapper.SUPPORTED_OILS.first()),
            batch = batch,
            usdToman = 100_000.0,
            usdBasisFreshness = DataFreshness.LIVE,
            cachedAssetsById = emptyMap(),
            nowMillis = fixedNowMillis
        ).single()

        val model = MarketSourceBreakdownBuilder.build(
            asset = brent,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = fixedNowMillis
        )

        assertEquals(EnStrings.sourceUnitCurrencyUsdPerBarrel, model.unitCurrencyText)
        assertTrue(model.mainPriceText.contains("75.25"))
        assertTrue(brent.disputed)
        val yahooRow = model.rows.first { it.sourceName == OilApiService.SOURCE_YAHOO }
        assertEquals(SourceBreakdownStatus.INCLUDED, yahooRow.status)
        assertTrue(yahooRow.reportedPriceText.contains("75.25"))
        assertTrue(yahooRow.rawQuoteText!!.startsWith(EnStrings.oilTomanEstimatePrefix))

        val cnbcRow = model.rows.first { it.sourceName == OilApiService.SOURCE_CNBC }
        assertEquals(SourceBreakdownStatus.REJECTED, cnbcRow.status)
        assertEquals(EnStrings.exclusionReasonDisagreement, cnbcRow.exclusionReasonText)
    }

    @Test
    fun `oil category pill displays Brent and WTI in USD per barrel with labeled Toman estimate and missing 24h change in EN and FA`() {
        val brent = MarketAsset(
            id = "BRENT",
            symbol = "BRENT",
            nameFa = "نفت خام برنت",
            nameEn = "Brent Crude",
            category = AssetCategory.OIL,
            badgeText = "BRENT",
            accentHex = 0xFF1F2937,
            priceToman = 7_525_000.0,
            priceUsd = 75.25,
            change24hPercent = 1.25,
            sourceLabel = "Calculated Avg (Yahoo Finance+CNBC)",
            updatedAtMillis = fixedNowMillis,
            freshness = DataFreshness.LIVE,
            sourceTimestampMillis = fixedNowMillis - 60_000L,
            fetchedAtMillis = fixedNowMillis,
            isCalculatedPrice = true,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
            isChange24hAvailable = true,
            change24hUsd = 0.93
        )
        val wtiNoChange = MarketAsset(
            id = "WTI",
            symbol = "WTI",
            nameFa = "نفت خام WTI",
            nameEn = "WTI Crude",
            category = AssetCategory.OIL,
            badgeText = "WTI",
            accentHex = 0xFF334155,
            priceToman = 0.0,
            priceUsd = 70.45,
            change24hPercent = 0.0,
            sourceLabel = "CNBC",
            updatedAtMillis = fixedNowMillis,
            freshness = DataFreshness.DELAYED,
            sourceTimestampMillis = fixedNowMillis - 45L * 60_000L,
            fetchedAtMillis = fixedNowMillis,
            singleSource = true,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
            isChange24hAvailable = false,
            change24hUsd = null
        )

        var language by mutableStateOf(AppLanguage.EN)
        var capturedDir: LayoutDirection? = null

        composeRule.setContent {
            val prefs = AppPreferences(
                themeMode = ThemeMode.AMOLED,
                language = language,
                usePersianDigits = (language == AppLanguage.FA),
                isGridView = true
            )
            val strings = if (language == AppLanguage.FA) FaStrings else EnStrings
            var detailAsset by remember { mutableStateOf<MarketAsset?>(null) }
            var breakdownAsset by remember { mutableStateOf<MarketAsset?>(null) }

            ChandTheme(themeMode = prefs.themeMode, language = prefs.language) {
                capturedDir = LocalLayoutDirection.current
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = listOf(brent, wtiNoChange),
                        lastUpdatedMillis = fixedNowMillis,
                        freshness = DataFreshness.LIVE
                    ),
                    filteredAssets = listOf(brent, wtiNoChange),
                    prefs = prefs,
                    strings = strings,
                    selectedCategory = AssetCategory.OIL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = { detailAsset = it },
                    onAssetPriceClick = { breakdownAsset = it },
                    onRefreshClick = {}
                )

                detailAsset?.let { asset ->
                    AssetDetailSheet(
                        asset = asset,
                        isPinned = false,
                        historyPoints = emptyList(),
                        historySourceLabel = "",
                        isHistoryLoading = false,
                        selectedRange = TimeRange.H24,
                        prefs = prefs,
                        strings = strings,
                        onSelectRange = {},
                        onRetryHistory = {},
                        onTogglePin = {},
                        onConvertClick = {},
                        onSourceBreakdownClick = { breakdownAsset = asset },
                        onDismiss = { detailAsset = null }
                    )
                }

                breakdownAsset?.let { asset ->
                    MarketSourceBreakdownSheet(
                        asset = asset,
                        prefs = prefs,
                        strings = strings,
                        onDismiss = { breakdownAsset = null },
                        referenceNowMillis = fixedNowMillis
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Ltr, capturedDir)
        composeRule.onNodeWithTag("category_OIL").assertIsDisplayed()
        composeRule.onNodeWithTag("asset_row_BRENT").assertIsDisplayed()
        composeRule.onNodeWithTag("asset_row_WTI").assertIsDisplayed()
        composeRule.onNodeWithTag("asset_price_BRENT").assertTextContains("$75.25", substring = true)
        composeRule.onNodeWithTag("asset_price_BRENT").assertTextContains(EnStrings.oilTomanEstimatePrefix, substring = true)
        composeRule.onNodeWithTag("asset_price_WTI").assertTextContains("$70.45", substring = true)
        composeRule.onNodeWithTag("delta_chip_WTI", useUnmergedTree = true).assertDoesNotExist()

        // Switch to Persian RTL feed and verify localized Oil category + card labels
        language = AppLanguage.FA
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, capturedDir)
        composeRule.onNodeWithTag("category_OIL").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(FaStrings.categoryOil).assertIsDisplayed()
        composeRule.onNodeWithTag("delta_chip_WTI", useUnmergedTree = true).assertDoesNotExist()

        // Switch back to EN and tap top header area of Brent card to open AssetDetailSheet -> tap hero price for breakdown sheet
        language = AppLanguage.EN
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("asset_row_BRENT").performTouchInput {
            click(Offset(centerX, 24f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_hero_price_trigger")
            .assertIsDisplayed()
            .assertTextContains(EnStrings.usdPerBarrelUnit, substring = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_breakdown_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("source_breakdown_main_price").assertTextContains("$75.25", substring = true)
    }
}
