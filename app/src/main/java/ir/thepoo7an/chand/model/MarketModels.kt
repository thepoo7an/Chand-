package ir.thepoo7an.chand.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssetCategory {
    ALL,
    PINNED,
    FIAT,
    GOLD,
    CRYPTO
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
    val intrinsicValueToman: Double? = null,
    val bubbleToman: Double? = null,
    val bubblePercent: Double? = null
)

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
    val language: AppLanguage = AppLanguage.EN,
    val usePersianDigits: Boolean = false,
    val isGridView: Boolean = true,
    val pinnedIds: Set<String> = DEFAULT_PINNED_IDS,
    val refreshIntervalSeconds: Int = 30,
    val widgetTranslucent: Boolean = true,
    val widgetOpacityPercent: Int = 70,
    val widgetHeroAssetId: String = "USD",
    val githubRepoSlug: String = ir.thepoo7an.chand.BuildConfig.GITHUB_REPOSITORY
) {
    companion object {
        val DEFAULT_PINNED_IDS = setOf("USD", "EUR", "AED", "GBP", "GOLD_18K", "COIN_QUARTER", "COIN_HALF", "USDT", "BTC", "ETH")
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
