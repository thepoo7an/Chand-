package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.model.SourceCandidateRecord
import kotlin.math.abs

/**
 * Cross-source price validation, data freshness derivation, sparkline scaling,
 * and cold-offline baseline catalog helpers shared across asset mappers.
 */
internal object MarketValidationHelpers {

    fun validateCrossSourcePrices(
        candidates: List<MarketPriceMapper.PriceCandidate>,
        thresholdPct: Double,
        nowMillis: Long = System.currentTimeMillis(),
        staleMaxAgeMillis: Long? = null
    ): MarketPriceMapper.ConsensusResult? {
        val rejectedRecords = mutableListOf<SourceCandidateRecord>()
        val validPool = mutableListOf<MarketPriceMapper.PriceCandidate>()

        for (cand in candidates) {
            if (cand.unavailabilityReason != null) {
                rejectedRecords.add(
                    SourceCandidateRecord(
                        sourceName = cand.sourceName,
                        priceToman = cand.price.takeIf { it.isFinite() && it > 0.0 },
                        rawPrice = cand.price.takeIf { it.isFinite() && it > 0.0 },
                        sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
                        isPrimary = cand.isPrimary,
                        status = CandidateStatus.REJECTED,
                        exclusionReason = cand.unavailabilityReason
                    )
                )
            } else if (!cand.price.isFinite() || cand.price <= 0.0) {
                rejectedRecords.add(
                    SourceCandidateRecord(
                        sourceName = cand.sourceName,
                        priceToman = null,
                        rawPrice = cand.price.takeIf { it.isFinite() },
                        sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
                        isPrimary = cand.isPrimary,
                        status = CandidateStatus.REJECTED,
                        exclusionReason = CandidateExclusionReason.INVALID
                    )
                )
            } else {
                validPool.add(cand)
            }
        }

        if (validPool.isEmpty()) return null

        val activeValid: List<MarketPriceMapper.PriceCandidate> = if (staleMaxAgeMillis != null && staleMaxAgeMillis > 0L) {
            val hasFreshKnown = validPool.any {
                it.timestampMillis > 0L && isTimestampFresh(it.timestampMillis, nowMillis, staleMaxAgeMillis)
            }
            if (hasFreshKnown) {
                val kept = mutableListOf<MarketPriceMapper.PriceCandidate>()
                for (cand in validPool) {
                    if (cand.timestampMillis > 0L && !isTimestampFresh(cand.timestampMillis, nowMillis, staleMaxAgeMillis)) {
                        rejectedRecords.add(
                            SourceCandidateRecord(
                                sourceName = cand.sourceName,
                                priceToman = cand.price,
                                rawPrice = cand.price,
                                sourceTimestampMillis = cand.timestampMillis,
                                isPrimary = cand.isPrimary,
                                status = CandidateStatus.REJECTED,
                                exclusionReason = CandidateExclusionReason.STALE
                            )
                        )
                    } else {
                        kept.add(cand)
                    }
                }
                kept.ifEmpty { validPool }
            } else {
                validPool
            }
        } else {
            validPool
        }

        fun toIncluded(cand: MarketPriceMapper.PriceCandidate) = SourceCandidateRecord(
            sourceName = cand.sourceName,
            priceToman = cand.price,
            rawPrice = cand.price,
            sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
            isPrimary = cand.isPrimary,
            status = CandidateStatus.INCLUDED,
            exclusionReason = null
        )

        fun toRejectedDisagreement(cand: MarketPriceMapper.PriceCandidate) = SourceCandidateRecord(
            sourceName = cand.sourceName,
            priceToman = cand.price,
            rawPrice = cand.price,
            sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
            isPrimary = cand.isPrimary,
            status = CandidateStatus.REJECTED,
            exclusionReason = CandidateExclusionReason.EXCESSIVE_DISAGREEMENT
        )

        if (activeValid.size == MarketValidationConfig.SINGLE_SOURCE_COUNT) {
            val sole = activeValid.first()
            val ts = sole.timestampMillis.takeIf { it > 0L } ?: 0L
            val allRecords = buildList {
                add(toIncluded(sole))
                addAll(rejectedRecords)
            }
            return MarketPriceMapper.ConsensusResult(
                price = sole.price,
                disputed = false,
                singleSource = true,
                acceptedSources = listOf(sole.sourceName),
                excludedSources = rejectedRecords.map { it.sourceName },
                sourceTimestampMillis = ts,
                selectedCandidate = sole,
                isCalculated = false,
                sourceAttributionLabel = sole.sourceName,
                candidateRecords = allRecords
            )
        }

        val sorted = activeValid.sortedBy { it.price }
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].price
        } else {
            (sorted[sorted.size / 2 - 1].price + sorted[sorted.size / 2].price) / 2.0
        }

        val primary = activeValid.firstOrNull { it.isPrimary } ?: activeValid.first()

        if (activeValid.size == MarketValidationConfig.DISPUTED_PAIR_SOURCE_COUNT) {
            val maxDevPct = activeValid.maxOf { devPct(it.price, median) }
            return if (maxDevPct > thresholdPct) {
                val ts = primary.timestampMillis.takeIf { it > 0L } ?: 0L
                val disagreed = activeValid.filter { it !== primary }
                val allRecords = buildList {
                    add(toIncluded(primary))
                    disagreed.forEach { add(toRejectedDisagreement(it)) }
                    addAll(rejectedRecords)
                }
                MarketPriceMapper.ConsensusResult(
                    price = primary.price,
                    disputed = true,
                    singleSource = false,
                    acceptedSources = listOf(primary.sourceName),
                    excludedSources = allRecords.filter { it.status == CandidateStatus.REJECTED }.map { it.sourceName },
                    sourceTimestampMillis = ts,
                    selectedCandidate = primary,
                    isCalculated = false,
                    sourceAttributionLabel = primary.sourceName,
                    candidateRecords = allRecords
                )
            } else {
                val allEqual = activeValid.all { abs(it.price - median) <= 1e-6 }
                val isCalculated = !allEqual
                val names = activeValid.joinToString("+") { it.sourceName }
                val label = if (isCalculated) "Calculated Avg ($names)" else names
                val ts = if (activeValid.all { it.timestampMillis > 0L }) {
                    activeValid.minOf { it.timestampMillis }
                } else {
                    0L
                }
                val chosen = activeValid.firstOrNull { abs(it.price - median) <= 1e-6 } ?: primary
                val allRecords = buildList {
                    activeValid.forEach { add(toIncluded(it)) }
                    addAll(rejectedRecords)
                }
                MarketPriceMapper.ConsensusResult(
                    price = median,
                    disputed = false,
                    singleSource = false,
                    acceptedSources = activeValid.map { it.sourceName },
                    excludedSources = rejectedRecords.map { it.sourceName },
                    sourceTimestampMillis = ts,
                    selectedCandidate = chosen,
                    isCalculated = isCalculated,
                    sourceAttributionLabel = label,
                    candidateRecords = allRecords
                )
            }
        }

        // >= 3 sources: use median and exclude outliers deviating > thresholdPct from median
        val inliers = activeValid.filter { devPct(it.price, median) <= thresholdPct }
        val outliers = activeValid.filter { devPct(it.price, median) > thresholdPct }

        return if (inliers.size >= 2) {
            val allEqual = inliers.all { abs(it.price - median) <= 1e-6 }
            val isCalculated = !allEqual
            val names = inliers.joinToString("+") { it.sourceName }
            val label = if (isCalculated) "Calculated Median ($names)" else names
            val ts = if (inliers.all { it.timestampMillis > 0L }) {
                inliers.minOf { it.timestampMillis }
            } else {
                0L
            }
            val chosen = inliers.firstOrNull { abs(it.price - median) <= 1e-6 }
                ?: inliers.firstOrNull { it.isPrimary }
                ?: inliers.first()
            val allRecords = buildList {
                inliers.forEach { add(toIncluded(it)) }
                outliers.forEach { add(toRejectedDisagreement(it)) }
                addAll(rejectedRecords)
            }
            MarketPriceMapper.ConsensusResult(
                price = median,
                disputed = false,
                singleSource = false,
                acceptedSources = inliers.map { it.sourceName },
                excludedSources = allRecords.filter { it.status == CandidateStatus.REJECTED }.map { it.sourceName },
                sourceTimestampMillis = ts,
                selectedCandidate = chosen,
                isCalculated = isCalculated,
                sourceAttributionLabel = label,
                candidateRecords = allRecords
            )
        } else {
            // Multiple sources spread widely with <2 corroborating inliers -> fall back to primary & mark disputed
            // Never attribute a synthetic median to a single provider that did not return that value.
            val chosen = primary
            val ts = chosen.timestampMillis.takeIf { it > 0L } ?: 0L
            val disagreed = activeValid.filter { it !== chosen }
            val allRecords = buildList {
                add(toIncluded(chosen))
                disagreed.forEach { add(toRejectedDisagreement(it)) }
                addAll(rejectedRecords)
            }
            MarketPriceMapper.ConsensusResult(
                price = chosen.price,
                disputed = true,
                singleSource = false,
                acceptedSources = listOf(chosen.sourceName),
                excludedSources = allRecords.filter { it.status == CandidateStatus.REJECTED }.map { it.sourceName },
                sourceTimestampMillis = ts,
                selectedCandidate = chosen,
                isCalculated = false,
                sourceAttributionLabel = chosen.sourceName,
                candidateRecords = allRecords
            )
        }
    }

    private fun isTimestampFresh(
        sourceTimestampMillis: Long,
        nowMillis: Long,
        maxAgeMillis: Long
    ): Boolean {
        if (sourceTimestampMillis <= 0L) return false
        if (sourceTimestampMillis > nowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS) {
            return false
        }
        val ageMillis = (nowMillis - sourceTimestampMillis).coerceAtLeast(0L)
        return ageMillis <= maxAgeMillis
    }

    private fun devPct(value: Double, reference: Double): Double {
        if (reference <= 0.0) return Double.POSITIVE_INFINITY
        return (abs(value - reference) / reference) * 100.0
    }

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
        if (sourceTimestampMillis <= 0L) return DataFreshness.DELAYED
        if (sourceTimestampMillis > nowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS) {
            return DataFreshness.DELAYED
        }

        val ageMillis = (nowMillis - sourceTimestampMillis).coerceAtLeast(0L)
        val maxAge = when (category) {
            AssetCategory.CRYPTO -> MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
            AssetCategory.OIL -> MarketValidationConfig.effectiveOilLiveMaxAgeMillis()
            AssetCategory.FIAT, AssetCategory.GOLD -> MarketValidationConfig.effectiveFiatGoldLiveMaxAgeMillis()
            AssetCategory.ALL, AssetCategory.PINNED -> MarketValidationConfig.effectiveFiatGoldLiveMaxAgeMillis()
        }
        return if (ageMillis <= maxAge) DataFreshness.LIVE else DataFreshness.DELAYED
    }

    fun offlineReferenceCatalog(nowMillis: Long = System.currentTimeMillis()): CachedMarketSnapshot {
        val usd = MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN
        val eurPerUsd = 0.92
        val aedPerUsd = 3.6725
        val gbpPerUsd = 0.79
        val ons = MarketPriceMapper.DEFAULT_OFFLINE_ONS_USD
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
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
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L
            ),
            MarketAsset(
                id = "BRENT",
                symbol = "BRENT",
                nameFa = "نفت خام برنت",
                nameEn = "Brent Crude",
                category = AssetCategory.OIL,
                badgeText = "BRENT",
                accentHex = 0xFF1F2937,
                priceToman = 0.0,
                priceUsd = null,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L,
                primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
                isChange24hAvailable = false
            ),
            MarketAsset(
                id = "WTI",
                symbol = "WTI",
                nameFa = "نفت خام WTI",
                nameEn = "WTI Crude",
                category = AssetCategory.OIL,
                badgeText = "WTI",
                accentHex = 0xFF334155,
                priceToman = 0.0,
                priceUsd = null,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = null,
                low24hToman = null,
                sparkline = emptyList(),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = 0L,
                freshness = DataFreshness.UNAVAILABLE,
                sourceTimestampMillis = 0L,
                fetchedAtMillis = 0L,
                primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
                isChange24hAvailable = false
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
