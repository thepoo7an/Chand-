package ir.thepoo7an.chand.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.AndroidApkPackageInspector
import ir.thepoo7an.chand.network.AppUpdateInfo
import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.HistoryNormalizer
import ir.thepoo7an.chand.network.InFlightRequestDeduplicator
import ir.thepoo7an.chand.network.MarketDataSource
import ir.thepoo7an.chand.network.MarketPriceMapper
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.RemoteConfigService
import ir.thepoo7an.chand.network.RemoteConfigSyncResult
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
    val freshness: DataFreshness = DataFreshness.CACHED
) {
    val isLive: Boolean
        get() = freshness == DataFreshness.LIVE || freshness == DataFreshness.DELAYED
}

class MarketRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val remote: MarketDataSource = MarketRemoteDataSource(),
    private val updateService: AppUpdateService = AppUpdateService(
        apkInspector = AndroidApkPackageInspector(context)
    ),
    private val remoteConfigService: RemoteConfigService = RemoteConfigService(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val historyCacheDir by lazy { File(context.filesDir, "history_cache").apply { mkdirs() } }
    private val refreshDeduplicator = InFlightRequestDeduplicator<String, MarketSyncResult>()
    private val historyDeduplicator = InFlightRequestDeduplicator<String, AssetHistoryResult>()

    val preferencesFlow: Flow<AppPreferences> = prefsRepo.preferencesFlow
    val remoteConfigFlow: Flow<RemoteAppConfig> = prefsRepo.remoteConfigFlow

    suspend fun loadCached(): CachedMarketSnapshot? = prefsRepo.loadCachedSnapshot()

    suspend fun hydrateLiveConfig(): RemoteAppConfig = prefsRepo.hydrateLiveConfig()

    suspend fun refreshMarket(): MarketSyncResult = refreshDeduplicator.execute("refresh") {
        prefsRepo.hydrateLiveConfig()
        val cached = prefsRepo.loadCachedSnapshot()
        val cachedAssetsById = cached?.assets?.associateBy { it.id }.orEmpty()
        val lastKnownUsd = cachedAssetsById["USD"]?.priceToman?.takeIf { it > 1_000.0 }
        val result = remote.fetchLiveMarket(
            lastKnownUsdToman = lastKnownUsd,
            cachedAssetsById = cachedAssetsById
        )
        if (result.isLive && result.assets.isNotEmpty()) {
            val snapshot = CachedMarketSnapshot(
                assets = result.assets,
                updatedAtMillis = result.fetchedAtMillis,
                sourceSummary = result.sourceSummary,
                freshness = result.freshness
            )
            prefsRepo.saveCachedSnapshot(snapshot)
            updateAllWidgets()
            return@execute MarketSyncResult(
                assets = result.assets,
                updatedAtMillis = result.fetchedAtMillis,
                sourceSummary = result.sourceSummary,
                freshness = result.freshness
            )
        }

        if (result.freshness == DataFreshness.CACHED && result.assets.isNotEmpty()) {
            return@execute MarketSyncResult(
                assets = result.assets,
                updatedAtMillis = result.fetchedAtMillis,
                sourceSummary = result.sourceSummary,
                freshness = DataFreshness.CACHED
            )
        }

        if (cached != null && cached.assets.isNotEmpty()) {
            val cachedMarked = cached.assets.map { asset ->
                if (!asset.hasUsablePrimaryPrice || asset.freshness == DataFreshness.UNAVAILABLE) {
                    asset.copy(freshness = DataFreshness.UNAVAILABLE)
                } else {
                    asset.copy(freshness = DataFreshness.CACHED)
                }
            }
            return@execute MarketSyncResult(
                assets = cachedMarked,
                updatedAtMillis = cached.updatedAtMillis,
                sourceSummary = cached.sourceSummary,
                freshness = DataFreshness.CACHED
            )
        }

        val fallback = MarketPriceMapper.offlineReferenceCatalog()
        MarketSyncResult(
            assets = fallback.assets,
            updatedAtMillis = fallback.updatedAtMillis,
            sourceSummary = fallback.sourceSummary,
            freshness = DataFreshness.UNAVAILABLE
        )
    }

    suspend fun fetchHistory(
        asset: MarketAsset,
        range: TimeRange,
        forceRefresh: Boolean = false
    ): AssetHistoryResult = withContext(Dispatchers.IO) {
        val cacheFile = File(historyCacheDir, "${asset.id}_${range.name}.json")
        val ttlMillis = if (range == TimeRange.H24) HISTORY_H24_CACHE_TTL_MS else HISTORY_MULTI_DAY_CACHE_TTL_MS
        val cached = readHistoryCache(cacheFile)

        if (!forceRefresh && cached != null && cached.isRealData) {
            val normalizedCached = cached.copy(
                points = HistoryNormalizer.normalize(
                    points = cached.points,
                    category = asset.category,
                    range = range
                )
            )
            if (normalizedCached.points.size >= 2) {
                val age = clockMillis() - normalizedCached.cachedAtMillis
                if (age in 0..ttlMillis) {
                    return@withContext normalizedCached
                }
            }
        }

        val dedupeKey = "${asset.id}|${range.name}"
        historyDeduplicator.execute(dedupeKey) {
            val freshRaw = remote.fetchAssetHistory(asset, range)
            val fresh = if (freshRaw.isRealData) {
                freshRaw.copy(
                    points = HistoryNormalizer.normalize(
                        points = freshRaw.points,
                        category = asset.category,
                        range = range
                    )
                )
            } else {
                freshRaw
            }
            if (fresh.isRealData && fresh.points.size >= 2) {
                val toSave = fresh.copy(cachedAtMillis = clockMillis())
                writeHistoryCache(cacheFile, toSave)
                return@execute toSave
            }

            if (cached != null && cached.isRealData) {
                val normalizedCached = cached.copy(
                    points = HistoryNormalizer.normalize(
                        points = cached.points,
                        category = asset.category,
                        range = range
                    )
                )
                if (normalizedCached.points.size >= 2) {
                    return@execute normalizedCached
                }
            }

            fresh
        }
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

    suspend fun setWidgetHeroAssetId(assetId: String) {
        prefsRepo.setWidgetHeroAssetId(assetId)
        updateAllWidgets()
    }

    suspend fun setGithubRepoSlug(slug: String) {
        prefsRepo.setGithubRepoSlug(slug)
    }

    suspend fun setLiveConfigUrl(url: String) {
        prefsRepo.setLiveConfigUrl(url)
    }

    suspend fun setDismissedLiveNoticeId(id: String) {
        prefsRepo.setDismissedLiveNoticeId(id)
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        prefsRepo.setDismissedUpdateTag(tag)
    }

    suspend fun syncLiveConfig(
        repoSlug: String,
        customUrl: String
    ): RemoteConfigSyncResult {
        prefsRepo.hydrateLiveConfig()
        val result = remoteConfigService.fetchLiveConfig(
            repoSlug = repoSlug,
            customConfigUrl = customUrl
        )
        if (result is RemoteConfigSyncResult.Applied) {
            prefsRepo.saveLiveConfig(result.config, result.syncedAtMillis)
            updateAllWidgets()
        }
        return result
    }

    suspend fun resetLiveConfig() {
        prefsRepo.resetLiveConfig()
        updateAllWidgets()
    }

    suspend fun checkForUpdate(repoSlug: String): AppUpdateState =
        updateService.checkForUpdate(repoSlug)

    suspend fun downloadUpdateApk(
        info: AppUpdateInfo,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = updateService.downloadApk(
        downloadUrl = info.downloadUrl,
        destinationFile = destinationFile,
        expectedSizeBytes = info.matchedApkSizeBytes,
        expectedSha256 = info.expectedSha256,
        expectedPackageName = info.expectedPackageName,
        expectedVersionCode = info.latestVersionCode,
        currentVersionCode = info.currentVersionCode,
        onProgress = onProgress
    )

    private suspend fun updateAllWidgets() {
        WidgetSyncCoordinator.updateAllWidgets(context)
    }

    companion object {
        const val HISTORY_H24_CACHE_TTL_MS = 3L * 60L * 1000L
        const val HISTORY_MULTI_DAY_CACHE_TTL_MS = 20L * 60L * 1000L
    }
}
