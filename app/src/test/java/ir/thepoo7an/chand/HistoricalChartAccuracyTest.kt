package ir.thepoo7an.chand

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.network.HistoryNormalizer.ChartDisplayMode
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.components.findNearestTimestampIndex
import ir.thepoo7an.chand.ui.components.formatChartAxisPrice
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class HistoricalChartAccuracyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleAsset(
        id: String = "USD",
        symbol: String = "USD",
        category: AssetCategory = AssetCategory.FIAT,
        priceToman: Double = 90_000.0
    ) = MarketAsset(
        id = id,
        symbol = symbol,
        nameFa = symbol,
        nameEn = symbol,
        category = category,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        change24hPercent = 2.0,
        change24hToman = 1800.0,
        freshness = DataFreshness.LIVE,
        updatedAtMillis = 1_750_000_000_000L
    )

    @Test
    fun `prepareChartSeries sorts deduplicates segments gaps and avoids synthetic fill when below 2 points`() {
        val baseTs = 1_750_000_000L
        val raw = listOf(
            AssetHistoryPoint(baseTs + 7200L, 91_200.0),
            AssetHistoryPoint(baseTs, 90_000.0),
            AssetHistoryPoint(baseTs, 90_100.0), // duplicate ts -> latest wins
            AssetHistoryPoint(baseTs + 3600L, 90_500.0),
            AssetHistoryPoint(baseTs + 3600L * 8L, 92_000.0), // gap > 2h threshold for 24H
            AssetHistoryPoint(baseTs + 3600L * 9L, 92_400.0),
            AssetHistoryPoint(baseTs + 3600L * 10L, -50.0) // invalid <= 0 rejected
        )

        val prepared = HistoryNormalizer.prepareChartSeries(
            points = raw,
            range = TimeRange.H24,
            nowSec = baseTs + 3600L * 10L,
            applyRangeCutoff = true,
            removeSpikes = false
        )

        assertTrue(prepared.hasSufficientHistory)
        assertEquals(5, prepared.observationCount)
        assertEquals(listOf(90_100.0, 90_500.0, 91_200.0, 92_000.0, 92_400.0), prepared.points.map { it.priceToman })
        assertTrue(prepared.hasGaps)
        assertEquals(2, prepared.segments.size)
        assertEquals(3, prepared.segments[0].size)
        assertEquals(2, prepared.segments[1].size)
        assertEquals(1, prepared.gaps.size)
        assertEquals(baseTs + 7200L, prepared.gaps.first().afterTimestampSec)
        assertEquals(baseTs + 3600L * 8L, prepared.gaps.first().beforeTimestampSec)

        // Single valid point must NOT fabricate sufficient history
        val singlePointSeries = HistoryNormalizer.prepareChartSeries(
            points = listOf(AssetHistoryPoint(baseTs, 90_000.0)),
            range = TimeRange.H24,
            nowSec = baseTs + 100L,
            applyRangeCutoff = true
        )
        assertFalse(singlePointSeries.hasSufficientHistory)
        assertEquals(1, singlePointSeries.observationCount)
    }

    @Test
    fun `findNearestTimestampIndex uses proportional timestamps rather than uniform spacing`() {
        val points = listOf(
            AssetHistoryPoint(1_000L, 100.0),
            AssetHistoryPoint(1_100L, 102.0),
            AssetHistoryPoint(2_000L, 110.0)
        )
        // Touch at 35% width -> timestamp = 1350 -> nearest to t=1100 (index 1, dist=250 vs index 2 dist=650)
        assertEquals(
            1,
            findNearestTimestampIndex(
                touchX = 35f,
                widthPx = 100f,
                horizontalPadPx = 0f,
                points = points,
                minTs = 1_000L,
                maxTs = 2_000L
            )
        )
        // Touch at 80% width -> timestamp = 1800 -> nearest to t=2000 (index 2)
        assertEquals(
            2,
            findNearestTimestampIndex(
                touchX = 80f,
                widthPx = 100f,
                horizontalPadPx = 0f,
                points = points,
                minTs = 1_000L,
                maxTs = 2_000L
            )
        )

        // Y-axis formatter respects units & Persian digits
        assertEquals("90,000", formatChartAxisPrice(90_000.0, isUsdPerBarrel = false, usePersianDigits = false))
        assertEquals("$74.5", formatChartAxisPrice(74.5, isUsdPerBarrel = true, usePersianDigits = false))
    }

    @Test
    fun `AssetDetailSheet switches between Line and Bar mode and shows gap badge and honest insufficient history state`() {
        val baseTs = 1_750_000_000L
        val gappedHistory = listOf(
            AssetHistoryPoint(baseTs, 88_000.0),
            AssetHistoryPoint(baseTs + 3600L, 88_500.0),
            AssetHistoryPoint(baseTs + 3600L * 10L, 90_000.0),
            AssetHistoryPoint(baseTs + 3600L * 11L, 90_400.0)
        )
        val prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false)

        composeRule.setContent {
            ChandTheme(themeMode = ThemeMode.AMOLED, language = AppLanguage.EN) {
                AssetDetailSheet(
                    asset = sampleAsset(),
                    isPinned = false,
                    historyPoints = gappedHistory,
                    historySourceLabel = "Wallex",
                    isHistoryLoading = false,
                    selectedRange = TimeRange.H24,
                    prefs = prefs,
                    strings = EnStrings,
                    onSelectRange = {},
                    onRetryHistory = {},
                    onTogglePin = {},
                    onConvertClick = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("chart_metadata_label").assertExists()
        composeRule.onNodeWithTag("chart_gap_badge").assertExists()
        composeRule.onNodeWithTag("history_chart_${ChartDisplayMode.LINE.name}").assertExists()
        composeRule.onNodeWithTag("chart_mode_bar").performClick()
        composeRule.onNodeWithTag("history_chart_${ChartDisplayMode.BAR.name}").assertExists()
        composeRule.onNodeWithText("Period Change (24H)").assertExists()
    }

    @Test
    fun `AssetDetailSheet renders unavailable empty state when fewer than 2 valid history points exist`() {
        val prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false)
        composeRule.setContent {
            ChandTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                AssetDetailSheet(
                    asset = sampleAsset(),
                    isPinned = false,
                    historyPoints = listOf(AssetHistoryPoint(1_750_000_000L, 90_000.0)),
                    historySourceLabel = "",
                    isHistoryLoading = false,
                    selectedRange = TimeRange.H24,
                    prefs = prefs,
                    strings = EnStrings,
                    onSelectRange = {},
                    onRetryHistory = {},
                    onTogglePin = {},
                    onConvertClick = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("history_unavailable_state").assertExists()
        composeRule.onNodeWithText(EnStrings.historyUnavailable).assertExists()
    }

    @Test
    fun `fetchAssetHistory preserves historical accuracy without silent scalePointsToCurrent distortion for Gold`() = runTest {
        val nowSec = System.currentTimeMillis() / 1000L
        val t0Sec = nowSec - 10_800L
        val t1Sec = nowSec - 7_200L
        val t2Sec = nowSec - 3_600L
        val t3Sec = nowSec - 30L

        val binanceKlinesPayload = """
            [
              [${t0Sec * 1000L},"2600.0","2600.0","2600.0","2600.0","1",${t0Sec * 1000L}],
              [${t1Sec * 1000L},"2630.0","2630.0","2630.0","2630.0","1",${t1Sec * 1000L}],
              [${t2Sec * 1000L},"2660.0","2660.0","2660.0","2660.0","1",${t2Sec * 1000L}],
              [${t3Sec * 1000L},"2700.0","2700.0","2700.0","2700.0","1",${t3Sec * 1000L}]
            ]
        """.trimIndent()
        val wallexUsdPayload = """
            {"s":"ok","t":[$t0Sec,$t1Sec,$t2Sec,$t3Sec],"c":["80000","80000","80000","80000"]}
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val body = when {
                    req.url.host.contains("binance.com") -> binanceKlinesPayload
                    req.url.host.contains("wallex.ir") -> wallexUsdPayload
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

        // Intentionally pass an inflated current priceToman (9,999,999) — real historical calculation
        // must NOT rescale historical points to force them toward 9,999,999!
        val gold18Asset = sampleAsset(
            id = "GOLD_18K",
            symbol = "18K",
            category = AssetCategory.GOLD,
            priceToman = 9_999_999.0
        )
        val result = ds.fetchAssetHistory(gold18Asset, TimeRange.H24)
        assertTrue(result.isRealData)
        assertTrue(result.points.size >= 4)

        val gram18PerTroyOz = (1.0 / MarketValidationConfig.TROY_OUNCE_GRAMS) *
            (MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0)
        val expected0 = (2600.0 * gram18PerTroyOz) * 80_000.0
        val expected1 = (2630.0 * gram18PerTroyOz) * 80_000.0
        val expected2 = (2660.0 * gram18PerTroyOz) * 80_000.0
        val expected3 = (2700.0 * gram18PerTroyOz) * 80_000.0
        assertEquals(expected0, result.points[0].priceToman, 5.0)
        assertEquals(expected1, result.points[1].priceToman, 5.0)
        assertEquals(expected2, result.points[2].priceToman, 5.0)
        assertEquals(expected3, result.points[3].priceToman, 5.0)
    }
}
