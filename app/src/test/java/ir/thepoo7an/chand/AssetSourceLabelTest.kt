package ir.thepoo7an.chand

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.ui.components.AssetDetailSheet
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
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
 * The provider attribution is generated dynamically by the network layer
 * (`"Calculated Median (Frankfurter + Currency-API)"`), so it used to be cut to
 * `"Calculated Median (Frank…"` inside the source pill. The pill must always show the fixed short
 * label in full at 320dp, in both directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class, qualifiers = "w320dp-h640dp")
class AssetSourceLabelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        Locale.setDefault(Locale.US)
    }

    private fun euro(sourceLabel: String): MarketAsset = MarketAsset(
        id = "EUR",
        symbol = "EUR",
        nameFa = "یورو",
        nameEn = "Euro",
        category = AssetCategory.FIAT,
        badgeText = "🇪🇺",
        accentHex = 0xFF3B82F6,
        priceToman = 318_400.0,
        change24hToman = 1_200.0,
        change24hPercent = 0.38,
        sourceLabel = sourceLabel,
        updatedAtMillis = 1_764_244_800_000L,
        freshness = DataFreshness.LIVE,
        fetchedAtMillis = 1_764_244_800_000L,
        primaryUnit = PriceDisplayUnit.TOMAN,
        isCalculatedPrice = true
    )

    private fun render(language: AppLanguage, asset: MarketAsset) {
        Locale.setDefault(if (language == AppLanguage.FA) Locale("fa", "IR") else Locale.US)
        val prefs = AppPreferences(language = language)
        val strings = if (language == AppLanguage.FA) FaStrings else EnStrings
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ChandTheme(language = language) {
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
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun textOf(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }

    @Test
    fun `the calculated median renders as the fixed short label in English`() {
        render(AppLanguage.EN, euro("Calculated Median (Frankfurter + Currency-API)"))

        val tag = "stat_pill_value_${EnStrings.sourceLabel}"
        assertEquals("Median (ECB)", textOf(tag))

        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the label must stay on screen at 320dp, was $bounds", bounds.right <= 320.dp)
        assertTrue("the label must have width, was $bounds", (bounds.right - bounds.left) > 0.dp)
    }

    @Test
    fun `the calculated median renders as the fixed short label in Persian`() {
        render(AppLanguage.FA, euro("Calculated Median (Frankfurter + Currency-API)"))

        val tag = "stat_pill_value_${FaStrings.sourceLabel}"
        assertEquals("میانگین (ECB)", textOf(tag))

        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the label must stay on screen at 320dp, was $bounds", bounds.right <= 320.dp)
        assertTrue("the label must have width, was $bounds", (bounds.right - bounds.left) > 0.dp)
    }

    @Test
    fun `a non-ECB median keeps its own short label`() {
        render(AppLanguage.EN, euro("Calculated Median (Wallex + Nobitex)"))

        assertEquals("Median", textOf("stat_pill_value_${EnStrings.sourceLabel}"))
    }

    @Test
    fun `a plain provider name is never rewritten`() {
        render(AppLanguage.EN, euro("TGJU"))

        assertEquals("TGJU", textOf("stat_pill_value_${EnStrings.sourceLabel}"))
    }
}
