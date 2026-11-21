package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders crisp circular vector flags, metallic gold coin emblems, and crypto medallions
 * inspired by the Chand?! iOS aesthetic.
 */
@Composable
fun AssetBadge(
    assetId: String,
    category: AssetCategory,
    fallbackText: String,
    accentHex: Long,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    when {
        category == AssetCategory.GOLD -> {
            GoldCoinBadge(assetId = assetId, size = size, modifier = modifier)
        }
        category == AssetCategory.CRYPTO -> {
            CryptoMedallionBadge(
                assetId = assetId,
                fallbackText = fallbackText,
                accentColor = Color(accentHex),
                size = size,
                modifier = modifier
            )
        }
        else -> {
            CircularFlagBadge(
                assetId = assetId,
                fallbackText = fallbackText,
                size = size,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun GoldCoinBadge(
    assetId: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        val r = this.size.minDimension / 2f
        val center = Offset(r, r)

        // Outer metallic gold gradient
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFEA61), Color(0xFFF5B700), Color(0xFFD97706)),
                start = Offset(0f, 0f),
                end = Offset(this.size.width, this.size.height)
            ),
            radius = r,
            center = center
        )

        // Inner stamped ring
        drawCircle(
            color = Color(0xFFB45309).copy(alpha = 0.35f),
            radius = r * 0.72f,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        if (assetId == "GOLD_18K" || assetId == "GOLD_24K" || assetId == "GOLD_MESGHAL" || assetId == "XAU_OZ") {
            // Geometric gold ingot / diamond motif
            val path = Path().apply {
                moveTo(center.x - r * 0.34f, center.y + r * 0.22f)
                lineTo(center.x - r * 0.20f, center.y - r * 0.22f)
                lineTo(center.x + r * 0.20f, center.y - r * 0.22f)
                lineTo(center.x + r * 0.34f, center.y + r * 0.22f)
                close()
            }
            drawPath(path, color = Color(0xFF92400E).copy(alpha = 0.45f))
        } else {
            // Azadi / Emami coin hexagon emblem
            val hexPath = Path()
            val hexR = r * 0.44f
            for (i in 0 until 6) {
                val angle = Math.toRadians((60 * i - 30).toDouble())
                val x = center.x + (hexR * cos(angle)).toFloat()
                val y = center.y + (hexR * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                color = Color(0xFF92400E).copy(alpha = 0.35f),
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFFFF7CC).copy(alpha = 0.7f),
                radius = r * 0.14f,
                center = center
            )
        }
    }
}

@Composable
private fun CryptoMedallionBadge(
    assetId: String,
    fallbackText: String,
    accentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val glyph = when (assetId) {
        "BTC" -> "₿"
        "ETH" -> "Ξ"
        "USDT" -> "₮"
        "TON" -> "◆"
        "SOL" -> "◎"
        "BNB" -> "◈"
        "XRP" -> "✕"
        "DOGE" -> "Ð"
        else -> fallbackText
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accentColor, accentColor.copy(alpha = 0.78f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = Color.White,
            fontSize = (size.value * 0.48f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CircularFlagBadge(
    assetId: String,
    fallbackText: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val supportedVector = assetId in setOf(
        "USD", "EUR", "AED", "GBP", "KWD", "TRY", "CAD", "CHF", "CNY", "JPY", "SAR", "IQD", "IRT"
    )
    if (!supportedVector) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = fallbackText, fontSize = (size.value * 0.54f).sp)
        }
        return
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        val w = this.size.width
        val h = this.size.height
        when (assetId) {
            "USD" -> drawUsFlag(w, h)
            "EUR" -> drawEuFlag(w, h)
            "AED" -> drawAedFlag(w, h)
            "GBP" -> drawUkFlag(w, h)
            "KWD" -> drawKwdFlag(w, h)
            "TRY" -> drawTryFlag(w, h)
            "CAD" -> drawCadFlag(w, h)
            "CHF" -> drawChfFlag(w, h)
            "CNY" -> drawCnyFlag(w, h)
            "JPY" -> drawJpyFlag(w, h)
            "SAR" -> drawSarFlag(w, h)
            "IQD" -> drawIqdFlag(w, h)
            "IRT" -> drawIranFlag(w, h)
        }
        // Subtle inner rim for crisp separation on white cards
        drawCircle(
            color = Color.Black.copy(alpha = 0.08f),
            radius = minOf(w, h) / 2f,
            style = Stroke(width = 0.8.dp.toPx())
        )
    }
}

private fun DrawScope.drawUsFlag(w: Float, h: Float) {
    drawRect(Color.White)
    val stripeH = h / 13f
    for (i in 0 until 13 step 2) {
        drawRect(
            color = Color(0xFFB91C1C),
            topLeft = Offset(0f, i * stripeH),
            size = Size(w, stripeH)
        )
    }
    drawRect(
        color = Color(0xFF1E3A8A),
        topLeft = Offset(0f, 0f),
        size = Size(w * 0.52f, stripeH * 7f)
    )
    for (row in 1..3) {
        for (col in 1..4) {
            drawCircle(
                color = Color.White,
                radius = w * 0.025f,
                center = Offset(col * w * 0.105f, row * h * 0.125f)
            )
        }
    }
}

private fun DrawScope.drawEuFlag(w: Float, h: Float) {
    drawRect(Color(0xFF003399))
    val cx = w / 2f
    val cy = h / 2f
    val ringR = w * 0.30f
    for (i in 0 until 12) {
        val angle = Math.toRadians((i * 30).toDouble())
        drawCircle(
            color = Color(0xFFFFCC00),
            radius = w * 0.045f,
            center = Offset(cx + (ringR * cos(angle)).toFloat(), cy + (ringR * sin(angle)).toFloat())
        )
    }
}

private fun DrawScope.drawAedFlag(w: Float, h: Float) {
    drawRect(Color(0xFF00732F), Offset(0f, 0f), Size(w, h / 3f))
    drawRect(Color.White, Offset(0f, h / 3f), Size(w, h / 3f))
    drawRect(Color(0xFF111111), Offset(0f, 2f * h / 3f), Size(w, h / 3f))
    drawRect(Color(0xFFCE1126), Offset(0f, 0f), Size(w * 0.28f, h))
}

private fun DrawScope.drawUkFlag(w: Float, h: Float) {
    drawRect(Color(0xFF012169))
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.16f)
    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.16f)
    drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.07f)
    drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.07f)
    drawRect(Color.White, Offset(w * 0.39f, 0f), Size(w * 0.22f, h))
    drawRect(Color.White, Offset(0f, h * 0.39f), Size(w, h * 0.22f))
    drawRect(Color(0xFFC8102E), Offset(w * 0.43f, 0f), Size(w * 0.14f, h))
    drawRect(Color(0xFFC8102E), Offset(0f, h * 0.43f), Size(w, h * 0.14f))
}

