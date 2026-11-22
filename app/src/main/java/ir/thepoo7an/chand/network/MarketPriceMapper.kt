package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Pure, deterministic price-mapping, cross-source validation, and normalization engine for Chand?!.
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

    val SUPPORTED_FIATS = listOf(
        FiatSpec("EUR", "EUR", "یورو", "Euro", "🇪🇺", 0xFF3B82F6, "price_eur", "EUR"),
        FiatSpec("AED", "AED", "درهم امارات", "UAE Dirham", "🇦🇪", 0xFF14B8A6, "price_aed", "AED"),
        FiatSpec("GBP", "GBP", "پوند انگلیس", "British Pound", "🇬🇧", 0xFF6366F1, "price_gbp", "GBP"),
        FiatSpec("KWD", "KWD", "دینار کویت", "Kuwaiti Dinar", "🇰🇼", 0xFF059669, "price_kwd", "KWD"),
        FiatSpec("TRY", "TRY", "لیر ترکیه", "Turkish Lira", "🇹🇷", 0xFFEF4444, "price_try", "TRY"),
        FiatSpec("CAD", "CAD", "دلار کانادا", "Canadian Dollar", "🇨🇦", 0xFFF43F5E, "price_cad", "CAD"),
        FiatSpec("CHF", "CHF", "فرانک سوئیس", "Swiss Franc", "🇨🇭", 0xFFEC4899, "price_chf", "CHF"),
        FiatSpec("CNY", "CNY", "یوان چین", "Chinese Yuan", "🇨🇳", 0xFFF59E0B, "price_cny", "CNY"),
        FiatSpec("AUD", "AUD", "دلار استرالیا", "Australian Dollar", "🇦🇺", 0xFF06B6D4, "price_aud", "AUD"),
        FiatSpec("JPY", "JPY", "ین ژاپن", "Japanese Yen", "🇯🇵", 0xFFFB7185, "price_jpy", "JPY"),
        FiatSpec("SAR", "SAR", "ریال عربستان", "Saudi Riyal", "🇸🇦", 0xFF22C55E, "price_sar", "SAR"),
        FiatSpec("IQD", "IQD", "دینار عراق", "Iraqi Dinar", "🇮🇶", 0xFF84CC16, "price_iqd", "IQD")
    )

    val SUPPORTED_CRYPTOS = listOf(
        CryptoSpec("USDT", "تتر", "Tether", "₮", 0xFF26A17B),
        CryptoSpec("BTC", "بیت‌کوین", "Bitcoin", "₿", 0xFFF7931A),
        CryptoSpec("ETH", "اتریوم", "Ethereum", "Ξ", 0xFF627EEA),
        CryptoSpec("TON", "تون‌کوین", "Toncoin", "◆", 0xFF0098EA),
        CryptoSpec("SOL", "سولانا", "Solana", "◎", 0xFF9945FF),
        CryptoSpec("BNB", "بایننس‌کوین", "BNB", "◈", 0xFFF3BA2F),
        CryptoSpec("XRP", "ریپل", "XRP", "✕", 0xFF38BDF8),
        CryptoSpec("DOGE", "دوج‌کوین", "Dogecoin", "Ð", 0xFFC2A633)
    )

    val SUPPORTED_GOLDS = listOf(
        GoldSpec("GOLD_18K", "GRAM", "طلای ۱۸ عیار", "18K Gold", "✨", "geram18"),
        GoldSpec("GOLD_24K", "GRAM24", "طلای ۲۴ عیار", "24K Gold", "✨", "geram24"),
        GoldSpec("GOLD_MESGHAL", "MESGHAL", "مثقال طلا", "Gold Mesghal", "⚖️", "mesghal"),
        GoldSpec("COIN_EMAMI", "SEKE", "سکه امامی", "Emami Coin", "🪙", "sekee"),
        GoldSpec("COIN_BAHAR", "BAHAR", "سکه بهار آزادی", "Bahar Azadi", "🪙", "sekeb"),
        GoldSpec("COIN_HALF", "HALF", "نیم سکه", "1/2 Azadi", "🥇", "nim"),
        GoldSpec("COIN_QUARTER", "QUARTER", "ربع سکه", "1/4 Azadi", "🏅", "rob"),
        GoldSpec("COIN_GERAMI", "GERAMI", "سکه گرمی", "Gerami Coin", "🟡", "gerami"),
        GoldSpec("XAU_OZ", "XAU", "انس جهانی طلا", "Gold Ounce", "🏆", "ons")
    )

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
    ): ConsensusResult? {
        val valid = candidates.filter { it.price > 0.0 && it.price.isFinite() }
        if (valid.isEmpty()) return null

        if (valid.size == MarketValidationConfig.SINGLE_SOURCE_COUNT) {
            val sole = valid.first()
            val ts = sole.timestampMillis.takeIf { it > 0L } ?: nowMillis
            return ConsensusResult(
                price = sole.price,
                disputed = false,
                singleSource = true,
                acceptedSources = listOf(sole.sourceName),
                excludedSources = emptyList(),
                sourceTimestampMillis = ts,
                selectedCandidate = sole
            )
        }

        val sorted = valid.sortedBy { it.price }
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].price
        } else {
            (sorted[sorted.size / 2 - 1].price + sorted[sorted.size / 2].price) / 2.0
        }

        val primary = valid.firstOrNull { it.isPrimary } ?: valid.first()

        if (valid.size == MarketValidationConfig.DISPUTED_PAIR_SOURCE_COUNT) {
            val maxDevPct = valid.maxOf { devPct(it.price, median) }
            return if (maxDevPct > thresholdPct) {
                val ts = primary.timestampMillis.takeIf { it > 0L } ?: nowMillis
                ConsensusResult(
                    price = primary.price,
                    disputed = true,
                    singleSource = false,
                    acceptedSources = listOf(primary.sourceName),
                    excludedSources = valid.filter { it !== primary }.map { it.sourceName },
                    sourceTimestampMillis = ts,
                    selectedCandidate = primary
                )
            } else {
                val stalestTs = valid.minOf { it.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis }
                ConsensusResult(
                    price = median,
                    disputed = false,
                    singleSource = false,
                    acceptedSources = valid.map { it.sourceName },
                    excludedSources = emptyList(),
                    sourceTimestampMillis = stalestTs,
                    selectedCandidate = primary
                )
            }
        }

        // >= 3 sources: use median and exclude outliers deviating > thresholdPct from median
        val inliers = valid.filter { devPct(it.price, median) <= thresholdPct }
        val outliers = valid.filter { devPct(it.price, median) > thresholdPct }

        return if (inliers.size >= 2) {
            val stalestTs = inliers.minOf { it.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis }
            val chosen = inliers.firstOrNull { it.isPrimary } ?: inliers.first()
            ConsensusResult(
                price = median,
                disputed = false,
                singleSource = false,
                acceptedSources = inliers.map { it.sourceName },
                excludedSources = outliers.map { it.sourceName },
                sourceTimestampMillis = stalestTs,
                selectedCandidate = chosen
            )
        } else {
            // Multiple sources spread widely with <2 corroborating inliers -> fall back to primary & mark disputed
            val chosen = if (inliers.size == 1 && primary !in inliers) primary else primary
            val ts = chosen.timestampMillis.takeIf { it > 0L } ?: nowMillis
            ConsensusResult(
                price = if (inliers.size == 1) median else chosen.price,
                disputed = true,
                singleSource = false,
                acceptedSources = listOf(chosen.sourceName),
                excludedSources = valid.filter { it !== chosen }.map { it.sourceName },
                sourceTimestampMillis = ts,
                selectedCandidate = chosen
            )
        }
    }

    private fun devPct(value: Double, reference: Double): Double {
        if (reference <= 0.0) return Double.POSITIVE_INFINITY
        return (abs(value - reference) / reference) * 100.0
    }

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
    ): DataFreshness {
        if (!hasUsableValue) return DataFreshness.UNAVAILABLE
        if (isCachedSource) return DataFreshness.CACHED
        if (!fetchedThisRefresh) return DataFreshness.CACHED

        val effectiveTs = sourceTimestampMillis.takeIf { it > 0L } ?: nowMillis
        val ageMillis = (nowMillis - effectiveTs).coerceAtLeast(0L)
        val maxAge = when (category) {
            AssetCategory.CRYPTO -> MarketValidationConfig.CRYPTO_LIVE_MAX_AGE_MILLIS
            AssetCategory.FIAT, AssetCategory.GOLD -> MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS
            AssetCategory.ALL, AssetCategory.PINNED -> MarketValidationConfig.FIAT_GOLD_LIVE_MAX_AGE_MILLIS
        }
        return if (ageMillis <= maxAge) DataFreshness.LIVE else DataFreshness.DELAYED
    }

    fun computeUsdtMedianToman(
        wallexUsdtToman: Double?,
        bitpinUsdtToman: Double?,
        nobitexUsdtToman: Double?
    ): Double? {
        val candidates = listOfNotNull(
            wallexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate("Wallex", it, isPrimary = true)
            },
            bitpinUsdtToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate("Bitpin", it, isPrimary = false)
            },
            nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate("Nobitex", it, isPrimary = false)
            }
        )
        return validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT
        )?.price
    }

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
    ): ResolvedUsdRate {
        val usdtCandidates = listOfNotNull(
            wallexUsdt?.priceToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate(
                    sourceName = "Wallex",
                    price = it,
                    isPrimary = true,
                    timestampMillis = wallexUsdt.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = wallexUsdt.changePct
                )
            },
            bitpinUsdt?.priceToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate(
                    sourceName = "Bitpin",
                    price = it,
                    isPrimary = wallexUsdt?.priceToman == null,
                    timestampMillis = bitpinUsdt.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = bitpinUsdt.changePct
                )
            },
            nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                PriceCandidate(
                    sourceName = "Nobitex",
                    price = it,
                    isPrimary = wallexUsdt?.priceToman == null && bitpinUsdt?.priceToman == null,
                    timestampMillis = nobitexTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                )
            }
        )

        val usdtConsensus = validateCrossSourcePrices(
            candidates = usdtCandidates,
            thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = nowMillis
        )

        val anchorUsd = lastKnownUsdToman?.takeIf { it > 1_000.0 } ?: DEFAULT_OFFLINE_USD_TOMAN

        val usdtToman = usdtConsensus?.price ?: tgjuUsdQuote?.priceToman ?: anchorUsd
        val usdtChangePct = wallexUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpinUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: computeSeriesChangePct(usdtSpark)
            ?: tgjuUsdQuote?.changePct
            ?: 0.0

        val usdtFreshness = when {
            usdtConsensus != null -> deriveFreshness(
                category = AssetCategory.CRYPTO,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = usdtConsensus.sourceTimestampMillis,
                nowMillis = nowMillis
            )
            tgjuUsdQuote != null -> deriveFreshness(
                category = AssetCategory.FIAT,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = tgjuUsdQuote.timestampMillis,
                nowMillis = nowMillis
            )
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> DataFreshness.CACHED
            else -> DataFreshness.UNAVAILABLE
        }
        val usdtTs = when {
            usdtConsensus != null -> usdtConsensus.sourceTimestampMillis
            tgjuUsdQuote != null -> tgjuUsdQuote.timestampMillis.takeIf { it > 0L } ?: nowMillis
            else -> cachedUsdTimestampMillis
        }

        val usdToman: Double
        val basis: TomanRateBasis
        val sourceLabel: String
        val usdFreshness: DataFreshness
        val usdTs: Long
        var usdDisputed = false
        var usdSingleSource = false

        when {
            tgjuUsdQuote != null -> {
                usdToman = tgjuUsdQuote.priceToman
                basis = TomanRateBasis.TEHRAN_FREE_MARKET
                sourceLabel = "TGJU (Free)"
                usdTs = tgjuUsdQuote.timestampMillis.takeIf { it > 0L } ?: nowMillis
                usdFreshness = deriveFreshness(
                    category = AssetCategory.FIAT,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = usdTs,
                    nowMillis = nowMillis
                )
                // Cross-check TGJU cash USD against USDT free-market benchmark if available
                if (usdtConsensus != null) {
                    val pair = validateCrossSourcePrices(
                        candidates = listOf(
                            PriceCandidate("TGJU", tgjuUsdQuote.priceToman, isPrimary = true, timestampMillis = usdTs),
                            PriceCandidate("USDT", usdtConsensus.price, isPrimary = false, timestampMillis = usdtTs)
                        ),
                        thresholdPct = MarketValidationConfig.FIAT_DISAGREEMENT_THRESHOLD_PCT,
                        nowMillis = nowMillis
                    )
                    usdDisputed = pair?.disputed == true
                } else {
                    usdSingleSource = true
                }
            }
            usdtConsensus != null -> {
                usdToman = usdtConsensus.price
                basis = TomanRateBasis.USDT_FREE_MARKET_MEDIAN
                sourceLabel = "USDT Median (Free)"
                usdTs = usdtConsensus.sourceTimestampMillis
                usdFreshness = deriveFreshness(
                    category = AssetCategory.FIAT,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = usdTs,
                    nowMillis = nowMillis
                )
                usdDisputed = usdtConsensus.disputed
                usdSingleSource = usdtConsensus.singleSource
            }
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> {
                usdToman = lastKnownUsdToman
                basis = TomanRateBasis.CACHED_USD_ANCHOR
                sourceLabel = "Cached USD Anchor"
                usdTs = cachedUsdTimestampMillis
                usdFreshness = DataFreshness.CACHED
                usdSingleSource = true
            }
            else -> {
                usdToman = DEFAULT_OFFLINE_USD_TOMAN
                basis = TomanRateBasis.OFFLINE_REFERENCE
                sourceLabel = "Offline Reference"
                usdTs = 0L
                usdFreshness = DataFreshness.UNAVAILABLE
                usdSingleSource = true
            }
        }

        val usdChangePct = tgjuUsdQuote?.changePct?.takeIf { abs(it) > 0.001 }
            ?: tgjuTableFallbackDelta?.second
            ?: usdtChangePct
        val usdChangeToman = tgjuUsdQuote?.changeToman?.takeIf { abs(it) > 0.1 }
            ?: tgjuTableFallbackDelta?.first
            ?: (usdToman * usdChangePct / 100.0)

        return ResolvedUsdRate(
            usdToman = usdToman,
            usdtToman = usdtToman,
            usdChangePct = usdChangePct,
            usdChangeToman = usdChangeToman,
            usdtChangePct = usdtChangePct,
            rateBasis = basis,
            usdSourceLabel = sourceLabel,
            usdFreshness = usdFreshness,
            usdtFreshness = usdtFreshness,
            usdSourceTimestampMillis = usdTs,
            usdtSourceTimestampMillis = usdtTs,
            usdDisputed = usdDisputed,
            usdSingleSource = usdSingleSource,
            usdtDisputed = usdtConsensus?.disputed ?: false,
            usdtSingleSource = usdtConsensus?.singleSource ?: (tgjuUsdQuote != null || basis != TomanRateBasis.OFFLINE_REFERENCE)
        )
    }

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
    ): MarketAsset? {
        val effectiveFxRate = fxConsensus?.price ?: fxPerUsd?.takeIf { it > 0.0 }
        val crossPriceToman = if (effectiveFxRate != null && effectiveFxRate > 0.0) usdToman / effectiveFxRate else null

        var tgjuPriceToman = tgjuQuote?.priceToman
        var tgjuHighToman = tgjuQuote?.highToman
        var tgjuLowToman = tgjuQuote?.lowToman
        var tgjuChangeToman = tgjuQuote?.changeToman

        // Normalize 100-unit (JPY) and 1000-unit (IQD) TGJU quotes against real FX cross-rate
        if (tgjuPriceToman != null && crossPriceToman != null && crossPriceToman > 0.0) {
            val ratio = tgjuPriceToman / crossPriceToman
            if (ratio in 50.0..200.0) {
                tgjuPriceToman /= 100.0
                tgjuHighToman = tgjuHighToman?.let { it / 100.0 }
                tgjuLowToman = tgjuLowToman?.let { it / 100.0 }
                tgjuChangeToman = tgjuChangeToman?.let { it / 100.0 }
            } else if (ratio in 500.0..2000.0) {
                tgjuPriceToman /= 1000.0
                tgjuHighToman = tgjuHighToman?.let { it / 1000.0 }
                tgjuLowToman = tgjuLowToman?.let { it / 1000.0 }
                tgjuChangeToman = tgjuChangeToman?.let { it / 1000.0 }
            }
        }

        val tgjuTs = tgjuQuote?.timestampMillis?.takeIf { it > 0L } ?: nowMillis
        val fxTs = fxConsensus?.sourceTimestampMillis?.takeIf { it > 0L } ?: nowMillis
        val fxRawFreshness = deriveFreshness(
            category = AssetCategory.FIAT,
            fetchedThisRefresh = true,
            hasUsableValue = crossPriceToman != null,
            isCachedSource = false,
            sourceTimestampMillis = fxTs,
            nowMillis = nowMillis
        )
        val fxDerivedFreshness = DataFreshness.stalest(fxRawFreshness, usdBasisFreshness)

        val candidates = mutableListOf<PriceCandidate>()
        if (tgjuPriceToman != null && tgjuPriceToman > 0.0) {
            candidates.add(
                PriceCandidate(
                    sourceName = "TGJU",
                    price = tgjuPriceToman,
                    isPrimary = true,
                    timestampMillis = tgjuTs
                )
            )
        }
        if (crossPriceToman != null && crossPriceToman > 0.0) {
            candidates.add(
                PriceCandidate(
                    sourceName = "FX + USD",
                    price = crossPriceToman,
                    isPrimary = tgjuPriceToman == null,
                    timestampMillis = minOf(fxTs, usdSourceTimestampMillis.takeIf { it > 0L } ?: nowMillis)
                )
            )
        }
        if (candidates.isEmpty()) return null

        val priceToman: Double
        val highToman: Double?
        val lowToman: Double?
        val changeToman: Double
        val src: String
        val disputed: Boolean
        val singleSource: Boolean
        val freshness: DataFreshness
        val sourceTs: Long

        if (tgjuPriceToman != null && tgjuPriceToman > 0.0) {
            // TGJU is primary Tehran domestic quote. Check disagreement if FX + USD exists.
            val pairValidation = if (crossPriceToman != null && crossPriceToman > 0.0) {
                validateCrossSourcePrices(
                    candidates = candidates,
                    thresholdPct = MarketValidationConfig.FIAT_DISAGREEMENT_THRESHOLD_PCT,
                    nowMillis = nowMillis
                )
            } else {
                null
            }
            priceToman = tgjuPriceToman
            highToman = tgjuHighToman?.takeIf { it > 0.0 }?.let { maxOf(it, priceToman) }
            lowToman = tgjuLowToman?.takeIf { it > 0.0 }?.let { minOf(it, priceToman) }
            changeToman = tgjuChangeToman ?: 0.0
            src = "TGJU"
            disputed = pairValidation?.disputed == true
            singleSource = crossPriceToman == null
            sourceTs = tgjuTs
            freshness = deriveFreshness(
                category = AssetCategory.FIAT,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = tgjuTs,
                nowMillis = nowMillis
            )
        } else {
            priceToman = crossPriceToman!!
            highToman = null
            lowToman = null
            changeToman = priceToman * usdChangePct / 100.0
            src = if (fxConsensus != null && fxConsensus.acceptedSources.isNotEmpty()) {
                "${fxConsensus.acceptedSources.joinToString("+")} + USD"
            } else {
                "FX + USD"
            }
            disputed = fxConsensus?.disputed == true
            singleSource = fxConsensus?.singleSource ?: true
            sourceTs = minOf(fxTs, usdSourceTimestampMillis.takeIf { it > 0L } ?: nowMillis)
            freshness = fxDerivedFreshness
        }

        val changePct = tgjuQuote?.changePct?.takeIf { abs(it) > 0.001 }
            ?: if (tgjuQuote != null && abs(changeToman) > 0.1 && priceToman > abs(changeToman)) {
                (changeToman / (priceToman - changeToman)) * 100.0
            } else if (tgjuQuote == null) {
                usdChangePct
            } else {
                0.0
            }

        val rawSpark = when {
            currencySpark.size >= 3 -> currencySpark
            usdtSpark.size >= 4 -> usdtSpark
            else -> emptyList()
        }
        val spark = if (rawSpark.size >= 2) {
            scaleSeriesToCurrent(rawSpark, priceToman)
        } else {
            emptyList()
        }

        return MarketAsset(
            id = spec.id,
            symbol = spec.symbol,
            nameFa = spec.nameFa,
            nameEn = spec.nameEn,
            category = AssetCategory.FIAT,
            badgeText = spec.badge,
            accentHex = spec.accentHex,
            priceToman = priceToman,
            priceUsd = if (effectiveFxRate != null && effectiveFxRate > 0.0) 1.0 / effectiveFxRate else null,
            change24hPercent = changePct,
            change24hToman = changeToman,
            high24hToman = highToman,
            low24hToman = lowToman,
            sparkline = spark,
            sourceLabel = src,
            updatedAtMillis = nowMillis,
            freshness = freshness,
            sourceTimestampMillis = sourceTs,
            fetchedAtMillis = nowMillis,
            disputed = disputed,
            singleSource = singleSource
        )
    }

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
    ): MarketAsset? {
        if (spec.id == "USDT") {
            val usdtCandidates = listOfNotNull(
                wallex?.priceToman?.takeIf { it > 1_000.0 }?.let {
                    PriceCandidate(
                        sourceName = "Wallex",
                        price = it,
                        isPrimary = true,
                        timestampMillis = wallex.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        changePct = wallex.changePct,
                        highToman = wallex.highToman,
                        lowToman = wallex.lowToman
                    )
                },
                bitpin?.priceToman?.takeIf { it > 1_000.0 }?.let {
                    PriceCandidate(
                        sourceName = "Bitpin",
                        price = it,
                        isPrimary = wallex?.priceToman == null,
                        timestampMillis = bitpin.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        changePct = bitpin.changePct,
                        highToman = bitpin.highToman,
                        lowToman = bitpin.lowToman
                    )
                },
                nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                    PriceCandidate(
                        sourceName = "Nobitex",
                        price = it,
                        isPrimary = wallex?.priceToman == null && bitpin?.priceToman == null,
                        timestampMillis = nobitexTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                    )
                }
            )

            val consensus = validateCrossSourcePrices(
                candidates = usdtCandidates,
                thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
                nowMillis = nowMillis
            )

            val priceToman = consensus?.price ?: usdtToman.takeIf { it > 0.0 } ?: return null
            val changePct = usdtChangePct
            val changeToman = priceToman * (changePct / 100.0)
            val source = consensus?.acceptedSources?.joinToString("+")?.ifEmpty { "Live" }
                ?: listOfNotNull(
                    if (wallex?.priceToman != null) "Wallex" else null,
                    if (bitpin?.priceToman != null) "Bitpin" else null,
                    if (nobitexUsdtToman != null) "Nobitex" else null
                ).joinToString("+").ifEmpty { "Cached" }

            val sourceTs = consensus?.sourceTimestampMillis ?: usdtSourceTimestampMillis
            val directFreshness = if (consensus != null) {
                deriveFreshness(
                    category = AssetCategory.CRYPTO,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = sourceTs,
                    nowMillis = nowMillis
                )
            } else {
                usdtBasisFreshness
            }
            val freshness = DataFreshness.stalest(directFreshness, usdtBasisFreshness)

            val spark = if (sparkInput.size >= 4) {
                scaleSeriesToCurrent(sparkInput, priceToman)
            } else {
                emptyList()
            }

            return MarketAsset(
                id = spec.id,
                symbol = spec.id,
                nameFa = spec.nameFa,
                nameEn = spec.nameEn,
                category = AssetCategory.CRYPTO,
                badgeText = spec.badge,
                accentHex = spec.accentHex,
                priceToman = priceToman,
                priceUsd = 1.0,
                change24hPercent = changePct,
                change24hToman = changeToman,
                high24hToman = consensus?.selectedCandidate?.highToman,
                low24hToman = consensus?.selectedCandidate?.lowToman,
                sparkline = spark,
                sourceLabel = source,
                updatedAtMillis = nowMillis,
                freshness = freshness,
                sourceTimestampMillis = sourceTs,
                fetchedAtMillis = nowMillis,
                disputed = consensus?.disputed == true,
                singleSource = consensus?.singleSource ?: true
            )
        }

        val priceUsd = binance?.priceUsd ?: wallex?.priceUsd ?: bitpin?.priceUsd
        val candidates = mutableListOf<PriceCandidate>()
        wallex?.priceToman?.takeIf { it > 0.0 }?.let {
            candidates.add(
                PriceCandidate(
                    sourceName = "Wallex",
                    price = it,
                    isPrimary = true,
                    timestampMillis = wallex.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = wallex.changePct,
                    highToman = wallex.highToman,
                    lowToman = wallex.lowToman
                )
            )
        }
        bitpin?.priceToman?.takeIf { it > 0.0 }?.let {
            candidates.add(
                PriceCandidate(
                    sourceName = "Bitpin",
                    price = it,
                    isPrimary = wallex?.priceToman == null,
                    timestampMillis = bitpin.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = bitpin.changePct,
                    highToman = bitpin.highToman,
                    lowToman = bitpin.lowToman
                )
            )
        }
        if (binance?.priceUsd != null && binance.priceUsd > 0.0 && usdtToman > 0.0) {
            candidates.add(
                PriceCandidate(
                    sourceName = "Binance",
                    price = binance.priceUsd * usdtToman,
                    isPrimary = wallex?.priceToman == null && bitpin?.priceToman == null,
                    timestampMillis = minOf(
                        binance.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        usdtSourceTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                    ),
                    changePct = binance.changePct,
                    highToman = binance.highUsd?.takeIf { it > 0.0 }?.let { it * usdtToman },
                    lowToman = binance.lowUsd?.takeIf { it > 0.0 }?.let { it * usdtToman }
                )
            )
        }

        val consensus = validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = nowMillis
        ) ?: return null

        val priceToman = consensus.price
        val changePct = binance?.changePct?.takeIf { abs(it) > 0.001 }
            ?: wallex?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpin?.changePct?.takeIf { abs(it) > 0.001 }
            ?: 0.0
        val changeToman = priceToman * (changePct / 100.0)

        val usesDerivedBinance = "Binance" in consensus.acceptedSources
        val directFreshness = deriveFreshness(
            category = AssetCategory.CRYPTO,
            fetchedThisRefresh = true,
            hasUsableValue = true,
            isCachedSource = false,
            sourceTimestampMillis = consensus.sourceTimestampMillis,
            nowMillis = nowMillis
        )
        val freshness = if (usesDerivedBinance) {
            DataFreshness.stalest(directFreshness, usdtBasisFreshness)
        } else {
            directFreshness
        }

        val source = consensus.acceptedSources.joinToString("+").ifEmpty { "Live" }
        val highToman = consensus.selectedCandidate.highToman?.takeIf { it > 0.0 }
        val lowToman = consensus.selectedCandidate.lowToman?.takeIf { it > 0.0 }

        val spark = if (sparkInput.size >= 4) {
            scaleSeriesToCurrent(sparkInput, priceToman)
        } else {
            emptyList()
        }

        return MarketAsset(
            id = spec.id,
            symbol = spec.id,
            nameFa = spec.nameFa,
            nameEn = spec.nameEn,
            category = AssetCategory.CRYPTO,
            badgeText = spec.badge,
            accentHex = spec.accentHex,
            priceToman = priceToman,
            priceUsd = priceUsd,
            change24hPercent = changePct,
            change24hToman = changeToman,
            high24hToman = highToman,
            low24hToman = lowToman,
            sparkline = spark,
            sourceLabel = source,
            updatedAtMillis = nowMillis,
            freshness = freshness,
            sourceTimestampMillis = consensus.sourceTimestampMillis,
            fetchedAtMillis = nowMillis,
            disputed = consensus.disputed,
            singleSource = consensus.singleSource
        )
    }

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
    ): List<MarketAsset> {
        val onsRaw = tgju["ons"]
        val allSpotCandidates = mutableListOf<PriceCandidate>()
        goldSpotCandidates.filter { it.price in 1000.0..10_000.0 }.forEach { allSpotCandidates.add(it) }
        paxgQuote?.priceUsd?.takeIf { it in 1000.0..10_000.0 }?.let { paxgPrice ->
            allSpotCandidates.add(
                PriceCandidate(
                    sourceName = "Binance PAXG",
                    price = paxgPrice,
                    isPrimary = allSpotCandidates.none { it.isPrimary },
                    timestampMillis = paxgQuote.timestampMillis.takeIf { it > 0L } ?: nowMillis,
                    changePct = paxgQuote.changePct,
                    highToman = paxgQuote.highUsd?.let { it * usdToman },
                    lowToman = paxgQuote.lowUsd?.let { it * usdToman }
                )
            )
        }
        val tgjuOnsUsd = (onsRaw?.priceToman ?: 0.0) * 10.0
        if (tgjuOnsUsd in 1000.0..10_000.0) {
            allSpotCandidates.add(
                PriceCandidate(
                    sourceName = "TGJU",
                    price = tgjuOnsUsd,
                    isPrimary = allSpotCandidates.none { it.isPrimary },
                    timestampMillis = onsRaw?.timestampMillis?.takeIf { it > 0L } ?: nowMillis,
                    changePct = onsRaw?.changePct ?: 0.0
                )
            )
        }

        val onsConsensus = validateCrossSourcePrices(
            candidates = allSpotCandidates,
            thresholdPct = MarketValidationConfig.FIAT_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = nowMillis
        )
        val hasRealOns = onsConsensus != null
        val onsUsd = onsConsensus?.price ?: DEFAULT_OFFLINE_ONS_USD
        val onsTs = onsConsensus?.sourceTimestampMillis ?: 0L
        val onsRawFreshness = deriveFreshness(
            category = AssetCategory.GOLD,
            fetchedThisRefresh = hasRealOns,
            hasUsableValue = hasRealOns,
            isCachedSource = !hasRealOns,
            sourceTimestampMillis = onsTs,
            nowMillis = nowMillis
        )
        val derivedGoldFreshness = DataFreshness.stalest(onsRawFreshness, usdBasisFreshness)
        val derivedGoldTs = minOf(
            onsTs.takeIf { it > 0L } ?: nowMillis,
            usdSourceTimestampMillis.takeIf { it > 0L } ?: nowMillis
        )

        val gram24Theoretical = (onsUsd / MarketValidationConfig.TROY_OUNCE_GRAMS) * usdToman
        val gram18Theoretical = gram24Theoretical * (MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0)
        val canComputeIntrinsic = hasRealOns && usdToman > 1_000.0 && usdBasisFreshness != DataFreshness.UNAVAILABLE

        val result = ArrayList<MarketAsset>(SUPPORTED_GOLDS.size)

        for (spec in SUPPORTED_GOLDS) {
            val q = tgju[spec.tgjuKey]
            val coinSpec = MarketValidationConfig.COIN_SPECS[spec.id]

            if (spec.id == "XAU_OZ") {
                val xauPrice = onsUsd * usdToman
                val changePct = paxgQuote?.changePct ?: onsRaw?.changePct ?: usdChangePct
                val changeToman = xauPrice * changePct / 100.0
                val xauSource = onsConsensus?.acceptedSources?.joinToString("+")?.let { "$it + USD" }
                    ?: "Offline Reference"
                val xauHigh = paxgQuote?.highUsd?.takeIf { it > 0.0 }?.let { it * usdToman }
                val xauLow = paxgQuote?.lowUsd?.takeIf { it > 0.0 }?.let { it * usdToman }
                val spark = when {
                    goldSpark.size >= 4 -> scaleSeriesToCurrent(goldSpark, xauPrice)
                    usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, xauPrice)
                    else -> emptyList()
                }

                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = xauPrice,
                        priceUsd = onsUsd,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = xauHigh,
                        low24hToman = xauLow,
                        sparkline = spark,
                        sourceLabel = xauSource,
                        updatedAtMillis = nowMillis,
                        freshness = derivedGoldFreshness,
                        sourceTimestampMillis = derivedGoldTs,
                        fetchedAtMillis = nowMillis,
                        disputed = onsConsensus?.disputed == true,
                        singleSource = onsConsensus?.singleSource ?: true
                    )
                )
                continue
            }

            if (coinSpec != null) {
                val intrinsic = if (canComputeIntrinsic) {
                    coinSpec.pureGoldGrams * gram24Theoretical
                } else {
                    cachedAssetsById[spec.id]?.intrinsicValueToman
                }

                if (q != null && q.priceToman > 0.0) {
                    val price = q.priceToman
                    val changePct = q.changePct.takeIf { abs(it) > 0.001 } ?: 0.0
                    val changeToman = q.changeToman.takeIf { abs(it) > 0.1 } ?: (price * changePct / 100.0)
                    val bubbleToman = intrinsic?.let { price - it }
                    val bubblePct = if (intrinsic != null && intrinsic > 0.0) {
                        ((price - intrinsic) / intrinsic) * 100.0
                    } else {
                        null
                    }
                    val ts = q.timestampMillis.takeIf { it > 0L } ?: nowMillis
                    val freshness = deriveFreshness(
                        category = AssetCategory.GOLD,
                        fetchedThisRefresh = true,
                        hasUsableValue = true,
                        isCachedSource = false,
                        sourceTimestampMillis = ts,
                        nowMillis = nowMillis
                    )
                    val spark = when {
                        goldSpark.size >= 4 -> scaleSeriesToCurrent(goldSpark, price)
                        usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, price)
                        else -> emptyList()
                    }
                    result.add(
                        MarketAsset(
                            id = spec.id,
                            symbol = spec.symbol,
                            nameFa = spec.nameFa,
                            nameEn = spec.nameEn,
                            category = AssetCategory.GOLD,
                            badgeText = spec.badge,
                            accentHex = 0xFFF59E0B,
                            priceToman = price,
                            priceUsd = if (usdToman > 0.0) price / usdToman else null,
                            change24hPercent = changePct,
                            change24hToman = changeToman,
                            high24hToman = q.highToman?.takeIf { it > 0.0 }?.let { maxOf(it, price) },
                            low24hToman = q.lowToman?.takeIf { it > 0.0 }?.let { minOf(it, price) },
                            sparkline = spark,
                            sourceLabel = "TGJU",
                            updatedAtMillis = nowMillis,
                            freshness = freshness,
                            sourceTimestampMillis = ts,
                            fetchedAtMillis = nowMillis,
                            disputed = false,
                            singleSource = true,
                            intrinsicValueToman = intrinsic,
                            bubbleToman = bubbleToman,
                            bubblePercent = bubblePct
                        )
                    )
                } else {
                    val cachedCoin = cachedAssetsById[spec.id]
                        ?.takeIf { it.priceToman > 0.0 && it.freshness != DataFreshness.UNAVAILABLE }
                    if (cachedCoin != null) {
                        val bubbleToman = intrinsic?.let { cachedCoin.priceToman - it } ?: cachedCoin.bubbleToman
                        val bubblePct = if (intrinsic != null && intrinsic > 0.0) {
                            ((cachedCoin.priceToman - intrinsic) / intrinsic) * 100.0
                        } else {
                            cachedCoin.bubblePercent
                        }
                        result.add(
                            cachedCoin.copy(
                                freshness = DataFreshness.CACHED,
                                intrinsicValueToman = intrinsic,
                                bubbleToman = bubbleToman,
                                bubblePercent = bubblePct
                            )
                        )
                    } else {
                        result.add(
                            MarketAsset(
                                id = spec.id,
                                symbol = spec.symbol,
                                nameFa = spec.nameFa,
                                nameEn = spec.nameEn,
                                category = AssetCategory.GOLD,
                                badgeText = spec.badge,
                                accentHex = 0xFFF59E0B,
                                priceToman = 0.0,
                                priceUsd = null,
                                change24hPercent = 0.0,
                                change24hToman = 0.0,
                                high24hToman = null,
                                low24hToman = null,
                                sparkline = emptyList(),
                                sourceLabel = "Unavailable",
                                updatedAtMillis = nowMillis,
                                freshness = DataFreshness.UNAVAILABLE,
                                sourceTimestampMillis = 0L,
                                fetchedAtMillis = nowMillis,
                                disputed = false,
                                singleSource = false,
                                intrinsicValueToman = intrinsic,
                                bubbleToman = null,
                                bubblePercent = null
                            )
                        )
                    }
                }
                continue
            }

            // Benchmark gold units (18K gram, 24K gram, Mesghal)
            val fallbackTheoretical = when (spec.id) {
                "GOLD_18K" -> gram18Theoretical
                "GOLD_24K" -> gram24Theoretical
                "GOLD_MESGHAL" -> gram18Theoretical * MarketValidationConfig.MESGHAL_18K_EQUIVALENT_GRAMS
                else -> gram18Theoretical
            }

            if (q != null && q.priceToman > 0.0) {
                val price = q.priceToman
                val tableFallback = if (spec.id == "GOLD_18K") computeTableDelta(tgjuGoldRecent, price) else null
                val changePct = q.changePct.takeIf { abs(it) > 0.001 } ?: tableFallback?.second ?: 0.0
                val changeToman = q.changeToman.takeIf { abs(it) > 0.1 }
                    ?: tableFallback?.first
                    ?: (price * changePct / 100.0)
                val ts = q.timestampMillis.takeIf { it > 0L } ?: nowMillis
                val freshness = deriveFreshness(
                    category = AssetCategory.GOLD,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = ts,
                    nowMillis = nowMillis
                )
                val spark = when {
                    goldSpark.size >= 4 -> scaleSeriesToCurrent(goldSpark, price)
                    usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, price)
                    else -> emptyList()
                }
                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = price,
                        priceUsd = if (usdToman > 0.0) price / usdToman else null,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = q.highToman?.takeIf { it > 0.0 }?.let { maxOf(it, price) },
                        low24hToman = q.lowToman?.takeIf { it > 0.0 }?.let { minOf(it, price) },
                        sparkline = spark,
                        sourceLabel = "TGJU",
                        updatedAtMillis = nowMillis,
                        freshness = freshness,
                        sourceTimestampMillis = ts,
                        fetchedAtMillis = nowMillis,
                        disputed = false,
                        singleSource = true
                    )
                )
            } else if (hasRealOns && usdToman > 1_000.0) {
                val price = fallbackTheoretical
                val changePct = paxgQuote?.changePct ?: usdChangePct
                val changeToman = price * changePct / 100.0
                val spark = when {
                    goldSpark.size >= 4 -> scaleSeriesToCurrent(goldSpark, price)
                    usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, price)
                    else -> emptyList()
                }
                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = price,
                        priceUsd = price / usdToman,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = null,
                        low24hToman = null,
                        sparkline = spark,
                        sourceLabel = "${onsConsensus?.selectedCandidate?.sourceName ?: "XAU"} + USD",
                        updatedAtMillis = nowMillis,
                        freshness = derivedGoldFreshness,
                        sourceTimestampMillis = derivedGoldTs,
                        fetchedAtMillis = nowMillis,
                        disputed = onsConsensus?.disputed == true,
                        singleSource = onsConsensus?.singleSource ?: true
                    )
                )
            } else {
                val cachedGold = cachedAssetsById[spec.id]
                    ?.takeIf { it.priceToman > 0.0 && it.freshness != DataFreshness.UNAVAILABLE }
                if (cachedGold != null) {
                    result.add(cachedGold.copy(freshness = DataFreshness.CACHED))
                } else {
                    result.add(
                        MarketAsset(
                            id = spec.id,
                            symbol = spec.symbol,
                            nameFa = spec.nameFa,
                            nameEn = spec.nameEn,
                            category = AssetCategory.GOLD,
                            badgeText = spec.badge,
                            accentHex = 0xFFF59E0B,
                            priceToman = 0.0,
                            priceUsd = null,
                            change24hPercent = 0.0,
                            change24hToman = 0.0,
                            high24hToman = null,
                            low24hToman = null,
                            sparkline = emptyList(),
                            sourceLabel = "Unavailable",
                            updatedAtMillis = nowMillis,
                            freshness = DataFreshness.UNAVAILABLE,
                            sourceTimestampMillis = 0L,
                            fetchedAtMillis = nowMillis
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Last-resort offline baseline used ONLY when all live APIs fail AND no persisted cache exists
     * (e.g., cold offline first boot). Clearly surfaced with Offline Reference label & banner,
     * never marked LIVE.
     */
    fun offlineReferenceCatalog(nowMillis: Long = System.currentTimeMillis()): CachedMarketSnapshot {
        val usd = DEFAULT_OFFLINE_USD_TOMAN
        val eurPerUsd = 0.92
        val aedPerUsd = 3.6725
        val gbpPerUsd = 0.79
        val ons = DEFAULT_OFFLINE_ONS_USD
        val gram18 = (ons / MarketValidationConfig.TROY_OUNCE_GRAMS) * usd *
            (MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0)

        val assets = listOf(
            MarketAsset(
                id = "USD",
                symbol = "USD",
                nameFa = "دلار آمریکا",
                nameEn = "US Dollar",
                category = AssetCategory.FIAT,
                badgeText = "🇺🇸",
                accentHex = 0xFF10B981,
                priceToman = usd,
                priceUsd = 1.0,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "EUR",
                symbol = "EUR",
                nameFa = "یورو",
                nameEn = "Euro",
                category = AssetCategory.FIAT,
                badgeText = "🇪🇺",
                accentHex = 0xFF3B82F6,
                priceToman = usd / eurPerUsd,
                priceUsd = 1.0 / eurPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "AED",
                symbol = "AED",
                nameFa = "درهم امارات",
                nameEn = "UAE Dirham",
                category = AssetCategory.FIAT,
                badgeText = "🇦🇪",
                accentHex = 0xFF14B8A6,
                priceToman = usd / aedPerUsd,
                priceUsd = 1.0 / aedPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "GBP",
                symbol = "GBP",
                nameFa = "پوند انگلیس",
                nameEn = "British Pound",
                category = AssetCategory.FIAT,
                badgeText = "🇬🇧",
                accentHex = 0xFF6366F1,
                priceToman = usd / gbpPerUsd,
                priceUsd = 1.0 / gbpPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "GOLD_18K",
                symbol = "GRAM",
                nameFa = "طلای ۱۸ عیار",
                nameEn = "Gram",
                category = AssetCategory.GOLD,
                badgeText = "✨",
                accentHex = 0xFFF59E0B,
                priceToman = gram18,
                priceUsd = gram18 / usd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "USDT",
                symbol = "USDT",
                nameFa = "تتر",
                nameEn = "Tether",
                category = AssetCategory.CRYPTO,
                badgeText = "₮",
                accentHex = 0xFF26A17B,
                priceToman = usd,
                priceUsd = 1.0,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = nowMillis
            )
        )

        return CachedMarketSnapshot(
            assets = assets,
            updatedAtMillis = 0L,
            sourceSummary = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
            freshness = DataFreshness.UNAVAILABLE
        )
    }

    fun scaleSeriesToCurrent(values: List<Double>, targetCurrent: Double): List<Double> {
        val valid = values.filter { it.isFinite() && it > 0.0 }
        if (valid.size < 2 || !targetCurrent.isFinite() || targetCurrent <= 0.0) return emptyList()
        val last = valid.last()
        val factor = targetCurrent / last
        return valid.map { it * factor }
    }

    fun computeSeriesChangePct(series: List<Double>): Double? {
        if (series.size < 2) return null
        val first = series.first()
        val last = series.last()
        if (first <= 0.0 || last <= 0.0) return null
        return ((last - first) / first) * 100.0
    }
}
