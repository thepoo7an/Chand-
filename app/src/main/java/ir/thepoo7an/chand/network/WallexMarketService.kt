package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.TimeRange
import ir.thepoo7an.chand.network.MarketPriceMapper.ExchangeQuote
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Wallex (والکس) public market data: live Toman/USD quotes and the UDF history used for the
 * crypto sparklines and charts.
 *
 * - Quotes: `https://api.wallex.ir/v1/markets` (live config `wallexMarketsUrl`). Pairs quoted in
 *   `TMN` are Toman quotes, pairs quoted in `USDT` are the USD basis; a pair carrying both sides
 *   yields one [ExchangeQuote] with `high`/`low` from the `stats` block.
 * - History: `https://api.wallex.ir/v1/udf/history` (live config `wallexUdfUrlTemplate`, which
 *   accepts `{symbol}`, `{resolution}`, `{from}`, `{to}`). Series shorter than a few points are
 *   rejected by the caller, not here: this service returns whatever the provider published.
 *
 * Failure semantics match the rest of the network layer: unreachable, malformed or explicitly
 * non-`ok` payloads degrade to an empty result, and a quote without its own timestamp reports
 * `0L` instead of borrowing the fetch time.
 */
class WallexMarketService(
    private val client: OkHttpClient = NetworkConfig.sharedClient
) {

    /** Live Toman/USD quotes keyed by base asset code (e.g. `BTC`, `USDT`). */
    suspend fun fetchQuotes(nowMillis: Long = System.currentTimeMillis()): Map<String, ExchangeQuote> {
        val url = LiveConfigRegistry.currentConfig().endpoints.wallexMarketsUrl
            .ifBlank { DEFAULT_MARKETS_URL }
        val raw = when (
            val outcome = NetworkConfig.executeGetWithRetry(
                client = client,
                url = url,
                profile = NetworkConfig.RequestProfile.FAST_QUOTE
            )
        ) {
            is NetworkOutcome.Success -> outcome.data
            is NetworkOutcome.HttpError, is NetworkOutcome.Failure -> return emptyMap()
        }
        return parseQuotesPayload(raw, nowMillis)
    }

    /** UDF history points in **Toman** for [symbol] (e.g. `BTCTMN`), oldest first. */
    suspend fun fetchUdfHistory(
        symbol: String,
        range: TimeRange,
        fromSec: Long,
        toSec: Long,
        category: AssetCategory = AssetCategory.CRYPTO,
        spikeThresholdPct: Double = MarketValidationConfig.spikeThresholdPct(category, range)
    ): List<AssetHistoryPoint> {
        val resolution = when (range) {
            TimeRange.H24 -> "60"
            TimeRange.D7 -> "240"
            TimeRange.M1, TimeRange.Y1 -> "1D"
        }
        val template = LiveConfigRegistry.currentConfig().endpoints.wallexUdfUrlTemplate
        val url = if (template.isNotBlank()) {
            template.replace("{symbol}", symbol)
                .replace("{resolution}", resolution)
                .replace("{from}", fromSec.toString())
                .replace("{to}", toSec.toString())
        } else {
            "$DEFAULT_UDF_URL?symbol=$symbol&resolution=$resolution&from=$fromSec&to=$toSec"
        }
        val raw = when (
            val outcome = NetworkConfig.executeGetWithRetry(
                client = client,
                url = url,
                profile = NetworkConfig.RequestProfile.HISTORY
            )
        ) {
            is NetworkOutcome.Success -> outcome.data
            is NetworkOutcome.HttpError, is NetworkOutcome.Failure -> return emptyList()
        }
        return parseUdfPayload(raw, spikeThresholdPct = spikeThresholdPct)
    }

    companion object {
        const val DEFAULT_MARKETS_URL = "https://api.wallex.ir/v1/markets"
        const val DEFAULT_UDF_URL = "https://api.wallex.ir/v1/udf/history"

        fun parseQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, ExchangeQuote> = runCatching {
            val root = JSONObject(raw)
            val resultObj = root.optJSONObject("result") ?: return emptyMap()
            val markets = resultObj.optJSONObject("markets") ?: return emptyMap()
            val rootTs = ProviderParsing.parseQuoteTimestampMillis(resultObj, root)
            data class WallexSide(
                val price: Double,
                val change: Double,
                val high: Double?,
                val low: Double?,
                val timestampMillis: Long
            )
            val tmnPrices = mutableMapOf<String, WallexSide>()
            val usdtPrices = mutableMapOf<String, WallexSide>()

            val keys = markets.keys()
            while (keys.hasNext()) {
                val sym = keys.next()
                val item = markets.optJSONObject(sym) ?: continue
                val base = item.optString("baseAsset")
                val quote = item.optString("quoteAsset")
                val stats = item.optJSONObject("stats")
                val price = ProviderParsing.parseNumeric(item.optString("price"))
                    ?: stats?.optString("lastPrice")?.let { ProviderParsing.parseNumeric(it) }
                    ?: continue
                val change = item.optDouble("change_24h", Double.NaN).let {
                    if (it.isNaN()) stats?.optDouble("24h_ch", 0.0) ?: 0.0 else it
                }
                val high = stats?.optString("24h_highPrice")
                    ?.let { ProviderParsing.parseNumeric(it) }?.takeIf { it > 0.0 }
                val low = stats?.optString("24h_lowPrice")
                    ?.let { ProviderParsing.parseNumeric(it) }?.takeIf { it > 0.0 }
                val itemTs = ProviderParsing.parseQuoteTimestampMillis(item, stats)
                    .takeIf { it > 0L } ?: rootTs
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

        /** Parses a UDF history payload (`{"s":"ok","t":[…],"c":[…]}`) into normalised Toman points. */
        fun parseUdfPayload(
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
                val close = ProviderParsing.parseNumeric(cArr.optString(i)) ?: cArr.optDouble(i, -1.0)
                if (ts > 0 && close > 0.0) {
                    list.add(AssetHistoryPoint(ts, close))
                }
            }
            HistoryNormalizer.normalize(list, spikeThresholdPct = spikeThresholdPct)
        }.getOrDefault(emptyList())
    }
}
