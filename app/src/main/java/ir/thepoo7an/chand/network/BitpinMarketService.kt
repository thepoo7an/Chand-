package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.network.MarketPriceMapper.ExchangeQuote
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Bitpin (بیت‌پین) public market quotes — the first provider split out of the 1,600-line
 * `MarketRemoteDataSource`, one provider per change.
 *
 * Endpoint: `https://api.bitpin.ir/v1/mkt/markets/` (overridable through the live config's
 * `bitpinMarketsUrl`). Each entry of `results` describes a pair by currency codes and carries a
 * `price_info` block; pairs quoted in `IRT` become the Toman quote, pairs quoted in `USDT` become
 * the USD basis used when the Toman side is missing. Anything else is ignored on purpose.
 *
 * Failure semantics: an unreachable or malformed payload yields an **empty map**, never a zero
 * price — the aggregator reports the provider as failed and the cross-source comparison shows it
 * as unavailable instead of publishing a fabricated rate.
 *
 * Contract fixture: `app/src/test/resources/fixtures/bitpin/markets.json`.
 */
class BitpinMarketService(
    private val client: OkHttpClient = NetworkConfig.sharedClient
) {

    /** Live Toman/USD quotes keyed by base asset code (e.g. `BTC`, `USDT`). */
    suspend fun fetchQuotes(nowMillis: Long = System.currentTimeMillis()): Map<String, ExchangeQuote> {
        val url = LiveConfigRegistry.currentConfig().endpoints.bitpinMarketsUrl
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

    companion object {
        const val DEFAULT_MARKETS_URL = "https://api.bitpin.ir/v1/mkt/markets/"

        /**
         * Parses a `/mkt/markets/` payload. [nowMillis] is the fetch time and is deliberately not
         * used as a source timestamp: a quote without its own timestamp reports `0L` (unknown)
         * so the freshness chips never claim data is newer than it is.
         */
        fun parseQuotesPayload(
            raw: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, ExchangeQuote> = runCatching {
            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: return emptyMap()
            val rootTs = ProviderParsing.parseQuoteTimestampMillis(root)
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
                val price = ProviderParsing.parseNumeric(priceInfo?.optString("price"))
                    ?: ProviderParsing.parseNumeric(item.optString("price"))
                    ?: continue
                val change = priceInfo?.optDouble("change", 0.0) ?: 0.0
                val itemTs = ProviderParsing.parseQuoteTimestampMillis(priceInfo, item)
                    .takeIf { it > 0L } ?: rootTs
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
    }
}
