package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
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
        nobitexTimestampMillis: Long = 0L,
        cachedUsdTimestampMillis: Long = 0L,
        tgjuFailed: Boolean = false,
        wallexFailed: Boolean = false,
        bitpinFailed: Boolean = false,
        nobitexFailed: Boolean = false
    ): MarketPriceMapper.ResolvedUsdRate {
        val wallexValidPrice = wallexUsdt?.priceToman?.takeIf { it.isFinite() && it > 1_000.0 }
        val bitpinValidPrice = bitpinUsdt?.priceToman?.takeIf { it.isFinite() && it > 1_000.0 }
        val nobitexValidPrice = nobitexUsdtToman?.takeIf { it.isFinite() && it > 1_000.0 }

        val usdtCandidates = buildList {
            when {
                wallexValidPrice != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = wallexValidPrice,
                        isPrimary = true,
                        timestampMillis = wallexUsdt.timestampMillis.coerceAtLeast(0L),
                        changePct = wallexUsdt.changePct
                    )
                )
                wallexUsdt?.priceToman != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = wallexUsdt.priceToman,
                        isPrimary = false,
                        timestampMillis = wallexUsdt.timestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (wallexFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }

            when {
                bitpinValidPrice != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = bitpinValidPrice,
                        isPrimary = wallexValidPrice == null,
                        timestampMillis = bitpinUsdt.timestampMillis.coerceAtLeast(0L),
                        changePct = bitpinUsdt.changePct
                    )
                )
                bitpinUsdt?.priceToman != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = bitpinUsdt.priceToman,
                        isPrimary = false,
                        timestampMillis = bitpinUsdt.timestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (bitpinFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }

            when {
                nobitexValidPrice != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Nobitex",
                        price = nobitexValidPrice,
                        isPrimary = wallexValidPrice == null && bitpinValidPrice == null,
                        timestampMillis = nobitexTimestampMillis.coerceAtLeast(0L)
                    )
                )
                nobitexUsdtToman != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Nobitex",
                        price = nobitexUsdtToman,
                        isPrimary = false,
                        timestampMillis = nobitexTimestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Nobitex",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (nobitexFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }
        }

        val usdtConsensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = usdtCandidates,
            thresholdPct = MarketValidationConfig.effectiveCryptoDisagreementThresholdPct(),
            nowMillis = nowMillis,
            staleMaxAgeMillis = MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
        )

        val validTgjuUsd = tgjuUsdQuote?.takeIf { it.priceToman.isFinite() && it.priceToman > 1_000.0 }
        val anchorUsd = lastKnownUsdToman?.takeIf { it.isFinite() && it > 1_000.0 }
            ?: MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN

        val usdtToman = usdtConsensus?.price ?: validTgjuUsd?.priceToman ?: anchorUsd
        val usdtChangePct = wallexUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpinUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: MarketValidationHelpers.computeSeriesChangePct(usdtSpark)
            ?: validTgjuUsd?.changePct
            ?: 0.0

        val usdtTs = when {
            usdtConsensus != null -> usdtConsensus.sourceTimestampMillis
            validTgjuUsd != null -> validTgjuUsd.timestampMillis.coerceAtLeast(0L)
            else -> cachedUsdTimestampMillis.coerceAtLeast(0L)
        }

        val usdtFreshness = when {
            usdtConsensus != null -> MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.CRYPTO,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = usdtTs,
                nowMillis = nowMillis
            )
            validTgjuUsd != null -> MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.FIAT,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = usdtTs,
                nowMillis = nowMillis
            )
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> DataFreshness.CACHED
            else -> DataFreshness.UNAVAILABLE
        }

        val usdtSourceLabel = when {
            usdtConsensus != null -> usdtConsensus.sourceAttributionLabel
            validTgjuUsd != null -> "TGJU (Free)"
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> "Cached USD Anchor"
            else -> "Offline Reference"
        }

        val usdToman: Double
        val basis: TomanRateBasis
        val sourceLabel: String
        val usdFreshness: DataFreshness
        val usdTs: Long
        var usdDisputed = false
        var usdSingleSource = false
        val usdIsCalculated: Boolean
        val usdCandidates: List<SourceCandidateRecord>

        when {
            validTgjuUsd != null -> {
                usdToman = validTgjuUsd.priceToman
                basis = TomanRateBasis.TEHRAN_FREE_MARKET
                sourceLabel = "TGJU (Free)"
                usdTs = validTgjuUsd.timestampMillis.coerceAtLeast(0L)
                usdFreshness = MarketValidationHelpers.deriveFreshness(
                    category = AssetCategory.FIAT,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = usdTs,
                    nowMillis = nowMillis
                )
                usdIsCalculated = false
                if (usdtConsensus != null) {
                    val pair = MarketValidationHelpers.validateCrossSourcePrices(
                        candidates = listOf(
                            MarketPriceMapper.PriceCandidate(
                                sourceName = "TGJU",
                                price = validTgjuUsd.priceToman,
                                isPrimary = true,
                                timestampMillis = usdTs
                            ),
                            MarketPriceMapper.PriceCandidate(
                                sourceName = "USDT (${usdtConsensus.sourceAttributionLabel})",
                                price = usdtConsensus.price,
                                isPrimary = false,
                                timestampMillis = usdtTs
                            )
                        ),
                        thresholdPct = MarketValidationConfig.effectiveFiatDisagreementThresholdPct(),
                        nowMillis = nowMillis
                    )
                    usdDisputed = pair?.disputed == true
                    usdCandidates = pair?.candidateRecords.orEmpty()
                } else {
                    usdSingleSource = true
                    usdCandidates = buildList {
                        add(
                            SourceCandidateRecord(
                                sourceName = "TGJU",
                                priceToman = validTgjuUsd.priceToman,
                                rawPrice = validTgjuUsd.priceToman,
                                sourceTimestampMillis = usdTs,
                                isPrimary = true,
                                status = CandidateStatus.INCLUDED,
                                exclusionReason = null
                            )
                        )
                        usdtCandidates.forEach { cand ->
                            add(
                                SourceCandidateRecord(
                                    sourceName = cand.sourceName,
                                    priceToman = cand.price.takeIf { it.isFinite() && it > 0.0 },
                                    rawPrice = cand.price.takeIf { it.isFinite() && it > 0.0 },
                                    sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
                                    isPrimary = false,
                                    status = CandidateStatus.REJECTED,
                                    exclusionReason = cand.unavailabilityReason ?: CandidateExclusionReason.INVALID
                                )
                            )
                        }
                    }
                }
            }
            usdtConsensus != null -> {
                usdToman = usdtConsensus.price
                basis = TomanRateBasis.USDT_FREE_MARKET_MEDIAN
                sourceLabel = if (usdtConsensus.isCalculated) {
                    "${usdtConsensus.sourceAttributionLabel} (Free)"
                } else {
                    "${usdtConsensus.sourceAttributionLabel} (USDT Free)"
                }
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
                usdIsCalculated = usdtConsensus.isCalculated
                usdCandidates = buildList {
                    add(
                        SourceCandidateRecord(
                            sourceName = "TGJU",
                            priceToman = tgjuUsdQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            rawPrice = tgjuUsdQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            sourceTimestampMillis = tgjuUsdQuote?.timestampMillis?.coerceAtLeast(0L) ?: 0L,
                            isPrimary = false,
                            status = CandidateStatus.REJECTED,
                            exclusionReason = when {
                                tgjuUsdQuote != null -> CandidateExclusionReason.INVALID
                                tgjuFailed -> CandidateExclusionReason.PROVIDER_FAILURE
                                else -> CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                    addAll(usdtConsensus.candidateRecords)
                }
            }
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> {
                usdToman = lastKnownUsdToman
                basis = TomanRateBasis.CACHED_USD_ANCHOR
                sourceLabel = "Cached USD Anchor"
                usdTs = cachedUsdTimestampMillis.coerceAtLeast(0L)
                usdFreshness = DataFreshness.CACHED
                usdSingleSource = true
                usdIsCalculated = false
                usdCandidates = buildList {
                    add(
                        SourceCandidateRecord(
                            sourceName = "TGJU",
                            priceToman = tgjuUsdQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            rawPrice = tgjuUsdQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            sourceTimestampMillis = tgjuUsdQuote?.timestampMillis?.coerceAtLeast(0L) ?: 0L,
                            isPrimary = false,
                            status = CandidateStatus.REJECTED,
                            exclusionReason = when {
                                tgjuUsdQuote != null -> CandidateExclusionReason.INVALID
                                tgjuFailed -> CandidateExclusionReason.PROVIDER_FAILURE
                                else -> CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                    add(
                        SourceCandidateRecord(
                            sourceName = "Cached USD Anchor",
                            priceToman = lastKnownUsdToman,
                            rawPrice = lastKnownUsdToman,
                            sourceTimestampMillis = usdTs,
                            isPrimary = true,
                            status = CandidateStatus.INCLUDED,
                            exclusionReason = null
                        )
                    )
                }
            }
            else -> {
                usdToman = MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN
                basis = TomanRateBasis.OFFLINE_REFERENCE
                sourceLabel = "Offline Reference"
                usdTs = 0L
                usdFreshness = DataFreshness.UNAVAILABLE
                usdSingleSource = true
                usdIsCalculated = false
                usdCandidates = buildList {
                    add(
                        SourceCandidateRecord(
                            sourceName = "TGJU",
                            priceToman = null,
                            rawPrice = null,
                            sourceTimestampMillis = 0L,
                            isPrimary = false,
                            status = CandidateStatus.REJECTED,
                            exclusionReason = if (tgjuFailed) {
                                CandidateExclusionReason.PROVIDER_FAILURE
                            } else {
                                CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                    usdtCandidates.forEach { cand ->
                        add(
                            SourceCandidateRecord(
                                sourceName = cand.sourceName,
                                priceToman = null,
                                rawPrice = null,
                                sourceTimestampMillis = 0L,
                                isPrimary = false,
                                status = CandidateStatus.REJECTED,
                                exclusionReason = cand.unavailabilityReason ?: CandidateExclusionReason.MISSING_DATA
                            )
                        )
                    }
                }
            }
        }

        val usdChangePct = validTgjuUsd?.changePct?.takeIf { abs(it) > 0.001 }
            ?: tgjuTableFallbackDelta?.second
            ?: usdtChangePct
        val usdChangeToman = validTgjuUsd?.changeToman?.takeIf { abs(it) > 0.1 }
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
            usdtSingleSource = usdtConsensus?.singleSource ?: (validTgjuUsd != null || basis != TomanRateBasis.OFFLINE_REFERENCE),
            usdIsCalculated = usdIsCalculated,
            usdtIsCalculated = usdtConsensus?.isCalculated == true,
            usdtSourceLabel = usdtSourceLabel,
            usdCandidates = usdCandidates,
            usdtCandidates = usdtConsensus?.candidateRecords.orEmpty()
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
        usdSourceTimestampMillis: Long = 0L,
        tgjuFailed: Boolean = false,
        fxFailedProviders: Set<String> = emptySet()
    ): MarketAsset? {
        val effectiveFxRate = fxConsensus?.price ?: fxPerUsd?.takeIf { it.isFinite() && it > 0.0 }
        val crossPriceToman = if (effectiveFxRate != null && effectiveFxRate > 0.0 && usdToman > 0.0) {
            usdToman / effectiveFxRate
        } else {
            null
        }

        var tgjuPriceToman = tgjuQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 }
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

        val tgjuTs = tgjuQuote?.timestampMillis?.coerceAtLeast(0L) ?: 0L
        val fxTs = fxConsensus?.sourceTimestampMillis?.coerceAtLeast(0L) ?: 0L
        val safeUsdTs = usdSourceTimestampMillis.coerceAtLeast(0L)
        val combinedCrossTs = if (fxTs > 0L && safeUsdTs > 0L) minOf(fxTs, safeUsdTs) else 0L

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
        val fxPairSourceName = if (fxConsensus != null && fxConsensus.sourceAttributionLabel.isNotBlank()) {
            "${fxConsensus.sourceAttributionLabel} + USD"
        } else {
            "FX + USD"
        }
        if (crossPriceToman != null && crossPriceToman > 0.0) {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = fxPairSourceName,
                    price = crossPriceToman,
                    isPrimary = tgjuPriceToman == null,
                    timestampMillis = combinedCrossTs
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
        val isCalculatedPrice: Boolean
        val candidateRecords: List<SourceCandidateRecord>

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
            isCalculatedPrice = false
            freshness = MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.FIAT,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = tgjuTs,
                nowMillis = nowMillis
            )
            candidateRecords = buildList {
                add(
                    SourceCandidateRecord(
                        sourceName = "TGJU",
                        priceToman = priceToman,
                        rawPrice = priceToman,
                        sourceTimestampMillis = tgjuTs,
                        isPrimary = true,
                        status = CandidateStatus.INCLUDED,
                        exclusionReason = null
                    )
                )
                if (crossPriceToman != null && crossPriceToman > 0.0) {
                    add(
                        SourceCandidateRecord(
                            sourceName = fxPairSourceName,
                            priceToman = crossPriceToman,
                            rawPrice = effectiveFxRate,
                            sourceTimestampMillis = combinedCrossTs,
                            isPrimary = false,
                            status = if (disputed) CandidateStatus.REJECTED else CandidateStatus.INCLUDED,
                            exclusionReason = if (disputed) CandidateExclusionReason.EXCESSIVE_DISAGREEMENT else null
                        )
                    )
                }
                fxFailedProviders.forEach { provider ->
                    if (none { it.sourceName == provider }) {
                        add(
                            SourceCandidateRecord(
                                sourceName = provider,
                                priceToman = null,
                                rawPrice = null,
                                sourceTimestampMillis = 0L,
                                isPrimary = false,
                                status = CandidateStatus.REJECTED,
                                exclusionReason = CandidateExclusionReason.PROVIDER_FAILURE
                            )
                        )
                    }
                }
            }
        } else {
            priceToman = crossPriceToman!!
            highToman = null
            lowToman = null
            changeToman = priceToman * usdChangePct / 100.0
            src = fxPairSourceName
            disputed = fxConsensus?.disputed == true
            singleSource = fxConsensus?.singleSource ?: true
            sourceTs = combinedCrossTs
            freshness = fxDerivedFreshness
            isCalculatedPrice = fxConsensus?.isCalculated == true
            candidateRecords = buildList {
                if (tgjuQuote != null || tgjuFailed || spec.tgjuKey.isNotBlank()) {
                    add(
                        SourceCandidateRecord(
                            sourceName = "TGJU",
                            priceToman = tgjuQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            rawPrice = tgjuQuote?.priceToman?.takeIf { it.isFinite() && it > 0.0 },
                            sourceTimestampMillis = tgjuTs,
                            isPrimary = false,
                            status = CandidateStatus.REJECTED,
                            exclusionReason = when {
                                tgjuQuote != null -> CandidateExclusionReason.INVALID
                                tgjuFailed -> CandidateExclusionReason.PROVIDER_FAILURE
                                else -> CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                }
                if (fxConsensus != null) {
                    fxConsensus.candidateRecords.forEach { rec ->
                        val convertedToman = rec.rawPrice?.takeIf { it > 0.0 }?.let { usdToman / it }
                        add(
                            rec.copy(
                                priceToman = convertedToman,
                                detail = rec.rawPrice?.let { "fx=$it" } ?: rec.detail
                            )
                        )
                    }
                } else {
                    add(
                        SourceCandidateRecord(
                            sourceName = "FX + USD",
                            priceToman = crossPriceToman,
                            rawPrice = effectiveFxRate,
                            sourceTimestampMillis = combinedCrossTs,
                            isPrimary = true,
                            status = CandidateStatus.INCLUDED,
                            exclusionReason = null
                        )
                    )
                }
                fxFailedProviders.forEach { provider ->
                    if (none { it.sourceName == provider }) {
                        add(
                            SourceCandidateRecord(
                                sourceName = provider,
                                priceToman = null,
                                rawPrice = null,
                                sourceTimestampMillis = 0L,
                                isPrimary = false,
                                status = CandidateStatus.REJECTED,
                                exclusionReason = CandidateExclusionReason.PROVIDER_FAILURE
                            )
                        )
                    }
                }
            }
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
            singleSource = singleSource,
            isCalculatedPrice = isCalculatedPrice,
            candidates = candidateRecords
        )
    }
}

