package ir.thepoo7an.chand

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.ui.strings.EnStrings
import ir.thepoo7an.chand.ui.strings.FaStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.widget.WidgetStatusHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage5WidgetFreshnessTest {

    private fun sampleAsset(
        id: String = "USD",
        symbol: String = "USD",
        category: AssetCategory = AssetCategory.FIAT,
        priceToman: Double = 71_500.0,
        freshness: DataFreshness = DataFreshness.LIVE
    ): MarketAsset = MarketAsset(
        id = id,
        symbol = symbol,
        nameFa = "دارایی",
        nameEn = "Asset",
        category = category,
        badgeText = "💵",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        freshness = freshness
    )

    @Test
    fun `formatTime returns blank string for zero or negative timestamps and LockScreen pill never falls back to LIVE`() {
        assertEquals("", NumberFormatters.formatTime(0L, usePersianDigits = false))
        assertEquals("", NumberFormatters.formatTime(0L, usePersianDigits = true))
        assertEquals("", NumberFormatters.formatTime(-1_000L, usePersianDigits = false))

        val asset = sampleAsset(freshness = DataFreshness.LIVE)
        val freshnessWhenZero = WidgetStatusHelper.resolveFreshness(
            displayedAssets = listOf(asset),
            snapshotFreshness = DataFreshness.LIVE,
            updatedAtMillis = 0L,
            nowMillis = 1_700_000_000_000L
        )
        assertEquals(DataFreshness.UNAVAILABLE, freshnessWhenZero)

        val lockPillEn = WidgetStatusHelper.formatLockScreenStatusPill(
            freshness = freshnessWhenZero,
            updatedAtMillis = 0L,
            language = AppLanguage.EN,
            usePersianDigits = false
        )
        val lockPillFa = WidgetStatusHelper.formatLockScreenStatusPill(
            freshness = freshnessWhenZero,
            updatedAtMillis = 0L,
            language = AppLanguage.FA,
            usePersianDigits = true
        )

        assertEquals(EnStrings.unavailableBadge, lockPillEn)
        assertEquals(FaStrings.unavailableBadge, lockPillFa)
        assertFalse(lockPillEn.contains(EnStrings.liveBadge, ignoreCase = true))
        assertFalse(lockPillFa.contains(FaStrings.liveBadge))
    }

    @Test
    fun `resolveFreshness downgrades persisted LIVE snapshots to CACHED once category max age expires`() {
        val baseTime = 1_700_000_000_000L
        val usd = sampleAsset(id = "USD", symbol = "USD", category = AssetCategory.FIAT, freshness = DataFreshness.LIVE)
        val btc = sampleAsset(id = "BTC", symbol = "BTC", category = AssetCategory.CRYPTO, freshness = DataFreshness.LIVE)

        // Fiat within 30 minutes -> stays LIVE
        assertEquals(
            DataFreshness.LIVE,
            WidgetStatusHelper.resolveFreshness(
                displayedAssets = listOf(usd),
                snapshotFreshness = DataFreshness.LIVE,
                updatedAtMillis = baseTime,
                nowMillis = baseTime + MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS - 1_000L
            )
        )

        // Fiat past 30 minutes -> becomes CACHED even if stored snapshot originally said LIVE
        assertEquals(
            DataFreshness.CACHED,
            WidgetStatusHelper.resolveFreshness(
                displayedAssets = listOf(usd),
                snapshotFreshness = DataFreshness.LIVE,
                updatedAtMillis = baseTime,
                nowMillis = baseTime + MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS + 1_000L
            )
        )

        // Crypto past 5 minutes -> becomes CACHED earlier than fiat
        assertEquals(
            DataFreshness.CACHED,
            WidgetStatusHelper.resolveFreshness(
                displayedAssets = listOf(btc),
                snapshotFreshness = DataFreshness.LIVE,
                updatedAtMillis = baseTime,
                nowMillis = baseTime + MarketValidationConfig.CRYPTO_LIVE_MAX_AGE_MILLIS + 1_000L
            )
        )
    }

    @Test
    fun `resolveFreshness honors stalest asset or snapshot freshness and rejects zero prices`() {
        val now = 1_700_000_000_000L
        val liveUsd = sampleAsset(id = "USD", freshness = DataFreshness.LIVE)
        val delayedGold = sampleAsset(id = "GOLD_18K", freshness = DataFreshness.DELAYED)
        val cachedEur = sampleAsset(id = "EUR", freshness = DataFreshness.CACHED)
        val zeroPriceAsset = sampleAsset(id = "USD", priceToman = 0.0, freshness = DataFreshness.LIVE)

        assertEquals(
            DataFreshness.DELAYED,
            WidgetStatusHelper.resolveFreshness(listOf(liveUsd, delayedGold), DataFreshness.LIVE, now, now)
        )
        assertEquals(
            DataFreshness.CACHED,
            WidgetStatusHelper.resolveFreshness(listOf(liveUsd, delayedGold, cachedEur), DataFreshness.LIVE, now, now)
        )
        assertEquals(
            DataFreshness.CACHED,
            WidgetStatusHelper.resolveFreshness(listOf(liveUsd), DataFreshness.CACHED, now, now)
        )
        assertEquals(
            DataFreshness.UNAVAILABLE,
            WidgetStatusHelper.resolveFreshness(listOf(zeroPriceAsset), DataFreshness.LIVE, now, now)
        )
        assertEquals(
            DataFreshness.UNAVAILABLE,
            WidgetStatusHelper.resolveFreshness(emptyList(), DataFreshness.LIVE, now, now)
        )
    }

    @Test
    fun `widget status labels distinctly format LIVE, DELAYED, CACHED, and UNAVAILABLE in EN and FA`() {
        val ts = 1_700_000_000_000L
        val expectedTimeEn = NumberFormatters.formatTime(ts, usePersianDigits = false)
        val expectedTimeFa = NumberFormatters.formatTime(ts, usePersianDigits = true)
        assertTrue(expectedTimeEn.isNotBlank())

        val liveEn = WidgetStatusHelper.formatStatusLabel(DataFreshness.LIVE, ts, AppLanguage.EN, false)
        val delayedEn = WidgetStatusHelper.formatStatusLabel(DataFreshness.DELAYED, ts, AppLanguage.EN, false)
        val cachedEn = WidgetStatusHelper.formatStatusLabel(DataFreshness.CACHED, ts, AppLanguage.EN, false)
        val naEn = WidgetStatusHelper.formatStatusLabel(DataFreshness.UNAVAILABLE, ts, AppLanguage.EN, false)

        assertEquals("${EnStrings.liveBadge} • $expectedTimeEn", liveEn)
        assertEquals("${EnStrings.delayedBadge} • $expectedTimeEn", delayedEn)
        assertEquals("${EnStrings.cachedBadge} • $expectedTimeEn", cachedEn)
        assertEquals(EnStrings.unavailableBadge, naEn)
        assertNotEquals(liveEn, cachedEn)
        assertNotEquals(liveEn, delayedEn)

        val liveFa = WidgetStatusHelper.formatStatusLabel(DataFreshness.LIVE, ts, AppLanguage.FA, true)
        val cachedFa = WidgetStatusHelper.formatStatusLabel(DataFreshness.CACHED, ts, AppLanguage.FA, true)
        val heroSubFa = WidgetStatusHelper.formatHeroSubLine("USD", DataFreshness.CACHED, ts, AppLanguage.FA, true)

        assertEquals("${FaStrings.liveBadge} • $expectedTimeFa", liveFa)
        assertEquals("${FaStrings.cachedBadge} • $expectedTimeFa", cachedFa)
        assertEquals("USD • ${FaStrings.cachedBadge} • $expectedTimeFa", heroSubFa)
        assertNotEquals(liveFa, cachedFa)
    }
}
