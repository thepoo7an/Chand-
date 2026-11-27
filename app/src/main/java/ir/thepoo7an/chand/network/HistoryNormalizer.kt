package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.TimeRange
import kotlin.math.abs

/**
 * Shared history normalization pipeline applied uniformly across all remote and cached series
 * (TGJU, Wallex, Nobitex, Binance, CoinGecko, Frankfurter, CurrencyAPI):
 * 1. Filters invalid points (non-positive timestamps, non-positive or non-finite prices).
 * 2. Deduplicates timestamps deterministically (latest valid entry wins) and sorts ascending.
 * 3. Removes isolated single-tick reverting spikes (> [spikeThresholdPct]% jump that immediately reverts),
 *    unless corroborated by another provider within [toleranceSec] and half the threshold.
 *    Sustained jumps/shocks (where the subsequent observation holds the new price level) are never
 *    classified as reverting spikes and are preserved.
 *
 * Calibrated spike thresholds by category and range (see [MarketValidationConfig.spikeThresholdPct]):
 * - `24H` (all categories): `15%`
 * - `FIAT` (`7D` / `1M` / `1Y`): `15%` (`7D`/`1M`), `25%` (`1Y`)
 * - `GOLD` (`7D` / `1M` / `1Y`): `15%` (`7D`/`1M`), `25%` (`1Y`)
 * - `OIL` (`7D` / `1M` / `1Y`): `20%` (`7D`/`1M`), `35%` (`1Y`)
 * - `CRYPTO` (`7D` / `1M` / `1Y`): `40%`
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

    enum class ChartDisplayMode {
        LINE,
        BAR
    }

    data class ChartSeriesGap(
        val afterTimestampSec: Long,
        val beforeTimestampSec: Long
    ) {
        val durationSec: Long
            get() = (beforeTimestampSec - afterTimestampSec).coerceAtLeast(0L)
    }

    data class PreparedHistorySeries(
        val points: List<AssetHistoryPoint>,
        val segments: List<List<AssetHistoryPoint>>,
        val gaps: List<ChartSeriesGap>,
        val minTimestampSec: Long,
        val maxTimestampSec: Long,
        val minPrice: Double,
        val maxPrice: Double,
        val openPrice: Double,
        val closePrice: Double,
        val periodDeltaPrice: Double,
        val periodDeltaPercent: Double
    ) {
        val observationCount: Int
            get() = points.size

        val hasSufficientHistory: Boolean
            get() = points.size >= 2

        val hasGaps: Boolean
            get() = gaps.isNotEmpty()
    }

    /**
     * Filters points strictly to `[nowSec - range.lookbackSeconds - edgeGraceSec, nowSec + 300L]`
     * so long-range responses don't bleed old observations into narrower windows.
     */
    fun filterToRange(
        points: List<AssetHistoryPoint>,
        range: TimeRange,
        nowSec: Long
    ): List<AssetHistoryPoint> {
        if (points.isEmpty() || nowSec <= 0L) return points
        val edgeGraceSec = when (range) {
            TimeRange.H24 -> 3_600L
            TimeRange.D7, TimeRange.M1, TimeRange.Y1 -> 43_200L
        }
        val minAllowedSec = (nowSec - range.lookbackSeconds - edgeGraceSec).coerceAtLeast(1L)
        val maxAllowedSec = (nowSec + 300L).coerceAtMost(MAX_REASONABLE_TIMESTAMP_SEC)
        return points.filter { it.timestampSec in minAllowedSec..maxAllowedSec }
    }

    /**
     * Computes the maximum permissible spacing between consecutive observations before
     * splitting a historical chart line across an outage or missing data interval.
     * Requires at least 5 observations so sparse series do not produce false gap splits.
     */
    fun gapThresholdSec(
        range: TimeRange,
        sortedPoints: List<AssetHistoryPoint>
    ): Long {
        if (sortedPoints.size < 5) return Long.MAX_VALUE
        val diffs = ArrayList<Long>(sortedPoints.size - 1)
        for (i in 1 until sortedPoints.size) {
            val d = sortedPoints[i].timestampSec - sortedPoints[i - 1].timestampSec
            if (d > 0L) diffs.add(d)
        }
        if (diffs.isEmpty()) return Long.MAX_VALUE
        diffs.sort()
        val medianStepSec = diffs[diffs.size / 2]

        return when (range) {
            TimeRange.H24 -> if (medianStepSec <= 6L * 3_600L) 4L * 3_600L else medianStepSec * 2L + 3_600L
            TimeRange.D7 -> if (medianStepSec <= 8L * 3_600L) 16L * 3_600L else 4L * 86_400L
            TimeRange.M1 -> maxOf(4L * 86_400L, medianStepSec * 3L)
            TimeRange.Y1 -> maxOf(14L * 86_400L, medianStepSec * 4L)
        }
    }

    /**
     * Prepares a chart-ready series from raw points:
     * - Filters invalid timestamps/prices, deduplicates timestamps, sorts chronologically.
     * - Optionally clips to the target [TimeRange] window ending at [nowSec].
     * - Segments points across real data gaps so lines never silently bridge large outages.
     * - Computes honest range bounds and period open->close deltas without fabricating points.
     */
    fun prepareChartSeries(
        points: List<AssetHistoryPoint>,
        range: TimeRange,
        category: AssetCategory? = null,
        nowSec: Long? = null,
        applyRangeCutoff: Boolean = false,
        removeSpikes: Boolean = false
    ): PreparedHistorySeries {
        val maxTs = nowSec?.let { it + 300L }
        val cleaned = if (removeSpikes && category != null) {
            normalize(
                points = points,
                category = category,
                range = range,
                maxTimestampSec = maxTs
            )
        } else {
            filterValidSorted(points, maxTimestampSec = maxTs)
        }
        val ranged = if (applyRangeCutoff && nowSec != null) {
            filterToRange(cleaned, range, nowSec)
        } else {
            cleaned
        }

        if (ranged.isEmpty()) {
            return PreparedHistorySeries(
                points = emptyList(),
                segments = emptyList(),
                gaps = emptyList(),
                minTimestampSec = 0L,
                maxTimestampSec = 0L,
                minPrice = 0.0,
                maxPrice = 0.0,
                openPrice = 0.0,
                closePrice = 0.0,
                periodDeltaPrice = 0.0,
                periodDeltaPercent = 0.0
            )
        }

        val thresholdSec = gapThresholdSec(range, ranged)
        val segments = mutableListOf<List<AssetHistoryPoint>>()
        val gaps = mutableListOf<ChartSeriesGap>()
        var currentSegment = mutableListOf(ranged.first())

        for (i in 1 until ranged.size) {
            val prev = ranged[i - 1]
            val curr = ranged[i]
            val delta = curr.timestampSec - prev.timestampSec
            if (delta > thresholdSec) {
                segments.add(currentSegment)
                gaps.add(ChartSeriesGap(afterTimestampSec = prev.timestampSec, beforeTimestampSec = curr.timestampSec))
                currentSegment = mutableListOf(curr)
            } else {
                currentSegment.add(curr)
            }
        }
        segments.add(currentSegment)

        val minPrice = ranged.minOf { it.priceToman }
        val maxPrice = ranged.maxOf { it.priceToman }
        val openPrice = ranged.first().priceToman
        val closePrice = ranged.last().priceToman
        val deltaPrice = if (ranged.size >= 2) closePrice - openPrice else 0.0
        val deltaPct = if (ranged.size >= 2 && openPrice > 0.0) (deltaPrice / openPrice) * 100.0 else 0.0

        return PreparedHistorySeries(
            points = ranged,
            segments = segments,
            gaps = gaps,
            minTimestampSec = ranged.first().timestampSec,
            maxTimestampSec = ranged.last().timestampSec,
            minPrice = minPrice,
            maxPrice = maxPrice,
            openPrice = openPrice,
            closePrice = closePrice,
            periodDeltaPrice = deltaPrice,
            periodDeltaPercent = deltaPct
        )
    }
}
