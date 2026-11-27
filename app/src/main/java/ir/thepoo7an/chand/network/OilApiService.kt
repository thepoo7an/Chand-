package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

/**
 * Global crude oil market data service supporting Brent Crude (primary) and WTI Crude (secondary)
 * strictly in USD per barrel (`USD/barrel`).
 *
 * Provider evaluation & selection:
 * 1. **Yahoo Finance v8 Chart (`BZ=F`, `CL=F`, `query1`/`query2`)**: Keyless HTTPS JSON with exchange
 *    market timestamps (`regularMarketTime`), intraday/daily/yearly candles, and explicit `"USD"` currency.
 * 2. **CNBC Quote REST (`@LCO.1`, `@CL.1`)**: Keyless batch HTTPS JSON quote endpoint with ISO-8601
 *    timestamps, explicit `"USD"` currency, `change_pct`, `change`, `high`, and `low`.
 * 3. **U.S. EIA API v2 (`RBRTE`, `RWTC`) — Optional official settlement feed**: Activated only when an
 *    operator supplies `EIA_API_KEY` (via environment / Secrets without hardcoding in source) or a custom
 *    `oilEiaSpotUrl` via live config. Provides official `"dollars per barrel"` daily FOB settlements.
 */
class OilApiService(
    private val client: OkHttpClient = NetworkConfig.sharedClient,
    private val eiaApiKeyProvider: () -> String = { resolveConfiguredEiaApiKey() }
) {
    data class OilQuoteSnapshot(
        val assetId: String,
        val sourceName: String,
        val priceUsd: Double?,
        val currency: String = "",
        val unit: String = UNIT_USD_PER_BARREL,
        val change24hPct: Double? = null,
        val change24hUsd: Double? = null,
        val high24hUsd: Double? = null,
        val low24hUsd: Double? = null,
        val sourceTimestampMillis: Long = 0L,
        val sparklineUsd: List<Double> = emptyList(),
        val isPrimary: Boolean = false,
        val failureReason: CandidateExclusionReason? = null
    )

    data class OilMarketBatch(
        val quotesByProvider: Map<String, Map<String, OilQuoteSnapshot>>,
        val failedProviders: Set<String>,
        val configuredProviders: List<String>
    )

    private val liveDeduplicator = InFlightRequestDeduplicator<String, OilMarketBatch>()
    private val historyDeduplicator = InFlightRequestDeduplicator<String, Pair<List<AssetHistoryPoint>, String>>()

    suspend fun fetchLiveOilQuotes(nowMillis: Long = System.currentTimeMillis()): OilMarketBatch =
        withContext(Dispatchers.IO) {
            val ep = LiveConfigRegistry.currentConfig().endpoints
            val eiaKey = eiaApiKeyProvider().trim()
            val hasEia = eiaKey.isNotBlank() || ep.oilEiaSpotUrl.isNotBlank()
            val dedupeKey = "${ep.oilCnbcQuotesUrl}|${ep.oilYahooChartUrlTemplates.joinToString(",")}|$hasEia"

            liveDeduplicator.execute(dedupeKey) {
                coroutineScope {
                    val cnbcDeferred = async { fetchCnbcOilQuotes(ep.oilCnbcQuotesUrl, nowMillis) }
                    val yahooBrentDeferred = async {
                        fetchYahooOilQuote("BRENT", "BZ=F", ep.oilYahooChartUrlTemplates, nowMillis)
                    }
                    val yahooWtiDeferred = async {
                        fetchYahooOilQuote("WTI", "CL=F", ep.oilYahooChartUrlTemplates, nowMillis)
                    }
                    val eiaDeferred = async {
                        if (hasEia) fetchEiaSpotQuotes(ep.oilEiaSpotUrl, eiaKey, nowMillis) else null
                    }

                    val cnbcResult = cnbcDeferred.await()
                    val yahooBrent = yahooBrentDeferred.await()
                    val yahooWti = yahooWtiDeferred.await()
                    val eiaResult = eiaDeferred.await()

                    val quotesByProvider = LinkedHashMap<String, Map<String, OilQuoteSnapshot>>()
                    val failedProviders = LinkedHashSet<String>()
                    val configuredProviders = mutableListOf(SOURCE_YAHOO, SOURCE_CNBC)

                    val yahooMap = LinkedHashMap<String, OilQuoteSnapshot>()
                    yahooBrent?.let { yahooMap["BRENT"] = it }
                    yahooWti?.let { yahooMap["WTI"] = it }
                    if (yahooMap.isEmpty()) {
                        failedProviders.add(SOURCE_YAHOO)
                    } else {
                        quotesByProvider[SOURCE_YAHOO] = yahooMap
                    }

                    if (cnbcResult == null) {
                        failedProviders.add(SOURCE_CNBC)
                    } else {
                        quotesByProvider[SOURCE_CNBC] = cnbcResult
                    }

                    if (hasEia) {
                        configuredProviders.add(SOURCE_EIA)
                        if (eiaResult == null) {
                            failedProviders.add(SOURCE_EIA)
                        } else {
                            quotesByProvider[SOURCE_EIA] = eiaResult
                        }
                    }

                    OilMarketBatch(
                        quotesByProvider = quotesByProvider,
                        failedProviders = failedProviders,
                        configuredProviders = configuredProviders
                    )
                }
            }
        }

    suspend fun fetchOilHistory(
        assetId: String,
        range: TimeRange,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<List<AssetHistoryPoint>, String> = withContext(Dispatchers.IO) {
        val yahooSymbol = when (assetId) {
            "BRENT" -> "BZ=F"
            "WTI" -> "CL=F"
            else -> return@withContext emptyList<AssetHistoryPoint>() to ""
        }
        val (yahooRange, interval) = when (range) {
            TimeRange.H24 -> "2d" to "30m"
            TimeRange.D7 -> "7d" to "1h"
            TimeRange.M1 -> "1mo" to "1d"
            TimeRange.Y1 -> "1y" to "1d"
        }
        val ep = LiveConfigRegistry.currentConfig().endpoints
        val dedupeKey = "$assetId|${range.name}|${ep.oilYahooChartUrlTemplates.joinToString(",")}"
        historyDeduplicator.execute(dedupeKey) {
            val templates = ep.oilYahooChartUrlTemplates.ifEmpty { DEFAULT_YAHOO_CHART_TEMPLATES }
            for (tmpl in templates) {
                val url = tmpl
                    .replace("{symbol}", yahooSymbol)
                    .replace("{range}", yahooRange)
                    .replace("{interval}", interval)
                val outcome = NetworkConfig.executeGetWithRetry(
                    client = client,
                    url = url,
                    profile = NetworkConfig.RequestProfile.HISTORY
                )
                if (outcome is NetworkOutcome.Success) {
                    val parsed = parseYahooChartHistoryPayload(
                        raw = outcome.data,
                        range = range,
                        nowMillis = nowMillis
                    )
                    if (parsed.size >= 3) {
                        return@execute parsed to SOURCE_YAHOO
                    }
                }
            }

            // Fallback to EIA daily spot archive on multi-day/yearly ranges when EIA is configured
            val eiaKey = eiaApiKeyProvider().trim()
            if (range != TimeRange.H24 && (eiaKey.isNotBlank() || ep.oilEiaSpotUrl.isNotBlank())) {
                val rawEia = executeEiaRequest(ep.oilEiaSpotUrl, eiaKey, length = if (range == TimeRange.Y1) 370 else 45)
                if (rawEia != null) {
                    val seriesCode = if (assetId == "BRENT") "RBRTE" else "RWTC"
                    val eiaPts = parseEiaSpotHistoryPayload(rawEia, seriesCode = seriesCode, range = range)
                    if (eiaPts.size >= 3) {
                        return@execute eiaPts to SOURCE_EIA
                    }
                }
            }

            emptyList<AssetHistoryPoint>() to ""
        }
    }

    private suspend fun fetchCnbcOilQuotes(
        customUrl: String,
        nowMillis: Long
    ): Map<String, OilQuoteSnapshot>? {
        val url = customUrl.ifBlank { DEFAULT_CNBC_QUOTES_URL }
        val outcome = NetworkConfig.executeGetWithRetry(
            client = client,
            url = url,
            profile = NetworkConfig.RequestProfile.FAST_QUOTE
        )
        return when (outcome) {
            is NetworkOutcome.Success -> {
                val parsed = parseCnbcQuotesPayload(outcome.data, nowMillis)
                parsed.takeIf { it.isNotEmpty() }
            }
            is NetworkOutcome.HttpError, is NetworkOutcome.Failure -> null
        }
    }

    private suspend fun fetchYahooOilQuote(
        assetId: String,
        yahooSymbol: String,
        customTemplates: List<String>,
        nowMillis: Long
    ): OilQuoteSnapshot? {
        val templates = customTemplates.ifEmpty { DEFAULT_YAHOO_CHART_TEMPLATES }
        for (tmpl in templates) {
            val url = tmpl
                .replace("{symbol}", yahooSymbol)
                .replace("{range}", "5d")
                .replace("{interval}", "1h")
            val outcome = NetworkConfig.executeGetWithRetry(
                client = client,
                url = url,
                profile = NetworkConfig.RequestProfile.FAST_QUOTE
            )
            if (outcome is NetworkOutcome.Success) {
                val snap = parseYahooChartQuotePayload(
                    raw = outcome.data,
                    assetId = assetId,
                    nowMillis = nowMillis,
                    isPrimary = true
                )
                if (snap != null) return snap
            }
        }
        return null
    }

    private suspend fun fetchEiaSpotQuotes(
        customUrl: String,
        apiKey: String,
        nowMillis: Long
    ): Map<String, OilQuoteSnapshot>? {
        val raw = executeEiaRequest(customUrl, apiKey, length = 12) ?: return null
        val parsed = parseEiaSpotQuotesPayload(raw, nowMillis)
        return parsed.takeIf { it.isNotEmpty() }
    }

    private suspend fun executeEiaRequest(
        customUrl: String,
        apiKey: String,
        length: Int
    ): String? {
        val baseUrl = customUrl.ifBlank {
            "https://api.eia.gov/v2/petroleum/pri/spt/data/?frequency=daily&data[0]=value&facets[series][]=RBRTE&facets[series][]=RWTC&sort[0][column]=period&sort[0][direction]=desc&length=$length"
        }
        val requestBuilder = Request.Builder().url(baseUrl)
        if (apiKey.isNotBlank()) {
            requestBuilder.header("X-Api-Key", apiKey)
        }
        return try {
            val profiled = NetworkConfig.clientFor(NetworkConfig.RequestProfile.FAST_QUOTE, client)
            NetworkConfig.retrySuspend(maxAttempts = 2) {
                profiled.newCall(requestBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body?.string()?.takeIf { it.isNotBlank() }
                        ?: throw IOException("Empty EIA response")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val SOURCE_YAHOO = "Yahoo Finance"
        const val SOURCE_CNBC = "CNBC"
        const val SOURCE_EIA = "U.S. EIA"
        const val UNIT_USD_PER_BARREL = "USD/barrel"

        internal const val MIN_VALID_USD_PER_BARREL = 5.0
        internal const val MAX_VALID_USD_PER_BARREL = 500.0

        internal const val DEFAULT_CNBC_QUOTES_URL =
            "https://quote.cnbc.com/quote-html-webservice/restQuote/symbolType/symbol?symbols=@LCO.1|@CL.1&requestMethod=itv&noform=1&partnerId=2&fund=1&exthrs=1&output=json"

        internal val DEFAULT_YAHOO_CHART_TEMPLATES = listOf(
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}",
            "https://query2.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}"
        )

        /**
         * The key is baked in at build time (`EIA_API_KEY` env var or `-PeiaApiKey=` Gradle
         * property). Android app processes never inherit shell environment variables, so reading
         * `System.getenv` here would always return an empty string in a shipped APK.
         */
        private fun resolveConfiguredEiaApiKey(): String =
            ir.thepoo7an.chand.BuildConfig.EIA_API_KEY.trim()

        internal fun parseYahooChartQuotePayload(
            raw: String,
            assetId: String,
            nowMillis: Long = System.currentTimeMillis(),
            isPrimary: Boolean = true
        ): OilQuoteSnapshot? = runCatching {
            val root = JSONObject(raw)
            val chart = root.optJSONObject("chart") ?: return null
            if (!chart.isNull("error")) return null
            val resultArr = chart.optJSONArray("result") ?: return null
            val first = resultArr.optJSONObject(0) ?: return null
            val meta = first.optJSONObject("meta") ?: return null

            val currency = meta.optString("currency").trim().uppercase(Locale.US)
            if (currency != "USD") {
                return OilQuoteSnapshot(
                    assetId = assetId,
                    sourceName = SOURCE_YAHOO,
                    priceUsd = null,
                    currency = currency,
                    isPrimary = isPrimary,
                    failureReason = CandidateExclusionReason.INVALID
                )
            }

            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (!price.isFinite() || price !in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) {
                return OilQuoteSnapshot(
                    assetId = assetId,
                    sourceName = SOURCE_YAHOO,
                    priceUsd = price.takeIf { it.isFinite() },
                    currency = currency,
                    isPrimary = isPrimary,
                    failureReason = if (price.isFinite()) CandidateExclusionReason.INVALID else CandidateExclusionReason.MISSING_DATA
                )
            }

            val rawMarketTimeSec = meta.optLong("regularMarketTime", 0L)
            val sourceTsMillis = normalizeEpochMillis(rawMarketTimeSec, nowMillis)

            val high24h = meta.optDouble("regularMarketDayHigh", Double.NaN)
                .takeIf { it.isFinite() && it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }
            val low24h = meta.optDouble("regularMarketDayLow", Double.NaN)
                .takeIf { it.isFinite() && it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }

            val tsArr = first.optJSONArray("timestamp")
            val closeArr = first.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close")

            val candles = ArrayList<AssetHistoryPoint>()
            if (tsArr != null && closeArr != null) {
                val n = minOf(tsArr.length(), closeArr.length())
                for (i in 0 until n) {
                    val tsSec = tsArr.optLong(i, 0L)
                    val c = closeArr.optDouble(i, Double.NaN)
                    if (tsSec > 0L && c.isFinite() && c in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) {
                        candles.add(AssetHistoryPoint(tsSec, c))
                    }
                }
            }

            val prevClose = resolveYahoo24hReferenceClose(
                meta = meta,
                candles = candles,
                marketTimeSec = rawMarketTimeSec
            )
            val changeUsd: Double?
            val changePct: Double?
            if (prevClose != null && prevClose > 0.0) {
                val diff = price - prevClose
                changeUsd = diff
                changePct = (diff / prevClose) * 100.0
            } else {
                changeUsd = null
                changePct = null
            }

            val cutoff24hSec = (if (rawMarketTimeSec > 0L) rawMarketTimeSec else (nowMillis / 1000L)) - 24L * 3600L
            val recent24hSpark = candles
                .filter { it.timestampSec >= cutoff24hSec }
                .map { it.priceToman }
                .ifEmpty { candles.takeLast(24).map { it.priceToman } }

            OilQuoteSnapshot(
                assetId = assetId,
                sourceName = SOURCE_YAHOO,
                priceUsd = price,
                currency = currency,
                unit = UNIT_USD_PER_BARREL,
                change24hPct = changePct,
                change24hUsd = changeUsd,
                high24hUsd = high24h,
                low24hUsd = low24h,
                sourceTimestampMillis = sourceTsMillis,
                sparklineUsd = recent24hSpark,
                isPrimary = isPrimary,
                failureReason = null
            )
        }.getOrNull()

        private fun resolveYahoo24hReferenceClose(
            meta: JSONObject,
            candles: List<AssetHistoryPoint>,
            marketTimeSec: Long
        ): Double? {
            val regularPrev = meta.optDouble("regularMarketPreviousClose", Double.NaN)
                .takeIf { it.isFinite() && it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }
            if (regularPrev != null) return regularPrev

            val prevClose = meta.optDouble("previousClose", Double.NaN)
                .takeIf { it.isFinite() && it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }
            if (prevClose != null) return prevClose

            if (candles.size >= 2 && marketTimeSec > 0L) {
                val target24hAgo = marketTimeSec - 24L * 3600L
                val candidate = candles
                    .filter { it.timestampSec in (marketTimeSec - 36L * 3600L)..(marketTimeSec - 12L * 3600L) }
                    .minByOrNull { abs(it.timestampSec - target24hAgo) }
                if (candidate != null) return candidate.priceToman
            }

            return null
        }

        internal fun parseYahooChartHistoryPayload(
            raw: String,
            range: TimeRange,
            nowMillis: Long = System.currentTimeMillis()
        ): List<AssetHistoryPoint> = runCatching {
            val root = JSONObject(raw)
            val chart = root.optJSONObject("chart") ?: return emptyList()
            if (!chart.isNull("error")) return emptyList()
            val first = chart.optJSONArray("result")?.optJSONObject(0) ?: return emptyList()
            val meta = first.optJSONObject("meta") ?: return emptyList()
            val currency = meta.optString("currency").trim().uppercase(Locale.US)
            if (currency != "USD") return emptyList()

            val tsArr = first.optJSONArray("timestamp") ?: return emptyList()
            val closeArr = first.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close")
                ?: return emptyList()

            val nowSec = nowMillis / 1000L
            val cutoffSec = if (range == TimeRange.H24) nowSec - 48L * 3600L else nowSec - range.lookbackSeconds
            val rawPts = ArrayList<AssetHistoryPoint>(tsArr.length())
            val n = minOf(tsArr.length(), closeArr.length())
            for (i in 0 until n) {
                val ts = tsArr.optLong(i, 0L)
                val close = closeArr.optDouble(i, Double.NaN)
                if (ts > 0L && ts >= cutoffSec && close.isFinite() && close in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) {
                    rawPts.add(AssetHistoryPoint(ts, close))
                }
            }

            val normalized = HistoryNormalizer.normalize(
                points = rawPts,
                category = AssetCategory.OIL,
                range = range
            )
            if (range == TimeRange.H24 && normalized.size > 48) {
                normalized.takeLast(48)
            } else {
                normalized
            }
        }.getOrDefault(emptyList())

        internal fun parseCnbcQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, OilQuoteSnapshot> = runCatching {
            val root = JSONObject(raw)
            val formattedResult = root.optJSONObject("FormattedQuoteResult") ?: return emptyMap()
            val quotesArr = formattedResult.optJSONArray("FormattedQuote") ?: return emptyMap()
            val out = LinkedHashMap<String, OilQuoteSnapshot>()

            for (i in 0 until quotesArr.length()) {
                val obj = quotesArr.optJSONObject(i) ?: continue
                val sym = obj.optString("symbol").trim().uppercase(Locale.US)
                val assetId = when (sym) {
                    "@LCO.1", "LCO.1", "UKOIL" -> "BRENT"
                    "@CL.1", "CL.1", "USOIL" -> "WTI"
                    else -> continue
                }

                val currency = obj.optString("currency")
                    .ifBlank { obj.optString("currencyCode") }
                    .trim()
                    .uppercase(Locale.US)
                if (currency.isNotEmpty() && currency != "USD") {
                    out[assetId] = OilQuoteSnapshot(
                        assetId = assetId,
                        sourceName = SOURCE_CNBC,
                        priceUsd = null,
                        currency = currency,
                        isPrimary = false,
                        failureReason = CandidateExclusionReason.INVALID
                    )
                    continue
                }
                if (currency.isEmpty()) {
                    out[assetId] = OilQuoteSnapshot(
                        assetId = assetId,
                        sourceName = SOURCE_CNBC,
                        priceUsd = null,
                        currency = "",
                        isPrimary = false,
                        failureReason = CandidateExclusionReason.INVALID
                    )
                    continue
                }

                val price = MarketRemoteDataSource.parseNumeric(obj.optString("last"))
                if (price == null || !price.isFinite() || price !in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) {
                    out[assetId] = OilQuoteSnapshot(
                        assetId = assetId,
                        sourceName = SOURCE_CNBC,
                        priceUsd = price?.takeIf { it.isFinite() },
                        currency = currency,
                        isPrimary = false,
                        failureReason = if (price != null) CandidateExclusionReason.INVALID else CandidateExclusionReason.MISSING_DATA
                    )
                    continue
                }

                val tsRaw = obj.optString("last_time").ifBlank { obj.optString("last_timedate") }
                val parsedTs = FinancialApiService.parseIsoTimestampMillis(tsRaw)
                    ?.let { if (it <= nowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS) it else 0L }
                    ?: 0L

                val parsedChangePct = parseOptionalCnbcNumber(obj.optString("change_pct"))
                val parsedChangeUsd = parseOptionalCnbcNumber(obj.optString("change"))

                val finalChangePct: Double?
                val finalChangeUsd: Double?
                when {
                    parsedChangePct != null && parsedChangeUsd != null -> {
                        finalChangePct = parsedChangePct
                        finalChangeUsd = parsedChangeUsd
                    }
                    parsedChangePct != null -> {
                        finalChangePct = parsedChangePct
                        val prev = price / (1.0 + parsedChangePct / 100.0)
                        finalChangeUsd = if (prev.isFinite() && prev > 0.0) price - prev else null
                    }
                    parsedChangeUsd != null -> {
                        finalChangeUsd = parsedChangeUsd
                        val prev = price - parsedChangeUsd
                        finalChangePct = if (prev.isFinite() && prev > 0.0) (parsedChangeUsd / prev) * 100.0 else null
                    }
                    else -> {
                        finalChangePct = null
                        finalChangeUsd = null
                    }
                }

                val highUsd = parseOptionalCnbcNumber(obj.optString("high"))
                    ?.takeIf { it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }
                val lowUsd = parseOptionalCnbcNumber(obj.optString("low"))
                    ?.takeIf { it in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL }

                out[assetId] = OilQuoteSnapshot(
                    assetId = assetId,
                    sourceName = SOURCE_CNBC,
                    priceUsd = price,
                    currency = currency,
                    unit = UNIT_USD_PER_BARREL,
                    change24hPct = finalChangePct,
                    change24hUsd = finalChangeUsd,
                    high24hUsd = highUsd,
                    low24hUsd = lowUsd,
                    sourceTimestampMillis = parsedTs,
                    isPrimary = false,
                    failureReason = null
                )
            }
            out
        }.getOrDefault(emptyMap())

        internal fun parseEiaSpotQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, OilQuoteSnapshot> = runCatching {
            val root = JSONObject(raw)
            val response = root.optJSONObject("response") ?: return emptyMap()
            val dataArr = response.optJSONArray("data") ?: return emptyMap()

            data class EiaPoint(val period: String, val tsMillis: Long, val value: Double, val units: String)
            val seriesRows = LinkedHashMap<String, MutableList<EiaPoint>>()

            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val series = item.optString("series").trim().uppercase(Locale.US)
                val assetId = when (series) {
                    "RBRTE" -> "BRENT"
                    "RWTC" -> "WTI"
                    else -> continue
                }
                val period = item.optString("period").trim()
                val units = item.optString("units").trim().lowercase(Locale.US)
                val rawVal = item.opt("value")
                val value = when (rawVal) {
                    is Number -> rawVal.toDouble()
                    is String -> MarketRemoteDataSource.parseNumeric(rawVal)
                    else -> null
                } ?: continue
                if (!value.isFinite() || value !in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) continue
                if (units.isNotEmpty() && "barrel" !in units && "bbl" !in units) continue

                val tsMillis = FinancialApiService.parseDailyUtcTimestampMillis(period, hourUtc = 21, nowMillis = nowMillis)
                seriesRows.getOrPut(assetId) { mutableListOf() }.add(
                    EiaPoint(period = period, tsMillis = tsMillis, value = value, units = units)
                )
            }

            val out = LinkedHashMap<String, OilQuoteSnapshot>()
            seriesRows.forEach { (assetId, pts) ->
                val sortedDesc = pts.sortedByDescending { it.period }
                val latest = sortedDesc.firstOrNull() ?: return@forEach
                val prev = sortedDesc.getOrNull(1)
                val maxPrevGapMs = 4L * 24L * 3600L * 1000L
                val canComputeDailyChange = prev != null &&
                    latest.tsMillis > 0L &&
                    prev.tsMillis > 0L &&
                    (latest.tsMillis - prev.tsMillis) in 1L..maxPrevGapMs &&
                    prev.value > 0.0
                val changeUsd = if (canComputeDailyChange) latest.value - prev.value else null
                val changePct = if (canComputeDailyChange) ((latest.value - prev.value) / prev.value) * 100.0 else null

                out[assetId] = OilQuoteSnapshot(
                    assetId = assetId,
                    sourceName = SOURCE_EIA,
                    priceUsd = latest.value,
                    currency = "USD",
                    unit = UNIT_USD_PER_BARREL,
                    change24hPct = changePct,
                    change24hUsd = changeUsd,
                    sourceTimestampMillis = latest.tsMillis,
                    isPrimary = false,
                    failureReason = null
                )
            }
            out
        }.getOrDefault(emptyMap())

        internal fun parseEiaSpotHistoryPayload(
            raw: String,
            seriesCode: String,
            range: TimeRange
        ): List<AssetHistoryPoint> = runCatching {
            val root = JSONObject(raw)
            val dataArr = root.optJSONObject("response")?.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<AssetHistoryPoint>()
            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                if (!item.optString("series").equals(seriesCode, ignoreCase = true)) continue
                val period = item.optString("period").trim()
                val rawVal = item.opt("value")
                val value = when (rawVal) {
                    is Number -> rawVal.toDouble()
                    is String -> MarketRemoteDataSource.parseNumeric(rawVal)
                    else -> null
                } ?: continue
                if (!value.isFinite() || value !in MIN_VALID_USD_PER_BARREL..MAX_VALID_USD_PER_BARREL) continue
                val tsMillis = FinancialApiService.parseDailyUtcTimestampMillis(period, hourUtc = 21, nowMillis = Long.MAX_VALUE / 2)
                if (tsMillis > 0L) {
                    list.add(AssetHistoryPoint(tsMillis / 1000L, value))
                }
            }
            HistoryNormalizer.normalize(list, category = AssetCategory.OIL, range = range)
        }.getOrDefault(emptyList())

        private fun parseOptionalCnbcNumber(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().lowercase(Locale.US)
            if (clean == "--" || clean == "n/a" || clean == "na" || clean == "unch") return null
            return MarketRemoteDataSource.parseNumeric(raw)?.takeIf { it.isFinite() }
        }

        private fun normalizeEpochMillis(rawSecOrMs: Long, nowMillis: Long): Long {
            if (rawSecOrMs <= 0L) return 0L
            val millis = if (rawSecOrMs < 10_000_000_000L) rawSecOrMs * 1000L else rawSecOrMs
            return if (millis <= nowMillis + MarketValidationConfig.FUTURE_TIMESTAMP_REJECT_TOLERANCE_MILLIS) {
                millis
            } else {
                0L
            }
        }
    }
}
