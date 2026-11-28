package ir.thepoo7an.chand

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.widget.WidgetHeroMetrics
import ir.thepoo7an.chand.widget.WidgetHeroPresentation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "No clipping in a 1x1 cell" measured against the type scale the widget actually uses
 * ([WidgetHeroMetrics]): every string of the hero model must fit the width it is given and the two
 * rows must fit the height, at fontScale 1.0 and 1.3.
 *
 * This is a real text layout measurement (Compose's [TextMeasurer] on the device's font resolver), not
 * a screenshot baseline; the widget's RemoteViews layout is 1:1 with these constraints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class, qualifiers = "w320dp-h640dp")
class WidgetHeroFitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun usd(
        language: AppLanguage = AppLanguage.EN,
        changePct: Double = 12.5,
        freshness: DataFreshness = DataFreshness.LIVE,
        priceToman: Double = 61_250_000_000.0
    ) = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        change24hToman = 123_400.0,
        change24hPercent = changePct,
        updatedAtMillis = NOW,
        fetchedAtMillis = NOW,
        freshness = freshness
    )

    private data class Measured(val label: String, val widthPx: Float, val heightPx: Float)

    /** Measures every hero string at [fontScale] and returns them in dp for readable assertions. */
    private fun measure(
        fontScale: Float,
        scale: WidgetHeroMetrics.HeroScale,
        language: AppLanguage = AppLanguage.EN,
        asset: MarketAsset = usd(language)
    ): List<Measured> {
        val model = WidgetHeroPresentation.build(
            asset = asset,
            prefs = AppPreferences(language = language),
            snapshotFreshness = DataFreshness.LIVE,
            updatedAtMillis = NOW,
            nowMillis = NOW
        )
        val captured = mutableListOf<Measured>()
        composeRule.setContent {
            val baseDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(baseDensity, fontScale)) {
                val measurer: TextMeasurer = rememberTextMeasurer()
                SideEffect {
                    captured.clear()
                    fun add(name: String, text: String, style: TextStyle) {
                        val result = measurer.measure(text = text, style = style, maxLines = 1)
                        captured += Measured(
                            label = "$name='$text'",
                            widthPx = result.size.width.toFloat(),
                            heightPx = result.size.height.toFloat()
                        )
                    }
                    add("name", model.assetLabel, TextStyle(fontSize = scale.nameSize, fontWeight = FontWeight.Medium))
                    add("status", model.statusLine, TextStyle(fontSize = scale.statusSize))
                    add("price", model.priceText, TextStyle(fontSize = scale.priceSize, fontWeight = FontWeight.Bold))
                    model.deltaText?.let { add("delta", it, TextStyle(fontSize = scale.deltaSize)) }
                }
            }
        }
        composeRule.waitForIdle()
        return captured
    }

    private fun dp(px: Float): Float = px / composeRule.density.density

    @Test
    fun `the compact scale fits a one by one cell at fontScale 1_0`() {
        assertFits(scale = WidgetHeroMetrics.COMPACT, fontScale = 1.0f)
    }

    @Test
    fun `the compact scale still fits a one by one cell at fontScale 1_3`() {
        assertFits(scale = WidgetHeroMetrics.COMPACT, fontScale = 1.3f)
    }

    @Test
    fun `the compact Persian hero fits a one by one cell at fontScale 1_3`() {
        assertFits(scale = WidgetHeroMetrics.COMPACT, fontScale = 1.3f, language = AppLanguage.FA)
    }

    @Test
    fun `a stale marker and the largest price still fit the compact cell`() {
        assertFits(
            scale = WidgetHeroMetrics.COMPACT,
            fontScale = 1.3f,
            asset = usd(freshness = DataFreshness.DELAYED)
        )
    }

    @Test
    fun `the regular scale fits the default two by two placement at fontScale 1_3`() {
        assertFits(scale = WidgetHeroMetrics.REGULAR, fontScale = 1.3f)
    }

    @Test
    fun `the size threshold picks the compact scale for a one by one cell`() {
        assertTrue(
            WidgetHeroMetrics.forSize(DpSize(70.dp, 70.dp)) === WidgetHeroMetrics.COMPACT
        )
        assertTrue(
            WidgetHeroMetrics.forSize(DpSize(110.dp, 110.dp)) === WidgetHeroMetrics.REGULAR
        )
    }

    private fun assertFits(
        scale: WidgetHeroMetrics.HeroScale,
        fontScale: Float,
        language: AppLanguage = AppLanguage.EN,
        asset: MarketAsset = usd(language)
    ) {
        val measured = measure(fontScale, scale, language, asset)
        val cell = scale.cellSize.value
        val padding = scale.padding.value

        // Row 1 is the badge plus the name/status column; row 2 is the price plus the delta.
        val rowOneWidth = cell - 2 * padding - scale.badgeSize.value - scale.badgeTextGap.value
        val rowTwoWidth = cell - 2 * padding

        measured.forEach { m ->
            val width = dp(m.widthPx)
            val budget = when {
                m.label.startsWith("name") || m.label.startsWith("status") -> rowOneWidth
                else -> rowTwoWidth
            }
            assertTrue(
                "${m.label} is ${width}dp wide at fontScale $fontScale, budget ${budget}dp",
                width <= budget
            )
        }

        // The two rows must also fit vertically: name + status stacked, then the price row.
        val rowOneHeight = measured.filter { it.label.startsWith("name") || it.label.startsWith("status") }
            .sumOf { dp(it.heightPx).toDouble() }
        val rowTwoHeight = measured.first { it.label.startsWith("price") }.let { dp(it.heightPx).toDouble() }
        val totalHeight = rowOneHeight + rowTwoHeight
        assertTrue(
            "the hero needs ${totalHeight}dp of height at fontScale $fontScale, cell has ${cell - 2 * padding}dp",
            totalHeight <= cell - 2 * padding
        )
    }

    private companion object {
        const val NOW = 1_764_244_800_000L
    }
}
