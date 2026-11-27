package ir.thepoo7an.chand.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.ThemeMode

enum class PriceTrendDirection {
    UP,
    DOWN,
    NEUTRAL
}

fun resolvePriceTrend(
    changeAmount: Double?,
    changePct: Double,
    hasChangeData: Boolean = true
): PriceTrendDirection {
    if (!hasChangeData) return PriceTrendDirection.NEUTRAL
    val validPct = changePct.isFinite()
    val validAmt = changeAmount != null && changeAmount.isFinite()
    if (!validPct && !validAmt) return PriceTrendDirection.NEUTRAL
    return when {
        (validPct && changePct > 0.005) || (validAmt && changeAmount!! > 0.001) -> PriceTrendDirection.UP
        (validPct && changePct < -0.005) || (validAmt && changeAmount!! < -0.001) -> PriceTrendDirection.DOWN
        else -> PriceTrendDirection.NEUTRAL
    }
}

@Immutable
data class MarketColors(
    val priceUp: Color,
    val priceUpContainer: Color,
    val priceDown: Color,
    val priceDownContainer: Color,
    val priceNeutral: Color,
    val priceNeutralContainer: Color,
    val gain: Color,
    val gainContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val gold: Color,
    val hairline: Color,
    val isAmoled: Boolean
) {
    fun trendColor(direction: PriceTrendDirection): Color = when (direction) {
        PriceTrendDirection.UP -> priceUp
        PriceTrendDirection.DOWN -> priceDown
        PriceTrendDirection.NEUTRAL -> priceNeutral
    }

    fun trendContainerColor(direction: PriceTrendDirection): Color = when (direction) {
        PriceTrendDirection.UP -> priceUpContainer
        PriceTrendDirection.DOWN -> priceDownContainer
        PriceTrendDirection.NEUTRAL -> priceNeutralContainer
    }
}

val LocalMarketColors = staticCompositionLocalOf {
    MarketColors(
        priceUp = PriceUpRedAmoled,
        priceUpContainer = PriceUpRedContainerAmoled,
        priceDown = PriceDownGreenAmoled,
        priceDownContainer = PriceDownGreenContainerAmoled,
        priceNeutral = PriceNeutralGrayAmoled,
        priceNeutralContainer = PriceNeutralGrayContainerAmoled,
        gain = GainEmerald,
        gainContainer = GainEmeraldDarkBg,
        loss = LossCoral,
        lossContainer = LossCoralDarkBg,
        gold = GoldAmber,
        hairline = AmoledBorder,
        isAmoled = true
    )
}

private val LightColors = lightColorScheme(
    primary = SkyFinancial,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LossCoral
)

private val SlateDarkColors = darkColorScheme(
    primary = SkySoft,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = LossCoral
)

private val AmoledColors = darkColorScheme(
    primary = CyanElectric,
    onPrimary = Color(0xFF00242B),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color.Black,
    secondaryContainer = AmoledSurfaceVariant,
    onSecondaryContainer = AmoledTextPrimary,
    background = AmoledBg,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledTextSecondary,
    outline = AmoledBorder,
    outlineVariant = AmoledBorder,
    error = LossCoral
)

@Composable
fun ChandTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    language: AppLanguage = AppLanguage.FA,
    content: @Composable () -> Unit
) {
    val isDark = themeMode != ThemeMode.LIGHT
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> SlateDarkColors
        ThemeMode.AMOLED -> AmoledColors
    }

    val marketColors = when (themeMode) {
        ThemeMode.LIGHT -> MarketColors(
            priceUp = PriceUpRedLight,
            priceUpContainer = PriceUpRedContainerLight,
            priceDown = PriceDownGreenLight,
            priceDownContainer = PriceDownGreenContainerLight,
            priceNeutral = PriceNeutralGrayLight,
            priceNeutralContainer = PriceNeutralGrayContainerLight,
            gain = Color(0xFF059669),
            gainContainer = GainEmeraldLightBg,
            loss = Color(0xFFDC2626),
            lossContainer = LossCoralLightBg,
            gold = GoldAmber,
            hairline = LightBorder,
            isAmoled = false
        )
        ThemeMode.DARK -> MarketColors(
            priceUp = PriceUpRedDark,
            priceUpContainer = PriceUpRedContainerDark,
            priceDown = PriceDownGreenDark,
            priceDownContainer = PriceDownGreenContainerDark,
            priceNeutral = PriceNeutralGrayDark,
            priceNeutralContainer = PriceNeutralGrayContainerDark,
            gain = GainEmerald,
            gainContainer = GainEmeraldDarkBg,
            loss = LossCoral,
            lossContainer = LossCoralDarkBg,
            gold = GoldAmber,
            hairline = DarkBorder,
            isAmoled = false
        )
        ThemeMode.AMOLED -> MarketColors(
            priceUp = PriceUpRedAmoled,
            priceUpContainer = PriceUpRedContainerAmoled,
            priceDown = PriceDownGreenAmoled,
            priceDownContainer = PriceDownGreenContainerAmoled,
            priceNeutral = PriceNeutralGrayAmoled,
            priceNeutralContainer = PriceNeutralGrayContainerAmoled,
            gain = GainEmerald,
            gainContainer = GainEmeraldDarkBg,
            loss = LossCoral,
            lossContainer = LossCoralDarkBg,
            gold = GoldAmber,
            hairline = AmoledBorder,
            isAmoled = true
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    val layoutDirection = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildChandTypography(isPersian = language == AppLanguage.FA)

    CompositionLocalProvider(
        LocalMarketColors provides marketColors,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
