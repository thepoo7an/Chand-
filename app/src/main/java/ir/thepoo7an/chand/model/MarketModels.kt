package ir.thepoo7an.chand.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssetCategory {
    ALL,
    PINNED,
    FIAT,
    GOLD,
    CRYPTO,
    OIL
}

@Serializable
enum class PriceDisplayUnit {
    TOMAN,
    USD_PER_BARREL
}

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED
}

@Serializable
enum class AppLanguage {
    FA,
    EN
}

enum class TimeRange(
    val labelFa: String,
    val labelEn: String,
    val nobitexResolution: String,
    val lookbackSeconds: Long
) {
    H24("۲۴ ساعت", "24H", "60", 24L * 3600L),
    D7("۷ روز", "7D", "240", 7L * 24L * 3600L),
    M1("۱ ماه", "1M", "D", 30L * 24L * 3600L),
    Y1("۱ سال", "1Y", "D", 365L * 24L * 3600L)
}

@Serializable
enum class DataFreshness {
    LIVE,
    DELAYED,
    CACHED,
    UNAVAILABLE;

    companion object {
        /**
         * Returns the stalest freshness across inputs ("a derived price is only as fresh as its stalest input").
         */
        fun stalest(vararg inputs: DataFreshness): DataFreshness {
            if (inputs.isEmpty()) return UNAVAILABLE
            return inputs.maxBy { it.ordinal }
        }
    }
}

@Serializable
enum class CandidateStatus {
    INCLUDED,
    REJECTED
}

@Serializable
enum class CandidateExclusionReason {
    STALE,
    INVALID,
    EXCESSIVE_DISAGREEMENT,
    MISSING_DATA,
    PROVIDER_FAILURE
}

@Serializable
data class SourceCandidateRecord(
    val sourceName: String,
    val priceToman: Double? = null,
    val rawPrice: Double? = null,
    val sourceTimestampMillis: Long = 0L,
    val fetchedAtMillis: Long = 0L,
    val isPrimary: Boolean = false,
    val status: CandidateStatus = CandidateStatus.INCLUDED,
    val exclusionReason: CandidateExclusionReason? = null,
    val detail: String = ""
)

@Serializable
data class MarketAsset(
    val id: String,
    val symbol: String,
    val nameFa: String,
    val nameEn: String,
    val category: AssetCategory,
    val badgeText: String,
    val accentHex: Long,
    val priceToman: Double,
    val priceUsd: Double? = null,
    val change24hPercent: Double = 0.0,
    val change24hToman: Double = 0.0,
    val high24hToman: Double? = null,
    val low24hToman: Double? = null,
    val sparkline: List<Double> = emptyList(),
    val sourceLabel: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val freshness: DataFreshness = DataFreshness.CACHED,
    val sourceTimestampMillis: Long = 0L,
    val fetchedAtMillis: Long = 0L,
    val disputed: Boolean = false,
    val singleSource: Boolean = false,
    val isCalculatedPrice: Boolean = false,
    val candidates: List<SourceCandidateRecord> = emptyList(),
    val intrinsicValueToman: Double? = null,
    val bubbleToman: Double? = null,
    val bubblePercent: Double? = null,
    val primaryUnit: PriceDisplayUnit = PriceDisplayUnit.TOMAN,
    val isChange24hAvailable: Boolean = true,
    val change24hUsd: Double? = null,
    val high24hUsd: Double? = null,
    val low24hUsd: Double? = null
) {
    val isUsdPerBarrel: Boolean
        get() = primaryUnit == PriceDisplayUnit.USD_PER_BARREL

    val primaryPriceValue: Double
        get() = if (isUsdPerBarrel) (priceUsd ?: 0.0) else priceToman

    val hasUsablePrimaryPrice: Boolean
        get() = primaryPriceValue.isFinite() && primaryPriceValue > 0.0
}

@Serializable
data class AssetHistoryPoint(
    val timestampSec: Long,
    val priceToman: Double
)

@Serializable
data class AssetHistoryResult(
    val points: List<AssetHistoryPoint>,
    val sourceLabel: String = "",
    val isRealData: Boolean = true,
    val cachedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class CachedMarketSnapshot(
    val assets: List<MarketAsset>,
    val updatedAtMillis: Long,
    val sourceSummary: String,
    val freshness: DataFreshness = DataFreshness.CACHED
)

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val language: AppLanguage = AppLanguage.FA,
    val usePersianDigits: Boolean = (language == AppLanguage.FA),
    val isGridView: Boolean = true,
    val pinnedIds: Set<String> = DEFAULT_PINNED_IDS,
    val refreshIntervalSeconds: Int = 30,
    val widgetTranslucent: Boolean = true,
    val widgetOpacityPercent: Int = 70,
    val widgetHeroAssetId: String = "USD",
    val githubRepoSlug: String = ir.thepoo7an.chand.BuildConfig.GITHUB_REPOSITORY,
    val liveConfigUrl: String = "",
    val liveConfigLastSyncMillis: Long = 0L,
    val dismissedLiveNoticeId: String = "",
    val dismissedUpdateTag: String = "",
    val showSupportCard: Boolean = false,
    val comparisonAssetIds: Set<String> = DEFAULT_COMPARISON_ASSET_IDS
) {
    companion object {
        val DEFAULT_PINNED_IDS = setOf("USD", "EUR", "AED", "GBP", "GOLD_18K", "COIN_QUARTER", "COIN_HALF", "USDT", "BTC", "ETH")
        val DEFAULT_COMPARISON_ASSET_IDS = setOf("USD", "USDT", "EUR", "GOLD_18K")
        val CONFIGURABLE_COMPARISON_IDS = listOf("USD", "USDT", "EUR", "GOLD_18K")
    }
}

sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Ready(
        val assets: List<MarketAsset>,
        val lastUpdatedMillis: Long,
        val isRefreshing: Boolean = false,
        val sourceSummary: String = "",
        val errorBanner: String? = null,
        val freshness: DataFreshness = DataFreshness.CACHED
    ) : MarketUiState

    data class Error(val message: String) : MarketUiState
}
