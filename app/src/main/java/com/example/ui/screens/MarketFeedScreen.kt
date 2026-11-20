package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.MiniSparkline
import com.example.ui.strings.ChandStrings
import com.example.ui.strings.NumberFormatters
import com.example.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketFeedScreen(
    uiState: MarketUiState,
    filteredAssets: List<MarketAsset>,
    prefs: AppPreferences,
    strings: ChandStrings,
    selectedCategory: AssetCategory,
    searchQuery: String,
    onSelectCategory: (AssetCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onAssetClick: (MarketAsset) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val isFa = prefs.language == AppLanguage.FA
    val readyState = uiState as? MarketUiState.Ready
    val isRefreshing = readyState?.isRefreshing == true

    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_spin"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = marketColors.gainContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .alpha(if (readyState?.errorBanner == null) pulseAlpha else 0.5f)
                                    .clip(CircleShape)
                                    .background(if (readyState?.errorBanner == null) marketColors.gain else marketColors.gold)
                            )
                            Text(
                                text = if (isRefreshing) strings.updating else strings.liveBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (readyState?.errorBanner == null) marketColors.gain else marketColors.gold
                            )
                        }
                    }
                }
                if (readyState != null && readyState.lastUpdatedMillis > 0L) {
                    val timeStr = NumberFormatters.formatTime(readyState.lastUpdatedMillis, prefs.usePersianDigits)
                    val metaText = if (readyState.sourceSummary.isNotEmpty()) {
                        "${strings.lastUpdatePrefix} $timeStr • ${readyState.sourceSummary}"
                    } else {
                        "${strings.lastUpdatePrefix} $timeStr"
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = strings.retryLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = if (isRefreshing) Modifier.rotate(refreshRotation) else Modifier
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = strings.settingsTitle,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Offline / Cached Notice
        AnimatedVisibility(visible = readyState?.errorBanner != null) {
            Surface(
                color = marketColors.lossContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = marketColors.loss,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.offlineCachedBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = marketColors.loss
                    )
                }
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = strings.searchPlaceholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = marketColors.hairline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("market_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AssetCategory.entries) { category ->
                val selected = category == selectedCategory
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else marketColors.hairline
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCategory(category) }
                        .testTag("category_${category.name}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = strings.categoryLabel(category),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Content State
        when (uiState) {
            is MarketUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = strings.updating,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is MarketUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = strings.offlineCachedBanner,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = onRefreshClick,
                            modifier = Modifier.testTag("error_retry_button")
                        ) {
                            Text(text = strings.retryLabel)
                        }
                    }
                }
            }

            is MarketUiState.Ready -> {
                val pinnedAssets = uiState.assets.filter { it.id in prefs.pinnedIds }
                val showPinnedStrip = selectedCategory == AssetCategory.ALL &&
                    searchQuery.isBlank() &&
                    pinnedAssets.isNotEmpty()

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefreshClick,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredAssets.isEmpty()) {
                        EmptyMarketState(
                            isPinnedEmpty = selectedCategory == AssetCategory.PINNED && searchQuery.isBlank(),
                            strings = strings
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (showPinnedStrip) {
                                item(key = "pinned_header") {
                                    Text(
                                        text = strings.pinnedSectionTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                                    )
                                }
                                item(key = "pinned_strip") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(pinnedAssets, key = { "pin_card_${it.id}" }) { asset ->
                                            PinnedWatchCard(
                                                asset = asset,
                                                isFa = isFa,
                                                usePersianDigits = prefs.usePersianDigits,
                                                strings = strings,
                                                onClick = { onAssetClick(asset) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                item(key = "all_markets_header") {
                                    Text(
                                        text = strings.allMarketsTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            items(filteredAssets, key = { it.id }) { asset ->
                                MarketAssetRow(
                                    asset = asset,
                                    isPinned = asset.id in prefs.pinnedIds,
                                    isFa = isFa,
                                    usePersianDigits = prefs.usePersianDigits,
                                    strings = strings,
                                    onClick = { onAssetClick(asset) },
                                    onTogglePin = { onTogglePin(asset.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
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
private fun PinnedWatchCard(
    asset: MarketAsset,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onClick: () -> Unit
) {
    val marketColors = LocalMarketColors.current
    val isGain = asset.change24hPercent >= 0.0
    val trendColor = if (isGain) marketColors.gain else marketColors.loss
    val trendBg = if (isGain) marketColors.gainContainer else marketColors.lossContainer

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, marketColors.hairline),
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("pinned_card_${asset.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = asset.badgeText, fontSize = 18.sp)
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = trendBg
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatPercent(asset.change24hPercent, usePersianDigits),
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            MiniSparkline(
                points = asset.sparkline,
                lineColor = trendColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Column {
                Text(
                    text = if (isFa) asset.nameFa else asset.nameEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatToman(asset.priceToman, usePersianDigits),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = strings.tomanUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketAssetRow(
    asset: MarketAsset,
    isPinned: Boolean,
    isFa: Boolean,
    usePersianDigits: Boolean,
    strings: ChandStrings,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val isGain = asset.change24hPercent >= 0.0
    val trendColor = if (isGain) marketColors.gain else marketColors.loss
    val trendBg = if (isGain) marketColors.gainContainer else marketColors.lossContainer

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else marketColors.hairline
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("asset_row_${asset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(asset.accentHex).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = asset.badgeText, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isFa) asset.nameFa else asset.nameEn,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (asset.category == AssetCategory.CRYPTO && asset.priceUsd != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = NumberFormatters.formatUsd(asset.priceUsd, usePersianDigits),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (asset.sourceLabel.isNotEmpty()) {
                        Text(
                            text = "• ${asset.sourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }

            // Mini Sparkline
            MiniSparkline(
                points = asset.sparkline,
                lineColor = trendColor,
                modifier = Modifier
                    .width(46.dp)
                    .height(24.dp)
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Price & Change
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatToman(asset.priceToman, usePersianDigits),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isFa) "ت" else "TMN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = trendBg
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatPercent(asset.change24hPercent, usePersianDigits),
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Pin Button (48dp touch target)
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pin_button_${asset.id}")
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) strings.unpinAsset else strings.pinAsset,
                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyMarketState(
    isPinnedEmpty: Boolean,
    strings: ChandStrings
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isPinnedEmpty) Icons.Outlined.PushPin else Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (isPinnedEmpty) strings.emptyPinnedTitle else strings.emptySearchTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isPinnedEmpty) strings.emptyPinnedSubtitle else strings.emptySearchSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
