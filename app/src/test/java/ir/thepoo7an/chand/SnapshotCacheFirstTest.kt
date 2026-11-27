package ir.thepoo7an.chand

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.data.SnapshotPolicy
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.MarketUiState
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.MarketDataSource
import ir.thepoo7an.chand.network.RemoteFetchResult
import ir.thepoo7an.chand.network.TomanRateBasis
import ir.thepoo7an.chand.ui.viewmodel.MarketViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * Cache-first / stale-while-revalidate contract:
 * the snapshot is always rendered first, the network is only asked when the snapshot is stale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class SnapshotCacheFirstTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private companion object {
        /** 2025-11-27T12:00:00Z; every snapshot timestamp below is relative to it. */
        const val NOW = 1_764_244_800_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LiveConfigRegistry.resetToDefault()
    }

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
        Dispatchers.resetMain()
    }

    // --- policy ------------------------------------------------------------------------------

    @Test
    fun `a snapshot timestamp that is missing, zero or future-dated is never fresh`() {
        assertEquals(Long.MAX_VALUE, SnapshotPolicy.ageMillis(0L, NOW))
        assertEquals(Long.MAX_VALUE, SnapshotPolicy.ageMillis(-1L, NOW))
        assertEquals(Long.MAX_VALUE, SnapshotPolicy.ageMillis(NOW + 60_000L, NOW))

        assertFalse(SnapshotPolicy.isFresh(0L, NOW))
        assertFalse(
            "a backwards clock jump must not pin a snapshot as fresh",
            SnapshotPolicy.isFresh(NOW + 60_000L, NOW)
        )
        assertFalse(SnapshotPolicy.isAgeFresh(Long.MAX_VALUE))
        assertTrue(SnapshotPolicy.shouldRevalidate(null, NOW))
        assertTrue(SnapshotPolicy.shouldRevalidate(0L, NOW))
        assertTrue(SnapshotPolicy.shouldRevalidate(NOW + 60_000L, NOW))
    }

    @Test
    fun `the soft TTL boundary belongs to the stale side`() {
        assertEquals(SnapshotPolicy.SOFT_TTL_MS, SnapshotPolicy.ageMillis(NOW - SnapshotPolicy.SOFT_TTL_MS, NOW))
        assertTrue(SnapshotPolicy.isAgeFresh(SnapshotPolicy.SOFT_TTL_MS - 1L))
        assertFalse(SnapshotPolicy.isAgeFresh(SnapshotPolicy.SOFT_TTL_MS))
        assertFalse(SnapshotPolicy.isAgeFresh(SnapshotPolicy.SOFT_TTL_MS + 1L))
        assertTrue(SnapshotPolicy.isAgeFresh(0L))
        assertFalse(SnapshotPolicy.isAgeFresh(-1L))

        assertTrue(SnapshotPolicy.isFresh(NOW - SnapshotPolicy.SOFT_TTL_MS + 1L, NOW))
        assertFalse(SnapshotPolicy.isFresh(NOW - SnapshotPolicy.SOFT_TTL_MS, NOW))
        assertTrue(SnapshotPolicy.isFresh(NOW, NOW))

        assertFalse(SnapshotPolicy.shouldRevalidate(NOW - 1_000L, NOW))
        assertTrue(SnapshotPolicy.shouldRevalidate(NOW - 10_000L, NOW, softTtlMillis = 5_000L))
    }

    // --- view model --------------------------------------------------------------------------

    private class FakeRemoteDataSource : MarketDataSource {
        val liveCalls = AtomicInteger(0)
        var gate: CompletableDeferred<Unit>? = null
        var nextResult: RemoteFetchResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 0L,
            sourceSummary = "",
            freshness = DataFreshness.UNAVAILABLE,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )

        override suspend fun fetchLiveMarket(
            lastKnownUsdToman: Double?,
            cachedAssetsById: Map<String, MarketAsset>
        ): RemoteFetchResult {
            liveCalls.incrementAndGet()
            gate?.await()
            return nextResult
        }

        override suspend fun fetchAssetHistory(
            asset: MarketAsset,
            range: TimeRange
        ): AssetHistoryResult = AssetHistoryResult(emptyList(), "", false)
    }

    private fun buildDeps(remote: MarketDataSource): Pair<PreferencesRepository, MarketRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dsFile = tmpDir.newFile("cache_first_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dsFile }
        val prefsRepo = PreferencesRepository(context, dataStore)
        val repo = MarketRepository(context, prefsRepo, remote = remote)
        return prefsRepo to repo
    }

    private fun asset(price: Double = 85_000.0): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = price,
        change24hPercent = 1.25,
        change24hToman = 1_050.0,
        freshness = DataFreshness.LIVE,
        sourceLabel = "TGJU (Free)",
        updatedAtMillis = NOW
    )

    private fun snapshot(price: Double, updatedAtMillis: Long): CachedMarketSnapshot =
        CachedMarketSnapshot(
            assets = listOf(asset(price)),
            updatedAtMillis = updatedAtMillis,
            sourceSummary = "Cached • TGJU",
            freshness = DataFreshness.LIVE
        )

    private fun liveResult(price: Double): RemoteFetchResult = RemoteFetchResult(
        assets = listOf(asset(price)),
        fetchedAtMillis = NOW,
        sourceSummary = "Free Market • TGJU",
        freshness = DataFreshness.LIVE,
        rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
    )

    private fun awaitCondition(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.runCurrent()
            if (predicate()) return
            Thread.sleep(10L)
        }
        testDispatcher.scheduler.runCurrent()
    }

    private fun newViewModel(
        prefsRepo: PreferencesRepository,
        repo: MarketRepository
    ): MarketViewModel = MarketViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        prefsRepo,
        repo,
        autoStart = false,
        clockMillis = { NOW }
    )

    @Test
    fun `a cold start on a fresh snapshot renders it without touching the providers`() = runTest(testDispatcher) {
        val remote = FakeRemoteDataSource().apply { nextResult = liveResult(90_000.0) }
        val (prefsRepo, repo) = buildDeps(remote)
        prefsRepo.saveCachedSnapshot(snapshot(price = 84_000.0, updatedAtMillis = NOW - 5_000L))

        val vm = newViewModel(prefsRepo, repo)
        vm.loadInitialData()
        awaitCondition { vm.uiState.value is MarketUiState.Ready }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("a fresh snapshot must not trigger a provider fetch", 0, remote.liveCalls.get())
        val state = vm.uiState.value as MarketUiState.Ready
        assertEquals(84_000.0, state.assets.first().priceToman, 0.01)
        assertEquals(DataFreshness.CACHED, state.freshness)
        assertFalse("no spinner for data that is already fresh", state.isRefreshing)
    }

    @Test
    fun `a stale snapshot is rendered immediately and revalidated in the background`() = runTest(testDispatcher) {
        val remote = FakeRemoteDataSource().apply {
            gate = CompletableDeferred()
            nextResult = liveResult(90_000.0)
        }
        val (prefsRepo, repo) = buildDeps(remote)
        prefsRepo.saveCachedSnapshot(
            snapshot(price = 84_000.0, updatedAtMillis = NOW - 10L * SnapshotPolicy.SOFT_TTL_MS)
        )

        val vm = newViewModel(prefsRepo, repo)
        vm.loadInitialData()
        awaitCondition {
            val state = vm.uiState.value as? MarketUiState.Ready
            state != null && remote.liveCalls.get() == 1
        }

        val stale = vm.uiState.value as MarketUiState.Ready
        assertEquals(84_000.0, stale.assets.first().priceToman, 0.01)
        assertEquals(DataFreshness.CACHED, stale.freshness)
        assertTrue("stale data is shown while the revalidation runs", stale.isRefreshing)

        remote.gate?.complete(Unit)
        awaitCondition {
            val state = vm.uiState.value as? MarketUiState.Ready
            state != null && !state.isRefreshing && state.freshness == DataFreshness.LIVE
        }

        val revalidated = vm.uiState.value as MarketUiState.Ready
        assertEquals(90_000.0, revalidated.assets.first().priceToman, 0.01)
        assertEquals(1, remote.liveCalls.get())
    }

    @Test
    fun `without any snapshot the first launch still fetches`() = runTest(testDispatcher) {
        val remote = FakeRemoteDataSource().apply { nextResult = liveResult(91_000.0) }
        val (prefsRepo, repo) = buildDeps(remote)

        val vm = newViewModel(prefsRepo, repo)
        vm.loadInitialData()
        awaitCondition {
            val state = vm.uiState.value as? MarketUiState.Ready
            state != null && state.freshness == DataFreshness.LIVE
        }

        assertEquals(1, remote.liveCalls.get())
        val state = vm.uiState.value as MarketUiState.Ready
        assertEquals(91_000.0, state.assets.first().priceToman, 0.01)
        assertFalse(state.isRefreshing)
    }
}
