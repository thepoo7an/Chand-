package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.ThemeMode
import com.example.ui.strings.ChandStrings
import com.example.ui.strings.NumberFormatters
import com.example.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: AppPreferences,
    strings: ChandStrings,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPersianDigits: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current

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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

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
                        val selected = prefs.themeMode == mode
                        SelectableOptionTile(
                            label = label,
                            selected = selected,
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
