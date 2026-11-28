package ir.thepoo7an.chand.widget

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.components.FreshnessAgeTier
import ir.thepoo7an.chand.ui.components.FreshnessTimeFormatter
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.stringsFor
import kotlin.math.abs

/**
 * What the 1x1/2x2 hero widget actually renders: exactly two short rows.
 *
 * The widget had the same contradiction the app cards had, in a smaller space: `USD • DELAYED • 10:59`
 * plus a `• 0.0%` delta plus a trailing `↓`, all of which overflowed a 1x1 cell. The rules here are
 * deliberately stricter than the app's, because the surface is tiny:
 *
 * - The name row shows the **symbol** (`USD`), never the full name (`US Dollar`), and the line under
 *   it never repeats the symbol: it is the fetch time (`10:59`) or the stale marker, nothing more.
 * - A stale quote gets a single `stale` marker instead of a timestamp and a status word; the clock is
 *   only shown when it belongs to a real, fresh fetch.
 * - There is no delta at all unless a real, non-zero change exists — no `0.0%`, no arrow, no `—`.
 *   The arrow is dropped even then: on a widget it reads as a direction the data cannot prove.
 *
 * Every string is length-bounded by construction, so the two rows fit a 1x1 cell at fontScale 1.0–1.3
 * (see `WidgetHeroPresentationTest`).
 */
data class WidgetHeroModel(
    /** Short label for row 1: the symbol, at most [MAX_ASSET_LABEL_LENGTH] characters. */
    val assetLabel: String,
    /**
     * Second text line: the fetch time (`10:59`) when the quote is fresh, the stale marker otherwise.
     * Never a status word, a symbol or an arrow, and at most [MAX_STATUS_LINE_LENGTH] characters.
     */
    val statusLine: String,
    /** Price for row 2, or an em dash when the quote is unusable. */
    val priceText: String,
    /** Percent change for row 2, or `null` when there is nothing honest to show. */
    val deltaText: String?,
    /** True when neither the quote nor its timestamp can be trusted as fresh. */
    val isStale: Boolean
) {
    /** Row 2 is price plus an optional percent; the widget never renders a third text row. */
    val hasSecondRow: Boolean
        get() = priceText.isNotBlank()

    companion object {
        const val MAX_ASSET_LABEL_LENGTH = 6
        const val MAX_STATUS_LINE_LENGTH = 8
        const val MAX_DELTA_LENGTH = 7
        const val MAX_PRICE_LENGTH = 12
    }
}

object WidgetHeroPresentation {

    fun build(
        asset: MarketAsset,
        prefs: AppPreferences,
        snapshotFreshness: DataFreshness,
        updatedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): WidgetHeroModel {
        val strings = stringsFor(prefs.language)
        val displayed = listOf(asset)
        val effectiveFreshness = WidgetStatusHelper.resolveFreshness(
            displayedAssets = displayed,
            snapshotFreshness = snapshotFreshness,
            updatedAtMillis = updatedAtMillis,
            nowMillis = nowMillis
        )
        val ageTier = WidgetStatusHelper.resolveFreshnessAgeTier(
            displayedAssets = displayed,
            updatedAtMillis = updatedAtMillis,
            nowMillis = nowMillis
        )
        val isUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
        val isStale = isUnavailable ||
            effectiveFreshness != DataFreshness.LIVE ||
            ageTier != FreshnessAgeTier.NORMAL

        val assetLabel = asset.symbol.trim().ifEmpty {
            if (prefs.language == AppLanguage.FA) asset.nameFa else asset.nameEn
        }

        val fetchMillis = FreshnessTimeFormatter.lastSuccessfulFetchMillis(asset)
            .takeIf { it > 0L }
            ?: updatedAtMillis
        val clock = NumberFormatters.formatTime(fetchMillis, prefs.usePersianDigits)
        val statusLine = when {
            isStale -> strings.staleShortLabel
            clock.isBlank() -> strings.staleShortLabel
            else -> clock
        }

        val priceText = when {
            isUnavailable -> "—"
            asset.isUsdPerBarrel -> NumberFormatters.formatUsd(asset.priceUsd ?: 0.0, prefs.usePersianDigits)
            else -> NumberFormatters.formatCardPrice(asset.priceToman, prefs.usePersianDigits)
        }

        return WidgetHeroModel(
            assetLabel = assetLabel,
            statusLine = statusLine,
            priceText = priceText,
            deltaText = deltaText(asset, prefs, effectiveFreshness, isStale),
            isStale = isStale
        )
    }

    /**
     * The percent-only change, or `null` when the widget must not show a delta at all: unavailable
     * quote, stale data, a missing 24h change, or a change that is exactly zero. `0.0%` is never a
     * thing the widget is allowed to print.
     */
    internal fun deltaText(
        asset: MarketAsset,
        prefs: AppPreferences,
        effectiveFreshness: DataFreshness,
        isStale: Boolean
    ): String? {
        if (isStale) return null
        if (effectiveFreshness != DataFreshness.LIVE) return null
        if (asset.freshness == DataFreshness.DELAYED || asset.freshness == DataFreshness.CACHED) return null
        if (asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice) return null
        if (asset.isUsdPerBarrel && !asset.isChange24hAvailable) return null

        val absoluteChange = if (asset.isUsdPerBarrel) asset.change24hUsd ?: 0.0 else asset.change24hToman
        val hasRealChange = absoluteChange != 0.0 || asset.change24hPercent != 0.0
        if (!hasRealChange) return null
        if (abs(asset.change24hPercent) < 0.05) return null
        return NumberFormatters.applyDigits(
            NumberFormatters.formatSignedPercent(asset.change24hPercent),
            prefs.usePersianDigits
        )
    }
}
