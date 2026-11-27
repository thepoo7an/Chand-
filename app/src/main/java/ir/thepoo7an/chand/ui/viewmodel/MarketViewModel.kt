package ir.thepoo7an.chand.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.alerts.PriceAlertEvaluator
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.PriceAlert
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import java.util.UUID
import ir.thepoo7an.chand.network.AppUpdateDownloadState
import ir.thepoo7an.chand.network.AppUpdateException
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateInstaller
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.InstallLaunchResult
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.RemoteConfigSyncResult
import ir.thepoo7an.chand.network.RemoteConfigUiState
import ir.thepoo7an.chand.network.TomanRateBasis
import ir.thepoo7an.chand.network.UpdateErrorReason
import ir.thepoo7an.chand.network.UpdateSecurityValidator
import ir.thepoo7an.chand.widget.WidgetSyncCoordinator
import javax.net.ssl.SSLException
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MarketViewModel(
    application: Application,
    private val prefsRepo: PreferencesRepository,
    private val repository: MarketRepository,
    private val autoStart: Boolean = true
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        prefsRepo = PreferencesRepository(application.applicationContext),
        repository = createDefaultRepository(application.applicationContext),
        autoStart = true
    )

    companion object {
        const val TOMAN_ASSET_ID = "IRT"

        private fun createDefaultRepository(context: android.content.Context): MarketRepository {
            val prefsRepo = PreferencesRepository(context)
            return MarketRepository(context, prefsRepo)
        }

        fun provideFactory(
            application: Application,
            prefsRepo: PreferencesRepository,
            repository: MarketRepository,
            autoStart: Boolean = true
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MarketViewModel::class.java)) {
                    return MarketViewModel(
                        application = application,
                        prefsRepo = prefsRepo,
                        repository = repository,
                        autoStart = autoStart
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    val preferences: StateFlow<AppPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppPreferences()
    )

    val priceAlerts: StateFlow<List<PriceAlert>> = repository.priceAlertsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val liveConfig: StateFlow<RemoteAppConfig> = repository.remoteConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LiveConfigRegistry.currentConfig()
    )

    private val _liveConfigSyncState = MutableStateFlow<RemoteConfigUiState>(RemoteConfigUiState.Idle)
    val liveConfigSyncState: StateFlow<RemoteConfigUiState> = _liveConfigSyncState.asStateFlow()

    private val _uiState = MutableStateFlow<MarketUiState>(MarketUiState.Loading)
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(AssetCategory.ALL)
    val selectedCategory: StateFlow<AssetCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedAssetForDetail = MutableStateFlow<MarketAsset?>(null)
    val selectedAssetForDetail: StateFlow<MarketAsset?> = _selectedAssetForDetail.asStateFlow()

    private val _selectedAssetForSourceBreakdown = MutableStateFlow<MarketAsset?>(null)
    val selectedAssetForSourceBreakdown: StateFlow<MarketAsset?> = _selectedAssetForSourceBreakdown.asStateFlow()

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
    private var refreshJob: Job? = null
    private var historyJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var pendingInstallAfterPermission: Boolean = false

    init {
        if (autoStart) {
            loadInitialData()
            observeNetworkRestoration()
            syncLiveConfigSilently()
            checkForUpdatesSilently()
            viewModelScope.launch {
                val existingAlerts = runCatching { repository.getPriceAlerts() }.getOrDefault(emptyList())
                repository.syncAlertSchedule(existingAlerts)
            }
        }
    }

    private fun observeNetworkRestoration() {
        viewModelScope.launch {
            WidgetSyncCoordinator.networkRestoredEvents.collect {
                refreshNow(silent = true)
            }
        }
    }

    private fun syncLiveConfigSilently() {
        viewModelScope.launch {
            try {
                val prefs = prefsRepo.preferencesFlow.first()
                val result = repository.syncLiveConfig(
                    repoSlug = prefs.githubRepoSlug,
                    customUrl = prefs.liveConfigUrl
                )
                if (result is RemoteConfigSyncResult.Applied) {
                    _liveConfigSyncState.value = RemoteConfigUiState.Synced(
                        configVersion = result.config.configVersion,
                        syncedAtMillis = result.syncedAtMillis,
                        versionChanged = result.versionChanged
                    )
                    if (result.versionChanged) {
                        refreshNow(silent = true)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Silent remote config sync must never disrupt startup
            }
        }
    }

    private fun updateTagFor(info: AppUpdateInfo): String =
        "${info.latestVersionName}#${info.latestVersionCode ?: 0}"

    private fun checkForUpdatesSilently() {
        viewModelScope.launch {
            try {
                val cfg = repository.hydrateLiveConfig()
                if (!cfg.features.enableInAppApkUpdates) return@launch
                val prefs = prefsRepo.preferencesFlow.first()
                val slug = prefs.githubRepoSlug
                if (slug.isNotBlank()) {
                    val res = repository.checkForUpdate(slug)
                    if (res is AppUpdateState.UpdateAvailable) {
                        _updateState.value = res
                        _updateBannerDismissed.value = (updateTagFor(res.info) == prefs.dismissedUpdateTag)
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

    fun loadInitialData() {
        viewModelScope.launch {
            try {
                repository.hydrateLiveConfig()
                val cached = repository.loadCached()
                if (cached != null && cached.assets.isNotEmpty()) {
                    _uiState.value = MarketUiState.Ready(
                        assets = cached.assets.map {
                            it.copy(
                                freshness = if (it.hasUsablePrimaryPrice) DataFreshness.CACHED else DataFreshness.UNAVAILABLE
                            )
                        },
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
            refreshNow(silent = false)
        }
    }

    fun startAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            combine(
                preferences.map { it.refreshIntervalSeconds },
                liveConfig.map { it.features.minAutoRefreshSeconds }
            ) { intervalSec, minIntervalSec ->
                if (intervalSec <= 0) 0 else maxOf(intervalSec, minIntervalSec)
            }
                .distinctUntilChanged()
                .collectLatest { effectiveIntervalSec ->
                    if (effectiveIntervalSec <= 0) return@collectLatest
                    while (isActive) {
                        delay(effectiveIntervalSec * 1000L)
                        refreshNow(silent = true)
                    }
                }
        }
    }

    fun stopAutoRefresh() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshNow(silent: Boolean = false) {
        if (!silent) {
            _uiState.update { state ->
                when (state) {
                    is MarketUiState.Ready -> state.copy(isRefreshing = true)
                    is MarketUiState.Error -> MarketUiState.Loading
                    MarketUiState.Loading -> state
                }
            }
        }

        if (refreshJob?.isActive == true) {
            return
        }

        refreshJob = viewModelScope.launch {
            try {
                val sync = try {
                    repository.refreshMarket()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    null
                }

                val existingReady = _uiState.value as? MarketUiState.Ready
                val existingHasUsableCache = existingReady?.assets?.any { it.hasUsablePrimaryPrice } == true
                val incomingUsable = sync != null && sync.assets.isNotEmpty() &&
                    (sync.freshness != DataFreshness.UNAVAILABLE || !existingHasUsableCache)

                if (incomingUsable && sync != null) {
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
                    _selectedAssetForDetail.update { openAsset ->
                        openAsset?.let { asset -> sync.assets.firstOrNull { it.id == asset.id } ?: asset }
                    }
                    _selectedAssetForSourceBreakdown.update { openAsset ->
                        openAsset?.let { asset -> sync.assets.firstOrNull { it.id == asset.id } ?: asset }
                    }
                } else {
                    _uiState.update { state ->
                        when (state) {
                            is MarketUiState.Ready -> state.copy(
                                isRefreshing = false,
                                freshness = DataFreshness.CACHED,
                                errorBanner = "offline"
                            )
                            is MarketUiState.Error, MarketUiState.Loading -> MarketUiState.Error("network_unavailable")
                        }
                    }
                }
            } finally {
                _uiState.update { state ->
                    if (state is MarketUiState.Ready && state.isRefreshing) {
                        state.copy(isRefreshing = false)
                    } else {
                        state
                    }
                }
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

    fun setShowSupportCard(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShowSupportCard(enabled)
        }
    }

    fun toggleComparisonAsset(assetId: String) {
        viewModelScope.launch {
            repository.toggleComparisonAsset(assetId)
        }
    }

    fun upsertPriceAlert(
        existingId: String?,
        assetId: String,
        upperBoundToman: Double?,
        lowerBoundToman: Double?,
        isEnabled: Boolean = true
    ) {
        val cleanUpper = upperBoundToman?.takeIf { it.isFinite() && it > 0.0 }
        val cleanLower = lowerBoundToman?.takeIf { it.isFinite() && it > 0.0 }
        val candidate = PriceAlert(
            id = existingId ?: UUID.randomUUID().toString(),
            assetId = assetId,
            upperBoundToman = cleanUpper,
            lowerBoundToman = cleanLower,
            isEnabled = isEnabled,
            triggeredUpper = false,
            triggeredLower = false,
            lastTriggeredAtMillis = 0L,
            createdAtMillis = System.currentTimeMillis()
        )
        if (!candidate.hasValidBounds) return
        viewModelScope.launch {
            val current = repository.getPriceAlerts().toMutableList()
            val idx = current.indexOfFirst { it.id == candidate.id }
            if (idx >= 0) {
                current[idx] = candidate.copy(createdAtMillis = current[idx].createdAtMillis)
            } else {
                current.add(0, candidate)
            }
            repository.savePriceAlerts(current)
        }
    }

    fun deletePriceAlert(alertId: String) {
        viewModelScope.launch {
            val updated = repository.getPriceAlerts().filterNot { it.id == alertId }
            repository.savePriceAlerts(updated)
        }
    }

    fun togglePriceAlertEnabled(alertId: String) {
        viewModelScope.launch {
            val updated = repository.getPriceAlerts().map { alert ->
                if (alert.id == alertId) alert.copy(isEnabled = !alert.isEnabled) else alert
            }
            repository.savePriceAlerts(updated)
        }
    }

    fun rearmPriceAlert(alertId: String) {
        viewModelScope.launch {
            val updated = repository.getPriceAlerts().map { alert ->
                if (alert.id == alertId) PriceAlertEvaluator.rearm(alert) else alert
            }
            repository.savePriceAlerts(updated)
        }
    }

    suspend fun sendTestPriceAlert(): Boolean = repository.sendTestAlert()

    fun setGithubRepoSlug(slug: String) {
        val trimmed = slug.trim()
        val changed = trimmed != preferences.value.githubRepoSlug
        viewModelScope.launch {
            repository.setGithubRepoSlug(trimmed)
        }
        if (changed) {
            updateDownloadJob?.cancel()
            updateDownloadJob = null
            pendingInstallAfterPermission = false
            _updateState.value = AppUpdateState.Idle
            _updateDownloadState.value = AppUpdateDownloadState.Idle
            _liveConfigSyncState.value = RemoteConfigUiState.Idle
        }
    }

    fun setLiveConfigUrl(url: String) {
        viewModelScope.launch {
            repository.setLiveConfigUrl(url)
        }
    }

    fun syncLiveConfigNow(customUrlOverride: String? = null, repoOverride: String? = null) {
        viewModelScope.launch {
            val targetRepo = (repoOverride ?: preferences.value.githubRepoSlug).trim()
            val targetUrl = (customUrlOverride ?: preferences.value.liveConfigUrl).trim()
            if (repoOverride != null && targetRepo != preferences.value.githubRepoSlug) {
                repository.setGithubRepoSlug(targetRepo)
            }
            if (customUrlOverride != null && targetUrl != preferences.value.liveConfigUrl) {
                repository.setLiveConfigUrl(targetUrl)
            }
            _liveConfigSyncState.value = RemoteConfigUiState.Syncing
            val result = try {
                repository.syncLiveConfig(repoSlug = targetRepo, customUrl = targetUrl)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                RemoteConfigSyncResult.Error(t.message ?: "sync_failed")
            }
            when (result) {
                is RemoteConfigSyncResult.Applied -> {
                    _liveConfigSyncState.value = RemoteConfigUiState.Synced(
                        configVersion = result.config.configVersion,
                        syncedAtMillis = result.syncedAtMillis,
                        versionChanged = result.versionChanged
                    )
                    refreshNow(silent = true)
                }
                is RemoteConfigSyncResult.NotConfiguredOrMissing -> {
                    _liveConfigSyncState.value = RemoteConfigUiState.NoRemoteConfigYet(System.currentTimeMillis())
                }
                is RemoteConfigSyncResult.IncompatibleVersion -> {
                    _liveConfigSyncState.value = RemoteConfigUiState.Incompatible(result.minAppVersionCode)
                }
                is RemoteConfigSyncResult.Error -> {
                    _liveConfigSyncState.value = RemoteConfigUiState.Error(result.reason)
                }
            }
        }
    }

    fun resetLiveConfig() {
        viewModelScope.launch {
            repository.resetLiveConfig()
            _liveConfigSyncState.value = RemoteConfigUiState.Idle
            refreshNow(silent = true)
        }
    }

    fun dismissLiveNotice(noticeId: String) {
        if (noticeId.isBlank()) return
        viewModelScope.launch {
            repository.setDismissedLiveNoticeId(noticeId)
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
            } catch (t: Throwable) {
                val reason = when (t) {
                    is AppUpdateException -> t.reason
                    is SSLException -> UpdateErrorReason.HTTPS_VALIDATION_FAILED
                    else -> UpdateErrorReason.NETWORK_FAILURE
                }
                AppUpdateState.Error(reason.code, reason)
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
        val currentDownloading = _updateDownloadState.value as? AppUpdateDownloadState.Downloading
        if (updateDownloadJob?.isActive == true &&
            currentDownloading?.info?.latestVersionName == info.latestVersionName &&
            currentDownloading.info.latestVersionCode == info.latestVersionCode
        ) {
            return
        }

        if (!info.hasVerifiedMetadata || !UpdateSecurityValidator.isValidSha256Hex(info.expectedSha256)) {
            _updateDownloadState.value = AppUpdateDownloadState.Error(
                info = info,
                errorCode = UpdateErrorReason.METADATA_INVALID.code,
                reason = UpdateErrorReason.METADATA_INVALID
            )
            return
        }

        val appContext = getApplication<Application>().applicationContext
        val existingReady = AppUpdateInstaller.findAlreadyDownloadedApk(appContext, info)
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
                    info = info,
                    destinationFile = destFile
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
            } catch (t: Throwable) {
                val reason = when (t) {
                    is AppUpdateException -> t.reason
                    is SSLException -> UpdateErrorReason.HTTPS_VALIDATION_FAILED
                    else -> if (UpdateSecurityValidator.isInsufficientStorageCause(t)) {
                        UpdateErrorReason.INSUFFICIENT_STORAGE
                    } else {
                        UpdateErrorReason.NETWORK_FAILURE
                    }
                }
                _updateDownloadState.value = AppUpdateDownloadState.Error(
                    info = info,
                    errorCode = reason.code,
                    reason = reason
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
            } else {
                _updateDownloadState.value = AppUpdateDownloadState.Error(
                    info = ready.info,
                    errorCode = UpdateErrorReason.INCOMPLETE_DOWNLOAD.code,
                    reason = UpdateErrorReason.INCOMPLETE_DOWNLOAD
                )
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
            InstallLaunchResult.FILE_MISSING,
            InstallLaunchResult.CHECKSUM_MISMATCH,
            InstallLaunchResult.CORRUPTED_APK,
            InstallLaunchResult.WRONG_PACKAGE_NAME,
            InstallLaunchResult.OLDER_OR_EQUAL_VERSION,
            InstallLaunchResult.UNEXPECTED_SIGNER,
            InstallLaunchResult.FAILED -> {
                pendingInstallAfterPermission = false
                val reason = result.toUpdateErrorReason() ?: UpdateErrorReason.INSTALL_FAILED
                _updateDownloadState.value = AppUpdateDownloadState.Error(
                    info = info,
                    errorCode = reason.code,
                    reason = reason
                )
            }
        }
    }

    fun openUpdateCatalog() {
        _updateBannerDismissed.value = false
    }

    fun dismissUpdateBanner() {
        _updateBannerDismissed.value = true
        val info = (_updateState.value as? AppUpdateState.UpdateAvailable)?.info ?: return
        val tag = updateTagFor(info)
        viewModelScope.launch {
            repository.setDismissedUpdateTag(tag)
        }
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

    fun openSourceBreakdown(asset: MarketAsset) {
        _selectedAssetForSourceBreakdown.value = asset
    }

    fun closeSourceBreakdown() {
        _selectedAssetForSourceBreakdown.value = null
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
