package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MarketRepository
import com.example.data.PreferencesRepository
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AssetCategory
import com.example.model.AssetHistoryPoint
import com.example.model.MarketAsset
import com.example.model.MarketUiState
import com.example.model.ThemeMode
import com.example.model.TimeRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()

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

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cached = repository.loadCached()
            if (cached != null && cached.assets.isNotEmpty()) {
                _uiState.value = MarketUiState.Ready(
                    assets = cached.assets,
                    lastUpdatedMillis = cached.updatedAtMillis,
                    isRefreshing = true,
                    sourceSummary = cached.sourceSummary
                )
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

            val sync = repository.refreshMarket()
            if (sync != null && sync.assets.isNotEmpty()) {
                _uiState.value = MarketUiState.Ready(
                    assets = sync.assets,
                    lastUpdatedMillis = sync.updatedAtMillis,
                    isRefreshing = false,
                    sourceSummary = sync.sourceSummary,
                    errorBanner = if (sync.isLive) null else "offline"
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
                _uiState.value = current.copy(isRefreshing = false, errorBanner = "offline")
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

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch {
            repository.setRefreshInterval(seconds)
        }
    }

    fun openAssetDetail(asset: MarketAsset) {
        _selectedAssetForDetail.value = asset
        _selectedTimeRange.value = TimeRange.H24
        loadHistoryFor(asset, TimeRange.H24)
    }

    fun closeAssetDetail() {
        _selectedAssetForDetail.value = null
        historyJob?.cancel()
    }

    fun selectTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        val asset = _selectedAssetForDetail.value ?: return
        loadHistoryFor(asset, range)
    }

    private fun loadHistoryFor(asset: MarketAsset, range: TimeRange) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _isHistoryLoading.value = true
            val points = repository.fetchHistory(asset, range)
            _historyPoints.value = points
            _isHistoryLoading.value = false
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
