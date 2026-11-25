package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Free-market USD anchor resolution and global fiat currency price mapping.
 */
internal object FiatPriceMapper {

    val SUPPORTED_FIATS: List<MarketPriceMapper.FiatSpec> = listOf(
        MarketPriceMapper.FiatSpec("EUR", "EUR", "یورو", "Euro", "🇪🇺", 0xFF3B82F6, "price_eur", "EUR"),
        MarketPriceMapper.FiatSpec("AED", "AED", "درهم امارات", "UAE Dirham", "🇦🇪", 0xFF14B8A6, "price_aed", "AED"),
        MarketPriceMapper.FiatSpec("GBP", "GBP", "پوند انگلیس", "British Pound", "🇬🇧", 0xFF6366F1, "price_gbp", "GBP"),
        MarketPriceMapper.FiatSpec("KWD", "KWD", "دینار کویت", "Kuwaiti Dinar", "🇰🇼", 0xFF059669, "price_kwd", "KWD"),
        MarketPriceMapper.FiatSpec("TRY", "TRY", "لیر ترکیه", "Turkish Lira", "🇹🇷", 0xFFEF4444, "price_try", "TRY"),
        MarketPriceMapper.FiatSpec("CAD", "CAD", "دلار کانادا", "Canadian Dollar", "🇨🇦", 0xFFF43F5E, "price_cad", "CAD"),
        MarketPriceMapper.FiatSpec("CHF", "CHF", "فرانک سوئیس", "Swiss Franc", "🇨🇭", 0xFFEC4899, "price_chf", "CHF"),
        MarketPriceMapper.FiatSpec("CNY", "CNY", "یوان چین", "Chinese Yuan", "🇨🇳", 0xFFF59E0B, "price_cny", "CNY"),
        MarketPriceMapper.FiatSpec("AUD", "AUD", "دلار استرالیا", "Australian Dollar", "🇦🇺", 0xFF06B6D4, "price_aud", "AUD"),
        MarketPriceMapper.FiatSpec("JPY", "JPY", "ین ژاپن", "Japanese Yen", "🇯🇵", 0xFFFB7185, "price_jpy", "JPY"),
        MarketPriceMapper.FiatSpec("SAR", "SAR", "ریال عربستان", "Saudi Riyal", "🇸🇦", 0xFF22C55E, "price_sar", "SAR"),
        MarketPriceMapper.FiatSpec("IQD", "IQD", "دینار عراق", "Iraqi Dinar", "🇮🇶", 0xFF84CC16, "price_iqd", "IQD")
    )

