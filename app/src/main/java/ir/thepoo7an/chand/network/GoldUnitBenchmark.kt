package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Cross-checks domestic gold and coin quotes against a benchmark derived from data the app already
 * fetched, and flags a *unit* mix-up (gram vs mesghal, 18K vs 24K, Toman vs Rial) instead of showing
 * a confidently wrong number.
 *
 * The benchmark is one troy ounce of spot gold converted to Toman and down to an 18K gram:
 *
 * ```
 * gram24Toman = XAU_USD / 31.1035 * USD_TMN
 * gram18Toman = gram24Toman * 0.750            // 18/24 karat
 * ```
 *
 * Note the troy-ounce division: `XAU_USD * USD_TMN` is the price of an **ounce** in Toman, which is
 * ~31× a gram. Comparing a per-gram quote against the un-divided product flags every single asset,
 * so the conversion is part of the definition rather than the caller's responsibility.
 *
 * Everything else is expressed as a fixed multiple of that 18K gram in [EIGHTEEN_KARAT_GRAM_EQUIVALENTS]
 * (weights and fineness come from [MarketValidationConfig.COIN_SPECS]), so a wrong unit surfaces as a
 * ~2× or ~10× deviation rather than as a plausible-looking price.
 *
 * The check is skipped silently unless **both** inputs are fresh and non-zero; a stale XAU or a
 * missing USD rate is not evidence of anything.
 */
object GoldUnitBenchmark {

    /** Deviation above which the quote is treated as a unit mix-up rather than market premium. */
    const val DEVIATION_FLAG_PERCENT: Double = 20.0

    /**
     * 18K-gram equivalents per unit of each domestic asset. Data-driven on purpose: the single place
     * to correct a weight or fineness is here (and [MarketValidationConfig.COIN_SPECS] for coins).
     */
    val EIGHTEEN_KARAT_GRAM_EQUIVALENTS: Map<String, Double> = buildMap {
        val karat18 = MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0
        put("GOLD_18K", 1.0)
        put("GOLD_24K", 1.0 / karat18)
        put("GOLD_MESGHAL", MarketValidationConfig.MESGHAL_18K_EQUIVALENT_GRAMS)
        MarketValidationConfig.COIN_SPECS.forEach { (id, spec) ->
            put(id, spec.pureGoldGrams / karat18)
        }
    }

    /** 18K-gram equivalent for [assetId], or `null` when the asset is not a gold/coin unit. */
    fun eighteenKaratGramEquivalent(assetId: String): Double? = EIGHTEEN_KARAT_GRAM_EQUIVALENTS[assetId]

    /** Benchmark price of one 18K gram in Toman, or `null` when the inputs are not usable. */
    fun gram18Toman(xauUsdPerOunce: Double?, usdToman: Double?): Double? {
        if (xauUsdPerOunce == null || usdToman == null) return null
        if (!xauUsdPerOunce.isFinite() || !usdToman.isFinite()) return null
        if (xauUsdPerOunce <= 0.0 || usdToman <= 0.0) return null
        val karat18 = MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0
        return (xauUsdPerOunce / MarketValidationConfig.TROY_OUNCE_GRAMS) * usdToman * karat18
    }

    /** Expected Toman price for [assetId], or `null` when there is no benchmark for it. */
    fun expectedToman(assetId: String, xauUsdPerOunce: Double?, usdToman: Double?): Double? {
        val multiple = eighteenKaratGramEquivalent(assetId) ?: return null
        val gram18 = gram18Toman(xauUsdPerOunce, usdToman) ?: return null
        return gram18 * multiple
    }

    /** Absolute percentage deviation of [actual] from [expected]; `null` for unusable inputs. */
    fun deviationPercent(actual: Double, expected: Double?): Double? {
        if (expected == null || !expected.isFinite() || expected <= 0.0) return null
        if (!actual.isFinite() || actual <= 0.0) return null
        return abs(actual - expected) / expected * 100.0
    }

    /** True when [actual] is far enough from the benchmark to be a unit mix-up. */
    fun isUnitMismatch(actual: Double, expected: Double?): Boolean {
        val deviation = deviationPercent(actual, expected) ?: return false
        return deviation > DEVIATION_FLAG_PERCENT
    }

    /**
     * Returns [assets] with `disputed`/`unitMismatch` set on every gold or coin quote that deviates
     * from the benchmark by more than [DEVIATION_FLAG_PERCENT]. Returns the input list untouched —
     * no partial flags, no reordering — when the XAU or USD basis is missing or stale.
     */
    fun flagUnitMixups(assets: List<MarketAsset>): List<MarketAsset> {
        val xau = assets.firstOrNull { it.id == XAU_ID && it.isFreshBasis() }
            ?.priceUsd?.takeIf { it > 0.0 }
            ?: return assets
        val usd = assets.firstOrNull { it.id == USD_ID && it.isFreshBasis() }
            ?.priceToman?.takeIf { it > 0.0 }
            ?: return assets
        val gram18 = gram18Toman(xau, usd) ?: return assets

        var changed = false
        val result = assets.map { asset ->
            val multiple = eighteenKaratGramEquivalent(asset.id) ?: return@map asset
            val expected = gram18 * multiple
            if (!isUnitMismatch(asset.priceToman, expected)) return@map asset
            changed = true
            asset.copy(disputed = true, unitMismatch = true)
        }
        return if (changed) result else assets
    }

    private const val XAU_ID = "XAU_OZ"
    private const val USD_ID = "USD"

    /** A basis quote counts only when it is live-ish and carries a usable value. */
    private fun MarketAsset.isFreshBasis(): Boolean =
        freshness == DataFreshness.LIVE || freshness == DataFreshness.DELAYED
}
