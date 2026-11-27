package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.BuildConfig
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.RemoteAppConfig
import ir.thepoo7an.chand.model.RemoteAssetOverride
import ir.thepoo7an.chand.model.RemoteEndpointsConfig
import ir.thepoo7an.chand.model.RemoteExtraAsset
import ir.thepoo7an.chand.model.RemoteFeaturesConfig
import ir.thepoo7an.chand.model.RemoteLiveChange
import ir.thepoo7an.chand.model.RemoteLiveNotice
import ir.thepoo7an.chand.model.RemoteValidationConfig
import ir.thepoo7an.chand.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe process-wide registry holding the active validated [RemoteAppConfig].
 * Updates take effect immediately across market fetchers, mappers, validation rules, and UI
 * without requiring an APK download or process restart.
 */
object LiveConfigRegistry {
    private val currentRef = AtomicReference(RemoteAppConfig())
    private val _configFlow = MutableStateFlow(RemoteAppConfig())
    val configFlow: StateFlow<RemoteAppConfig> = _configFlow.asStateFlow()

    fun currentConfig(): RemoteAppConfig = currentRef.get()

    fun applyConfig(raw: RemoteAppConfig, appVersionCode: Int = BuildConfig.VERSION_CODE): Boolean {
        if (!isCompatible(raw, appVersionCode)) return false
        val clean = sanitizeConfig(raw)
        currentRef.set(clean)
        _configFlow.value = clean
        return true
    }

    fun resetToDefault() {
        val defaultCfg = RemoteAppConfig()
        currentRef.set(defaultCfg)
        _configFlow.value = defaultCfg
    }

    fun isCompatible(config: RemoteAppConfig, appVersionCode: Int = BuildConfig.VERSION_CODE): Boolean {
        if (appVersionCode < config.minAppVersionCode) return false
        val maxVer = config.maxAppVersionCode
        if (maxVer != null && appVersionCode > maxVer) return false
        return true
    }

    private val ISO_INSTANT_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    /**
     * Parses the subset of ISO-8601 used by `publishedAtIso`/`expiresAtIso`. Returns `null` for
     * blank or unparsable input; callers must treat that as "no expiry" instead of failing.
     */
    fun parseInstantMillis(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        ISO_INSTANT_PATTERNS.forEach { pattern ->
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(trimmed)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    /** True only when the config carries a valid `expiresAtIso` that is already in the past. */
    fun isExpired(config: RemoteAppConfig, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = parseInstantMillis(config.expiresAtIso) ?: return false
        return nowMillis > expiresAt
    }

    private val IPV4_LITERAL_REGEX = Regex("^[0-9.]+$")
    private val PRIVATE_IPV4_REGEX =
        Regex("^(10\\.|127\\.|0\\.|169\\.254\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)")
    private val DNS_LABEL_REGEX = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * Accepts only absolute `https://` URLs with a real DNS host. Deliberately *not* an
     * allowlist — endpoint mirrors are the whole point of the live config — but rejects the
     * shapes that enable credential smuggling, SSRF probes, and trick hosts.
     */
    fun isSafeHttpsUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.any { it.isWhitespace() || it == '\\' || it == '\u0000' }) return false
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false

        val authority = trimmed
            .substringAfter("://", "")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
        if (authority.isBlank() || authority.length > 260) return false
        // `user:pass@host` would let a config hide the real target behind credentials.
        if (authority.contains('@')) return false
        // IPv6 literals are not used by any supported provider; reject to keep parsing simple.
        if (authority.startsWith("[") || authority.count { it == ':' } > 1) return false

        val hostPart = authority.substringBefore(":").trim().lowercase(Locale.US)
        val portPart = authority.substringAfter(":", "")
        if (authority.contains(':')) {
            if (portPart.isEmpty() || portPart.any { !it.isDigit() }) return false
            val port = portPart.toIntOrNull() ?: return false
            if (port !in 1..65535) return false
        }

        if (hostPart.isBlank() || hostPart.length > 253) return false
        if (hostPart.endsWith(".") || ".." in hostPart) return false
        if (hostPart == "localhost" || hostPart.endsWith(".localhost")) return false
        if (hostPart.endsWith(".local") || hostPart.endsWith(".internal")) return false
        // Reject bare/private/link-local IPv4 literals (loopback, RFC1918, metadata endpoints).
        if (IPV4_LITERAL_REGEX.matches(hostPart)) return false
        if (PRIVATE_IPV4_REGEX.containsMatchIn(hostPart)) return false
        if (!hostPart.contains('.')) return false

        return hostPart.split('.').all { DNS_LABEL_REGEX.matches(it) }
    }

