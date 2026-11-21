package ir.thepoo7an.chand.widget

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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import ir.thepoo7an.chand.MainActivity
import ir.thepoo7an.chand.R
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.ui.strings.NumberFormatters

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
        WidgetSyncCoordinator.registerConnectivityMonitor(context.applicationContext)
        val ok = WidgetSyncCoordinator.syncWidgetsNow(context.applicationContext, force = true)
        if (!ok) {
            WidgetSyncCoordinator.updateAllWidgets(context.applicationContext)
        }
    }
}

private object LockWidgetColors {
    val pureWhite = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val softWhite = ColorProvider(day = Color(0xD9FFFFFF), night = Color(0xD9FFFFFF))
    val mutedWhite = ColorProvider(day = Color(0x99FFFFFF), night = Color(0x99FFFFFF))
    val pureBlack = ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))
}

/**
 * Widget 5 (Lock Screen / Transparent): Background-free monochrome pill aesthetic inspired by
 * Nothing OS lock-screen typography (high-contrast white text, solid white pills, outlined capsules).
 */
class ChandLockScreenWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefsRepo = PreferencesRepository(context)
        val prefs = prefsRepo.getPreferences()
        val snapshot = prefsRepo.loadCachedSnapshot()
        val allAssets = snapshot?.assets.orEmpty()

        val hero = allAssets.firstOrNull { it.id == "USD" }
            ?: allAssets.firstOrNull { it.id in prefs.pinnedIds }
            ?: allAssets.firstOrNull()

        val secondaryTargets = listOf("EUR", "GOLD_18K", "GBP", "AED", "BTC")
        val secondary = secondaryTargets
            .filter { it != hero?.id }
            .mapNotNull { targetId -> allAssets.firstOrNull { it.id == targetId } }
            .take(2)

        provideContent {
            LockScreenWidgetContent(
                hero = hero,
                secondary = secondary,
                updatedAtMillis = snapshot?.updatedAtMillis ?: 0L,
                prefs = prefs
            )
        }
    }
}

@Composable
private fun LockScreenWidgetContent(
    hero: MarketAsset?,
    secondary: List<MarketAsset>,
    updatedAtMillis: Long,
    prefs: AppPreferences
) {
    val size = LocalSize.current
    val isTall = size.height >= 84.dp
    val isWide = size.width >= 215.dp
    val isFa = prefs.language == AppLanguage.FA
    val timeTag = NumberFormatters.formatTime(updatedAtMillis, prefs.usePersianDigits)
        .ifBlank { if (isFa) "زنده" else "LIVE" }

    // Root Box intentionally has NO background so the lock screen / home wallpaper shines through.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = if (isTall) 6.dp else 4.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hero == null) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionRunCallback<RefreshAllWidgetsAction>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHAND",
                    style = TextStyle(
                        color = LockWidgetColors.pureWhite,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_lock_pill_solid))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFa) "لمس برای نرخ" else "TAP TO SYNC",
                        style = TextStyle(
                            color = LockWidgetColors.pureBlack,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "✳",
                    style = TextStyle(
                        color = LockWidgetColors.pureWhite,
                        fontSize = 15.sp
                    )
                )
            }
        } else {
            val deltaText = NumberFormatters.formatDeltaWithArrow(
                hero.change24hToman,
                hero.change24hPercent,
                prefs.usePersianDigits
            )
            val pctText = NumberFormatters.formatPercent(hero.change24hPercent, prefs.usePersianDigits)
            val statusBadgeText = if (isWide && isTall) "$deltaText • $pctText" else deltaText

            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Row 1: Monochrome pill header inspired by [20] (NOV) 21:45 ▮  ✳
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHAND",
                        style = TextStyle(
                            color = LockWidgetColors.softWhite,
                            fontSize = if (isTall) 11.sp else 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_lock_pill_solid))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hero.symbol,
                            style = TextStyle(
                                color = LockWidgetColors.pureBlack,
                                fontSize = if (isTall) 10.sp else 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_lock_pill_outline))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeTag,
                            style = TextStyle(
                                color = LockWidgetColors.pureWhite,
                                fontSize = if (isTall) 10.sp else 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    if (isWide) {
                        Spacer(modifier = GlanceModifier.width(5.dp))
                        Box(
                            modifier = GlanceModifier
                                .width(4.dp)
                                .height(10.dp)
                                .background(LockWidgetColors.pureWhite)
                        ) {}
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .clickable(actionRunCallback<RefreshAllWidgetsAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✳",
                            style = TextStyle(
                                color = LockWidgetColors.pureWhite,
                                fontSize = if (isTall) 15.sp else 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(if (isTall) 5.dp else 3.dp))

                // Row 2: Hero rate + inverted solid white delta pill (like CLEAR NIGHT)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = NumberFormatters.formatCardPrice(hero.priceToman, prefs.usePersianDigits),
                        style = TextStyle(
                            color = LockWidgetColors.pureWhite,
                            fontSize = if (isTall) 25.sp else 19.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Text(
                        text = "TMN",
                        style = TextStyle(
                            color = LockWidgetColors.mutedWhite,
                            fontSize = if (isTall) 10.sp else 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_lock_pill_solid))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = TextStyle(
                                color = LockWidgetColors.pureBlack,
                                fontSize = if (isTall) 10.sp else 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Row 3 (Tall lock screen layout): Outlined EUR + 18K Gold sub-tickers without background
                if (isTall && secondary.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        secondary.forEachIndexed { idx, item ->
                            val shortCode = when (item.id) {
                                "GOLD_18K" -> "18K"
                                else -> item.symbol
                            }
                            val arrow = when {
                                item.change24hToman > 0.001 || item.change24hPercent > 0.005 -> "↑"
                                item.change24hToman < -0.001 || item.change24hPercent < -0.005 -> "↓"
                                else -> "•"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = GlanceModifier
                                        .background(ImageProvider(R.drawable.widget_lock_pill_outline))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shortCode,
                                        style = TextStyle(
                                            color = LockWidgetColors.pureWhite,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = GlanceModifier.width(5.dp))
                                Text(
                                    text = NumberFormatters.formatCardPrice(
                                        item.priceToman,
                                        prefs.usePersianDigits
                                    ),
                                    style = TextStyle(
                                        color = LockWidgetColors.pureWhite,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                                if (isWide) {
                                    Spacer(modifier = GlanceModifier.width(3.dp))
                                    Text(
                                        text = arrow,
                                        style = TextStyle(
                                            color = LockWidgetColors.mutedWhite,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            if (idx < secondary.lastIndex) {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
        }
    }
}
