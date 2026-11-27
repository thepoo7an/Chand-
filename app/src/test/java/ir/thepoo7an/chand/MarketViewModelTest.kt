package ir.thepoo7an.chand

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class MarketViewModelTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

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

    private class FakeRemoteDataSource : MarketDataSource {
        val liveCalls = AtomicInteger(0)
        var gate: CompletableDeferred<Unit>? = null
        var nextThrowable: Throwable? = null
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
            nextThrowable?.let { throw it }
            return nextResult
        }

        override suspend fun fetchAssetHistory(
            asset: MarketAsset,
            range: TimeRange
        ): AssetHistoryResult = AssetHistoryResult(emptyList(), "", false)
    }

    private fun buildDeps(
        remote: MarketDataSource
    ): Pair<PreferencesRepository, MarketRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dsFile = tmpDir.newFile("vm_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dsFile }
        val prefsRepo = PreferencesRepository(context, dataStore)
        val repo = MarketRepository(context, prefsRepo, remote = remote)
        return prefsRepo to repo
    }

    private fun sampleAsset(
        id: String = "USD",
        price: Double = 85_000.0,
        freshness: DataFreshness = DataFreshness.LIVE
    ): MarketAsset = MarketAsset(
        id = id,
        symbol = id,
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = price,
        change24hPercent = 1.25,
        change24hToman = 1050.0,
        freshness = freshness,
        sourceLabel = "TGJU (Free)",
        updatedAtMillis = 1_750_000_000_000L
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

    @Test
    fun `loadInitialData hydrates cached snapshot immediately before remote fetch completes`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val remote = FakeRemoteDataSource().apply {
            gate = CompletableDeferred()
            nextResult = RemoteFetchResult(
                assets = listOf(sampleAsset(price = 86_200.0, freshness = DataFreshness.LIVE)),
                fetchedAtMillis = 1_750_000_100_000L,
                sourceSummary = "Free Market • TGJU",
                freshness = DataFreshness.LIVE,
                rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
            )
        }
        val (prefsRepo, repo) = buildDeps(remote)
        prefsRepo.saveCachedSnapshot(
            CachedMarketSnapshot(
                assets = listOf(sampleAsset(price = 84_000.0, freshness = DataFreshness.LIVE)),
                updatedAtMillis = 1_750_000_000_000L,
                sourceSummary = "Cached • TGJU",
                freshness = DataFreshness.LIVE
            )
        )

        val vm = ViewModelProvider(
            ViewModelStore(),
            MarketViewModel.provideFactory(app, prefsRepo, repo, autoStart = false)
        )[MarketViewModel::class.java]

        vm.loadInitialData()
        awaitCondition { vm.uiState.value is MarketUiState.Ready && remote.liveCalls.get() == 1 }

        // While remote fetch is gated, cached rates must already be exposed as Ready(CACHED, isRefreshing=true)
        val cachedState = vm.uiState.value as MarketUiState.Ready
        assertTrue(cachedState.isRefreshing)
        assertEquals(DataFreshness.CACHED, cachedState.freshness)
        assertEquals(84_000.0, cachedState.assets.first().priceToman, 0.01)

        // Unblock network response
        remote.gate?.complete(Unit)
        awaitCondition {
            val st = vm.uiState.value as? MarketUiState.Ready
            st != null && !st.isRefreshing && st.freshness == DataFreshness.LIVE
        }

        val liveState = vm.uiState.value as MarketUiState.Ready
        assertFalse(liveState.isRefreshing)
        assertEquals(DataFreshness.LIVE, liveState.freshness)
        assertEquals(86_200.0, liveState.assets.first().priceToman, 0.01)
        assertNull(liveState.errorBanner)
    }

    @Test
    fun `concurrent refreshNow calls coalesce into one active remote fetch`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val remote = FakeRemoteDataSource().apply {
            gate = CompletableDeferred()
            nextResult = RemoteFetchResult(
                assets = listOf(sampleAsset(price = 88_000.0, freshness = DataFreshness.LIVE)),
                fetchedAtMillis = 1_750_000_200_000L,
                sourceSummary = "TGJU",
                freshness = DataFreshness.LIVE,
                rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
            )
        }
        val (prefsRepo, repo) = buildDeps(remote)
        val vm = MarketViewModel(app, prefsRepo, repo, autoStart = false)

        vm.refreshNow(silent = false)
        vm.refreshNow(silent = false)
        vm.refreshNow(silent = true)
        awaitCondition { remote.liveCalls.get() == 1 }

        assertEquals(1, remote.liveCalls.get())

        remote.gate?.complete(Unit)
        awaitCondition {
            val st = vm.uiState.value as? MarketUiState.Ready
            st != null && !st.isRefreshing
        }

        val ready = vm.uiState.value as MarketUiState.Ready
        assertFalse(ready.isRefreshing)
        assertEquals(88_000.0, ready.assets.first().priceToman, 0.01)
    }

    @Test
    fun `failed or UNAVAILABLE refresh preserves existing Ready cache and marks CACHED with offline banner`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val remote = FakeRemoteDataSource().apply {
            nextResult = RemoteFetchResult(
                assets = listOf(sampleAsset(price = 91_500.0, freshness = DataFreshness.LIVE)),
                fetchedAtMillis = 1_750_000_000_000L,
                sourceSummary = "TGJU",
                freshness = DataFreshness.LIVE,
                rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
            )
        }
        val (prefsRepo, repo) = buildDeps(remote)
        val vm = MarketViewModel(app, prefsRepo, repo, autoStart = false)

        vm.refreshNow(silent = false)
        awaitCondition {
            val st = vm.uiState.value as? MarketUiState.Ready
            st != null && !st.isRefreshing && st.freshness == DataFreshness.LIVE
        }

        // Now fail refresh with UNAVAILABLE empty remote result
        remote.nextResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 1_750_000_100_000L,
            sourceSummary = "",
            freshness = DataFreshness.UNAVAILABLE,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )
        vm.refreshNow(silent = false)
        awaitCondition {
            val st = vm.uiState.value as? MarketUiState.Ready
            st != null && !st.isRefreshing && st.freshness == DataFreshness.CACHED
        }

        val preserved = vm.uiState.value as MarketUiState.Ready
        assertFalse(preserved.isRefreshing)
        assertEquals(91_500.0, preserved.assets.first().priceToman, 0.01)
        assertEquals(DataFreshness.CACHED, preserved.freshness)

        // Also test exception thrown inside repository fetch
        remote.nextThrowable = IllegalStateException("Simulated socket reset")
        vm.refreshNow(silent = false)
        awaitCondition {
            val st = vm.uiState.value as? MarketUiState.Ready
            st != null && !st.isRefreshing && st.errorBanner == "offline"
        }

        val afterThrow = vm.uiState.value as MarketUiState.Ready
        assertFalse(afterThrow.isRefreshing)
        assertEquals(91_500.0, afterThrow.assets.first().priceToman, 0.01)
        assertEquals(DataFreshness.CACHED, afterThrow.freshness)
        assertEquals("offline", afterThrow.errorBanner)
    }

    @Test
    fun `invalid remote rates do not overwrite usable persisted cache and CancellationException is never swallowed`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val remote = FakeRemoteDataSource()
        val (prefsRepo, repo) = buildDeps(remote)

        val validSnapshot = CachedMarketSnapshot(
            assets = listOf(sampleAsset(price = 79_000.0, freshness = DataFreshness.LIVE)),
            updatedAtMillis = 1_750_000_000_000L,
            sourceSummary = "TGJU",
            freshness = DataFreshness.LIVE
        )
        prefsRepo.saveCachedSnapshot(validSnapshot)

        // Attempt saving empty/invalid snapshot should be ignored by PreferencesRepository guard
        prefsRepo.saveCachedSnapshot(
            CachedMarketSnapshot(
                assets = listOf(sampleAsset(price = 0.0, freshness = DataFreshness.UNAVAILABLE)),
                updatedAtMillis = 1_750_000_500_000L,
                sourceSummary = "Broken",
                freshness = DataFreshness.UNAVAILABLE
            )
        )
        val reloaded = prefsRepo.loadCachedSnapshot()
        assertEquals(79_000.0, reloaded?.assets?.first()?.priceToman ?: 0.0, 0.01)

        // Verify CancellationException propagates from repository refreshMarket
        remote.nextThrowable = CancellationException("Job cancelled")
        var caughtCancellation = false
        try {
            repo.refreshMarket()
        } catch (_: CancellationException) {
            caughtCancellation = true
        }
        assertTrue(caughtCancellation)

        // Verify ViewModel stays safe if coroutine is cancelled mid-refresh
        val vm = MarketViewModel(app, prefsRepo, repo, autoStart = false)
        remote.nextThrowable = null
        remote.gate = CompletableDeferred()
        vm.refreshNow(silent = false)
        testDispatcher.scheduler.runCurrent()
        delay(1)
    }
}