    internal fun sanitizeConfig(raw: RemoteAppConfig): RemoteAppConfig {
        fun cleanUrl(url: String): String = url.trim().takeIf(::isSafeHttpsUrl).orEmpty()
        fun cleanUrlList(list: List<String>): List<String> = list.mapNotNull { cleanUrl(it).takeIf { u -> u.isNotEmpty() } }.distinct()

        val ep = raw.endpoints
        val cleanEndpoints = RemoteEndpointsConfig(
            tgjuAjaxUrl = cleanUrl(ep.tgjuAjaxUrl),
            tgjuTableUrlTemplate = cleanUrl(ep.tgjuTableUrlTemplate),
            wallexMarketsUrl = cleanUrl(ep.wallexMarketsUrl),
            wallexUdfUrlTemplate = cleanUrl(ep.wallexUdfUrlTemplate),
            bitpinMarketsUrl = cleanUrl(ep.bitpinMarketsUrl),
            nobitexOrderbookUrlTemplate = cleanUrl(ep.nobitexOrderbookUrlTemplate),
            nobitexUdfUrlTemplate = cleanUrl(ep.nobitexUdfUrlTemplate),
            binanceTickerUrl = cleanUrl(ep.binanceTickerUrl),
            binanceKlinesUrlTemplate = cleanUrl(ep.binanceKlinesUrlTemplate),
            coinGeckoHistoryUrlTemplate = cleanUrl(ep.coinGeckoHistoryUrlTemplate),
            frankfurterLatestUrls = cleanUrlList(ep.frankfurterLatestUrls),
            frankfurterSeriesUrlTemplates = cleanUrlList(ep.frankfurterSeriesUrlTemplates),
            openErLatestUrl = cleanUrl(ep.openErLatestUrl),
            goldSpotXauUrl = cleanUrl(ep.goldSpotXauUrl),
            currencyCdnUrlTemplates = cleanUrlList(ep.currencyCdnUrlTemplates),
            oilYahooChartUrlTemplates = cleanUrlList(ep.oilYahooChartUrlTemplates),
            oilCnbcQuotesUrl = cleanUrl(ep.oilCnbcQuotesUrl),
            oilEiaSpotUrl = cleanUrl(ep.oilEiaSpotUrl)
        )

        val v = raw.validation
        val cleanValidation = RemoteValidationConfig(
            cryptoDisagreementThresholdPct = v.cryptoDisagreementThresholdPct?.takeIf { it.isFinite() }?.coerceIn(0.5, 25.0),
            fiatDisagreementThresholdPct = v.fiatDisagreementThresholdPct?.takeIf { it.isFinite() }?.coerceIn(0.5, 25.0),
            cryptoLiveMaxAgeMinutes = v.cryptoLiveMaxAgeMinutes?.coerceIn(1, 180),
            fiatGoldLiveMaxAgeMinutes = v.fiatGoldLiveMaxAgeMinutes?.coerceIn(1, 360),
            spikeThreshold24hPct = v.spikeThreshold24hPct?.takeIf { it.isFinite() }?.coerceIn(5.0, 100.0),
            spikeThresholdCryptoMultiDayPct = v.spikeThresholdCryptoMultiDayPct?.takeIf { it.isFinite() }?.coerceIn(5.0, 150.0),
            spikeThresholdFiatMultiDayPct = v.spikeThresholdFiatMultiDayPct?.takeIf { it.isFinite() }?.coerceIn(5.0, 100.0),
            spikeThresholdGoldMultiDayPct = v.spikeThresholdGoldMultiDayPct?.takeIf { it.isFinite() }?.coerceIn(5.0, 100.0)
        )

        val f = raw.features
        val cleanFeatures = RemoteFeaturesConfig(
            enableConverterEquivalents = f.enableConverterEquivalents,
            enableCoinBubbleEstimate = f.enableCoinBubbleEstimate,
            enableHistoricalCharts = f.enableHistoricalCharts,
            enableInAppApkUpdates = f.enableInAppApkUpdates,
            minAutoRefreshSeconds = f.minAutoRefreshSeconds.coerceIn(5, 300)
        )

        val cleanDisabled = raw.disabledAssetIds
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotBlank() && it != "USD" }
            .distinct()

