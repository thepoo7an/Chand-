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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
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
import ir.thepoo7an.chand.network.FreshnessAgeTier
import ir.thepoo7an.chand.network.FreshnessTimeFormatter
import ir.thepoo7an.chand.network.MarketComparisonHelper
import ir.thepoo7an.chand.network.TomanRateBasis
import ir.thepoo7an.chand.ui.components.AssetBadge
import ir.thepoo7an.chand.ui.components.AssetTrendPresentation
import ir.thepoo7an.chand.ui.components.MarketSourcesSheet
import ir.thepoo7an.chand.ui.components.MarketSourcesSummary
import ir.thepoo7an.chand.ui.components.MiniSparkline
import ir.thepoo7an.chand.ui.strings.ChandStrings
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.ProviderLabels
import ir.thepoo7an.chand.ui.theme.InterFontFamily
import ir.thepoo7an.chand.ui.theme.LightBg
import ir.thepoo7an.chand.ui.theme.LightBgBottom
import ir.thepoo7an.chand.ui.theme.LocalMarketColors
import ir.thepoo7an.chand.ui.theme.VazirmatnFontFamily
import ir.thepoo7an.chand.ui.theme.resolvePriceTrend

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
    activeAlertCount: Int = 0,
    onAlertsClick: () -> Unit = {},
    referenceNowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val marketColors = LocalMarketColors.current
    val isFa = prefs.language == AppLanguage.FA
    val readyState = uiState as? MarketUiState.Ready
    // One provider list for the whole screen: the meta row shows only its size, the info sheet shows
    // every provider with its fetch time and the assets it feeds.
    val allFeedAssets = readyState?.assets.orEmpty()
    val providerGroups = remember(allFeedAssets, isFa, strings) {
        MarketSourcesSummary.build(allFeedAssets, isFa = isFa, strings = strings)
    }
    val providerCount = providerGroups.size
    val isRefreshing = readyState?.isRefreshing == true
    var showSearchBar by remember { mutableStateOf(false) }
    var showSourcesSheet by remember { mutableStateOf(false) }

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
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
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
                        fontFamily = if (isFa) VazirmatnFontFamily else InterFontFamily,
                        fontSize = headerTitleFontSize(
                            isFa = isFa,
                            screenWidthDp = screenWidthDp
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = if (isFa) 0.sp else (-0.6).sp,
                        // The brand word must never break mid-word ("Nerkhna / ma"): one line, no wrap.
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("header_title")
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

                // Calendar date only: the clock used to repeat the status-bar time and ate the width the
                // title needed. On narrow screens the date column is dropped entirely and the functional
                // "Last sync" time is shown in the meta row instead.
                if (screenWidthDp >= 360) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.testTag("header_date")
                    ) {
                        val timestampMillis = (readyState?.lastUpdatedMillis ?: 0L).takeIf { it > 0L }
                            ?: System.currentTimeMillis()
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = NumberFormatters.formatChartJalaliDate(
                                    timestampSec = timestampMillis / 1000L,
                                    usePersianDigits = prefs.usePersianDigits,
                                    includeTime = false,
                                    includeYear = true
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.testTag("header_date_text")
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
                }

                IconButton(
                    onClick = onAlertsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("header_alerts_button")
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = strings.alertsTitle,
                            tint = if (activeAlertCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (activeAlertCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

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
            // The row scrolls, so the 16dp content padding is what keeps the first and the last chip
            // away from the screen edge instead of being cut in half by it.
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("category_row")
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
                            modifier = Modifier.padding(horizontal = 12.dp)
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

            val localizedSummary = ProviderLabels.shortSummary(
                summary = TomanRateBasis.localizeSummary(
                    summary = readyState?.sourceSummary.orEmpty(),
                    isFa = isFa
                ),
                isFa = isFa
            )
            val availableSummary = localizedSummary.isNotBlank()
            if (availableSummary || readyState?.lastUpdatedMillis != null) {
                val syncTime = NumberFormatters.formatTime(
                    readyState?.lastUpdatedMillis ?: 0L,
                    prefs.usePersianDigits
                ).ifEmpty { "—" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "${strings.lastSyncPrefix}: $syncTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        modifier = Modifier.testTag("last_sync_label")
                    )
                    if (availableSummary) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            modifier = Modifier.testTag("rate_basis_summary")
                        )
                        Text(
                            // At most "Sources: N"; the provider list itself lives behind the info button.
                            text = "${strings.sourcesLabel}: $providerCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            modifier = Modifier.testTag("sources_count_label")
                        )
                        IconButton(
                            onClick = { showSourcesSheet = true },
                            modifier = Modifier
                                .size(28.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("sources_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = strings.sourcesInfoAction,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                modifier = Modifier.size(16.dp)
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
                                            comparisonAssetIds = prefs.comparisonAssetIds,
                                            referenceNowMillis = referenceNowMillis,
                                            strings = strings,
                                            onClick = { onAssetClick(asset) },
                                            onPriceClick = { onAssetPriceClick(asset) },
                                            onLongClick = { onTogglePin(asset.id) }
                                        )
                                    }
                                    if (prefs.showSupportCard) {
                                        item(
                                            key = "support_dev_card",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            SupportDevelopmentCard(
                                                repoSlug = prefs.githubRepoSlug,
                                                strings = strings,
                                                onOpenRepo = { url ->
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                    }
                                                }
                                            )
                                        }
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
                                        comparisonAssetIds = prefs.comparisonAssetIds,
                                        referenceNowMillis = referenceNowMillis,
                                        strings = strings,
                                        onClick = { onAssetClick(asset) },
                                        onPriceClick = { onAssetPriceClick(asset) },
                                        onTogglePin = { onTogglePin(asset.id) }
                                    )
                                }
                                if (prefs.showSupportCard) {
                                    item(key = "support_dev_card") {
                                        SupportDevelopmentCard(
                                            repoSlug = prefs.githubRepoSlug,
                                            strings = strings,
                                            onOpenRepo = { url ->
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // The provider list, with fetch times and the assets each provider feeds, lives behind the
        // info button so the feed itself only ever says "Sources: N".
        if (showSourcesSheet) {
            MarketSourcesSheet(
                groups = providerGroups,
                prefs = prefs,
                strings = strings,
                onDismiss = { showSourcesSheet = false }
            )
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
    comparisonAssetIds: Set<String>,
    referenceNowMillis: Long,
    strings: ChandStrings,
    onClick: () -> Unit,
    onPriceClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val marketColors = LocalMarketColors.current
    val haptic = LocalHapticFeedback.current
    val isOilUsd = asset.isUsdPerBarrel
    val isUnavailable = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
    val hasDisplayChange = !isUnavailable && (!isOilUsd || asset.isChange24hAvailable)
    val trendDirection = resolvePriceTrend(
        changeAmount = if (isOilUsd) asset.change24hUsd else asset.change24hToman,
        changePct = asset.change24hPercent,
        hasChangeData = hasDisplayChange
    )
    val trendColor = marketColors.trendColor(trendDirection)
    val trendBg = marketColors.trendContainerColor(trendDirection)
    val ageTier = FreshnessTimeFormatter.resolveAssetTier(asset, referenceNowMillis)
    // A DELAYED/CACHED badge next to "Updated just now" reads as a contradiction, so the badge only
    // appears once the age chip itself says the data is old.
    val statusBadgeText = when {
        asset.unitMismatch -> strings.checkUnitLabel
        asset.disputed -> strings.disputedLabel
        asset.freshness == DataFreshness.UNAVAILABLE -> strings.unavailableBadge
        (asset.freshness == DataFreshness.DELAYED || asset.freshness == DataFreshness.CACHED) &&
            ageTier != FreshnessAgeTier.NORMAL ->
            if (asset.freshness == DataFreshness.DELAYED) strings.delayedBadge else strings.cachedBadge
        else -> null
    }
    // Null means "no evidence of a trend": nothing is drawn rather than a misleading "0".
    val deltaLabel = AssetTrendPresentation.deltaLabel(asset, usePersianDigits, hasDisplayChange)
    // Without a chart there is nothing to reserve vertical space for; a fixed card height left a large
    // empty band in the middle of every DELAYED card.
    val hasTrendChart = AssetTrendPresentation.shouldShowSparkline(asset)
    val cardHeight = when {
        isOilUsd && hasTrendChart -> 206.dp
        isOilUsd -> 182.dp
        hasTrendChart -> 192.dp
        else -> 168.dp
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
            .height(cardHeight)
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
            if (hasTrendChart) {
                // Subtle atmospheric micro-sparkline along lower mid
                MiniSparkline(
                    points = asset.sparkline,
                    lineColor = trendColor.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .align(Alignment.Center)
                        .padding(horizontal = 14.dp)
                        .testTag("sparkline_${asset.id}")
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
                        if (deltaLabel != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = trendBg,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .testTag("delta_chip_${asset.id}")
                                ) {
                                    Text(
                                        text = deltaLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = trendColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (statusBadgeText != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (asset.unitMismatch || asset.disputed) {
                                    marketColors.gold
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                modifier = Modifier.testTag("status_badge_${asset.id}")
                            )
                        } else if (isOilUsd) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.liveBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = marketColors.gain,
                                maxLines = 1
                            )
                        } else if (asset.isStablecoinPeg) {
                            // A dollar peg is context, not news: "$1" alone was a stray, unlabeled number.
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.testTag("peg_chip_${asset.id}")
                            ) {
                                Text(
                                    text = strings.stablecoinPegLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
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
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = if (isOilUsd) 24.sp else 27.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 30.sp,
                                    fontFeatureSettings = "tnum"
                                ),
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
                            asset.sourceLabel.isNotBlank() -> ProviderLabels.short(asset.sourceLabel, isFa)
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

                    val freshnessLabel = FreshnessTimeFormatter.formatRelativeAgeLabel(
                        lastFetchMillis = FreshnessTimeFormatter.lastSuccessfulFetchMillis(asset),
                        nowMillis = referenceNowMillis,
                        strings = strings,
                        usePersianDigits = usePersianDigits,
                        isUnavailable = isUnavailable
                    )
                    val freshnessColor = when (ageTier) {
                        FreshnessAgeTier.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        FreshnessAgeTier.STALE_AMBER -> marketColors.gold
                        FreshnessAgeTier.CRITICAL_RED, FreshnessAgeTier.UNAVAILABLE -> marketColors.loss
                    }
                    val comparisonSummary = remember(asset, comparisonAssetIds) {
                        MarketComparisonHelper.buildComparison(asset, comparisonAssetIds)
                    }

                    Text(
                        text = freshnessLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = freshnessColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("freshness_chip_${asset.id}")
                    )

                    if (comparisonSummary != null) {
                        val badgeText = MarketComparisonHelper.formatComparisonBadge(
                            summary = comparisonSummary,
                            isFa = isFa,
                            usePersianDigits = usePersianDigits
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (comparisonSummary.isDivergent) {
                                marketColors.gold.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = if (comparisonSummary.isDivergent) {
                                BorderStroke(0.75.dp, marketColors.gold.copy(alpha = 0.5f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onPriceClick)
                                .testTag("comparison_badge_${asset.id}")
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (comparisonSummary.isDivergent) {
                                    marketColors.gold
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    comparisonAssetIds: Set<String>,
    referenceNowMillis: Long,
    strings: ChandStrings,
    onClick: () -> Unit,
    onPriceClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val isOilUsd = asset.isUsdPerBarrel
    val isUnavailableRow = asset.freshness == DataFreshness.UNAVAILABLE || !asset.hasUsablePrimaryPrice
    val hasDisplayChange = !isUnavailableRow && (!isOilUsd || asset.isChange24hAvailable)
    val trendDirection = resolvePriceTrend(
        changeAmount = if (isOilUsd) asset.change24hUsd else asset.change24hToman,
        changePct = asset.change24hPercent,
        hasChangeData = hasDisplayChange
    )
    val trendColor = marketColors.trendColor(trendDirection)
    val trendBg = marketColors.trendContainerColor(trendDirection)
    val ageTier = FreshnessTimeFormatter.resolveAssetTier(asset, referenceNowMillis)
    val rowDeltaLabel = AssetTrendPresentation.deltaLabel(asset, usePersianDigits, hasDisplayChange)
    val rowStatusText = when {
        asset.unitMismatch -> strings.checkUnitLabel
        asset.disputed -> strings.disputedLabel
        asset.freshness == DataFreshness.UNAVAILABLE -> strings.unavailableBadge
        (asset.freshness == DataFreshness.DELAYED || asset.freshness == DataFreshness.CACHED) &&
            ageTier != FreshnessAgeTier.NORMAL ->
            if (asset.freshness == DataFreshness.DELAYED) strings.delayedBadge else strings.cachedBadge
        isOilUsd -> strings.liveBadge
        else -> null
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
                    if (asset.isStablecoinPeg) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("peg_chip_${asset.id}")
                        ) {
                            Text(
                                text = strings.stablecoinPegLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (asset.category == AssetCategory.CRYPTO && asset.priceUsd != null) {
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
                            color = when {
                                asset.unitMismatch || asset.disputed -> marketColors.gold
                                rowStatusText == strings.liveBadge -> marketColors.gain
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier.testTag("status_badge_${asset.id}")
                        )
                    }
                }
                if (isOilUsd) {
                    val subEstimate = when {
                        asset.freshness != DataFreshness.UNAVAILABLE && asset.priceToman > 0.0 ->
                            "${strings.oilTomanEstimatePrefix} ${NumberFormatters.formatCardPrice(asset.priceToman, usePersianDigits)} ${strings.tomanUnit}"
                        asset.sourceLabel.isNotBlank() -> ProviderLabels.short(asset.sourceLabel, isFa)
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
                val freshnessLabel = FreshnessTimeFormatter.formatRelativeAgeLabel(
                    lastFetchMillis = FreshnessTimeFormatter.lastSuccessfulFetchMillis(asset),
                    nowMillis = referenceNowMillis,
                    strings = strings,
                    usePersianDigits = usePersianDigits,
                    isUnavailable = isUnavailableRow
                )
                val freshnessColor = when (ageTier) {
                    FreshnessAgeTier.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    FreshnessAgeTier.STALE_AMBER -> marketColors.gold
                    FreshnessAgeTier.CRITICAL_RED, FreshnessAgeTier.UNAVAILABLE -> marketColors.loss
                }
                val comparisonSummary = remember(asset, comparisonAssetIds) {
                    MarketComparisonHelper.buildComparison(asset, comparisonAssetIds)
                }

                Text(
                    text = freshnessLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = freshnessColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("freshness_chip_${asset.id}")
                )

                if (comparisonSummary != null) {
                    val badgeText = MarketComparisonHelper.formatComparisonBadge(
                        summary = comparisonSummary,
                        isFa = isFa,
                        usePersianDigits = usePersianDigits
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (comparisonSummary.isDivergent) {
                            marketColors.gold.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (comparisonSummary.isDivergent) {
                            BorderStroke(0.75.dp, marketColors.gold.copy(alpha = 0.5f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onPriceClick)
                            .testTag("comparison_badge_${asset.id}")
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (comparisonSummary.isDivergent) {
                                marketColors.gold
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (AssetTrendPresentation.shouldShowSparkline(asset)) {
                MiniSparkline(
                    points = asset.sparkline,
                    lineColor = trendColor,
                    modifier = Modifier
                        .width(44.dp)
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                        .testTag("sparkline_${asset.id}")
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
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
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
                if (rowDeltaLabel != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = trendBg,
                        modifier = Modifier.testTag("delta_chip_${asset.id}")
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = rowDeltaLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = trendColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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

@Composable
private fun SupportDevelopmentCard(
    repoSlug: String,
    strings: ChandStrings,
    onOpenRepo: (String) -> Unit
) {
    val marketColors = LocalMarketColors.current
    val resolvedSlug = repoSlug.trim().ifBlank { "thepoo7an/chand-iran-currency-gold-crypto" }
    val githubUrl = "https://github.com/$resolvedSlug"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, marketColors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenRepo(githubUrl) }
            .testTag("support_development_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = strings.supportDevCardTitle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = strings.supportDevCardTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.supportDevCardSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = strings.supportDevOpenRepo,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Title size ladder for the brand header.
 *
 * "Nerkhnama" is a single unbreakable word; at 30sp it no longer fits next to the date column and
 * three icon buttons on a 320dp-wide screen, which is what produced the "Nerkhna / ma" break. The
 * size steps down with the available width so the header stays on one line in both languages.
 * Exposed (internal) so a Robolectric test can pin the ladder without measuring fonts.
 */
internal fun headerTitleFontSize(isFa: Boolean, screenWidthDp: Int): TextUnit = when {
    screenWidthDp <= 340 -> if (isFa) 23.sp else 23.sp
    screenWidthDp <= 380 -> if (isFa) 26.sp else 26.sp
    else -> if (isFa) 29.sp else 30.sp
}
