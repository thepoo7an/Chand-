package ir.thepoo7an.chand

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.MarketDataSource
import ir.thepoo7an.chand.network.RemoteFetchResult
import ir.thepoo7an.chand.network.TomanRateBasis
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ChandApplication::class)
class MarketRepositoryTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private class FakeMarketDataSource : MarketDataSource {
        var liveCalls = 0
        var lastAnchorPassed: Double? = null
        var nextLiveResult: RemoteFetchResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 0L,
            sourceSummary = "",
            isLive = false,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )

        var historyCalls = 0
        var nextHistoryResult: AssetHistoryResult = AssetHistoryResult(
            points = emptyList(),
            sourceLabel = "",
            isRealData = false
        )

        override suspend fun fetchLiveMarket(lastKnownUsdToman: Double?): RemoteFetchResult {
            liveCalls++
            lastAnchorPassed = lastKnownUsdToman
            return nextLiveResult
        }

        override suspend fun fetchAssetHistory(asset: MarketAsset, range: TimeRange): AssetHistoryResult {
            historyCalls++
            return nextHistoryResult
        }
    }

    private fun buildRepository(fakeRemote: FakeMarketDataSource): MarketRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStoreFile = tmpDir.newFile("repo_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        val prefsRepo = PreferencesRepository(context, dataStore)
        return MarketRepository(context, prefsRepo, remote = fakeRemote)
    }

    private fun sampleUsdAsset(priceToman: Double = 71_500.0): MarketAsset = MarketAsset(
        id = "USD",
        symbol = "USD",
        nameFa = "دلار آمریکا",
        nameEn = "US Dollar",
        category = AssetCategory.FIAT,
        badgeText = "🇺🇸",
        accentHex = 0xFF10B981,
        priceToman = priceToman,
        priceUsd = 1.0,
        sourceLabel = "TGJU (Free)"
    )

    @Test
    fun `refreshMarket saves live snapshot, passes USD anchor next call, and falls back to cache on outage`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)

        fakeRemote.nextLiveResult = RemoteFetchResult(
            assets = listOf(sampleUsdAsset(71_500.0)),
            fetchedAtMillis = 1_700_000_000_000L,
            sourceSummary = "Free Market (Tehran) • TGJU",
            isLive = true,
            rateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
        )
        val liveSync = repo.refreshMarket()
        assertTrue(liveSync.isLive)
        assertEquals(71_500.0, liveSync.assets.first().priceToman, 0.01)
        assertEquals(null, fakeRemote.lastAnchorPassed)

        fakeRemote.nextLiveResult = RemoteFetchResult(
            assets = emptyList(),
            fetchedAtMillis = 1_700_000_060_000L,
            sourceSummary = "",
            isLive = false,
            rateBasis = TomanRateBasis.OFFLINE_REFERENCE
        )
        val offlineFallback = repo.refreshMarket()
        assertEquals(71_500.0, fakeRemote.lastAnchorPassed ?: 0.0, 0.01)
        assertFalse(offlineFallback.isLive)
        assertEquals(71_500.0, offlineFallback.assets.first().priceToman, 0.01)
        assertEquals("Free Market (Tehran) • TGJU", offlineFallback.sourceSummary)
    }

    @Test
    fun `refreshMarket on cold start outage falls back to emergency offline reference catalog`() = runTest {
        val fakeRemote = FakeMarketDataSource()
        val repo = buildRepository(fakeRemote)

        val sync = repo.refreshMarket()
        assertFalse(sync.isLive)
        assertEquals(TomanRateBasis.OFFLINE_REFERENCE.labelEn, sync.sourceSummary)
        assertTrue(sync.assets.any { it.id == "USD" })
        assertTrue(sync.assets.any { it.id == "GOLD_18K" })
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
}