        val cleanOverrides = raw.assetOverrides
            .mapNotNull { ov ->
                val cleanId = ov.id.trim().uppercase(Locale.US)
                if (cleanId.isBlank()) null
                else RemoteAssetOverride(
                    id = cleanId,
                    enabled = if (cleanId == "USD") true else ov.enabled,
                    nameFa = ov.nameFa?.trim()?.takeIf { it.isNotBlank() },
                    nameEn = ov.nameEn?.trim()?.takeIf { it.isNotBlank() },
                    symbol = ov.symbol?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
                    badgeText = ov.badgeText?.trim()?.takeIf { it.isNotBlank() },
                    accentHex = ov.accentHex,
                    tgjuKey = ov.tgjuKey?.trim()?.takeIf { it.isNotBlank() },
                    fxCode = ov.fxCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
                    coinGeckoId = ov.coinGeckoId?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
                )
            }
            .distinctBy { it.id }

        val cleanExtras = raw.extraAssets
            .mapNotNull { extra ->
                val cleanId = extra.id.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9_]"), "")
                val cleanSym = extra.symbol.trim().uppercase(Locale.US)
                val cleanFa = extra.nameFa.trim()
                val cleanEn = extra.nameEn.trim()
                if (cleanId.length !in 2..24 || cleanSym.isBlank() || (cleanFa.isBlank() && cleanEn.isBlank())) {
                    null
                } else {
                    val safeCategory = when (extra.category) {
                        AssetCategory.FIAT, AssetCategory.GOLD, AssetCategory.CRYPTO, AssetCategory.OIL -> extra.category
                        AssetCategory.ALL, AssetCategory.PINNED -> AssetCategory.CRYPTO
                    }
                    RemoteExtraAsset(
                        id = cleanId,
                        symbol = cleanSym,
                        nameFa = cleanFa.ifBlank { cleanEn },
                        nameEn = cleanEn.ifBlank { cleanFa },
                        category = safeCategory,
                        badgeText = extra.badgeText.trim().ifBlank { "🪙" },
                        accentHex = extra.accentHex,
                        tgjuKey = extra.tgjuKey?.trim()?.takeIf { it.isNotBlank() },
                        fxCode = extra.fxCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
                        coinGeckoId = extra.coinGeckoId?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
                    )
                }
            }
            .distinctBy { it.id }

        val cleanNotice = raw.liveNotice?.let { notice ->
            val cleanId = notice.id.trim()
            val hasBody = notice.bodyFa.isNotBlank() || notice.bodyEn.isNotBlank() ||
                notice.titleFa.isNotBlank() || notice.titleEn.isNotBlank()
            if (cleanId.isBlank() || !hasBody) {
                null
            } else {
                val sev = notice.severity.trim().uppercase(Locale.US)
                val validSev = if (sev in setOf("INFO", "WARNING", "SUCCESS")) sev else "INFO"
                RemoteLiveNotice(
                    id = cleanId,
                    titleFa = notice.titleFa.trim(),
                    titleEn = notice.titleEn.trim(),
                    bodyFa = notice.bodyFa.trim(),
                    bodyEn = notice.bodyEn.trim(),
                    severity = validSev,
                    dismissible = notice.dismissible,
                    actionLabelFa = notice.actionLabelFa.trim(),
                    actionLabelEn = notice.actionLabelEn.trim(),
                    actionUrl = cleanUrl(notice.actionUrl)
                )
            }
        }

        val cleanExpiry = raw.expiresAtIso.trim()

        val cleanChanges = raw.appliedChanges.mapNotNull { ch ->
            if (ch.titleFa.isBlank() && ch.titleEn.isBlank()) null
            else RemoteLiveChange(
                titleFa = ch.titleFa.trim().ifBlank { ch.titleEn.trim() },
                titleEn = ch.titleEn.trim().ifBlank { ch.titleFa.trim() },
                category = ch.category.trim().uppercase(Locale.US).ifBlank { "CONFIG" }
            )
        }

        return RemoteAppConfig(
            configVersion = raw.configVersion.coerceAtLeast(0),
            minAppVersionCode = raw.minAppVersionCode.coerceAtLeast(1),
            maxAppVersionCode = raw.maxAppVersionCode,
            publishedAtIso = raw.publishedAtIso.trim(),
            summaryFa = raw.summaryFa.trim(),
            summaryEn = raw.summaryEn.trim(),
            endpoints = cleanEndpoints,
            validation = cleanValidation,
            features = cleanFeatures,
            disabledAssetIds = cleanDisabled,
            assetOverrides = cleanOverrides,
            extraAssets = cleanExtras,
            liveNotice = cleanNotice,
            appliedChanges = cleanChanges,
            expiresAtIso = cleanExpiry
        )
    }
}

