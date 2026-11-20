package com.example.model

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
    val high24hToman: Double = priceToman,
    val low24hToman: Double = priceToman,
    val sparkline: List<Double> = emptyList(),
    val sourceLabel: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class AssetHistoryPoint(
    val timestampSec: Long,
    val priceToman: Double
)

@Serializable
data class CachedMarketSnapshot(
    val assets: List<MarketAsset>,
    val updatedAtMillis: Long,
    val sourceSummary: String
)

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val language: AppLanguage = AppLanguage.FA,
    val usePersianDigits: Boolean = true,
    val pinnedIds: Set<String> = DEFAULT_PINNED_IDS,
    val refreshIntervalSeconds: Int = 30
) {
    companion object {
        val DEFAULT_PINNED_IDS = setOf("USD", "EUR", "GOLD_18K", "COIN_EMAMI", "USDT", "BTC")
    }
}

sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Ready(
        val assets: List<MarketAsset>,
        val lastUpdatedMillis: Long,
        val isRefreshing: Boolean = false,
        val sourceSummary: String = "",
        val errorBanner: String? = null
    ) : MarketUiState

    data class Error(val message: String) : MarketUiState
}
