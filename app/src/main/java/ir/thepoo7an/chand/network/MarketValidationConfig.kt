package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.TimeRange

/**
 * Single centralized configuration for market data validation, cross-source consensus,
 * freshness thresholds, adaptive history spike thresholds, and physical gold coin specifications.
 *
 * Defaults follow Stage 2 Decisions and commonly cited market conventions (see [COIN_SPECS] caveats).
 */
object MarketValidationConfig {

    /** Maximum allowed deviation (%) from cross-source median for cryptocurrency prices. */
    const val CRYPTO_DISAGREEMENT_THRESHOLD_PCT: Double = 3.0

    /** Maximum allowed deviation (%) from cross-source median for fiat / free-market prices. */
    const val FIAT_DISAGREEMENT_THRESHOLD_PCT: Double = 3.0

    /**
     * Default minimum jump (%) followed by reversion considered a single-tick history spike
     * when asset category / time range are unspecified (preserves legacy 24H / default behavior).
     */
    const val HISTORY_SPIKE_THRESHOLD_PCT: Double = 15.0

    /** Adaptive history spike threshold (%) for 24H range across all asset categories. */
    const val HISTORY_SPIKE_THRESHOLD_24H_PCT: Double = 15.0

    /** Adaptive history spike threshold (%) for daily-or-coarser crypto series (7D / 1M / 1Y). */
    const val HISTORY_SPIKE_THRESHOLD_CRYPTO_MULTI_DAY_PCT: Double = 40.0

    /** Adaptive history spike threshold (%) for daily-or-coarser fiat series (7D / 1M / 1Y). */
    const val HISTORY_SPIKE_THRESHOLD_FIAT_MULTI_DAY_PCT: Double = 15.0

    /** Adaptive history spike threshold (%) for daily-or-coarser gold/coin series (7D / 1M / 1Y). */
    const val HISTORY_SPIKE_THRESHOLD_GOLD_MULTI_DAY_PCT: Double = 15.0

    /** Default corroboration timestamp tolerance (1 hour) when range is unspecified. */
    const val HISTORY_CORROBORATION_DEFAULT_TOLERANCE_SEC: Long = 3_600L

    /** Corroboration timestamp tolerance for 7D 4-hour candle buckets (2 hours). */
    const val HISTORY_CORROBORATION_D7_TOLERANCE_SEC: Long = 2L * 3_600L

    /** Corroboration timestamp tolerance for daily 1M / 1Y candle buckets (12 hours). */
    const val HISTORY_CORROBORATION_DAILY_TOLERANCE_SEC: Long = 12L * 3_600L

    /**
     * Returns the single-tick reverting spike threshold (%) calibrated by asset [category] and [range].
     * - 24H: 15% for every category.
     * - 7D / 1M / 1Y (daily or coarser buckets): 40% for crypto, 15% for fiat and gold.
     */
    fun spikeThresholdPct(category: AssetCategory, range: TimeRange): Double = when (range) {
        TimeRange.H24 -> HISTORY_SPIKE_THRESHOLD_24H_PCT
        TimeRange.D7, TimeRange.M1, TimeRange.Y1 -> when (category) {
            AssetCategory.CRYPTO -> HISTORY_SPIKE_THRESHOLD_CRYPTO_MULTI_DAY_PCT
            AssetCategory.FIAT -> HISTORY_SPIKE_THRESHOLD_FIAT_MULTI_DAY_PCT
            AssetCategory.GOLD -> HISTORY_SPIKE_THRESHOLD_GOLD_MULTI_DAY_PCT
            AssetCategory.ALL, AssetCategory.PINNED -> HISTORY_SPIKE_THRESHOLD_PCT
        }
    }

    /**
     * Returns bucket-sized timestamp tolerance (seconds) for cross-provider spike corroboration.
     */
    fun corroborationToleranceSec(range: TimeRange?): Long = when (range) {
        TimeRange.H24 -> HISTORY_CORROBORATION_DEFAULT_TOLERANCE_SEC
        TimeRange.D7 -> HISTORY_CORROBORATION_D7_TOLERANCE_SEC
        TimeRange.M1, TimeRange.Y1 -> HISTORY_CORROBORATION_DAILY_TOLERANCE_SEC
        null -> HISTORY_CORROBORATION_DEFAULT_TOLERANCE_SEC
    }

    /** Maximum source timestamp age (5 minutes) for a fetched crypto asset to be marked LIVE. */
    const val CRYPTO_LIVE_MAX_AGE_MILLIS: Long = 5L * 60L * 1000L

    /** Maximum source timestamp age (30 minutes) for a fetched fiat or gold asset to be marked LIVE. */
    const val FIAT_GOLD_LIVE_MAX_AGE_MILLIS: Long = 30L * 60L * 1000L

    /** Minimum source count required to take a consensus median and exclude outliers. */
    const val CONSENSUS_MEDIAN_MIN_SOURCES: Int = 3

    /** Exact two-source count: on disagreement (> threshold), primary source wins and asset is marked disputed. */
    const val DISPUTED_PAIR_SOURCE_COUNT: Int = 2

    /** Single-source count: accepted and marked singleSource. */
    const val SINGLE_SOURCE_COUNT: Int = 1

    /** Allowed future clock skew tolerance (2 minutes) when computing source timestamp age. */
    const val CLOCK_SKEW_TOLERANCE_MILLIS: Long = 2L * 60L * 1000L

    /** Standard troy ounce mass in grams. */
    const val TROY_OUNCE_GRAMS: Double = 31.1035

    /** 18K gold fineness ratio in per-mille. */
    const val GOLD_18K_FINENESS_PER_MILLE: Int = 750

    /**
     * Gold Mesghal equivalence in 18K grams: 1 Mesghal (4.608 g at 705‰ commercial benchmark)
     * ≈ 4.3318 g of 18K gold (750‰).
     */
    const val MESGHAL_18K_EQUIVALENT_GRAMS: Double = 4.3318

    data class CoinSpec(
        val assetId: String,
        val weightGrams: Double,
        val finenessPerMille: Int
    ) {
        val pureGoldGrams: Double
            get() = weightGrams * (finenessPerMille / 1000.0)
    }

    /**
     * Commonly cited gold coin specifications, NOT verified against an official primary mint source:
     * - Full Bahar Azadi (Emami / Old Design): 8.133 g at 900‰ fineness (21.6K)
     * - Half Bahar Azadi: 4.066 g at 900‰ fineness
     * - Quarter Bahar Azadi: 2.033 g at 900‰ fineness
     * - Gerami (One-Gram Coin): 1.01 g at 900‰ fineness — UNVERIFIED against an official CBI/mint source.
     */
    val COIN_SPECS: Map<String, CoinSpec> = mapOf(
        "COIN_EMAMI" to CoinSpec("COIN_EMAMI", weightGrams = 8.133, finenessPerMille = 900),
        "COIN_BAHAR" to CoinSpec("COIN_BAHAR", weightGrams = 8.133, finenessPerMille = 900),
        "COIN_HALF" to CoinSpec("COIN_HALF", weightGrams = 4.066, finenessPerMille = 900),
        "COIN_QUARTER" to CoinSpec("COIN_QUARTER", weightGrams = 2.033, finenessPerMille = 900),
        "COIN_GERAMI" to CoinSpec("COIN_GERAMI", weightGrams = 1.01, finenessPerMille = 900)
    )
}