sealed interface RemoteConfigSyncResult {
    data class Applied(
        val config: RemoteAppConfig,
        val sourceUrl: String,
        val syncedAtMillis: Long,
        val versionChanged: Boolean
    ) : RemoteConfigSyncResult

    data class IncompatibleVersion(
        val remoteConfigVersion: Int,
        val minAppVersionCode: Int,
        val maxAppVersionCode: Int?
    ) : RemoteConfigSyncResult

    data object NotConfiguredOrMissing : RemoteConfigSyncResult
    data class Error(val reason: String) : RemoteConfigSyncResult
}

sealed interface RemoteConfigUiState {
    data object Idle : RemoteConfigUiState
    data object Syncing : RemoteConfigUiState
    data class Synced(val configVersion: Int, val syncedAtMillis: Long, val versionChanged: Boolean) : RemoteConfigUiState
    data class NoRemoteConfigYet(val checkedAtMillis: Long) : RemoteConfigUiState
    data class Incompatible(val minVersionCode: Int) : RemoteConfigUiState
    data class Error(val message: String) : RemoteConfigUiState
}

/**
 * Fetches Play-compliant JSON live configuration (`chand-live-config.json`) from custom HTTPS URL,
 * jsDelivr CDN mirror, or GitHub Raw, validates compatibility & bounds, and applies changes online
 * without needing APK re-installation.
 */
