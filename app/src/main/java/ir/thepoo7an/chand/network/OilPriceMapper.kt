package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceDisplayUnit
import ir.thepoo7an.chand.model.SourceCandidateRecord
import kotlin.math.abs

/**
 * Pure mapper & cross-source consensus engine for global crude oil benchmarks (`BRENT`, `WTI`).
 *
 * Design guarantees:
 * - Primary unit is ALWAYS `USD/barrel` (`PriceDisplayUnit.USD_PER_BARREL`).
 * - Consensus & disagreement validation run exclusively on `USD/barrel` quotes, isolated from FX/Gold/Crypto.
 * - Optional Toman equivalent (`priceToman`) is computed ONLY when a live/cached valid `USD/TMN` basis is
 *   available (`usdToman > 1_000.0 && usdBasisFreshness != UNAVAILABLE`), and never gates oil USD availability.
 * - Missing 24-hour percentage change is represented explicitly via `isChange24hAvailable = false` rather than
 *   fabricating `0.0%` or reusing unrelated USD/TMN percentage changes.
 */
object OilPriceMapper {

    data class OilSpec(
        val id: String,
        val symbol: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val accentHex: Long,
        val isPrimaryBenchmark: Boolean
    )

    val SUPPORTED_OILS: List<OilSpec> = listOf(
        OilSpec(
            id = "BRENT",
            symbol = "BRENT",
            nameFa = "نفت خام برنت",
            nameEn = "Brent Crude",
            badge = "BRENT",
            accentHex = 0xFF1F2937,
            isPrimaryBenchmark = true
        ),
        OilSpec(
            id = "WTI",
            symbol = "WTI",
            nameFa = "نفت خام WTI",
            nameEn = "WTI Crude",
            badge = "WTI",
            accentHex = 0xFF334155,
            isPrimaryBenchmark = false
        )
    )

    fun mapOilAssets(
        specs: List<OilSpec> = SUPPORTED_OILS,
        batch: OilApiService.OilMarketBatch,
        usdToman: Double,
        usdBasisFreshness: DataFreshness,
        cachedAssetsById: Map<String, MarketAsset>,
        nowMillis: Long
    ): List<MarketAsset> {
        val hasUsableUsdAnchor = usdToman.isFinite() &&
            usdToman > 1_000.0 &&
            usdBasisFreshness != DataFreshness.UNAVAILABLE

        return specs.map { spec ->
            mapSingleOilAsset(
                spec = spec,
                batch = batch,
                usdToman = if (hasUsableUsdAnchor) usdToman else 0.0,
                cached = cachedAssetsById[spec.id],
                nowMillis = nowMillis
            )
        }
    }

