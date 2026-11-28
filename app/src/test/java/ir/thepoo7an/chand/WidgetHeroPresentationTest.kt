package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.widget.WidgetHeroModel
import ir.thepoo7an.chand.widget.WidgetHeroPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1x1 hero widget used to render `USD • DELAYED • 10:59 • ↓` next to a `• 0.0%` delta: a stale
 * quote pretending to have a trend, in a cell too small to hold it. The model below is the whole
 * contract of what that widget may put on screen.
 */
class WidgetHeroPresentationTest {

    private companion object {
        const val NOW = 1_764_244_800_000L
        val PREFS = AppPreferences(language = AppLanguage.EN, usePersianDigits = false)
        val PREFS_FA = AppPreferences(language = AppLanguage.FA, usePersianDigits = true)
    }

    private fun usd(
        freshness: DataFreshness = DataFreshness.LIVE,
        changeToman: Double = 1_200.0,
        changePct: Double = 1.35,
        fetchedAtMillis: Long = NOW - 60_000L,
        sparkline: List<Double> = emptyList()
    ): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = 292_890.0,
        change24hToman = changeToman,
        change24hPercent = changePct,
        sparkline = sparkline,
        sourceLabel = "TGJU",
        updatedAtMillis = fetchedAtMillis,
        fetchedAtMillis = fetchedAtMillis,
        sourceTimestampMillis = fetchedAtMillis,
        freshness = freshness
    )

    private fun model(
        asset: MarketAsset,
        snapshotFreshness: DataFreshness = DataFreshness.LIVE,
        prefs: AppPreferences = PREFS,
        nowMillis: Long = NOW
    ): WidgetHeroModel = WidgetHeroPresentation.build(
        asset = asset,
        prefs = prefs,
        snapshotFreshness = snapshotFreshness,
        updatedAtMillis = asset.updatedAtMillis,
        nowMillis = nowMillis
    )

    @Test
    fun `a delayed quote shows a single stale marker and no delta at all`() {
        val hero = model(usd(freshness = DataFreshness.DELAYED, fetchedAtMillis = NOW - 7L * 24 * 3_600_000L))

        assertTrue(hero.isStale)
        assertEquals("stale", hero.statusLine)
        assertFalse("the status line never repeats the symbol", hero.statusLine.contains("USD"))
        assertNull("a stale quote must not print a delta, let alone 0.0%", hero.deltaText)
        assertFalse("no arrow, no status word and no clock may survive", hero.statusLine.contains("DELAYED"))
        assertFalse(hero.statusLine.contains(":"))
        assertFalse(hero.statusLine.contains("↓"))
    }

    @Test
    fun `a cached snapshot is stale even when the asset itself claims to be live`() {
        val hero = model(usd(), snapshotFreshness = DataFreshness.CACHED)

        assertTrue(hero.isStale)
        assertNull(hero.deltaText)
        assertEquals("stale", hero.statusLine)
    }

    @Test
    fun `an unavailable quote shows an em dash price and nothing else`() {
        val hero = model(usd(freshness = DataFreshness.UNAVAILABLE))

        assertTrue(hero.isStale)
        assertEquals("—", hero.priceText)
        assertNull(hero.deltaText)
        assertEquals("stale", hero.statusLine)
    }

    @Test
    fun `a fresh quote shows the real fetch time and never a zero delta`() {
        val hero = model(usd(changeToman = 0.0, changePct = 0.0))

        assertFalse(hero.isStale)
        assertTrue(
            "the second line is the fetch time alone, was '${hero.statusLine}'",
            Regex("^[0-9]{1,2}:[0-9]{2}$").matches(hero.statusLine)
        )
        assertNull("a zero change must not be printed as 0.0%", hero.deltaText)
    }

    @Test
    fun `a fresh quote with a real change shows the percent without an arrow`() {
        val hero = model(usd(changeToman = 3_500.0, changePct = 1.22))

        assertFalse(hero.isStale)
        assertEquals("+1.2%", hero.deltaText)
        assertEquals("the percent is the second line, the price row stays free", "+1.2%", hero.statusLine)
        assertFalse("a widget cannot prove a direction, so no arrow", hero.deltaText!!.contains("↑"))
        assertFalse(hero.deltaText!!.contains("↓"))
    }

    @Test
    fun `a flat series is still not a trend on the widget`() {
        // Even the app's honest "0" case is suppressed here: a 1x1 cell has no room for a nuance, and
        // "0.0%" next to a timestamp is exactly the contradiction that was reported.
        val hero = model(
            usd(
                changeToman = 0.0,
                changePct = 0.0,
                sparkline = listOf(292_890.0, 292_890.0, 292_890.0)
            )
        )

        assertNull(hero.deltaText)
    }

    @Test
    fun `oil without a 24h change shows no delta but keeps its price`() {
        val oil = usd(freshness = DataFreshness.LIVE).copy(
            id = "BRENT",
            symbol = "BRENT",
            category = AssetCategory.OIL,
            priceUsd = 75.25,
            isChange24hAvailable = false,
            change24hUsd = null,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL
        )

        val hero = model(oil)

        assertNull(hero.deltaText)
        assertEquals("\$75.25", hero.priceText)
    }

    @Test
    fun `the name row uses the symbol so a 1x1 cell cannot clip it`() {
        val hero = model(usd())

        assertEquals("USD", hero.assetLabel)
        assertTrue(
            "the symbol must stay within the 1x1 budget, was ${hero.assetLabel}",
            hero.assetLabel.length <= WidgetHeroModel.MAX_ASSET_LABEL_LENGTH
        )
        assertFalse("never the full display name", hero.assetLabel.contains("Dollar"))
    }

    @Test
    fun `every rendered string stays within the 1x1 character budget at fontScale 1_3`() {
        val hero = model(usd(changeToman = 123_400.0, changePct = 12.5), prefs = PREFS_FA)

        assertTrue(hero.assetLabel.length <= WidgetHeroModel.MAX_ASSET_LABEL_LENGTH)
        assertTrue(
            "status line \"${hero.statusLine}\" exceeds the two-row budget",
            hero.statusLine.length <= WidgetHeroModel.MAX_STATUS_LINE_LENGTH
        )
        assertTrue(hero.priceText.length <= WidgetHeroModel.MAX_PRICE_LENGTH)
        assertTrue((hero.deltaText?.length ?: 0) <= WidgetHeroModel.MAX_DELTA_LENGTH)
        assertTrue("row 2 exists: price is always rendered", hero.hasSecondRow)
    }

    @Test
    fun `the Persian widget uses Persian digits and its own stale marker`() {
        // No change value, so the second line is the fetch time.
        val quiet = model(usd(changeToman = 0.0, changePct = 0.0), prefs = PREFS_FA)
        assertFalse(quiet.isStale)
        assertTrue(
            "the time must use Persian digits: ${quiet.statusLine}",
            Regex("^[\u06F0-\u06F9]{1,2}:[\u06F0-\u06F9]{2}$").matches(quiet.statusLine)
        )

        val changed = model(usd(changeToman = 3_500.0, changePct = 1.22), prefs = PREFS_FA)
        assertEquals("+۱.۲%", changed.statusLine)

        val stale = model(usd(freshness = DataFreshness.DELAYED), prefs = PREFS_FA)
        assertTrue("the stale marker must be Persian: ${stale.statusLine}", stale.statusLine.contains("کهنه"))
    }
}
