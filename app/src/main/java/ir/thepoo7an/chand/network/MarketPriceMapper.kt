package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset

/**
 * Pure, deterministic price-mapping, cross-source validation, and normalization engine for Chand?!.
 *
 * Public facade preserving the stable [MarketPriceMapper] API while delegating implementations
 * by concern/asset class to:
 * - [MarketValidationHelpers]: cross-source consensus, freshness derivation, sparkline scaling, offline baseline.
 * - [FiatPriceMapper]: free-market USD anchor resolution and global fiat currencies.
 * - [CryptoPriceMapper]: USDT median and crypto exchange consensus.
 * - [GoldPriceMapper]: XAU spot, domestic gold benchmarks, and physical CBI coins.
 *
 * Documented Provider Fallback Order per Asset Class:
 * 1. **Free-market USD benchmark (`USD`)**:
 *    1) TGJU (`price_dollar_rl`) -> 2) TGJU summary-table latest -> 3) Real-time 24/7 USDT median
 *    (`Wallex` -> `Bitpin` -> `Nobitex`) -> 4) Cached USD anchor (`CACHED`) ->
 *    5) Baseline offline reference (`UNAVAILABLE`).
 * 2. **Global Fiat Currencies (`EUR`, `AED`, `GBP`, ...)**:
 *    1) TGJU direct Toman quote (`price_*`, normalized for 100-unit JPY / 1000-unit IQD) ->
 *    2) Cross-source FX consensus (`Frankfurter ECB` -> `Fawazahmed0 Currency-API` -> `Open ExchangeRate-API`)
 *       combined with free-market `USD/TMN` (`usdToman / fxPerUsd`).
 * 3. **Cryptocurrencies (`USDT`, `BTC`, `ETH`, ...)**:
 *    - `USDT`: Cross-source consensus across `Wallex` (primary), `Bitpin`, `Nobitex` (`>3%` rule applied).
 *    - Non-USDT: Cross-source consensus across `Wallex TMN` (primary), `Bitpin IRT`, and `Binance USDT * usdtToman`.
 * 4. **Gold & Physical Coins**:
 *    - Global spot (`XAU_OZ`): Cross-source USD spot consensus (`GoldAPI` primary, `Binance PAXG`,
 *      `CurrencyAPI XAU`, `TGJU ons`) multiplied by `usdToman`.
 *    - Benchmark gold (`GOLD_18K`, `GOLD_24K`, `GOLD_MESGHAL`): TGJU direct quote, or derived from `XAU_OZ * usdToman`.
 *    - Physical CBI coins (`COIN_EMAMI`, `COIN_BAHAR`, `COIN_HALF`, `COIN_QUARTER`, `COIN_GERAMI`):
 *      TGJU market quote (with separately computed intrinsic melt value & bubble estimate), else last valid
 *      cached coin quote (`CACHED`), else `UNAVAILABLE` (never fabricated from fixed multipliers).
 */
object MarketPriceMapper {

    const val DEFAULT_OFFLINE_USD_TOMAN = 68_500.0
    const val DEFAULT_OFFLINE_ONS_USD = 2_650.0

    data class TgjuQuote(
        val priceToman: Double,
        val openToman: Double?,
        val highToman: Double? = null,
        val lowToman: Double? = null,
        val changeToman: Double,
        val changePct: Double,
        val timestampMillis: Long = 0L
    )

    data class ExchangeQuote(
        val priceToman: Double?,
        val priceUsd: Double?,
        val changePct: Double,
        val highToman: Double? = null,
        val lowToman: Double? = null,
        val highUsd: Double? = null,
        val lowUsd: Double? = null,
        val timestampMillis: Long = 0L
    )

    data class PriceCandidate(
        val sourceName: String,
        val price: Double,
        val isPrimary: Boolean = false,
        val timestampMillis: Long = 0L,
        val changePct: Double = 0.0,
        val highToman: Double? = null,
        val lowToman: Double? = null
    )

    data class ConsensusResult(
        val price: Double,
        val disputed: Boolean,
        val singleSource: Boolean,
        val acceptedSources: List<String>,
        val excludedSources: List<String>,
        val sourceTimestampMillis: Long,
        val selectedCandidate: PriceCandidate
    )

    data class ResolvedUsdRate(
        val usdToman: Double,
        val usdtToman: Double,
        val usdChangePct: Double,
        val usdChangeToman: Double,
        val usdtChangePct: Double,
        val rateBasis: TomanRateBasis,
        val usdSourceLabel: String,
        val usdFreshness: DataFreshness = DataFreshness.LIVE,
        val usdtFreshness: DataFreshness = DataFreshness.LIVE,
        val usdSourceTimestampMillis: Long = 0L,
        val usdtSourceTimestampMillis: Long = 0L,
        val usdDisputed: Boolean = false,
        val usdSingleSource: Boolean = false,
        val usdtDisputed: Boolean = false,
        val usdtSingleSource: Boolean = false
    )

