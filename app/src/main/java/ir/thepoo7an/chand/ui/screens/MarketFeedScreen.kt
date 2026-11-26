package ir.thepoo7an.chand.ui.screens

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
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.RemoteLiveNotice
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.network.TomanRateBasis
import ir.thepoo7an.chand.ui.components.AssetBadge
import ir.thepoo7an.chand.ui.components.MiniSparkline
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.theme.LightBg
import ir.thepoo7an.chand.ui.theme.LightBgBottom
import ir.thepoo7an.chand.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketFeedScreen(
    uiState: MarketUiState,
    filteredAssets: List<MarketAsset>,
    prefs: AppPreferences,
    strings: ChandStrings,
    selectedCategory: AssetCategory,
    searchQuery: String,
    liveNotice: RemoteLiveNotice? = null,
    dismissedNoticeId: String = "",
    onDismissLiveNotice: (String) -> Unit = {},
    onSelectCategory: (AssetCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleGridView: (Boolean) -> Unit,
    onAssetClick: (MarketAsset) -> Unit,
    onAssetPriceClick: (MarketAsset) -> Unit = onAssetClick,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                        fontFamily = ir.thepoo7an.chand.ui.theme.InterFontFamily,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.6).sp
                    )
                    val isLivePulse = readyState?.freshness == DataFreshness.LIVE
                    val isDelayedPulse = readyState?.freshness == DataFreshness.DELAYED
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(
                                when {
                                    isLivePulse -> pulseAlpha
                                    isDelayedPulse -> 0.85f
                                    else -> 0.55f
                                }
                            )
                            .clip(CircleShape)
                            .background(
                                when {
                                    isLivePulse -> marketColors.gain
                                    isDelayedPulse -> marketColors.gold
                                    else -> marketColors.loss
                                }
                            )
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

            // Offline / Delayed Banner
            AnimatedVisibility(visible = readyState?.errorBanner != null) {
                val isDelayed = readyState?.errorBanner == "delayed"
                val bannerBg = if (isDelayed) marketColors.gold.copy(alpha = 0.14f) else marketColors.lossContainer
                val bannerFg = if (isDelayed) marketColors.gold else marketColors.loss
                Surface(
                    color = bannerBg,
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
                            tint = bannerFg,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (readyState?.errorBanner) {
                                "offline_reference" -> strings.offlineReferenceBanner
                                "delayed" -> strings.delayedBanner
                                else -> strings.offlineCachedBanner
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = bannerFg
                        )
                    }
                }
            }

            // Online Live Notice Banner (no APK download needed)
            val showLiveNotice = liveNotice != null &&
                liveNotice.id.isNotBlank() &&
                liveNotice.id != dismissedNoticeId
            AnimatedVisibility(visible = showLiveNotice) {
                liveNotice?.let { notice ->
                    val noticeBg = when (notice.severity) {
                        "WARNING" -> marketColors.gold.copy(alpha = 0.14f)
                        "SUCCESS" -> marketColors.gainContainer
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    }
                    val noticeFg = when (notice.severity) {
                        "WARNING" -> marketColors.gold
                        "SUCCESS" -> marketColors.gain
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val noticeTitle = if (isFa) notice.titleFa.ifBlank { notice.titleEn } else notice.titleEn.ifBlank { notice.titleFa }
                    val noticeBody = if (isFa) notice.bodyFa.ifBlank { notice.bodyEn } else notice.bodyEn.ifBlank { notice.bodyFa }
                    val actionLabel = if (isFa) notice.actionLabelFa.ifBlank { notice.actionLabelEn } else notice.actionLabelEn.ifBlank { notice.actionLabelFa }

                    Surface(
                        color = noticeBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, noticeFg.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("live_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (notice.severity == "SUCCESS") Icons.Outlined.CheckCircle else Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = noticeFg,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                if (noticeTitle.isNotBlank()) {
                                    Text(
                                        text = noticeTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (noticeBody.isNotBlank()) {
                                    Text(
                                        text = noticeBody,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (notice.actionUrl.isNotBlank() && actionLabel.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(notice.actionUrl))
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (notice.dismissible) {
                                IconButton(
                                    onClick = { onDismissLiveNotice(notice.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dismiss_live_notice_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = strings.laterButton,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
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

            val localizedSummary = TomanRateBasis.localizeSummary(
                summary = readyState?.sourceSummary.orEmpty(),
                isFa = isFa
            )
            if (localizedSummary.isNotBlank()) {
                Text(
                    text = "${strings.sourceLabel}: $localizedSummary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 2.dp)
                        .testTag("rate_basis_summary")
                )
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
                                text = strings.networkUnavailableBanner,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
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
                                            strings = strings,
                                            onClick = { onAssetClick(asset) },
                                            onPriceClick = { onAssetPriceClick(asset) },
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
                                        onPriceClick = { onAssetPriceClick(asset) },
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
    strings: ChandStrings,
    onClick: () -> Unit,
    onPriceClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val marketColors = LocalMarketColors.current
    val haptic = LocalHapticFeedback.current
    val isOilUsd = asset.isUsdPerBarrel
    val isGain = asset.change24hPercent >= 0.0
    val trendColor = if (isOilUsd && !asset.isChange24hAvailable) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (isGain) {
        marketColors.gain
    } else {
        marketColors.loss
    }
    val isUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
    val statusBadgeText = when {
        asset.disputed -> strings.disputedLabel
        asset.freshness == DataFreshness.DELAYED -> strings.delayedBadge
        asset.freshness == DataFreshness.CACHED -> strings.cachedBadge
        asset.freshness == DataFreshness.UNAVAILABLE -> strings.unavailableBadge
        else -> null
    }

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
            .height(if (isOilUsd) 182.dp else 170.dp)
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
            if (asset.sparkline.size >= 2) {
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
            }

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

                // Bottom Block: Arrow Delta + Huge Bold Tabular Price (tappable for Source Breakdown)
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClickLabel = strings.sourceTapHint,
                            onClick = onPriceClick
                        )
                        .minimumInteractiveComponentSize()
                        .testTag("asset_price_${asset.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val deltaLabel = when {
                                isUnavailable -> "—"
                                isOilUsd && !asset.isChange24hAvailable -> strings.change24hUnavailable
                                isOilUsd -> NumberFormatters.formatPercent(asset.change24hPercent, usePersianDigits)
                                else -> NumberFormatters.formatDeltaWithArrow(
                                    asset.change24hToman,
                                    asset.change24hPercent,
                                    usePersianDigits
                                )
                            }
                            Text(
                                text = deltaLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUnavailable) MaterialTheme.colorScheme.onSurfaceVariant else trendColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        if (statusBadgeText != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (asset.disputed) marketColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        } else if (isOilUsd) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.liveBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = marketColors.gain,
                                maxLines = 1
                            )
                        } else if (asset.category == AssetCategory.CRYPTO && asset.priceUsd != null) {
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
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = when {
                                    isUnavailable -> "—"
                                    isOilUsd -> NumberFormatters.formatUsd(asset.priceUsd ?: 0.0, usePersianDigits)
                                    else -> NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)
                                },
                                fontSize = if (isOilUsd) 24.sp else 27.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                color = if (isUnavailable) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isOilUsd && !isUnavailable) {
                                Text(
                                    text = strings.usdPerBarrelShortUnit,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (isOilUsd) {
                        val metaLine = when {
                            !isUnavailable && asset.priceToman > 0.0 ->
                                "${strings.oilTomanEstimatePrefix} ${NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)} ${strings.tomanUnit}"
                            asset.sourceLabel.isNotBlank() -> asset.sourceLabel
                            else -> ""
                        }
                        if (metaLine.isNotEmpty()) {
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
    onPriceClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val isOilUsd = asset.isUsdPerBarrel
    val isGain = asset.change24hPercent >= 0.0
    val hasDisplayChange = !isOilUsd || asset.isChange24hAvailable
    val trendColor = if (!hasDisplayChange) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (isGain) {
        marketColors.gain
    } else {
        marketColors.loss
    }
    val trendBg = if (!hasDisplayChange) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (isGain) {
        marketColors.gainContainer
    } else {
        marketColors.lossContainer
    }

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

            val rowStatusText = when {
                asset.disputed -> strings.disputedLabel
                asset.freshness == DataFreshness.DELAYED -> strings.delayedBadge
                asset.freshness == DataFreshness.CACHED -> strings.cachedBadge
                asset.freshness == DataFreshness.UNAVAILABLE -> strings.unavailableBadge
                isOilUsd -> strings.liveBadge
                else -> null
            }

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
                    if (rowStatusText != null) {
                        Text(
                            text = rowStatusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (asset.disputed) marketColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (isOilUsd) {
                    val subEstimate = when {
                        asset.freshness != DataFreshness.UNAVAILABLE && asset.priceToman > 0.0 ->
                            "${strings.oilTomanEstimatePrefix} ${NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)} ${strings.tomanUnit}"
                        asset.sourceLabel.isNotBlank() -> asset.sourceLabel
                        else -> ""
                    }
                    if (subEstimate.isNotEmpty()) {
                        Text(
                            text = subEstimate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (asset.sparkline.size >= 2) {
                MiniSparkline(
                    points = asset.sparkline,
                    lineColor = trendColor,
                    modifier = Modifier
                        .width(44.dp)
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        onClickLabel = strings.sourceTapHint,
                        onClick = onPriceClick
                    )
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("asset_price_${asset.id}")
            ) {
                val isUnavailableRow = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                isUnavailableRow -> "—"
                                isOilUsd -> NumberFormatters.formatUsd(asset.priceUsd ?: 0.0, usePersianDigits)
                                else -> NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnavailableRow) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (isOilUsd && !isUnavailableRow) {
                            Text(
                                text = strings.usdPerBarrelShortUnit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isUnavailableRow) MaterialTheme.colorScheme.surfaceVariant else trendBg
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = when {
                                isUnavailableRow -> strings.unavailableBadge
                                isOilUsd && !asset.isChange24hAvailable -> strings.change24hUnavailable
                                isOilUsd -> NumberFormatters.formatPercent(asset.change24hPercent, usePersianDigits)
                                else -> NumberFormatters.formatDeltaWithArrow(
                                    asset.change24hToman,
                                    asset.change24hPercent,
                                    usePersianDigits
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnavailableRow) MaterialTheme.colorScheme.onSurfaceVariant else trendColor,
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
