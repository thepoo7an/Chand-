package ir.thepoo7an.chand.model

import kotlinx.serialization.Serializable

/**
 * Remote endpoint overrides applied dynamically via online JSON config without APK re-install.
 * Blank strings / empty lists keep the built-in app defaults.
 */
@Serializable
data class RemoteEndpointsConfig(
    val tgjuAjaxUrl: String = "",
    val tgjuTableUrlTemplate: String = "",
    val wallexMarketsUrl: String = "",
    val wallexUdfUrlTemplate: String = "",
    val bitpinMarketsUrl: String = "",
    val nobitexOrderbookUrlTemplate: String = "",
    val nobitexUdfUrlTemplate: String = "",
    val binanceTickerUrl: String = "",
    val binanceKlinesUrlTemplate: String = "",
    val coinGeckoHistoryUrlTemplate: String = "",
    val frankfurterLatestUrls: List<String> = emptyList(),
    val frankfurterSeriesUrlTemplates: List<String> = emptyList(),
    val openErLatestUrl: String = "",
    val goldSpotXauUrl: String = "",
    val currencyCdnUrlTemplates: List<String> = emptyList()
)

/**
 * Dynamic validation & freshness threshold overrides tuned remotely.
 */
@Serializable
data class RemoteValidationConfig(
    val cryptoDisagreementThresholdPct: Double? = null,
    val fiatDisagreementThresholdPct: Double? = null,
    val cryptoLiveMaxAgeMinutes: Int? = null,
    val fiatGoldLiveMaxAgeMinutes: Int? = null,
    val spikeThreshold24hPct: Double? = null,
    val spikeThresholdCryptoMultiDayPct: Double? = null,
    val spikeThresholdFiatMultiDayPct: Double? = null,
    val spikeThresholdGoldMultiDayPct: Double? = null
)

/**
 * Remote feature toggles controllable online without downloading a new APK.
 */
@Serializable
data class RemoteFeaturesConfig(
    val enableConverterEquivalents: Boolean = true,
    val enableCoinBubbleEstimate: Boolean = true,
    val enableHistoricalCharts: Boolean = true,
    val enableInAppApkUpdates: Boolean = true,
    val minAutoRefreshSeconds: Int = 10
)

/**
 * Overrides metadata or disables a built-in market asset dynamically.
 */
@Serializable
data class RemoteAssetOverride(
    val id: String,
    val enabled: Boolean = true,
    val nameFa: String? = null,
    val nameEn: String? = null,
    val symbol: String? = null,
    val badgeText: String? = null,
    val accentHex: Long? = null,
    val tgjuKey: String? = null,
    val fxCode: String? = null,
    val coinGeckoId: String? = null
)

/**
 * Defines a brand-new asset (fiat, gold, or cryptocurrency) injected online into the app catalog.
 */
@Serializable
data class RemoteExtraAsset(
    val id: String,
    val symbol: String,
    val nameFa: String,
    val nameEn: String,
    val category: AssetCategory = AssetCategory.CRYPTO,
    val badgeText: String = "🪙",
    val accentHex: Long = 0xFF6366F1,
    val tgjuKey: String? = null,
    val fxCode: String? = null,
    val coinGeckoId: String? = null
)

/**
 * Live bilingual announcement / operational notice displayed inside MarketFeedScreen.
 */
@Serializable
data class RemoteLiveNotice(
    val id: String = "",
    val titleFa: String = "",
    val titleEn: String = "",
    val bodyFa: String = "",
    val bodyEn: String = "",
    val severity: String = "INFO",
    val dismissible: Boolean = true,
    val actionLabelFa: String = "",
    val actionLabelEn: String = "",
    val actionUrl: String = ""
)

/**
 * Human-readable changelog entry describing an online live patch applied to the current build.
 */
@Serializable
data class RemoteLiveChange(
    val titleFa: String = "",
    val titleEn: String = "",
    val category: String = "CONFIG"
)

/**
 * Google Play compliant JSON live configuration manifest.
 * Supports dynamic endpoint mirrors, asset additions/toggles, validation threshold tuning,
 * feature flags, and live announcements without any dynamic executable code loading (DCL).
 */
@Serializable
data class RemoteAppConfig(
    val configVersion: Int = 0,
    val minAppVersionCode: Int = 1,
    val maxAppVersionCode: Int? = null,
    val publishedAtIso: String = "",
    val summaryFa: String = "",
    val summaryEn: String = "",
    val endpoints: RemoteEndpointsConfig = RemoteEndpointsConfig(),
    val validation: RemoteValidationConfig = RemoteValidationConfig(),
    val features: RemoteFeaturesConfig = RemoteFeaturesConfig(),
    val disabledAssetIds: List<String> = emptyList(),
    val assetOverrides: List<RemoteAssetOverride> = emptyList(),
    val extraAssets: List<RemoteExtraAsset> = emptyList(),
    val liveNotice: RemoteLiveNotice? = null,
    val appliedChanges: List<RemoteLiveChange> = emptyList()
) {
    val isBuiltInDefault: Boolean
        get() = configVersion <= 0 &&
            disabledAssetIds.isEmpty() &&
            assetOverrides.isEmpty() &&
            extraAssets.isEmpty() &&
            liveNotice == null &&
            appliedChanges.isEmpty() &&
            endpoints == RemoteEndpointsConfig() &&
            validation == RemoteValidationConfig() &&
            features == RemoteFeaturesConfig()

    fun activeOverrideCount(): Int {
        var count = 0
        if (endpoints != RemoteEndpointsConfig()) count++
        if (validation != RemoteValidationConfig()) count++
        if (features != RemoteFeaturesConfig()) count++
        count += disabledAssetIds.size
        count += assetOverrides.size
        count += extraAssets.size
        return count
    }
}
