package ir.thepoo7an.chand.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketSourceBreakdownSheet(
    asset: MarketAsset,
    prefs: AppPreferences,
    strings: ChandStrings,
    onDismiss: () -> Unit,
    referenceNowMillis: Long = System.currentTimeMillis()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isFa = prefs.language == AppLanguage.FA
    val model = remember(asset, isFa, prefs.usePersianDigits, strings, referenceNowMillis) {
        MarketSourceBreakdownBuilder.build(
            asset = asset,
            isFa = isFa,
            usePersianDigits = prefs.usePersianDigits,
            strings = strings,
            referenceNowMillis = referenceNowMillis
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        MarketSourceBreakdownContent(
            asset = asset,
            model = model,
            usePersianDigits = prefs.usePersianDigits,
            strings = strings,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun MarketSourceBreakdownContent(
    asset: MarketAsset,
    model: MarketSourceBreakdownModel,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current

    val (freshnessBg, freshnessFg) = when (model.freshness) {
        DataFreshness.LIVE -> marketColors.gainContainer to marketColors.gain
        DataFreshness.DELAYED -> marketColors.gold.copy(alpha = 0.16f) to marketColors.gold
        DataFreshness.CACHED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DataFreshness.UNAVAILABLE -> marketColors.lossContainer to marketColors.loss
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("source_breakdown_sheet"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // Sheet Title & Asset Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssetBadge(
                    assetId = asset.id,
                    category = asset.category,
                    fallbackText = asset.badgeText,
                    accentHex = asset.accentHex,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.sourceBreakdownTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.displayName} (${model.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("source_breakdown_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = strings.laterButton,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.sourceBreakdownSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Aggregated Price Summary Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, marketColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.priceKindLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (model.disputed) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = marketColors.gold.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = strings.disputedLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = marketColors.gold,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = freshnessBg,
                                modifier = Modifier
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("source_breakdown_freshness")
                            ) {
                                Text(
                                    text = model.freshnessLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = freshnessFg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = model.mainPriceText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("source_breakdown_main_price")
                            )
                        }
                        if (model.mainPriceText != "—") {
                            Text(
                                text = model.unitCurrencyText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (model.mainCardPriceText != model.mainPriceText) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "(${model.mainCardPriceText})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = marketColors.hairline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = model.sourceAttributionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.lastSuccessfulUpdateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = model.lastSuccessfulUpdateText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (model.hasLastSuccessfulUpdate) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("source_breakdown_last_updated")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val countText = NumberFormatters.applyDigits(model.rows.size.toString(), usePersianDigits)
            Text(
                text = "${strings.configuredSourcesCountPrefix} $countText",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .testTag("source_breakdown_list"),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (model.rows.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, marketColors.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("source_breakdown_empty")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = strings.sourceNoRecordsNotice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = model.rows,
                        key = { "${it.sourceName}_${it.status.name}_${it.sourceTimestampMillis}" }
                    ) { row ->
                        SourceCandidateBreakdownCard(
                            row = row,
                            strings = strings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCandidateBreakdownCard(
    row: SourceBreakdownRow,
    strings: ChandStrings
) {
    val marketColors = LocalMarketColors.current

    val cardContainerColor: Color
    val cardBorderColor: Color
    val statusBg: Color
    val statusFg: Color
    val contributionIcon: androidx.compose.ui.graphics.vector.ImageVector

    when (row.status) {
        SourceBreakdownStatus.INCLUDED -> {
            cardContainerColor = marketColors.gainContainer.copy(alpha = 0.28f)
            cardBorderColor = marketColors.gain.copy(alpha = 0.38f)
            statusBg = marketColors.gainContainer
            statusFg = marketColors.gain
            contributionIcon = Icons.Outlined.CheckCircle
        }
        SourceBreakdownStatus.DELAYED -> {
            cardContainerColor = marketColors.gold.copy(alpha = 0.08f)
            cardBorderColor = marketColors.gold.copy(alpha = 0.42f)
            statusBg = marketColors.gold.copy(alpha = 0.16f)
            statusFg = marketColors.gold
            contributionIcon = Icons.Outlined.AccessTime
        }
        SourceBreakdownStatus.REJECTED -> {
            cardContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
            cardBorderColor = marketColors.loss.copy(alpha = 0.32f)
            statusBg = marketColors.lossContainer
            statusFg = marketColors.loss
            contributionIcon = Icons.Outlined.Block
        }
        SourceBreakdownStatus.INVALID -> {
            cardContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
            cardBorderColor = marketColors.loss.copy(alpha = 0.42f)
            statusBg = marketColors.lossContainer
            statusFg = marketColors.loss
            contributionIcon = Icons.Outlined.ErrorOutline
        }
        SourceBreakdownStatus.UNAVAILABLE -> {
            cardContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            cardBorderColor = marketColors.hairline
            statusBg = MaterialTheme.colorScheme.surfaceVariant
            statusFg = MaterialTheme.colorScheme.onSurfaceVariant
            contributionIcon = Icons.Outlined.CloudOff
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardContainerColor,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source_row_${row.sourceName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Source name + Primary badge + Status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (row.isPrimary) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = strings.sourcePrimaryTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag("source_status_${row.sourceName}")
                ) {
                    Text(
                        text = row.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Row 2: Reported Price + Unit & Currency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = row.reportedPriceText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (row.reportedPriceText == "—") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    Text(
                        text = row.unitCurrencyText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                row.rawQuoteText?.let { raw ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Row 3: Source Timestamp & Fetch Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.sourceTimestampLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = row.timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.hasValidTimestamp) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            marketColors.gold
                        }
                    )
                }
            }
            if (row.hasValidFetchedAt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.fetchedAtTimestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = row.fetchedAtText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("source_fetched_at_${row.sourceName}")
                        )
                    }
                }
            }

            // Row 4: Contribution indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = contributionIcon,
                    contentDescription = null,
                    tint = statusFg,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = row.contributionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.contributedToFinal) statusFg else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (row.contributedToFinal) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Row 5: Exclusion Reason (when applicable)
            row.exclusionReasonText?.let { reasonText ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusFg.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reasonText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.status == SourceBreakdownStatus.UNAVAILABLE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            statusFg
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
