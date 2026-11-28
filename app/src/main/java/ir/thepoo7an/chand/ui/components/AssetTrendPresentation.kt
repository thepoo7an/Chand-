package ir.thepoo7an.chand.ui.components

import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.NumberFormatters

/**
 * Single source of truth for "should this card show a trend at all?".
 *
 * A card used to render `Updated just now` next to a `DELAYED` badge, a flat-looking sparkline and
 * a `• 0` delta chip, which reads as "the price did not move". Nothing about a missing series is a
 * zero: the trend block is simply hidden unless there is real evidence for it.
 *
 * Rules:
 * - The sparkline/delta are hidden when the quote is `UNAVAILABLE`, when the trend data is `DELAYED`,
 *   or when the series is empty/degenerate (fewer than two usable points).
 * - A delta is only shown when it is backed by data: a non-zero change, or a genuinely flat series
 *   (`first == last`), which is the only case where `0` is honest.
 */
object AssetTrendPresentation {

    /** True when the series can be drawn at all (enough points, at least one positive value). */
    fun hasUsableSeries(asset: MarketAsset): Boolean =
        asset.sparkline.size >= 2 && asset.sparkline.all { it.isFinite() } && asset.sparkline.any { it > 0.0 }

    /**
     * True when the sparkline may be drawn. Delayed history is deliberately excluded: a stale line
     * next to a fresh quote is the exact contradiction the review flagged.
     */
    fun shouldShowSparkline(asset: MarketAsset): Boolean =
        asset.freshness != DataFreshness.UNAVAILABLE &&
            asset.freshness != DataFreshness.DELAYED &&
            hasUsableSeries(asset)

    /** True when the series exists and genuinely starts and ends at the same price. */
    fun isFlatSeries(asset: MarketAsset): Boolean =
        hasUsableSeries(asset) && asset.sparkline.first() == asset.sparkline.last()

    /**
     * Delta chip text, or `null` when the chip must not be rendered at all.
     *
     * @param hasDisplayChange whether the asset carries a usable change value for its display unit
     *   (oil is quoted in USD, everything else in Toman).
     */
    fun deltaLabel(
        asset: MarketAsset,
        usePersianDigits: Boolean,
        hasDisplayChange: Boolean
    ): String? {
        if (asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice) return null
        if (asset.freshness == DataFreshness.DELAYED) return null
        if (!hasDisplayChange) return null
        val hasRealChange = if (asset.isUsdPerBarrel) {
            (asset.change24hUsd ?: 0.0) != 0.0 || asset.change24hPercent != 0.0
        } else {
            asset.change24hToman != 0.0 || asset.change24hPercent != 0.0
        }
        return when {
            hasRealChange -> if (asset.isUsdPerBarrel) {
                NumberFormatters.formatUsdDeltaWithArrow(
                    changeUsd = asset.change24hUsd,
                    changePct = asset.change24hPercent,
                    usePersianDigits = usePersianDigits
                )
            } else {
                NumberFormatters.formatDeltaWithArrow(
                    changeToman = asset.change24hToman,
                    changePct = asset.change24hPercent,
                    usePersianDigits = usePersianDigits
                )
            }
            isFlatSeries(asset) -> NumberFormatters.formatDeltaWithArrow(
                changeToman = 0.0,
                changePct = 0.0,
                usePersianDigits = usePersianDigits
            )
            // No change value and no flat series: a `0` here would be an invented number.
            else -> null
        }
    }
}
