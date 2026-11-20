package com.example.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesRepository
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AssetCategory
import com.example.model.CachedMarketSnapshot
import com.example.model.MarketAsset
import com.example.network.MarketRemoteDataSource
import com.example.ui.strings.NumberFormatters

private object WidgetColors {
    val solidDivider = ColorProvider(day = Color(0xFFECE8E0), night = Color(0xFF1D232E))
    val glassDivider = ColorProvider(day = Color(0x240F172A), night = Color(0x26FFFFFF))
    val title = ColorProvider(day = Color(0xFF111317), night = Color(0xFFFFFFFF))
    val sub = ColorProvider(day = Color(0xFF525B69), night = Color(0xFFCBD5E1))
    val gain = ColorProvider(day = Color(0xFF059669), night = Color(0xFF34D399))
    val loss = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
    val solidBadgeBg = ColorProvider(day = Color(0xFFF3F1EC), night = Color(0xFF161D28))
    val glassBadgeBg = ColorProvider(day = Color(0x260F172A), night = Color(0x29FFFFFF))
}

@DrawableRes
private fun widgetBackgroundDrawableRes(prefs: AppPreferences): Int {
    if (!prefs.widgetTranslucent) return R.drawable.widget_bg_solid
    return when {
        prefs.widgetOpacityPercent <= 58 -> R.drawable.widget_bg_glass_50
        prefs.widgetOpacityPercent >= 78 -> R.drawable.widget_bg_glass_85
        else -> R.drawable.widget_bg_glass_70
    }
}

/**
 * Widget 1 (2x2 Hero Card): Single primary currency card (US Dollar or top pinned),
 * mirroring the top-left Chand?! iOS widget.
 */
class ChandSingleAssetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()
        val assets = snapshot?.assets.orEmpty()
        val hero = assets.firstOrNull { it.id == "USD" }
            ?: assets.firstOrNull { it.id in prefs.pinnedIds }
            ?: assets.firstOrNull()

        provideContent {
            SingleHeroWidgetContent(asset = hero, prefs = prefs)
        }
    }
}

@Composable
private fun SingleHeroWidgetContent(
    asset: MarketAsset?,
    prefs: AppPreferences
) {
    val isFa = prefs.language == AppLanguage.FA
    val badgeBg = if (prefs.widgetTranslucent) WidgetColors.glassBadgeBg else WidgetColors.solidBadgeBg

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(widgetBackgroundDrawableRes(prefs)))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (asset == null) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Chand?!",
                    style = TextStyle(
                        color = WidgetColors.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            val isGain = asset.change24hPercent >= 0.0
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .background(badgeBg)
                            .cornerRadius(18.dp)
                            .clickable(actionRunCallback<RefreshAllWidgetsAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = asset.badgeText,
                            style = TextStyle(fontSize = 18.sp)
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isFa) asset.nameFa else asset.nameEn,
                            style = TextStyle(
                                color = WidgetColors.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = asset.symbol,
                            style = TextStyle(
                                color = WidgetColors.sub,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(
                    text = NumberFormatters.formatDeltaWithArrow(
                        asset.change24hToman,
                        asset.change24hPercent,
                        prefs.usePersianDigits
                    ),
                    style = TextStyle(
                        color = if (isGain) WidgetColors.gain else WidgetColors.loss,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = NumberFormatters.formatCardPrice(asset.priceToman, prefs.usePersianDigits),
                    style = TextStyle(
                        color = WidgetColors.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Widget 2 (4x2 Wide Strip): 3-row pinned currency list (EUR, GBP, AED or pinned favorites),
 * mirroring the middle wide Chand?! iOS widget.
 */
class ChandWideListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()
        val allAssets = snapshot?.assets.orEmpty()

        val preferredIds = listOf("EUR", "GBP", "AED", "USD", "GOLD_18K")
        val pinnedNonCrypto = allAssets.filter {
            it.id in prefs.pinnedIds && it.category != AssetCategory.CRYPTO && it.id != "USD"
        }
        val fallbackNonCrypto = allAssets.filter { it.id in preferredIds }
        val rows = (if (pinnedNonCrypto.size >= 3) pinnedNonCrypto else pinnedNonCrypto + fallbackNonCrypto)
            .distinctBy { it.id }
            .take(3)

        provideContent {
            MultiRowWidgetContent(
                rows = rows,
                prefs = prefs,
                compactMode = false
            )
        }
    }
}

/**
 * Widget 3 (2x2 Compact Crypto Watch): 3-row compact BTC / ETH / USDT card,
 * mirroring the bottom-right Chand?! iOS widget.
 */
class ChandCompactCryptoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()
        val allAssets = snapshot?.assets.orEmpty()

        val cryptoOrder = listOf("BTC", "ETH", "USDT", "TON", "SOL")
        val cryptos = cryptoOrder.mapNotNull { id -> allAssets.firstOrNull { it.id == id } }.take(3)
        val rows = cryptos.ifEmpty { allAssets.take(3) }

        provideContent {
            MultiRowWidgetContent(
                rows = rows,
                prefs = prefs,
                compactMode = true
            )
        }
    }
}

/**
 * Widget 4 (2x1 Compact Essential Strip): US Dollar, Euro, and 18K Gold with translucent glass support.
 */
class ChandCompact2x1Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()
        val allAssets = snapshot?.assets.orEmpty()

        val essentialIds = listOf("USD", "EUR", "GOLD_18K")
        val rows = essentialIds.mapNotNull { targetId ->
            allAssets.firstOrNull { it.id == targetId }
        }

        provideContent {
            Compact2x1WidgetContent(
                rows = rows,
                prefs = prefs
            )
        }
    }
}

