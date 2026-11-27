package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import kotlin.math.abs

data class PriceComparisonSummary(
    val assetId: String,
    val primarySource: String,
    val secondarySource: String,
    val primaryPriceToman: Double,
    val secondaryPriceToman: Double,
    val deltaToman: Double,
    val deltaPct: Double,
    val isDivergent: Boolean
)

/**
 * Computes subtle multi-source spread comparisons from an asset's retained [SourceCandidateRecord]s
 * (e.g., Wallex vs TGJU on USD, Bitpin vs Nobitex on USDT, EUR/GOLD_18K cross checks).
 */
object MarketComparisonHelper {

    const val DIVERGENCE_THRESHOLD_PCT: Double = 0.5

    fun buildComparison(
        asset: MarketAsset,
        enabledAssetIds: Set<String> = AppPreferences.DEFAULT_COMPARISON_ASSET_IDS
    ): PriceComparisonSummary? {
        if (asset.id !in enabledAssetIds) return null
        if (asset.isUsdPerBarrel || asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice) {
            return null
        }

        val priced = asset.candidates
            .filter { cand ->
                val price = cand.priceToman
                price != null && price.isFinite() && price > 0.0
            }
            .distinctBy { it.sourceName }

        if (priced.size < 2) return null

        val primary: SourceCandidateRecord
        val secondary: SourceCandidateRecord

        when (asset.id) {
            "USD" -> {
                val tgju = priced.firstOrNull { it.sourceName.equals("TGJU", ignoreCase = true) }
                val wallex = priced.firstOrNull { it.sourceName.equals("Wallex", ignoreCase = true) }
                if (tgju != null && wallex != null) {
                    primary = tgju
                    secondary = wallex
                } else {
                    primary = priced.firstOrNull { it.isPrimary } ?: priced.first()
                    secondary = priced.firstOrNull { it.sourceName != primary.sourceName } ?: return null
                }
            }
            "USDT" -> {
                val bitpin = priced.firstOrNull { it.sourceName.equals("Bitpin", ignoreCase = true) }
                val nobitex = priced.firstOrNull { it.sourceName.equals("Nobitex", ignoreCase = true) }
                val wallex = priced.firstOrNull { it.sourceName.equals("Wallex", ignoreCase = true) }
                when {
                    bitpin != null && nobitex != null -> {
                        primary = bitpin
                        secondary = nobitex
                    }
                    wallex != null && bitpin != null -> {
                        primary = wallex
                        secondary = bitpin
                    }
                    else -> {
                        primary = priced.firstOrNull { it.isPrimary } ?: priced.first()
                        secondary = priced.firstOrNull { it.sourceName != primary.sourceName } ?: return null
                    }
                }
            }
            else -> {
                primary = priced.firstOrNull { it.isPrimary } ?: priced.first()
                secondary = priced.firstOrNull { it.sourceName != primary.sourceName } ?: return null
            }
        }

        val primaryPrice = primary.priceToman ?: return null
        val secondaryPrice = secondary.priceToman ?: return null
        if (primaryPrice <= 0.0 || secondaryPrice <= 0.0) return null

        val deltaToman = secondaryPrice - primaryPrice
        val deltaPct = (abs(deltaToman) / primaryPrice) * 100.0
        val isDivergent = deltaPct > DIVERGENCE_THRESHOLD_PCT

        return PriceComparisonSummary(
            assetId = asset.id,
            primarySource = shortProviderName(primary.sourceName),
            secondarySource = shortProviderName(secondary.sourceName),
            primaryPriceToman = primaryPrice,
            secondaryPriceToman = secondaryPrice,
            deltaToman = deltaToman,
            deltaPct = deltaPct,
            isDivergent = isDivergent
        )
    }

    fun formatComparisonBadge(
        summary: PriceComparisonSummary,
        isFa: Boolean,
        usePersianDigits: Boolean
    ): String {
        val absDelta = abs(summary.deltaToman)
        if (absDelta < 1.0) {
            return if (isFa) {
                "${summary.secondarySource} = ${summary.primarySource}"
            } else {
                "${summary.secondarySource} = ${summary.primarySource}"
            }
        }
        val sign = if (summary.deltaToman > 0.0) "+" else "-"
        val formattedDelta = NumberFormatters.formatToman(absDelta, usePersianDigits)
        return if (isFa) {
            "${summary.secondarySource} $sign$formattedDelta نسبت به ${summary.primarySource}"
        } else {
            "${summary.secondarySource} $sign$formattedDelta TMN vs ${summary.primarySource}"
        }
    }

    private fun shortProviderName(raw: String): String = when {
        raw.contains(" + USD") -> raw.replace(" + USD", "")
        else -> raw
    }
}
