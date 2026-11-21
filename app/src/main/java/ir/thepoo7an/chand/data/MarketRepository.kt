package ir.thepoo7an.chand.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.MarketDataSource
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.widget.WidgetSyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class MarketSyncResult(
    val assets: List<MarketAsset>,
    val updatedAtMillis: Long,
    val sourceSummary: String,
    val isLive: Boolean
)

class MarketRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val remote: MarketDataSource = MarketRemoteDataSource(),
    private val updateService: AppUpdateService = AppUpdateService()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val historyCacheDir by lazy { File(context.filesDir, "history_cache").apply { mkdirs() } }

    val preferencesFlow: Flow<AppPreferences> = prefsRepo.preferencesFlow

    suspend fun loadCached(): CachedMarketSnapshot? = prefsRepo.loadCachedSnapshot()

    suspend fun refreshMarket(): MarketSyncResult {
        val cached = prefsRepo.loadCachedSnapshot()
        val lastKnownUsd = cached?.assets?.firstOrNull { it.id == "USD" }?.priceToman?.takeIf { it > 1_000.0 }
        val result = remote.fetchLiveMarket(lastKnownUsdToman = lastKnownUsd)
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

        if (cached != null && cached.assets.isNotEmpty()) {
            return MarketSyncResult(
                assets = cached.assets,
                updatedAtMillis = cached.updatedAtMillis,
                sourceSummary = cached.sourceSummary,
                isLive = false
            )
        }

        val fallback = MarketPriceMapper.offlineReferenceCatalog()
        return MarketSyncResult(
            assets = fallback.assets,
            updatedAtMillis = fallback.updatedAtMillis,
            sourceSummary = fallback.sourceSummary,
            isLive = false
        )
    }

    suspend fun fetchHistory(
        asset: MarketAsset,
        range: TimeRange,
        forceRefresh: Boolean = false
    ): AssetHistoryResult = withContext(Dispatchers.IO) {
        val cacheFile = File(historyCacheDir, "${asset.id}_${range.name}.json")
        val ttlMillis = if (range == TimeRange.H24) 3 * 60 * 1000L else 20 * 60 * 1000L
        val cached = readHistoryCache(cacheFile)

        if (!forceRefresh && cached != null && cached.isRealData && cached.points.size >= 2) {
            val age = System.currentTimeMillis() - cached.cachedAtMillis
            if (age in 0..ttlMillis) {
                return@withContext cached
            }
        }

        val fresh = remote.fetchAssetHistory(asset, range)
        if (fresh.isRealData && fresh.points.size >= 2) {
            val toSave = fresh.copy(cachedAtMillis = System.currentTimeMillis())
            writeHistoryCache(cacheFile, toSave)
            return@withContext toSave
        }

        if (cached != null && cached.isRealData && cached.points.size >= 2) {
            return@withContext cached
        }

        fresh
    }

    private fun readHistoryCache(file: File): AssetHistoryResult? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString<AssetHistoryResult>(file.readText())
    }.getOrNull()

    private fun writeHistoryCache(file: File, result: AssetHistoryResult) {
        runCatching { file.writeText(json.encodeToString(result)) }
    }

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

    suspend fun setGithubRepoSlug(slug: String) {
        prefsRepo.setGithubRepoSlug(slug)
    }

    suspend fun checkForUpdate(repoSlug: String): AppUpdateState =
        updateService.checkForUpdate(repoSlug)

    private suspend fun updateAllWidgets() {
        WidgetSyncCoordinator.updateAllWidgets(context)
    }
}