private fun DrawScope.drawKwdFlag(w: Float, h: Float) {
    drawRect(Color(0xFF007A3D), Offset(0f, 0f), Size(w, h / 3f))
    drawRect(Color.White, Offset(0f, h / 3f), Size(w, h / 3f))
    drawRect(Color(0xFFCE1126), Offset(0f, 2f * h / 3f), Size(w, h / 3f))
    val trap = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.30f, h / 3f)
        lineTo(w * 0.30f, 2f * h / 3f)
        lineTo(0f, h)
        close()
    }
    drawPath(trap, Color(0xFF111111))
}

private fun DrawScope.drawTryFlag(w: Float, h: Float) {
    drawRect(Color(0xFFE30A17))
    drawCircle(Color.White, radius = w * 0.22f, center = Offset(w * 0.44f, h * 0.5f))
    drawCircle(Color(0xFFE30A17), radius = w * 0.175f, center = Offset(w * 0.49f, h * 0.5f))
    drawCircle(Color.White, radius = w * 0.065f, center = Offset(w * 0.68f, h * 0.5f))
}

private fun DrawScope.drawCadFlag(w: Float, h: Float) {
    drawRect(Color.White)
    drawRect(Color(0xFFD80621), Offset(0f, 0f), Size(w * 0.26f, h))
    drawRect(Color(0xFFD80621), Offset(w * 0.74f, 0f), Size(w * 0.26f, h))
    drawCircle(Color(0xFFD80621), radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
}

private fun DrawScope.drawChfFlag(w: Float, h: Float) {
    drawRect(Color(0xFFD52B1E))
    drawRect(Color.White, Offset(w * 0.42f, h * 0.22f), Size(w * 0.16f, h * 0.56f))
    drawRect(Color.White, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.16f))
}

private fun DrawScope.drawCnyFlag(w: Float, h: Float) {
    drawRect(Color(0xFFDE2910))
    drawCircle(Color(0xFFFFDE00), radius = w * 0.11f, center = Offset(w * 0.30f, h * 0.36f))
    drawCircle(Color(0xFFFFDE00), radius = w * 0.04f, center = Offset(w * 0.46f, h * 0.24f))
    drawCircle(Color(0xFFFFDE00), radius = w * 0.04f, center = Offset(w * 0.51f, h * 0.35f))
    drawCircle(Color(0xFFFFDE00), radius = w * 0.04f, center = Offset(w * 0.48f, h * 0.46f))
}

private fun DrawScope.drawJpyFlag(w: Float, h: Float) {
    drawRect(Color.White)
    drawCircle(Color(0xFFBC002D), radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.5f))
}

private fun DrawScope.drawSarFlag(w: Float, h: Float) {
    drawRect(Color(0xFF006C35))
    drawRect(Color.White, Offset(w * 0.25f, h * 0.62f), Size(w * 0.50f, h * 0.07f))
    drawRect(Color.White, Offset(w * 0.30f, h * 0.38f), Size(w * 0.40f, h * 0.12f))
}

private fun DrawScope.drawIqdFlag(w: Float, h: Float) {
    drawRect(Color(0xFFCE1126), Offset(0f, 0f), Size(w, h / 3f))
    drawRect(Color.White, Offset(0f, h / 3f), Size(w, h / 3f))
    drawRect(Color(0xFF111111), Offset(0f, 2f * h / 3f), Size(w, h / 3f))
    drawCircle(Color(0xFF007A3D), radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.5f))
}

private fun DrawScope.drawIranFlag(w: Float, h: Float) {
    drawRect(Color(0xFF239F40), Offset(0f, 0f), Size(w, h / 3f))
    drawRect(Color.White, Offset(0f, h / 3f), Size(w, h / 3f))
    drawRect(Color(0xFFDA0000), Offset(0f, 2f * h / 3f), Size(w, h / 3f))
    drawCircle(Color(0xFFDA0000), radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.5f))
}
