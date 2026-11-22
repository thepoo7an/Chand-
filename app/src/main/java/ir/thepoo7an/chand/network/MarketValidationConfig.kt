package ir.thepoo7an.chand.network

/**
 * Single centralized configuration for market data validation, cross-source consensus,
 * freshness thresholds, and physical gold coin specifications.
 *
 * Defaults follow Stage 2 Decisions and Central Bank of Iran (CBI) official coin mint standards.
 */
object MarketValidationConfig {

    /** Maximum allowed deviation (%) from cross-source median for cryptocurrency prices. */
    const val CRYPTO_DISAGREEMENT_THRESHOLD_PCT: Double = 3.0

    /** Maximum allowed deviation (%) from cross-source median for fiat / free-market prices. */
    const val FIAT_DISAGREEMENT_THRESHOLD_PCT: Double = 3.0

    /**
     * Minimum jump (%) followed by reversion considered a single-tick history spike (Stage 3).
     * Kept here alongside all market validation thresholds.
     */
    const val HISTORY_SPIKE_THRESHOLD_PCT: Double = 15.0

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
     * Official Central Bank of Iran (CBI) gold coin specifications:
     * - Full Bahar Azadi (Emami / Old Design): 8.133 g at 900‰ fineness (21.6K)
     * - Half Bahar Azadi: 4.066 g at 900‰ fineness
     * - Quarter Bahar Azadi: 2.033 g at 900‰ fineness
     * - Gerami (One-Gram Coin): 1.01 g at 900‰ fineness
     */
    val COIN_SPECS: Map<String, CoinSpec> = mapOf(
        "COIN_EMAMI" to CoinSpec("COIN_EMAMI", weightGrams = 8.133, finenessPerMille = 900),
        "COIN_BAHAR" to CoinSpec("COIN_BAHAR", weightGrams = 8.133, finenessPerMille = 900),
        "COIN_HALF" to CoinSpec("COIN_HALF", weightGrams = 4.066, finenessPerMille = 900),
        "COIN_QUARTER" to CoinSpec("COIN_QUARTER", weightGrams = 2.033, finenessPerMille = 900),
        "COIN_GERAMI" to CoinSpec("COIN_GERAMI", weightGrams = 1.01, finenessPerMille = 900)
    )
}
