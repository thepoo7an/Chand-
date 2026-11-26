package ir.thepoo7an.chand

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownBuilder
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownContent
import ir.thepoo7an.chand.ui.components.MarketSourceBreakdownSheet
import ir.thepoo7an.chand.ui.components.SourceBreakdownStatus
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.ChandTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class Stage2SourceBreakdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    private val fixedNowMillis = 1_795_176_000_000L // 2026-11-20 12:00:00 UTC

    private fun buildMixedUsdAsset(): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = 115_200.0,
        priceUsd = 1.0,
        change24hPercent = 0.5,
        change24hToman = 500.0,
        sourceLabel = "Calculated Avg (Wallex+Bitpin) (Free)",
        updatedAtMillis = fixedNowMillis,
        freshness = DataFreshness.LIVE,
        sourceTimestampMillis = fixedNowMillis - 30_000L,
        fetchedAtMillis = fixedNowMillis,
        disputed = false,
        singleSource = false,
        isCalculatedPrice = true,
        candidates = listOf(
            SourceCandidateRecord(
                sourceName = "Wallex",
                priceToman = 115_000.0,
                rawPrice = 115_000.0,
                sourceTimestampMillis = fixedNowMillis - 20_000L,
                isPrimary = true,
                status = CandidateStatus.INCLUDED,
                exclusionReason = null
            ),
            SourceCandidateRecord(
                sourceName = "Bitpin",
                priceToman = 115_400.0,
                rawPrice = 115_400.0,
                sourceTimestampMillis = fixedNowMillis - 30_000L,
                isPrimary = false,
                status = CandidateStatus.INCLUDED,
                exclusionReason = null
            ),
            SourceCandidateRecord(
                sourceName = "DelayedDesk",
                priceToman = 115_250.0,
                rawPrice = 115_250.0,
                sourceTimestampMillis = fixedNowMillis - 90L * 60_000L,
                isPrimary = false,
                status = CandidateStatus.INCLUDED,
                exclusionReason = null
            ),
            SourceCandidateRecord(
                sourceName = "Nobitex",
                priceToman = 128_000.0,
                rawPrice = 128_000.0,
                sourceTimestampMillis = fixedNowMillis - 15_000L,
                isPrimary = false,
                status = CandidateStatus.REJECTED,
                exclusionReason = CandidateExclusionReason.EXCESSIVE_DISAGREEMENT
            ),
            SourceCandidateRecord(
                sourceName = "StaleFeed",
                priceToman = 114_900.0,
                rawPrice = 114_900.0,
                sourceTimestampMillis = fixedNowMillis - 4L * 3600_000L,
                isPrimary = false,
                status = CandidateStatus.REJECTED,
                exclusionReason = CandidateExclusionReason.STALE
            ),
            SourceCandidateRecord(
                sourceName = "BadQuote",
                priceToman = null,
                rawPrice = -5.0,
                sourceTimestampMillis = 0L,
                isPrimary = false,
                status = CandidateStatus.REJECTED,
                exclusionReason = CandidateExclusionReason.INVALID
            ),
            SourceCandidateRecord(
                sourceName = "TGJU",
                priceToman = null,
                rawPrice = null,
                sourceTimestampMillis = 0L,
                isPrimary = false,
                status = CandidateStatus.REJECTED,
                exclusionReason = CandidateExclusionReason.PROVIDER_FAILURE
            )
        )
    )

    // -------------------------------------------------------------------------
    // Unit Tests: Consistency, Inclusion/Exclusion, Missing/Stale, Cached
    // -------------------------------------------------------------------------

    @Test
    fun `breakdown model matches main calculated average price from MarketPriceMapper exactly`() {
        val resolved = MarketPriceMapper.resolveFreeMarketUsd(
            tgjuUsdQuote = null,
            wallexUsdt = MarketPriceMapper.ExchangeQuote(priceToman = 115_000.0, priceUsd = 1.0, changePct = 0.5, timestampMillis = fixedNowMillis - 20_000L),
            bitpinUsdt = MarketPriceMapper.ExchangeQuote(priceToman = 115_400.0, priceUsd = 1.0, changePct = 0.4, timestampMillis = fixedNowMillis - 30_000L),
            nobitexUsdtToman = 135_000.0,
            usdtSpark = emptyList(),
            tgjuTableFallbackDelta = null,
            lastKnownUsdToman = null,
            nowMillis = fixedNowMillis,
            nobitexTimestampMillis = fixedNowMillis - 25_000L,
            tgjuFailed = true
        )

        val usdAsset = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = resolved.usdToman,
            priceUsd = 1.0,
            sourceLabel = resolved.usdSourceLabel,
            updatedAtMillis = fixedNowMillis,
            freshness = resolved.usdFreshness,
            sourceTimestampMillis = resolved.usdSourceTimestampMillis,
            fetchedAtMillis = fixedNowMillis,
            disputed = resolved.usdDisputed,
            singleSource = resolved.usdSingleSource,
            isCalculatedPrice = resolved.usdIsCalculated,
            candidates = resolved.usdCandidates
        )

        val model = MarketSourceBreakdownBuilder.build(
            asset = usdAsset,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = fixedNowMillis
        )

        assertEquals(usdAsset.priceToman, model.mainPriceToman, 0.0001)
        assertEquals(
            NumberFormatters.formatCardPrice(usdAsset.priceToman, usePersianDigits = false),
            model.mainCardPriceText
        )
        assertEquals("115,400", model.mainPriceText)
        assertTrue(model.isCalculatedPrice)
        assertEquals(EnStrings.calculatedMainPriceLabel, model.priceKindLabel)

        val wallex = model.rows.first { it.sourceName == "Wallex" }
        val bitpin = model.rows.first { it.sourceName == "Bitpin" }
        val nobitex = model.rows.first { it.sourceName == "Nobitex" }
        val tgju = model.rows.first { it.sourceName == "TGJU" }

        assertEquals(SourceBreakdownStatus.INCLUDED, wallex.status)
        assertTrue(wallex.contributedToFinal)
        assertEquals(SourceBreakdownStatus.INCLUDED, bitpin.status)
        assertTrue(bitpin.contributedToFinal)
        assertEquals(SourceBreakdownStatus.REJECTED, nobitex.status)
        assertFalse(nobitex.contributedToFinal)
        assertEquals(EnStrings.exclusionReasonDisagreement, nobitex.exclusionReasonText)
        assertEquals(SourceBreakdownStatus.UNAVAILABLE, tgju.status)
        assertFalse(tgju.contributedToFinal)
        assertEquals(EnStrings.exclusionReasonProviderFailure, tgju.exclusionReasonText)
    }

    @Test
    fun `missing and stale timestamps never display 1970 epoch and map to DELAYED or UNAVAILABLE`() {
        val asset = buildMixedUsdAsset().copy(
            updatedAtMillis = 0L,
            fetchedAtMillis = 0L,
            freshness = DataFreshness.DELAYED
        )
        val model = MarketSourceBreakdownBuilder.build(
            asset = asset,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = fixedNowMillis
        )

        assertFalse(model.hasLastSuccessfulUpdate)
        assertEquals(EnStrings.sourceTimestampUnavailable, model.lastSuccessfulUpdateText)
        assertFalse(model.lastSuccessfulUpdateText.contains("1970"))
        assertFalse(model.lastSuccessfulUpdateText.contains("1348"))

        val badQuote = model.rows.first { it.sourceName == "BadQuote" }
        assertEquals(SourceBreakdownStatus.INVALID, badQuote.status)
        assertFalse(badQuote.contributedToFinal)
        assertFalse(badQuote.hasValidTimestamp)
        assertEquals(EnStrings.sourceTimestampUnavailable, badQuote.timestampText)
        assertEquals("—", badQuote.reportedPriceText)
        assertEquals(EnStrings.exclusionReasonInvalid, badQuote.exclusionReasonText)

        val delayedRow = model.rows.first { it.sourceName == "DelayedDesk" }
        assertEquals(SourceBreakdownStatus.DELAYED, delayedRow.status)
        assertTrue(delayedRow.contributedToFinal)
        assertNull(delayedRow.exclusionReasonText)

        val staleRow = model.rows.first { it.sourceName == "StaleFeed" }
        assertEquals(SourceBreakdownStatus.REJECTED, staleRow.status)
        assertFalse(staleRow.contributedToFinal)
        assertEquals(EnStrings.exclusionReasonStale, staleRow.exclusionReasonText)
    }

    @Test
    fun `cached data is never labeled LIVE in EN or FA`() {
        val cachedAsset = buildMixedUsdAsset().copy(freshness = DataFreshness.CACHED)
        val enModel = MarketSourceBreakdownBuilder.build(
            asset = cachedAsset,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = fixedNowMillis
        )
        val faModel = MarketSourceBreakdownBuilder.build(
            asset = cachedAsset,
            isFa = true,
            usePersianDigits = true,
            strings = FaStrings,
            referenceNowMillis = fixedNowMillis
        )

        assertEquals(DataFreshness.CACHED, enModel.freshness)
        assertEquals(EnStrings.cachedBadge, enModel.freshnessLabel)
        assertNotEquals(EnStrings.liveBadge, enModel.freshnessLabel)
        assertEquals(FaStrings.cachedBadge, faModel.freshnessLabel)
        assertNotEquals(FaStrings.liveBadge, faModel.freshnessLabel)
    }

    @Test
    fun `offline reference with no candidates does not fabricate duplicate sources`() {
        val offlineAsset = MarketAsset(
            id = "USD",
            symbol = "USD",
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = 100_000.0,
            freshness = DataFreshness.UNAVAILABLE,
            updatedAtMillis = 0L,
            fetchedAtMillis = 0L,
            candidates = emptyList()
        )
        val model = MarketSourceBreakdownBuilder.build(
            asset = offlineAsset,
            isFa = false,
            usePersianDigits = false,
            strings = EnStrings,
            referenceNowMillis = fixedNowMillis
        )

        assertTrue(model.rows.isEmpty())
        assertEquals("—", model.mainPriceText)
        assertEquals(EnStrings.unavailableBadge, model.freshnessLabel)
    }

    // -------------------------------------------------------------------------
    // UI Tests (Compose + Robolectric)
    // -------------------------------------------------------------------------

    @Test
    fun `tapping main price on MarketFeedScreen opens MarketSourceBreakdownSheet with mixed statuses`() {
        val usdAsset = buildMixedUsdAsset()
        val prefs = AppPreferences(
            themeMode = ThemeMode.AMOLED,
            language = AppLanguage.EN,
            usePersianDigits = false,
            isGridView = true
        )

        composeRule.setContent {
            var selectedBreakdown by remember { mutableStateOf<MarketAsset?>(null) }
            ChandTheme(themeMode = prefs.themeMode, language = prefs.language) {
                MarketFeedScreen(
                    uiState = MarketUiState.Ready(
                        assets = listOf(usdAsset),
                        lastUpdatedMillis = fixedNowMillis,
                        freshness = DataFreshness.LIVE
                    ),
                    filteredAssets = listOf(usdAsset),
                    prefs = prefs,
                    strings = EnStrings,
                    selectedCategory = AssetCategory.ALL,
                    searchQuery = "",
                    onSelectCategory = {},
                    onSearchQueryChange = {},
                    onTogglePin = {},
                    onToggleGridView = {},
                    onAssetClick = {},
                    onAssetPriceClick = { selectedBreakdown = it },
                    onRefreshClick = {}
                )

                selectedBreakdown?.let { asset ->
                    MarketSourceBreakdownSheet(
                        asset = asset,
                        prefs = prefs,
                        strings = EnStrings,
                        onDismiss = { selectedBreakdown = null },
                        referenceNowMillis = fixedNowMillis
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("asset_price_USD").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("source_breakdown_sheet").assertIsDisplayed()
        composeRule.onNodeWithText(EnStrings.sourceBreakdownTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("source_breakdown_main_price").assertTextContains("115,200")
        composeRule.onNodeWithTag("source_status_Wallex").assertTextContains(EnStrings.sourceStatusIncluded)

        composeRule.onNodeWithTag("source_breakdown_list").performScrollToNode(hasTestTag("source_row_DelayedDesk"))
        composeRule.onNodeWithTag("source_status_DelayedDesk").assertTextContains(EnStrings.sourceStatusDelayed)

        composeRule.onNodeWithTag("source_breakdown_list").performScrollToNode(hasTestTag("source_row_Nobitex"))
        composeRule.onNodeWithTag("source_status_Nobitex").assertTextContains(EnStrings.sourceStatusRejected)
        composeRule.onNodeWithText(EnStrings.exclusionReasonDisagreement).assertExists()

        composeRule.onNodeWithTag("source_breakdown_list").performScrollToNode(hasTestTag("source_row_BadQuote"))
        composeRule.onNodeWithTag("source_status_BadQuote").assertTextContains(EnStrings.sourceStatusInvalid)

        composeRule.onNodeWithTag("source_breakdown_list").performScrollToNode(hasTestTag("source_row_TGJU"))
        composeRule.onNodeWithTag("source_status_TGJU").assertTextContains(EnStrings.sourceStatusUnavailable)
        composeRule.onNodeWithText(EnStrings.exclusionReasonProviderFailure).assertExists()
    }

    @Test
    fun `tapping source pill inside AssetDetailSheet triggers source breakdown`() {
        val usdAsset = buildMixedUsdAsset()
        val prefs = AppPreferences(
            themeMode = ThemeMode.DARK,
            language = AppLanguage.EN,
            usePersianDigits = false
        )

        composeRule.setContent {
            var showBreakdown by remember { mutableStateOf(false) }
            ChandTheme(themeMode = prefs.themeMode, language = prefs.language) {
                AssetDetailSheet(
                    asset = usdAsset,
                    isPinned = true,
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
                    onSourceBreakdownClick = { showBreakdown = true },
                    onDismiss = {}
                )

                if (showBreakdown) {
                    MarketSourceBreakdownSheet(
                        asset = usdAsset,
                        prefs = prefs,
                        strings = EnStrings,
                        onDismiss = { showBreakdown = false },
                        referenceNowMillis = fixedNowMillis
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_source_pill_trigger").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("source_breakdown_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("source_breakdown_main_price").assertTextContains("115,200")
    }

    @Test
    fun `source breakdown supports Persian RTL and English LTR layouts and never labels CACHED as LIVE`() {
        val cachedAsset = buildMixedUsdAsset().copy(freshness = DataFreshness.CACHED)
        var language by mutableStateOf(AppLanguage.FA)
        var capturedFaDir: LayoutDirection? = null
        var capturedEnDir: LayoutDirection? = null

        composeRule.setContent {
            ChandTheme(language = language) {
                if (language == AppLanguage.FA) {
                    capturedFaDir = LocalLayoutDirection.current
                    val faModel = MarketSourceBreakdownBuilder.build(
                        asset = cachedAsset,
                        isFa = true,
                        usePersianDigits = true,
                        strings = FaStrings,
                        referenceNowMillis = fixedNowMillis
                    )
                    MarketSourceBreakdownContent(
                        asset = cachedAsset,
                        model = faModel,
                        usePersianDigits = true,
                        strings = FaStrings,
                        onDismiss = {}
                    )
                } else {
                    capturedEnDir = LocalLayoutDirection.current
                    val enModel = MarketSourceBreakdownBuilder.build(
                        asset = cachedAsset,
                        isFa = false,
                        usePersianDigits = false,
                        strings = EnStrings,
                        referenceNowMillis = fixedNowMillis
                    )
                    MarketSourceBreakdownContent(
                        asset = cachedAsset,
                        model = enModel,
                        usePersianDigits = false,
                        strings = EnStrings,
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, capturedFaDir)
        composeRule.onNodeWithText(FaStrings.sourceBreakdownTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("source_breakdown_freshness").assertTextContains(FaStrings.cachedBadge)
        composeRule.onNodeWithText(FaStrings.liveBadge).assertDoesNotExist()
        composeRule.onNodeWithTag("source_status_Wallex").assertTextContains(FaStrings.sourceStatusIncluded)
        composeRule.onNodeWithTag("source_breakdown_list").performScrollToNode(hasTestTag("source_row_Nobitex"))
        composeRule.onNodeWithTag("source_status_Nobitex").assertTextContains(FaStrings.sourceStatusRejected)

        language = AppLanguage.EN
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Ltr, capturedEnDir)
        composeRule.onNodeWithText(EnStrings.sourceBreakdownTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("source_breakdown_freshness").assertTextContains(EnStrings.cachedBadge)
        composeRule.onNodeWithText(EnStrings.liveBadge).assertDoesNotExist()
    }
}
