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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.thepoo7an.chand.BuildConfig
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: AppPreferences,
    strings: ChandStrings,
    updateState: AppUpdateState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPersianDigits: (Boolean) -> Unit,
    onSetIsGridView: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit,
    onSetWidgetTranslucent: (Boolean) -> Unit,
    onSetWidgetOpacityPercent: (Int) -> Unit,
    onCheckForUpdates: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current
    var repoInput by remember(prefs.githubRepoSlug) { mutableStateOf(prefs.githubRepoSlug) }

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
                val isFa = prefs.language == AppLanguage.FA

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
                                val sampleRows = listOf(
                                    Triple("🇺🇸", if (isFa) "دلار" else "USD", "100,045"),
                                    Triple("🇪🇺", if (isFa) "یورو" else "EUR", "109,755"),
                                    Triple("✨", if (isFa) "طلا" else "GOLD", "7,403,000")
                                )
                                sampleRows.forEachIndexed { idx, (flag, code, price) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = flag, fontSize = 11.sp)
                                            Text(
                                                text = code,
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
                                                    text = if (idx == 1) "↓" else "↑",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (idx == 1) marketColors.loss else marketColors.gain
                                                )
                                                Text(
                                                    text = NumberFormatters.applyDigits(price, prefs.usePersianDigits),
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
                                        text = "CHAND",
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
                                            text = "USD",
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
                                            text = NumberFormatters.applyDigits("21:45", prefs.usePersianDigits),
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
                                        text = NumberFormatters.applyDigits("269,970", prefs.usePersianDigits),
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
                                        text = NumberFormatters.applyDigits("↑1.43%", prefs.usePersianDigits),
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
                                listOf(
                                    "EUR" to NumberFormatters.applyDigits("297,320", prefs.usePersianDigits),
                                    "18K" to NumberFormatters.applyDigits("7,403,000", prefs.usePersianDigits)
                                ).forEach { (sym, valStr) ->
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
            }

            // App Updates Section (Zero-permission GitHub Releases check + Browser download)
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
                            onValueChange = { repoInput = it },
                            singleLine = true,
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
                                val sizeMb = if (info.matchedApkSizeBytes > 0L) {
                                    String.format(
                                        java.util.Locale.US,
                                        " • %.1f MB",
                                        info.matchedApkSizeBytes / (1024.0 * 1024.0)
                                    )
                                } else {
                                    ""
                                }
                                Text(
                                    text = "${strings.updateAvailableTitle}: v${info.latestVersionName}" +
                                        (info.latestVersionCode?.let { " (Build $it)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = marketColors.gain
                                )
                                Text(
                                    text = "${info.matchedApkName}$sizeMb",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (info.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = info.releaseNotes.take(260),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("download_update_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.downloadUpdateButton, fontSize = 12.sp, maxLines = 1)
                                    }
                                    if (info.releasePageUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl))
                                                    )
                                                }
                                            }
                                        ) {
                                            Text(strings.viewReleaseButton, fontSize = 12.sp, maxLines = 1)
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
                                Text(
                                    text = strings.updateCheckErrorLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = marketColors.loss
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