    data class FiatSpec(
        val id: String,
        val symbol: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val accentHex: Long,
        val tgjuKey: String,
        val fxCode: String
    )

    data class CryptoSpec(
        val id: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val accentHex: Long
    )

    data class GoldSpec(
        val id: String,
        val symbol: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val tgjuKey: String
    )

    val SUPPORTED_FIATS: List<FiatSpec> = FiatPriceMapper.SUPPORTED_FIATS

    val SUPPORTED_CRYPTOS: List<CryptoSpec> = CryptoPriceMapper.SUPPORTED_CRYPTOS

    val SUPPORTED_GOLDS: List<GoldSpec> = GoldPriceMapper.SUPPORTED_GOLDS

    /**
     * Cross-source validation rule (Decisions):
     * - >= 3 valid sources: take cross-source median and exclude outliers deviating > [thresholdPct]%.
     * - 2 valid sources: if deviation from median <= [thresholdPct]%, use midpoint consensus;
     *   if they disagree (> [thresholdPct]%), use primary source and mark `disputed = true`.
     * - 1 valid source: accept it and mark `singleSource = true`.
     */
    fun validateCrossSourcePrices(
        candidates: List<PriceCandidate>,
        thresholdPct: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): ConsensusResult? = MarketValidationHelpers.validateCrossSourcePrices(
        candidates = candidates,
        thresholdPct = thresholdPct,
        nowMillis = nowMillis
    )

    /**
     * Derives [DataFreshness] per Decisions:
     * - LIVE: fetched in current refresh and timestamp age <= 5m (crypto) or 30m (fiat/gold).
     * - DELAYED: fetched in current refresh from a live source, but timestamp age > maxAge.
     * - CACHED: served from persisted cache or derived from cached anchor.
     * - UNAVAILABLE: no usable value.
     */
    fun deriveFreshness(
        category: AssetCategory,
        fetchedThisRefresh: Boolean,
        hasUsableValue: Boolean,
        isCachedSource: Boolean,
        sourceTimestampMillis: Long,
        nowMillis: Long
    ): DataFreshness = MarketValidationHelpers.deriveFreshness(
        category = category,
        fetchedThisRefresh = fetchedThisRefresh,
        hasUsableValue = hasUsableValue,
        isCachedSource = isCachedSource,
        sourceTimestampMillis = sourceTimestampMillis,
        nowMillis = nowMillis
    )

    fun computeUsdtMedianToman(
        wallexUsdtToman: Double?,
        bitpinUsdtToman: Double?,
        nobitexUsdtToman: Double?
    ): Double? = FiatPriceMapper.computeUsdtMedianToman(
        wallexUsdtToman = wallexUsdtToman,
        bitpinUsdtToman = bitpinUsdtToman,
        nobitexUsdtToman = nobitexUsdtToman
    )

    fun resolveFreeMarketUsd(
        tgjuUsdQuote: TgjuQuote?,
        wallexUsdt: ExchangeQuote?,
        bitpinUsdt: ExchangeQuote?,
        nobitexUsdtToman: Double?,
        usdtSpark: List<Double>,
        tgjuTableFallbackDelta: Pair<Double, Double>?,
        lastKnownUsdToman: Double?,
        nowMillis: Long = System.currentTimeMillis(),
        nobitexTimestampMillis: Long = nowMillis,
        cachedUsdTimestampMillis: Long = 0L
    ): ResolvedUsdRate = FiatPriceMapper.resolveFreeMarketUsd(
        tgjuUsdQuote = tgjuUsdQuote,
        wallexUsdt = wallexUsdt,
        bitpinUsdt = bitpinUsdt,
        nobitexUsdtToman = nobitexUsdtToman,
        usdtSpark = usdtSpark,
        tgjuTableFallbackDelta = tgjuTableFallbackDelta,
        lastKnownUsdToman = lastKnownUsdToman,
        nowMillis = nowMillis,
        nobitexTimestampMillis = nobitexTimestampMillis,
        cachedUsdTimestampMillis = cachedUsdTimestampMillis
    )

