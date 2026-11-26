package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.RemoteFeaturesConfig
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailSheet(
    asset: MarketAsset,
    isPinned: Boolean,
    historyPoints: List<AssetHistoryPoint>,
    historySourceLabel: String,
    isHistoryLoading: Boolean,
    selectedRange: TimeRange,
    prefs: AppPreferences,
    strings: ChandStrings,
    features: RemoteFeaturesConfig = RemoteFeaturesConfig(),
    onSelectRange: (TimeRange) -> Unit,
    onRetryHistory: () -> Unit,
    onTogglePin: () -> Unit,
    onConvertClick: () -> Unit,
    onSourceBreakdownClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val marketColors = LocalMarketColors.current
    var scrubbedIndex by remember(historyPoints) { mutableStateOf<Int?>(null) }

    val isOilUsd = asset.isUsdPerBarrel
    val activePoint = scrubbedIndex?.let { idx -> historyPoints.getOrNull(idx) }
    val displayPrimaryPrice = activePoint?.priceToman ?: asset.primaryPriceValue

    val (rangeChangePrimary, rangeChangePct, hasDisplayChange) = remember(
        historyPoints,
        asset.change24hToman,
        asset.change24hUsd,
        asset.change24hPercent,
        asset.isChange24hAvailable,
        scrubbedIndex,
        isOilUsd
    ) {
        if (historyPoints.size >= 2) {
            val first = historyPoints.first().priceToman
            val target = activePoint?.priceToman ?: historyPoints.last().priceToman
            val diff = target - first
            val pct = if (first > 0.0) (diff / first) * 100.0 else asset.change24hPercent
            Triple(diff, pct, true)
        } else if (isOilUsd) {
            Triple(asset.change24hUsd ?: 0.0, asset.change24hPercent, asset.isChange24hAvailable)
        } else {
            Triple(asset.change24hToman, asset.change24hPercent, asset.isChange24hAvailable)
        }
    }

    val (rangeHigh, rangeLow) = remember(
        historyPoints,
        selectedRange,
        asset.high24hToman,
        asset.low24hToman,
        asset.high24hUsd,
        asset.low24hUsd,
        isOilUsd
    ) {
        val assetHigh = if (isOilUsd) asset.high24hUsd else asset.high24hToman
        val assetLow = if (isOilUsd) asset.low24hUsd else asset.low24hToman
        if (historyPoints.size >= 2) {
            val maxHist = historyPoints.maxOf { it.priceToman }
            val minHist = historyPoints.minOf { it.priceToman }
            if (selectedRange == TimeRange.H24) {
                listOfNotNull(maxHist, assetHigh).maxOrNull() to
                    listOfNotNull(minHist, assetLow).minOrNull()
            } else {
                maxHist to minHist
            }
        } else {
            assetHigh to assetLow
        }
    }

    val isPositive = rangeChangePct >= 0.0
    val trendColor = if (!hasDisplayChange) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (isPositive) {
        marketColors.gain
    } else {
        marketColors.loss
    }
    val trendBg = if (!hasDisplayChange) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (isPositive) {
        marketColors.gainContainer
    } else {
        marketColors.lossContainer
    }
    val isFa = prefs.language == AppLanguage.FA

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
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssetBadge(
                    assetId = asset.id,
                    category = asset.category,
                    fallbackText = asset.badgeText,
                    accentHex = asset.accentHex,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isFa) asset.nameFa else asset.nameEn,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_pin_button")
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) strings.unpinAsset else strings.pinAsset,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClickLabel = strings.sourceTapHint,
                            onClick = onSourceBreakdownClick
                        )
                        .minimumInteractiveComponentSize()
                        .testTag("detail_hero_price_trigger")
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val isUnavailablePrice = activePoint == null &&
                            (asset.freshness == DataFreshness.UNAVAILABLE || displayPrimaryPrice <= 0.0)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = when {
                                    isUnavailablePrice -> "—"
                                    isOilUsd -> NumberFormatters.formatUsd(displayPrimaryPrice, prefs.usePersianDigits)
                                    else -> NumberFormatters.formatToman(displayPrimaryPrice, prefs.usePersianDigits)
                                },
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!isUnavailablePrice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOilUsd) strings.usdPerBarrelUnit else strings.tomanUnit,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                    }
                    val subtitleText = when {
                        activePoint != null -> NumberFormatters.formatChartJalaliDate(
                            timestampSec = activePoint.timestampSec,
                            usePersianDigits = prefs.usePersianDigits,
                            includeTime = selectedRange == TimeRange.H24 || selectedRange == TimeRange.D7,
                            includeYear = true
                        )
                        isOilUsd && asset.priceToman > 0.0 ->
                            "${strings.oilTomanEstimatePrefix} ${NumberFormatters.formatToman(asset.priceToman, prefs.usePersianDigits)} ${strings.tomanUnit}"
                        isOilUsd -> strings.chartInteractiveHint
                        else -> asset.priceUsd?.let {
                            NumberFormatters.formatUsd(it, prefs.usePersianDigits)
                        } ?: strings.chartInteractiveHint
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (activePoint != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = trendBg
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val badgeText = if (!hasDisplayChange) {
                            strings.change24hUnavailable
                        } else if (isOilUsd) {
                            val deltaStr = NumberFormatters.formatUsdDeltaWithArrow(
                                rangeChangePrimary,
                                rangeChangePct,
                                prefs.usePersianDigits
                            )
                            val pctStr = NumberFormatters.formatPercent(rangeChangePct, prefs.usePersianDigits)
                            "$deltaStr ($pctStr)"
                        } else {
                            val deltaStr = NumberFormatters.formatDeltaWithArrow(
                                rangeChangePrimary,
                                rangeChangePct,
                                prefs.usePersianDigits
                            )
                            val pctStr = NumberFormatters.formatPercent(rangeChangePct, prefs.usePersianDigits)
                            "$deltaStr ($pctStr)"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = trendColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Chart Container + Real Date Span Footer
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, marketColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            !features.enableHistoricalCharts -> {
                                Text(
                                    text = strings.chartsDisabledNotice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            isHistoryLoading && historyPoints.isEmpty() -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            historyPoints.size < 2 -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = strings.historyUnavailable,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = onRetryHistory) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = strings.retryLabel)
                                    }
                                }
                            }
                            else -> {
                                InteractiveHistoryChart(
                                    points = historyPoints,
                                    lineColor = trendColor,
                                    gridColor = marketColors.hairline,
                                    selectedIndex = scrubbedIndex,
                                    onSelectIndex = { scrubbedIndex = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }

                    if (historyPoints.size >= 2) {
                        val firstPt = historyPoints.first()
                        val lastPt = historyPoints.last()
                        val showYear = selectedRange == TimeRange.Y1 || selectedRange == TimeRange.M1
                        val showTime = selectedRange == TimeRange.H24

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = NumberFormatters.formatChartJalaliDate(
                                        firstPt.timestampSec,
                                        prefs.usePersianDigits,
                                        includeTime = showTime,
                                        includeYear = showYear
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = NumberFormatters.formatChartJalaliDate(
                                        historyPoints[historyPoints.size / 2].timestampSec,
                                        prefs.usePersianDigits,
                                        includeTime = showTime,
                                        includeYear = showYear
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = NumberFormatters.formatChartJalaliDate(
                                        lastPt.timestampSec,
                                        prefs.usePersianDigits,
                                        includeTime = showTime,
                                        includeYear = showYear
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TimeRange Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeRange.entries.forEach { range ->
                    val selected = range == selectedRange
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectRange(range) }
                            .testTag("range_${range.name}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isFa) range.labelFa else range.labelEn,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Range High / Low / Real History Source
            val statusNotes = buildList {
                when (asset.freshness) {
                    DataFreshness.DELAYED -> add(strings.delayedBadge)
                    DataFreshness.CACHED -> add(strings.cachedBadge)
                    DataFreshness.UNAVAILABLE -> add(strings.unavailableBadge)
                    DataFreshness.LIVE -> Unit
                }
                if (asset.disputed) add(strings.disputedLabel)
                else if (asset.singleSource && historySourceLabel.isEmpty()) add(strings.singleSourceLabel)
            }
            val baseSource = historySourceLabel.ifEmpty { asset.sourceLabel.ifEmpty { strings.liveBadge } }
            val sourceDisplay = if (statusNotes.isEmpty()) baseSource else "$baseSource · ${statusNotes.joinToString(" · ")}"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPillCard(
                    label = if (selectedRange == TimeRange.H24) strings.high24h else strings.rangeHighLabel,
                    value = rangeHigh?.let {
                        if (isOilUsd) "${NumberFormatters.formatUsd(it, prefs.usePersianDigits)} ${strings.usdPerBarrelShortUnit}"
                        else NumberFormatters.formatToman(it, prefs.usePersianDigits)
                    } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                StatPillCard(
                    label = if (selectedRange == TimeRange.H24) strings.low24h else strings.rangeLowLabel,
                    value = rangeLow?.let {
                        if (isOilUsd) "${NumberFormatters.formatUsd(it, prefs.usePersianDigits)} ${strings.usdPerBarrelShortUnit}"
                        else NumberFormatters.formatToman(it, prefs.usePersianDigits)
                    } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                StatPillCard(
                    label = strings.sourceLabel,
                    value = sourceDisplay,
                    onClick = onSourceBreakdownClick,
                    modifier = Modifier
                        .weight(0.95f)
                        .testTag("detail_source_pill_trigger")
                )
            }

            if (features.enableCoinBubbleEstimate && asset.intrinsicValueToman != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val bubbleDisplay = if (asset.bubbleToman != null && asset.bubblePercent != null) {
                    val amt = NumberFormatters.formatToman(asset.bubbleToman, prefs.usePersianDigits)
                    val pct = NumberFormatters.formatPercent(asset.bubblePercent, prefs.usePersianDigits)
                    "$amt ($pct)"
                } else {
                    "—"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPillCard(
                        label = strings.intrinsicEstimateLabel,
                        value = NumberFormatters.formatToman(asset.intrinsicValueToman, prefs.usePersianDigits),
                        modifier = Modifier.weight(1f)
                    )
                    StatPillCard(
                        label = strings.bubbleEstimateLabel,
                        value = bubbleDisplay,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConvertClick,
                enabled = asset.priceToman > 0.0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("detail_convert_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.convertThisAsset,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun StatPillCard(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else marketColors.hairline
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .minimumInteractiveComponentSize()
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
