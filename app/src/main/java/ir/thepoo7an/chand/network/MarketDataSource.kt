package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.TimeRange

data class RemoteFetchResult(
    val assets: List<MarketAsset>,
    val fetchedAtMillis: Long,
    val sourceSummary: String,
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val rateBasis: TomanRateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
) {
    /**
     * Compatibility accessor: true only when fetched in this refresh as LIVE or DELAYED.
     * Cached anchor / cached / unavailable states return false.
     */
    val isLive: Boolean
        get() = freshness == DataFreshness.LIVE || freshness == DataFreshness.DELAYED
}

interface MarketDataSource {
    suspend fun fetchLiveMarket(
        lastKnownUsdToman: Double? = null,
        cachedAssetsById: Map<String, MarketAsset> = emptyMap()
    ): RemoteFetchResult

    suspend fun fetchAssetHistory(asset: MarketAsset, range: TimeRange): AssetHistoryResult
}
