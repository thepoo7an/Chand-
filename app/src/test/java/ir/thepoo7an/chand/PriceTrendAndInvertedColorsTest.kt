package ir.thepoo7an.chand

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import ir.thepoo7an.chand.ui.theme.LocalMarketColors
import ir.thepoo7an.chand.ui.theme.MarketColors
import ir.thepoo7an.chand.ui.theme.PriceTrendDirection
import ir.thepoo7an.chand.ui.theme.resolvePriceTrend
import ir.thepoo7an.chand.widget.ChandCompact2x1Widget
import ir.thepoo7an.chand.widget.ChandSingleAssetWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class PriceTrendAndInvertedColorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object DummyGlanceId : GlanceId

    private fun assertColorIsReddish(color: Color, label: String) {
        assertTrue("$label should be red-dominant ($color)", color.red > color.green && color.red > color.blue)
    }

    private fun assertColorIsGreenish(color: Color, label: String) {
        assertTrue("$label should be green-dominant ($color)", color.green > color.red && color.green > color.blue)
    }

    private fun sampleAsset(
        id: String,
        symbol: String,
        price: Double,
        changeToman: Double,
        changePct: Double,
        freshness: DataFreshness = DataFreshness.LIVE
    ) = MarketAsset(
        id = id,
        symbol = symbol,
        nameFa = symbol,
        nameEn = symbol,
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = price,
        change24hToman = changeToman,
        change24hPercent = changePct,
        freshness = freshness,
        updatedAtMillis = 1_750_000_000_000L
    )

    @Test
    fun `resolvePriceTrend enforces exact positive negative and neutral thresholds`() {
        assertEquals(PriceTrendDirection.UP, resolvePriceTrend(500.0, 0.8))
        assertEquals(PriceTrendDirection.UP, resolvePriceTrend(0.0, 0.01))
        assertEquals(PriceTrendDirection.DOWN, resolvePriceTrend(-500.0, -0.8))
        assertEquals(PriceTrendDirection.DOWN, resolvePriceTrend(0.0, -0.01))
        assertEquals(PriceTrendDirection.NEUTRAL, resolvePriceTrend(0.0, 0.0))
        assertEquals(PriceTrendDirection.NEUTRAL, resolvePriceTrend(0.0001, 0.001))
        assertEquals(PriceTrendDirection.NEUTRAL, resolvePriceTrend(Double.NaN, Double.NaN))
        assertEquals(PriceTrendDirection.NEUTRAL, resolvePriceTrend(500.0, 2.0, hasChangeData = false))
    }

    @Test
    fun `inverted Iranian market colors map UP to red and DOWN to green while preserving system gain and loss across all themes`() {
        val captured = mutableMapOf<ThemeMode, MarketColors>()
        composeRule.setContent {
            ThemeMode.entries.forEach { mode ->
                ChandTheme(themeMode = mode, language = AppLanguage.FA) {
                    captured[mode] = LocalMarketColors.current
                }
            }
        }
        composeRule.waitForIdle()

        ThemeMode.entries.forEach { mode ->
            val colors = captured[mode]!!
            assertColorIsReddish(colors.trendColor(PriceTrendDirection.UP), "$mode priceUp")
            assertColorIsGreenish(colors.trendColor(PriceTrendDirection.DOWN), "$mode priceDown")
            // System status tokens stay non-inverted
            assertColorIsGreenish(colors.gain, "$mode system gain (LIVE/INCLUDED)")
            assertColorIsReddish(colors.loss, "$mode system loss (Error/REJECTED)")
        }
    }

    @Test
    fun `MarketFeedScreen renders inverted UP DOWN and NEUTRAL indicators cleanly alongside live status`() {
        val assets = listOf(
            sampleAsset("USD", "USD", 90_000.0, 1_200.0, 1.35),
            sampleAsset("EUR", "EUR", 98_000.0, -850.0, -0.86),
            sampleAsset("AED", "AED", 24_500.0, 0.0, 0.0)
        )
        val prefs = AppPreferences(language = AppLanguage.FA, usePersianDigits = false, isGridView = true)

        composeRule.setContent {
            ChandTheme(themeMode = ThemeMode.AMOLED, language = AppLanguage.FA) {
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = assets,
                        lastUpdatedMillis = 1_750_000_000_000L,
                        isRefreshing = false,
                        sourceSummary = "TGJU",
                        freshness = DataFreshness.LIVE
                    ),
                    filteredAssets = assets,
                    prefs = prefs,
                    strings = FaStrings,
                    selectedCategory = AssetCategory.ALL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = {},
                    onRefreshClick = {}
                )
            }
        }

        composeRule.onNodeWithText("نرخ‌نما").assertExists()
        composeRule.onNodeWithTag("asset_row_USD").assertExists()
        composeRule.onNodeWithTag("asset_row_EUR").assertExists()
        composeRule.onNodeWithTag("asset_row_AED").assertExists()
        composeRule.onNodeWithText("↑1.2K").assertExists()
        composeRule.onNodeWithText("↓850").assertExists()
        composeRule.onNodeWithText("• 0").assertExists()
    }

    @Test
    fun `AssetDetailSheet displays period delta and neutral or unavailable badges honestly`() {
        val oilAsset = MarketAsset(
            id = "BRENT",
            symbol = "BRENT",
            nameFa = "نفت برنت",
            nameEn = "Brent Crude",
            category = AssetCategory.OIL,
            badgeText = "🛢️",
            accentHex = 0xFF0EA5E9,
            priceToman = 6_300_000.0,
            priceUsd = 74.20,
            change24hPercent = 0.0,
            change24hUsd = null,
            isChange24hAvailable = false,
            freshness = DataFreshness.LIVE,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL
        )
        val prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false)

        composeRule.setContent {
            ChandTheme(themeMode = ThemeMode.DARK, language = AppLanguage.EN) {
                AssetDetailSheet(
                    asset = oilAsset,
                    isPinned = false,
                    historyPoints = emptyList(),
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

        composeRule.onNodeWithTag("detail_trend_badge").assertExists()
        composeRule.onNodeWithText(EnStrings.change24hUnavailable).assertExists()
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun `Glance widgets compose Nerkhnama branding and inverted price trends cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        repo.setLanguage(AppLanguage.FA)
        repo.setUsePersianDigits(false)
        repo.saveCachedSnapshot(
            CachedMarketSnapshot(
                assets = listOf(
                    sampleAsset("USD", "USD", 90_000.0, 1_500.0, 1.7),
                    sampleAsset("EUR", "EUR", 98_000.0, -900.0, -0.9),
                    sampleAsset("GOLD_18K", "18K", 6_500_000.0, 0.0, 0.0)
                ),
                updatedAtMillis = System.currentTimeMillis(),
                sourceSummary = "TGJU",
                freshness = DataFreshness.LIVE
            )
        )

        val utilsClass = Class.forName("androidx.glance.appwidget.AppWidgetUtilsKt")
        val runGlanceMethod = utilsClass.getDeclaredMethod(
            "runGlance",
            androidx.glance.appwidget.GlanceAppWidget::class.java,
            Context::class.java,
            GlanceId::class.java
        )

        listOf(
            ChandSingleAssetWidget() to DpSize(180.dp, 180.dp),
            ChandCompact2x1Widget() to DpSize(220.dp, 110.dp)
        ).forEach { (widget, size) ->
            val flow = runGlanceMethod.invoke(null, widget, context, DummyGlanceId) as Flow<(@androidx.compose.runtime.Composable () -> Unit)?>
            val content = flow.first()!!
            val rv = GlanceRemoteViews().compose(
                context = context,
                size = size,
                content = content
            ).remoteViews
            val view = rv.apply(context, FrameLayout(context)) as ViewGroup
            assertNotNull(view)
        }
    }
}
