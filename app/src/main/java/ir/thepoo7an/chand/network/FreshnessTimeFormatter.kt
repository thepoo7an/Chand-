package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.stringsFor

enum class FreshnessAgeTier {
    NORMAL,
    STALE_AMBER,
    CRITICAL_RED,
    UNAVAILABLE
}

/**
 * Formats per-asset relative age (`<= 30m` normal, `> 30m` amber, `> 2h` red)
 * from genuine `lastSuccessfulFetch` timestamps without overwriting old cached fetch times.
 */
object FreshnessTimeFormatter {

    const val AMBER_AGE_THRESHOLD_MS: Long = 30L * 60L * 1000L
    const val RED_AGE_THRESHOLD_MS: Long = 2L * 60L * 60L * 1000L

    fun lastSuccessfulFetchMillis(asset: MarketAsset): Long =
        asset.fetchedAtMillis.takeIf { it > 0L }
            ?: asset.updatedAtMillis.takeIf { it > 0L }
            ?: 0L

    fun resolveAssetTier(
        asset: MarketAsset,
        nowMillis: Long = System.currentTimeMillis()
    ): FreshnessAgeTier = resolveAgeTier(
        lastFetchMillis = lastSuccessfulFetchMillis(asset),
        nowMillis = nowMillis,
        isUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
    )

    fun resolveAgeTier(
        lastFetchMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        isUnavailable: Boolean = false
    ): FreshnessAgeTier {
        if (isUnavailable || lastFetchMillis <= 0L) return FreshnessAgeTier.UNAVAILABLE
        val ageMs = (nowMillis - lastFetchMillis).coerceAtLeast(0L)
        return when {
            ageMs > RED_AGE_THRESHOLD_MS -> FreshnessAgeTier.CRITICAL_RED
            ageMs > AMBER_AGE_THRESHOLD_MS -> FreshnessAgeTier.STALE_AMBER
            else -> FreshnessAgeTier.NORMAL
        }
    }

    fun formatRelativeAgeLabel(
        lastFetchMillis: Long,
        nowMillis: Long,
        strings: ChandStrings,
        usePersianDigits: Boolean,
        isUnavailable: Boolean = false
    ): String {
        if (isUnavailable || lastFetchMillis <= 0L) return strings.unavailableBadge
        val relativePart = formatCompactDuration(
            lastFetchMillis = lastFetchMillis,
            nowMillis = nowMillis,
            strings = strings,
            usePersianDigits = usePersianDigits
        )
        return "${strings.freshnessPrefix} $relativePart"
    }

    fun formatCompactDuration(
        lastFetchMillis: Long,
        nowMillis: Long,
        language: AppLanguage,
        usePersianDigits: Boolean,
        isUnavailable: Boolean = false
    ): String {
        val strings = stringsFor(language)
        if (isUnavailable || lastFetchMillis <= 0L) return strings.unavailableBadge
        return formatCompactDuration(lastFetchMillis, nowMillis, strings, usePersianDigits)
    }

    fun formatCompactDuration(
        lastFetchMillis: Long,
        nowMillis: Long,
        strings: ChandStrings,
        usePersianDigits: Boolean
    ): String {
        if (lastFetchMillis <= 0L) return strings.unavailableBadge
        val ageMs = (nowMillis - lastFetchMillis).coerceAtLeast(0L)
        val totalMinutes = ageMs / 60_000L
        return when {
            totalMinutes < 1L -> strings.freshnessJustNow
            totalMinutes < 60L -> {
                val n = NumberFormatters.applyDigits(totalMinutes.toString(), usePersianDigits)
                if (strings.freshnessMinutesAgoSuffix == "m ago") {
                    "$n${strings.freshnessMinutesAgoSuffix}"
                } else {
                    "$n ${strings.freshnessMinutesAgoSuffix}"
                }
            }
            totalMinutes < 24L * 60L -> {
                val hours = totalMinutes / 60L
                val n = NumberFormatters.applyDigits(hours.toString(), usePersianDigits)
                if (strings.freshnessHoursAgoSuffix == "h ago") {
                    "$n${strings.freshnessHoursAgoSuffix}"
                } else {
                    "$n ${strings.freshnessHoursAgoSuffix}"
                }
            }
            else -> {
                val days = totalMinutes / (24L * 60L)
                val n = NumberFormatters.applyDigits(days.toString(), usePersianDigits)
                if (strings.freshnessDaysAgoSuffix == "d ago") {
                    "$n${strings.freshnessDaysAgoSuffix}"
                } else {
                    "$n ${strings.freshnessDaysAgoSuffix}"
                }
            }
        }
    }
}
