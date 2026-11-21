package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AssetHistoryPoint
import kotlin.math.roundToInt

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
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: (maxVal * 0.01).coerceAtLeast(1.0)

        val w = size.width
        val h = size.height
        val padY = h * 0.12f
        val usableH = (h - 2f * padY).coerceAtLeast(1f)
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val norm = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
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
                colors = listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.0f)),
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
    points: List<AssetHistoryPoint>,
    lineColor: Color,
    gridColor: Color,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(points) {
                if (points.size < 2) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val idx = ((offset.x / size.width.coerceAtLeast(1)) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onSelectIndex(idx)
                        tryAwaitRelease()
                        onSelectIndex(null)
                    }
                )
            }
            .pointerInput(points) {
                if (points.size < 2) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val idx = ((offset.x / size.width.coerceAtLeast(1)) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onSelectIndex(idx)
                    },
                    onHorizontalDrag = { change, _ ->
                        val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onSelectIndex(idx)
                    },
                    onDragEnd = { onSelectIndex(null) },
                    onDragCancel = { onSelectIndex(null) }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas

            val prices = points.map { it.priceToman }
            val minVal = prices.minOrNull() ?: return@Canvas
            val maxVal = prices.maxOrNull() ?: return@Canvas
            val range = (maxVal - minVal).takeIf { it > 0.0 } ?: (maxVal * 0.01).coerceAtLeast(1.0)

            val w = size.width
            val h = size.height
            val padTop = 12.dp.toPx()
            val padBottom = 12.dp.toPx()
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
            val stepX = w / (points.size - 1).coerceAtLeast(1)

            // Subtle horizontal reference lines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                val gy = padTop + usableH * ratio
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            // Baseline at opening price
            val openNorm = ((prices.first() - minVal) / range).toFloat().coerceIn(0f, 1f)
            val openY = padTop + usableH * (1f - openNorm)
            drawLine(
                color = lineColor.copy(alpha = 0.35f),
                start = Offset(0f, openY),
                end = Offset(w, openY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            val linePath = Path()
            val fillPath = Path()
            val coords = ArrayList<Offset>(points.size)

            prices.forEachIndexed { idx, value ->
                val x = idx * stepX
                val norm = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
                val y = padTop + usableH * (1f - norm)
                coords.add(Offset(x, y))
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (idx == prices.lastIndex) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f)),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val activeIdx = selectedIndex?.coerceIn(0, coords.lastIndex)
            if (activeIdx != null) {
                val pt = coords[activeIdx]
                drawLine(
                    color = lineColor.copy(alpha = 0.6f),
                    start = Offset(pt.x, 0f),
                    end = Offset(pt.x, h),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(
                    color = lineColor.copy(alpha = 0.22f),
                    radius = 12.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = pt
                )
            } else {
                val lastPt = coords.last()
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = 8.dp.toPx(),
                    center = lastPt
                )
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = lastPt
                )
            }
        }
    }
}
