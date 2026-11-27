package ir.thepoo7an.chand

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.RemoteEndpointsConfig
import ir.thepoo7an.chand.model.RemoteFeaturesConfig
import ir.thepoo7an.chand.model.RemoteValidationConfig
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.network.MarketValidationConfig
import ir.thepoo7an.chand.network.MarketValidationHelpers
import ir.thepoo7an.chand.network.RemoteConfigService
import ir.thepoo7an.chand.network.RemoteConfigSyncResult
import ir.thepoo7an.chand.ui.screens.formatRawInput
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveConfigAndBugfixTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Before
    fun setUp() {
        LiveConfigRegistry.resetToDefault()
    }

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    @Test
    fun `LiveConfigRegistry sanitizes URLs, clamps bounds, prevents disabling USD, and overrides validation rules`() {
        val raw = RemoteAppConfig(
            configVersion = 4,
            minAppVersionCode = 1,
            endpoints = RemoteEndpointsConfig(
                tgjuAjaxUrl = "http://insecure.example.com/ajax.json",
                wallexMarketsUrl = " https://mirror.example.com/v1/markets ",
                frankfurterLatestUrls = listOf("http://bad.test/latest", "https://fx.example.com/latest")
            ),
            validation = RemoteValidationConfig(
                cryptoDisagreementThresholdPct = 0.05, // below min 0.5 -> clamped to 0.5
                fiatDisagreementThresholdPct = 6.5,
                cryptoLiveMaxAgeMinutes = 12,
                fiatGoldLiveMaxAgeMinutes = 45,
                spikeThreshold24hPct = 22.0,
                spikeThresholdCryptoMultiDayPct = 55.0
            ),
            features = RemoteFeaturesConfig(
                enableConverterEquivalents = false,
                minAutoRefreshSeconds = 1 // below min 5 -> clamped to 5
            ),
            disabledAssetIds = listOf("usd", "DOGE", "doge"),
            appliedChanges = listOf()
        )

        assertTrue(LiveConfigRegistry.applyConfig(raw, appVersionCode = 10))
        val active = LiveConfigRegistry.currentConfig()

        // HTTP URLs rejected, HTTPS URLs trimmed & kept
        assertEquals("", active.endpoints.tgjuAjaxUrl)
        assertEquals("https://mirror.example.com/v1/markets", active.endpoints.wallexMarketsUrl)
        assertEquals(listOf("https://fx.example.com/latest"), active.endpoints.frankfurterLatestUrls)

        // Safe bounds clamping & validation wiring
        assertEquals(0.5, MarketValidationConfig.effectiveCryptoDisagreementThresholdPct(), 0.001)
        assertEquals(6.5, MarketValidationConfig.effectiveFiatDisagreementThresholdPct(), 0.001)
        assertEquals(12L * 60_000L, MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis())
        assertEquals(45L * 60_000L, MarketValidationConfig.effectiveFiatGoldLiveMaxAgeMillis())
        assertEquals(22.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.CRYPTO, TimeRange.H24), 0.001)
        assertEquals(55.0, MarketValidationConfig.spikeThresholdPct(AssetCategory.CRYPTO, TimeRange.D7), 0.001)
        assertEquals(5, active.features.minAutoRefreshSeconds)
        assertFalse(active.features.enableConverterEquivalents)

        // Critical USD anchor cannot be disabled
        assertEquals(listOf("DOGE"), active.disabledAssetIds)

        // Freshness helper uses overridden crypto max age (12m instead of default 5m)
        val nowMs = 1_760_000_000_000L
        assertEquals(
            DataFreshness.LIVE,
            MarketValidationHelpers.deriveFreshness(
                category = AssetCategory.CRYPTO,
                hasUsableValue = true,
                fetchedThisRefresh = true,
                isCachedSource = false,
                sourceTimestampMillis = nowMs - (10L * 60_000L),
                nowMillis = nowMs
            )
        )
    }

    @Test
    fun `isSafeHttpsUrl rejects credential smuggling, private hosts, IP literals and malformed labels`() {
        // Accepted: real, public, mirror-style HTTPS hosts (the mirror feature must keep working).
        assertTrue(LiveConfigRegistry.isSafeHttpsUrl("https://mirror.example.com/v1/markets"))
        assertTrue(LiveConfigRegistry.isSafeHttpsUrl("https://cdn.provider.example.net/path?q=1"))
        assertTrue(LiveConfigRegistry.isSafeHttpsUrl("https://api.example.com:8443/data"))

        // Rejected: credentials, private/loopback hosts, IP literals, IPv6, malformed labels.
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://user:pass@mirror.example.com/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://mirror.example.com@evil.test/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://127.0.0.1/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://10.0.0.5:8080/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://192.168.1.7/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://169.254.169.254/latest/meta-data"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://172.20.0.1/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://[::1]/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://localhost/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://api.localhost/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://printer.local/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://svc.internal/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://bad..example.com/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://-bad.example.com/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://example.com:notaport/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://example.com:99999/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("https://example.com\\@evil.test/x"))
        assertFalse(LiveConfigRegistry.isSafeHttpsUrl("http://mirror.example.com/x"))
    }

    @Test
    fun `RemoteConfigService orders candidates custom HTTPS then jsDelivr then GitHub Raw and rejects older APK incompatibility`() = runTest {
        val candidates = RemoteConfigService.buildCandidateUrls(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            customConfigUrl = "https://custom.example.com/live.json"
        )
        assertEquals(
            listOf(
                "https://custom.example.com/live.json",
                "https://cdn.jsdelivr.net/gh/thepoo7an/chand-iran-currency-gold-crypto@main/chand-live-config.json",
                "https://raw.githubusercontent.com/thepoo7an/chand-iran-currency-gold-crypto/main/chand-live-config.json"
            ),
            candidates
        )

        // Incompatible minAppVersionCode should fail cleanly without replacing current config
        val incompatibleJson = """{"configVersion":99,"minAppVersionCode":500,"summaryEn":"Future build"}"""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(incompatibleJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        // Signature enforcement is covered by LiveConfigSignatureTest.
        val service = RemoteConfigService(client, trustedConfigKeys = emptyMap())
        val res = service.fetchLiveConfig(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            customConfigUrl = "https://custom.example.com/live.json",
            appVersionCode = 10
        )
        assertTrue(res is RemoteConfigSyncResult.IncompatibleVersion)
    }

    @Test
    fun `MarketRepository syncLiveConfig fetches online config, persists it, and applies extra assets + overrides cleanly`() = runTest {
        val configPayload = """
            {
              "configVersion": 3,
              "minAppVersionCode": 1,
              "summaryFa": "افزودن آواکس و اصلاح تتر",
              "summaryEn": "Added AVAX and tuned thresholds",
              "disabledAssetIds": ["SHIB"],
              "extraAssets": [
                {
                  "id": "AVAX",
                  "symbol": "AVAX",
                  "nameFa": "آوالانچ",
                  "nameEn": "Avalanche",
                  "category": "CRYPTO",
                  "badgeText": "🔺",
                  "accentHex": 4293467747,
                  "coinGeckoId": "avalanche-2"
                }
              ],
              "appliedChanges": [
                {"titleFa": "افزودن رمزارز AVAX", "titleEn": "Added AVAX crypto asset", "category": "ASSET"}
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(configPayload.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStoreFile = tmpDir.newFile("live_cfg_repo_${System.nanoTime()}.preferences_pb")
        val prefsRepo = PreferencesRepository(
            context,
            PreferenceDataStoreFactory.create { dataStoreFile }
        )
        val repo = MarketRepository(
            context = context,
            prefsRepo = prefsRepo,
            remote = MarketRemoteDataSource(),
            remoteConfigService = RemoteConfigService(client, trustedConfigKeys = emptyMap())
        )

        val syncResult = repo.syncLiveConfig("thepoo7an/chand-iran-currency-gold-crypto", "https://cdn.example.com/live.json")
        assertTrue(syncResult is RemoteConfigSyncResult.Applied)
        val applied = syncResult as RemoteConfigSyncResult.Applied
        assertEquals(3, applied.config.configVersion)
        assertEquals("AVAX", applied.config.extraAssets.firstOrNull()?.id)
        assertEquals(listOf("SHIB"), LiveConfigRegistry.currentConfig().disabledAssetIds)

        // Cold hydrate from DataStore restores the synced live config
        LiveConfigRegistry.resetToDefault()
        val rehydrated = repo.hydrateLiveConfig()
        assertEquals(3, rehydrated.configVersion)
        assertEquals("avalanche-2", rehydrated.extraAssets.firstOrNull()?.coinGeckoId)
    }

    @Test
    fun `Converter formatting preserves 16-digit input grouping and 8-decimal fractional precision`() {
        assertEquals("1,234,567,890,123,456", formatRawInput("1234567890123456"))
        assertEquals("1,234,567,890,123,456.78", formatRawInput("1234567890123456.78"))
        assertEquals("0.00001425", NumberFormatters.formatConverterAmount(0.00001425, usePersianDigits = false))
        assertEquals("۰.۰۰۰۰۱۴۲۵", NumberFormatters.formatConverterAmount(0.00001425, usePersianDigits = true))
        assertEquals("0", NumberFormatters.formatConverterAmount(0.0, usePersianDigits = false))
    }
}
