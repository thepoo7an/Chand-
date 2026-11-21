package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.MarketUiState
import com.example.network.AppUpdateState
import com.example.ui.components.AssetDetailSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.WatchlistSheet
import com.example.ui.navigation.ChandRoute
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.MarketFeedScreen
import com.example.ui.strings.ChandStrings
import com.example.ui.strings.stringsFor
import com.example.ui.theme.ChandTheme
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.MarketViewModel

@Composable
fun ChandApp(
    viewModel: MarketViewModel = viewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredAssets by viewModel.filteredAssets.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDetailAsset by viewModel.selectedAssetForDetail.collectAsStateWithLifecycle()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsStateWithLifecycle()
    val historyPoints by viewModel.historyPoints.collectAsStateWithLifecycle()
    val historySourceLabel by viewModel.historySourceLabel.collectAsStateWithLifecycle()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateBannerDismissed by viewModel.updateBannerDismissed.collectAsStateWithLifecycle()

    val fromAssetId by viewModel.fromAssetId.collectAsStateWithLifecycle()
    val toAssetId by viewModel.toAssetId.collectAsStateWithLifecycle()
    val converterInput by viewModel.converterInput.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showWatchlistSheet by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.startAutoRefresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.stopAutoRefresh()
    }

    val strings = stringsFor(prefs.language)

    ChandTheme(
        themeMode = prefs.themeMode,
        language = prefs.language
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        val isMarketSelected = currentDestination?.hasRoute<ChandRoute.Market>() != false
        val isConverterSelected = currentDestination?.hasRoute<ChandRoute.Converter>() == true
        val isRefreshing = (uiState as? MarketUiState.Ready)?.isRefreshing == true

        fun navigateTo(route: ChandRoute) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 640.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                NavigationRailItem(
                                    selected = isMarketSelected,
                                    onClick = { navigateTo(ChandRoute.Market) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isMarketSelected) Icons.AutoMirrored.Filled.ShowChart
                                            else Icons.AutoMirrored.Outlined.ShowChart,
                                            contentDescription = strings.marketTab
                                        )
                                    },
                                    label = { Text(text = strings.marketTab) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("rail_markets")
                                )
                                NavigationRailItem(
                                    selected = isConverterSelected,
                                    onClick = { navigateTo(ChandRoute.Converter) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isConverterSelected) Icons.Filled.Calculate
                                            else Icons.Outlined.Calculate,
                                            contentDescription = strings.converterTab
                                        )
                                    },
                                    label = { Text(text = strings.converterTab) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("rail_converter")
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { showWatchlistSheet = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("rail_watchlist_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = strings.watchlistTitle
                                    )
                                }
                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("rail_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = strings.settingsTitle
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = ChandRoute.Market,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<ChandRoute.Market> {
                                    MarketFeedScreen(
                                        uiState = uiState,
                                        filteredAssets = filteredAssets,
                                        prefs = prefs,
                                        strings = strings,
                                        selectedCategory = selectedCategory,
                                        searchQuery = searchQuery,
                                        onSelectCategory = viewModel::selectCategory,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onTogglePin = viewModel::togglePin,
                                        onToggleGridView = viewModel::setIsGridView,
                                        onAssetClick = viewModel::openAssetDetail,
                                        onRefreshClick = { viewModel.refreshNow(silent = false) }
                                    )
                                }

                                composable<ChandRoute.Converter> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = if (useRail) 0.dp else 82.dp),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        ConverterScreen(
                                            uiState = uiState,
                                            fromAssetId = fromAssetId,
                                            toAssetId = toAssetId,
                                            inputAmountText = converterInput,
                                            prefs = prefs,
                                            strings = strings,
                                            onSetFromAsset = viewModel::setFromAsset,
                                            onSetToAsset = viewModel::setToAsset,
                                            onSwapAssets = viewModel::swapConverterAssets,
                                            onPresetClick = viewModel::setConverterPreset,
                                            onKeypadPress = viewModel::onKeypadPress,
                                            onOpenSettings = { showSettingsSheet = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chand?! Floating Glassy Pill Dock on Phones
                    if (!useRail) {
                        FloatingChandDock(
                            isMarketSelected = isMarketSelected,
                            isConverterSelected = isConverterSelected,
                            isRefreshing = isRefreshing,
                            strings = strings,
                            onMarketsClick = { navigateTo(ChandRoute.Market) },
                            onConverterClick = { navigateTo(ChandRoute.Converter) },
                            onAddWatchlistClick = { showWatchlistSheet = true },
                            onRefreshClick = { viewModel.refreshNow(silent = false) },
                            onSettingsClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }

        selectedDetailAsset?.let { detailAsset ->
            AssetDetailSheet(
                asset = detailAsset,
                isPinned = detailAsset.id in prefs.pinnedIds,
                historyPoints = historyPoints,
                historySourceLabel = historySourceLabel,
                isHistoryLoading = isHistoryLoading,
                selectedRange = selectedTimeRange,
                prefs = prefs,
                strings = strings,
                onSelectRange = viewModel::selectTimeRange,
                onRetryHistory = viewModel::retryHistory,
                onTogglePin = { viewModel.togglePin(detailAsset.id) },
                onConvertClick = {
                    viewModel.prepareConverterForAsset(detailAsset.id)
                    viewModel.closeAssetDetail()
                    navigateTo(ChandRoute.Converter)
                },
                onDismiss = viewModel::closeAssetDetail
            )
        }

        if (showWatchlistSheet) {
            val allAssets = (uiState as? MarketUiState.Ready)?.assets.orEmpty()
            WatchlistSheet(
                assets = allAssets,
                prefs = prefs,
                strings = strings,
                onTogglePin = viewModel::togglePin,
                onDismiss = { showWatchlistSheet = false }
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                prefs = prefs,
                strings = strings,
                updateState = updateState,
                onSetThemeMode = viewModel::setThemeMode,
                onSetLanguage = viewModel::setLanguage,
                onSetPersianDigits = viewModel::setUsePersianDigits,
                onSetIsGridView = viewModel::setIsGridView,
                onSetRefreshInterval = viewModel::setRefreshInterval,
                onSetWidgetTranslucent = viewModel::setWidgetTranslucent,
                onSetWidgetOpacityPercent = viewModel::setWidgetOpacityPercent,
                onCheckForUpdates = viewModel::checkForUpdates,
                onDismiss = { showSettingsSheet = false }
            )
        }

        if (updateState is AppUpdateState.UpdateAvailable && !updateBannerDismissed && !showSettingsSheet) {
            val info = (updateState as AppUpdateState.UpdateAvailable).info
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdateBanner,
                title = {
                    Text(
                        text = "${strings.updateAvailableTitle} (v${info.latestVersionName})",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${strings.currentVersionLabel}: v${info.currentVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = info.matchedApkName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = info.releaseNotes.take(300),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                )
                            }
                            viewModel.dismissUpdateBanner()
                        }
                    ) {
                        Text(strings.downloadUpdateButton)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdateBanner) {
                        Text(strings.laterButton)
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingChandDock(
    isMarketSelected: Boolean,
    isConverterSelected: Boolean,
    isRefreshing: Boolean,
    strings: ChandStrings,
    onMarketsClick: () -> Unit,
    onConverterClick: () -> Unit,
    onAddWatchlistClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val marketColors = LocalMarketColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "dock_spin")
    val spinDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dock_spin_deg"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = if (marketColors.isAmoled) 0.dp else 10.dp,
        border = BorderStroke(1.dp, marketColors.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // + Add/Manage Pinned Watchlist (mirroring left '+' of Chand?!)
            IconButton(
                onClick = onAddWatchlistClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dock_watchlist_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = strings.watchlistTitle,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Center Segmented Markets / Converter + Live Refresh
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DockSegmentPill(
                    label = strings.marketTab,
                    selected = isMarketSelected,
                    icon = if (isMarketSelected) Icons.AutoMirrored.Filled.ShowChart
                    else Icons.AutoMirrored.Outlined.ShowChart,
                    onClick = onMarketsClick,
                    modifier = Modifier.testTag("nav_markets")
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRefreshClick)
                        .testTag("refresh_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = strings.retryLabel,
                            tint = if (isRefreshing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .then(if (isRefreshing) Modifier.rotate(spinDeg) else Modifier)
                        )
                    }
                }

                DockSegmentPill(
                    label = strings.converterTab,
                    selected = isConverterSelected,
                    icon = if (isConverterSelected) Icons.Filled.Calculate
                    else Icons.Outlined.Calculate,
                    onClick = onConverterClick,
                    modifier = Modifier.testTag("nav_converter")
                )
            }

            // ... Settings & Theme (mirroring right '...' of Chand?!)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = strings.settingsTitle,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DockSegmentPill(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.onBackground
        else Color.Transparent,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
