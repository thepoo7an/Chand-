package ir.thepoo7an.chand

import androidx.activity.compose.setContent
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
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
    val composeRule = createAndroidComposeRule<MainActivity>()

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
            bounds.height < 48.dp
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
            bounds.height < 48.dp
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
        composeRule.onNodeWithTag("freshness_chip_USD").assertExists()
        composeRule.onNodeWithTag("sparkline_USD").assertDoesNotExist()
        composeRule.onNodeWithTag("delta_chip_USD").assertDoesNotExist()
        composeRule.onNodeWithTag("status_badge_USD").assertDoesNotExist()
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

        composeRule.onNodeWithTag("sparkline_USD").assertExists()
        composeRule.onNodeWithTag("delta_chip_USD").assertExists()
    }

    @Test
    fun `an asset without any history renders no trend affordance at all`() {
        render(AppLanguage.FA, listOf(sampleAsset(sparkline = emptyList())))

        composeRule.onNodeWithTag("sparkline_USD").assertDoesNotExist()
        composeRule.onNodeWithTag("delta_chip_USD").assertDoesNotExist()
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
            disputed: Boolean = false
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
            sourceLabel = "TGJU (Free)",
            updatedAtMillis = updatedAtMillis,
            freshness = freshness,
            fetchedAtMillis = updatedAtMillis,
            sourceTimestampMillis = updatedAtMillis,
            disputed = disputed
        )
    }
}
