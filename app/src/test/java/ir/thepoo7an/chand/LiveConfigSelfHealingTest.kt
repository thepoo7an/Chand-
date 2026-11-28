package ir.thepoo7an.chand

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.RemoteEndpointsConfig
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.RemoteConfigService
import ir.thepoo7an.chand.network.RemoteConfigSyncResult
import kotlinx.coroutines.runBlocking
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

/**
 * Covers the self-healing side of the live config: expiry handling and the automatic rollback to
 * built-in defaults after repeated total refresh failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class LiveConfigSelfHealingTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = tmpDir.newFile("healing_${System.nanoTime()}.preferences_pb")
        repo = PreferencesRepository(
            context,
            PreferenceDataStoreFactory.create { file }
        )
        // `ChandApplication.onCreate` hydrates the *app-level* store on a background coroutine, and
        // that store outlives a single test (the `preferencesDataStore` delegate is a process-wide
        // singleton). A config persisted by an earlier test class could therefore be applied here
        // mid-test and make these assertions flap. Clear it before resetting the registry so the
        // in-flight hydration has nothing to apply.
        runCatching { PreferencesRepository(context).resetLiveConfig() }
        LiveConfigRegistry.resetToDefault()
    }

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    private fun remoteConfig(version: Int, expiresAtIso: String = ""): RemoteAppConfig = RemoteAppConfig(
        configVersion = version,
        minAppVersionCode = 1,
        summaryEn = "Remote config v$version",
        endpoints = RemoteEndpointsConfig(tgjuAjaxUrl = "https://mirror.example.com/tgju"),
        expiresAtIso = expiresAtIso
    )

    // --- expiry ---------------------------------------------------------------------------

    @Test
    fun `expiry only triggers for a parseable timestamp that has been reached`() {
        val now = 1_764_201_600_000L // 2025-11-27T00:00:00Z
        assertTrue(LiveConfigRegistry.isExpired(remoteConfig(1, "2025-11-01T00:00:00Z"), now))
        assertFalse(LiveConfigRegistry.isExpired(remoteConfig(1, "2026-01-01T00:00:00Z"), now))
        // Fail closed: the expiry instant itself is already stale, one millisecond earlier is fine.
        assertTrue(
            "the expiry instant is already expired",
            LiveConfigRegistry.isExpired(remoteConfig(1, "2025-11-27T00:00:00Z"), now)
        )
        assertFalse(
            LiveConfigRegistry.isExpired(remoteConfig(1, "2025-11-27T00:00:00Z"), now - 1)
        )
        assertFalse("blank expiresAtIso means no expiry", LiveConfigRegistry.isExpired(remoteConfig(1), now))
        assertFalse(
            "unparsable expiry must not silently kill the config",
            LiveConfigRegistry.isExpired(remoteConfig(1, "next tuesday"), now)
        )
    }

    @Test
    fun `parseInstantMillis understands the documented ISO-8601 shapes`() {
        val expected = 1_764_223_200_000L // 2025-11-27T06:00:00Z
        assertEquals(expected, LiveConfigRegistry.parseInstantMillis("2025-11-27T06:00:00Z"))
        assertEquals(expected, LiveConfigRegistry.parseInstantMillis("2025-11-27T06:00:00.000Z"))
        assertEquals(expected, LiveConfigRegistry.parseInstantMillis("2025-11-27T06:00:00+00:00"))
        assertEquals(0L, LiveConfigRegistry.parseInstantMillis("1970-01-01T00:00:00Z"))
        assertEquals(null, LiveConfigRegistry.parseInstantMillis(""))
        assertEquals(null, LiveConfigRegistry.parseInstantMillis("not-a-date"))
    }

    @Test
    fun `expired config is neither persisted nor applied`() = runBlocking {
        val applied = repo.saveLiveConfig(
            remoteConfig(4, expiresAtIso = "2020-01-01T00:00:00Z"),
            syncedAtMillis = 1_000L
        )
        assertFalse("an expired config must be rejected", applied)
        assertTrue(
            "the active config must stay the built-in default, was v${LiveConfigRegistry.currentConfig().configVersion}",
            LiveConfigRegistry.currentConfig().isBuiltInDefault
        )
        assertEquals(0L, repo.getPreferences().liveConfigLastSyncMillis)
    }

    @Test
    fun `hydrating an expired stored config drops it instead of keeping stale endpoints`() = runBlocking {
        assertTrue(
            repo.saveLiveConfig(
                remoteConfig(5, expiresAtIso = "2030-01-01T00:00:00Z"),
                syncedAtMillis = 2_000L
            )
        )
        assertEquals(5, repo.hydrateLiveConfig().configVersion)

        // Same stored document, evaluated after its expiry moment.
        val hydrated = repo.hydrateLiveConfig(nowMillis = 1_893_456_000_000L) // 2030-01-01T00:00:00Z
        assertTrue(hydrated.isBuiltInDefault)
        assertTrue(LiveConfigRegistry.currentConfig().isBuiltInDefault)
        assertEquals(0L, repo.getPreferences().liveConfigLastSyncMillis)
    }

    @Test
    fun `fetchLiveConfig skips an expired candidate`() = runTest {
        val body = """
            {
              "configVersion": 6,
              "minAppVersionCode": 1,
              "summaryEn": "Expired",
              "expiresAtIso": "2020-01-01T00:00:00Z",
              "endpoints": { "tgjuAjaxUrl": "https://mirror.example.com/tgju" }
            }
        """.trimIndent()
        val service = RemoteConfigService(bodyServiceClient(body), trustedConfigKeys = emptyMap())
        val result = service.fetchLiveConfig(
            repoSlug = "thepoo7an/chand-iran-currency-gold-crypto",
            appVersionCode = 100
        )
        assertTrue("expected Error but was $result", result is RemoteConfigSyncResult.Error)
        assertEquals("config_expired", (result as RemoteConfigSyncResult.Error).reason)
    }

    // --- self healing ---------------------------------------------------------------------

    @Test
    fun `three consecutive failures roll the remote config back and keep it reverted`() = runBlocking {
        assertTrue(repo.saveLiveConfig(remoteConfig(2), syncedAtMillis = 3_000L))
        assertEquals(2, LiveConfigRegistry.currentConfig().configVersion)

        assertFalse("first failure must not roll back", repo.recordMarketRefreshOutcome(false))
        assertFalse("second failure must not roll back", repo.recordMarketRefreshOutcome(false))
        assertEquals(2, LiveConfigRegistry.currentConfig().configVersion)

        assertTrue("third failure must roll back", repo.recordMarketRefreshOutcome(false))
        assertTrue(LiveConfigRegistry.currentConfig().isBuiltInDefault)
        assertEquals(2, repo.currentRevertedVersion())
        assertEquals(0L, repo.getPreferences().liveConfigLastSyncMillis)

        // A mirror that still serves the broken version cannot undo the recovery…
        assertFalse(repo.saveLiveConfig(remoteConfig(2), syncedAtMillis = 4_000L))
        assertTrue(LiveConfigRegistry.currentConfig().isBuiltInDefault)
        assertFalse(repo.saveLiveConfig(remoteConfig(1), syncedAtMillis = 4_000L))
        assertTrue(LiveConfigRegistry.currentConfig().isBuiltInDefault)

        // …but a new publication is trusted again.
        assertTrue(repo.saveLiveConfig(remoteConfig(3), syncedAtMillis = 5_000L))
        assertEquals(3, LiveConfigRegistry.currentConfig().configVersion)
    }

    @Test
    fun `a successful refresh resets the failure streak`() = runBlocking {
        assertTrue(repo.saveLiveConfig(remoteConfig(2), syncedAtMillis = 6_000L))

        assertFalse(repo.recordMarketRefreshOutcome(false))
        assertFalse(repo.recordMarketRefreshOutcome(false))
        assertFalse(repo.recordMarketRefreshOutcome(true))
        assertFalse(repo.recordMarketRefreshOutcome(false))
        assertFalse(repo.recordMarketRefreshOutcome(false))

        assertEquals(
            "two failures after a success must not reach the threshold",
            2,
            LiveConfigRegistry.currentConfig().configVersion
        )
        assertTrue("still the remote config", !LiveConfigRegistry.currentConfig().isBuiltInDefault)
    }

    @Test
    fun `failures never roll back when only the built-in defaults are active`() = runBlocking {
        repeat(5) {
            assertFalse(repo.recordMarketRefreshOutcome(success = false))
        }
        assertEquals(0, repo.currentRevertedVersion())
        assertTrue(
            "nothing to roll back, so the built-in default must still be active (was v${LiveConfigRegistry.currentConfig().configVersion})",
            LiveConfigRegistry.currentConfig().isBuiltInDefault
        )
    }

    @Test
    fun `manual reset clears the revert marker so the same version can be applied again`() = runBlocking {
        assertTrue(repo.saveLiveConfig(remoteConfig(2), syncedAtMillis = 7_000L))
        repeat(3) { repo.recordMarketRefreshOutcome(success = false) }
        assertEquals(2, repo.currentRevertedVersion())

        repo.resetLiveConfig()
        assertEquals(0, repo.currentRevertedVersion())
        assertTrue(repo.saveLiveConfig(remoteConfig(2), syncedAtMillis = 8_000L))
        assertEquals(2, LiveConfigRegistry.currentConfig().configVersion)
    }

    private fun bodyServiceClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()
}
