package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.TimeRange
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
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

    @GET("https://open.er-api.com/v6/latest/USD")
    suspend fun getOpenExchangeRatesLatest(): OpenErLatestResponse

    @GET("https://api.gold-api.com/price/XAU")
    suspend fun getGoldSpotXau(): GoldSpotResponse

    @GET("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{date}/v1/currencies/usd.json")
    suspend fun getCurrencyCdnSnapshot(@Path("date") date: String): CurrencyCdnResponse

    @GET
    suspend fun getCurrencyCdnFallback(@Url url: String): CurrencyCdnResponse
}

/**
 * Reliable multi-provider Retrofit financial data service for real-time and long-term
 * historical fiat exchange rates & gold spot prices (no API key required).
 *
 * Providers:
 * 1. Frankfurter (ECB official rates & daily timeseries)
 * 2. Fawazahmed0 Currency-API (150+ currencies + XAU precious metals historical snapshots)
 * 3. Gold-API (real-time XAU/USD gold ounce spot)
 * 4. Open ExchangeRate-API (160+ live global FX rates)
 */
class FinancialApiService(
    client: OkHttpClient = defaultFinancialClient()
) {
    private val moshi: Moshi = Moshi.Builder().build()

    private val api: FinancialRetrofitApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.app/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FinancialRetrofitApi::class.java)
    }

    /**
     * Fetches latest USD-based exchange rates across multiple reliable FX providers.
     */
    suspend fun fetchLiveUsdRates(): Map<String, Double> = withContext(Dispatchers.IO) {
        coroutineScope {
            val openErDeferred = async { runCatching { api.getOpenExchangeRatesLatest().rates }.getOrDefault(emptyMap()) }
            val cdnDeferred = async { fetchCdnRates("latest") }
            val frankfurterDeferred = async {
                runCatching { api.getFrankfurterLatest("https://api.frankfurter.dev/v1/latest?base=USD").rates }
                    .getOrElse {
                        runCatching { api.getFrankfurterLatest("https://api.frankfurter.app/latest?from=USD").rates }
                            .getOrDefault(emptyMap())
                    }
            }

            val openEr = openErDeferred.await()
            val cdn = cdnDeferred.await()
            val frankfurter = frankfurterDeferred.await()

            val merged = LinkedHashMap<String, Double>()
            frankfurter.forEach { (k, v) -> if (v > 0.0) merged[k.uppercase(Locale.US)] = v }
            cdn.forEach { (k, v) -> if (v > 0.0) merged[k.uppercase(Locale.US)] = v }
            openEr.forEach { (k, v) -> if (v > 0.0) merged[k.uppercase(Locale.US)] = v }
            merged
        }
    }

    /**
     * Fetches real-time Gold spot price (USD per Troy Ounce) from Gold-API with CDN fallback.
     */
    suspend fun fetchLiveGoldSpotUsd(): Double? = withContext(Dispatchers.IO) {
        val spot = runCatching { api.getGoldSpotXau().price }.getOrNull()
        if (spot != null && spot in 1000.0..10_000.0) {
            return@withContext spot
        }
        val cdn = fetchCdnRates("latest")
        val xauPerUsd = cdn["xau"] ?: cdn["XAU"]
        if (xauPerUsd != null && xauPerUsd > 0.0) {
            val usdPerOz = 1.0 / xauPerUsd
            if (usdPerOz in 1000.0..10_000.0) return@withContext usdPerOz
        }
        null
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
        if (upper in ECB_SUPPORTED_CURRENCIES) {
            val frankfurterPoints = fetchFrankfurterRange(upper, fromSec, toSec)
            if (frankfurterPoints.size >= 3) {
                return@withContext frankfurterPoints to "Frankfurter ECB"
            }
        }

        val cdnPoints = fetchCdnHistoricalSeries(
            currencyKey = upper.lowercase(Locale.US),
            fromSec = fromSec,
            toSec = toSec,
            range = range,
            invertRate = false
        )
        if (cdnPoints.size >= 3) {
            return@withContext cdnPoints to "CurrencyAPI"
        }

        emptyList<Pair<Long, Double>>() to ""
    }

    /**
     * Fetches historical USD-per-Troy-Ounce Gold (`XAU`) points over 7D, 1M, or 1Y
     * from Fawazahmed0 Currency-API precious metals archive.
     */
    suspend fun fetchGoldUsdHistory(
        fromSec: Long,
        toSec: Long,
        range: TimeRange
    ): List<AssetHistoryPoint> = withContext(Dispatchers.IO) {
        val raw = fetchCdnHistoricalSeries(
            currencyKey = "xau",
            fromSec = fromSec,
            toSec = toSec,
            range = range,
            invertRate = true
        )
        raw.filter { it.second in 1000.0..10_000.0 }
            .map { AssetHistoryPoint(it.first, it.second) }
    }

    /**
     * Fetches a single batch multi-currency FX history series from Frankfurter ECB
     * for supported symbols, returning map of SYMBOL -> chronological list of (timestampSec, unitsPerUsd).
     */
    suspend fun fetchMultiCurrencyRange(
        symbols: List<String>,
        fromSec: Long,
        toSec: Long
    ): Map<String, List<Pair<Long, Double>>> = withContext(Dispatchers.IO) {
        val ecbTargets = symbols
            .map { it.uppercase(Locale.US) }
            .filter { it in ECB_SUPPORTED_CURRENCIES && it != "USD" }
            .distinct()
        if (ecbTargets.isEmpty()) return@withContext emptyMap()

        val toParam = ecbTargets.joinToString(",")
        val sdf = utcDateFormat()
        val startStr = sdf.format(Date(fromSec * 1000L))
        val endStr = sdf.format(Date(toSec * 1000L))

        val resp = runCatching {
            api.getFrankfurterSeries("https://api.frankfurter.dev/v1/$startStr..$endStr", "USD", toParam)
        }.getOrElse {
            runCatching {
                api.getFrankfurterSeries("https://api.frankfurter.app/$startStr..$endStr", "USD", toParam)
            }.getOrNull()
        } ?: return@withContext emptyMap()

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
        result
    }

    private suspend fun fetchFrankfurterRange(
        targetSymbol: String,
        fromSec: Long,
        toSec: Long
    ): List<Pair<Long, Double>> {
        val sdf = utcDateFormat()
        val startStr = sdf.format(Date(fromSec * 1000L))
        val endStr = sdf.format(Date(toSec * 1000L))

        val resp = runCatching {
            api.getFrankfurterSeries("https://api.frankfurter.dev/v1/$startStr..$endStr", "USD", targetSymbol)
        }.getOrElse {
            runCatching {
                api.getFrankfurterSeries("https://api.frankfurter.app/$startStr..$endStr", "USD", targetSymbol)
            }.getOrNull()
        } ?: return emptyList()

        return resp.rates.entries
            .sortedBy { it.key }
            .mapNotNull { (dateKey, rateMap) ->
                val valRate = rateMap[targetSymbol] ?: return@mapNotNull null
                if (valRate <= 0.0) return@mapNotNull null
                val ts = (runCatching { sdf.parse(dateKey)?.time }.getOrNull() ?: return@mapNotNull null) / 1000L
                ts to valRate
            }
    }

    private suspend fun fetchCdnHistoricalSeries(
        currencyKey: String,
        fromSec: Long,
        toSec: Long,
        range: TimeRange,
        invertRate: Boolean
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
        tasks.awaitAll()
            .filterNotNull()
            .sortedBy { it.first }
            .distinctBy { it.first }
    }

    private suspend fun fetchCdnRates(dateTag: String): Map<String, Double> {
        val primary = runCatching { api.getCurrencyCdnSnapshot(dateTag).usd }.getOrNull()
        if (!primary.isNullOrEmpty()) return primary
        return runCatching {
            api.getCurrencyCdnFallback("https://$dateTag.currency-api.pages.dev/v1/currencies/usd.json").usd
        }.getOrDefault(emptyMap())
    }

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
        private val ECB_SUPPORTED_CURRENCIES = setOf(
            "EUR", "GBP", "TRY", "CAD", "CHF", "CNY", "AUD", "JPY",
            "BGN", "BRL", "CZK", "DKK", "HKD", "HUF", "IDR", "ILS",
            "INR", "ISK", "KRW", "MXN", "MYR", "NOK", "NZD", "PHP",
            "PLN", "RON", "SEK", "SGD", "THB", "ZAR"
        )

        fun defaultFinancialClient(): OkHttpClient = NetworkConfig.sharedClient
    }
}
