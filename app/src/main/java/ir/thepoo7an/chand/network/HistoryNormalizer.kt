package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.TimeRange
import kotlin.math.abs

/**
 * Shared history normalization pipeline applied uniformly across all remote and cached series
 * (TGJU, Wallex, Nobitex, Binance, CoinGecko, Frankfurter, CurrencyAPI):
 * 1. Filters invalid points (non-positive timestamps, non-positive or non-finite prices).
 * 2. Deduplicates timestamps (latest entry wins) and sorts chronologically ascending.
 * 3. Removes isolated single-tick spikes (> [spikeThresholdPct]% jump that immediately reverts),
 *    unless corroborated by another provider within [toleranceSec] and half the threshold.
 */
object HistoryNormalizer {

    /** Upper bound (2100-01-01 UTC in seconds) to reject un-divided millisecond or corrupted future timestamps. */
    const val MAX_REASONABLE_TIMESTAMP_SEC: Long = 4_102_444_800L

    data class NormalizationResult(
        val points: List<AssetHistoryPoint>,
        val removedSpikeCount: Int,
        val invalidCount: Int = 0,
        val duplicateCount: Int = 0
    )

    @Volatile
    internal var lastRemovedSpikeCount: Int = 0
        private set

    fun normalize(
        points: List<AssetHistoryPoint>,
        spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT,
        corroborating: List<AssetHistoryPoint> = emptyList(),
        toleranceSec: Long = MarketValidationConfig.corroborationToleranceSec(null),
        maxTimestampSec: Long? = null
    ): List<AssetHistoryPoint> {
        val res = normalizeWithStats(
            points = points,
            spikeThresholdPct = spikeThresholdPct,
            corroborating = corroborating,
            toleranceSec = toleranceSec,
            maxTimestampSec = maxTimestampSec
        )
        lastRemovedSpikeCount = res.removedSpikeCount
        return res.points
    }

