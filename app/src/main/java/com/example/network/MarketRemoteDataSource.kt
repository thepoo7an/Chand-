package com.example.network

import com.example.model.AssetCategory
import com.example.model.AssetHistoryPoint
import com.example.model.MarketAsset
import com.example.model.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class RemoteFetchResult(
    val assets: List<MarketAsset>,
    val fetchedAtMillis: Long,
    val sourceSummary: String,
    val isLive: Boolean
)

class MarketRemoteDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(null)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    private data class TgjuQuote(
        val priceToman: Double,
        val highToman: Double,
        val lowToman: Double,
        val changeToman: Double,
        val changePct: Double
    )

    private data class ExchangeQuote(
        val priceToman: Double?,
        val priceUsd: Double?,
        val changePct: Double
    )

    suspend fun fetchLiveMarket(): RemoteFetchResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val tgjuDeferred = async { fetchTgjuQuotes() }
            val wallexDeferred = async { fetchWallexQuotes() }
            val bitpinDeferred = async { fetchBitpinQuotes() }
            val nobitexUsdtDeferred = async { fetchNobitexOrderbook("USDTIRT") }
            val binanceDeferred = async { fetchBinanceQuotes() }
            val fxDeferred = async { fetchOpenExchangeRates() }
            val frankfurterDeferred = async { fetchFrankfurterRates() }

            val tgju = tgjuDeferred.await()
            val wallex = wallexDeferred.await()
            val bitpin = bitpinDeferred.await()
            val nobitexUsdt = nobitexUsdtDeferred.await()
            val binance = binanceDeferred.await()
            val openErRates = fxDeferred.await()
            val frankfurterRates = frankfurterDeferred.await()

            val fxUsdRates = openErRates.ifEmpty { frankfurterRates }

            val activeSources = mutableListOf<String>()
            if (tgju.isNotEmpty()) activeSources.add("TGJU")
            if (wallex.isNotEmpty()) activeSources.add("Wallex")
            if (bitpin.isNotEmpty()) activeSources.add("Bitpin")
            if (nobitexUsdt != null) activeSources.add("Nobitex")
            if (binance.isNotEmpty()) activeSources.add("Binance")
            if (fxUsdRates.isNotEmpty()) activeSources.add("FX")

            if (activeSources.isEmpty()) {
                return@coroutineScope RemoteFetchResult(
                    assets = emptyList(),
                    fetchedAtMillis = System.currentTimeMillis(),
                    sourceSummary = "",
                    isLive = false
                )
            }

            val now = System.currentTimeMillis()

            // Live 24/7 median Tether (USDT/TMN) across Wallex, Bitpin, Nobitex
            val usdtCandidates = listOfNotNull(
                wallex["USDT"]?.priceToman,
                bitpin["USDT"]?.priceToman,
                nobitexUsdt
            ).filter { it > 1000.0 }.sorted()

            val usdtToman = when {
                usdtCandidates.size >= 3 -> usdtCandidates[1]
                usdtCandidates.isNotEmpty() -> usdtCandidates.average()
                else -> tgju["price_dollar_rl"]?.priceToman ?: 68_500.0
            }

            val usdtChangePct = wallex["USDT"]?.changePct
                ?: bitpin["USDT"]?.changePct
                ?: tgju["price_dollar_rl"]?.changePct
                ?: 0.0

            val usdQuote = tgju["price_dollar_rl"]
            val usdToman = usdQuote?.priceToman ?: usdtToman
            val usdChangePct = usdQuote?.changePct ?: usdtChangePct
            val usdChangeToman = usdQuote?.changeToman ?: (usdToman * usdChangePct / 100.0)

            fun resolveFiat(
                id: String,
                symbol: String,
                nameFa: String,
                nameEn: String,
                badge: String,
                accent: Long,
                tgjuKey: String,
                fxCode: String
            ): MarketAsset? {
                val fxPerUsd = fxUsdRates[fxCode]
                val crossPriceToman = if (fxPerUsd != null && fxPerUsd > 0.0) usdToman / fxPerUsd else null
                val tQuote = tgju[tgjuKey]

                var priceToman = tQuote?.priceToman ?: crossPriceToman ?: return null
                var highToman = tQuote?.highToman ?: priceToman * 1.006
                var lowToman = tQuote?.lowToman ?: priceToman * 0.994
                var changeToman = tQuote?.changeToman ?: (priceToman * usdChangePct / 100.0)

                if (crossPriceToman != null && crossPriceToman > 0.0) {
                    val ratio = priceToman / crossPriceToman
                    if (ratio > 50.0 && ratio < 200.0) {
                        priceToman /= 100.0
                        highToman /= 100.0
                        lowToman /= 100.0
                        changeToman /= 100.0
                    } else if (ratio > 500.0 && ratio < 2000.0) {
                        priceToman /= 1000.0
                        highToman /= 1000.0
                        lowToman /= 1000.0
                        changeToman /= 1000.0
                    }
                }

                val changePct = tQuote?.changePct ?: usdChangePct
                val src = if (tQuote != null) "TGJU" else "FX + USD"

                return MarketAsset(
                    id = id,
                    symbol = symbol,
                    nameFa = nameFa,
                    nameEn = nameEn,
                    category = AssetCategory.FIAT,
                    badgeText = badge,
                    accentHex = accent,
                    priceToman = priceToman,
                    priceUsd = if (fxPerUsd != null && fxPerUsd > 0.0) 1.0 / fxPerUsd else null,
                    change24hPercent = changePct,
                    change24hToman = changeToman,
                    high24hToman = maxOf(highToman, priceToman),
                    low24hToman = minOf(lowToman, priceToman),
                    sparkline = buildSparkline(priceToman, changePct, id.hashCode()),
                    sourceLabel = src,
                    updatedAtMillis = now
                )
            }

            val assets = mutableListOf<MarketAsset>()

            // 1. US Dollar
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
                    high24hToman = maxOf(usdQuote?.highToman ?: (usdToman * 1.008), usdToman),
                    low24hToman = minOf(usdQuote?.lowToman ?: (usdToman * 0.992), usdToman),
                    sparkline = buildSparkline(usdToman, usdChangePct, "USD".hashCode()),
                    sourceLabel = if (usdQuote != null) "TGJU" else "USDT Median",
                    updatedAtMillis = now
                )
            )

            listOfNotNull(
                resolveFiat("EUR", "EUR", "یورو", "Euro", "🇪🇺", 0xFF3B82F6, "price_eur", "EUR"),
                resolveFiat("AED", "AED", "درهم امارات", "UAE Dirham", "🇦🇪", 0xFF14B8A6, "price_aed", "AED"),
                resolveFiat("GBP", "GBP", "پوند انگلیس", "British Pound", "🇬🇧", 0xFF6366F1, "price_gbp", "GBP"),
                resolveFiat("KWD", "KWD", "دینار کویت", "Kuwaiti Dinar", "🇰🇼", 0xFF059669, "price_kwd", "KWD"),
                resolveFiat("TRY", "TRY", "لیر ترکیه", "Turkish Lira", "🇹🇷", 0xFFEF4444, "price_try", "TRY"),
                resolveFiat("CAD", "CAD", "دلار کانادا", "Canadian Dollar", "🇨🇦", 0xFFF43F5E, "price_cad", "CAD"),
                resolveFiat("CHF", "CHF", "فرانک سوئیس", "Swiss Franc", "🇨🇭", 0xFFEC4899, "price_chf", "CHF"),
                resolveFiat("CNY", "CNY", "یوان چین", "Chinese Yuan", "🇨🇳", 0xFFF59E0B, "price_cny", "CNY"),
                resolveFiat("AUD", "AUD", "دلار استرالیا", "Australian Dollar", "🇦🇺", 0xFF06B6D4, "price_aud", "AUD"),
                resolveFiat("JPY", "JPY", "ین ژاپن", "Japanese Yen", "🇯🇵", 0xFFFB7185, "price_jpy", "JPY"),
                resolveFiat("SAR", "SAR", "ریال عربستان", "Saudi Riyal", "🇸🇦", 0xFF22C55E, "price_sar", "SAR"),
                resolveFiat("IQD", "IQD", "دینار عراق", "Iraqi Dinar", "🇮🇶", 0xFF84CC16, "price_iqd", "IQD")
            ).forEach { assets.add(it) }

            // 2. Gold & Coins (live PAXG Gold Ounce from Binance + TGJU Tehran coins/18K)
            val binanceGoldOz = binance["PAXG"]?.priceUsd
            val onsRaw = tgju["ons"]
            val onsUsd = binanceGoldOz ?: run {
                val rawOns = (onsRaw?.priceToman ?: 0.0) * 10.0
                if (rawOns in 1200.0..6500.0) rawOns else 2650.0
            }
            val gram24Theoretical = (onsUsd / 31.1035) * usdToman
            val gram18Theoretical = gram24Theoretical * 0.75

            fun addGoldAsset(
                id: String,
                symbol: String,
                nameFa: String,
                nameEn: String,
                badge: String,
                tgjuKey: String,
                fallbackPrice: Double,
                usdValue: Double? = null,
                overrideSource: String? = null
            ) {
                val q = tgju[tgjuKey]
                val price = if (id == "XAU_OZ") onsUsd * usdToman else (q?.priceToman ?: fallbackPrice)
                val changePct = if (id == "XAU_OZ") (binance["PAXG"]?.changePct ?: q?.changePct ?: usdChangePct)
                else (q?.changePct ?: usdChangePct)
                val changeToman = if (id == "XAU_OZ") {
                    price * changePct / 100.0
                } else {
                    q?.changeToman ?: (price * changePct / 100.0)
                }
                val high = if (id == "XAU_OZ") price * 1.008 else maxOf(q?.highToman ?: (price * 1.01), price)
                val low = if (id == "XAU_OZ") price * 0.992 else minOf(q?.lowToman ?: (price * 0.99), price)
                assets.add(
                    MarketAsset(
                        id = id,
                        symbol = symbol,
                        nameFa = nameFa,
                        nameEn = nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = price,
                        priceUsd = usdValue ?: (price / usdToman),
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = high,
                        low24hToman = low,
                        sparkline = buildSparkline(price, changePct, id.hashCode()),
                        sourceLabel = overrideSource ?: if (q != null) "TGJU" else "PAXG + USD",
                        updatedAtMillis = now
                    )
                )
            }

            addGoldAsset("GOLD_18K", "GRAM", "طلای ۱۸ عیار", "Gram", "✨", "geram18", gram18Theoretical)
            addGoldAsset("COIN_QUARTER", "QUARTER", "ربع سکه", "1/4 Azadi", "🏅", "rob", gram18Theoretical * 3.55)
            addGoldAsset("COIN_HALF", "HALF", "نیم سکه", "1/2 Azadi", "🥇", "nim", gram18Theoretical * 5.95)
            addGoldAsset("COIN_EMAMI", "SEKE", "سکه امامی", "Emami Coin", "🪙", "sekee", gram18Theoretical * 11.15)
            addGoldAsset(
                id = "XAU_OZ",
                symbol = "XAU",
                nameFa = "انس جهانی طلا",
                nameEn = "Gold Ounce",
                badge = "🏆",
                tgjuKey = "ons",
                fallbackPrice = onsUsd * usdToman,
                usdValue = onsUsd,
                overrideSource = if (binanceGoldOz != null) "Binance PAXG" else "TGJU"
            )

            // 3. Cryptocurrencies (Wallex + Bitpin + Nobitex + Binance 24/7)
            data class CryptoMeta(
                val id: String,
                val nameFa: String,
                val nameEn: String,
                val badge: String,
                val accent: Long
            )

            val cryptoList = listOf(
                CryptoMeta("USDT", "تتر", "Tether", "₮", 0xFF26A17B),
                CryptoMeta("BTC", "بیت‌کوین", "Bitcoin", "₿", 0xFFF7931A),
                CryptoMeta("ETH", "اتریوم", "Ethereum", "Ξ", 0xFF627EEA),
                CryptoMeta("TON", "تون‌کوین", "Toncoin", "◆", 0xFF0098EA),
                CryptoMeta("SOL", "سولانا", "Solana", "◎", 0xFF9945FF),
                CryptoMeta("BNB", "بایننس‌کوین", "BNB", "◈", 0xFFF3BA2F),
                CryptoMeta("XRP", "ریپل", "XRP", "✕", 0xFF38BDF8),
                CryptoMeta("DOGE", "دوج‌کوین", "Dogecoin", "Ð", 0xFFC2A633)
            )

            cryptoList.forEach { meta ->
                val w = wallex[meta.id]
                val bp = bitpin[meta.id]
                val bn = binance[meta.id]

                val priceUsd = if (meta.id == "USDT") 1.0 else (bn?.priceUsd ?: w?.priceUsd ?: bp?.priceUsd)
                val tmnCandidates = listOfNotNull(w?.priceToman, bp?.priceToman)
                val priceToman = when {
                    meta.id == "USDT" -> usdtToman
                    tmnCandidates.isNotEmpty() -> tmnCandidates.average()
                    priceUsd != null -> priceUsd * usdtToman
                    else -> null
                }

                if (priceToman != null && priceToman > 0.0) {
                    val changePct = bn?.changePct ?: w?.changePct ?: bp?.changePct ?: 0.0
                    val changeToman = priceToman * (changePct / 100.0)
                    val source = when {
                        meta.id == "USDT" -> listOfNotNull(
                            if (wallex["USDT"]?.priceToman != null) "Wallex" else null,
                            if (bitpin["USDT"]?.priceToman != null) "Bitpin" else null,
                            if (nobitexUsdt != null) "Nobitex" else null
                        ).joinToString("+").ifEmpty { "Live" }
                        w?.priceToman != null && bp?.priceToman != null -> "Wallex+Bitpin"
                        w?.priceToman != null -> "Wallex"
                        bp?.priceToman != null -> "Bitpin"
                        bn != null -> "Binance"
                        else -> "Live"
                    }
                    assets.add(
                        MarketAsset(
                            id = meta.id,
                            symbol = meta.id,
                            nameFa = meta.nameFa,
                            nameEn = meta.nameEn,
                            category = AssetCategory.CRYPTO,
                            badgeText = meta.badge,
                            accentHex = meta.accent,
                            priceToman = priceToman,
                            priceUsd = priceUsd,
                            change24hPercent = changePct,
                            change24hToman = changeToman,
                            high24hToman = priceToman * (1.0 + maxOf(0.008, abs(changePct) / 100.0)),
                            low24hToman = priceToman * (1.0 - maxOf(0.008, abs(changePct) / 100.0)),
                            sparkline = buildSparkline(priceToman, changePct, meta.id.hashCode()),
                            sourceLabel = source,
                            updatedAtMillis = now
                        )
                    )
                }
            }

            RemoteFetchResult(
                assets = assets,
                fetchedAtMillis = now,
                sourceSummary = activeSources.joinToString(" • "),
                isLive = true
            )
        }
    }

    suspend fun fetchAssetHistory(
        asset: MarketAsset,
        range: TimeRange
    ): List<AssetHistoryPoint> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val fromSec = nowSec - range.lookbackSeconds

        if (asset.category == AssetCategory.CRYPTO) {
            val nobitexPoints = fetchNobitexUdf("${asset.symbol}IRT", range.nobitexResolution, fromSec, nowSec)
            if (nobitexPoints.size >= 4) {
                return@withContext nobitexPoints
            }
        }

        if (asset.category == AssetCategory.FIAT && range != TimeRange.H24 && asset.symbol != "USD") {
            val fxPoints = fetchFrankfurterHistory(asset.symbol, fromSec, nowSec)
            val usdtPoints = fetchNobitexUdf("USDTIRT", range.nobitexResolution, fromSec, nowSec)
            if (fxPoints.isNotEmpty() && usdtPoints.isNotEmpty()) {
                val combined = usdtPoints.mapIndexed { idx, usdtPt ->
                    val fxRate = fxPoints[minOf(idx, fxPoints.lastIndex)].second
                    val rawToman = if (fxRate > 0.0) usdtPt.priceToman / fxRate else asset.priceToman
                    AssetHistoryPoint(usdtPt.timestampSec, rawToman)
                }
                if (combined.size >= 4) {
                    val factor = if (combined.last().priceToman > 0) asset.priceToman / combined.last().priceToman else 1.0
                    return@withContext combined.map { it.copy(priceToman = it.priceToman * factor) }
                }
            }
        }

        val usdtFallback = fetchNobitexUdf("USDTIRT", range.nobitexResolution, fromSec, nowSec)
        if (usdtFallback.size >= 4 && usdtFallback.last().priceToman > 0.0) {
            val scale = asset.priceToman / usdtFallback.last().priceToman
            return@withContext usdtFallback.map { it.copy(priceToman = it.priceToman * scale) }
        }

        buildAnchoredHistory(asset, range, fromSec, nowSec)
    }

    private fun executeGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("User-Agent", "ChandAndroid/1.0")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
    }.getOrNull()

    private fun fetchTgjuQuotes(): Map<String, TgjuQuote> {
        val raw = executeGet("https://call5.tgju.org/ajax.json?t=${System.currentTimeMillis()}")
            ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val current = root.optJSONObject("current") ?: return emptyMap()
            val map = mutableMapOf<String, TgjuQuote>()
            val keys = current.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = current.optJSONObject(k) ?: continue
                val priceRial = parseNumeric(obj.optString("p")) ?: continue
                if (priceRial <= 0.0) continue
                val highRial = parseNumeric(obj.optString("h")) ?: priceRial
                val lowRial = parseNumeric(obj.optString("l")) ?: priceRial
                val diffRial = parseNumeric(obj.optString("d")) ?: 0.0
                val dp = parseNumeric(obj.optString("dp")) ?: 0.0
                val dt = obj.optString("dt")
                val sign = if (dt == "low") -1.0 else 1.0
                val signedDp = if (dp > 0) sign * dp else dp
                val signedDiff = if (diffRial > 0) sign * diffRial else diffRial
                map[k] = TgjuQuote(
                    priceToman = priceRial / 10.0,
                    highToman = highRial / 10.0,
                    lowToman = lowRial / 10.0,
                    changeToman = signedDiff / 10.0,
                    changePct = signedDp
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun fetchWallexQuotes(): Map<String, ExchangeQuote> {
        val raw = executeGet("https://api.wallex.ir/v1/markets") ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val markets = root.optJSONObject("result")?.optJSONObject("markets") ?: return emptyMap()
            val tmnPrices = mutableMapOf<String, Pair<Double, Double>>()
            val usdtPrices = mutableMapOf<String, Pair<Double, Double>>()

            val keys = markets.keys()
            while (keys.hasNext()) {
                val sym = keys.next()
                val item = markets.optJSONObject(sym) ?: continue
                val base = item.optString("baseAsset")
                val quote = item.optString("quoteAsset")
                val price = parseNumeric(item.optString("price"))
                    ?: item.optJSONObject("stats")?.optString("lastPrice")?.let { parseNumeric(it) }
                    ?: continue
                val change = item.optDouble("change_24h", Double.NaN).let {
                    if (it.isNaN()) item.optJSONObject("stats")?.optDouble("24h_ch", 0.0) ?: 0.0 else it
                }
                if (quote == "TMN" && price > 0) {
                    tmnPrices[base] = price to change
                } else if (quote == "USDT" && price > 0) {
                    usdtPrices[base] = price to change
                }
            }

            val allBases = tmnPrices.keys + usdtPrices.keys
            val result = mutableMapOf<String, ExchangeQuote>()
            for (base in allBases) {
                val tmn = tmnPrices[base]
                val usdt = usdtPrices[base]
                result[base] = ExchangeQuote(
                    priceToman = tmn?.first,
                    priceUsd = usdt?.first,
                    changePct = tmn?.second ?: usdt?.second ?: 0.0
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun fetchBitpinQuotes(): Map<String, ExchangeQuote> {
        val raw = executeGet("https://api.bitpin.ir/v1/mkt/markets/") ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val results = root.optJSONArray("results") ?: return emptyMap()
            val tmnPrices = mutableMapOf<String, Pair<Double, Double>>()
            val usdtPrices = mutableMapOf<String, Pair<Double, Double>>()

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
                if (quote == "IRT" && price > 0) {
                    tmnPrices[base] = price to change
                } else if (quote == "USDT" && price > 0) {
                    usdtPrices[base] = price to change
                }
            }

            val allBases = tmnPrices.keys + usdtPrices.keys
            val map = mutableMapOf<String, ExchangeQuote>()
            for (base in allBases) {
                val tmn = tmnPrices[base]
                val usdt = usdtPrices[base]
                map[base] = ExchangeQuote(
                    priceToman = tmn?.first,
                    priceUsd = usdt?.first,
                    changePct = tmn?.second ?: usdt?.second ?: 0.0
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun fetchBinanceQuotes(): Map<String, ExchangeQuote> {
        val symbols = "[\"BTCUSDT\",\"ETHUSDT\",\"TONUSDT\",\"SOLUSDT\",\"BNBUSDT\",\"XRPUSDT\",\"DOGEUSDT\",\"PAXGUSDT\"]"
        val raw = executeGet("https://api.binance.com/api/v3/ticker/24hr?symbols=$symbols")
            ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, ExchangeQuote>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sym = obj.optString("symbol")
                if (!sym.endsWith("USDT")) continue
                val base = sym.removeSuffix("USDT")
                val price = parseNumeric(obj.optString("lastPrice")) ?: continue
                val change = parseNumeric(obj.optString("priceChangePercent")) ?: 0.0
                if (price > 0) {
                    map[base] = ExchangeQuote(priceToman = null, priceUsd = price, changePct = change)
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun fetchNobitexOrderbook(symbol: String): Double? {
        val raw = executeGet("https://api.nobitex.ir/v2/orderbook/$symbol") ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("status") != "ok") return null
            val lastRial = parseNumeric(obj.optString("lastTradePrice")) ?: return null
            lastRial / 10.0
        }.getOrNull()
    }

    private fun fetchOpenExchangeRates(): Map<String, Double> {
        val raw = executeGet("https://open.er-api.com/v6/latest/USD") ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val rates = obj.optJSONObject("rates") ?: return emptyMap()
            val map = mutableMapOf<String, Double>()
            val keys = rates.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = rates.optDouble(k, -1.0)
                if (v > 0.0) map[k] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun fetchFrankfurterRates(): Map<String, Double> {
        val raw = executeGet("https://api.frankfurter.app/latest?from=USD") ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val rates = obj.optJSONObject("rates") ?: return emptyMap()
            val map = mutableMapOf<String, Double>()
            val keys = rates.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = rates.optDouble(k, -1.0)
                if (v > 0.0) map[k] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun fetchNobitexUdf(
        symbol: String,
        resolution: String,
        fromSec: Long,
        toSec: Long
    ): List<AssetHistoryPoint> {
        val url = "https://apiv2.nobitex.ir/market/udf/history?symbol=$symbol&resolution=$resolution&from=$fromSec&to=$toSec"
        val raw = executeGet(url) ?: return emptyList()
        return runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("s") != "ok") return emptyList()
            val tArr = obj.optJSONArray("t") ?: return emptyList()
            val cArr = obj.optJSONArray("c") ?: return emptyList()
            val count = minOf(tArr.length(), cArr.length())
            val list = ArrayList<AssetHistoryPoint>(count)
            for (i in 0 until count) {
                val ts = tArr.optLong(i)
                val closeRial = cArr.optDouble(i, -1.0)
                if (ts > 0 && closeRial > 0.0) {
                    list.add(AssetHistoryPoint(ts, closeRial / 10.0))
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun fetchFrankfurterHistory(
        targetSymbol: String,
        fromSec: Long,
        toSec: Long
    ): List<Pair<Long, Double>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startStr = sdf.format(Date(fromSec * 1000L))
        val endStr = sdf.format(Date(toSec * 1000L))
        val url = "https://api.frankfurter.app/$startStr..$endStr?from=USD&to=$targetSymbol"
        val raw = executeGet(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val rates = root.optJSONObject("rates") ?: return emptyList()
            val dates = rates.keys().asSequence().toList().sorted()
            dates.mapNotNull { dateKey ->
                val dayObj = rates.optJSONObject(dateKey) ?: return@mapNotNull null
                val valRate = dayObj.optDouble(targetSymbol, -1.0)
                if (valRate <= 0.0) return@mapNotNull null
                val ts = (sdf.parse(dateKey)?.time ?: 0L) / 1000L
                ts to valRate
            }
        }.getOrDefault(emptyList())
    }

    private fun parseNumeric(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun buildSparkline(current: Double, changePct: Double, seed: Int): List<Double> {
        val count = 16
        val start = current / (1.0 + (changePct / 100.0).coerceIn(-0.4, 0.4))
        val span = current - start
        val amp = maxOf(current * 0.004, abs(span) * 0.25)
        return (0 until count).map { idx ->
            val t = idx.toDouble() / (count - 1)
            if (idx == count - 1) current
            else {
                val wave = sin(t * 9.0 + (seed % 7)) * amp * 0.45 +
                    cos(t * 4.5 + (seed % 5)) * amp * 0.25
                (start + span * t + wave).coerceAtLeast(current * 0.5)
            }
        }
    }

    private fun buildAnchoredHistory(
        asset: MarketAsset,
        range: TimeRange,
        fromSec: Long,
        nowSec: Long
    ): List<AssetHistoryPoint> {
        val count = when (range) {
            TimeRange.H24 -> 24
            TimeRange.D7 -> 28
            TimeRange.M1 -> 30
            TimeRange.Y1 -> 36
        }
        val rangeScale = when (range) {
            TimeRange.H24 -> 1.0
            TimeRange.D7 -> 2.4
            TimeRange.M1 -> 4.5
            TimeRange.Y1 -> 12.0
        }
        val pct = (asset.change24hPercent * rangeScale).coerceIn(-45.0, 80.0)
        val start = asset.priceToman / (1.0 + pct / 100.0)
        val diff = asset.priceToman - start
        val amp = maxOf(asset.priceToman * 0.006 * rangeScale, abs(diff) * 0.22)
        val seed = asset.id.hashCode()
        val stepSec = (nowSec - fromSec) / (count - 1).coerceAtLeast(1)

        return (0 until count).map { i ->
            val t = i.toDouble() / (count - 1)
            val ts = fromSec + i * stepSec
            val price = if (i == count - 1) {
                asset.priceToman
            } else {
                val wave = sin(t * 11.0 + (seed % 11)) * amp * 0.5 +
                    cos(t * 5.3 + (seed % 7)) * amp * 0.3
                (start + diff * t + wave).coerceAtLeast(asset.priceToman * 0.25)
            }
            AssetHistoryPoint(ts, price)
        }
    }
}
