package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.components.AssetTrendPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card must never invent a trend: no sparkline without a series, no `0` without flat data.
 */
class AssetTrendPresentationTest {

    private fun asset(
        sparkline: List<Double> = emptyList(),
        freshness: DataFreshness = DataFreshness.LIVE,
        changeToman: Double = 0.0,
        changePct: Double = 0.0,
        price: Double = 292_890.0
    ): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = price,
        change24hPercent = changePct,
        change24hToman = changeToman,
        sparkline = sparkline,
        freshness = freshness
    )

    @Test
    fun `an empty series hides both the sparkline and the delta chip`() {
        val empty = asset(sparkline = emptyList())

        assertFalse(AssetTrendPresentation.shouldShowSparkline(empty))
        assertNull(
            "an empty history must not surface as a zero delta",
            AssetTrendPresentation.deltaLabel(empty, usePersianDigits = false, hasDisplayChange = true)
        )
    }

    @Test
    fun `a degenerate single-point or all-zero series is not a trend`() {
        assertFalse(AssetTrendPresentation.shouldShowSparkline(asset(sparkline = listOf(1.0))))
        assertFalse(AssetTrendPresentation.shouldShowSparkline(asset(sparkline = listOf(0.0, 0.0))))
        assertFalse(
            AssetTrendPresentation.shouldShowSparkline(asset(sparkline = listOf(Double.NaN, 5.0)))
        )
    }

    @Test
    fun `a genuinely flat series is the only case that may show a zero delta`() {
        val flat = asset(sparkline = listOf(292_890.0, 292_890.0, 292_890.0))

        assertTrue(AssetTrendPresentation.isFlatSeries(flat))
        val label = AssetTrendPresentation.deltaLabel(flat, usePersianDigits = false, hasDisplayChange = true)
        assertNotNull(label)
        assertTrue("flat data should read as no change, was $label", label!!.contains("0"))
    }

    @Test
    fun `delayed history hides the trend even when the quote itself is fresh`() {
        val delayed = asset(
            sparkline = listOf(290_000.0, 291_000.0, 292_890.0),
            freshness = DataFreshness.DELAYED
        )

        assertFalse(AssetTrendPresentation.shouldShowSparkline(delayed))
        assertNull(AssetTrendPresentation.deltaLabel(delayed, false, hasDisplayChange = true))
    }

    @Test
    fun `a live series with a real change keeps its delta and sparkline`() {
        val live = asset(
            sparkline = listOf(290_000.0, 291_000.0, 292_890.0),
            changeToman = 1_890.0,
            changePct = 0.65
        )

        assertTrue(AssetTrendPresentation.shouldShowSparkline(live))
        val label = AssetTrendPresentation.deltaLabel(live, usePersianDigits = false, hasDisplayChange = true)
        assertNotNull(label)
        assertTrue("a real change keeps its arrow, was $label", label!!.startsWith("↑"))
    }

    @Test
    fun `only a dollar-pegged stablecoin is treated as a peg`() {
        assertTrue(asset(price = 25_700_000.0).copy(category = AssetCategory.CRYPTO, priceUsd = 1.0).isStablecoinPeg)
        assertTrue(asset(price = 25_700_000.0).copy(category = AssetCategory.CRYPTO, priceUsd = 0.999).isStablecoinPeg)
        assertFalse(asset(price = 25_700_000.0).copy(category = AssetCategory.CRYPTO, priceUsd = 1.05).isStablecoinPeg)
        assertFalse(asset(price = 25_700_000.0).copy(category = AssetCategory.CRYPTO, priceUsd = null).isStablecoinPeg)
        // A fiat rate of ~1 dollar (e.g. a pegged Gulf currency) is not a stablecoin card.
        assertFalse(asset(price = 25_700.0).copy(category = AssetCategory.FIAT, priceUsd = 1.0).isStablecoinPeg)
    }

    @Test
    fun `an unavailable or change-less asset never shows a delta chip`() {
        val unavailable = asset(sparkline = listOf(1.0, 2.0), freshness = DataFreshness.UNAVAILABLE)
        assertNull(AssetTrendPresentation.deltaLabel(unavailable, false, hasDisplayChange = true))

        val noChangeValue = asset(sparkline = listOf(1.0, 2.0))
        assertNull(
            "no change value and no flat series means no chip at all",
            AssetTrendPresentation.deltaLabel(noChangeValue, false, hasDisplayChange = false)
        )
    }
}
