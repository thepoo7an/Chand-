package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.TimeRange
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import ir.thepoo7an.chand.network.NetworkConfig.RequestProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@JsonClass(generateAdapter = true)
data class FrankfurterLatestResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class FrankfurterTimeSeriesResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    @Json(name = "start_date") val startDate: String = "",
    @Json(name = "end_date") val endDate: String = "",
    val rates: Map<String, Map<String, Double>> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class OpenErLatestResponse(
    val result: String = "",
    @Json(name = "base_code") val baseCode: String = "USD",
    @Json(name = "time_last_update_unix") val timeLastUpdateUnix: Long = 0L,
    val rates: Map<String, Double> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GoldSpotResponse(
    val name: String = "",
    val price: Double = 0.0,
    val symbol: String = "XAU",
    val updatedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class CurrencyCdnResponse(
    val date: String = "",
    val usd: Map<String, Double> = emptyMap()
)

interface FinancialRetrofitApi {
    @GET
    suspend fun getFrankfurterLatest(@Url url: String): FrankfurterLatestResponse

    @GET
    suspend fun getFrankfurterSeries(
        @Url url: String,
        @Query("from") from: String = "USD",
        @Query("to") to: String
    ): FrankfurterTimeSeriesResponse

    @GET
    suspend fun getOpenExchangeRatesLatest(@Url url: String): OpenErLatestResponse

    @GET
    suspend fun getGoldSpotXau(@Url url: String): GoldSpotResponse

    @GET
    suspend fun getCurrencyCdnSnapshot(@Url url: String): CurrencyCdnResponse
}

/**
 * Reliable multi-provider Retrofit financial data service for real-time and long-term
 * historical fiat exchange rates & gold spot prices (no API key required).
 *
 * Provider priority & cross-validation order:
 * 1. Frankfurter (ECB official rates & daily timeseries) - primary for ECB currencies
 * 2. Fawazahmed0 Currency-API (150+ currencies + XAU precious metals snapshots)
 * 3. Open ExchangeRate-API (160+ live global FX rates)
 * 4. Gold-API (real-time XAU/USD gold ounce spot)
 */
class FinancialApiService(
    client: OkHttpClient = defaultFinancialClient()
) {
    private val moshi: Moshi = Moshi.Builder().build()

    private val quoteApi: FinancialRetrofitApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.app/")
            .client(NetworkConfig.clientFor(RequestProfile.FAST_QUOTE, client))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FinancialRetrofitApi::class.java)
    }

    private val historyApi: FinancialRetrofitApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.app/")
            .client(NetworkConfig.clientFor(RequestProfile.HISTORY, client))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FinancialRetrofitApi::class.java)
    }

    private val liveUsdDeduplicator = InFlightRequestDeduplicator<String, SourcedFxSnapshot>()
    private val liveGoldSpotDeduplicator = InFlightRequestDeduplicator<String, MarketPriceMapper.PriceCandidate?>()
    private val cdnResponseDeduplicator = InFlightRequestDeduplicator<String, CurrencyCdnResponse?>()
    private val multiFxHistoryDeduplicator = InFlightRequestDeduplicator<String, Map<String, List<Pair<Long, Double>>>>()
    private val currencyHistoryDeduplicator = InFlightRequestDeduplicator<String, Pair<List<Pair<Long, Double>>, String>>()
    private val goldHistoryDeduplicator = InFlightRequestDeduplicator<String, List<AssetHistoryPoint>>()

    private suspend fun <T> safeRetry(
        profile: RequestProfile,
        block: suspend () -> T
    ): T? = try {
        NetworkConfig.retrySuspend(
            maxAttempts = profile.defaultMaxAttempts,
            baseDelayMillis = profile.baseDelayMillis,
            maxDelayMillis = profile.maxDelayMillis,
            block = block
        )
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }

    data class SourcedFxSnapshot(
        val candidatesBySymbol: Map<String, List<MarketPriceMapper.PriceCandidate>>,
        val consensusRates: Map<String, MarketPriceMapper.ConsensusResult>,
        val goldSpotXauCandidates: List<MarketPriceMapper.PriceCandidate>
    )

    suspend fun fetchLiveUsdRateSnapshot(nowMillis: Long = System.currentTimeMillis()): SourcedFxSnapshot =
        withContext(Dispatchers.IO) {
            liveUsdDeduplicator.execute("live") {
                val ep = LiveConfigRegistry.currentConfig().endpoints
                val openErUrl = ep.openErLatestUrl.ifBlank { DEFAULT_OPEN_ER_URL }
                val frankfurterUrls = ep.frankfurterLatestUrls.ifEmpty { DEFAULT_FRANKFURTER_LATEST_URLS }
                coroutineScope {
                    val openErDeferred = async {
                        safeRetry(RequestProfile.FAST_QUOTE) { quoteApi.getOpenExchangeRatesLatest(openErUrl) }
                    }
                    val cdnDeferred = async { fetchCdnResponse("latest") }
                    val frankfurterDeferred = async {
                        var res: FrankfurterLatestResponse? = null
                        for (url in frankfurterUrls) {
                            res = safeRetry(RequestProfile.FAST_QUOTE) { quoteApi.getFrankfurterLatest(url) }
                            if (res != null && res.rates.isNotEmpty()) break
                        }
                        res
                    }

                    val openEr = openErDeferred.await()
                    val cdn = cdnDeferred.await()
                    val frankfurter = frankfurterDeferred.await()

                val candidates = LinkedHashMap<String, MutableList<MarketPriceMapper.PriceCandidate>>()

                if (frankfurter != null && frankfurter.rates.isNotEmpty()) {
                    val ts = parseDailyUtcTimestampMillis(frankfurter.date, hourUtc = 15, nowMillis = nowMillis)
                    frankfurter.rates.forEach { (k, v) ->
                        if (v > 0.0 && v.isFinite()) {
                            candidates.getOrPut(k.uppercase(Locale.US)) { mutableListOf() }.add(
                                MarketPriceMapper.PriceCandidate(
                                    sourceName = "Frankfurter",
                                    price = v,
                                    isPrimary = true,
                                    timestampMillis = ts
                                )
                            )
                        }
                    }
                }

                if (cdn != null && cdn.usd.isNotEmpty()) {
                    val ts = parseDailyUtcTimestampMillis(cdn.date, hourUtc = 12, nowMillis = nowMillis)
                    cdn.usd.forEach { (k, v) ->
                        if (v > 0.0 && v.isFinite()) {
                            candidates.getOrPut(k.uppercase(Locale.US)) { mutableListOf() }.add(
                                MarketPriceMapper.PriceCandidate(
                                    sourceName = "CurrencyAPI",
                                    price = v,
                                    isPrimary = frankfurter == null,
                                    timestampMillis = ts
                                )
                            )
                        }
                    }
                }

                if (openEr != null && openEr.rates.isNotEmpty()) {
                    val ts = if (openEr.timeLastUpdateUnix > 0L) openEr.timeLastUpdateUnix * 1000L else nowMillis
                    openEr.rates.forEach { (k, v) ->
                        if (v > 0.0 && v.isFinite()) {
                            candidates.getOrPut(k.uppercase(Locale.US)) { mutableListOf() }.add(
                                MarketPriceMapper.PriceCandidate(
                                    sourceName = "OpenER",
                                    price = v,
                                    isPrimary = false,
                                    timestampMillis = ts
                                )
                            )
                        }
                    }
                }

                val consensusMap = LinkedHashMap<String, MarketPriceMapper.ConsensusResult>()
                candidates.forEach { (sym, list) ->
                    if (sym != "XAU") {
                        MarketPriceMapper.validateCrossSourcePrices(
                            candidates = list,
                            thresholdPct = MarketValidationConfig.effectiveFiatDisagreementThresholdPct(),
                            nowMillis = nowMillis
                        )?.let { consensusMap[sym] = it }
                    }
                }

                val xauCandidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
                val xauPerUsd = cdn?.usd?.get("xau") ?: cdn?.usd?.get("XAU")
                if (xauPerUsd != null && xauPerUsd > 0.0) {
                    val usdPerOz = 1.0 / xauPerUsd
                    if (usdPerOz in 1000.0..10_000.0) {
                        val cdnTs = parseDailyUtcTimestampMillis(cdn?.date.orEmpty(), hourUtc = 12, nowMillis = nowMillis)
                        xauCandidates.add(
                            MarketPriceMapper.PriceCandidate(
                                sourceName = "CurrencyAPI",
                                price = usdPerOz,
                                isPrimary = false,
                                timestampMillis = cdnTs
                            )
                        )
                    }
                }

                SourcedFxSnapshot(
                    candidatesBySymbol = candidates,
                    consensusRates = consensusMap,
                    goldSpotXauCandidates = xauCandidates
                )
            }
            }
        }

    /**
     * Fetches latest USD-based exchange rates using cross-source consensus validation.
     */
    suspend fun fetchLiveUsdRates(): Map<String, Double> =
        fetchLiveUsdRateSnapshot().consensusRates.mapValues { it.value.price }

    /**
     * Fetches real-time Gold spot price candidate (USD per Troy Ounce) from Gold-API.
     */
    suspend fun fetchLiveGoldSpotCandidate(nowMillis: Long = System.currentTimeMillis()): MarketPriceMapper.PriceCandidate? =
        withContext(Dispatchers.IO) {
            liveGoldSpotDeduplicator.execute("xau") {
                val goldUrl = LiveConfigRegistry.currentConfig().endpoints.goldSpotXauUrl
                    .ifBlank { DEFAULT_GOLD_SPOT_URL }
                val resp = safeRetry(RequestProfile.FAST_QUOTE) { quoteApi.getGoldSpotXau(goldUrl) }
                if (resp != null && resp.price in 1000.0..10_000.0) {
                    val ts = parseIsoTimestampMillis(resp.updatedAt) ?: nowMillis
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "GoldAPI",
                        price = resp.price,
                        isPrimary = true,
                        timestampMillis = ts
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Fetches real-time Gold spot price (USD per Troy Ounce) from Gold-API with CDN fallback.
     */
    suspend fun fetchLiveGoldSpotUsd(): Double? = withContext(Dispatchers.IO) {
        fetchLiveGoldSpotCandidate()?.price ?: run {
            val cdn = fetchCdnRates("latest")
            val xauPerUsd = cdn["xau"] ?: cdn["XAU"]
            if (xauPerUsd != null && xauPerUsd > 0.0) {
                val usdPerOz = 1.0 / xauPerUsd
                if (usdPerOz in 1000.0..10_000.0) return@withContext usdPerOz
            }
            null
        }
    }

    /**
     * Fetches long-term historical FX cross rates (units of [targetCurrency] per 1 USD)
     * using Frankfurter ECB timeseries first, with Fawazahmed0 Currency-API daily snapshots
     * as full coverage for non-ECB currencies (AED, KWD, SAR, IQD, etc.).
     */
    suspend fun fetchUsdToCurrencyHistory(
        targetCurrency: String,
        fromSec: Long,
        toSec: Long,
        range: TimeRange
    ): Pair<List<Pair<Long, Double>>, String> = withContext(Dispatchers.IO) {
        val upper = targetCurrency.uppercase(Locale.US)
        val dedupeKey = "$upper|${range.name}|${fromSec / 60L}|${toSec / 60L}"
        currencyHistoryDeduplicator.execute(dedupeKey) {
            if (upper in ECB_SUPPORTED_CURRENCIES) {
                val frankfurterPoints = fetchFrankfurterRange(upper, fromSec, toSec, range)
                if (frankfurterPoints.size >= 3) {
                    return@execute frankfurterPoints to "Frankfurter ECB"
                }
            }

            val cdnPoints = fetchCdnHistoricalSeries(
                currencyKey = upper.lowercase(Locale.US),
                fromSec = fromSec,
                toSec = toSec,
                range = range,
                invertRate = false,
                category = AssetCategory.FIAT
            )
            if (cdnPoints.size >= 3) {
                return@execute cdnPoints to "CurrencyAPI"
            }

            emptyList<Pair<Long, Double>>() to ""
        }
    }

    /**
     * Fetches historical USD-per-Troy-Ounce Gold (`XAU`) points over 7D, 1M, or 1Y
     * from Fawazahmed0 Currency-API precious metals archive.
     */
    suspend fun fetchGoldUsdHistory(
        fromSec: Long,
        toSec: Long,
        range: TimeRange,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(AssetCategory.GOLD, range)
    ): List<AssetHistoryPoint> = withContext(Dispatchers.IO) {
        val dedupeKey = "${range.name}|${fromSec / 60L}|${toSec / 60L}|$spikeThresholdPct"
        goldHistoryDeduplicator.execute(dedupeKey) {
            val raw = fetchCdnHistoricalSeries(
                currencyKey = "xau",
                fromSec = fromSec,
                toSec = toSec,
                range = range,
                invertRate = true,
                category = AssetCategory.GOLD,
                spikeThresholdPct = spikeThresholdPct
            )
            val pts = raw.filter { it.second in 1000.0..10_000.0 }
                .map { AssetHistoryPoint(it.first, it.second) }
            HistoryNormalizer.normalize(
                points = pts,
                category = AssetCategory.GOLD,
                range = range,
                spikeThresholdPct = spikeThresholdPct
            )
        }
    }

    /**
     * Fetches a single batch multi-currency FX history series from Frankfurter ECB
     * for supported symbols, returning map of SYMBOL -> chronological list of (timestampSec, unitsPerUsd).
     */
    suspend fun fetchMultiCurrencyRange(
        symbols: List<String>,
        fromSec: Long,
        toSec: Long,
        range: TimeRange = TimeRange.D7
    ): Map<String, List<Pair<Long, Double>>> = withContext(Dispatchers.IO) {
        val ecbTargets = symbols
            .map { it.uppercase(Locale.US) }
            .filter { it in ECB_SUPPORTED_CURRENCIES && it != "USD" }
            .distinct()
        if (ecbTargets.isEmpty()) return@withContext emptyMap()

        val dedupeKey = "${ecbTargets.sorted().joinToString(",")}|${fromSec / 60L}|${toSec / 60L}"
        multiFxHistoryDeduplicator.execute(dedupeKey) {
            val toParam = ecbTargets.joinToString(",")
            val sdf = utcDateFormat()
            val startStr = sdf.format(Date(fromSec * 1000L))
            val endStr = sdf.format(Date(toSec * 1000L))

            val seriesTemplates = LiveConfigRegistry.currentConfig().endpoints.frankfurterSeriesUrlTemplates
                .ifEmpty { DEFAULT_FRANKFURTER_SERIES_TEMPLATES }
            var resp: FrankfurterTimeSeriesResponse? = null
            for (tmpl in seriesTemplates) {
                val url = tmpl.replace("{start}", startStr).replace("{end}", endStr)
                resp = safeRetry(RequestProfile.HISTORY) {
                    historyApi.getFrankfurterSeries(url, "USD", toParam)
                }
                if (resp != null && resp.rates.isNotEmpty()) break
            }
            if (resp == null) return@execute emptyMap()

            val result = LinkedHashMap<String, MutableList<Pair<Long, Double>>>()
            resp.rates.entries
                .sortedBy { it.key }
                .forEach { (dateKey, rateMap) ->
                    val ts = (runCatching { sdf.parse(dateKey)?.time }.getOrNull() ?: return@forEach) / 1000L
                    rateMap.forEach { (sym, valRate) ->
                        if (valRate > 0.0) {
                            result.getOrPut(sym.uppercase(Locale.US)) { mutableListOf() }
                                .add(ts to valRate)
                        }
                    }
                }
            result.mapValues { (_, list) ->
                HistoryNormalizer.normalizePairs(list, category = AssetCategory.FIAT, range = range)
            }
        }
    }

    private suspend fun fetchFrankfurterRange(
        targetSymbol: String,
        fromSec: Long,
        toSec: Long,
        range: TimeRange = TimeRange.M1
    ): List<Pair<Long, Double>> {
        val sdf = utcDateFormat()
        val startStr = sdf.format(Date(fromSec * 1000L))
        val endStr = sdf.format(Date(toSec * 1000L))

        val seriesTemplates = LiveConfigRegistry.currentConfig().endpoints.frankfurterSeriesUrlTemplates
            .ifEmpty { DEFAULT_FRANKFURTER_SERIES_TEMPLATES }
        var resp: FrankfurterTimeSeriesResponse? = null
        for (tmpl in seriesTemplates) {
            val url = tmpl.replace("{start}", startStr).replace("{end}", endStr)
            resp = safeRetry(RequestProfile.HISTORY) {
                historyApi.getFrankfurterSeries(url, "USD", targetSymbol)
            }
            if (resp != null && resp.rates.isNotEmpty()) break
        }
        if (resp == null) return emptyList()

        val rawPairs = resp.rates.entries
            .sortedBy { it.key }
            .mapNotNull { (dateKey, rateMap) ->
                val valRate = rateMap[targetSymbol] ?: return@mapNotNull null
                if (valRate <= 0.0) return@mapNotNull null
                val ts = (runCatching { sdf.parse(dateKey)?.time }.getOrNull() ?: return@mapNotNull null) / 1000L
                ts to valRate
            }
        return HistoryNormalizer.normalizePairs(rawPairs, category = AssetCategory.FIAT, range = range)
    }

    private suspend fun fetchCdnHistoricalSeries(
        currencyKey: String,
        fromSec: Long,
        toSec: Long,
        range: TimeRange,
        invertRate: Boolean,
        category: AssetCategory = AssetCategory.FIAT,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<Pair<Long, Double>> = coroutineScope {
        val sampleSeconds = buildSampleTimestamps(fromSec, toSec, range)
        val sdf = utcDateFormat()
        val tasks = sampleSeconds.map { tsSec ->
            val dateStr = sdf.format(Date(tsSec * 1000L))
            async {
                val rates = fetchCdnRates(dateStr)
                val raw = rates[currencyKey] ?: rates[currencyKey.uppercase(Locale.US)]
                if (raw != null && raw > 0.0) {
                    val value = if (invertRate) 1.0 / raw else raw
                    tsSec to value
                } else {
                    null
                }
            }
        }
        HistoryNormalizer.normalizePairs(
            pairs = tasks.awaitAll().filterNotNull(),
            category = category,
            range = range,
            spikeThresholdPct = spikeThresholdPct
        )
    }

    private suspend fun fetchCdnResponse(dateTag: String): CurrencyCdnResponse? =
        cdnResponseDeduplicator.execute(dateTag) {
            val profile = if (dateTag == "latest") RequestProfile.FAST_QUOTE else RequestProfile.HISTORY
            val clientApi = if (dateTag == "latest") quoteApi else historyApi
            val templates = LiveConfigRegistry.currentConfig().endpoints.currencyCdnUrlTemplates
                .ifEmpty { DEFAULT_CURRENCY_CDN_TEMPLATES }
            for (tmpl in templates) {
                val url = tmpl.replace("{date}", dateTag)
                val resp = safeRetry(profile) { clientApi.getCurrencyCdnSnapshot(url) }
                if (resp != null && resp.usd.isNotEmpty()) return@execute resp
            }
            null
        }

    private suspend fun fetchCdnRates(dateTag: String): Map<String, Double> =
        fetchCdnResponse(dateTag)?.usd ?: emptyMap()

    private fun buildSampleTimestamps(fromSec: Long, toSec: Long, range: TimeRange): List<Long> {
        val daySec = 86_400L
        val safeEnd = toSec - daySec // CDN snapshots exist up to yesterday UTC + 'latest'
        val stepDays = when (range) {
            TimeRange.H24 -> 1L
            TimeRange.D7 -> 1L
            TimeRange.M1 -> 2L
            TimeRange.Y1 -> 14L
        }
        val list = ArrayList<Long>()
        var cursor = fromSec
        while (cursor <= safeEnd) {
            list.add(cursor)
            cursor += stepDays * daySec
        }
        if (list.isEmpty() || safeEnd - list.last() > daySec) {
            if (safeEnd > fromSec) list.add(safeEnd)
        }
        return list
    }

    private fun utcDateFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        internal const val DEFAULT_OPEN_ER_URL = "https://open.er-api.com/v6/latest/USD"
        internal const val DEFAULT_GOLD_SPOT_URL = "https://api.gold-api.com/price/XAU"
        internal val DEFAULT_FRANKFURTER_LATEST_URLS = listOf(
            "https://api.frankfurter.dev/v1/latest?base=USD",
            "https://api.frankfurter.app/latest?from=USD"
        )
        internal val DEFAULT_FRANKFURTER_SERIES_TEMPLATES = listOf(
            "https://api.frankfurter.dev/v1/{start}..{end}",
            "https://api.frankfurter.app/{start}..{end}"
        )
        internal val DEFAULT_CURRENCY_CDN_TEMPLATES = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{date}/v1/currencies/usd.json",
            "https://{date}.currency-api.pages.dev/v1/currencies/usd.json"
        )

        private val ECB_SUPPORTED_CURRENCIES = setOf(
            "EUR", "GBP", "TRY", "CAD", "CHF", "CNY", "AUD", "JPY",
            "BGN", "BRL", "CZK", "DKK", "HKD", "HUF", "IDR", "ILS",
            "INR", "ISK", "KRW", "MXN", "MYR", "NOK", "NZD", "PHP",
            "PLN", "RON", "SEK", "SGD", "THB", "ZAR"
        )

        internal fun parseDailyUtcTimestampMillis(
            dateStr: String,
            hourUtc: Int,
            nowMillis: Long
        ): Long {
            val clean = dateStr.trim().take(10)
            if (clean.length < 10) return nowMillis
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedMidnight = runCatching { sdf.parse(clean)?.time }.getOrNull() ?: return nowMillis
            val todayMidnight = runCatching {
                sdf.parse(sdf.format(Date(nowMillis)))?.time
            }.getOrNull()
            return if (todayMidnight != null && parsedMidnight >= todayMidnight) {
                nowMillis
            } else {
                minOf(parsedMidnight + hourUtc.toLong() * 3600_000L, nowMillis)
            }
        }

        internal fun parseIsoTimestampMillis(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val normalized = trimmed
                .replace(Regex("\\.\\d+"), "")
                .replace("Z", "+0000")
                .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            val patterns = listOf("yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss")
            for (pattern in patterns) {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = runCatching { sdf.parse(normalized)?.time }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
            return null
        }

        fun defaultFinancialClient(): OkHttpClient = NetworkConfig.sharedClient
    }
}
