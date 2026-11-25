package ir.thepoo7an.chand.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.ThemeMode
import ir.thepoo7an.chand.network.LiveConfigRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.chandDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chand_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class PreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.chandDataStore
) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val USE_PERSIAN_DIGITS = booleanPreferencesKey("use_persian_digits")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val PINNED_IDS = stringSetPreferencesKey("pinned_ids")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_seconds")
        val WIDGET_TRANSLUCENT = booleanPreferencesKey("widget_translucent")
        val WIDGET_OPACITY_PERCENT = intPreferencesKey("widget_opacity_percent")
        val WIDGET_HERO_ASSET_ID = stringPreferencesKey("widget_hero_asset_id")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repo_slug")
        val CACHED_SNAPSHOT = stringPreferencesKey("cached_snapshot_json")
        val CACHED_UPDATED_AT = longPreferencesKey("cached_updated_at")
        val LIVE_CONFIG_URL = stringPreferencesKey("live_config_url")
        val LIVE_CONFIG_JSON = stringPreferencesKey("live_config_json")
        val LIVE_CONFIG_LAST_SYNC = longPreferencesKey("live_config_last_sync")
        val DISMISSED_LIVE_NOTICE_ID = stringPreferencesKey("dismissed_live_notice_id")
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
    }

    private val safeDataFlow: Flow<Preferences> = dataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    val preferencesFlow: Flow<AppPreferences> = safeDataFlow.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.AMOLED

        val language = prefs[Keys.LANGUAGE]?.let { name ->
            AppLanguage.entries.firstOrNull { it.name == name }
        } ?: AppLanguage.EN

        val usePersianDigits = prefs[Keys.USE_PERSIAN_DIGITS] ?: (language == AppLanguage.FA)
        val isGridView = prefs[Keys.IS_GRID_VIEW] ?: true
        val pinnedIds = prefs[Keys.PINNED_IDS] ?: AppPreferences.DEFAULT_PINNED_IDS
        val refreshInterval = prefs[Keys.REFRESH_INTERVAL] ?: 30
        val widgetTranslucent = prefs[Keys.WIDGET_TRANSLUCENT] ?: true
        val widgetOpacityPercent = prefs[Keys.WIDGET_OPACITY_PERCENT] ?: 70
        val widgetHeroAssetId = prefs[Keys.WIDGET_HERO_ASSET_ID]?.takeIf { it.isNotBlank() } ?: "USD"
        val githubRepoSlug = prefs[Keys.GITHUB_REPO_SLUG]?.takeIf { it.isNotBlank() }
            ?: ir.thepoo7an.chand.BuildConfig.GITHUB_REPOSITORY
        val liveConfigUrl = prefs[Keys.LIVE_CONFIG_URL].orEmpty()
        val liveConfigLastSync = prefs[Keys.LIVE_CONFIG_LAST_SYNC] ?: 0L
        val dismissedLiveNoticeId = prefs[Keys.DISMISSED_LIVE_NOTICE_ID].orEmpty()
        val dismissedUpdateTag = prefs[Keys.DISMISSED_UPDATE_TAG].orEmpty()

        AppPreferences(
            themeMode = theme,
            language = language,
            usePersianDigits = usePersianDigits,
            isGridView = isGridView,
            pinnedIds = pinnedIds,
            refreshIntervalSeconds = refreshInterval,
            widgetTranslucent = widgetTranslucent,
            widgetOpacityPercent = widgetOpacityPercent,
            widgetHeroAssetId = widgetHeroAssetId,
            githubRepoSlug = githubRepoSlug,
            liveConfigUrl = liveConfigUrl,
            liveConfigLastSyncMillis = liveConfigLastSync,
            dismissedLiveNoticeId = dismissedLiveNoticeId,
            dismissedUpdateTag = dismissedUpdateTag
        )
    }

    val remoteConfigFlow: Flow<RemoteAppConfig> = safeDataFlow.map { prefs ->
        decodeAndSanitizeConfig(prefs[Keys.LIVE_CONFIG_JSON]) ?: RemoteAppConfig()
    }

    private fun decodeAndSanitizeConfig(raw: String?): RemoteAppConfig? {
        if (raw.isNullOrBlank()) return null
        val decoded = runCatching { json.decodeFromString<RemoteAppConfig>(raw) }.getOrNull() ?: return null
        if (!LiveConfigRegistry.isCompatible(decoded)) return null
        return LiveConfigRegistry.sanitizeConfig(decoded)
    }

    val cachedSnapshotFlow: Flow<CachedMarketSnapshot?> = safeDataFlow.map { prefs ->
        val raw = prefs[Keys.CACHED_SNAPSHOT] ?: return@map null
        runCatching { json.decodeFromString<CachedMarketSnapshot>(raw) }.getOrNull()
    }

    suspend fun getPreferences(): AppPreferences =
        runCatching { preferencesFlow.first() }.getOrDefault(AppPreferences())

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit {
            it[Keys.LANGUAGE] = language.name
            it[Keys.USE_PERSIAN_DIGITS] = (language == AppLanguage.FA)
        }
    }

    suspend fun setUsePersianDigits(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_PERSIAN_DIGITS] = enabled }
    }

    suspend fun setIsGridView(isGrid: Boolean) {
        dataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun setRefreshInterval(seconds: Int) {
        dataStore.edit { it[Keys.REFRESH_INTERVAL] = seconds }
    }

    suspend fun setWidgetTranslucent(enabled: Boolean) {
        dataStore.edit { it[Keys.WIDGET_TRANSLUCENT] = enabled }
    }

    suspend fun setWidgetOpacityPercent(percent: Int) {
        dataStore.edit { it[Keys.WIDGET_OPACITY_PERCENT] = percent }
    }

    suspend fun setWidgetHeroAssetId(assetId: String) {
        dataStore.edit { it[Keys.WIDGET_HERO_ASSET_ID] = assetId }
    }

    suspend fun setGithubRepoSlug(slug: String) {
        dataStore.edit { it[Keys.GITHUB_REPO_SLUG] = slug.trim() }
    }

    suspend fun setLiveConfigUrl(url: String) {
        dataStore.edit { it[Keys.LIVE_CONFIG_URL] = url.trim() }
    }

    suspend fun setDismissedLiveNoticeId(id: String) {
        dataStore.edit { it[Keys.DISMISSED_LIVE_NOTICE_ID] = id.trim() }
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        dataStore.edit { it[Keys.DISMISSED_UPDATE_TAG] = tag.trim() }
    }

    suspend fun hydrateLiveConfig(): RemoteAppConfig {
        val raw = runCatching { safeDataFlow.first()[Keys.LIVE_CONFIG_JSON] }.getOrNull()
        val parsed = decodeAndSanitizeConfig(raw)
        if (parsed != null) {
            LiveConfigRegistry.applyConfig(parsed)
            return parsed
        }
        return LiveConfigRegistry.currentConfig()
    }

    suspend fun saveLiveConfig(config: RemoteAppConfig, syncedAtMillis: Long) {
        val clean = LiveConfigRegistry.sanitizeConfig(config)
        val encoded = json.encodeToString(clean)
        dataStore.edit { prefs ->
            prefs[Keys.LIVE_CONFIG_JSON] = encoded
            prefs[Keys.LIVE_CONFIG_LAST_SYNC] = syncedAtMillis
        }
        LiveConfigRegistry.applyConfig(clean)
    }

    suspend fun resetLiveConfig() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.LIVE_CONFIG_JSON)
            prefs.remove(Keys.LIVE_CONFIG_LAST_SYNC)
        }
        LiveConfigRegistry.resetToDefault()
    }

    suspend fun togglePin(assetId: String): Set<String> {
        var updated: Set<String> = emptySet()
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_IDS] ?: AppPreferences.DEFAULT_PINNED_IDS
            updated = if (assetId in current) current - assetId else current + assetId
            prefs[Keys.PINNED_IDS] = updated
        }
        return updated
    }

    suspend fun saveCachedSnapshot(snapshot: CachedMarketSnapshot) {
        val encoded = json.encodeToString(snapshot)
        dataStore.edit { prefs ->
            prefs[Keys.CACHED_SNAPSHOT] = encoded
            prefs[Keys.CACHED_UPDATED_AT] = snapshot.updatedAtMillis
        }
    }

    suspend fun loadCachedSnapshot(): CachedMarketSnapshot? {
        val raw = runCatching { safeDataFlow.first()[Keys.CACHED_SNAPSHOT] }.getOrNull()
            ?: return null
        return runCatching { json.decodeFromString<CachedMarketSnapshot>(raw) }.getOrNull()
    }
}
