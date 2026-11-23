package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Cross-source price validation, data freshness derivation, sparkline scaling,
 * and cold-offline baseline catalog helpers shared across asset mappers.
 */
internal object MarketValidationHelpers {

    fun validateCrossSourcePrices(
        candidates: List<MarketPriceMapper.PriceCandidate>,
        thresholdPct: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): MarketPriceMapper.ConsensusResult? {
        val valid = candidates.filter { it.price > 0.0 && it.price.isFinite() }
        if (valid.isEmpty()) return null

        if (valid.size == MarketValidationConfig.SINGLE_SOURCE_COUNT) {
            val sole = valid.first()
            val ts = sole.timestampMillis.takeIf { it > 0L } ?: nowMillis
            return MarketPriceMapper.ConsensusResult(
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
                MarketPriceMapper.ConsensusResult(
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
                MarketPriceMapper.ConsensusResult(
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
            MarketPriceMapper.ConsensusResult(
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
            MarketPriceMapper.ConsensusResult(
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
