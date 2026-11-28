package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.network.GoldUnitBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gold/coin sanity check is derived from spot XAU and the USD rate the app already fetched, so a
 * gram-vs-mesghal or 18K-vs-24K mix-up becomes a visible flag instead of a plausible-looking price.
 */
class GoldUnitBenchmarkTest {

    private companion object {
        const val XAU_USD = 4_000.0
        const val USD_TOMAN = 300_000.0

        /** Written out independently of the implementation: ounce -> gram -> 18K ratio. */
        val EXPECTED_18K: Double = XAU_USD / 31.1035 * USD_TOMAN * 0.750

        val EXPANDED_24K: Double = EXPECTED_18K / 0.750
    }

    private fun gold(
        id: String,
        priceToman: Double,
        freshness: DataFreshness = DataFreshness.LIVE,
        category: AssetCategory = AssetCategory.GOLD
    ): MarketAsset = MarketAsset(
        id = id,
        symbol = id,
        nameFa = id,
        nameEn = id,
        category = category,
        badgeText = "✨",
        accentHex = 0xFFF59E0B,
        priceToman = priceToman,
        priceUsd = if (id == "XAU_OZ") XAU_USD else null,
        freshness = freshness
    )

    private fun basisAssets(): List<MarketAsset> = listOf(
        gold("XAU_OZ", priceToman = XAU_USD * USD_TOMAN),
        gold("USD", priceToman = USD_TOMAN, category = AssetCategory.FIAT)
    )

    @Test
    fun `the 18K benchmark is one ounce of spot gold converted to a single 18K gram`() {
        val expected = GoldUnitBenchmark.expectedToman("GOLD_18K", XAU_USD, USD_TOMAN)

        assertEquals(EXPECTED_18K, expected ?: 0.0, 1.0)
        assertEquals(
            "24K is one 18K step above, i.e. /0.750",
            (expected ?: 0.0) / 0.750,
            GoldUnitBenchmark.expectedToman("GOLD_24K", XAU_USD, USD_TOMAN) ?: 0.0,
            1.0
        )
        assertNull(GoldUnitBenchmark.expectedToman("USD", XAU_USD, USD_TOMAN))
        assertNull("no benchmark without spot gold", GoldUnitBenchmark.expectedToman("GOLD_18K", null, USD_TOMAN))
        assertNull("no benchmark without a USD rate", GoldUnitBenchmark.expectedToman("GOLD_18K", XAU_USD, 0.0))
    }

    @Test
    fun `a correct 18K value inside the market band is not flagged`() {
        val assets = basisAssets() + gold("GOLD_18K", priceToman = EXPECTED_18K * 1.05)

        val checked = GoldUnitBenchmark.flagUnitMixups(assets)

        val gram18 = checked.first { it.id == "GOLD_18K" }
        assertFalse("a 5% premium over bullion is normal, not a unit bug", gram18.unitMismatch)
        assertFalse(gram18.disputed)
    }

    @Test
    fun `a value twice the expected benchmark is flagged as a unit mix-up`() {
        val assets = basisAssets() + gold("GOLD_18K", priceToman = EXPECTED_18K * 2.0)

        val checked = GoldUnitBenchmark.flagUnitMixups(assets)

        val gram18 = checked.first { it.id == "GOLD_18K" }
        assertTrue("2x the benchmark means a wrong unit (gram vs ounce/rial)", gram18.unitMismatch)
        assertTrue("the flag reuses the existing disputed surface", gram18.disputed)
        assertEquals("the price itself is never rewritten", EXPECTED_18K * 2.0, gram18.priceToman, 0.01)
    }

    @Test
    fun `a missing or stale spot basis flags nothing`() {
        val wrongPrice = EXPECTED_18K * 2.0

        val withoutXau = GoldUnitBenchmark.flagUnitMixups(
            listOf(gold("USD", USD_TOMAN, category = AssetCategory.FIAT), gold("GOLD_18K", wrongPrice))
        )
        assertFalse("no XAU means no evidence", withoutXau.first { it.id == "GOLD_18K" }.unitMismatch)

        val staleXau = GoldUnitBenchmark.flagUnitMixups(
            listOf(
                gold("XAU_OZ", XAU_USD * USD_TOMAN, freshness = DataFreshness.CACHED),
                gold("USD", USD_TOMAN, category = AssetCategory.FIAT),
                gold("GOLD_18K", wrongPrice)
            )
        )
        assertFalse("a cached XAU is not a fresh basis", staleXau.first { it.id == "GOLD_18K" }.unitMismatch)

        val withoutUsd = GoldUnitBenchmark.flagUnitMixups(
            listOf(gold("XAU_OZ", XAU_USD * USD_TOMAN), gold("GOLD_18K", wrongPrice))
        )
        assertFalse("no USD rate means no evidence", withoutUsd.first { it.id == "GOLD_18K" }.unitMismatch)
    }

    @Test
    fun `the mesghal and coin multiples come from the documented data table`() {
        val mesghal = GoldUnitBenchmark.eighteenKaratGramEquivalent("GOLD_MESGHAL")
        val emami = GoldUnitBenchmark.eighteenKaratGramEquivalent("COIN_EMAMI")

        assertEquals(4.3318, mesghal ?: 0.0, 0.001)
        assertTrue("a full coin is ~9.7 18K grams, was $emami", (emami ?: 0.0) in 9.0..10.0)
        assertNull(GoldUnitBenchmark.eighteenKaratGramEquivalent("USDT"))
    }

    @Test
    fun `only the deviating asset is copied and flagged`() {
        val assets = basisAssets() + listOf(
            gold("GOLD_18K", EXPECTED_18K * 1.02),
            gold("GOLD_24K", EXPANDED_24K * 2.4)
        )

        val checked = GoldUnitBenchmark.flagUnitMixups(assets)

        assertFalse(checked.first { it.id == "GOLD_18K" }.unitMismatch)
        assertTrue(checked.first { it.id == "GOLD_24K" }.unitMismatch)
    }
}