    fun normalize(
        points: List<AssetHistoryPoint>,
        category: AssetCategory,
        range: TimeRange,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range),
        corroborating: List<AssetHistoryPoint> = emptyList(),
        toleranceSec: Long = MarketValidationConfig.corroborationToleranceSec(range),
        maxTimestampSec: Long? = null
    ): List<AssetHistoryPoint> {
        val res = normalizeWithStats(
            points = points,
            spikeThresholdPct = spikeThresholdPct,
            corroborating = corroborating,
            toleranceSec = toleranceSec,
            maxTimestampSec = maxTimestampSec
        )
        lastRemovedSpikeCount = res.removedSpikeCount
        return res.points
    }

    fun normalizeWithStats(
        points: List<AssetHistoryPoint>,
        spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT,
        corroborating: List<AssetHistoryPoint> = emptyList(),
        toleranceSec: Long = MarketValidationConfig.corroborationToleranceSec(null),
        maxTimestampSec: Long? = null
    ): NormalizationResult {
        val (sorted, invalidCount, duplicateCount) = filterValidSortedWithCounts(points, maxTimestampSec)
        if (sorted.isEmpty()) {
            return NormalizationResult(
                points = emptyList(),
                removedSpikeCount = 0,
                invalidCount = invalidCount,
                duplicateCount = duplicateCount
            )
        }
        if (sorted.size < 3 || spikeThresholdPct <= 0.0) {
            return NormalizationResult(
                points = sorted,
                removedSpikeCount = 0,
                invalidCount = invalidCount,
                duplicateCount = duplicateCount
            )
        }

        val sortedCorroborating = filterValidSorted(corroborating, maxTimestampSec)
        val spikeFiltered = filterRevertingSpikesWithStats(
            sorted = sorted,
            thresholdPct = spikeThresholdPct,
            sortedCorroborating = sortedCorroborating,
            toleranceSec = toleranceSec
        )
        return spikeFiltered.copy(
            invalidCount = invalidCount,
            duplicateCount = duplicateCount
        )
    }

    fun normalizeWithStats(
        points: List<AssetHistoryPoint>,
        category: AssetCategory,
        range: TimeRange,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range),
        corroborating: List<AssetHistoryPoint> = emptyList(),
        toleranceSec: Long = MarketValidationConfig.corroborationToleranceSec(range),
        maxTimestampSec: Long? = null
    ): NormalizationResult = normalizeWithStats(
        points = points,
        spikeThresholdPct = spikeThresholdPct,
        corroborating = corroborating,
        toleranceSec = toleranceSec,
        maxTimestampSec = maxTimestampSec
    )

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

    fun normalizePairs(
        pairs: List<Pair<Long, Double>>,
        category: AssetCategory,
        range: TimeRange,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<Pair<Long, Double>> {
        if (pairs.isEmpty()) return emptyList()
        return normalize(
            points = pairs.map { (ts, v) -> AssetHistoryPoint(ts, v) },
            category = category,
            range = range,
            spikeThresholdPct = spikeThresholdPct
        ).map { it.timestampSec to it.priceToman }
    }

    private fun filterValidSorted(
        points: List<AssetHistoryPoint>,
        maxTimestampSec: Long? = null
    ): List<AssetHistoryPoint> = filterValidSortedWithCounts(points, maxTimestampSec).first

    private fun filterValidSortedWithCounts(
        points: List<AssetHistoryPoint>,
        maxTimestampSec: Long? = null
    ): Triple<List<AssetHistoryPoint>, Int, Int> {
        if (points.isEmpty()) return Triple(emptyList(), 0, 0)
        val effectiveMaxTs = maxTimestampSec?.coerceAtMost(MAX_REASONABLE_TIMESTAMP_SEC)
            ?: MAX_REASONABLE_TIMESTAMP_SEC
        val dedupedByTs = LinkedHashMap<Long, AssetHistoryPoint>(points.size)
        var invalidCount = 0
        var duplicateCount = 0
        for (pt in points) {
            if (pt.timestampSec !in 1L..effectiveMaxTs || !pt.priceToman.isFinite() || pt.priceToman <= 0.0) {
                invalidCount++
                continue
            }
            if (dedupedByTs.containsKey(pt.timestampSec)) {
                duplicateCount++
            }
            dedupedByTs[pt.timestampSec] = pt
        }
        if (dedupedByTs.isEmpty()) return Triple(emptyList(), invalidCount, duplicateCount)
        return Triple(dedupedByTs.values.sortedBy { it.timestampSec }, invalidCount, duplicateCount)
    }

    internal fun filterRevertingSpikes(
        sorted: List<AssetHistoryPoint>,
        thresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT,
        corroborating: List<AssetHistoryPoint> = emptyList(),
        toleranceSec: Long = MarketValidationConfig.corroborationToleranceSec(null)
    ): List<AssetHistoryPoint> = filterRevertingSpikesWithStats(
        sorted = sorted,
        thresholdPct = thresholdPct,
        sortedCorroborating = filterValidSorted(corroborating),
        toleranceSec = toleranceSec
    ).points

    private fun filterRevertingSpikesWithStats(
        sorted: List<AssetHistoryPoint>,
        thresholdPct: Double,
        sortedCorroborating: List<AssetHistoryPoint>,
        toleranceSec: Long
    ): NormalizationResult {
        if (sorted.size < 3 || thresholdPct <= 0.0) return NormalizationResult(sorted, 0)

        val result = ArrayList<AssetHistoryPoint>(sorted.size)
        result.add(sorted.first())
        var removedCount = 0

        for (i in 1 until sorted.lastIndex) {
            val prev = result.last()
            val curr = sorted[i]
            val next = sorted[i + 1]

            if (isRevertingSpike(prev.priceToman, curr.priceToman, next.priceToman, thresholdPct) &&
                !isCorroborated(curr, sortedCorroborating, thresholdPct, toleranceSec)
            ) {
                removedCount++
                continue
            }
            result.add(curr)
        }

        result.add(sorted.last())
        return NormalizationResult(result, removedCount)
    }

    private fun isCorroborated(
        curr: AssetHistoryPoint,
        sortedCorroborating: List<AssetHistoryPoint>,
        thresholdPct: Double,
        toleranceSec: Long
    ): Boolean {
        if (sortedCorroborating.isEmpty() || toleranceSec < 0L || thresholdPct <= 0.0) return false
        val halfThreshold = thresholdPct / 2.0
        val targetTs = curr.timestampSec

        val minTs = if (targetTs > Long.MIN_VALUE + toleranceSec) targetTs - toleranceSec else Long.MIN_VALUE
        val maxTs = if (targetTs < Long.MAX_VALUE - toleranceSec) targetTs + toleranceSec else Long.MAX_VALUE

        var lo = 0
        var hi = sortedCorroborating.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedCorroborating[mid].timestampSec < minTs) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }

        var idx = lo
        while (idx < sortedCorroborating.size) {
            val other = sortedCorroborating[idx]
            if (other.timestampSec > maxTs) break
            val diffPct = (abs(other.priceToman - curr.priceToman) / curr.priceToman) * 100.0
            if (diffPct <= halfThreshold) return true
            idx++
        }
        return false
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
