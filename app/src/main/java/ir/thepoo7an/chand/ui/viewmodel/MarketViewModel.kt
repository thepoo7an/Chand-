package ir.thepoo7an.chand.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.AppUpdateDownloadState
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateInstaller
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.InstallLaunchResult
import ir.thepoo7an.chand.network.TomanRateBasis
import ir.thepoo7an.chand.widget.WidgetSyncCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TOMAN_ASSET_ID = "IRT"
    }

    private val prefsRepo = PreferencesRepository(application.applicationContext)
    private val repository = MarketRepository(application.applicationContext, prefsRepo)

    val preferences: StateFlow<AppPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppPreferences()
    )

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(AssetCategory.ALL)
    val selectedCategory: StateFlow<AssetCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAssetForDetail = MutableStateFlow<MarketAsset?>(null)
    val selectedAssetForDetail: StateFlow<MarketAsset?> = _selectedAssetForDetail.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.H24)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _historyPoints = MutableStateFlow<List<AssetHistoryPoint>>(emptyList())
    val historyPoints: StateFlow<List<AssetHistoryPoint>> = _historyPoints.asStateFlow()

    private val _historySourceLabel = MutableStateFlow("")
    val historySourceLabel: StateFlow<String> = _historySourceLabel.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val _updateBannerDismissed = MutableStateFlow(false)
    val updateBannerDismissed: StateFlow<Boolean> = _updateBannerDismissed.asStateFlow()

    private val _updateDownloadState = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val updateDownloadState: StateFlow<AppUpdateDownloadState> = _updateDownloadState.asStateFlow()

    private val _installApkRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val installApkRequests: SharedFlow<String> = _installApkRequests.asSharedFlow()

    // Converter State
    private val _fromAssetId = MutableStateFlow("USD")
    val fromAssetId: StateFlow<String> = _fromAssetId.asStateFlow()

    private val _toAssetId = MutableStateFlow(TOMAN_ASSET_ID)
    val toAssetId: StateFlow<String> = _toAssetId.asStateFlow()

    private val _converterInput = MutableStateFlow("100")
    val converterInput: StateFlow<String> = _converterInput.asStateFlow()

    val filteredAssets: StateFlow<List<MarketAsset>> = combine(
        _uiState,
        preferences,
        _selectedCategory,
        _searchQuery
    ) { state, prefs, category, query ->
        val ready = state as? MarketUiState.Ready ?: return@combine emptyList()
        val q = query.trim().lowercase()
        ready.assets
            .filter { asset ->
                val matchesCat = when (category) {
                    AssetCategory.ALL -> true
                    AssetCategory.PINNED -> asset.id in prefs.pinnedIds
                    else -> asset.category == category
                }
                val matchesQuery = q.isEmpty() ||
                    asset.nameFa.lowercase().contains(q) ||
                    asset.nameEn.lowercase().contains(q) ||
                    asset.symbol.lowercase().contains(q) ||
                    asset.id.lowercase().contains(q)
                matchesCat && matchesQuery
            }
            .sortedByDescending { it.id in prefs.pinnedIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private var pollingJob: Job? = null
    private var historyJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var pendingInstallAfterPermission: Boolean = false

    init {
        loadInitialData()
        observeNetworkRestoration()
        checkForUpdatesSilently()
    }

    private fun observeNetworkRestoration() {
        viewModelScope.launch {
            WidgetSyncCoordinator.networkRestoredEvents.collect {
                refreshNow(silent = true)
            }
        }
    }

    private fun checkForUpdatesSilently() {
        viewModelScope.launch {
            try {
                val slug = prefsRepo.preferencesFlow.first().githubRepoSlug
                if (slug.isNotBlank()) {
                    val res = repository.checkForUpdate(slug)
                    if (res is AppUpdateState.UpdateAvailable) {
                        _updateState.value = res
                        _updateBannerDismissed.value = false
                        syncCachedApkDownloadState(res.info)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Silent background check must never crash startup
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                val cached = repository.loadCached()
                if (cached != null && cached.assets.isNotEmpty()) {
                    _uiState.value = MarketUiState.Ready(
                        assets = cached.assets.map { it.copy(freshness = if (it.priceToman > 0.0) DataFreshness.CACHED else DataFreshness.UNAVAILABLE) },
                        lastUpdatedMillis = cached.updatedAtMillis,
                        isRefreshing = true,
                        sourceSummary = cached.sourceSummary,
                        freshness = DataFreshness.CACHED
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Ignore corrupted/missing cache on first launch
            }
            refreshNow()
        }
    }

    fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val intervalSec = preferences.value.refreshIntervalSeconds
                if (intervalSec > 0) {
                    delay(intervalSec * 1000L)
                    refreshNow(silent = true)
                } else {
                    delay(5_000L)
                }
            }
        }
    }

    fun stopAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshNow(silent: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            if (!silent && current is MarketUiState.Ready) {
                _uiState.value = current.copy(isRefreshing = true)
            } else if (!silent && current is MarketUiState.Error) {
                _uiState.value = MarketUiState.Loading
            }

            val sync = try {
                repository.refreshMarket()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }

            if (sync != null && sync.assets.isNotEmpty()) {
                _uiState.value = MarketUiState.Ready(
                    assets = sync.assets,
                    lastUpdatedMillis = sync.updatedAtMillis,
                    isRefreshing = false,
                    sourceSummary = sync.sourceSummary,
                    freshness = sync.freshness,
                    errorBanner = when {
                        sync.freshness == DataFreshness.LIVE -> null
                        sync.freshness == DataFreshness.DELAYED -> "delayed"
                        sync.sourceSummary.contains(TomanRateBasis.OFFLINE_REFERENCE.labelEn) -> "offline_reference"
                        else -> "offline"
                    }
                )
                // Also refresh currently open detail asset if visible
                _selectedAssetForDetail.value?.let { openAsset ->
                    sync.assets.firstOrNull { it.id == openAsset.id }?.let { fresh ->
                        _selectedAssetForDetail.value = fresh
                    }
                }
            } else if (_uiState.value !is MarketUiState.Ready) {
                _uiState.value = MarketUiState.Error("network_unavailable")
            } else if (current is MarketUiState.Ready) {
                _uiState.value = current.copy(isRefreshing = false, freshness = DataFreshness.CACHED, errorBanner = "offline")
            }
        }
    }

    fun selectCategory(category: AssetCategory) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(assetId: String) {
        viewModelScope.launch {
            repository.togglePin(assetId)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguage(language)
        }
    }

    fun setUsePersianDigits(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUsePersianDigits(enabled)
        }
    }

    fun setIsGridView(isGrid: Boolean) {
        viewModelScope.launch {
            repository.setIsGridView(isGrid)
        }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch {
            repository.setRefreshInterval(seconds)
        }
    }

    fun setWidgetTranslucent(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWidgetTranslucent(enabled)
        }
    }

    fun setWidgetOpacityPercent(percent: Int) {
        viewModelScope.launch {
            repository.setWidgetOpacityPercent(percent)
        }
    }

    fun setWidgetHeroAssetId(assetId: String) {
        viewModelScope.launch {
            repository.setWidgetHeroAssetId(assetId)
        }
    }

    fun setGithubRepoSlug(slug: String) {
        viewModelScope.launch {
            repository.setGithubRepoSlug(slug)
        }
    }

    fun checkForUpdates(repoOverride: String? = null) {
        viewModelScope.launch {
            val targetRepo = (repoOverride ?: preferences.value.githubRepoSlug).trim()
            if (repoOverride != null && repoOverride.trim() != preferences.value.githubRepoSlug) {
                repository.setGithubRepoSlug(targetRepo)
            }
            _updateState.value = AppUpdateState.Checking
            _updateBannerDismissed.value = false
            val result = try {
                repository.checkForUpdate(targetRepo)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                AppUpdateState.Error("update_check_failed")
            }
            _updateState.value = result
            if (result is AppUpdateState.UpdateAvailable) {
                syncCachedApkDownloadState(result.info)
            } else {
                updateDownloadJob?.cancel()
                _updateDownloadState.value = AppUpdateDownloadState.Idle
            }
        }
    }

    private fun syncCachedApkDownloadState(info: AppUpdateInfo) {
        val currentDownload = _updateDownloadState.value
        if (currentDownload is AppUpdateDownloadState.Downloading) return
        val appContext = getApplication<Application>().applicationContext
        val existingApk = AppUpdateInstaller.findAlreadyDownloadedApk(appContext, info)
        if (existingApk != null) {
            _updateDownloadState.value = AppUpdateDownloadState.ReadyToInstall(
                info = info,
                apkFilePath = existingApk.absolutePath,
                fileSizeBytes = existingApk.length()
            )
        } else if (currentDownload is AppUpdateDownloadState.ReadyToInstall &&
            currentDownload.info.latestVersionName != info.latestVersionName
        ) {
            _updateDownloadState.value = AppUpdateDownloadState.Idle
        }
    }

    fun startInAppUpdateDownload(info: AppUpdateInfo) {
        val existingReady = AppUpdateInstaller.findAlreadyDownloadedApk(
            getApplication<Application>().applicationContext,
            info
        )
        if (existingReady != null) {
            _updateDownloadState.value = AppUpdateDownloadState.ReadyToInstall(
                info = info,
                apkFilePath = existingReady.absolutePath,
                fileSizeBytes = existingReady.length()
            )
            _installApkRequests.tryEmit(existingReady.absolutePath)
            return
        }

        updateDownloadJob?.cancel()
        val appContext = getApplication<Application>().applicationContext
        val destFile = AppUpdateInstaller.targetApkFile(appContext, info)
        _updateDownloadState.value = AppUpdateDownloadState.Downloading(
            info = info,
            downloadedBytes = 0L,
            totalBytes = info.matchedApkSizeBytes
        )

        updateDownloadJob = viewModelScope.launch {
            try {
                AppUpdateInstaller.cleanupStaleApks(appContext, keepFile = destFile)
                val downloaded = repository.downloadUpdateApk(
                    downloadUrl = info.downloadUrl,
                    destinationFile = destFile,
                    expectedSizeBytes = info.matchedApkSizeBytes
                ) { bytes, total ->
                    _updateDownloadState.value = AppUpdateDownloadState.Downloading(
                        info = info,
                        downloadedBytes = bytes,
                        totalBytes = total
                    )
                }
                _updateDownloadState.value = AppUpdateDownloadState.ReadyToInstall(
                    info = info,
                    apkFilePath = downloaded.absolutePath,
                    fileSizeBytes = downloaded.length()
                )
                _installApkRequests.tryEmit(downloaded.absolutePath)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                _updateDownloadState.value = AppUpdateDownloadState.Error(
                    info = info,
                    errorCode = "download_failed"
                )
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        pendingInstallAfterPermission = false
        _updateDownloadState.value = AppUpdateDownloadState.Idle
    }

    fun markAwaitingInstallPermission() {
        pendingInstallAfterPermission = true
    }

    fun handleResumeForPendingInstall() {
        if (!pendingInstallAfterPermission) return
        val ready = _updateDownloadState.value as? AppUpdateDownloadState.ReadyToInstall ?: run {
            pendingInstallAfterPermission = false
            return
        }
        val appContext = getApplication<Application>().applicationContext
        if (AppUpdateInstaller.canRequestPackageInstalls(appContext)) {
            pendingInstallAfterPermission = false
            val file = File(ready.apkFilePath)
            if (file.exists()) {
                _installApkRequests.tryEmit(ready.apkFilePath)
            }
        }
    }

    fun handleInstallLaunchResult(result: InstallLaunchResult, info: AppUpdateInfo) {
        when (result) {
            InstallLaunchResult.LAUNCHED_INSTALLER -> {
                pendingInstallAfterPermission = false
            }
            InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION -> {
                pendingInstallAfterPermission = true
            }
            InstallLaunchResult.FILE_MISSING -> {
                pendingInstallAfterPermission = false
                _updateDownloadState.value = AppUpdateDownloadState.Idle
            }
            InstallLaunchResult.FAILED -> {
                pendingInstallAfterPermission = false
                _updateDownloadState.value = AppUpdateDownloadState.Error(
                    info = info,
                    errorCode = "install_failed"
                )
            }
        }
    }

    fun openUpdateCatalog() {
        _updateBannerDismissed.value = false
    }

    fun dismissUpdateBanner() {
        _updateBannerDismissed.value = true
    }

    fun openAssetDetail(asset: MarketAsset) {
        _selectedAssetForDetail.value = asset
        _selectedTimeRange.value = TimeRange.H24
        _historyPoints.value = emptyList()
        _historySourceLabel.value = ""
        loadHistoryFor(asset, TimeRange.H24, forceRefresh = false)
    }

    fun closeAssetDetail() {
        _selectedAssetForDetail.value = null
        historyJob?.cancel()
    }

    fun selectTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        val asset = _selectedAssetForDetail.value ?: return
        loadHistoryFor(asset, range, forceRefresh = false)
    }

    fun retryHistory() {
        val asset = _selectedAssetForDetail.value ?: return
        loadHistoryFor(asset, _selectedTimeRange.value, forceRefresh = true)
    }

    private fun loadHistoryFor(
        asset: MarketAsset,
        range: TimeRange,
        forceRefresh: Boolean
    ) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _isHistoryLoading.value = true
            try {
                val result = repository.fetchHistory(asset, range, forceRefresh = forceRefresh)
                _historyPoints.value = result.points
                _historySourceLabel.value = result.sourceLabel
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                _historyPoints.value = emptyList()
                _historySourceLabel.value = ""
            } finally {
                _isHistoryLoading.value = false
            }
        }
    }

    // Converter actions
    fun setFromAsset(assetId: String) {
        if (assetId == _toAssetId.value) {
            _toAssetId.value = _fromAssetId.value
        }
        _fromAssetId.value = assetId
    }

    fun setToAsset(assetId: String) {
        if (assetId == _fromAssetId.value) {
            _fromAssetId.value = _toAssetId.value
        }
        _toAssetId.value = assetId
    }

    fun prepareConverterForAsset(assetId: String) {
        _fromAssetId.value = assetId
        _toAssetId.value = TOMAN_ASSET_ID
        _converterInput.value = "1"
    }

    fun swapConverterAssets() {
        val oldFrom = _fromAssetId.value
        _fromAssetId.value = _toAssetId.value
        _toAssetId.value = oldFrom
    }

    fun setConverterPreset(value: String) {
        _converterInput.value = value
    }

    fun onKeypadPress(key: String) {
        val current = _converterInput.value
        when (key) {
            "C" -> _converterInput.value = "0"
            "BACKSPACE" -> {
                _converterInput.value = if (current.length <= 1) "0" else current.dropLast(1)
            }
            "." -> {
                if (!current.contains(".") && current.length < 15) {
                    _converterInput.value = "$current."
                }
            }
            "000" -> {
                if (current == "0") return
                if (current.length + 3 <= 16) {
                    _converterInput.value = current + "000"
                }
            }
            else -> {
                if (current.length >= 16) return
                _converterInput.value = if (current == "0") key else current + key
            }
        }
    }
}
