package ir.thepoo7an.chand

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.MarketDataSource
import ir.thepoo7an.chand.network.RemoteFetchResult
import ir.thepoo7an.chand.network.TomanRateBasis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class MarketRepositoryTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private class FakeMarketDataSource : MarketDataSource {
        var liveCalls = 0
        val liveCallCount = AtomicInteger(0)
        var liveGate: CompletableDeferred<Unit>? = null
        var lastAnchorPassed: Double? = null
        var lastCachedAssetsPassed: Map<String, MarketAsset> = emptyMap()
        var nextLiveResult: RemoteFetchResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 0L,
            sourceSummary = "",
            freshness = DataFreshness.UNAVAILABLE,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )

        var historyCalls = 0
        val historyCallCount = AtomicInteger(0)
        var historyGate: CompletableDeferred<Unit>? = null
        var nextHistoryResult: AssetHistoryResult = AssetHistoryResult(
            points = emptyList(),
            sourceLabel = "",
            isRealData = false
        )

        override suspend fun fetchLiveMarket(
            lastKnownUsdToman: Double?,
            cachedAssetsById: Map<String, MarketAsset>
        ): RemoteFetchResult {
            liveCalls++
            liveCallCount.incrementAndGet()
            lastAnchorPassed = lastKnownUsdToman
            lastCachedAssetsPassed = cachedAssetsById
            liveGate?.await()
            return nextLiveResult
        }

        override suspend fun fetchAssetHistory(asset: MarketAsset, range: TimeRange): AssetHistoryResult {
            historyCalls++
            historyCallCount.incrementAndGet()
            historyGate?.await()
            return nextHistoryResult
        }
    }

    private fun buildRepository(
        fakeRemote: FakeMarketDataSource,
        clockMillis: () -> Long = { System.currentTimeMillis() }
    ): MarketRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStoreFile = tmpDir.newFile("repo_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        val prefsRepo = PreferencesRepository(context, dataStore)
        return MarketRepository(context, prefsRepo, remote = fakeRemote, clockMillis = clockMillis)
    }

    private fun sampleUsdAsset(
        priceToman: Double = 71_500.0,
        freshness: DataFreshness = DataFreshness.LIVE
    ): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        priceUsd = 1.0,
        sourceLabel = "TGJU (Free)",
        freshness = freshness
    )

    @Test
    fun `refreshMarket saves live snapshot, passes USD anchor next call, and falls back to cache on outage`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)

        fakeRemote.nextLiveResult = RemoteFetchResult(
            assets = listOf(sampleUsdAsset(71_500.0, DataFreshness.LIVE)),
            fetchedAtMillis = 1_700_000_000_000L,
            sourceSummary = "Free Market (Tehran) • TGJU",
            freshness = DataFreshness.LIVE,
            rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
        )
        val liveSync = repo.refreshMarket()
        assertTrue(liveSync.isLive)
        assertEquals(DataFreshness.LIVE, liveSync.freshness)
        assertEquals(71_500.0, liveSync.assets.first().priceToman, 0.01)
        assertEquals(null, fakeRemote.lastAnchorPassed)

        fakeRemote.nextLiveResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 1_700_000_060_000L,
            sourceSummary = "",
            freshness = DataFreshness.UNAVAILABLE,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )
        val offlineFallback = repo.refreshMarket()
        assertEquals(71_500.0, fakeRemote.lastAnchorPassed ?: 0.0, 0.01)
        assertTrue(fakeRemote.lastCachedAssetsPassed.containsKey("USD"))
        assertFalse(offlineFallback.isLive)
        assertEquals(DataFreshness.CACHED, offlineFallback.freshness)
        assertEquals(DataFreshness.CACHED, offlineFallback.assets.first().freshness)
        assertEquals(71_500.0, offlineFallback.assets.first().priceToman, 0.01)
        assertEquals("Free Market (Tehran) • TGJU", offlineFallback.sourceSummary)
    }

    @Test
    fun `refreshMarket never marks cached USD anchor response as LIVE`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)

        fakeRemote.nextLiveResult = RemoteFetchResult(
            assets = listOf(sampleUsdAsset(70_000.0, DataFreshness.CACHED)),
            fetchedAtMillis = 1_700_000_120_000L,
            sourceSummary = "Cached USD Anchor • Binance • FinancialFX",
            freshness = DataFreshness.CACHED,
            rateBasis = TomanRateBasis.CACHED_USD_ANCHOR
        )

        val sync = repo.refreshMarket()
        assertFalse(sync.isLive)
        assertEquals(DataFreshness.CACHED, sync.freshness)
        assertEquals(DataFreshness.CACHED, sync.assets.first().freshness)
    }

    @Test
    fun `refreshMarket on cold start outage falls back to emergency offline reference catalog`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)

        val sync = repo.refreshMarket()
        assertFalse(sync.isLive)
        assertEquals(DataFreshness.UNAVAILABLE, sync.freshness)
        assertEquals(TomanRateBasis.OFFLINE_REFERENCE.labelEn, sync.sourceSummary)
        assertTrue(sync.assets.any { it.id == "USD" })
        assertTrue(sync.assets.any { it.id == "GOLD_18K" })
        assertTrue(sync.assets.all { it.freshness == DataFreshness.UNAVAILABLE })
    }

    @Test
    fun `fetchHistory hits cache within TTL, bypasses on forceRefresh, and retains stale cache on failure`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)
        val usd = sampleUsdAsset(71_500.0)

        fakeRemote.nextHistoryResult = AssetHistoryResult(
            points = listOf(
                AssetHistoryPoint(1_700_000_000L, 70_000.0),
                AssetHistoryPoint(1_700_003_600L, 71_000.0),
                AssetHistoryPoint(1_700_007_200L, 71_500.0)
            ),
            sourceLabel = "TGJU",
            isRealData = true
        )

        val first = repo.fetchHistory(usd, TimeRange.D7, forceRefresh = false)
        assertEquals(1, fakeRemote.historyCalls)
        assertTrue(first.isRealData)
        assertEquals(3, first.points.size)

        val second = repo.fetchHistory(usd, TimeRange.D7, forceRefresh = false)
        assertEquals(1, fakeRemote.historyCalls)
        assertEquals("TGJU", second.sourceLabel)

        fakeRemote.nextHistoryResult = AssetHistoryResult(
            points = listOf(
                AssetHistoryPoint(1_700_000_000L, 70_200.0),
                AssetHistoryPoint(1_700_007_200L, 71_800.0)
            ),
            sourceLabel = "TGJU Fresh",
            isRealData = true
        )
        val forced = repo.fetchHistory(usd, TimeRange.D7, forceRefresh = true)
        assertEquals(2, fakeRemote.historyCalls)
        assertEquals("TGJU Fresh", forced.sourceLabel)

        fakeRemote.nextHistoryResult = AssetHistoryResult(
            points = emptyList(),
            sourceLabel = "",
            isRealData = false
        )
        val degraded = repo.fetchHistory(usd, TimeRange.D7, forceRefresh = true)
        assertEquals(3, fakeRemote.historyCalls)
        assertTrue(degraded.isRealData)
        assertEquals("TGJU Fresh", degraded.sourceLabel)
    }

    @Test
    fun `fetchHistory normalizes unsorted duplicates and reverting spikes before caching`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)
        val usd = sampleUsdAsset(71_000.0)

        fakeRemote.nextHistoryResult = AssetHistoryResult(
            points = listOf(
                AssetHistoryPoint(1_700_003_000L, 71_000.0),
                AssetHistoryPoint(1_700_001_000L, 69_000.0),
                AssetHistoryPoint(1_700_002_000L, 95_000.0), // >15% spike that reverts
                AssetHistoryPoint(1_700_001_000L, 70_600.0)  // duplicate ts=1000 (latest wins)
            ),
            sourceLabel = "Wallex",
            isRealData = true
        )

        val normalized = repo.fetchHistory(usd, TimeRange.H24, forceRefresh = true)
        assertEquals(listOf(1_700_001_000L, 1_700_003_000L), normalized.points.map { it.timestampSec })
        assertEquals(listOf(70_600.0, 71_000.0), normalized.points.map { it.priceToman })

        // Reading back from disk cache preserves normalized series without re-calling remote
        val fromCache = repo.fetchHistory(usd, TimeRange.H24, forceRefresh = false)
        assertEquals(1, fakeRemote.historyCalls)
        assertEquals(normalized.points, fromCache.points)
    }

    @Test
    fun `concurrent refreshMarket and cache-miss fetchHistory coalesce and TTL expiration triggers re-fetch`() = runTest {
        var nowMs = 1_700_000_000_000L
        val fakeRemote = FakeMarketDataSource().apply {
            liveGate = CompletableDeferred()
            nextLiveResult = RemoteFetchResult(
                assets = listOf(sampleUsdAsset(72_000.0, DataFreshness.LIVE)),
                fetchedAtMillis = nowMs,
                sourceSummary = "Free Market (Tehran) • TGJU",
                freshness = DataFreshness.LIVE,
                rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
            )
            historyGate = CompletableDeferred()
            nextHistoryResult = AssetHistoryResult(
                points = listOf(
                    AssetHistoryPoint(1_700_000_000L, 71_000.0),
                    AssetHistoryPoint(1_700_003_600L, 72_000.0)
                ),
                sourceLabel = "Wallex",
                isRealData = true
            )
        }
        val repo = buildRepository(fakeRemote) { nowMs }

        val syncResults = coroutineScope {
            (1..5).map {
                async(Dispatchers.Default) { repo.refreshMarket() }
            }.also {
                delay(40)
                fakeRemote.liveGate?.complete(Unit)
            }.awaitAll()
        }
        assertEquals(1, fakeRemote.liveCallCount.get())
        assertTrue(syncResults.all { it.assets.firstOrNull()?.priceToman == 72_000.0 })

        val usd = sampleUsdAsset(72_000.0)
        val historyResults = coroutineScope {
            (1..5).map {
                async(Dispatchers.Default) { repo.fetchHistory(usd, TimeRange.H24, forceRefresh = false) }
            }.also {
                delay(40)
                fakeRemote.historyGate?.complete(Unit)
            }.awaitAll()
        }
        assertEquals(1, fakeRemote.historyCallCount.get())
        assertTrue(historyResults.all { it.points.size == 2 })

        // Within H24 TTL -> cache hit, no remote call
        nowMs += MarketRepository.HISTORY_H24_CACHE_TTL_MS - 1_000L
        repo.fetchHistory(usd, TimeRange.H24, forceRefresh = false)
        assertEquals(1, fakeRemote.historyCallCount.get())

        // Past H24 TTL -> remote call executes again
        nowMs += 5_000L
        fakeRemote.historyGate = null
        repo.fetchHistory(usd, TimeRange.H24, forceRefresh = false)
        assertEquals(2, fakeRemote.historyCallCount.get())
    }
}
