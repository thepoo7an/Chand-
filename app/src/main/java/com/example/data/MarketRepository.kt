package com.example.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.AssetHistoryPoint
import com.example.model.CachedMarketSnapshot
import com.example.model.MarketAsset
import com.example.model.ThemeMode
import com.example.model.TimeRange
import com.example.network.MarketRemoteDataSource
import com.example.widget.ChandCompact2x1Widget
import com.example.widget.ChandCompactCryptoWidget
import com.example.widget.ChandSingleAssetWidget
import com.example.widget.ChandWideListWidget
import kotlinx.coroutines.flow.Flow

data class MarketSyncResult(
    val assets: List<MarketAsset>,
    val updatedAtMillis: Long,
    val sourceSummary: String,
    val isLive: Boolean
)

class MarketRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val remote: MarketRemoteDataSource = MarketRemoteDataSource()
) {
    val preferencesFlow: Flow<AppPreferences> = prefsRepo.preferencesFlow

    suspend fun loadCached(): CachedMarketSnapshot? = prefsRepo.loadCachedSnapshot()

    suspend fun refreshMarket(): MarketSyncResult? {
        val result = remote.fetchLiveMarket()
        if (result.isLive && result.assets.isNotEmpty()) {
            val snapshot = CachedMarketSnapshot(
                assets = result.assets,
                updatedAtMillis = result.fetchedAtMillis,
                sourceSummary = result.sourceSummary
            )
            prefsRepo.saveCachedSnapshot(snapshot)
            updateAllWidgets()
            return MarketSyncResult(
                assets = result.assets,
                updatedAtMillis = result.fetchedAtMillis,
                sourceSummary = result.sourceSummary,
                isLive = true
            )
        }

        val cached = prefsRepo.loadCachedSnapshot() ?: return null
        return MarketSyncResult(
            assets = cached.assets,
            updatedAtMillis = cached.updatedAtMillis,
            sourceSummary = cached.sourceSummary,
            isLive = false
        )
    }

    suspend fun fetchHistory(asset: MarketAsset, range: TimeRange): List<AssetHistoryPoint> =
        remote.fetchAssetHistory(asset, range)

    suspend fun togglePin(assetId: String) {
        prefsRepo.togglePin(assetId)
        updateAllWidgets()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        prefsRepo.setThemeMode(mode)
        updateAllWidgets()
    }

    suspend fun setLanguage(language: AppLanguage) {
        prefsRepo.setLanguage(language)
        updateAllWidgets()
    }

    suspend fun setUsePersianDigits(enabled: Boolean) {
        prefsRepo.setUsePersianDigits(enabled)
        updateAllWidgets()
    }

    suspend fun setIsGridView(isGrid: Boolean) = prefsRepo.setIsGridView(isGrid)

    suspend fun setRefreshInterval(seconds: Int) = prefsRepo.setRefreshInterval(seconds)

    suspend fun setWidgetTranslucent(enabled: Boolean) {
        prefsRepo.setWidgetTranslucent(enabled)
        updateAllWidgets()
    }

    suspend fun setWidgetOpacityPercent(percent: Int) {
        prefsRepo.setWidgetOpacityPercent(percent)
        updateAllWidgets()
    }

    private suspend fun updateAllWidgets() {
        runCatching { ChandSingleAssetWidget().updateAll(context) }
        runCatching { ChandWideListWidget().updateAll(context) }
        runCatching { ChandCompactCryptoWidget().updateAll(context) }
        runCatching { ChandCompact2x1Widget().updateAll(context) }
    }
}
