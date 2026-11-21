package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.TimeRange

data class RemoteFetchResult(
    val assets: List<MarketAsset>,
    val fetchedAtMillis: Long,
    val sourceSummary: String,
    val isLive: Boolean,
    val rateBasis: TomanRateBasis = TomanRateBasis.TEHRAN_FREE_MARKET
)

interface MarketDataSource {
    suspend fun fetchLiveMarket(lastKnownUsdToman: Double? = null): RemoteFetchResult
    suspend fun fetchAssetHistory(asset: MarketAsset, range: TimeRange): AssetHistoryResult
}
