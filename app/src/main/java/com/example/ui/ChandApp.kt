package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.ui.components.AssetDetailSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.navigation.ChandRoute
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.MarketFeedScreen
import com.example.ui.strings.stringsFor
import com.example.ui.theme.ChandTheme
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
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsStateWithLifecycle()

    val fromAssetId by viewModel.fromAssetId.collectAsStateWithLifecycle()
    val toAssetId by viewModel.toAssetId.collectAsStateWithLifecycle()
    val converterInput by viewModel.converterInput.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }

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
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = isMarketSelected,
                                onClick = { navigateTo(ChandRoute.Market) },
                                icon = {
                                    Icon(
                                        imageVector = if (isMarketSelected) Icons.Filled.ShowChart else Icons.Outlined.ShowChart,
                                        contentDescription = strings.marketTab
                                    )
                                },
                                label = { Text(text = strings.marketTab) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_markets")
                            )
                            NavigationBarItem(
                                selected = isConverterSelected,
                                onClick = { navigateTo(ChandRoute.Converter) },
                                icon = {
                                    Icon(
                                        imageVector = if (isConverterSelected) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                        contentDescription = strings.converterTab
                                    )
                                },
                                label = { Text(text = strings.converterTab) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_converter")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
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
                                        imageVector = if (isMarketSelected) Icons.Filled.ShowChart else Icons.Outlined.ShowChart,
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
                                        imageVector = if (isConverterSelected) Icons.Filled.Calculate else Icons.Outlined.Calculate,
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
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
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
                                        onAssetClick = viewModel::openAssetDetail,
                                        onRefreshClick = { viewModel.refreshNow(silent = false) },
                                        onOpenSettings = { showSettingsSheet = true }
                                    )
                                }
                            }

                            composable<ChandRoute.Converter> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
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
            }
        }

        selectedDetailAsset?.let { detailAsset ->
            AssetDetailSheet(
                asset = detailAsset,
                isPinned = detailAsset.id in prefs.pinnedIds,
                historyPoints = historyPoints,
                isHistoryLoading = isHistoryLoading,
                selectedRange = selectedTimeRange,
                prefs = prefs,
                strings = strings,
                onSelectRange = viewModel::selectTimeRange,
                onTogglePin = { viewModel.togglePin(detailAsset.id) },
                onConvertClick = {
                    viewModel.prepareConverterForAsset(detailAsset.id)
                    viewModel.closeAssetDetail()
                    navigateTo(ChandRoute.Converter)
                },
                onDismiss = viewModel::closeAssetDetail
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                prefs = prefs,
                strings = strings,
                onSetThemeMode = viewModel::setThemeMode,
                onSetLanguage = viewModel::setLanguage,
                onSetPersianDigits = viewModel::setUsePersianDigits,
                onSetRefreshInterval = viewModel::setRefreshInterval,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}
