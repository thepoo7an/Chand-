package com.example.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.model.AppPreferences
import com.example.model.AssetHistoryPoint
import com.example.model.CachedMarketSnapshot
import com.example.model.MarketAsset
import com.example.model.TimeRange
import com.example.network.MarketRemoteDataSource
import com.example.widget.ChandGlanceWidget
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
            runCatching { ChandGlanceWidget().updateAll(context) }
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
        runCatching { ChandGlanceWidget().updateAll(context) }
    }

    suspend fun setThemeMode(mode: com.example.model.ThemeMode) {
        prefsRepo.setThemeMode(mode)
        runCatching { ChandGlanceWidget().updateAll(context) }
    }

    suspend fun setLanguage(language: com.example.model.AppLanguage) {
        prefsRepo.setLanguage(language)
        runCatching { ChandGlanceWidget().updateAll(context) }
    }

    suspend fun setUsePersianDigits(enabled: Boolean) {
        prefsRepo.setUsePersianDigits(enabled)
        runCatching { ChandGlanceWidget().updateAll(context) }
    }

    suspend fun setRefreshInterval(seconds: Int) = prefsRepo.setRefreshInterval(seconds)
}