class RemoteConfigService(
    private val client: OkHttpClient = NetworkConfig.sharedClient,
    /**
     * Trusted signing keys (`keyId` -> base64 SubjectPublicKeyInfo). Defaults to the keys baked
     * into this build; tests inject their own store instead.
     */
    private val trustedConfigKeys: Map<String, String> =
        LiveConfigSignature.parseTrustedKeys(BuildConfig.LIVE_CONFIG_PUBLIC_KEYS)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val syncDeduplicator = InFlightRequestDeduplicator<String, RemoteConfigSyncResult>()

    suspend fun fetchLiveConfig(
        repoSlug: String,
        customConfigUrl: String = "",
        currentActiveVersion: Int = LiveConfigRegistry.currentConfig().configVersion,
        appVersionCode: Int = BuildConfig.VERSION_CODE
    ): RemoteConfigSyncResult = withContext(Dispatchers.IO) {
        val urls = buildCandidateUrls(repoSlug, customConfigUrl)
        if (urls.isEmpty()) return@withContext RemoteConfigSyncResult.NotConfiguredOrMissing

        val dedupeKey = "${urls.joinToString("|")}|$appVersionCode"
        syncDeduplicator.execute(dedupeKey) {
            var sawHttp404Only = true
            var lastError = ""
            var parsedIncompatible: RemoteConfigSyncResult.IncompatibleVersion? = null
            // Empty trust store means the build opted out of signature enforcement.
            val trustedKeys = trustedConfigKeys

            for (url in urls) {
                val outcome = NetworkConfig.executeGetWithRetry(
                    client = client,
                    url = url,
                    profile = NetworkConfig.RequestProfile.UPDATE_CHECK
                )
                when (outcome) {
                    is NetworkOutcome.Success -> {
                        sawHttp404Only = false
                        // Verify before parsing: a tampered or unknown-key config must never be
                        // applied, and a signature error is not "no config published".
                        when (val signatureCheck = LiveConfigSignature.verify(outcome.data, trustedKeys)) {
                            is LiveConfigSignatureCheck.Invalid -> {
                                AppLog.w(
                                    "Rejected live config from $url (${signatureCheck.reason})"
                                )
                                lastError = "config_signature_${signatureCheck.reason}"
                                continue
                            }
                            LiveConfigSignatureCheck.Unsigned -> {
                                if (trustedKeys.isNotEmpty()) {
                                    AppLog.w("Rejected unsigned live config from $url")
                                    lastError = "config_unsigned"
                                    continue
                                }
                                AppLog.d("Live config from $url is unsigned and no keys are configured")
                            }
                            is LiveConfigSignatureCheck.Valid -> Unit
                        }
                        val parsed = parseConfigJson(outcome.data)
                        if (parsed != null) {
                            if (LiveConfigRegistry.isExpired(parsed)) {
                                // Publishing an expiry is how an operator retires an old config;
                                // an expired document must never replace what is running.
                                AppLog.w("Skipped expired live config from $url")
                                lastError = "config_expired"
                                continue
                            }
                            if (!LiveConfigRegistry.isCompatible(parsed, appVersionCode)) {
                                parsedIncompatible = RemoteConfigSyncResult.IncompatibleVersion(
                                    remoteConfigVersion = parsed.configVersion,
                                    minAppVersionCode = parsed.minAppVersionCode,
                                    maxAppVersionCode = parsed.maxAppVersionCode
                                )
                                continue
                            }
                            val sanitized = LiveConfigRegistry.sanitizeConfig(parsed)
                            val now = System.currentTimeMillis()
                            return@execute RemoteConfigSyncResult.Applied(
                                config = sanitized,
                                sourceUrl = url,
                                syncedAtMillis = now,
                                versionChanged = sanitized.configVersion != currentActiveVersion
                            )
                        } else {
                            lastError = "invalid_config_schema"
                        }
                    }
                    is NetworkOutcome.HttpError -> {
                        if (outcome.code != 404) {
                            sawHttp404Only = false
                            lastError = "http_${outcome.code}"
                        }
                    }
                    is NetworkOutcome.Failure -> {
                        sawHttp404Only = false
                        if (outcome.cause is CancellationException) throw outcome.cause
                        lastError = outcome.cause.message ?: "network_error"
                    }
                }
            }

            parsedIncompatible ?: if (sawHttp404Only) {
                AppLog.w("No live config published at any candidate URL (HTTP 404)")
                RemoteConfigSyncResult.NotConfiguredOrMissing
            } else {
                val reason = lastError.ifBlank { "sync_failed" }
                AppLog.w("Live config sync failed: $reason")
                RemoteConfigSyncResult.Error(reason)
            }
        }
    }

    fun parseConfigJson(raw: String): RemoteAppConfig? = runCatching {
        json.decodeFromString<RemoteAppConfig>(raw.trim())
    }.getOrNull()

    companion object {
        const val CONFIG_FILENAME = "chand-live-config.json"

        fun buildCandidateUrls(repoSlug: String, customConfigUrl: String = ""): List<String> {
            val result = mutableListOf<String>()
            val customTrimmed = customConfigUrl.trim()
            if (LiveConfigRegistry.isSafeHttpsUrl(customTrimmed)) {
                result.add(customTrimmed)
            }
            AppUpdateService.parseRepoSlug(repoSlug)?.let { (owner, repo) ->
                result.add("https://cdn.jsdelivr.net/gh/$owner/$repo@main/$CONFIG_FILENAME")
                result.add("https://raw.githubusercontent.com/$owner/$repo/main/$CONFIG_FILENAME")
            }
            return result.distinct()
        }
    }
}
