package ir.thepoo7an.chand.ui.components

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters

enum class SourceBreakdownStatus {
    INCLUDED,
    REJECTED,
    DELAYED,
    UNAVAILABLE,
    INVALID
}

data class SourceBreakdownRow(
    val sourceName: String,
    val isPrimary: Boolean,
    val reportedPriceToman: Double?,
    val reportedPriceText: String,
    val unitCurrencyText: String,
    val rawQuoteText: String?,
    val sourceTimestampMillis: Long,
    val hasValidTimestamp: Boolean,
    val timestampText: String,
    val fetchedAtMillis: Long,
    val hasValidFetchedAt: Boolean,
    val fetchedAtText: String,
    val status: SourceBreakdownStatus,
    val statusLabel: String,
    val contributedToFinal: Boolean,
    val contributionLabel: String,
    val exclusionReason: CandidateExclusionReason?,
    val exclusionReasonText: String?
)

data class MarketSourceBreakdownModel(
    val assetId: String,
    val symbol: String,
    val displayName: String,
    val mainPriceToman: Double,
    val mainPriceText: String,
    val mainCardPriceText: String,
    val unitCurrencyText: String,
    val isCalculatedPrice: Boolean,
    val priceKindLabel: String,
    val sourceAttributionLabel: String,
    val freshness: DataFreshness,
    val freshnessLabel: String,
    val lastSuccessfulUpdateMillis: Long,
    val lastSuccessfulUpdateText: String,
    val hasLastSuccessfulUpdate: Boolean,
    val disputed: Boolean,
    val rows: List<SourceBreakdownRow>
)

object MarketSourceBreakdownBuilder {

    fun build(
        asset: MarketAsset,
        isFa: Boolean,
        usePersianDigits: Boolean,
        strings: ChandStrings,
        referenceNowMillis: Long = System.currentTimeMillis()
    ): MarketSourceBreakdownModel {
        val isOilUsd = asset.isUsdPerBarrel
        val isMainUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
        val mainPriceText = when {
            isMainUnavailable -> "—"
            isOilUsd -> NumberFormatters.formatUsd(asset.priceUsd ?: 0.0, usePersianDigits)
            else -> NumberFormatters.formatToman(asset.priceToman, usePersianDigits)
        }
        val mainCardPriceText = when {
            isMainUnavailable -> "—"
            isOilUsd -> NumberFormatters.formatUsd(asset.priceUsd ?: 0.0, usePersianDigits)
            else -> NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)
        }

        val lastUpdatedMillis = asset.updatedAtMillis.takeIf { it > 0L }
            ?: asset.fetchedAtMillis.takeIf { it > 0L }
            ?: 0L
        val hasLastUpdate = lastUpdatedMillis > 0L &&
            lastUpdatedMillis <= referenceNowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS
        val lastUpdateText = if (hasLastUpdate) {
            NumberFormatters.formatJalaliDateTime(lastUpdatedMillis, usePersianDigits)
        } else {
            strings.sourceTimestampUnavailable
        }

        val freshnessLabel = when (asset.freshness) {
            DataFreshness.LIVE -> strings.liveBadge
            DataFreshness.DELAYED -> strings.delayedBadge
            DataFreshness.CACHED -> strings.cachedBadge
            DataFreshness.UNAVAILABLE -> strings.unavailableBadge
        }

        val maxAgeMillis = when (asset.category) {
            AssetCategory.CRYPTO -> MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
            AssetCategory.OIL -> MarketValidationConfig.effectiveOilLiveMaxAgeMillis()
            AssetCategory.FIAT, AssetCategory.GOLD, AssetCategory.ALL, AssetCategory.PINNED ->
                MarketValidationConfig.effectiveFiatGoldLiveMaxAgeMillis()
        }

        val effectiveCandidates: List<SourceCandidateRecord> = if (asset.candidates.isNotEmpty()) {
            asset.candidates
        } else if (!isMainUnavailable && asset.sourceLabel.isNotBlank()) {
            listOf(
                SourceCandidateRecord(
                    sourceName = asset.sourceLabel,
                    priceToman = asset.priceToman.takeIf { it > 0.0 },
                    rawPrice = if (isOilUsd) asset.priceUsd else asset.priceToman,
                    sourceTimestampMillis = asset.sourceTimestampMillis.coerceAtLeast(0L),
                    fetchedAtMillis = asset.fetchedAtMillis.coerceAtLeast(0L),
                    isPrimary = true,
                    status = CandidateStatus.INCLUDED,
                    exclusionReason = null,
                    detail = if (isOilUsd && asset.priceUsd != null) "usdBbl=${asset.priceUsd}" else ""
                )
            )
        } else {
            emptyList()
        }

