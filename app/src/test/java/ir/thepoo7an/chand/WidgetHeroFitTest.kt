package ir.thepoo7an.chand

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.widget.WidgetHeroMetrics
import ir.thepoo7an.chand.widget.WidgetHeroPresentation
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "No clipping in a 1x1 cell", expressed as a geometry budget instead of a screenshot baseline.
 *
 * Robolectric's text metrics are stubs (every line reports the same height regardless of font size or
 * fontScale), so a Compose measurement there would be a false green. This test instead applies the same
 * arithmetic the layout relies on — average glyph advance ≈ [AVG_CHAR_EM] em for digits and Latin text,
 * scaled by `fontScale` — to the *production* type scale from [WidgetHeroMetrics] and the *production*
 * strings from [WidgetHeroPresentation]. A regression such as "USD • DELAYED • 10:59 • ↓" fails here.
 */
class WidgetHeroFitTest {

    private companion object {
        const val NOW = 1_764_244_800_000L

        /** Digits in the app's fonts advance ~0.55em; Latin caps are wider, Persian narrower. */
        const val AVG_CHAR_EM = 0.55
    }

    private fun usd(
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

    private fun hero(
        language: AppLanguage = AppLanguage.EN,
        asset: MarketAsset = usd()
    ) = WidgetHeroPresentation.build(
        asset = asset,
        prefs = AppPreferences(language = language),
        snapshotFreshness = DataFreshness.LIVE,
        updatedAtMillis = NOW,
        nowMillis = NOW
    )

    /** How many characters of [fontSizeSp] fit in [availableDp] at [fontScale]. */
    private fun maxCharacters(availableDp: Double, fontSizeSp: Float, fontScale: Float): Int =
        floor(availableDp / (AVG_CHAR_EM * fontSizeSp * fontScale)).toInt()

    private fun assertFits(
        scale: WidgetHeroMetrics.HeroScale,
        fontScale: Float,
        language: AppLanguage = AppLanguage.EN,
        asset: MarketAsset = usd()
    ) {
        val model = hero(language, asset)
        val cell = scale.cellSize.value.toDouble()
        val padding = scale.padding.value.toDouble()

        // Row 1: badge + the name/status column. Row 2: price + optional percent.
        val rowOneWidth = cell - 2 * padding - scale.badgeSize.value - scale.badgeTextGap.value
        val rowTwoWidth = cell - 2 * padding

        val nameBudget = maxCharacters(rowOneWidth, scale.nameSize.value, fontScale)
        val statusBudget = maxCharacters(rowOneWidth, scale.statusSize.value, fontScale)
        val delta = model.deltaText
        val deltaWidthDp = delta
            ?.let { it.length * AVG_CHAR_EM * scale.deltaSize.value * fontScale } ?: 0.0
        val priceBudget = maxCharacters(
            rowTwoWidth - deltaWidthDp - (if (delta != null) scale.priceDeltaGap.value.toDouble() else 0.0),
            scale.priceSize.value,
            fontScale
        )
        val deltaBudget = maxCharacters(rowTwoWidth, scale.deltaSize.value, fontScale)

        assertTrue(
            "name \"${model.assetLabel}\" needs ${model.assetLabel.length} chars, budget $nameBudget " +
                "(${scale.cellSize} @ fontScale $fontScale)",
            model.assetLabel.length <= nameBudget
        )
        assertTrue(
            "status \"${model.statusLine}\" needs ${model.statusLine.length} chars, budget $statusBudget",
            model.statusLine.length <= statusBudget
        )
        assertTrue(
            "price \"${model.priceText}\" needs ${model.priceText.length} chars, budget $priceBudget",
            model.priceText.length <= priceBudget
        )
        delta?.let {
            assertTrue(
                "delta \"$it\" needs ${it.length} chars, budget $deltaBudget",
                it.length <= deltaBudget
            )
        }

        // Two rows: name + status stacked, then the price row. Each row is one line of text.
        val lineHeightFactor = 1.25
        val rowOneHeight = (scale.nameSize.value + scale.statusSize.value) * fontScale * lineHeightFactor
        val rowTwoHeight = scale.priceSize.value * fontScale * lineHeightFactor
        val availableHeight = cell - 2 * padding
        assertTrue(
            "the hero needs ${rowOneHeight + rowTwoHeight}dp of height at fontScale $fontScale, " +
                "the cell has ${availableHeight}dp",
            rowOneHeight + rowTwoHeight <= availableHeight
        )
    }

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
    fun `a stale marker and a billion-Toman price still fit the compact cell`() {
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
    fun `a one by one cell selects the compact scale`() {
        assertEquals(WidgetHeroMetrics.COMPACT, WidgetHeroMetrics.forSize(DpSize(70.dp, 70.dp)))
        assertEquals(WidgetHeroMetrics.REGULAR, WidgetHeroMetrics.forSize(DpSize(110.dp, 110.dp)))
        // A very wide but short cell counts as small: the height is what clips.
        assertEquals(WidgetHeroMetrics.COMPACT, WidgetHeroMetrics.forSize(DpSize(180.dp, 70.dp)))
    }

    @Test
    fun `the old overflowing status line would fail this budget`() {
        // Guard against re-introducing the reported regression: "USD • DELAYED • 10:59 • ↓".
        val compact = WidgetHeroMetrics.COMPACT
        val budget = maxCharacters(
            compact.cellSize.value - 2 * compact.padding.value,
            compact.statusSize.value,
            1.3f
        )
        assertTrue("the old combined status line must not fit any more", "USD • DELAYED • 10:59 • ↓".length > budget)
    }
}