@Composable
private fun Compact2x1WidgetContent(
    rows: List<MarketAsset>,
    prefs: AppPreferences
) {
    val size = LocalSize.current
    val isFa = prefs.language == AppLanguage.FA
    val isTall = size.height >= 92.dp
    val showFullDelta = size.width >= 165.dp || isTall
    val dividerColor = if (prefs.widgetTranslucent) WidgetColors.glassDivider else WidgetColors.solidDivider

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(widgetBackgroundDrawableRes(prefs)))
            .padding(
                horizontal = if (isTall) 12.dp else 10.dp,
                vertical = if (isTall) 8.dp else 5.dp
            )
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (rows.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionRunCallback<RefreshAllWidgetsAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFa) "دلار • یورو • طلا (لمس)" else "USD • EUR • GOLD",
                    style = TextStyle(
                        color = WidgetColors.sub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rows.forEachIndexed { idx, asset ->
                    val isGain = asset.change24hPercent >= 0.0
                    val trendColor = if (isGain) WidgetColors.gain else WidgetColors.loss
                    val shortLabel = when (asset.id) {
                        "USD" -> if (isFa) "دلار" else "USD"
                        "EUR" -> if (isFa) "یورو" else "EUR"
                        "GOLD_18K" -> if (isFa) "طلا" else "GOLD"
                        else -> asset.symbol
                    }
                    val arrowSymbol = when {
                        asset.change24hToman > 0.001 || asset.change24hPercent > 0.005 -> "↑"
                        asset.change24hToman < -0.001 || asset.change24hPercent < -0.005 -> "↓"
                        else -> "•"
                    }

                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.clickable(actionRunCallback<RefreshAllWidgetsAction>())
                        ) {
                            Text(
                                text = asset.badgeText,
                                style = TextStyle(fontSize = if (isTall) 13.sp else 11.sp)
                            )
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = shortLabel,
                                style = TextStyle(
                                    color = WidgetColors.sub,
                                    fontSize = if (isTall) 12.sp else 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        if (showFullDelta) {
                            Text(
                                text = NumberFormatters.formatDeltaWithArrow(
                                    asset.change24hToman,
                                    asset.change24hPercent,
                                    prefs.usePersianDigits
                                ),
                                style = TextStyle(
                                    color = trendColor,
                                    fontSize = if (isTall) 10.sp else 9.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.width(5.dp))
                        } else {
                            Text(
                                text = arrowSymbol,
                                style = TextStyle(
                                    color = trendColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.width(3.dp))
                        }

                        Text(
                            text = NumberFormatters.formatCardPrice(
                                asset.priceToman,
                                prefs.usePersianDigits
                            ),
                            style = TextStyle(
                                color = WidgetColors.title,
                                fontSize = if (isTall) 13.sp else 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }

                    if (isTall && idx < rows.lastIndex) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(dividerColor)
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiRowWidgetContent(
    rows: List<MarketAsset>,
    prefs: AppPreferences,
    compactMode: Boolean
) {
    val isFa = prefs.language == AppLanguage.FA
    val dividerColor = if (prefs.widgetTranslucent) WidgetColors.glassDivider else WidgetColors.solidDivider
    val badgeBg = if (prefs.widgetTranslucent) WidgetColors.glassBadgeBg else WidgetColors.solidBadgeBg

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(widgetBackgroundDrawableRes(prefs)))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (rows.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isFa) "برای دریافت نرخ لمس کنید" else "Tap to load rates",
                    style = TextStyle(color = WidgetColors.sub, fontSize = 12.sp)
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rows.forEachIndexed { idx, asset ->
                    val isGain = asset.change24hPercent >= 0.0
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(if (compactMode) 22.dp else 24.dp)
                                .background(badgeBg)
                                .cornerRadius(12.dp)
                                .clickable(actionRunCallback<RefreshAllWidgetsAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = asset.badgeText,
                                style = TextStyle(fontSize = if (compactMode) 12.sp else 14.sp)
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = asset.symbol,
                            style = TextStyle(
                                color = WidgetColors.title,
                                fontSize = if (compactMode) 13.sp else 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = NumberFormatters.formatDeltaWithArrow(
                                    asset.change24hToman,
                                    asset.change24hPercent,
                                    prefs.usePersianDigits
                                ),
                                style = TextStyle(
                                    color = if (isGain) WidgetColors.gain else WidgetColors.loss,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = NumberFormatters.formatCardPrice(
                                    asset.priceToman,
                                    prefs.usePersianDigits
                                ),
                                style = TextStyle(
                                    color = WidgetColors.title,
                                    fontSize = if (compactMode) 14.sp else 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    if (idx < rows.lastIndex) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(dividerColor)
                        ) {}
                    }
                }
            }
        }
    }
}

class RefreshAllWidgetsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val remote = MarketRemoteDataSource()
        val prefsRepo = PreferencesRepository(context)
        val res = remote.fetchLiveMarket()
        if (res.isLive && res.assets.isNotEmpty()) {
            prefsRepo.saveCachedSnapshot(
                CachedMarketSnapshot(
                    assets = res.assets,
                    updatedAtMillis = res.fetchedAtMillis,
                    sourceSummary = res.sourceSummary
                )
            )
        }
        ChandSingleAssetWidget().updateAll(context)
        ChandWideListWidget().updateAll(context)
        ChandCompactCryptoWidget().updateAll(context)
        ChandCompact2x1Widget().updateAll(context)
    }
}
