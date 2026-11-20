package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AssetCategory
import com.example.model.MarketAsset
import com.example.model.MarketUiState
import com.example.ui.strings.ChandStrings
import com.example.ui.strings.NumberFormatters
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.MarketViewModel

private enum class PickerTarget { FROM, TO }

@Composable
fun ConverterScreen(
    uiState: MarketUiState,
    fromAssetId: String,
    toAssetId: String,
    inputAmountText: String,
    prefs: AppPreferences,
    strings: ChandStrings,
    onSetFromAsset: (String) -> Unit,
    onSetToAsset: (String) -> Unit,
    onSwapAssets: () -> Unit,
    onPresetClick: (String) -> Unit,
    onKeypadPress: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assets = (uiState as? MarketUiState.Ready)?.assets.orEmpty()
    val isFa = prefs.language == AppLanguage.FA
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var swapSpins by remember { mutableStateOf(0) }
    val swapRotation by animateFloatAsState(
        targetValue = swapSpins * 180f,
        animationSpec = spring(),
        label = "swap_spin"
    )

    val tomanVirtual = remember(strings) {
        MarketAsset(
            id = MarketViewModel.TOMAN_ASSET_ID,
            symbol = "TMN",
            nameFa = strings.tomanAssetName,
            nameEn = strings.tomanAssetName,
            category = AssetCategory.FIAT,
            badgeText = "🇮🇷",
            accentHex = 0xFF10B981,
            priceToman = 1.0
        )
    }

    val allChoices = remember(assets, tomanVirtual) { listOf(tomanVirtual) + assets }
    val fromAsset = allChoices.firstOrNull { it.id == fromAssetId } ?: tomanVirtual
    val toAsset = allChoices.firstOrNull { it.id == toAssetId } ?: tomanVirtual

    val inputNumeric = inputAmountText.toDoubleOrNull() ?: 0.0
    val tomanValue = inputNumeric * fromAsset.priceToman
    val outputValue = if (toAsset.priceToman > 0.0) tomanValue / toAsset.priceToman else 0.0
    val unitCrossRate = if (toAsset.priceToman > 0.0) fromAsset.priceToman / toAsset.priceToman else 0.0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 900.dp)
    ) {
        val isWide = maxWidth >= 640.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.converterTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("converter_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = strings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConverterCardsColumn(
                        fromAsset = fromAsset,
                        toAsset = toAsset,
                        inputAmountText = inputAmountText,
                        outputValue = outputValue,
                        tomanValue = tomanValue,
                        unitCrossRate = unitCrossRate,
                        assets = assets,
                        swapRotation = swapRotation,
                        isFa = isFa,
                        prefs = prefs,
                        strings = strings,
                        onOpenPicker = { pickerTarget = it },
                        onSwap = {
                            swapSpins++
                            onSwapAssets()
                        },
                        onPresetClick = onPresetClick,
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState())
                    )
                    NumericKeypad(
                        usePersianDigits = prefs.usePersianDigits,
                        onKeyPress = onKeypadPress,
                        modifier = Modifier
                            .weight(0.9f)
                            .align(Alignment.Bottom)
                            .padding(bottom = 16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    ConverterCardsColumn(
                        fromAsset = fromAsset,
                        toAsset = toAsset,
                        inputAmountText = inputAmountText,
                        outputValue = outputValue,
                        tomanValue = tomanValue,
                        unitCrossRate = unitCrossRate,
                        assets = assets,
                        swapRotation = swapRotation,
                        isFa = isFa,
                        prefs = prefs,
                        strings = strings,
                        onOpenPicker = { pickerTarget = it },
                        onSwap = {
                            swapSpins++
                            onSwapAssets()
                        },
                        onPresetClick = onPresetClick,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NumericKeypad(
                        usePersianDigits = prefs.usePersianDigits,
                        onKeyPress = onKeypadPress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    pickerTarget?.let { target ->
        AssetPickerSheet(
            choices = allChoices,
            isFa = isFa,
            usePersianDigits = prefs.usePersianDigits,
            strings = strings,
            onSelect = { selected ->
                if (target == PickerTarget.FROM) onSetFromAsset(selected.id) else onSetToAsset(selected.id)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun ConverterCardsColumn(
    fromAsset: MarketAsset,
    toAsset: MarketAsset,
    inputAmountText: String,
    outputValue: Double,
    tomanValue: Double,
    unitCrossRate: Double,
    assets: List<MarketAsset>,
    swapRotation: Float,
    isFa: Boolean,
    prefs: AppPreferences,
    strings: ChandStrings,
    onOpenPicker: (PickerTarget) -> Unit,
    onSwap: () -> Unit,
    onPresetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Conversion cards with centered Swap overlay
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConverterSideCard(
                    roleLabel = strings.fromLabel,
                    asset = fromAsset,
                    displayValue = NumberFormatters.applyDigits(
                        formatRawInput(inputAmountText),
                        prefs.usePersianDigits
                    ),
                    isInput = true,
                    isFa = isFa,
                    onSelectAssetClick = { onOpenPicker(PickerTarget.FROM) },
                    modifier = Modifier.testTag("converter_from_card")
                )

                ConverterSideCard(
                    roleLabel = strings.toLabel,
                    asset = toAsset,
                    displayValue = NumberFormatters.formatToman(outputValue, prefs.usePersianDigits),
                    isInput = false,
                    isFa = isFa,
                    onSelectAssetClick = { onOpenPicker(PickerTarget.TO) },
                    modifier = Modifier.testTag("converter_to_card")
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSwap)
                    .testTag("swap_currencies_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = strings.swapLabel,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(swapRotation)
                    )
                }
            }
        }

        // Unit reference line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.ratePerOnePrefix,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "1 ${fromAsset.symbol} = ${
                        NumberFormatters.formatToman(unitCrossRate, prefs.usePersianDigits)
                    } ${toAsset.symbol}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Quick Amount Presets
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf("1", "10", "100", "1000", "10000")
            items(presets) { preset ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, marketColors.hairline),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPresetClick(preset) }
                        .testTag("preset_$preset")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = NumberFormatters.applyDigits(
                                    preset.toDouble().let { NumberFormatters.formatToman(it, false) },
                                    prefs.usePersianDigits
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Live Equivalents Strip
        val benchmarkAssets = remember(assets, fromAsset.id, toAsset.id) {
            assets.filter {
                it.id in listOf("USD", "EUR", "GOLD_18K", "BTC") &&
                    it.id != fromAsset.id &&
                    it.id != toAsset.id
            }
        }
        if (benchmarkAssets.isNotEmpty() && tomanValue > 0.0) {
            Text(
                text = strings.quickEquivalentTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(benchmarkAssets, key = { it.id }) { item ->
                    val eqVal = if (item.priceToman > 0.0) tomanValue / item.priceToman else 0.0
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, marketColors.hairline)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${item.badgeText} ${item.symbol}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = NumberFormatters.formatToman(eqVal, prefs.usePersianDigits),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterSideCard(
    roleLabel: String,
    asset: MarketAsset,
    displayValue: String,
    isInput: Boolean,
    isFa: Boolean,
    onSelectAssetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isInput) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else marketColors.hairline
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onSelectAssetClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = asset.badgeText, fontSize = 20.sp)
                    Column {
                        Text(
                            text = asset.symbol,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFa) asset.nameFa else asset.nameEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isInput) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    usePersianDigits: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val haptic = LocalHapticFeedback.current

    val rows = listOf(
        listOf("7", "8", "9", "C"),
        listOf("4", "5", "6", "BACKSPACE"),
        listOf("1", "2", "3", "000"),
        listOf(".", "0")
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowKeys.forEach { key ->
                        val isAction = key == "C" || key == "BACKSPACE"
                        val weight = if (rowKeys.size == 2 && key == "0") 3f else 1f
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when (key) {
                                "C" -> marketColors.lossContainer
                                "BACKSPACE" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isAction) Color.Transparent else marketColors.hairline
                            ),
                            modifier = Modifier
                                .weight(weight)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKeyPress(key)
                                }
                                .testTag("keypad_$key")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (key == "BACKSPACE") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (key == "C" || key == ".") key
                                        else NumberFormatters.applyDigits(key, usePersianDigits),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (key == "C") marketColors.loss
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerSheet(
    choices: List<MarketAsset>,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onSelect: (MarketAsset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = strings.selectAssetTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(choices, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, marketColors.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(item) }
                            .testTag("picker_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = item.badgeText, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFa) item.nameFa else item.nameEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.symbol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.id != MarketViewModel.TOMAN_ASSET_ID) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "${
                                            NumberFormatters.formatToman(item.priceToman, usePersianDigits)
                                        } ${if (isFa) "ت" else "TMN"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRawInput(raw: String): String {
    val parts = raw.split(".")
    val intPart = parts.getOrNull(0)?.toLongOrNull()
    val formattedInt = if (intPart != null) {
        NumberFormatters.formatToman(intPart.toDouble(), false)
    } else {
        parts.getOrNull(0).orEmpty()
    }
    return if (parts.size > 1) "$formattedInt.${parts[1]}" else formattedInt
}
