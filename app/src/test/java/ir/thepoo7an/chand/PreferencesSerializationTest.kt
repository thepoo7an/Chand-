package ir.thepoo7an.chand

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class PreferencesSerializationTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private fun createIsolatedPrefsRepo(): PreferencesRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = tmpDir.newFile("prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { file }
        return PreferencesRepository(context, dataStore)
    }

    @Test
    fun `preferences DataStore round-trips theme, language, widget opacity, repo slug, and pins`() = runTest {
        val repo = createIsolatedPrefsRepo()

        val initial = repo.getPreferences()
        assertEquals(ThemeMode.AMOLED, initial.themeMode)
        assertEquals(AppLanguage.FA, initial.language)
        assertTrue(initial.usePersianDigits)
        assertTrue(initial.widgetTranslucent)
        assertEquals("USD", initial.widgetHeroAssetId)
        assertEquals(BuildConfig.GITHUB_REPOSITORY, initial.githubRepoSlug)

        repo.setThemeMode(ThemeMode.DARK)
        repo.setLanguage(AppLanguage.EN)
        repo.setIsGridView(false)
        repo.setRefreshInterval(60)
        repo.setWidgetTranslucent(false)
        repo.setWidgetOpacityPercent(45)
        repo.setWidgetHeroAssetId("COIN_EMAMI")
        repo.setGithubRepoSlug("  thepoo7an/chand-iran-currency-gold-crypto  ")
        repo.togglePin("USD")
        repo.togglePin("SOL")

        val updated = repo.getPreferences()
        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertEquals(AppLanguage.EN, updated.language)
        assertFalse(updated.usePersianDigits)
        assertFalse(updated.isGridView)
        assertEquals(60, updated.refreshIntervalSeconds)
        assertFalse(updated.widgetTranslucent)
        assertEquals(45, updated.widgetOpacityPercent)
        assertEquals("COIN_EMAMI", updated.widgetHeroAssetId)
        assertEquals("thepoo7an/chand-iran-currency-gold-crypto", updated.githubRepoSlug)
        assertFalse("USD" in updated.pinnedIds)
        assertTrue("SOL" in updated.pinnedIds)
    }

    @Test
    fun `language defaults to FA, honors saved EN and FA across restarts, falls back on invalid value, and keeps digits separate`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Fresh install defaults to FA + AMOLED, with EN unaffected once saved across DataStore restart
        val fileEn = tmpDir.newFile("lang_en_${System.nanoTime()}.preferences_pb")
        val scopeEn1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repoEn1 = PreferencesRepository(context, PreferenceDataStoreFactory.create(scope = scopeEn1) { fileEn })
        val fresh = repoEn1.getPreferences()
        assertEquals(AppLanguage.FA, fresh.language)
        assertEquals(ThemeMode.AMOLED, fresh.themeMode)
        assertTrue(fresh.usePersianDigits)

        repoEn1.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, repoEn1.getPreferences().language)
        scopeEn1.coroutineContext[Job]?.cancelAndJoin()

        // Simulate app restart with a new DataStore & repository pointing to the same file
        val scopeEn2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repoEn2 = PreferencesRepository(context, PreferenceDataStoreFactory.create(scope = scopeEn2) { fileEn })
        val afterRestartEn = repoEn2.getPreferences()
        assertEquals(AppLanguage.EN, afterRestartEn.language)
        assertFalse(afterRestartEn.usePersianDigits)

        // Separate digits toggle survives restart in EN
        repoEn2.setUsePersianDigits(true)
        scopeEn2.coroutineContext[Job]?.cancelAndJoin()

        val scopeEn3 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repoEn3 = PreferencesRepository(context, PreferenceDataStoreFactory.create(scope = scopeEn3) { fileEn })
        assertEquals(AppLanguage.EN, repoEn3.getPreferences().language)
        assertTrue(repoEn3.getPreferences().usePersianDigits)
        scopeEn3.coroutineContext[Job]?.cancelAndJoin()

        // 2. Explicitly saved FA + separate Persian digits OFF survives restart
        val fileFa = tmpDir.newFile("lang_fa_${System.nanoTime()}.preferences_pb")
        val scopeFa1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repoFa1 = PreferencesRepository(context, PreferenceDataStoreFactory.create(scope = scopeFa1) { fileFa })
        repoFa1.setLanguage(AppLanguage.FA)
        repoFa1.setUsePersianDigits(false)
        scopeFa1.coroutineContext[Job]?.cancelAndJoin()

        val scopeFa2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repoFa2 = PreferencesRepository(context, PreferenceDataStoreFactory.create(scope = scopeFa2) { fileFa })
        val afterRestartFa = repoFa2.getPreferences()
        assertEquals(AppLanguage.FA, afterRestartFa.language)
        assertFalse(afterRestartFa.usePersianDigits)
        scopeFa2.coroutineContext[Job]?.cancelAndJoin()

        // 3. Invalid persisted language string falls back safely to FA
        val fileInvalid = tmpDir.newFile("lang_invalid_${System.nanoTime()}.preferences_pb")
        val scopeInvalid = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsInvalid = PreferenceDataStoreFactory.create(scope = scopeInvalid) { fileInvalid }
        dsInvalid.edit { it[stringPreferencesKey("language")] = "UNSUPPORTED_LOCALE" }
        val repoInvalid = PreferencesRepository(context, dsInvalid)
        assertEquals(AppLanguage.FA, repoInvalid.getPreferences().language)
        scopeInvalid.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun `cached market snapshot survives DataStore JSON round-trip accurately`() = runTest {
        val repo = createIsolatedPrefsRepo()
        val snapshot = CachedMarketSnapshot(
            assets = listOf(
                MarketAsset(
                    id = "USD",
                    symbol = "USD",
                    nameFa = "دلار آمریکا",
                    nameEn = "US Dollar",
                    category = AssetCategory.FIAT,
                    badgeText = "🇺🇸",
                    accentHex = 0xFF10B981,
                    priceToman = 72_450.0,
                    priceUsd = 1.0,
                    change24hPercent = 1.15,
                    change24hToman = 820.0,
                    high24hToman = 72_600.0,
                    low24hToman = 71_600.0,
                    sparkline = listOf(71_600.0, 72_000.0, 72_450.0),
                    sourceLabel = "TGJU (Free)",
                    updatedAtMillis = 1_750_000_000_000L
                )
            ),
            updatedAtMillis = 1_750_000_000_000L,
            sourceSummary = "Free Market (Tehran) • TGJU"
        )

        repo.saveCachedSnapshot(snapshot)
        val restored = repo.loadCachedSnapshot()
        assertNotNull(restored)
        assertEquals(snapshot.updatedAtMillis, restored!!.updatedAtMillis)
        assertEquals(snapshot.sourceSummary, restored.sourceSummary)
        assertEquals(1, restored.assets.size)
        assertEquals(72_450.0, restored.assets.first().priceToman, 0.01)
        assertEquals("TGJU (Free)", restored.assets.first().sourceLabel)
    }

    @Test
    fun `kotlinx serialization ignores unknown forward-compatible fields on history and snapshot payloads`() {
        val json = Json { ignoreUnknownKeys = true }
        val history = AssetHistoryResult(
            points = listOf(AssetHistoryPoint(1_750_000_000L, 71_000.0)),
            sourceLabel = "Wallex",
            isRealData = true,
            cachedAtMillis = 1_750_000_123_000L
        )
        val encoded = json.encodeToString(history)
        val withFutureField = encoded.dropLast(1) + ""","futureFlag":true}"""
        val decoded = json.decodeFromString<AssetHistoryResult>(withFutureField)
        assertEquals("Wallex", decoded.sourceLabel)
        assertEquals(1, decoded.points.size)
        assertEquals(71_000.0, decoded.points.first().priceToman, 0.01)
    }

    @Test
    fun `old-format CachedMarketSnapshot JSON without Stage 2 fields decodes safely with defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val legacyPayload = """
            {
              "assets": [
                {
                  "id": "USD",
                  "symbol": "USD",
                  "nameFa": "دلار آمریکا",
                  "nameEn": "US Dollar",
                  "category": "FIAT",
                  "badgeText": "🇺🇸",
                  "accentHex": 4279286145,
                  "priceToman": 71250.0,
                  "priceUsd": 1.0,
                  "change24hPercent": 0.45,
                  "change24hToman": 320.0,
                  "high24hToman": 71500.0,
                  "low24hToman": 70900.0,
                  "sparkline": [70900.0, 71100.0, 71250.0],
                  "sourceLabel": "TGJU (Free)",
                  "updatedAtMillis": 1740000000000
                },
                {
                  "id": "COIN_EMAMI",
                  "symbol": "SEKE",
                  "nameFa": "سکه امامی",
                  "nameEn": "Emami Coin",
                  "category": "GOLD",
                  "badgeText": "🪙",
                  "accentHex": 4294286859,
                  "priceToman": 74500000.0
                }
              ],
              "updatedAtMillis": 1740000000000,
              "sourceSummary": "Free Market (Tehran) • TGJU"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<CachedMarketSnapshot>(legacyPayload)
        assertEquals(2, decoded.assets.size)
        assertEquals(ir.thepoo7an.chand.model.DataFreshness.CACHED, decoded.freshness)

        val usd = decoded.assets[0]
        assertEquals(71_250.0, usd.priceToman, 0.01)
        assertEquals(71_500.0, usd.high24hToman ?: 0.0, 0.01)
        assertEquals(ir.thepoo7an.chand.model.DataFreshness.CACHED, usd.freshness)
        assertFalse(usd.disputed)
        assertFalse(usd.singleSource)
        assertEquals(null, usd.intrinsicValueToman)

        val emami = decoded.assets[1]
        assertEquals(null, emami.high24hToman)
        assertEquals(null, emami.low24hToman)
        assertEquals(ir.thepoo7an.chand.model.DataFreshness.CACHED, emami.freshness)
    }

    @Test
    fun `live config, dismissal flags, and resetLiveConfig round-trip cleanly in DataStore`() = runTest {
        val repo = createIsolatedPrefsRepo()
        try {
            repo.setLiveConfigUrl("https://cdn.example.com/chand-live-config.json")
            repo.setDismissedLiveNoticeId("notice_42")
            repo.setDismissedUpdateTag("1.2.0#25")

            val prefs = repo.getPreferences()
            assertEquals("https://cdn.example.com/chand-live-config.json", prefs.liveConfigUrl)
            assertEquals("notice_42", prefs.dismissedLiveNoticeId)
            assertEquals("1.2.0#25", prefs.dismissedUpdateTag)

            val cfg = ir.thepoo7an.chand.model.RemoteAppConfig(
                configVersion = 7,
                minAppVersionCode = 1,
                summaryFa = "اصلاح مسیر تتر",
                summaryEn = "Updated USDT mirror",
                disabledAssetIds = listOf("DOGE"),
                appliedChanges = listOf(
                    ir.thepoo7an.chand.model.RemoteLiveChange("آپدیت منبع", "Updated mirror", "ENDPOINT")
                )
            )
            repo.saveLiveConfig(cfg, syncedAtMillis = 1_760_000_000_000L)

            val hydrated = repo.hydrateLiveConfig()
            assertEquals(7, hydrated.configVersion)
            assertEquals(listOf("DOGE"), hydrated.disabledAssetIds)
            assertEquals(1_760_000_000_000L, repo.getPreferences().liveConfigLastSyncMillis)

            repo.resetLiveConfig()
            assertTrue(ir.thepoo7an.chand.network.LiveConfigRegistry.currentConfig().isBuiltInDefault)
            assertEquals(0L, repo.getPreferences().liveConfigLastSyncMillis)
        } finally {
            ir.thepoo7an.chand.network.LiveConfigRegistry.resetToDefault()
        }
    }
}
