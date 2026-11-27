package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.network.HistoryNormalizer.ChartDisplayMode
import ir.thepoo7an.chand.network.HistoryNormalizer.PreparedHistorySeries
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import kotlin.math.abs

@Composable
fun MiniSparkline(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: return@Canvas
        val maxVal = points.maxOrNull() ?: return@Canvas
        val rawSpan = maxVal - minVal
        val isFlat = rawSpan <= 0.0
        val range = if (isFlat) 1.0 else rawSpan

        val w = size.width
        val h = size.height
        val padY = h * 0.12f
        val usableH = (h - 2f * padY).coerceAtLeast(1f)
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val norm = if (isFlat) 0.5f else ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
            val y = padY + usableH * (1f - norm)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun InteractiveHistoryChart(
    series: PreparedHistorySeries,
    mode: ChartDisplayMode,
    lineColor: Color,
    gridColor: Color,
    axisLabelColor: Color,
    selectedIndex: Int?,
    isUsdPerBarrel: Boolean,
    usePersianDigits: Boolean,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = series.points
    if (points.size < 2) return

    val minVal = series.minPrice
    val maxVal = series.maxPrice
    val midVal = (minVal + maxVal) / 2.0
    val minTs = series.minTimestampSec
    val maxTs = series.maxTimestampSec

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.testTag("history_chart_${mode.name}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crisp Y-axis scale column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 48.dp, max = 72.dp)
                    .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatChartAxisPrice(maxVal, isUsdPerBarrel, usePersianDigits),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = axisLabelColor,
                    maxLines = 1
                )
                Text(
                    text = formatChartAxisPrice(midVal, isUsdPerBarrel, usePersianDigits),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = axisLabelColor.copy(alpha = 0.72f),
                    maxLines = 1
                )
                Text(
                    text = formatChartAxisPrice(minVal, isUsdPerBarrel, usePersianDigits),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = axisLabelColor,
                    maxLines = 1
                )
            }

            // Interactive timestamp-proportional Plot Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 10.dp)
                    .pointerInput(points, minTs, maxTs) {
                        if (points.size < 2) return@pointerInput
                        detectTapGestures(
                            onPress = { offset ->
                                val padPx = 8.dp.toPx()
                                val idx = findNearestTimestampIndex(
                                    touchX = offset.x,
                                    widthPx = size.width.toFloat(),
                                    horizontalPadPx = padPx,
                                    points = points,
                                    minTs = minTs,
                                    maxTs = maxTs
                                )
                                onSelectIndex(idx)
                                tryAwaitRelease()
                                onSelectIndex(null)
                            }
                        )
                    }
                    .pointerInput(points, minTs, maxTs) {
                        if (points.size < 2) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val padPx = 8.dp.toPx()
                                val idx = findNearestTimestampIndex(
                                    touchX = offset.x,
                                    widthPx = size.width.toFloat(),
                                    horizontalPadPx = padPx,
                                    points = points,
                                    minTs = minTs,
                                    maxTs = maxTs
                                )
                                onSelectIndex(idx)
                            },
                            onHorizontalDrag = { change, _ ->
                                val padPx = 8.dp.toPx()
                                val idx = findNearestTimestampIndex(
                                    touchX = change.position.x,
                                    widthPx = size.width.toFloat(),
                                    horizontalPadPx = padPx,
                                    points = points,
                                    minTs = minTs,
                                    maxTs = maxTs
                                )
                                onSelectIndex(idx)
                            },
                            onDragEnd = { onSelectIndex(null) },
                            onDragCancel = { onSelectIndex(null) }
                        )
                    }
                    .testTag("history_chart_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val padX = 8.dp.toPx()
                    val padTop = 12.dp.toPx()
                    val padBottom = 12.dp.toPx()
                    val usableW = (w - 2f * padX).coerceAtLeast(1f)
                    val usableH = (h - padTop - padBottom).coerceAtLeast(1f)

                    val rawSpan = maxVal - minVal
                    val isFlat = rawSpan <= 0.0
                    val valueRange = if (isFlat) 1.0 else rawSpan
                    val tsSpan = (maxTs - minTs).coerceAtLeast(1L).toDouble()

                    fun xFor(ts: Long, fallbackIdx: Int): Float {
                        if (maxTs <= minTs) {
                            return padX + (fallbackIdx.toFloat() / (points.size - 1).coerceAtLeast(1)) * usableW
                        }
                        val normX = ((ts - minTs).toDouble() / tsSpan).toFloat().coerceIn(0f, 1f)
                        return padX + normX * usableW
                    }

                    fun yFor(value: Double): Float {
                        val normY = if (isFlat) 0.5f else ((value - minVal) / valueRange).toFloat().coerceIn(0f, 1f)
                        return padTop + usableH * (1f - normY)
                    }

                    // Horizontal gridlines aligned with top / mid / bottom Y-axis labels
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { ratio ->
                        val gy = padTop + usableH * ratio
                        drawLine(
                            color = gridColor.copy(alpha = 0.55f),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // Opening-price reference guide
                    val openY = yFor(series.openPrice)
                    drawLine(
                        color = lineColor.copy(alpha = 0.32f),
                        start = Offset(0f, openY),
                        end = Offset(w, openY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )

                    val coords = ArrayList<Offset>(points.size)
                    val indexByTs = HashMap<Long, Int>(points.size)
                    points.forEachIndexed { idx, pt ->
                        val c = Offset(xFor(pt.timestampSec, idx), yFor(pt.priceToman))
                        coords.add(c)
                        indexByTs[pt.timestampSec] = idx
                    }

                    when (mode) {
                        ChartDisplayMode.LINE -> {
                            val gapDash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                            val segments = series.segments
                            segments.forEachIndexed { segIdx, segment ->
                                if (segment.size >= 2) {
                                    val linePath = Path()
                                    val fillPath = Path()
                                    val bottomY = padTop + usableH
                                    segment.forEachIndexed { sIdx, pt ->
                                        val globalIdx = indexByTs[pt.timestampSec] ?: 0
                                        val p = coords[globalIdx]
                                        if (sIdx == 0) {
                                            linePath.moveTo(p.x, p.y)
                                            fillPath.moveTo(p.x, bottomY)
                                            fillPath.lineTo(p.x, p.y)
                                        } else {
                                            linePath.lineTo(p.x, p.y)
                                            fillPath.lineTo(p.x, p.y)
                                        }
                                        if (sIdx == segment.lastIndex) {
                                            fillPath.lineTo(p.x, bottomY)
                                            fillPath.close()
                                        }
                                    }

                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.0f)),
                                            startY = padTop,
                                            endY = bottomY
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = lineColor,
                                        style = Stroke(
                                            width = 2.4.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                } else if (segment.size == 1) {
                                    val globalIdx = indexByTs[segment.first().timestampSec] ?: 0
                                    val isolated = coords[globalIdx]
                                    drawCircle(
                                        color = lineColor,
                                        radius = 3.5.dp.toPx(),
                                        center = isolated
                                    )
                                }

                                // Subtle dashed gap indicator between disconnected segments
                                if (segIdx < segments.lastIndex && segment.isNotEmpty() && segments[segIdx + 1].isNotEmpty()) {
                                    val endIdx = indexByTs[segment.last().timestampSec] ?: 0
                                    val nextIdx = indexByTs[segments[segIdx + 1].first().timestampSec] ?: 0
                                    drawLine(
                                        color = axisLabelColor.copy(alpha = 0.35f),
                                        start = coords[endIdx],
                                        end = coords[nextIdx],
                                        strokeWidth = 1.2.dp.toPx(),
                                        pathEffect = gapDash
                                    )
                                }
                            }
                        }

                        ChartDisplayMode.BAR -> {
                            val bottomY = padTop + usableH
                            val minSpacingPx = if (coords.size >= 2) {
                                var minGap = usableW
                                for (i in 1 until coords.size) {
                                    val d = coords[i].x - coords[i - 1].x
                                    if (d > 0.5f && d < minGap) minGap = d
                                }
                                minGap
                            } else {
                                usableW * 0.4f
                            }
                            val barWidth = (minSpacingPx * 0.68f)
                                .coerceIn(3.dp.toPx(), 18.dp.toPx())
                            val minBarHeight = 4.dp.toPx()
                            val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                            coords.forEachIndexed { idx, pt ->
                                val barHeight = (bottomY - pt.y).coerceAtLeast(minBarHeight)
                                val topY = (bottomY - barHeight).coerceAtLeast(padTop)
                                val isActive = selectedIndex == idx
                                val barAlpha = when {
                                    selectedIndex == null -> 0.80f
                                    isActive -> 1.0f
                                    else -> 0.40f
                                }
                                drawRoundRect(
                                    color = lineColor.copy(alpha = barAlpha),
                                    topLeft = Offset((pt.x - barWidth / 2f).coerceIn(0f, (w - barWidth).coerceAtLeast(0f)), topY),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }

                    val activeIdx = selectedIndex?.coerceIn(0, coords.lastIndex)
                    if (activeIdx != null) {
                        val pt = coords[activeIdx]
                        drawLine(
                            color = lineColor.copy(alpha = 0.65f),
                            start = Offset(pt.x, padTop),
                            end = Offset(pt.x, padTop + usableH),
                            strokeWidth = 1.2.dp.toPx()
                        )
                        drawCircle(
                            color = lineColor.copy(alpha = 0.22f),
                            radius = 11.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 4.5.dp.toPx(),
                            center = pt
                        )
                    } else if (mode == ChartDisplayMode.LINE) {
                        val lastPt = coords.last()
                        drawCircle(
                            color = lineColor.copy(alpha = 0.25f),
                            radius = 7.dp.toPx(),
                            center = lastPt
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 3.8.dp.toPx(),
                            center = lastPt
                        )
                    }
                }
            }
        }
    }
}

internal fun findNearestTimestampIndex(
    touchX: Float,
    widthPx: Float,
    horizontalPadPx: Float,
    points: List<AssetHistoryPoint>,
    minTs: Long,
    maxTs: Long
): Int {
    if (points.isEmpty()) return 0
    if (points.size == 1) return 0
    val usableW = (widthPx - 2f * horizontalPadPx).coerceAtLeast(1f)
    val clampedRatio = ((touchX - horizontalPadPx) / usableW).coerceIn(0f, 1f)
    if (maxTs <= minTs) {
        return (clampedRatio * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)
    }
    val targetTs = minTs + clampedRatio.toDouble() * (maxTs - minTs).toDouble()
    var bestIdx = 0
    var bestDist = Double.MAX_VALUE
    points.forEachIndexed { idx, pt ->
        val dist = abs(pt.timestampSec.toDouble() - targetTs)
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
    }
    return bestIdx
}

internal fun formatChartAxisPrice(
    price: Double,
    isUsdPerBarrel: Boolean,
    usePersianDigits: Boolean
): String {
    if (!price.isFinite() || price <= 0.0) return "—"
    return if (isUsdPerBarrel) {
        NumberFormatters.formatUsd(price, usePersianDigits)
    } else {
        NumberFormatters.formatCardPrice(price, usePersianDigits)
    }
}
