package ir.thepoo7an.chand

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.ui.screens.MarketFeedScreen
import ir.thepoo7an.chand.ui.screens.headerTitleFontSize
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.stringsFor
import ir.thepoo7an.chand.ui.theme.ChandTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Markets-screen chrome measured at a 320dp-wide screen, in both locales.
 *
 * The screen is rendered directly with explicit state so each test pins one layout rule instead of
 * depending on the network, a view model, or the device locale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class, qualifiers = "w320dp-h640dp")
class MarketsChromeTest {

    @get:Rule
    // A bare ComponentActivity: this class measures layout, it does not need the real app
    // activity (whose startup work would leak into unrelated tests).
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
        Locale.setDefault(Locale.US)
    }

    private fun render(
        language: AppLanguage,
        assets: List<MarketAsset> = emptyList(),
        state: MarketUiState = MarketUiState.Ready(
            assets = assets,
            lastUpdatedMillis = NOW,
            sourceSummary = "Free Market (Tehran) • TGJU • Wallex • Nobitex • Binance",
            freshness = DataFreshness.LIVE
        )
    ) {
        Locale.setDefault(if (language == AppLanguage.FA) Locale("fa", "IR") else Locale.US)
        val strings = stringsFor(language)
        val prefs = AppPreferences(language = language)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ChandTheme(language = language) {
                    MarketFeedScreen(
                        uiState = state,
                        filteredAssets = assets,
                        prefs = prefs,
                        strings = strings,
                        selectedCategory = AssetCategory.ALL,
                        searchQuery = "",
                        onSelectCategory = {},
                        onSearchQueryChange = {},
                        onTogglePin = {},
                        onToggleGridView = {},
                        onAssetClick = {},
                        onRefreshClick = {},
                        referenceNowMillis = NOW
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the English brand title is one line wide enough at 320dp`() {
        render(AppLanguage.EN)

        val title = composeRule.onNodeWithTag("header_title")
        title.assertIsDisplayed()
        val bounds = title.getUnclippedBoundsInRoot()
        assertTrue(
            "The title must stay on a single line at 320dp, was $bounds",
            (bounds.bottom - bounds.top) < 48.dp
        )
        assertTrue("The title must not be clipped on the trailing edge, was $bounds", bounds.right <= 320.dp)
    }

    @Test
    fun `the Persian brand title is one line at 320dp too`() {
        render(AppLanguage.FA)

        val title = composeRule.onNodeWithTag("header_title")
        title.assertIsDisplayed()
        val bounds = title.getUnclippedBoundsInRoot()
        assertTrue(
            "«نرخ‌نما» must stay on a single line at 320dp, was $bounds",
            (bounds.bottom - bounds.top) < 48.dp
        )
        assertTrue("The title must not be clipped on the trailing edge, was $bounds", bounds.right <= 320.dp)
    }

    @Test
    fun `the title size ladder steps down instead of wrapping`() {
        assertEquals(23.sp, headerTitleFontSize(isFa = false, screenWidthDp = 320))
        assertEquals(23.sp, headerTitleFontSize(isFa = true, screenWidthDp = 320))
        assertEquals(26.sp, headerTitleFontSize(isFa = false, screenWidthDp = 360))
        assertEquals(30.sp, headerTitleFontSize(isFa = false, screenWidthDp = 412))
        assertEquals(29.sp, headerTitleFontSize(isFa = true, screenWidthDp = 412))
        assertEquals("Nerkhnama", EnStrings.appTitle)
        assertEquals("نرخ‌نما", FaStrings.appTitle)
    }

    @Test
    fun `a delayed asset shows no sparkline, no delta chip and no delayed badge`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(
                    sparkline = listOf(290_000.0, 291_000.0, 292_890.0),
                    freshness = DataFreshness.DELAYED,
                    changeToman = 0.0,
                    changePct = 0.0
                )
            )
        )

        // "Updated just now" next to DELAYED and a "• 0" chip is the contradiction from the review.
        composeRule.onNodeWithTag("freshness_chip_USD", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("sparkline_USD", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("delta_chip_USD", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("status_badge_USD", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a live asset with real change keeps its sparkline and delta chip`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(
                    sparkline = listOf(290_000.0, 291_000.0, 292_890.0),
                    changeToman = 1_890.0,
                    changePct = 0.65
                )
            )
        )

        composeRule.onNodeWithTag("sparkline_USD", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("delta_chip_USD", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an asset without any history renders no trend affordance at all`() {
        render(AppLanguage.FA, listOf(sampleAsset(sparkline = emptyList())))

        composeRule.onNodeWithTag("sparkline_USD", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("delta_chip_USD", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a unit mix-up shows a check-unit badge instead of a confident price`() {
        render(AppLanguage.EN, listOf(sampleAsset(unitMismatch = true, disputed = true)))

        composeRule.onNodeWithTag("status_badge_USD", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(EnStrings.checkUnitLabel).assertExists()
    }

    @Test
    fun `at 320dp the header has no date column and no clock at all`() {
        render(AppLanguage.EN)

        // Narrow screens give the whole width to the title; the date column is dropped instead of
        // squeezing the brand word or duplicating the status-bar clock.
        composeRule.onNodeWithTag("header_date").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w420dp-h800dp")
    fun `on a wide header the date is a calendar date, never a clock`() {
        render(AppLanguage.EN)

        val date = textOf("header_date_text")
        assertTrue("the header date must exist on a wide screen, was '$date'", date.isNotBlank())
        assertTrue(
            "the header must show a calendar date, not a clock, was '$date'",
            !date.contains(":") && !date.contains("AM") && !date.contains("PM")
        )
    }

    @Test
    fun `the meta row shows a functional last-sync time instead of a clock`() {
        render(AppLanguage.EN)

        val text = textOf("last_sync_label")
        assertTrue("the sync line must be present, was '$text'", text.startsWith("${EnStrings.lastSyncPrefix}: "))
        assertTrue(
            "the sync line must carry the real fetch time, was '$text'",
            Regex("[0-9]{1,2}:[0-9]{2}").containsMatchIn(text)
        )
    }

    @Test
    fun `the sync line is localised in Persian and stays on one line at 320dp`() {
        render(AppLanguage.FA)

        val text = textOf("last_sync_label")
        assertTrue("the sync line must be Persian, was '$text'", text.startsWith("${FaStrings.lastSyncPrefix}: "))
        assertTrue(
            "time must use Persian digits, was '$text'",
            Regex("[۰-۹]{1,2}:[۰-۹]{2}").containsMatchIn(text)
        )
        val bounds = composeRule.onNodeWithTag("last_sync_label").getUnclippedBoundsInRoot()
        assertTrue("the sync line must fit 320dp, was $bounds", bounds.right <= 320.dp)
    }

    private fun textOf(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }

    @Test
    fun `the first category chip is fully inside the screen at rest`() {
        render(AppLanguage.EN)

        val first = composeRule.onNodeWithTag("category_ALL").getUnclippedBoundsInRoot()
        assertTrue("the first chip must not be clipped, was $first", first.left >= 0.dp)
        assertTrue("the first chip must keep its 16dp inset, was $first", first.left >= 15.dp)
    }

    @Test
    fun `the last category chip is fully visible once the row is scrolled to the end`() {
        render(AppLanguage.FA)

        val last = AssetCategory.entries.last()
        composeRule.onNodeWithTag("category_row").performScrollToIndex(AssetCategory.entries.lastIndex)
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("category_${last.name}").getUnclippedBoundsInRoot()
        assertTrue(
            "the last chip must keep its 16dp inset instead of being clipped, was $bounds",
            bounds.right <= 306.dp
        )
    }

    @Test
    fun `the feed shows only a provider count, not the provider sentence`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(id = "USD"),
                sampleAsset(id = "EUR").copy(sourceLabel = "Wallex")
            )
        )

        assertEquals("Sources: 2", textOf("sources_count_label"))
        composeRule.onNodeWithTag("sources_info_button").assertExists()
        composeRule.onNodeWithTag("sources_sheet").assertDoesNotExist()
    }

    @Test
    fun `the info button opens a sheet with every provider and its fetch time`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(id = "USD"),
                sampleAsset(id = "EUR").copy(sourceLabel = "Wallex")
            )
        )

        composeRule.onNodeWithTag("sources_info_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sources_sheet").assertExists()
        composeRule.onNodeWithTag("provider_row_tgju").assertExists()
        composeRule.onNodeWithTag("provider_row_wallex").assertExists()
        assertEquals("TGJU", textOf("provider_label_tgju"))
        assertTrue(
            "each provider row must show when it was fetched",
            Regex("[0-9]{1,2}:[0-9]{2}").containsMatchIn(textOf("provider_fetched_tgju"))
        )
    }

    @Test
    fun `in LTR the provider label starts on the leading left edge`() {
        render(AppLanguage.EN, listOf(sampleAsset(id = "USD")))

        composeRule.onNodeWithTag("sources_info_button").performClick()
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("provider_label_tgju").getUnclippedBoundsInRoot()
        assertTrue("in LTR the label starts on the left, was $bounds", bounds.left < 160.dp)
    }

    @Test
    fun `in RTL the provider label starts on the leading right edge and stays inside 320dp`() {
        render(AppLanguage.FA, listOf(sampleAsset(id = "USD")))

        composeRule.onNodeWithTag("sources_info_button").performClick()
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("provider_label_tgju").getUnclippedBoundsInRoot()
        assertTrue("in RTL the label starts on the right, was $bounds", bounds.right > 160.dp)
        assertTrue("the label must stay inside 320dp, was $bounds", bounds.right <= 320.dp)
    }

    @Test
    fun `a card without chart data collapses instead of leaving an empty band`() {
        render(AppLanguage.EN, listOf(sampleAsset(sparkline = emptyList())))

        val bounds = composeRule.onNodeWithTag("asset_row_USD", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("a chart-less card must collapse to the compact layout, was $height", height <= 172.dp)
        // Compact must still be complete: price and freshness stay visible, nothing is clipped away.
        composeRule.onNodeWithTag("asset_price_USD", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("freshness_chip_USD", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a card with a real chart keeps the taller layout`() {
        render(
            AppLanguage.EN,
            listOf(sampleAsset(sparkline = listOf(290_000.0, 291_500.0, 292_890.0), changeToman = 1_890.0, changePct = 0.65))
        )

        val bounds = composeRule.onNodeWithTag("asset_row_USD", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("a chart card must keep room for the line, was $height", height >= 188.dp)
        composeRule.onNodeWithTag("sparkline_USD", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a dollar peg is labelled instead of a stray dollar value`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(id = "USDT", price = 25_700_000.0)
                    .copy(category = AssetCategory.CRYPTO, priceUsd = 1.0, symbol = "USDT")
            )
        )

        assertEquals("\$1 = peg", textOf("peg_chip_USDT"))
        assertTrue(
            "a bare dollar value must not be rendered any more",
            composeRule.onAllNodesWithText("\$1", substring = true).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun `the peg label is localised`() {
        render(
            AppLanguage.FA,
            listOf(
                sampleAsset(id = "USDT", price = 25_700_000.0)
                    .copy(category = AssetCategory.CRYPTO, priceUsd = 1.0, symbol = "USDT")
            )
        )

        assertEquals(FaStrings.stablecoinPegLabel, textOf("peg_chip_USDT"))
    }

    @Test
    fun `a non-pegged crypto keeps its dollar price and gets no peg chip`() {
        render(
            AppLanguage.EN,
            listOf(
                sampleAsset(id = "BTC", price = 6_500_000_000.0)
                    .copy(category = AssetCategory.CRYPTO, priceUsd = 65_432.0, symbol = "BTC")
            )
        )

        composeRule.onNodeWithTag("peg_chip_BTC", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("\$65,432", substring = true, useUnmergedTree = true).assertExists()
    }

    companion object {
        /** 2025-11-27T12:00:00Z — the fixed "now" every card is measured against. */
        const val NOW = 1_764_244_800_000L

        fun sampleAsset(
            id: String = "USD",
            price: Double = 292_890.0,
            freshness: DataFreshness = DataFreshness.LIVE,
            sparkline: List<Double> = emptyList(),
            changeToman: Double = 0.0,
            changePct: Double = 0.0,
            updatedAtMillis: Long = NOW - 5_000L,
            disputed: Boolean = false,
            unitMismatch: Boolean = false
        ): MarketAsset = MarketAsset(
            id = id,
            symbol = id,
            nameFa = "دلار آمریکا",
            nameEn = "US Dollar",
            category = AssetCategory.FIAT,
            badgeText = "🇺🇸",
            accentHex = 0xFF10B981,
            priceToman = price,
            change24hPercent = changePct,
            change24hToman = changeToman,
            sparkline = sparkline,
            sourceLabel = "TGJU",
            updatedAtMillis = updatedAtMillis,
            freshness = freshness,
            fetchedAtMillis = updatedAtMillis,
            sourceTimestampMillis = updatedAtMillis,
            disputed = disputed,
            unitMismatch = unitMismatch
        )
    }
}
