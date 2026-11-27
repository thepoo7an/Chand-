package ir.thepoo7an.chand.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import ir.thepoo7an.chand.BuildConfig
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.network.AppUpdateDownloadState
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateInstaller
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.RemoteConfigUiState
import ir.thepoo7an.chand.network.UpdateErrorReason
import ir.thepoo7an.chand.network.UpdateSecurityValidator
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LocalMarketColors
import ir.thepoo7an.chand.ui.theme.PriceTrendDirection
import ir.thepoo7an.chand.ui.theme.resolvePriceTrend
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: AppPreferences,
    strings: ChandStrings,
    updateState: AppUpdateState,
    updateDownloadState: AppUpdateDownloadState = AppUpdateDownloadState.Idle,
    availableAssets: List<MarketAsset> = emptyList(),
    liveConfig: RemoteAppConfig = RemoteAppConfig(),
    liveConfigSyncState: RemoteConfigUiState = RemoteConfigUiState.Idle,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPersianDigits: (Boolean) -> Unit,
    onSetIsGridView: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit,
    onSetWidgetTranslucent: (Boolean) -> Unit,
    onSetWidgetOpacityPercent: (Int) -> Unit,
    onSetWidgetHeroAsset: (String) -> Unit,
    onSetGithubRepoSlug: (String) -> Unit,
    onCheckForUpdates: (String) -> Unit,
    onSetLiveConfigUrl: (String) -> Unit = {},
    onSyncLiveConfig: (String, String) -> Unit = { _, _ -> },
    onResetLiveConfig: () -> Unit = {},
    onStartUpdateDownload: (AppUpdateInfo) -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onInstallUpdateApk: (File, AppUpdateInfo) -> Unit = { _, _ -> },
    onOpenUpdateCatalog: () -> Unit = {},
    onSetShowSupportCard: (Boolean) -> Unit = {},
    onToggleComparisonAsset: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current
    val isFa = prefs.language == AppLanguage.FA
    var repoInput by remember(prefs.githubRepoSlug) { mutableStateOf(prefs.githubRepoSlug) }
    var liveUrlInput by remember(prefs.liveConfigUrl) { mutableStateOf(prefs.liveConfigUrl) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Layout Style Section (2-Column Grid vs List)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.layoutSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableOptionTile(
                        label = strings.layoutGrid,
                        selected = prefs.isGridView,
                        hairline = marketColors.hairline,
                        onClick = { onSetIsGridView(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("layout_grid")
                    )
                    SelectableOptionTile(
                        label = strings.layoutList,
                        selected = !prefs.isGridView,
                        hairline = marketColors.hairline,
                        onClick = { onSetIsGridView(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("layout_list")
                    )
                }
            }

            // Theme Mode Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.themeSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        Triple(ThemeMode.LIGHT, strings.themeLight, "theme_light"),
                        Triple(ThemeMode.DARK, strings.themeDark, "theme_dark"),
                        Triple(ThemeMode.AMOLED, strings.themeAmoled, "theme_amoled")
                    )
                    options.forEach { (mode, label, tag) ->
                        SelectableOptionTile(
                            label = label,
                            selected = prefs.themeMode == mode,
                            hairline = marketColors.hairline,
                            onClick = { onSetThemeMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tag)
                        )
                    }
                }
            }

            // Language Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.languageSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableOptionTile(
                        label = strings.langPersian,
                        selected = prefs.language == AppLanguage.FA,
                        hairline = marketColors.hairline,
                        onClick = { onSetLanguage(AppLanguage.FA) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lang_fa")
                    )
                    SelectableOptionTile(
                        label = strings.langEnglish,
                        selected = prefs.language == AppLanguage.EN,
                        hairline = marketColors.hairline,
                        onClick = { onSetLanguage(AppLanguage.EN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lang_en")
                    )
                }
            }

            // Persian Digits Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, marketColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.persianDigitsLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = prefs.usePersianDigits,
                        onCheckedChange = onSetPersianDigits,
                        modifier = Modifier.testTag("persian_digits_switch")
                    )
                }
            }

            // Auto-refresh interval
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.autoRefreshSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(15, 30, 60, 0)
                    intervals.forEach { sec ->
                        val label = if (sec == 0) {
                            strings.manualRefreshOnly
                        } else {
                            "${NumberFormatters.applyDigits(sec.toString(), prefs.usePersianDigits)} ${strings.secondsUnit}"
                        }
                        SelectableOptionTile(
                            label = label,
                            selected = prefs.refreshIntervalSeconds == sec,
                            hairline = marketColors.hairline,
                            onClick = { onSetRefreshInterval(sec) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("refresh_interval_$sec")
                        )
                    }
                }
            }

            // Home Screen Widgets (Translucent Glass + 2x1 USD / EUR / Gold Preview)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.widgetSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, marketColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.widgetTranslucentLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = prefs.widgetTranslucent,
                            onCheckedChange = onSetWidgetTranslucent,
                            modifier = Modifier.testTag("widget_translucent_switch")
                        )
                    }
                }

                AnimatedVisibility(visible = prefs.widgetTranslucent) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.widgetOpacityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50, 70, 85).forEach { pct ->
                                SelectableOptionTile(
                                    label = "${NumberFormatters.applyDigits(pct.toString(), prefs.usePersianDigits)}%",
                                    selected = prefs.widgetOpacityPercent == pct,
                                    hairline = marketColors.hairline,
                                    onClick = { onSetWidgetOpacityPercent(pct) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("widget_opacity_$pct")
                                )
                            }
                        }
                    }
                }

                val heroCandidates = remember(availableAssets, prefs.pinnedIds) {
                    val defaultOrder = listOf(
                        "USD" to ("🇺🇸" to "USD"),
                        "EUR" to ("🇪🇺" to "EUR"),
                        "GOLD_18K" to ("✨" to "18K"),
                        "GOLD_MESGHAL" to ("⚖️" to "MSQ"),
                        "COIN_EMAMI" to ("🪙" to "SEKE"),
                        "BTC" to ("₿" to "BTC"),
                        "ETH" to ("Ξ" to "ETH"),
                        "USDT" to ("₮" to "USDT")
                    )
                    val assetMap = availableAssets.associateBy { it.id }
                    val baseIds = (defaultOrder.map { it.first } + prefs.pinnedIds).distinct()
                    baseIds.mapNotNull { id ->
                        val live = assetMap[id]
                        val fallback = defaultOrder.firstOrNull { it.first == id }?.second
                        when {
                            live != null -> Triple(live.id, live.badgeText, live.symbol)
                            fallback != null -> Triple(id, fallback.first, fallback.second)
                            else -> null
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.widgetHeroAssetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(heroCandidates, key = { it.first }) { (assetId, badge, symbol) ->
                            val selected = prefs.widgetHeroAssetId == assetId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else marketColors.hairline
                                ),
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSetWidgetHeroAsset(assetId) }
                                    .testTag("widget_hero_$assetId")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(text = badge, fontSize = 13.sp)
                                    Text(
                                        text = symbol,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Miniature 2x1 Widget Preview over Wallpaper Backdrop
                val previewAlpha = if (!prefs.widgetTranslucent) 1f else when {
                    prefs.widgetOpacityPercent <= 58 -> 0.50f
                    prefs.widgetOpacityPercent >= 78 -> 0.85f
                    else -> 0.70f
                }
                val isLightTheme = prefs.themeMode == ThemeMode.LIGHT
                val previewCardBg = if (isLightTheme) {
                    Color.White.copy(alpha = previewAlpha)
                } else {
                    Color(0xFF080B10).copy(alpha = previewAlpha)
                }
                val previewBorder = if (isLightTheme) {
                    Color(0xFF0F172A).copy(alpha = if (prefs.widgetTranslucent) 0.18f else 0.10f)
                } else {
                    Color.White.copy(alpha = if (prefs.widgetTranslucent) 0.24f else 0.12f)
                }

                val previewMap = remember(availableAssets) { availableAssets.associateBy { it.id } }
                val usdPreview = previewMap["USD"]
                val eurPreview = previewMap["EUR"]
                val goldPreview = previewMap["GOLD_18K"]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E3A8A),
                                    Color(0xFF6D28D9),
                                    Color(0xFF0F766E)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = previewCardBg,
                            border = BorderStroke(1.dp, previewBorder),
                            modifier = Modifier.width(196.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                data class PreviewRow(val flag: String, val code: String, val price: String, val trend: PriceTrendDirection)
                                val sampleRows = listOf(
                                    PreviewRow(
                                        usdPreview?.badgeText ?: "🇺🇸",
                                        if (isFa) "دلار" else "USD",
                                        usdPreview?.priceToman?.takeIf { it > 0.0 }?.let { NumberFormatters.formatToman(it, false) } ?: "100,045",
                                        if (usdPreview != null) {
                                            resolvePriceTrend(usdPreview.change24hToman, usdPreview.change24hPercent, usdPreview.hasUsablePrimaryPrice)
                                        } else {
                                            PriceTrendDirection.UP
                                        }
                                    ),
                                    PreviewRow(
                                        eurPreview?.badgeText ?: "🇪🇺",
                                        if (isFa) "یورو" else "EUR",
                                        eurPreview?.priceToman?.takeIf { it > 0.0 }?.let { NumberFormatters.formatToman(it, false) } ?: "109,755",
                                        if (eurPreview != null) {
                                            resolvePriceTrend(eurPreview.change24hToman, eurPreview.change24hPercent, eurPreview.hasUsablePrimaryPrice)
                                        } else {
                                            PriceTrendDirection.DOWN
                                        }
                                    ),
                                    PreviewRow(
                                        goldPreview?.badgeText ?: "✨",
                                        if (isFa) "طلا" else "GOLD",
                                        goldPreview?.priceToman?.takeIf { it > 0.0 }?.let { NumberFormatters.formatToman(it, false) } ?: "7,403,000",
                                        if (goldPreview != null) {
                                            resolvePriceTrend(goldPreview.change24hToman, goldPreview.change24hPercent, goldPreview.hasUsablePrimaryPrice)
                                        } else {
                                            PriceTrendDirection.UP
                                        }
                                    )
                                )
                                sampleRows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = row.flag, fontSize = 11.sp)
                                            Text(
                                                text = row.code,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLightTheme) Color(0xFF4B5563) else Color(0xFFCBD5E1)
                                            )
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = when (row.trend) {
                                                        PriceTrendDirection.UP -> "↑"
                                                        PriceTrendDirection.DOWN -> "↓"
                                                        PriceTrendDirection.NEUTRAL -> "•"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = marketColors.trendColor(row.trend)
                                                )
                                                Text(
                                                    text = NumberFormatters.applyDigits(row.price, prefs.usePersianDigits),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isLightTheme) Color(0xFF111317) else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = strings.widgetPreviewTitle,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }

                // Transparent Nothing-Inspired Lock Screen Widget Preview
                val lockHero = previewMap[prefs.widgetHeroAssetId] ?: usdPreview
                val lockStatusWord = when (lockHero?.freshness) {
                    DataFreshness.DELAYED -> strings.delayedBadge
                    DataFreshness.CACHED -> strings.cachedBadge
                    DataFreshness.UNAVAILABLE -> strings.unavailableBadge
                    else -> strings.liveBadge
                }
                val lockTimeStr = lockHero?.updatedAtMillis?.takeIf { it > 0L }
                    ?.let { NumberFormatters.formatTime(it, prefs.usePersianDigits) }
                    ?.takeIf { it.isNotBlank() }
                    ?: NumberFormatters.applyDigits("21:45", prefs.usePersianDigits)
                val lockStatusLabel = if (lockHero?.freshness == DataFreshness.UNAVAILABLE) {
                    strings.unavailableBadge
                } else {
                    "$lockStatusWord • $lockTimeStr"
                }
                val lockHeroPrice = lockHero?.priceToman?.takeIf { it > 0.0 }
                    ?.let { NumberFormatters.formatToman(it, prefs.usePersianDigits) }
                    ?: NumberFormatters.applyDigits("269,970", prefs.usePersianDigits)
                val lockHeroDelta = lockHero?.takeIf { it.priceToman > 0.0 }
                    ?.let { NumberFormatters.formatPercent(it.change24hPercent, prefs.usePersianDigits) }
                    ?: NumberFormatters.applyDigits("↑1.43%", prefs.usePersianDigits)
                val lockPair = listOfNotNull(
                    eurPreview?.let {
                        it.symbol to (it.priceToman.takeIf { p -> p > 0.0 }?.let { p -> NumberFormatters.formatToman(p, prefs.usePersianDigits) } ?: "—")
                    } ?: ("EUR" to NumberFormatters.applyDigits("297,320", prefs.usePersianDigits)),
                    goldPreview?.let {
                        it.symbol to (it.priceToman.takeIf { p -> p > 0.0 }?.let { p -> NumberFormatters.formatToman(p, prefs.usePersianDigits) } ?: "—")
                    } ?: ("18K" to NumberFormatters.applyDigits("7,403,000", prefs.usePersianDigits))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF000000),
                                    Color(0xFF0D1117),
                                    Color(0xFF020617)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (isFa) "نرخ‌نما" else "NERKHNAMA",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.White
                                    ) {
                                        Text(
                                            text = lockHero?.symbol ?: "USD",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
                                    ) {
                                        Text(
                                            text = lockStatusLabel,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(10.dp)
                                            .background(Color.White)
                                    )
                                }
                                Text(
                                    text = "✳",
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = lockHeroPrice,
                                        fontSize = 22.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "TMN",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White
                                ) {
                                    Text(
                                        text = lockHeroDelta,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                lockPair.forEach { (sym, valStr) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f))
                                        ) {
                                            Text(
                                                text = sym,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = valStr,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.widgetLockPreviewTitle,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = marketColors.gold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, marketColors.gold.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_widget_pre12_notice")
                    ) {
                        Text(
                            text = strings.lockScreenPreAndroid12Notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = marketColors.gold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Multi-Source Price Comparison Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.comparisonSectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.comparisonSectionSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPreferences.CONFIGURABLE_COMPARISON_IDS.forEach { assetId ->
                        val enabled = assetId in prefs.comparisonAssetIds
                        val chipLabel = when (assetId) {
                            "GOLD_18K" -> if (isFa) "طلا ۱۸" else "18K"
                            else -> assetId
                        }
                        SelectableOptionTile(
                            label = chipLabel,
                            selected = enabled,
                            hairline = marketColors.hairline,
                            onClick = { onToggleComparisonAsset(assetId) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comparison_toggle_$assetId")
                        )
                    }
                }
            }

            // Optional Support Development Card Toggle (hidden by default)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, marketColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.supportCardToggleLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    )
                    Switch(
                        checked = prefs.showSupportCard,
                        onCheckedChange = onSetShowSupportCard,
                        modifier = Modifier.testTag("support_card_switch")
                    )
                }
            }

            // Online Live Patch & Rules Section (Applied Instantly Without APK Download)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.liveConfigSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, marketColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val versionStatus = if (liveConfig.isBuiltInDefault) {
                                    strings.liveConfigBuiltInLabel
                                } else {
                                    val overrideCount = liveConfig.activeOverrideCount()
                                    val verStr = "${strings.liveConfigVersionPrefix}${NumberFormatters.applyDigits(liveConfig.configVersion.toString(), prefs.usePersianDigits)}"
                                    if (overrideCount > 0) {
                                        "$verStr • ${NumberFormatters.applyDigits(overrideCount.toString(), prefs.usePersianDigits)} ${strings.liveConfigOverridesActiveLabel}"
                                    } else {
                                        verStr
                                    }
                                }
                                Text(
                                    text = versionStatus,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.liveConfigSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (prefs.liveConfigLastSyncMillis > 0L) {
                            Text(
                                text = "${strings.liveConfigLastSyncPrefix} ${
                                    NumberFormatters.formatJalaliDateTime(prefs.liveConfigLastSyncMillis, prefs.usePersianDigits)
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = liveUrlInput,
                            onValueChange = { newUrl ->
                                liveUrlInput = newUrl
                                if (newUrl.isBlank() || LiveConfigRegistry.isSafeHttpsUrl(newUrl)) {
                                    onSetLiveConfigUrl(newUrl)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { onSyncLiveConfig(liveUrlInput, repoInput) }
                            ),
                            label = { Text(strings.liveConfigUrlLabel, fontSize = 12.sp) },
                            placeholder = { Text(strings.liveConfigUrlPlaceholder, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_config_url_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSyncLiveConfig(liveUrlInput, repoInput) },
                                enabled = liveConfigSyncState !is RemoteConfigUiState.Syncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("sync_live_config_button")
                            ) {
                                if (liveConfigSyncState is RemoteConfigUiState.Syncing) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.liveConfigSyncingLabel, fontSize = 12.sp)
                                } else {
                                    Text(strings.liveConfigSyncButton, fontSize = 12.sp)
                                }
                            }
                            if (!liveConfig.isBuiltInDefault) {
                                OutlinedButton(
                                    onClick = onResetLiveConfig,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reset_live_config_button")
                                ) {
                                    Text(strings.liveConfigResetButton, fontSize = 12.sp)
                                }
                            }
                        }

                        when (liveConfigSyncState) {
                            is RemoteConfigUiState.Synced -> {
                                Text(
                                    text = strings.liveConfigSyncedToast,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.gain
                                )
                            }
                            is RemoteConfigUiState.NoRemoteConfigYet -> {
                                Text(
                                    text = strings.liveConfigNoRemoteLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is RemoteConfigUiState.Incompatible -> {
                                Text(
                                    text = strings.liveConfigIncompatibleLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.gold
                                )
                            }
                            is RemoteConfigUiState.Error -> {
                                Text(
                                    text = "${strings.liveConfigErrorLabel}: ${liveConfigSyncState.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.loss
                                )
                            }
                            RemoteConfigUiState.Idle, RemoteConfigUiState.Syncing -> Unit
                        }

                        if (!liveConfig.isBuiltInDefault) {
                            val summaryText = if (isFa) {
                                liveConfig.summaryFa.ifBlank { liveConfig.summaryEn }
                            } else {
                                liveConfig.summaryEn.ifBlank { liveConfig.summaryFa }
                            }.ifBlank { strings.liveConfigDefaultSummary }

                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (liveConfig.appliedChanges.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = strings.liveConfigChangesTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    liveConfig.appliedChanges.take(6).forEach { change ->
                                        val changeText = if (isFa) change.titleFa else change.titleEn
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = changeText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // App Updates Section (Zero-permission GitHub Releases check + In-app APK download)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.updateSection,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, marketColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = strings.currentVersionLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Button(
                                onClick = { onCheckForUpdates(repoInput) },
                                enabled = updateState !is AppUpdateState.Checking,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("check_updates_button")
                            ) {
                                if (updateState is AppUpdateState.Checking) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = strings.checkingUpdatesLabel, fontSize = 12.sp)
                                } else {
                                    Text(text = strings.checkUpdatesButton, fontSize = 12.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { newVal ->
                                repoInput = newVal
                                if (AppUpdateService.parseRepoSlug(newVal) != null &&
                                    newVal.trim() != prefs.githubRepoSlug
                                ) {
                                    onSetGithubRepoSlug(newVal.trim())
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (repoInput.trim().isNotBlank()) {
                                        onSetGithubRepoSlug(repoInput.trim())
                                    }
                                }
                            ),
                            label = { Text(strings.githubRepoLabel, fontSize = 12.sp) },
                            placeholder = { Text(strings.githubRepoPlaceholder, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input")
                        )

                        when (updateState) {
                            is AppUpdateState.UpdateAvailable -> {
                                val info = updateState.info
                                val sizeLabel = formatApkBytes(info.matchedApkSizeBytes, prefs.usePersianDigits)
                                val parsedSections = remember(info.releaseNotes) {
                                    AppUpdateService.parseReleaseNotes(info.releaseNotes)
                                }
                                val previewItems = remember(parsedSections) {
                                    parsedSections.flatMap { it.items }.take(4)
                                }
                                val isDownloadingThis = updateDownloadState is AppUpdateDownloadState.Downloading &&
                                    updateDownloadState.info.latestVersionName == info.latestVersionName
                                val readyThis = (updateDownloadState as? AppUpdateDownloadState.ReadyToInstall)?.takeIf {
                                    it.info.latestVersionName == info.latestVersionName
                                }
                                val errorThis = (updateDownloadState as? AppUpdateDownloadState.Error)?.takeIf {
                                    it.info.latestVersionName == info.latestVersionName
                                }
                                val canInstallApps = AppUpdateInstaller.canRequestPackageInstalls(context)

                                Text(
                                    text = "${strings.updateAvailableTitle}: v${info.latestVersionName}" +
                                        (info.latestVersionCode?.let { " (Build $it)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = marketColors.gain
                                )
                                Text(
                                    text = if (sizeLabel.isNotBlank()) "${info.matchedApkName} • $sizeLabel" else info.matchedApkName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (previewItems.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = strings.changelogTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        previewItems.forEach { item ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 6.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(marketColors.gain)
                                                )
                                                Text(
                                                    text = item,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isDownloadingThis) {
                                    val downloading = updateDownloadState as AppUpdateDownloadState.Downloading
                                    val fraction = downloading.progressFraction
                                    if (fraction != null) {
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val pctLabel = downloading.progressPercent?.let {
                                            NumberFormatters.applyDigits("$it%", prefs.usePersianDigits)
                                        } ?: strings.downloadingUpdateLabel
                                        Text(
                                            text = pctLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = onCancelUpdateDownload) {
                                            Text(strings.cancelDownloadButton, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    if (errorThis != null) {
                                        Text(
                                            text = strings.updateErrorMessage(errorThis.reason),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = marketColors.loss,
                                            modifier = Modifier.testTag("settings_update_download_error")
                                        )
                                    } else if (readyThis != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = marketColors.gain,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = strings.downloadCompleteLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = marketColors.gain
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val safeFallbackUrl = info.releasePageUrl
                                            .takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                                            ?: info.downloadUrl.takeIf { UpdateSecurityValidator.isSafeExternalHttpsUrl(it) }
                                        Button(
                                            onClick = {
                                                when {
                                                    readyThis != null -> {
                                                        onInstallUpdateApk(File(readyThis.apkFilePath), info)
                                                    }
                                                    info.hasDirectApkAsset -> {
                                                        onStartUpdateDownload(info)
                                                    }
                                                    !safeFallbackUrl.isNullOrBlank() -> {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(safeFallbackUrl))
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("download_update_button")
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    readyThis != null && canInstallApps -> Icons.Outlined.InstallMobile
                                                    readyThis != null -> Icons.Outlined.Security
                                                    else -> Icons.Outlined.Download
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = when {
                                                    readyThis != null && canInstallApps -> strings.installUpdateButton
                                                    readyThis != null -> strings.grantInstallPermissionButton
                                                    errorThis != null && info.hasDirectApkAsset -> strings.retryDownloadButton
                                                    info.hasDirectApkAsset -> strings.downloadUpdateButton
                                                    else -> strings.openInBrowserFallback
                                                },
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = onOpenUpdateCatalog,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text(strings.updateCatalogTitle, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            is AppUpdateState.UpToDate -> {
                                Text(
                                    text = strings.upToDateLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.gain
                                )
                            }

                            is AppUpdateState.RepoNotConfigured -> {
                                Text(
                                    text = strings.updateRepoMissingHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is AppUpdateState.Error -> {
                                val errMsg = if (updateState.reason == UpdateErrorReason.NETWORK_FAILURE) {
                                    strings.updateCheckErrorLabel
                                } else {
                                    strings.updateErrorMessage(updateState.reason)
                                }
                                Text(
                                    text = errMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.loss,
                                    modifier = Modifier.testTag("settings_update_check_error")
                                )
                            }

                            AppUpdateState.Idle, AppUpdateState.Checking -> Unit
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SelectableOptionTile(
    label: String,
    selected: Boolean,
    hairline: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else hairline
        ),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