    fun computeUsdtMedianToman(
        wallexUsdtToman: Double?,
        bitpinUsdtToman: Double?,
        nobitexUsdtToman: Double?
    ): Double? {
        val candidates = listOfNotNull(
            wallexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate("Wallex", it, isPrimary = true)
            },
            bitpinUsdtToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate("Bitpin", it, isPrimary = false)
            },
            nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate("Nobitex", it, isPrimary = false)
            }
        )
        return MarketValidationHelpers.validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.effectiveCryptoDisagreementThresholdPct()
        )?.price
    }

    fun resolveFreeMarketUsd(
        tgjuUsdQuote: MarketPriceMapper.TgjuQuote?,
        wallexUsdt: MarketPriceMapper.ExchangeQuote?,
        bitpinUsdt: MarketPriceMapper.ExchangeQuote?,
        nobitexUsdtToman: Double?,
        usdtSpark: List<Double>,
        tgjuTableFallbackDelta: Pair<Double, Double>?,
        lastKnownUsdToman: Double?,
        nowMillis: Long = System.currentTimeMillis(),
        nobitexTimestampMillis: Long = nowMillis,
        cachedUsdTimestampMillis: Long = 0L
    ): MarketPriceMapper.ResolvedUsdRate {
        val usdtCandidates = listOfNotNull(
            wallexUsdt?.priceToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Wallex",
                    price = it,
                    isPrimary = true,
                    timestampMillis = wallexUsdt.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = wallexUsdt.changePct
                )
            },
            bitpinUsdt?.priceToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Bitpin",
                    price = it,
                    isPrimary = wallexUsdt?.priceToman == null,
                    timestampMillis = bitpinUsdt.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = bitpinUsdt.changePct
                )
            },
            nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Nobitex",
                    price = it,
                    isPrimary = wallexUsdt?.priceToman == null && bitpinUsdt?.priceToman == null,
                    timestampMillis = nobitexTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                )
            }
        )

        val usdtConsensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = usdtCandidates,
            thresholdPct = MarketValidationConfig.effectiveCryptoDisagreementThresholdPct(),
            nowMillis = nowMillis
        )

        val anchorUsd = lastKnownUsdToman?.takeIf { it > 1_000.0 } ?: MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN

        val usdtToman = usdtConsensus?.price ?: tgjuUsdQuote?.priceToman ?: anchorUsd
        val usdtChangePct = wallexUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpinUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: MarketValidationHelpers.computeSeriesChangePct(usdtSpark)
            ?: tgjuUsdQuote?.changePct
            ?: 0.0

        val usdtFreshness = when {
            usdtConsensus != null -> MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.CRYPTO,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = usdtConsensus.sourceTimestampMillis,
                nowMillis = nowMillis
            )
            tgjuUsdQuote != null -> MarketValidationHelpers.deriveFreshness(
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
                usdFreshness = MarketValidationHelpers.deriveFreshness(
                    category = AssetCategory.FIAT,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = usdTs,
                    nowMillis = nowMillis
                )
                // Cross-check TGJU cash USD against USDT free-market benchmark if available
                if (usdtConsensus != null) {
                    val pair = MarketValidationHelpers.validateCrossSourcePrices(
                        candidates = listOf(
                            MarketPriceMapper.PriceCandidate("TGJU", tgjuUsdQuote.priceToman, isPrimary = true, timestampMillis = usdTs),
                            MarketPriceMapper.PriceCandidate("USDT", usdtConsensus.price, isPrimary = false, timestampMillis = usdtTs)
                        ),
                        thresholdPct = MarketValidationConfig.effectiveFiatDisagreementThresholdPct(),
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
                usdFreshness = MarketValidationHelpers.deriveFreshness(
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
                usdToman = MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN
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

        return MarketPriceMapper.ResolvedUsdRate(
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
        spec: MarketPriceMapper.FiatSpec,
        usdToman: Double,
        usdChangePct: Double,
        tgjuQuote: MarketPriceMapper.TgjuQuote?,
        fxPerUsd: Double?,
        usdtSpark: List<Double>,
        nowMillis: Long,
        currencySpark: List<Double> = emptyList(),
        fxConsensus: MarketPriceMapper.ConsensusResult? = null,
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
        val fxRawFreshness = MarketValidationHelpers.deriveFreshness(
            category = AssetCategory.FIAT,
            fetchedThisRefresh = true,
            hasUsableValue = crossPriceToman != null,
            isCachedSource = false,
            sourceTimestampMillis = fxTs,
            nowMillis = nowMillis
        )
        val fxDerivedFreshness = DataFreshness.stalest(fxRawFreshness, usdBasisFreshness)

        val candidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
        if (tgjuPriceToman != null && tgjuPriceToman > 0.0) {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "TGJU",
                    price = tgjuPriceToman,
                    isPrimary = true,
                    timestampMillis = tgjuTs
                )
            )
        }
        if (crossPriceToman != null && crossPriceToman > 0.0) {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
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
                MarketValidationHelpers.validateCrossSourcePrices(
                    candidates = candidates,
                    thresholdPct = MarketValidationConfig.effectiveFiatDisagreementThresholdPct(),
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
            freshness = MarketValidationHelpers.deriveFreshness(
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
            MarketValidationHelpers.scaleSeriesToCurrent(rawSpark, priceToman)
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
}
