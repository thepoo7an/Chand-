package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetHistoryPoint
import kotlin.math.abs

/**
 * Shared history normalization pipeline applied uniformly across all remote and cached series
 * (TGJU, Wallex, Nobitex, Binance, CoinGecko, Frankfurter, CurrencyAPI):
 * 1. Filters invalid points (non-positive timestamps, non-positive or non-finite prices).
 * 2. Deduplicates timestamps (latest entry wins) and sorts chronologically ascending.
 * 3. Removes isolated single-tick spikes (> [spikeThresholdPct]% jump that immediately reverts).
 */
object HistoryNormalizer {

    fun normalize(
        points: List<AssetHistoryPoint>,
        spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
    ): List<AssetHistoryPoint> {
        if (points.isEmpty()) return emptyList()

        val dedupedByTs = LinkedHashMap<Long, AssetHistoryPoint>(points.size)
        for (pt in points) {
            if (pt.timestampSec > 0L && pt.priceToman.isFinite() && pt.priceToman > 0.0) {
                dedupedByTs[pt.timestampSec] = pt
            }
        }
        if (dedupedByTs.isEmpty()) return emptyList()

        val sorted = dedupedByTs.values.sortedBy { it.timestampSec }
        if (sorted.size < 3) return sorted

        return filterRevertingSpikes(sorted, spikeThresholdPct)
    }

    fun normalizePairs(
        pairs: List<Pair<Long, Double>>,
        spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
    ): List<Pair<Long, Double>> {
        if (pairs.isEmpty()) return emptyList()
        return normalize(
            points = pairs.map { (ts, v) -> AssetHistoryPoint(ts, v) },
            spikeThresholdPct = spikeThresholdPct
        ).map { it.timestampSec to it.priceToman }
    }

    internal fun filterRevertingSpikes(
        sorted: List<AssetHistoryPoint>,
        thresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
    ): List<AssetHistoryPoint> {
        if (sorted.size < 3 || thresholdPct <= 0.0) return sorted

        val result = ArrayList<AssetHistoryPoint>(sorted.size)
        result.add(sorted.first())

        for (i in 1 until sorted.lastIndex) {
            val prev = result.last()
            val curr = sorted[i]
            val next = sorted[i + 1]

            if (isRevertingSpike(prev.priceToman, curr.priceToman, next.priceToman, thresholdPct)) {
                continue
            }
            result.add(curr)
        }

        result.add(sorted.last())
        return result
    }

    internal fun isRevertingSpike(
        prevPrice: Double,
        currPrice: Double,
        nextPrice: Double,
        thresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
    ): Boolean {
        if (prevPrice <= 0.0 || currPrice <= 0.0 || nextPrice <= 0.0) return false

        val deltaPrev = currPrice - prevPrice
        val deltaNext = nextPrice - currPrice

        // Must reverse direction (up-then-down or down-then-up)
        if (deltaPrev * deltaNext >= 0.0) return false

        val jumpFromPrevPct = (abs(deltaPrev) / prevPrice) * 100.0
        val revertToNextPct = (abs(deltaNext) / nextPrice) * 100.0
        if (jumpFromPrevPct <= thresholdPct || revertToNextPct <= thresholdPct) return false

        // Confirm next actually reverted near prev rather than forming a sustained trend
        val endToEndDrift = abs(nextPrice - prevPrice)
        val endToEndPct = (endToEndDrift / prevPrice) * 100.0
        return endToEndPct <= thresholdPct || endToEndDrift < abs(deltaPrev) * 0.5
    }
}
