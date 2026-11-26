package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.AssetHistoryResult
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.CandidateStatus
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.MarketPriceMapper.ExchangeQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class MarketRemoteDataSource(
    private val client: OkHttpClient = NetworkConfig.sharedClient,
    private val financialApi: FinancialApiService = FinancialApiService(client),
    private val oilApi: OilApiService = OilApiService(client)
) : MarketDataSource {

    private val liveMarketDeduplicator = InFlightRequestDeduplicator<String, RemoteFetchResult>()
    private val assetHistoryDeduplicator = InFlightRequestDeduplicator<String, AssetHistoryResult>()
    private val usdHistoryDeduplicator = InFlightRequestDeduplicator<String, Pair<List<AssetHistoryPoint>, String>>()

    private val tgjuSlugs = mapOf(
        "USD" to "price_dollar_rl",
        "EUR" to "price_eur",
        "AED" to "price_aed",
        "GBP" to "price_gbp",
        "KWD" to "price_kwd",
        "TRY" to "price_try",
        "CAD" to "price_cad",
        "CHF" to "price_chf",
        "CNY" to "price_cny",
        "AUD" to "price_aud",
        "JPY" to "price_jpy",
        "SAR" to "price_sar",
        "IQD" to "price_iqd",
        "GOLD_18K" to "geram18",
        "GOLD_24K" to "geram24",
        "GOLD_MESGHAL" to "mesghal",
        "COIN_BAHAR" to "sekeb",
        "COIN_EMAMI" to "sekee",
        "COIN_HALF" to "nim",
        "COIN_QUARTER" to "rob",
        "COIN_GERAMI" to "gerami",
        "XAU_OZ" to "ons"
    )

    private val coinGeckoIds = mapOf(
        "USDT" to "tether",
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "TON" to "the-open-network",
        "SOL" to "solana",
        "BNB" to "binancecoin",
        "XRP" to "ripple",
        "DOGE" to "dogecoin",
        "XAU_OZ" to "pax-gold"
    )

    private fun effectiveTgjuSlugs(): Map<String, String> {
        val cfg = LiveConfigRegistry.currentConfig()
        val map = tgjuSlugs.toMutableMap()
        cfg.assetOverrides.forEach { ov ->
            if (!ov.tgjuKey.isNullOrBlank()) map[ov.id] = ov.tgjuKey
        }
        cfg.extraAssets.forEach { extra ->
            if (!extra.tgjuKey.isNullOrBlank()) map[extra.id] = extra.tgjuKey
        }
        return map
    }

    private fun effectiveCoinGeckoIds(): Map<String, String> {
        val cfg = LiveConfigRegistry.currentConfig()
        val map = coinGeckoIds.toMutableMap()
        cfg.assetOverrides.forEach { ov ->
            if (!ov.coinGeckoId.isNullOrBlank()) map[ov.id] = ov.coinGeckoId
        }
        cfg.extraAssets.forEach { extra ->
            if (!extra.coinGeckoId.isNullOrBlank()) map[extra.id] = extra.coinGeckoId
        }
        return map
    }

    private fun isAssetDisabled(id: String, cfg: ir.thepoo7an.chand.model.RemoteAppConfig): Boolean {
        if (id == "USD") return false
        if (id in cfg.disabledAssetIds) return true
        return cfg.assetOverrides.firstOrNull { it.id == id }?.enabled == false
    }

    private fun applyAssetOverride(
        asset: MarketAsset,
        cfg: ir.thepoo7an.chand.model.RemoteAppConfig
    ): MarketAsset {
        val ov = cfg.assetOverrides.firstOrNull { it.id == asset.id } ?: return asset
        return asset.copy(
            symbol = ov.symbol ?: asset.symbol,
            nameFa = ov.nameFa ?: asset.nameFa,
            nameEn = ov.nameEn ?: asset.nameEn,
            badgeText = ov.badgeText ?: asset.badgeText,
            accentHex = ov.accentHex ?: asset.accentHex
        )
    }

    override suspend fun fetchLiveMarket(
        lastKnownUsdToman: Double?,
        cachedAssetsById: Map<String, MarketAsset>
    ): RemoteFetchResult = withContext(Dispatchers.IO) {
        val liveConfig = LiveConfigRegistry.currentConfig()
        val dedupeKey = "${lastKnownUsdToman ?: "none"}|${cachedAssetsById.size}|cfg${liveConfig.configVersion}"
        liveMarketDeduplicator.execute(dedupeKey) {
            try {
                coroutineScope {
                    val nowMillis = System.currentTimeMillis()
                    val nowSec = nowMillis / 1000L
                    val from24hSec = nowSec - 24L * 3600L

                val effectiveFiats = buildList {
                    MarketPriceMapper.SUPPORTED_FIATS.forEach { spec ->
                        if (!isAssetDisabled(spec.id, liveConfig)) {
                            val ov = liveConfig.assetOverrides.firstOrNull { it.id == spec.id }
                            add(
                                spec.copy(
                                    symbol = ov?.symbol ?: spec.symbol,
                                    nameFa = ov?.nameFa ?: spec.nameFa,
                                    nameEn = ov?.nameEn ?: spec.nameEn,
                                    badge = ov?.badgeText ?: spec.badge,
                                    accentHex = ov?.accentHex ?: spec.accentHex,
                                    tgjuKey = ov?.tgjuKey ?: spec.tgjuKey,
                                    fxCode = ov?.fxCode ?: spec.fxCode
                                )
                            )
                        }
                    }
                    liveConfig.extraAssets
                        .filter { it.category == AssetCategory.FIAT && !isAssetDisabled(it.id, liveConfig) }
                        .forEach { extra ->
                            if (none { it.id == extra.id }) {
                                add(
                                    MarketPriceMapper.FiatSpec(
                                        id = extra.id,
                                        symbol = extra.symbol,
                                        nameFa = extra.nameFa,
                                        nameEn = extra.nameEn,
                                        badge = extra.badgeText,
                                        accentHex = extra.accentHex,
                                        tgjuKey = extra.tgjuKey ?: "",
                                        fxCode = extra.fxCode ?: extra.symbol
                                    )
                                )
                            }
                        }
                }

                val effectiveCryptos = buildList {
                    MarketPriceMapper.SUPPORTED_CRYPTOS.forEach { spec ->
                        if (!isAssetDisabled(spec.id, liveConfig)) {
                            val ov = liveConfig.assetOverrides.firstOrNull { it.id == spec.id }
                            add(
                                spec.copy(
                                    nameFa = ov?.nameFa ?: spec.nameFa,
                                    nameEn = ov?.nameEn ?: spec.nameEn,
                                    badge = ov?.badgeText ?: spec.badge,
                                    accentHex = ov?.accentHex ?: spec.accentHex
                                )
                            )
                        }
                    }
                    liveConfig.extraAssets
                        .filter { it.category == AssetCategory.CRYPTO && !isAssetDisabled(it.id, liveConfig) }
                        .forEach { extra ->
                            if (none { it.id == extra.id }) {
                                add(
                                    MarketPriceMapper.CryptoSpec(
                                        id = extra.id,
                                        nameFa = extra.nameFa,
                                        nameEn = extra.nameEn,
                                        badge = extra.badgeText,
                                        accentHex = extra.accentHex
                                    )
                                )
                            }
                        }
                }

                val extraBinanceSymbols = effectiveCryptos
                    .filter { it.id != "USDT" }
                    .map { "${it.id}USDT" }

                val tgjuDeferred = async { fetchTgjuQuotes(nowMillis) }
                val wallexDeferred = async { fetchWallexQuotes(nowMillis) }
                val bitpinDeferred = async { fetchBitpinQuotes(nowMillis) }
                val nobitexUsdtDeferred = async { fetchNobitexOrderbook("USDTIRT", nowMillis) }
                val binanceDeferred = async { fetchBinanceQuotes(nowMillis, extraBinanceSymbols) }
                val fxSnapshotDeferred = async { financialApi.fetchLiveUsdRateSnapshot(nowMillis) }
                val goldSpotDeferred = async { financialApi.fetchLiveGoldSpotCandidate(nowMillis) }
                val oilBatchDeferred = async { oilApi.fetchLiveOilQuotes(nowMillis) }

                val usdtSparkDeferred = async {
                    fetchWallexUdfHistory("USDTTMN", TimeRange.H24, from24hSec, nowSec, AssetCategory.CRYPTO)
                        .ifEmpty { fetchNobitexUdf("USDTIRT", TimeRange.H24, from24hSec, nowSec, AssetCategory.CRYPTO) }
                }
                val btcSparkDeferred = async {
                    fetchWallexUdfHistory("BTCTMN", TimeRange.H24, from24hSec, nowSec, AssetCategory.CRYPTO)
                        .ifEmpty { fetchBinanceKlines("BTCUSDT", TimeRange.H24, AssetCategory.CRYPTO) }
                }
                val ethSparkDeferred = async {
                    fetchWallexUdfHistory("ETHTMN", TimeRange.H24, from24hSec, nowSec, AssetCategory.CRYPTO)
                        .ifEmpty { fetchBinanceKlines("ETHUSDT", TimeRange.H24, AssetCategory.CRYPTO) }
                }
                val from7dSec = nowSec - 7L * 24L * 3600L
                val multiFxHistoryDeferred = async {
                    financialApi.fetchMultiCurrencyRange(
                        symbols = effectiveFiats.map { it.fxCode },
                        fromSec = from7dSec,
                        toSec = nowSec,
                        range = TimeRange.D7
                    )
                }
                val goldSparkDeferred = async { fetchBinanceKlines("PAXGUSDT", TimeRange.H24, AssetCategory.GOLD) }
                val tgjuUsdRecentDeferred = async { fetchTgjuTablePoints("price_dollar_rl", 7, AssetCategory.FIAT, TimeRange.D7) }
                val tgjuGoldRecentDeferred = async { fetchTgjuTablePoints("geram18", 3, AssetCategory.GOLD, TimeRange.D7) }

                val tgjuAjax = tgjuDeferred.await()
                val wallex = wallexDeferred.await()
                val bitpin = bitpinDeferred.await()
                val nobitexUsdt = nobitexUsdtDeferred.await()
                val binance = binanceDeferred.await()
                val fxSnapshot = fxSnapshotDeferred.await()
                val liveGoldSpotCandidate = goldSpotDeferred.await()
                val oilBatch = oilBatchDeferred.await()
                val multiFxHistory = multiFxHistoryDeferred.await()

                val usdtSpark = usdtSparkDeferred.await().map { it.priceToman }
                val btcSpark = btcSparkDeferred.await().map { it.priceToman }
                val ethSpark = ethSparkDeferred.await().map { it.priceToman }
                val goldSpark = goldSparkDeferred.await().map { it.priceToman }
                val tgjuUsdRecent = tgjuUsdRecentDeferred.await()
                val tgjuGoldRecent = tgjuGoldRecentDeferred.await()

                // Secondary TGJU fallback if ajax.json fails but summary-table-data responds
                val tgju = if (tgjuAjax.isNotEmpty()) {
                    tgjuAjax
                } else if (tgjuUsdRecent.isNotEmpty()) {
                    val lastPoint = tgjuUsdRecent.last()
                    val lastPrice = lastPoint.priceToman
                    val prevPrice = tgjuUsdRecent.getOrNull(tgjuUsdRecent.lastIndex - 1)?.priceToman ?: lastPrice
                    val diff = lastPrice - prevPrice
                    val pct = if (prevPrice > 0.0) (diff / prevPrice) * 100.0 else 0.0
                    mapOf(
                        "price_dollar_rl" to MarketPriceMapper.TgjuQuote(
                            priceToman = lastPrice,
                            openToman = prevPrice,
                            highToman = null,
                            lowToman = null,
                            changeToman = diff,
                            changePct = pct,
                            timestampMillis = lastPoint.timestampSec * 1000L
                        )
                    )
                } else {
                    emptyMap()
                }

                val activeSources = mutableListOf<String>()
                if (tgju.isNotEmpty()) activeSources.add("TGJU")
                if (wallex.isNotEmpty()) activeSources.add("Wallex")
                if (bitpin.isNotEmpty()) activeSources.add("Bitpin")
                if (nobitexUsdt != null) activeSources.add("Nobitex")
                if (binance.isNotEmpty()) activeSources.add("Binance")
                if (liveGoldSpotCandidate != null) activeSources.add("GoldAPI")
                if (fxSnapshot.consensusRates.isNotEmpty() || fxSnapshot.goldSpotXauCandidates.isNotEmpty()) {
                    activeSources.add("FinancialFX")
                }
                if (oilBatch.quotesByProvider.isNotEmpty()) {
                    activeSources.addAll(oilBatch.quotesByProvider.keys)
                }

                if (activeSources.isEmpty()) {
                    return@coroutineScope RemoteFetchResult(
                        assets = emptyList(),
                        fetchedAtMillis = nowMillis,
                        sourceSummary = "",
                        freshness = DataFreshness.UNAVAILABLE,
                        rateBasis = TomanRateBasis.OFFLINE_REFERENCE
                    )
                }

                val tgjuFailed = tgju.isEmpty()
                val wallexFailed = wallex.isEmpty()
                val bitpinFailed = bitpin.isEmpty()
                val nobitexFailed = nobitexUsdt == null
                val binanceFailed = binance.isEmpty()
                val goldApiFailed = liveGoldSpotCandidate == null
                val currencyApiFailed = "CurrencyAPI" in fxSnapshot.failedProviders

                val fetchDoneMillis = System.currentTimeMillis()
                val usdQuote = tgju["price_dollar_rl"]
                val tgjuUsdFallbackDelta = computeRecentTableDelta(
                    tgjuUsdRecent,
                    usdQuote?.priceToman ?: lastKnownUsdToman ?: MarketPriceMapper.DEFAULT_OFFLINE_USD_TOMAN
                )
                val cachedUsdTs = cachedAssetsById["USD"]
                    ?.let { it.sourceTimestampMillis.takeIf { ts -> ts > 0L } ?: it.updatedAtMillis }
                    ?: 0L

                val resolvedUsd = MarketPriceMapper.resolveFreeMarketUsd(
                    tgjuUsdQuote = usdQuote,
                    wallexUsdt = wallex["USDT"],
                    bitpinUsdt = bitpin["USDT"],
                    nobitexUsdtToman = nobitexUsdt?.first,
                    usdtSpark = usdtSpark,
                    tgjuTableFallbackDelta = tgjuUsdFallbackDelta,
                    lastKnownUsdToman = lastKnownUsdToman,
                    nowMillis = fetchDoneMillis,
                    nobitexTimestampMillis = nobitexUsdt?.second ?: 0L,
                    cachedUsdTimestampMillis = cachedUsdTs,
                    tgjuFailed = tgjuFailed,
                    wallexFailed = wallexFailed,
                    bitpinFailed = bitpinFailed,
                    nobitexFailed = nobitexFailed
                )

                val usdToman = resolvedUsd.usdToman
                val usdtToman = resolvedUsd.usdtToman
                val usdChangePct = resolvedUsd.usdChangePct
                val usdChangeToman = resolvedUsd.usdChangeToman
                val usdtChangePct = resolvedUsd.usdtChangePct

                val usdRealSpark = when {
                    usdtSpark.size >= 4 -> MarketPriceMapper.scaleSeriesToCurrent(usdtSpark, usdToman)
                    tgjuUsdRecent.size >= 2 -> tgjuUsdRecent.map { it.priceToman }
                    else -> emptyList()
                }

                val assets = mutableListOf<MarketAsset>()

                assets.add(
                    MarketAsset(
                        id = "USD",
                        symbol = "USD",
                        nameFa = "دلار آمریکا",
                        nameEn = "US Dollar",
                        category = AssetCategory.FIAT,
                        badgeText = "🇺🇸",
                        accentHex = 0xFF10B981,
                        priceToman = usdToman,
                        priceUsd = 1.0,
                        change24hPercent = usdChangePct,
                        change24hToman = usdChangeToman,
                        high24hToman = usdQuote?.highToman?.takeIf { it > 0.0 }?.let { maxOf(it, usdToman) },
                        low24hToman = usdQuote?.lowToman?.takeIf { it > 0.0 }?.let { minOf(it, usdToman) },
                        sparkline = usdRealSpark,
                        sourceLabel = resolvedUsd.usdSourceLabel,
                        updatedAtMillis = fetchDoneMillis,
                        freshness = resolvedUsd.usdFreshness,
                        sourceTimestampMillis = resolvedUsd.usdSourceTimestampMillis,
                        fetchedAtMillis = fetchDoneMillis,
                        disputed = resolvedUsd.usdDisputed,
                        singleSource = resolvedUsd.usdSingleSource,
                        isCalculatedPrice = resolvedUsd.usdIsCalculated,
                        candidates = resolvedUsd.usdCandidates
                    )
                )

                effectiveFiats.forEach { spec ->
                    val fxSeries = multiFxHistory[spec.fxCode].orEmpty()
                    val currencySpark = if (fxSeries.size >= 3) {
                        fxSeries.map { (ts, fx) ->
                            val usdAnchor = if (tgjuUsdRecent.size >= 2) {
                                nearestValue(ts, tgjuUsdRecent)
                            } else {
                                usdToman
                            }
                            usdAnchor / fx.coerceAtLeast(0.0001)
                        }
                    } else {
                        emptyList()
                    }
                    val fxConsensus = fxSnapshot.consensusRates[spec.fxCode]
                    MarketPriceMapper.mapFiatAsset(
                        spec = spec,
                        usdToman = usdToman,
                        usdChangePct = usdChangePct,
                        tgjuQuote = spec.tgjuKey.takeIf { it.isNotBlank() }?.let { tgju[it] },
                        fxPerUsd = fxConsensus?.price,
                        usdtSpark = usdtSpark,
                        nowMillis = fetchDoneMillis,
                        currencySpark = currencySpark,
                        fxConsensus = fxConsensus,
                        usdBasisFreshness = resolvedUsd.usdFreshness,
                        usdSourceTimestampMillis = resolvedUsd.usdSourceTimestampMillis,
                        tgjuFailed = tgjuFailed,
                        fxFailedProviders = fxSnapshot.failedProviders
                    )?.let { assets.add(it) }
                }

                val spotCandidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
                if (liveGoldSpotCandidate != null) spotCandidates.add(liveGoldSpotCandidate)
                spotCandidates.addAll(fxSnapshot.goldSpotXauCandidates)

                val mappedGolds = MarketPriceMapper.mapGoldAssets(
                    tgju = tgju,
                    usdToman = usdToman,
                    usdChangePct = usdChangePct,
                    usdBasisFreshness = resolvedUsd.usdFreshness,
                    usdSourceTimestampMillis = resolvedUsd.usdSourceTimestampMillis,
                    goldSpotCandidates = spotCandidates,
                    paxgQuote = binance["PAXG"],
                    tgjuGoldRecent = tgjuGoldRecent,
                    goldSpark = goldSpark,
                    usdtSpark = usdtSpark,
                    cachedAssetsById = cachedAssetsById,
                    nowMillis = fetchDoneMillis,
                    computeTableDelta = ::computeRecentTableDelta,
                    tgjuFailed = tgjuFailed,
                    binanceFailed = binanceFailed,
                    goldApiFailed = goldApiFailed,
                    currencyApiFailed = currencyApiFailed
                ).filterNot { isAssetDisabled(it.id, liveConfig) }
                    .map { applyAssetOverride(it, liveConfig) }
                assets.addAll(mappedGolds)

                // Support any dynamic extra Gold assets from remote config referencing TGJU
                liveConfig.extraAssets
                    .filter { it.category == AssetCategory.GOLD && !isAssetDisabled(it.id, liveConfig) && assets.none { existing -> existing.id == it.id } }
                    .forEach { extra ->
                        val q = extra.tgjuKey?.let { tgju[it] }
                        if (q != null && q.priceToman > 0.0) {
                            val ts = q.timestampMillis.coerceAtLeast(0L)
                            assets.add(
                                MarketAsset(
                                    id = extra.id,
                                    symbol = extra.symbol,
                                    nameFa = extra.nameFa,
                                    nameEn = extra.nameEn,
                                    category = AssetCategory.GOLD,
                                    badgeText = extra.badgeText,
                                    accentHex = extra.accentHex,
                                    priceToman = q.priceToman,
                                    priceUsd = if (usdToman > 0.0) q.priceToman / usdToman else null,
                                    change24hPercent = q.changePct,
                                    change24hToman = q.changeToman,
                                    high24hToman = q.highToman,
                                    low24hToman = q.lowToman,
                                    sparkline = MarketPriceMapper.scaleSeriesToCurrent(goldSpark, q.priceToman),
                                    sourceLabel = "TGJU",
                                    updatedAtMillis = fetchDoneMillis,
                                    freshness = MarketPriceMapper.deriveFreshness(
                                        category = AssetCategory.GOLD,
                                        fetchedThisRefresh = true,
                                        hasUsableValue = true,
                                        isCachedSource = false,
                                        sourceTimestampMillis = ts,
                                        nowMillis = fetchDoneMillis
                                    ),
                                    sourceTimestampMillis = ts,
                                    fetchedAtMillis = fetchDoneMillis,
                                    singleSource = true,
                                    isCalculatedPrice = false,
                                    candidates = listOf(
                                        SourceCandidateRecord(
                                            sourceName = "TGJU",
                                            priceToman = q.priceToman,
                                            rawPrice = q.priceToman,
                                            sourceTimestampMillis = ts,
                                            isPrimary = true,
                                            status = CandidateStatus.INCLUDED,
                                            exclusionReason = null
                                        )
                                    )
                                )
                            )
                        }
                    }

                effectiveCryptos.forEach { spec ->
                    val sparkInput = when (spec.id) {
                        "USDT" -> usdtSpark
                        "BTC" -> btcSpark
                        "ETH" -> ethSpark
                        else -> emptyList()
                    }
                    MarketPriceMapper.mapCryptoAsset(
                        spec = spec,
                        usdtToman = usdtToman,
                        usdtChangePct = usdtChangePct,
                        wallex = wallex[spec.id],
                        bitpin = bitpin[spec.id],
                        binance = binance[spec.id],
                        nobitexUsdtToman = nobitexUsdt?.first,
                        sparkInput = sparkInput,
                        nowMillis = fetchDoneMillis,
                        usdtBasisFreshness = resolvedUsd.usdtFreshness,
                        usdtSourceTimestampMillis = resolvedUsd.usdtSourceTimestampMillis,
                        nobitexTimestampMillis = nobitexUsdt?.second ?: 0L,
                        wallexFailed = wallexFailed,
                        bitpinFailed = bitpinFailed,
                        binanceFailed = binanceFailed,
                        nobitexFailed = nobitexFailed,
                        usdtBasisSourceLabel = resolvedUsd.usdtSourceLabel,
                        usdtBasisIsCalculated = resolvedUsd.usdtIsCalculated,
                        usdtBasisCandidates = resolvedUsd.usdtCandidates
                    )?.let { assets.add(it) }
                }

                val effectiveOilSpecs = MarketPriceMapper.SUPPORTED_OILS
                    .filterNot { isAssetDisabled(it.id, liveConfig) }
                if (effectiveOilSpecs.isNotEmpty()) {
                    val mappedOils = MarketPriceMapper.mapOilAssets(
                        specs = effectiveOilSpecs,
                        batch = oilBatch,
                        usdToman = usdToman,
                        usdBasisFreshness = resolvedUsd.usdFreshness,
                        cachedAssetsById = cachedAssetsById,
                        nowMillis = fetchDoneMillis
                    ).map { applyAssetOverride(it, liveConfig) }
                    assets.addAll(mappedOils)
                }

                val resultFreshness = when {
                    assets.any { it.freshness == DataFreshness.LIVE } -> DataFreshness.LIVE
                    assets.any { it.freshness == DataFreshness.DELAYED } -> DataFreshness.DELAYED
                    resolvedUsd.rateBasis == TomanRateBasis.OFFLINE_REFERENCE -> DataFreshness.UNAVAILABLE
                    resolvedUsd.rateBasis == TomanRateBasis.CACHED_USD_ANCHOR -> DataFreshness.CACHED
                    assets.any { it.freshness == DataFreshness.CACHED } -> DataFreshness.CACHED
                    else -> DataFreshness.UNAVAILABLE
                }

                RemoteFetchResult(
                    assets = assets,
                    fetchedAtMillis = fetchDoneMillis,
                    sourceSummary = "${resolvedUsd.rateBasis.labelEn} • ${activeSources.joinToString(" • ")}",
                    freshness = resultFreshness,
                    rateBasis = resolvedUsd.rateBasis
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            RemoteFetchResult(
                assets = emptyList(),
                fetchedAtMillis = System.currentTimeMillis(),
                sourceSummary = "",
                freshness = DataFreshness.UNAVAILABLE,
                rateBasis = TomanRateBasis.OFFLINE_REFERENCE
            )
        }
        }
    }

    /**
     * Fetches 100% real historical data from TGJU, Wallex, Nobitex, Binance, CoinGecko, and Frankfurter.
     * Never invents synthetic sine waves.
     */
    override suspend fun fetchAssetHistory(
        asset: MarketAsset,
        range: TimeRange
    ): AssetHistoryResult = withContext(Dispatchers.IO) {
        val dedupeKey = "${asset.id}|${range.name}"
        assetHistoryDeduplicator.execute(dedupeKey) {
            try {
                coroutineScope {
                    val nowSec = System.currentTimeMillis() / 1000L
                    val fromSec = nowSec - range.lookbackSeconds

                val liveAppendPrice = if (asset.freshness != DataFreshness.UNAVAILABLE && asset.priceToman > 0.0) {
                    asset.priceToman
                } else {
                    0.0
                }

                // 0. GLOBAL CRUDE OIL (Brent / WTI in USD/barrel)
                if (asset.category == AssetCategory.OIL) {
                    val (oilPts, oilSrc) = oilApi.fetchOilHistory(asset.id, range, System.currentTimeMillis())
                    if (oilPts.size >= 3) {
                        val liveUsd = if (asset.freshness != DataFreshness.UNAVAILABLE &&
                            (asset.priceUsd ?: 0.0) > 0.0
                        ) {
                            asset.priceUsd!!
                        } else {
                            0.0
                        }
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(oilPts, nowSec, liveUsd, scaleToLast = false),
                            sourceLabel = oilSrc,
                            isRealData = true
                        )
                    }
                    return@coroutineScope AssetHistoryResult(
                        points = emptyList(),
                        sourceLabel = "",
                        isRealData = false
                    )
                }

                // 1. CRYPTO ASSETS (Wallex TMN -> Nobitex IRT -> Binance USDT*USD -> CoinGecko*USD)
                if (asset.category == AssetCategory.CRYPTO) {
                    val wallexDeferred = async {
                        fetchWallexUdfHistory("${asset.symbol}TMN", range, fromSec, nowSec, asset.category)
                    }
                    val nobitexDeferred = async {
                        fetchNobitexUdf("${asset.symbol}IRT", range, fromSec, nowSec, asset.category)
                    }

                    val wallexPts = wallexDeferred.await()
                    if (wallexPts.size >= 4) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(wallexPts, nowSec, liveAppendPrice, scaleToLast = false),
                            sourceLabel = "Wallex",
                            isRealData = true
                        )
                    }
                    val nobitexPts = nobitexDeferred.await()
                    if (nobitexPts.size >= 4) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(nobitexPts, nowSec, liveAppendPrice, scaleToLast = false),
                            sourceLabel = "Nobitex",
                            isRealData = true
                        )
                    }

                    if (asset.symbol != "USDT") {
                        val binanceDeferred = async {
                            fetchBinanceKlines("${asset.symbol}USDT", range, asset.category)
                        }
                        val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }

                        var usdCurve = binanceDeferred.await()
                        var src = "Binance"
                        if (usdCurve.size < 4) {
                            val cgId = effectiveCoinGeckoIds()[asset.id]
                            if (cgId != null) {
                                usdCurve = fetchCoinGeckoHistory(cgId, range, asset.category)
                                src = "CoinGecko"
                            }
                        }
                        val (tmnCurve, tmnSrc) = usdBaseDeferred.await()
                        if (usdCurve.size >= 4 && tmnCurve.isNotEmpty()) {
                            val combined = combineUsdWithTomanHistory(
                                usdSeries = usdCurve,
                                tomanPerUsdSeries = tmnCurve,
                                category = asset.category,
                                range = range
                            )
                            if (combined.size >= 4) {
                                return@coroutineScope AssetHistoryResult(
                                    points = appendLivePointIfNewer(combined, nowSec, liveAppendPrice, scaleToLast = false),
                                    sourceLabel = "$src + $tmnSrc",
                                    isRealData = true
                                )
                            }
                        }
                    }
                }

                // 2. GOLD & COINS
                if (asset.category == AssetCategory.GOLD) {
                    if (asset.id == "XAU_OZ") {
                        val paxgDeferred = async {
                            fetchBinanceKlines("PAXGUSDT", range, AssetCategory.GOLD, spikeThresholdPct = 0.0)
                        }
                        val goldApiDeferred = async {
                            if (range != TimeRange.H24) {
                                financialApi.fetchGoldUsdHistory(fromSec, nowSec, range, spikeThresholdPct = 0.0)
                            } else {
                                emptyList()
                            }
                        }
                        val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }

                        val paxgRaw = paxgDeferred.await()
                        val goldCdnRaw = goldApiDeferred.await()
                        val (usdPts, usdSrc) = usdBaseDeferred.await()
                        val (primaryRaw, corroboratingRaw, goldSrc) = if (paxgRaw.size >= 4) {
                            Triple(paxgRaw, goldCdnRaw, "PAXG")
                        } else {
                            Triple(goldCdnRaw, paxgRaw, "GoldAPI")
                        }
                        val goldUsdPts = HistoryNormalizer.normalize(
                            points = primaryRaw,
                            category = AssetCategory.GOLD,
                            range = range,
                            corroborating = corroboratingRaw
                        )

                        if (goldUsdPts.size >= 3 && usdPts.isNotEmpty()) {
                            val combined = combineUsdWithTomanHistory(
                                usdSeries = goldUsdPts,
                                tomanPerUsdSeries = usdPts,
                                category = AssetCategory.GOLD,
                                range = range,
                                corroboratingUsdSeries = corroboratingRaw
                            )
                            if (combined.size >= 3) {
                                return@coroutineScope AssetHistoryResult(
                                    points = appendLivePointIfNewer(combined, nowSec, liveAppendPrice, scaleToLast = false),
                                    sourceLabel = "$goldSrc + $usdSrc",
                                    isRealData = true
                                )
                            }
                        }
                    }

                    // Multi-day ranges (7D, 1M, 1Y) -> TGJU official Tehran Gold & Coin history table
                    val slug = effectiveTgjuSlugs()[asset.id]
                    if (range != TimeRange.H24 && slug != null) {
                        val tgjuPts = fetchTgjuTablePoints(slug, tgjuLengthFor(range), asset.category, range)
                        if (tgjuPts.size >= 3) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(tgjuPts, nowSec, liveAppendPrice, scaleToLast = false),
                                sourceLabel = "TGJU",
                                isRealData = true
                            )
                        }
                    }

                    // 24H Gold or fallback -> combine global gold (PAXG or GoldAPI XAU) with real USD/TMN history
                    if (liveAppendPrice > 0.0 && asset.id in setOf("XAU_OZ", "GOLD_18K", "GOLD_24K", "GOLD_MESGHAL")) {
                        val paxgDeferred = async {
                            fetchBinanceKlines("PAXGUSDT", range, AssetCategory.GOLD, spikeThresholdPct = 0.0)
                        }
                        val goldApiDeferred = async {
                            if (range != TimeRange.H24) {
                                financialApi.fetchGoldUsdHistory(fromSec, nowSec, range, spikeThresholdPct = 0.0)
                            } else {
                                emptyList()
                            }
                        }
                        val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }
                        val paxgRaw = paxgDeferred.await()
                        val goldCdnRaw = goldApiDeferred.await()
                        val (usdPts, usdSrc) = usdBaseDeferred.await()
                        val (primaryRaw, corroboratingRaw, goldSrc) = if (paxgRaw.size >= 4) {
                            Triple(paxgRaw, goldCdnRaw, "PAXG")
                        } else {
                            Triple(goldCdnRaw, paxgRaw, "GoldAPI")
                        }
                        val goldUsdPts = HistoryNormalizer.normalize(
                            points = primaryRaw,
                            category = AssetCategory.GOLD,
                            range = range,
                            corroborating = corroboratingRaw
                        )

                        if (goldUsdPts.size >= 3 && usdPts.isNotEmpty()) {
                            val combined = combineUsdWithTomanHistory(
                                usdSeries = goldUsdPts,
                                tomanPerUsdSeries = usdPts,
                                category = AssetCategory.GOLD,
                                range = range,
                                corroboratingUsdSeries = corroboratingRaw
                            )
                            if (combined.size >= 3) {
                                val aligned = scalePointsToCurrent(combined, liveAppendPrice)
                                return@coroutineScope AssetHistoryResult(
                                    points = aligned,
                                    sourceLabel = "$goldSrc + $usdSrc",
                                    isRealData = true
                                )
                            }
                        }
                    }

                    // Final real fallback for Gold on 24H -> last daily points from TGJU if available
                    if (slug != null) {
                        val tgjuPts = fetchTgjuTablePoints(slug, 7, asset.category, TimeRange.D7)
                        if (tgjuPts.size >= 2) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(tgjuPts, nowSec, liveAppendPrice, scaleToLast = false),
                                sourceLabel = "TGJU Daily",
                                isRealData = true
                            )
                        }
                    }
                }

                // 3. FIAT CURRENCIES (USD, EUR, AED, GBP, KWD, TRY, SAR, IQD, ...)
                if (asset.category == AssetCategory.FIAT) {
                    val slug = effectiveTgjuSlugs()[asset.id]

                    // Multi-day ranges (7D, 1M, 1Y) -> TGJU official daily table first
                    if (range != TimeRange.H24 && slug != null) {
                        val tgjuPts = fetchTgjuTablePoints(slug, tgjuLengthFor(range), asset.category, range)
                        if (tgjuPts.size >= 3) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(tgjuPts, nowSec, liveAppendPrice, scaleToLast = false),
                                sourceLabel = "TGJU",
                                isRealData = true
                            )
                        }
                    }

                    // Multi-provider FinancialApiService FX timeseries (Frankfurter ECB + CurrencyAPI for AED/KWD/SAR/IQD)
                    if (asset.symbol != "USD") {
                        val fxDeferred = async {
                            financialApi.fetchUsdToCurrencyHistory(asset.symbol, fromSec, nowSec, range)
                        }
                        val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }
                        val (fxPts, fxSrc) = fxDeferred.await()
                        val (usdPts, usdSrc) = usdBaseDeferred.await()
                        if (fxPts.isNotEmpty() && usdPts.size >= 3) {
                            val combined = combineTomanWithInverseFx(
                                usdTomanSeries = usdPts,
                                fxPerUsdSeries = fxPts,
                                category = asset.category,
                                range = range
                            )
                            if (combined.size >= 3) {
                                return@coroutineScope AssetHistoryResult(
                                    points = scalePointsToCurrent(combined, liveAppendPrice),
                                    sourceLabel = "$fxSrc + $usdSrc",
                                    isRealData = true
                                )
                            }
                        }
                    }

                    // 24H Fiat or USD -> Real hourly USDT/TMN candles aligned smoothly to asset.priceToman
                    val (usdPts, usdSrc) = fetchUsdTomanHistory(range, fromSec, nowSec)
                    if (usdPts.size >= 4) {
                        val aligned = if (asset.symbol == "USD" && usdSrc == "TGJU") {
                            appendLivePointIfNewer(usdPts, nowSec, liveAppendPrice, scaleToLast = false)
                        } else {
                            scalePointsToCurrent(usdPts, liveAppendPrice)
                        }
                        return@coroutineScope AssetHistoryResult(
                            points = aligned,
                            sourceLabel = usdSrc,
                            isRealData = true
                        )
                    }

                    if (slug != null) {
                        val tgjuPts = fetchTgjuTablePoints(slug, 7, asset.category, TimeRange.D7)
                        if (tgjuPts.size >= 2) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(tgjuPts, nowSec, liveAppendPrice, scaleToLast = false),
                                sourceLabel = "TGJU Daily",
                                isRealData = true
                            )
                        }
                    }
                }

                AssetHistoryResult(
                    points = emptyList(),
                    sourceLabel = "",
                    isRealData = false
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            AssetHistoryResult(
                points = emptyList(),
                sourceLabel = "",
                isRealData = false
            )
        }
        }
    }

    private suspend fun fetchUsdTomanHistory(
        range: TimeRange,
        fromSec: Long,
        nowSec: Long
    ): Pair<List<AssetHistoryPoint>, String> {
        val bucketKey = "${range.name}|${nowSec / 60L}"
        return usdHistoryDeduplicator.execute(bucketKey) {
            coroutineScope {
                if (range != TimeRange.H24) {
                    val tgjuPts = fetchTgjuTablePoints(
                        slug = "price_dollar_rl",
                        length = tgjuLengthFor(range),
                        category = AssetCategory.FIAT,
                        range = range
                    )
                    if (tgjuPts.size >= 3) {
                        return@coroutineScope tgjuPts to "TGJU"
                    }
                }
                val wallexPts = fetchWallexUdfHistory("USDTTMN", range, fromSec, nowSec, AssetCategory.FIAT)
                if (wallexPts.size >= 4) {
                    return@coroutineScope wallexPts to "Wallex"
                }
                val nobitexPts = fetchNobitexUdf("USDTIRT", range, fromSec, nowSec, AssetCategory.FIAT)
                if (nobitexPts.size >= 4) {
                    return@coroutineScope nobitexPts to "Nobitex"
                }
                emptyList<AssetHistoryPoint>() to ""
            }
        }
    }

    private fun tgjuLengthFor(range: TimeRange): Int = when (range) {
        TimeRange.H24 -> 7
        TimeRange.D7 -> 14
        TimeRange.M1 -> 35
        TimeRange.Y1 -> 370
    }

    private suspend fun executeGet(
        url: String,
        profile: NetworkConfig.RequestProfile = NetworkConfig.profileForUrl(url)
    ): String? =
        when (val outcome = NetworkConfig.executeGetWithRetry(client = client, url = url, profile = profile)) {
            is NetworkOutcome.Success -> outcome.data
            is NetworkOutcome.HttpError, is NetworkOutcome.Failure -> null
        }

    private suspend fun fetchTgjuQuotes(nowMillis: Long): Map<String, MarketPriceMapper.TgjuQuote> {
        val url = LiveConfigRegistry.currentConfig().endpoints.tgjuAjaxUrl
            .ifBlank { "https://call5.tgju.org/ajax.json?t=$nowMillis" }
            .replace("{ts}", nowMillis.toString())
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        ) ?: return emptyMap()
        return parseTgjuQuotesPayload(raw, nowMillis)
    }

    private suspend fun fetchTgjuTablePoints(
        slug: String,
        length: Int,
        category: AssetCategory = AssetCategory.FIAT,
        range: TimeRange = TimeRange.M1,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val now = System.currentTimeMillis()
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.tgjuTableUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{slug}", slug)
                .replace("{length}", length.toString())
                .replace("{ts}", now.toString())
        } else {
            "https://api.tgju.org/v1/market/indicator/summary-table-data/$slug?length=$length&t=$now"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.HISTORY
        ) ?: return emptyList()
        return parseTgjuTablePayload(raw, slug = slug, spikeThresholdPct = spikeThresholdPct)
    }

    private suspend fun fetchWallexQuotes(nowMillis: Long): Map<String, ExchangeQuote> {
        val url = LiveConfigRegistry.currentConfig().endpoints.wallexMarketsUrl
            .ifBlank { "https://api.wallex.ir/v1/markets" }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        ) ?: return emptyMap()
        return parseWallexQuotesPayload(raw, nowMillis)
    }

    private suspend fun fetchWallexUdfHistory(
        symbol: String,
        range: TimeRange,
        fromSec: Long,
        toSec: Long,
        category: AssetCategory = AssetCategory.CRYPTO,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val res = when (range) {
            TimeRange.H24 -> "60"
            TimeRange.D7 -> "240"
            TimeRange.M1, TimeRange.Y1 -> "1D"
        }
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.wallexUdfUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{symbol}", symbol)
                .replace("{resolution}", res)
                .replace("{from}", fromSec.toString())
                .replace("{to}", toSec.toString())
        } else {
            "https://api.wallex.ir/v1/udf/history?symbol=$symbol&resolution=$res&from=$fromSec&to=$toSec"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.HISTORY
        ) ?: return emptyList()
        return parseWallexUdfPayload(raw, spikeThresholdPct = spikeThresholdPct)
    }

    private suspend fun fetchBitpinQuotes(nowMillis: Long): Map<String, ExchangeQuote> {
        val url = LiveConfigRegistry.currentConfig().endpoints.bitpinMarketsUrl
            .ifBlank { "https://api.bitpin.ir/v1/mkt/markets/" }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        ) ?: return emptyMap()
        return parseBitpinQuotesPayload(raw, nowMillis)
    }

    private suspend fun fetchBinanceQuotes(
        nowMillis: Long,
        extraSymbols: List<String> = emptyList()
    ): Map<String, ExchangeQuote> {
        val allSymbols = (listOf("BTCUSDT", "ETHUSDT", "TONUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT", "PAXGUSDT") + extraSymbols)
            .distinct()
        val symbolsJson = allSymbols.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val customBase = LiveConfigRegistry.currentConfig().endpoints.binanceTickerUrl
        val url = if (customBase.isNotBlank()) {
            if ("{symbols}" in customBase) customBase.replace("{symbols}", symbolsJson)
            else customBase
        } else {
            "https://api.binance.com/api/v3/ticker/24hr?symbols=$symbolsJson"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        ) ?: return emptyMap()
        return parseBinanceQuotesPayload(raw, nowMillis)
    }

    private suspend fun fetchBinanceKlines(
        symbol: String,
        range: TimeRange,
        category: AssetCategory = AssetCategory.CRYPTO,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val (interval, limit) = when (range) {
            TimeRange.H24 -> "1h" to 24
            TimeRange.D7 -> "4h" to 42
            TimeRange.M1 -> "1d" to 30
            TimeRange.Y1 -> "1d" to 365
        }
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.binanceKlinesUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{symbol}", symbol)
                .replace("{interval}", interval)
                .replace("{limit}", limit.toString())
        } else {
            "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.HISTORY
        ) ?: return emptyList()
        return parseBinanceKlinesPayload(raw, spikeThresholdPct = spikeThresholdPct)
    }

    private suspend fun fetchCoinGeckoHistory(
        coinId: String,
        range: TimeRange,
        category: AssetCategory = AssetCategory.CRYPTO,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val days = when (range) {
            TimeRange.H24 -> "1"
            TimeRange.D7 -> "7"
            TimeRange.M1 -> "30"
            TimeRange.Y1 -> "365"
        }
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.coinGeckoHistoryUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{coinId}", coinId)
                .replace("{days}", days)
        } else {
            "https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=usd&days=$days"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.HISTORY
        ) ?: return emptyList()
        return parseCoinGeckoHistoryPayload(raw, spikeThresholdPct = spikeThresholdPct)
    }

    private suspend fun fetchNobitexOrderbook(symbol: String, nowMillis: Long): Pair<Double, Long>? {
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.nobitexOrderbookUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{symbol}", symbol)
        } else {
            "https://api.nobitex.ir/v2/orderbook/$symbol"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        ) ?: return null
        return parseNobitexOrderbookPayload(raw, nowMillis)
    }

    private suspend fun fetchNobitexUdf(
        symbol: String,
        range: TimeRange,
        fromSec: Long,
        toSec: Long,
        category: AssetCategory = AssetCategory.CRYPTO,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val resolution = range.nobitexResolution
        val tmpl = LiveConfigRegistry.currentConfig().endpoints.nobitexUdfUrlTemplate
        val url = if (tmpl.isNotBlank()) {
            tmpl.replace("{symbol}", symbol)
                .replace("{resolution}", resolution)
                .replace("{from}", fromSec.toString())
                .replace("{to}", toSec.toString())
        } else {
            "https://apiv2.nobitex.ir/market/udf/history?symbol=$symbol&resolution=$resolution&from=$fromSec&to=$toSec"
        }
        val raw = executeGet(
            url = url,
            profile = NetworkConfig.RequestProfile.HISTORY
        ) ?: return emptyList()
        return parseNobitexUdfPayload(raw, spikeThresholdPct = spikeThresholdPct)
    }

    private fun combineUsdWithTomanHistory(
        usdSeries: List<AssetHistoryPoint>,
        tomanPerUsdSeries: List<AssetHistoryPoint>,
        category: AssetCategory = AssetCategory.CRYPTO,
        range: TimeRange = TimeRange.M1,
        corroboratingUsdSeries: List<AssetHistoryPoint> = emptyList()
    ): List<AssetHistoryPoint> {
        val cleanUsd = HistoryNormalizer.normalize(
            points = usdSeries,
            category = category,
            range = range,
            corroborating = corroboratingUsdSeries
        )
        val cleanToman = HistoryNormalizer.normalize(
            points = tomanPerUsdSeries,
            category = AssetCategory.FIAT,
            range = range
        )
        if (cleanUsd.isEmpty() || cleanToman.isEmpty()) return emptyList()
        val combined = cleanUsd.map { pt ->
            val usdTmn = nearestValue(pt.timestampSec, cleanToman)
            AssetHistoryPoint(pt.timestampSec, pt.priceToman * usdTmn)
        }
        val cleanCorroboratingUsd = HistoryNormalizer.normalize(corroboratingUsdSeries, spikeThresholdPct = 0.0)
        val corroboratingCombined = cleanCorroboratingUsd.map { pt ->
            val usdTmn = nearestValue(pt.timestampSec, cleanToman)
            AssetHistoryPoint(pt.timestampSec, pt.priceToman * usdTmn)
        }
        return HistoryNormalizer.normalize(
            points = combined,
            category = category,
            range = range,
            corroborating = corroboratingCombined
        )
    }

    private fun combineTomanWithInverseFx(
        usdTomanSeries: List<AssetHistoryPoint>,
        fxPerUsdSeries: List<Pair<Long, Double>>,
        category: AssetCategory = AssetCategory.FIAT,
        range: TimeRange = TimeRange.M1
    ): List<AssetHistoryPoint> {
        val cleanUsd = HistoryNormalizer.normalize(
            points = usdTomanSeries,
            category = AssetCategory.FIAT,
            range = range
        )
        val fxPoints = HistoryNormalizer.normalize(
            points = fxPerUsdSeries.map { AssetHistoryPoint(it.first, it.second) },
            category = AssetCategory.FIAT,
            range = range
        )
        if (cleanUsd.isEmpty() || fxPoints.isEmpty()) return emptyList()
        val combined = cleanUsd.map { pt ->
            val fx = nearestValue(pt.timestampSec, fxPoints).coerceAtLeast(0.0001)
            AssetHistoryPoint(pt.timestampSec, pt.priceToman / fx)
        }
        return HistoryNormalizer.normalize(
            points = combined,
            category = category,
            range = range
        )
    }

    private fun nearestValue(targetSec: Long, series: List<AssetHistoryPoint>): Double {
        if (series.size == 1) return series.first().priceToman
        var lo = 0
        var hi = series.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val ts = series[mid].timestampSec
            when {
                ts == targetSec -> return series[mid].priceToman
                ts < targetSec -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        val left = series.getOrNull(hi)
        val right = series.getOrNull(lo)
        return when {
            left == null -> right!!.priceToman
            right == null -> left.priceToman
            abs(targetSec - left.timestampSec) <= abs(right.timestampSec - targetSec) -> left.priceToman
            else -> right.priceToman
        }
    }

    private fun appendLivePointIfNewer(
        points: List<AssetHistoryPoint>,
        nowSec: Long,
        currentPrice: Double,
        scaleToLast: Boolean
    ): List<AssetHistoryPoint> {
        if (points.isEmpty() || currentPrice <= 0.0) return points
        val base = if (scaleToLast) scalePointsToCurrent(points, currentPrice) else points
        val lastTs = base.last().timestampSec
        return if (nowSec - lastTs > 600L) {
            base + AssetHistoryPoint(nowSec, currentPrice)
        } else {
            base.dropLast(1) + AssetHistoryPoint(lastTs, currentPrice)
        }
    }

    private fun scalePointsToCurrent(
        points: List<AssetHistoryPoint>,
        targetCurrent: Double
    ): List<AssetHistoryPoint> {
        if (points.isEmpty() || targetCurrent <= 0.0) return points
        val lastPrice = points.last().priceToman
        if (lastPrice <= 0.0) return points
        val factor = targetCurrent / lastPrice
        return points.map { it.copy(priceToman = it.priceToman * factor) }
    }

    private fun computeRecentTableDelta(
        points: List<AssetHistoryPoint>,
        currentPrice: Double
    ): Pair<Double, Double>? {
        if (points.size < 2 || currentPrice <= 0.0) return null
        val prev = points[points.lastIndex - 1].priceToman
        val last = points.last().priceToman
        if (prev <= 0.0 || last <= 0.0) return null
        val ratio = currentPrice / last
        val normalizedPrev = if (ratio in 0.85..1.15) prev else return null
        val diff = currentPrice - normalizedPrev
        val pct = (diff / normalizedPrev) * 100.0
        return diff to pct
    }

    companion object {
        internal fun parseTgjuQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, MarketPriceMapper.TgjuQuote> = runCatching {
            val root = JSONObject(raw)
            val current = root.optJSONObject("current") ?: return emptyMap()
            val map = mutableMapOf<String, MarketPriceMapper.TgjuQuote>()
            val keys = current.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = current.optJSONObject(k) ?: continue
                val priceRial = parseNumeric(obj.optString("p")) ?: continue
                if (priceRial <= 0.0) continue
                val openRial = parseNumeric(obj.optString("o"))?.takeIf { it > 0.0 }
                val highRial = parseNumeric(obj.optString("h"))?.takeIf { it > 0.0 }
                val lowRial = parseNumeric(obj.optString("l"))?.takeIf { it > 0.0 }
                var diffRial = parseNumeric(obj.optString("d")) ?: 0.0
                var dp = parseNumeric(obj.optString("dp")) ?: 0.0
                val dt = obj.optString("dt")
                val sign = if (dt == "low") -1.0 else 1.0

                if (dp > 0.0) dp *= sign
                if (diffRial > 0.0) diffRial *= sign

                if (abs(diffRial) < 0.01 && openRial != null && openRial > 0.0 && abs(priceRial - openRial) > 0.5) {
                    diffRial = priceRial - openRial
                }
                if (abs(dp) < 0.001 && abs(diffRial) > 0.5 && priceRial > diffRial) {
                    val prev = priceRial - diffRial
                    if (prev > 0.0) dp = (diffRial / prev) * 100.0
                }
                if (abs(diffRial) < 0.01 && abs(dp) > 0.001) {
                    val prev = priceRial / (1.0 + dp / 100.0)
                    diffRial = priceRial - prev
                }

                val quoteTs = parseTgjuTimestampMillis(obj.optString("ts")) ?: 0L

                map[k] = MarketPriceMapper.TgjuQuote(
                    priceToman = priceRial / 10.0,
                    openToman = openRial?.let { it / 10.0 },
                    highToman = highRial?.let { it / 10.0 },
                    lowToman = lowRial?.let { it / 10.0 },
                    changeToman = diffRial / 10.0,
                    changePct = dp,
                    timestampMillis = quoteTs
                )
            }
            map
        }.getOrDefault(emptyMap())

        internal fun parseTgjuTimestampMillis(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val tz = TimeZone.getTimeZone("Asia/Tehran")
            val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss")
            for (pattern in patterns) {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }
                val parsed = runCatching { sdf.parse(trimmed)?.time }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
            return null
        }

        internal fun tgjuTableScaleForSlug(slug: String): Double = when (slug.trim().lowercase(Locale.US)) {
            "price_jpy" -> 0.001   // TGJU quotes Rials per 100 JPY -> /1000 = Toman per 1 JPY
            "price_iqd" -> 0.0001  // TGJU quotes Rials per 1000 IQD -> /10000 = Toman per 1 IQD
            "ons" -> 1.0           // Global ounce spot in USD
            else -> 0.1            // Standard TGJU Tehran quotes in Rials per 1 unit -> /10 = Toman
        }

        internal fun parseTgjuTablePayload(
            raw: String,
            slug: String = "",
            spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        ): List<AssetHistoryPoint> = runCatching {
            val root = JSONObject(raw)
            val dataArr = root.optJSONArray("data") ?: return emptyList()
            val sdfSlash = SimpleDateFormat("yyyy/MM/dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tehran")
            }
            val sdfDash = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tehran")
            }
            val unitScale = tgjuTableScaleForSlug(slug)

            val parsed = ArrayList<AssetHistoryPoint>(dataArr.length())
            for (i in 0 until dataArr.length()) {
                val row = dataArr.optJSONArray(i) ?: continue
                if (row.length() < 7) continue
                val closeRaw = parseNumeric(row.optString(3))
                    ?: parseNumeric(row.optString(0))
                    ?: continue
                if (closeRaw <= 0.0) continue

                val dateStr = row.optString(6).trim().substringBefore(" ")
                val parsedDate = runCatching {
                    if (dateStr.contains("/")) sdfSlash.parse(dateStr) else sdfDash.parse(dateStr)
                }.getOrNull() ?: continue

                // Set to afternoon Tehran close (17:00) for clean ordering
                val tsSec = (parsedDate.time / 1000L) + 17L * 3600L
                parsed.add(AssetHistoryPoint(tsSec, closeRaw * unitScale))
            }

            HistoryNormalizer.normalize(parsed, spikeThresholdPct = spikeThresholdPct)
        }.getOrDefault(emptyList())

        internal fun parseWallexQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, ExchangeQuote> = runCatching {
            val root = JSONObject(raw)
            val resultObj = root.optJSONObject("result") ?: return emptyMap()
            val markets = resultObj.optJSONObject("markets") ?: return emptyMap()
            val rootTs = parseQuoteTimestampMillis(resultObj, root)
            data class WallexSide(val price: Double, val change: Double, val high: Double?, val low: Double?, val timestampMillis: Long)
            val tmnPrices = mutableMapOf<String, WallexSide>()
            val usdtPrices = mutableMapOf<String, WallexSide>()

            val keys = markets.keys()
            while (keys.hasNext()) {
                val sym = keys.next()
                val item = markets.optJSONObject(sym) ?: continue
                val base = item.optString("baseAsset")
                val quote = item.optString("quoteAsset")
                val stats = item.optJSONObject("stats")
                val price = parseNumeric(item.optString("price"))
                    ?: stats?.optString("lastPrice")?.let { parseNumeric(it) }
                    ?: continue
                val change = item.optDouble("change_24h", Double.NaN).let {
                    if (it.isNaN()) stats?.optDouble("24h_ch", 0.0) ?: 0.0 else it
                }
                val high = stats?.optString("24h_highPrice")?.let { parseNumeric(it) }?.takeIf { it > 0.0 }
                val low = stats?.optString("24h_lowPrice")?.let { parseNumeric(it) }?.takeIf { it > 0.0 }
                val itemTs = parseQuoteTimestampMillis(item, stats).takeIf { it > 0L } ?: rootTs
                if (quote == "TMN" && price > 0) {
                    tmnPrices[base] = WallexSide(price, change, high, low, itemTs)
                } else if (quote == "USDT" && price > 0) {
                    usdtPrices[base] = WallexSide(price, change, high, low, itemTs)
                }
            }

            val allBases = tmnPrices.keys + usdtPrices.keys
            val result = mutableMapOf<String, ExchangeQuote>()
            for (base in allBases) {
                val tmn = tmnPrices[base]
                val usdt = usdtPrices[base]
                val ts = tmn?.timestampMillis?.takeIf { it > 0L }
                    ?: usdt?.timestampMillis?.takeIf { it > 0L }
                    ?: 0L
                result[base] = ExchangeQuote(
                    priceToman = tmn?.price,
                    priceUsd = usdt?.price,
                    changePct = tmn?.change ?: usdt?.change ?: 0.0,
                    highToman = tmn?.high,
                    lowToman = tmn?.low,
                    highUsd = usdt?.high,
                    lowUsd = usdt?.low,
                    timestampMillis = ts
                )
            }
            result
        }.getOrDefault(emptyMap())

        internal fun parseBitpinQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, ExchangeQuote> = runCatching {
            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: return emptyMap()
            val rootTs = parseQuoteTimestampMillis(root)
            data class BitpinSide(val price: Double, val change: Double, val timestampMillis: Long)
            val tmnPrices = mutableMapOf<String, BitpinSide>()
            val usdtPrices = mutableMapOf<String, BitpinSide>()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val base = item.optJSONObject("currency1")?.optString("code")
                    ?: item.optJSONObject("base_currency")?.optString("code")
                    ?: continue
                val quote = item.optJSONObject("currency2")?.optString("code")
                    ?: item.optJSONObject("quote_currency")?.optString("code")
                    ?: continue
                val priceInfo = item.optJSONObject("price_info")
                val price = parseNumeric(priceInfo?.optString("price"))
                    ?: parseNumeric(item.optString("price"))
                    ?: continue
                val change = priceInfo?.optDouble("change", 0.0) ?: 0.0
                val itemTs = parseQuoteTimestampMillis(priceInfo, item).takeIf { it > 0L } ?: rootTs
                if (quote == "IRT" && price > 0) {
                    tmnPrices[base] = BitpinSide(price, change, itemTs)
                } else if (quote == "USDT" && price > 0) {
                    usdtPrices[base] = BitpinSide(price, change, itemTs)
                }
            }

            val allBases = tmnPrices.keys + usdtPrices.keys
            val map = mutableMapOf<String, ExchangeQuote>()
            for (base in allBases) {
                val tmn = tmnPrices[base]
                val usdt = usdtPrices[base]
                val ts = tmn?.timestampMillis?.takeIf { it > 0L }
                    ?: usdt?.timestampMillis?.takeIf { it > 0L }
                    ?: 0L
                map[base] = ExchangeQuote(
                    priceToman = tmn?.price,
                    priceUsd = usdt?.price,
                    changePct = tmn?.change ?: usdt?.change ?: 0.0,
                    timestampMillis = ts
                )
            }
            map
        }.getOrDefault(emptyMap())

        internal fun parseBinanceQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, ExchangeQuote> = runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, ExchangeQuote>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sym = obj.optString("symbol")
                if (!sym.endsWith("USDT")) continue
                val base = sym.removeSuffix("USDT")
                val price = parseNumeric(obj.optString("lastPrice")) ?: continue
                val change = parseNumeric(obj.optString("priceChangePercent")) ?: 0.0
                val high = parseNumeric(obj.optString("highPrice"))?.takeIf { it > 0.0 }
                val low = parseNumeric(obj.optString("lowPrice"))?.takeIf { it > 0.0 }
                val closeTime = obj.optLong("closeTime", 0L).takeIf { it > 0L } ?: 0L
                if (price > 0) {
                    map[base] = ExchangeQuote(
                        priceToman = null,
                        priceUsd = price,
                        changePct = change,
                        highUsd = high,
                        lowUsd = low,
                        timestampMillis = closeTime
                    )
                }
            }
            map
        }.getOrDefault(emptyMap())

        internal fun parseNobitexOrderbookPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Pair<Double, Long>? = runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("status") != "ok") return null
            val lastRial = parseNumeric(obj.optString("lastTradePrice")) ?: return null
            val ts = obj.optLong("lastUpdate", 0L).takeIf { it > 0L } ?: 0L
            (lastRial / 10.0) to ts
        }.getOrNull()

        private fun parseQuoteTimestampMillis(vararg objects: JSONObject?): Long {
            val candidateKeys = listOf("updated_at", "updatedAt", "lastUpdate", "last_update", "timestamp", "ts", "time")
            for (obj in objects) {
                if (obj == null) continue
                for (key in candidateKeys) {
                    if (!obj.has(key) || obj.isNull(key)) continue
                    val rawVal = obj.opt(key) ?: continue
                    when (rawVal) {
                        is Number -> {
                            val num = rawVal.toLong()
                            if (num > 0L) {
                                return if (num < 10_000_000_000L) num * 1000L else num
                            }
                        }
                        is String -> {
                            val trimmed = rawVal.trim()
                            if (trimmed.isEmpty()) continue
                            val numeric = trimmed.toLongOrNull()
                            if (numeric != null && numeric > 0L) {
                                return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
                            }
                            FinancialApiService.parseIsoTimestampMillis(trimmed)?.let { return it }
                            parseTgjuTimestampMillis(trimmed)?.let { return it }
                        }
                    }
                }
            }
            return 0L
        }

        internal fun parseWallexUdfPayload(
            raw: String,
            spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        ): List<AssetHistoryPoint> = runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("s") != "ok") return emptyList()
            val tArr = obj.optJSONArray("t") ?: return emptyList()
            val cArr = obj.optJSONArray("c") ?: return emptyList()
            val count = minOf(tArr.length(), cArr.length())
            val list = ArrayList<AssetHistoryPoint>(count)
            for (i in 0 until count) {
                val ts = tArr.optLong(i)
                val close = parseNumeric(cArr.optString(i)) ?: cArr.optDouble(i, -1.0)
                if (ts > 0 && close > 0.0) {
                    list.add(AssetHistoryPoint(ts, close))
                }
            }
            HistoryNormalizer.normalize(list, spikeThresholdPct = spikeThresholdPct)
        }.getOrDefault(emptyList())

        internal fun parseNobitexUdfPayload(
            raw: String,
            spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        ): List<AssetHistoryPoint> = runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("s") != "ok") return emptyList()
            val tArr = obj.optJSONArray("t") ?: return emptyList()
            val cArr = obj.optJSONArray("c") ?: return emptyList()
            val count = minOf(tArr.length(), cArr.length())
            val list = ArrayList<AssetHistoryPoint>(count)
            for (i in 0 until count) {
                val ts = tArr.optLong(i)
                val closeRial = parseNumeric(cArr.optString(i)) ?: cArr.optDouble(i, -1.0)
                if (ts > 0 && closeRial > 0.0) {
                    list.add(AssetHistoryPoint(ts, closeRial / 10.0))
                }
            }
            HistoryNormalizer.normalize(list, spikeThresholdPct = spikeThresholdPct)
        }.getOrDefault(emptyList())

        internal fun parseBinanceKlinesPayload(
            raw: String,
            spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        ): List<AssetHistoryPoint> = runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<AssetHistoryPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONArray(i) ?: continue
                if (item.length() < 7) continue
                val tsSec = item.optLong(6) / 1000L
                val close = parseNumeric(item.optString(4)) ?: continue
                if (tsSec > 0 && close > 0.0) {
                    list.add(AssetHistoryPoint(tsSec, close))
                }
            }
            HistoryNormalizer.normalize(list, spikeThresholdPct = spikeThresholdPct)
        }.getOrDefault(emptyList())

        internal fun parseCoinGeckoHistoryPayload(
            raw: String,
            spikeThresholdPct: Double = MarketValidationConfig.HISTORY_SPIKE_THRESHOLD_PCT
        ): List<AssetHistoryPoint> = runCatching {
            val root = JSONObject(raw)
            val prices = root.optJSONArray("prices") ?: return emptyList()
            val list = ArrayList<AssetHistoryPoint>(prices.length())
            for (i in 0 until prices.length()) {
                val pair = prices.optJSONArray(i) ?: continue
                val tsSec = pair.optLong(0) / 1000L
                val price = pair.optDouble(1, -1.0)
                if (tsSec > 0 && price > 0.0) {
                    list.add(AssetHistoryPoint(tsSec, price))
                }
            }
            val normalized = HistoryNormalizer.normalize(list, spikeThresholdPct = spikeThresholdPct)
            if (normalized.size <= 120) {
                normalized
            } else {
                val step = maxOf(1, normalized.size / 120)
                val sampled = ArrayList<AssetHistoryPoint>(121)
                for (i in normalized.indices step step) {
                    sampled.add(normalized[i])
                }
                if (sampled.lastOrNull()?.timestampSec != normalized.last().timestampSec) {
                    sampled.add(normalized.last())
                }
                sampled
            }
        }.getOrDefault(emptyList())

        internal fun parseNumeric(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            val noHtml = raw.replace(Regex("<[^>]*>"), " ")
            val ascii = StringBuilder(noHtml.length)
            for (ch in noHtml) {
                ascii.append(
                    when (ch) {
                        in '۰'..'۹' -> ('0' + (ch - '۰'))
                        in '٠'..'٩' -> ('0' + (ch - '٠'))
                        '−', '–' -> '-'
                        ',', '٬', ' ', '\u00A0' -> continue
                        else -> ch
                    }
                )
            }
            val match = Regex("-?\\d+(?:\\.\\d+)?").find(ascii.toString()) ?: return null
            return match.value.toDoubleOrNull()
        }
    }
}
