package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AssetCategory
import com.example.model.MarketAsset
import com.example.model.MarketUiState
import com.example.model.ThemeMode
import com.example.ui.components.AssetBadge
import com.example.ui.components.MiniSparkline
import com.example.ui.strings.ChandStrings
import com.example.ui.strings.NumberFormatters
import com.example.ui.theme.LightBg
import com.example.ui.theme.LightBgBottom
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
    onToggleGridView: (Boolean) -> Unit,
    onAssetClick: (MarketAsset) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val isFa = prefs.language == AppLanguage.FA
    val readyState = uiState as? MarketUiState.Ready
    val isRefreshing = readyState?.isRefreshing == true
    var showSearchBar by remember { mutableStateOf(false) }

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

    val bgBrush = when (prefs.themeMode) {
        ThemeMode.LIGHT -> Brush.verticalGradient(listOf(LightBg, LightBgBottom))
        ThemeMode.DARK -> Brush.verticalGradient(listOf(Color(0xFF0E131B), Color(0xFF111824)))
        ThemeMode.AMOLED -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(bgBrush) },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Chand?! Hero Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = strings.appTitle,
                        fontFamily = com.example.ui.theme.InterFontFamily,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.6).sp
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (readyState?.errorBanner == null) pulseAlpha else 0.45f)
                            .clip(CircleShape)
                            .background(if (readyState?.errorBanner == null) marketColors.gain else marketColors.gold)
                    )
                }

                // Jalali Date & Time aligned opposite title like Chand?!
                Column(horizontalAlignment = Alignment.End) {
                    val timestampMillis = readyState?.lastUpdatedMillis?.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatJalaliDateTime(
                                timestampMillis,
                                prefs.usePersianDigits
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (isRefreshing) {
                        Text(
                            text = strings.updating,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) onSearchQueryChange("")
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = strings.searchPlaceholder,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { onToggleGridView(!prefs.isGridView) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_layout_button")
                ) {
                    Icon(
                        imageVector = if (prefs.isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                        contentDescription = strings.layoutSection,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Collapsible Search Bar
            AnimatedVisibility(visible = showSearchBar) {
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
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = marketColors.hairline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("market_search_input")
                )
            }

            // Offline Banner
            AnimatedVisibility(visible = readyState?.errorBanner != null) {
                Surface(
                    color = marketColors.lossContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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

            // Category Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AssetCategory.entries) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.onBackground else marketColors.hairline
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(14.dp))
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
                                color = if (selected) MaterialTheme.colorScheme.background
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Market Feed Content
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
                        } else if (prefs.isGridView) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = if (maxWidth >= 620.dp) 3 else 2
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 6.dp,
                                        bottom = 104.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredAssets, key = { it.id }) { asset ->
                                        ChandBentoCard(
                                            asset = asset,
                                            isPinned = asset.id in prefs.pinnedIds,
                                            isFa = isFa,
                                            usePersianDigits = prefs.usePersianDigits,
                                            onClick = { onAssetClick(asset) },
                                            onLongClick = { onTogglePin(asset.id) }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 6.dp,
                                    bottom = 104.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredAssets, key = { it.id }) { asset ->
                                    MarketAssetListRow(
                                        asset = asset,
                                        isPinned = asset.id in prefs.pinnedIds,
                                        isFa = isFa,
                                        usePersianDigits = prefs.usePersianDigits,
                                        strings = strings,
                                        onClick = { onAssetClick(asset) },
                                        onTogglePin = { onTogglePin(asset.id) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChandBentoCard(
    asset: MarketAsset,
    isPinned: Boolean,
    isFa: Boolean,
    usePersianDigits: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val marketColors = LocalMarketColors.current
    val haptic = LocalHapticFeedback.current
    val isGain = asset.change24hPercent >= 0.0
    val trendColor = if (isGain) marketColors.gain else marketColors.loss

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (marketColors.isAmoled) 0.dp else 1.5.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (marketColors.isAmoled) marketColors.hairline
            else if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else marketColors.hairline.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .testTag("asset_row_${asset.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle atmospheric micro-sparkline along lower mid
            MiniSparkline(
                points = asset.sparkline,
                lineColor = trendColor.copy(alpha = 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 14.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Badge on start, Name & Ticker on end (mirroring Chand?!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AssetBadge(
                        assetId = asset.id,
                        category = asset.category,
                        fallbackText = asset.badgeText,
                        accentHex = asset.accentHex,
                        size = 38.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isFa) asset.nameFa else asset.nameEn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = asset.symbol,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Bottom Block: Arrow Delta + Huge Bold Tabular Price
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = NumberFormatters.formatDeltaWithArrow(
                                    asset.change24hToman,
                                    asset.change24hPercent,
                                    usePersianDigits
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = trendColor
                            )
                        }

                        if (asset.category == AssetCategory.CRYPTO && asset.priceUsd != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = NumberFormatters.formatUsd(asset.priceUsd, usePersianDigits),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 32.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketAssetListRow(
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else marketColors.hairline
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("asset_row_${asset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssetBadge(
                assetId = asset.id,
                category = asset.category,
                fallbackText = asset.badgeText,
                accentHex = asset.accentHex,
                size = 40.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

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
                    }
                }
            }

            MiniSparkline(
                points = asset.sparkline,
                lineColor = trendColor,
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(horizontalAlignment = Alignment.End) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = trendBg
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = NumberFormatters.formatDeltaWithArrow(
                                asset.change24hToman,
                                asset.change24hPercent,
                                usePersianDigits
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

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
