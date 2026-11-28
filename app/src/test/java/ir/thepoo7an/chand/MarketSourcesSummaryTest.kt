package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.components.MarketSourcesSummary
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Sources: N" must be backed by a real provider list: one row per provider, its newest fetch time,
 * and the assets it feeds.
 */
class MarketSourcesSummaryTest {

    private fun asset(
        id: String = "",
        nameEn: String = id,
        provider: String = "TGJU",
        fetchedAtMillis: Long = 1_700_000_000_000L,
        priceToman: Double = 292_890.0
    ): MarketAsset = MarketAsset(
        id = id,
        symbol = id,
        nameFa = "دارایی $id",
        nameEn = nameEn,
        category = AssetCategory.FIAT,
        badgeText = "🏷",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        sourceLabel = provider,
        updatedAtMillis = fetchedAtMillis,
        fetchedAtMillis = fetchedAtMillis
    )

    @Test
    fun `providers are grouped once each with the newest fetch time`() {
        val groups = MarketSourcesSummary.build(
            listOf(
                asset("USD", "US Dollar", "TGJU", 1_000L),
                asset("EUR", "Euro", "TGJU", 2_000L),
                asset("USDT", "Tether", "Wallex", 3_000L)
            ),
            isFa = false,
            strings = EnStrings
        )

        assertEquals(2, groups.size)
        assertEquals(2, MarketSourcesSummary.providerCount(listOf(asset("USD"), asset("EUR", provider = "Wallex"))))
        val tgju = groups.first { it.label == "TGJU" }
        assertEquals(2, tgju.assetCount)
        assertEquals("the newest fetch wins", 2_000L, tgju.lastFetchedMillis)
        assertEquals("US Dollar, Euro", tgju.assetsLabel)
    }

    @Test
    fun `a provider that feeds many assets lists a bounded number plus a counter`() {
        val many = (1..6).map { index -> asset("A$index", "Asset $index", "TGJU") }

        val row = MarketSourcesSummary.build(many, isFa = false, strings = EnStrings).single()

        assertEquals(6, row.assetCount)
        assertTrue("the row must stay short, was ${row.assetsLabel}", row.assetsLabel.endsWith("+2"))
        assertEquals(
            "Asset 1, Asset 2, Asset 3, Asset 4 +2",
            row.assetsLabel
        )
    }

    @Test
    fun `long provider attributions use the short label and a stable tag key`() {
        val groups = MarketSourcesSummary.build(
            listOf(asset("USD", provider = "Calculated Median (Frankfurter)")),
            isFa = false,
            strings = EnStrings
        )

        assertEquals("Median (ECB)", groups.single().label)
        assertEquals("median_ecb", groups.single().key)

        val faGroups = MarketSourcesSummary.build(
            listOf(asset("USD", provider = "Calculated Median (Frankfurter)")),
            isFa = true,
            strings = FaStrings
        )
        assertEquals("میانگین (ECB)", faGroups.single().label)
        assertEquals("the tag key must not depend on the locale", "median_ecb", faGroups.single().key)
    }

    @Test
    fun `assets without a provider or a price are not counted`() {
        val groups = MarketSourcesSummary.build(
            listOf(
                asset("USD", provider = ""),
                asset("EUR", provider = "TGJU", priceToman = 0.0),
                asset("GBP", provider = "Wallex")
            ),
            isFa = false,
            strings = EnStrings
        )

        assertEquals(1, groups.size)
        assertEquals("Wallex", groups.single().label)
    }
}