    fun mapSingleOilAsset(
        spec: OilSpec,
        batch: OilApiService.OilMarketBatch,
        usdToman: Double,
        cached: MarketAsset?,
        nowMillis: Long
    ): MarketAsset {
        val candidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
        val quoteBySource = LinkedHashMap<String, OilApiService.OilQuoteSnapshot>()

        batch.configuredProviders.forEachIndexed { idx, providerName ->
            val providerFailed = providerName in batch.failedProviders
            val snapshot = batch.quotesByProvider[providerName]?.get(spec.id)
            if (providerFailed) {
                candidates.add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = providerName,
                        price = Double.NaN,
                        isPrimary = idx == 0,
                        timestampMillis = 0L,
                        unavailabilityReason = CandidateExclusionReason.PROVIDER_FAILURE
                    )
                )
            } else if (snapshot == null) {
                candidates.add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = providerName,
                        price = Double.NaN,
                        isPrimary = idx == 0,
                        timestampMillis = 0L,
                        unavailabilityReason = CandidateExclusionReason.MISSING_DATA
                    )
                )
            } else if (snapshot.failureReason != null) {
                candidates.add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = providerName,
                        price = snapshot.priceUsd ?: Double.NaN,
                        isPrimary = snapshot.isPrimary || idx == 0,
                        timestampMillis = snapshot.sourceTimestampMillis,
                        unavailabilityReason = snapshot.failureReason
                    )
                )
            } else {
                val usd = snapshot.priceUsd
                val validCurrency = snapshot.currency.equals("USD", ignoreCase = true)
                val validUnit = snapshot.unit == OilApiService.UNIT_USD_PER_BARREL
                if (!validCurrency || !validUnit || usd == null || !usd.isFinite() ||
                    usd !in OilApiService.MIN_VALID_USD_PER_BARREL..OilApiService.MAX_VALID_USD_PER_BARREL
                ) {
                    candidates.add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = providerName,
                            price = usd?.takeIf { it.isFinite() } ?: Double.NaN,
                            isPrimary = snapshot.isPrimary || idx == 0,
                            timestampMillis = snapshot.sourceTimestampMillis,
                            unavailabilityReason = CandidateExclusionReason.INVALID
                        )
                    )
                } else {
                    quoteBySource[providerName] = snapshot
                    candidates.add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = providerName,
                            price = usd,
                            isPrimary = snapshot.isPrimary || idx == 0,
                            timestampMillis = snapshot.sourceTimestampMillis,
                            changePct = snapshot.change24hPct ?: Double.NaN
                        )
                    )
                }
            }
        }

        val consensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.effectiveOilDisagreementThresholdPct(),
            nowMillis = nowMillis,
            staleMaxAgeMillis = MarketValidationConfig.effectiveOilLiveMaxAgeMillis()
        )

        if (consensus != null) {
            val priceUsd = consensus.price
            val hasUsdToman = usdToman.isFinite() && usdToman > 1_000.0
            val estimatedToman = if (hasUsdToman) priceUsd * usdToman else 0.0

            val candidateRecords = consensus.candidateRecords.map { rec ->
                val rawUsd = rec.rawPrice?.takeIf { it.isFinite() && it > 0.0 }
                rec.copy(
                    priceToman = if (hasUsdToman && rawUsd != null) rawUsd * usdToman else null,
                    rawPrice = rawUsd,
                    detail = if (rawUsd != null) "usdBbl=$rawUsd" else ""
                )
            }

            // Use 24h change ONLY from included sources that reliably reported it.
            val includedQuotes = consensus.acceptedSources.mapNotNull { quoteBySource[it] }
            val quotesWithPct = includedQuotes.filter {
                it.change24hPct != null && it.change24hPct.isFinite()
            }
            val change24hPct: Double
            val change24hUsd: Double?
            val isChangeAvailable: Boolean
            if (quotesWithPct.isNotEmpty()) {
                isChangeAvailable = true
                val primaryWithChange = quotesWithPct.firstOrNull {
                    it.sourceName == consensus.selectedCandidate.sourceName
                }
                if (consensus.isCalculated && quotesWithPct.size >= 2) {
                    change24hPct = quotesWithPct.mapNotNull { it.change24hPct }.average()
                    val avgUsdChange = quotesWithPct.mapNotNull { it.change24hUsd }
                    change24hUsd = if (avgUsdChange.isNotEmpty()) {
                        avgUsdChange.average()
                    } else {
                        val prev = priceUsd / (1.0 + change24hPct / 100.0)
                        if (prev.isFinite() && prev > 0.0) priceUsd - prev else null
                    }
                } else {
                    val chosen = primaryWithChange ?: quotesWithPct.first()
                    change24hPct = chosen.change24hPct!!
                    change24hUsd = chosen.change24hUsd ?: run {
                        val prev = priceUsd / (1.0 + change24hPct / 100.0)
                        if (prev.isFinite() && prev > 0.0) priceUsd - prev else null
                    }
                }
            } else {
                isChangeAvailable = false
                change24hPct = 0.0
                change24hUsd = null
            }

            val changeToman = if (hasUsdToman && change24hUsd != null && change24hUsd.isFinite()) {
                change24hUsd * usdToman
            } else {
                0.0
            }

            val highUsd = includedQuotes.mapNotNull { it.high24hUsd }
                .filter { it.isFinite() && it > 0.0 }
                .maxOrNull()
                ?.let { maxOf(it, priceUsd) }
            val lowUsd = includedQuotes.mapNotNull { it.low24hUsd }
                .filter { it.isFinite() && it > 0.0 }
                .minOrNull()
                ?.let { minOf(it, priceUsd) }

            val rawSparkUsd = includedQuotes
                .firstOrNull { it.sparklineUsd.size >= 2 }
                ?.sparklineUsd
                ?: quoteBySource.values.firstOrNull { it.sparklineUsd.size >= 2 }?.sparklineUsd
                ?: emptyList()
            val sparklineUsd = MarketValidationHelpers.scaleSeriesToCurrent(rawSparkUsd, priceUsd)

            val freshness = MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.OIL,
                fetchedThisRefresh = true,
                hasUsableValue = true,
                isCachedSource = false,
                sourceTimestampMillis = consensus.sourceTimestampMillis,
                nowMillis = nowMillis
            )

            return MarketAsset(
                id = spec.id,
                symbol = spec.symbol,
                nameFa = spec.nameFa,
                nameEn = spec.nameEn,
                category = AssetCategory.OIL,
                badgeText = spec.badge,
                accentHex = spec.accentHex,
                priceToman = estimatedToman,
                priceUsd = priceUsd,
                change24hPercent = change24hPct,
                change24hToman = changeToman,
                high24hToman = if (hasUsdToman && highUsd != null) highUsd * usdToman else null,
                low24hToman = if (hasUsdToman && lowUsd != null) lowUsd * usdToman else null,
                sparkline = sparklineUsd,
                sourceLabel = consensus.sourceAttributionLabel.ifBlank { consensus.selectedCandidate.sourceName },
                updatedAtMillis = nowMillis,
                freshness = freshness,
                sourceTimestampMillis = consensus.sourceTimestampMillis,
                fetchedAtMillis = nowMillis,
                disputed = consensus.disputed,
                singleSource = consensus.singleSource,
                isCalculatedPrice = consensus.isCalculated,
                candidates = candidateRecords,
                primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
                isChange24hAvailable = isChangeAvailable,
                change24hUsd = change24hUsd,
                high24hUsd = highUsd,
                low24hUsd = lowUsd
            )
        }

        // All live oil providers failed/invalid/missing -> fall back to persisted cache ONLY if valid USD/bbl exists
        val cachedUsd = cached?.priceUsd?.takeIf {
            it.isFinite() && it in OilApiService.MIN_VALID_USD_PER_BARREL..OilApiService.MAX_VALID_USD_PER_BARREL
        }
        val rejectionRecords = candidates.map { cand ->
            SourceCandidateRecord(
                sourceName = cand.sourceName,
                priceToman = null,
                rawPrice = cand.price.takeIf { it.isFinite() && it > 0.0 },
                sourceTimestampMillis = cand.timestampMillis.coerceAtLeast(0L),
                isPrimary = cand.isPrimary,
                status = CandidateStatus.REJECTED,
                exclusionReason = cand.unavailabilityReason ?: CandidateExclusionReason.MISSING_DATA,
                detail = if (cand.price.isFinite() && cand.price > 0.0) "usdBbl=${cand.price}" else ""
            )
        }

        if (cached != null && cachedUsd != null && cached.freshness != DataFreshness.UNAVAILABLE) {
            val hasUsdToman = usdToman.isFinite() && usdToman > 1_000.0
            return cached.copy(
                priceUsd = cachedUsd,
                priceToman = if (hasUsdToman) cachedUsd * usdToman else 0.0,
                primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
                freshness = DataFreshness.CACHED,
                fetchedAtMillis = nowMillis,
                candidates = rejectionRecords.ifEmpty { cached.candidates }
            )
        }

        return MarketAsset(
            id = spec.id,
            symbol = spec.symbol,
            nameFa = spec.nameFa,
            nameEn = spec.nameEn,
            category = AssetCategory.OIL,
            badgeText = spec.badge,
            accentHex = spec.accentHex,
            priceToman = 0.0,
            priceUsd = null,
            change24hPercent = 0.0,
            change24hToman = 0.0,
            high24hToman = null,
            low24hToman = null,
            sparkline = emptyList(),
            sourceLabel = "",
            updatedAtMillis = 0L,
            freshness = DataFreshness.UNAVAILABLE,
            sourceTimestampMillis = 0L,
            fetchedAtMillis = nowMillis,
            disputed = false,
            singleSource = false,
            isCalculatedPrice = false,
            candidates = rejectionRecords,
            primaryUnit = PriceDisplayUnit.USD_PER_BARREL,
            isChange24hAvailable = false,
            change24hUsd = null,
            high24hUsd = null,
            low24hUsd = null
        )
    }
}