    fun mapFiatAsset(
        spec: FiatSpec,
        usdToman: Double,
        usdChangePct: Double,
        tgjuQuote: TgjuQuote?,
        fxPerUsd: Double?,
        usdtSpark: List<Double>,
        nowMillis: Long,
        currencySpark: List<Double> = emptyList(),
        fxConsensus: ConsensusResult? = null,
        usdBasisFreshness: DataFreshness = DataFreshness.LIVE,
        usdSourceTimestampMillis: Long = nowMillis
    ): MarketAsset? = FiatPriceMapper.mapFiatAsset(
        spec = spec,
        usdToman = usdToman,
        usdChangePct = usdChangePct,
        tgjuQuote = tgjuQuote,
        fxPerUsd = fxPerUsd,
        usdtSpark = usdtSpark,
        nowMillis = nowMillis,
        currencySpark = currencySpark,
        fxConsensus = fxConsensus,
        usdBasisFreshness = usdBasisFreshness,
        usdSourceTimestampMillis = usdSourceTimestampMillis
    )

    fun mapCryptoAsset(
        spec: CryptoSpec,
        usdtToman: Double,
        usdtChangePct: Double,
        wallex: ExchangeQuote?,
        bitpin: ExchangeQuote?,
        binance: ExchangeQuote?,
        nobitexUsdtToman: Double?,
        sparkInput: List<Double>,
        nowMillis: Long,
        usdtBasisFreshness: DataFreshness = DataFreshness.LIVE,
        usdtSourceTimestampMillis: Long = nowMillis,
        nobitexTimestampMillis: Long = nowMillis
    ): MarketAsset? = CryptoPriceMapper.mapCryptoAsset(
        spec = spec,
        usdtToman = usdtToman,
        usdtChangePct = usdtChangePct,
        wallex = wallex,
        bitpin = bitpin,
        binance = binance,
        nobitexUsdtToman = nobitexUsdtToman,
        sparkInput = sparkInput,
        nowMillis = nowMillis,
        usdtBasisFreshness = usdtBasisFreshness,
        usdtSourceTimestampMillis = usdtSourceTimestampMillis,
        nobitexTimestampMillis = nobitexTimestampMillis
    )

    /**
     * Maps all Gold & Physical Coin assets cleanly:
     * - Cross-validates `XAU_OZ` USD spot across GoldAPI (primary), Binance PAXG, CurrencyAPI XAU, and TGJU ons.
     * - Computes coin intrinsic melt value separately using verified CBI specs (`weightGrams * finenessPerMille / 1000.0`).
     * - Keeps TGJU coin market quote strictly separate from intrinsic/bubble estimates.
     * - When TGJU has no coin quote, serves last valid cached coin (`CACHED`) or marks `UNAVAILABLE`
     *   (priceToman = 0.0), never fabricating prices with fixed multipliers.
     */
    fun mapGoldAssets(
        tgju: Map<String, TgjuQuote>,
        usdToman: Double,
        usdChangePct: Double,
        usdBasisFreshness: DataFreshness,
        usdSourceTimestampMillis: Long,
        goldSpotCandidates: List<PriceCandidate>,
        paxgQuote: ExchangeQuote?,
        tgjuGoldRecent: List<AssetHistoryPoint>,
        goldSpark: List<Double>,
        usdtSpark: List<Double>,
        cachedAssetsById: Map<String, MarketAsset>,
        nowMillis: Long,
        computeTableDelta: (List<AssetHistoryPoint>, Double) -> Pair<Double, Double>? = { _, _ -> null }
    ): List<MarketAsset> = GoldPriceMapper.mapGoldAssets(
        tgju = tgju,
        usdToman = usdToman,
        usdChangePct = usdChangePct,
        usdBasisFreshness = usdBasisFreshness,
        usdSourceTimestampMillis = usdSourceTimestampMillis,
        goldSpotCandidates = goldSpotCandidates,
        paxgQuote = paxgQuote,
        tgjuGoldRecent = tgjuGoldRecent,
        goldSpark = goldSpark,
        usdtSpark = usdtSpark,
        cachedAssetsById = cachedAssetsById,
        nowMillis = nowMillis,
        computeTableDelta = computeTableDelta
    )

    /**
     * Last-resort offline baseline used ONLY when all live APIs fail AND no persisted cache exists
     * (e.g., cold offline first boot). Clearly surfaced with Offline Reference label & banner,
     * never marked LIVE.
     */
    fun offlineReferenceCatalog(nowMillis: Long = System.currentTimeMillis()): CachedMarketSnapshot =
        MarketValidationHelpers.offlineReferenceCatalog(nowMillis = nowMillis)

    fun scaleSeriesToCurrent(values: List<Double>, targetCurrent: Double): List<Double> =
        MarketValidationHelpers.scaleSeriesToCurrent(
            values = values,
            targetCurrent = targetCurrent
        )

    fun computeSeriesChangePct(series: List<Double>): Double? =
        MarketValidationHelpers.computeSeriesChangePct(series = series)
}