        val rows = effectiveCandidates.mapIndexed { index, candidate ->
            index to mapRow(
                asset = asset,
                candidate = candidate,
                maxAgeMillis = maxAgeMillis,
                usePersianDigits = usePersianDigits,
                strings = strings,
                referenceNowMillis = referenceNowMillis
            )
        }.sortedWith(
            compareBy<Pair<Int, SourceBreakdownRow>>(
                { (_, row) -> !row.contributedToFinal },
                { (_, row) ->
                    when (row.status) {
                        SourceBreakdownStatus.INCLUDED -> 0
                        SourceBreakdownStatus.DELAYED -> 1
                        SourceBreakdownStatus.REJECTED -> 2
                        SourceBreakdownStatus.INVALID -> 3
                        SourceBreakdownStatus.UNAVAILABLE -> 4
                    }
                },
                { (_, row) -> !row.isPrimary },
                { (index, _) -> index }
            )
        ).map { it.second }

        return MarketSourceBreakdownModel(
            assetId = asset.id,
            symbol = asset.symbol,
            displayName = if (isFa) asset.nameFa else asset.nameEn,
            mainPriceToman = if (isOilUsd) (asset.priceUsd ?: 0.0) else asset.priceToman,
            mainPriceText = mainPriceText,
            mainCardPriceText = mainCardPriceText,
            unitCurrencyText = if (isOilUsd) strings.sourceUnitCurrencyUsdPerBarrel else strings.sourceUnitCurrencyToman,
            isCalculatedPrice = asset.isCalculatedPrice,
            priceKindLabel = if (asset.isCalculatedPrice) {
                strings.calculatedMainPriceLabel
            } else {
                strings.primaryMainPriceLabel
            },
            sourceAttributionLabel = asset.sourceLabel.ifBlank { strings.unavailableBadge },
            freshness = asset.freshness,
            freshnessLabel = freshnessLabel,
            lastSuccessfulUpdateMillis = lastUpdatedMillis,
            lastSuccessfulUpdateText = lastUpdateText,
            hasLastSuccessfulUpdate = hasLastUpdate,
            disputed = asset.disputed,
            rows = rows
        )
    }

    private fun mapRow(
        asset: MarketAsset,
        candidate: SourceCandidateRecord,
        maxAgeMillis: Long,
        usePersianDigits: Boolean,
        strings: ChandStrings,
        referenceNowMillis: Long
    ): SourceBreakdownRow {
        val isOilUsd = asset.isUsdPerBarrel
        val validPriceToman = candidate.priceToman?.takeIf { it.isFinite() && it > 0.0 }
        val validPriceUsdBbl = if (isOilUsd) candidate.rawPrice?.takeIf { it.isFinite() && it > 0.0 } else null
        val reportedPriceText = when {
            isOilUsd -> validPriceUsdBbl?.let { NumberFormatters.formatUsd(it, usePersianDigits) } ?: "—"
            else -> validPriceToman?.let { NumberFormatters.formatToman(it, usePersianDigits) } ?: "—"
        }

        val ts = candidate.sourceTimestampMillis
        val isFutureTs = ts > referenceNowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS
        val hasValidTimestamp = ts > 0L && !isFutureTs
        val isTimestampStale = hasValidTimestamp &&
            (referenceNowMillis - ts).coerceAtLeast(0L) > maxAgeMillis

        val timestampText = if (hasValidTimestamp) {
            NumberFormatters.formatJalaliDateTime(ts, usePersianDigits)
        } else {
            strings.sourceTimestampUnavailable
        }

        val fetchedTs = candidate.fetchedAtMillis
        val isFutureFetchTs = fetchedTs > referenceNowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS
        val hasValidFetchedAt = fetchedTs > 0L && !isFutureFetchTs
        val fetchedAtText = if (hasValidFetchedAt) {
            NumberFormatters.formatJalaliDateTime(fetchedTs, usePersianDigits)
        } else {
            strings.sourceTimestampUnavailable
        }

        val assetIsUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
        val hasReportedPrimaryPrice = if (isOilUsd) validPriceUsdBbl != null else validPriceToman != null

        val status: SourceBreakdownStatus
        val contributed: Boolean
        val exclusionText: String?

        if (candidate.status == CandidateStatus.INCLUDED && !assetIsUnavailable && hasReportedPrimaryPrice) {
            contributed = true
            status = if (!hasValidTimestamp || isTimestampStale || asset.freshness == DataFreshness.DELAYED) {
                SourceBreakdownStatus.DELAYED
            } else {
                SourceBreakdownStatus.INCLUDED
            }
            exclusionText = null
        } else if (candidate.status == CandidateStatus.INCLUDED) {
            contributed = false
            status = SourceBreakdownStatus.UNAVAILABLE
            exclusionText = strings.exclusionReasonMissingData
        } else {
            contributed = false
            status = when (candidate.exclusionReason) {
                CandidateExclusionReason.INVALID -> SourceBreakdownStatus.INVALID
                CandidateExclusionReason.MISSING_DATA,
                CandidateExclusionReason.PROVIDER_FAILURE -> SourceBreakdownStatus.UNAVAILABLE
                CandidateExclusionReason.STALE,
                CandidateExclusionReason.EXCESSIVE_DISAGREEMENT,
                null -> SourceBreakdownStatus.REJECTED
            }
            exclusionText = when (candidate.exclusionReason) {
                CandidateExclusionReason.EXCESSIVE_DISAGREEMENT -> strings.exclusionReasonDisagreement
                CandidateExclusionReason.STALE -> strings.exclusionReasonStale
                CandidateExclusionReason.INVALID -> strings.exclusionReasonInvalid
                CandidateExclusionReason.MISSING_DATA -> strings.exclusionReasonMissingData
                CandidateExclusionReason.PROVIDER_FAILURE -> strings.exclusionReasonProviderFailure
                null -> strings.exclusionReasonValidationFailure
            }
        }

        val statusLabel = when (status) {
            SourceBreakdownStatus.INCLUDED -> strings.sourceStatusIncluded
            SourceBreakdownStatus.REJECTED -> strings.sourceStatusRejected
            SourceBreakdownStatus.DELAYED -> strings.sourceStatusDelayed
            SourceBreakdownStatus.UNAVAILABLE -> strings.sourceStatusUnavailable
            SourceBreakdownStatus.INVALID -> strings.sourceStatusInvalid
        }

        val unitCurrencyText = when {
            isOilUsd -> strings.sourceUnitCurrencyUsdPerBarrel
            validPriceToman != null -> strings.sourceUnitCurrencyToman
            candidate.rawPrice != null && candidate.detail.startsWith("xauUsd=") -> "USD / oz"
            candidate.rawPrice != null && candidate.detail.startsWith("fx=") -> "FX / USD"
            else -> strings.sourceUnitCurrencyToman
        }

        val rawQuoteText = if (isOilUsd) {
            validPriceToman?.let { tmn ->
                "${strings.oilTomanEstimatePrefix} ${NumberFormatters.formatToman(tmn, usePersianDigits)} ${strings.tomanUnit}"
            }
        } else {
            candidate.rawPrice
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { raw ->
                    when {
                        candidate.detail.startsWith("xauUsd=") ->
                            "${strings.sourceRawQuotePrefix} ${NumberFormatters.formatUsd(raw, usePersianDigits)}/oz"
                        candidate.detail.startsWith("fx=") ->
                            "${strings.sourceRawQuotePrefix} ${NumberFormatters.formatConverterAmount(raw, usePersianDigits)} ${asset.symbol}/USD"
                        else -> null
                    }
                }
        }

        return SourceBreakdownRow(
            sourceName = candidate.sourceName,
            isPrimary = candidate.isPrimary,
            reportedPriceToman = validPriceToman,
            reportedPriceText = reportedPriceText,
            unitCurrencyText = unitCurrencyText,
            rawQuoteText = rawQuoteText,
            sourceTimestampMillis = ts.coerceAtLeast(0L),
            hasValidTimestamp = hasValidTimestamp,
            timestampText = timestampText,
            fetchedAtMillis = fetchedTs.coerceAtLeast(0L),
            hasValidFetchedAt = hasValidFetchedAt,
            fetchedAtText = fetchedAtText,
            status = status,
            statusLabel = statusLabel,
            contributedToFinal = contributed,
            contributionLabel = if (contributed) strings.sourceContributedYes else strings.sourceContributedNo,
            exclusionReason = candidate.exclusionReason,
            exclusionReasonText = exclusionText
        )
    }
}
