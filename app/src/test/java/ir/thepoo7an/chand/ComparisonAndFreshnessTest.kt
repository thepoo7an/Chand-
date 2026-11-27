package ir.thepoo7an.chand

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.network.FiatPriceMapper
import ir.thepoo7an.chand.network.FreshnessAgeTier
import ir.thepoo7an.chand.network.FreshnessTimeFormatter
import ir.thepoo7an.chand.network.GoldPriceMapper
import ir.thepoo7an.chand.network.MarketComparisonHelper
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownBuilder
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownSheet
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.theme.ChandTheme
import ir.thepoo7an.chand.widget.ChandCompact2x1Widget
import ir.thepoo7an.chand.widget.ChandCompactCryptoWidget
import ir.thepoo7an.chand.widget.ChandLockScreenWidget
import ir.thepoo7an.chand.widget.ChandSingleAssetWidget
import ir.thepoo7an.chand.widget.ChandWideListWidget
import ir.thepoo7an.chand.widget.WidgetStatusHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
// Robolectric default surface is 320x470dp, far smaller than any real phone; Compose UI
// assertions such as `assertIsDisplayed` failed for bottom-sheet rows and card chips that
// are visible on an actual device. Render at a realistic 411x891dp viewport instead.
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = ChandApplication::class)
class ComparisonAndFreshnessTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object TestGlanceId : GlanceId

    @Test
    fun `MarketComparisonHelper flags spread over 0_5 percent and respects enabled asset IDs`() {
        val now = 1_750_000_000_000L
        val divergentUsd = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = 100_000.0,
            freshness = DataFreshness.LIVE,
            updatedAtMillis = now,
            fetchedAtMillis = now,
            candidates = listOf(
                SourceCandidateRecord("TGJU", priceToman = 100_000.0, isPrimary = true, status = CandidateStatus.INCLUDED),
                SourceCandidateRecord("Wallex", priceToman = 100_800.0, isPrimary = false, status = CandidateStatus.INCLUDED)
            )
        )

        val summary = MarketComparisonHelper.buildComparison(divergentUsd, setOf("USD", "USDT"))
        assertNotNull(summary)
        assertEquals(800.0, summary!!.deltaToman, 0.01)
        assertEquals(0.8, summary.deltaPct, 0.01)
        assertTrue(summary.isDivergent)

        // Spread <= 0.5% is not divergent
        val tightUsd = divergentUsd.copy(
            candidates = listOf(
                SourceCandidateRecord("TGJU", priceToman = 100_000.0, isPrimary = true),
                SourceCandidateRecord("Wallex", priceToman = 100_200.0, isPrimary = false)
            )
        )
        val tightSummary = MarketComparisonHelper.buildComparison(tightUsd, setOf("USD"))
        assertNotNull(tightSummary)
        assertFalse(tightSummary!!.isDivergent)

        // Disabled asset returns null
        assertNull(MarketComparisonHelper.buildComparison(divergentUsd, setOf("USDT")))
    }

    @Test
    fun `FiatPriceMapper retains individual Wallex Bitpin Nobitex candidates alongside TGJU USD`() {
        val now = 1_750_000_000_000L
        val resolved = FiatPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = MarketPriceMapper.TgjuQuote(
                priceToman = 115_000.0,
                openToman = null,
                changeToman = 100.0,
                changePct = 0.1,
                timestampMillis = now - 60_000L
            ),
            wallexUsdt = MarketPriceMapper.ExchangeQuote(
                priceToman = 115_400.0,
                priceUsd = null,
                changePct = 0.0,
                timestampMillis = now - 30_000L
            ),
            bitpinUsdt = MarketPriceMapper.ExchangeQuote(
                priceToman = 115_250.0,
                priceUsd = null,
                changePct = 0.0,
                timestampMillis = now - 30_000L
            ),
            nobitexUsdtToman = 115_300.0,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = null,
            nowMillis = now,
            nobitexTimestampMillis = now - 30_000L
        )
        val names = resolved.usdCandidates.map { it.sourceName }.toSet()
        assertTrue(names.contains("TGJU"))
        assertTrue(names.contains("Wallex"))
        assertTrue(names.contains("Bitpin"))
        assertTrue(names.contains("Nobitex"))
        assertEquals(115_000.0, resolved.usdToman, 0.01)
    }

    @Test
    fun `GoldPriceMapper appends global XAU+USD benchmark candidate alongside TGJU for GOLD_18K`() {
        val now = 1_750_000_000_000L
        val tgju = mapOf(
            "geram18" to MarketPriceMapper.TgjuQuote(
                priceToman = 5_800_000.0,
                openToman = null,
                changeToman = 20_000.0,
                changePct = 0.4,
                timestampMillis = now - 60_000L
            )
        )
        val spot = listOf(
            MarketPriceMapper.PriceCandidate(
                sourceName = "GoldAPI",
                price = 2650.0,
                isPrimary = true,
                timestampMillis = now - 60_000L
            )
        )
        val goldAssets = GoldPriceMapper.mapGoldAssets(
            tgju = tgju,
            usdToman = 90_000.0,
            usdChangePct = 0.0,
            usdBasisFreshness = DataFreshness.LIVE,
            usdSourceTimestampMillis = now - 60_000L,
            goldSpotCandidates = spot,
            paxgQuote = null,
            tgjuGoldRecent = emptyList(),
            goldSpark = emptyList(),
            usdtSpark = emptyList(),
            cachedAssetsById = emptyMap(),
            nowMillis = now
        )
        val gram18 = goldAssets.first { it.id == "GOLD_18K" }
        assertEquals(5_800_000.0, gram18.priceToman, 0.01)
        assertFalse(gram18.isCalculatedPrice)
        assertTrue(gram18.candidates.any { it.sourceName == "TGJU" && it.isPrimary })
        assertTrue(gram18.candidates.any { it.sourceName.contains("GoldAPI") && it.sourceName.contains("USD") })
    }

    @Test
    fun `FreshnessTimeFormatter classifies 30m amber and 2h red tiers accurately`() {
        val now = 1_750_000_000_000L
        assertEquals(FreshnessAgeTier.NORMAL, FreshnessTimeFormatter.resolveAgeTier(now - 5 * 60_000L, now))
        assertEquals(FreshnessAgeTier.NORMAL, FreshnessTimeFormatter.resolveAgeTier(now - 30 * 60_000L, now))
        assertEquals(FreshnessAgeTier.STALE_AMBER, FreshnessTimeFormatter.resolveAgeTier(now - 31 * 60_000L, now))
        assertEquals(FreshnessAgeTier.STALE_AMBER, FreshnessTimeFormatter.resolveAgeTier(now - 120 * 60_000L, now))
        assertEquals(FreshnessAgeTier.CRITICAL_RED, FreshnessTimeFormatter.resolveAgeTier(now - 121 * 60_000L, now))
        assertEquals(FreshnessAgeTier.UNAVAILABLE, FreshnessTimeFormatter.resolveAgeTier(now - 5 * 60_000L, now, isUnavailable = true))
        assertEquals(FreshnessAgeTier.UNAVAILABLE, FreshnessTimeFormatter.resolveAgeTier(0L, now))

        val label = FreshnessTimeFormatter.formatRelativeAgeLabel(
            lastFetchMillis = now - 45 * 60_000L,
            nowMillis = now,
            strings = EnStrings,
            usePersianDigits = false
        )
        assertTrue("Expected 45m ago in '$label'", label.contains("45m ago"))
    }

    @Test
    fun `MarketSourceBreakdownSheet displays separate Source Timestamp and Fetched At timestamps`() {
        val now = 1_750_000_000_000L
        val asset = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = 115_000.0,
            freshness = DataFreshness.LIVE,
            sourceTimestampMillis = now - 120_000L,
            fetchedAtMillis = now - 10_000L,
            updatedAtMillis = now - 10_000L,
            candidates = listOf(
                SourceCandidateRecord(
                    sourceName = "TGJU",
                    priceToman = 115_000.0,
                    sourceTimestampMillis = 0L, // unknown source timestamp
                    fetchedAtMillis = now - 10_000L,
                    isPrimary = true,
                    status = CandidateStatus.INCLUDED
                ),
                SourceCandidateRecord(
                    sourceName = "Wallex",
                    priceToman = 115_700.0,
                    sourceTimestampMillis = now - 60_000L,
                    fetchedAtMillis = now - 10_000L,
                    isPrimary = false,
                    status = CandidateStatus.REJECTED,
                    exclusionReason = CandidateExclusionReason.EXCESSIVE_DISAGREEMENT
                )
            )
        )

        val breakdown = MarketSourceBreakdownBuilder.build(
            asset = asset,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = now
        )
        assertFalse(breakdown.rows.first { it.sourceName == "TGJU" }.hasValidTimestamp)
        assertTrue(breakdown.rows.first { it.sourceName == "TGJU" }.hasValidFetchedAt)

        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                MarketSourceBreakdownSheet(
                    asset = asset,
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    onDismiss = {},
                    referenceNowMillis = now
                )
            }
        }
        composeRule.onNodeWithTag("source_fetched_at_TGJU").assertIsDisplayed()
        composeRule.onNodeWithTag("source_fetched_at_Wallex").assertIsDisplayed()
    }

    @Test
    fun `MarketFeedScreen renders freshness chip and clickable comparison badge`() {
        val now = 1_750_000_000_000L
        val usd = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = 115_000.0,
            freshness = DataFreshness.LIVE,
            updatedAtMillis = now - 40 * 60_000L,
            fetchedAtMillis = now - 40 * 60_000L,
            candidates = listOf(
                SourceCandidateRecord("TGJU", priceToman = 115_000.0, isPrimary = true),
                SourceCandidateRecord("Wallex", priceToman = 116_000.0, isPrimary = false)
            )
        )
        var openedPriceAsset: MarketAsset? = null
        composeRule.setContent {
            ChandTheme(language = AppLanguage.EN) {
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = listOf(usd),
                        lastUpdatedMillis = now - 40 * 60_000L,
                        sourceSummary = "TGJU",
                        freshness = DataFreshness.LIVE,
                        isRefreshing = false
                    ),
                    filteredAssets = listOf(usd),
                    prefs = AppPreferences(language = AppLanguage.EN, usePersianDigits = false),
                    strings = EnStrings,
                    selectedCategory = AssetCategory.ALL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = {},
                    onAssetPriceClick = { openedPriceAsset = it },
                    onRefreshClick = {},
                    referenceNowMillis = now
                )
            }
        }

        // The chip and badge sit inside the card's merged (clickable) semantics node, so they are
        // only addressable through the unmerged tree.
        composeRule.onNodeWithTag("freshness_chip_USD", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("comparison_badge_USD", useUnmergedTree = true)
            .assertExists()
            // Invoke the click semantics directly: touch injection is unreliable for nodes that
            // the (Robolectric) card layout clips, and the wiring is what this test covers.
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("USD", openedPriceAsset?.id)
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun `WidgetStatusHelper preserves core formatters, computes age tiers, and composes all 5 widgets`() = runBlocking {
        val now = 1_750_000_000_000L
        val staleAsset = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = 115_000.0,
            freshness = DataFreshness.LIVE,
            updatedAtMillis = now - 45 * 60_000L,
            fetchedAtMillis = now - 45 * 60_000L
        )

        assertEquals(
            FreshnessAgeTier.STALE_AMBER,
            WidgetStatusHelper.resolveFreshnessAgeTier(listOf(staleAsset), staleAsset.updatedAtMillis, now)
        )
        val statusWithAge = WidgetStatusHelper.formatWidgetStatusWithAge(
            freshness = DataFreshness.CACHED,
            displayedAssets = listOf(staleAsset),
            updatedAtMillis = staleAsset.updatedAtMillis,
            language = AppLanguage.EN,
            usePersianDigits = false,
            nowMillis = now
        )
        assertTrue("Expected 45m ago in '$statusWithAge'", statusWithAge.contains("45m ago"))

        // Compose all 5 widgets
        val context = ApplicationProvider.getApplicationContext<Context>()
        val utilsClass = Class.forName("androidx.glance.appwidget.AppWidgetUtilsKt")
        val runGlanceMethod = utilsClass.getDeclaredMethod(
            "runGlance",
            GlanceAppWidget::class.java,
            Context::class.java,
            GlanceId::class.java
        )
        val widgets = listOf(
            ChandSingleAssetWidget() to DpSize(180.dp, 180.dp),
            ChandWideListWidget() to DpSize(320.dp, 180.dp),
            ChandCompactCryptoWidget() to DpSize(180.dp, 180.dp),
            ChandCompact2x1Widget() to DpSize(220.dp, 110.dp),
            ChandLockScreenWidget() to DpSize(260.dp, 100.dp)
        )
        widgets.forEach { (widget, size) ->
            val flow = runGlanceMethod.invoke(null, widget, context, TestGlanceId) as Flow<(@Composable () -> Unit)?>
            val content = flow.first()
            assertNotNull(content)
            val rv = GlanceRemoteViews().compose(context = context, size = size, content = content!!).remoteViews
            assertTrue(rv.layoutId != 0)
        }
    }
}
