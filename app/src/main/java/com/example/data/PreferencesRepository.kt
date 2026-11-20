package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.AppPreferences
import com.example.model.CachedMarketSnapshot
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.chandDataStore: DataStore<Preferences> by preferencesDataStore(name = "chand_prefs")

class PreferencesRepository(private val context: Context) {

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
        val CACHED_SNAPSHOT = stringPreferencesKey("cached_snapshot_json")
        val CACHED_UPDATED_AT = longPreferencesKey("cached_updated_at")
    }

    val preferencesFlow: Flow<AppPreferences> = context.chandDataStore.data.map { prefs ->
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

        AppPreferences(
            themeMode = theme,
            language = language,
            usePersianDigits = usePersianDigits,
            isGridView = isGridView,
            pinnedIds = pinnedIds,
            refreshIntervalSeconds = refreshInterval,
            widgetTranslucent = widgetTranslucent,
            widgetOpacityPercent = widgetOpacityPercent
        )
    }

    suspend fun getPreferences(): AppPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.chandDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.chandDataStore.edit {
            it[Keys.LANGUAGE] = language.name
            it[Keys.USE_PERSIAN_DIGITS] = (language == AppLanguage.FA)
        }
    }

    suspend fun setUsePersianDigits(enabled: Boolean) {
        context.chandDataStore.edit { it[Keys.USE_PERSIAN_DIGITS] = enabled }
    }

    suspend fun setIsGridView(isGrid: Boolean) {
        context.chandDataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun setRefreshInterval(seconds: Int) {
        context.chandDataStore.edit { it[Keys.REFRESH_INTERVAL] = seconds }
    }

    suspend fun setWidgetTranslucent(enabled: Boolean) {
        context.chandDataStore.edit { it[Keys.WIDGET_TRANSLUCENT] = enabled }
    }

    suspend fun setWidgetOpacityPercent(percent: Int) {
        context.chandDataStore.edit { it[Keys.WIDGET_OPACITY_PERCENT] = percent }
    }

    suspend fun togglePin(assetId: String): Set<String> {
        var updated: Set<String> = emptySet()
        context.chandDataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_IDS] ?: AppPreferences.DEFAULT_PINNED_IDS
            updated = if (assetId in current) current - assetId else current + assetId
            prefs[Keys.PINNED_IDS] = updated
        }
        return updated
    }

    suspend fun saveCachedSnapshot(snapshot: CachedMarketSnapshot) {
        val encoded = json.encodeToString(snapshot)
        context.chandDataStore.edit { prefs ->
            prefs[Keys.CACHED_SNAPSHOT] = encoded
            prefs[Keys.CACHED_UPDATED_AT] = snapshot.updatedAtMillis
        }
    }

    suspend fun loadCachedSnapshot(): CachedMarketSnapshot? {
        val raw = context.chandDataStore.data.first()[Keys.CACHED_SNAPSHOT] ?: return null
        return runCatching { json.decodeFromString<CachedMarketSnapshot>(raw) }.getOrNull()
    }
}
