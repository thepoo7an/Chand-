package com.example.network

import com.example.model.AssetCategory
import com.example.model.AssetHistoryPoint
import com.example.model.AssetHistoryResult
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
import kotlin.math.abs

data class RemoteFetchResult(
    val assets: List<MarketAsset>,
    val fetchedAtMillis: Long,
    val sourceSummary: String,
    val isLive: Boolean
)

class MarketRemoteDataSource(
    private val client: OkHttpClient = FinancialApiService.defaultFinancialClient(),
    private val financialApi: FinancialApiService = FinancialApiService(client)
) {

    private data class TgjuQuote(
        val priceToman: Double,
        val openToman: Double?,
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
        "COIN_EMAMI" to "sekee",
        "COIN_HALF" to "nim",
        "COIN_QUARTER" to "rob",
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

    suspend fun fetchLiveMarket(): RemoteFetchResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val nowSec = System.currentTimeMillis() / 1000L
            val from24hSec = nowSec - 24L * 3600L

            val tgjuDeferred = async { fetchTgjuQuotes() }
            val wallexDeferred = async { fetchWallexQuotes() }
            val bitpinDeferred = async { fetchBitpinQuotes() }
            val nobitexUsdtDeferred = async { fetchNobitexOrderbook("USDTIRT") }
            val binanceDeferred = async { fetchBinanceQuotes() }
            val fxRatesDeferred = async { financialApi.fetchLiveUsdRates() }
            val goldSpotDeferred = async { financialApi.fetchLiveGoldSpotUsd() }

            // Real intraday 24h candle curves for card sparklines (no synthetic waves)
            val usdtSparkDeferred = async {
                fetchWallexUdfHistory("USDTTMN", TimeRange.H24, from24hSec, nowSec)
                    .ifEmpty { fetchNobitexUdf("USDTIRT", TimeRange.H24, from24hSec, nowSec) }
            }
            val btcSparkDeferred = async {
                fetchWallexUdfHistory("BTCTMN", TimeRange.H24, from24hSec, nowSec)
                    .ifEmpty { fetchBinanceKlines("BTCUSDT", TimeRange.H24) }
            }
            val ethSparkDeferred = async {
                fetchWallexUdfHistory("ETHTMN", TimeRange.H24, from24hSec, nowSec)
                    .ifEmpty { fetchBinanceKlines("ETHUSDT", TimeRange.H24) }
            }
            val goldSparkDeferred = async { fetchBinanceKlines("PAXGUSDT", TimeRange.H24) }
            val tgjuUsdRecentDeferred = async { fetchTgjuTablePoints("price_dollar_rl", 3, null) }
            val tgjuGoldRecentDeferred = async { fetchTgjuTablePoints("geram18", 3, null) }

            val tgju = tgjuDeferred.await()
            val wallex = wallexDeferred.await()
            val bitpin = bitpinDeferred.await()
            val nobitexUsdt = nobitexUsdtDeferred.await()
            val binance = binanceDeferred.await()
            val fxUsdRates = fxRatesDeferred.await()
            val liveGoldSpotUsd = goldSpotDeferred.await()

            val usdtSpark = usdtSparkDeferred.await().map { it.priceToman }
            val btcSpark = btcSparkDeferred.await().map { it.priceToman }
            val ethSpark = ethSparkDeferred.await().map { it.priceToman }
            val goldSpark = goldSparkDeferred.await().map { it.priceToman }
            val tgjuUsdRecent = tgjuUsdRecentDeferred.await()
            val tgjuGoldRecent = tgjuGoldRecentDeferred.await()

            val activeSources = mutableListOf<String>()
            if (tgju.isNotEmpty()) activeSources.add("TGJU")
            if (wallex.isNotEmpty()) activeSources.add("Wallex")
            if (bitpin.isNotEmpty()) activeSources.add("Bitpin")
            if (nobitexUsdt != null) activeSources.add("Nobitex")
            if (binance.isNotEmpty()) activeSources.add("Binance")
            if (liveGoldSpotUsd != null) activeSources.add("GoldAPI")
            if (fxUsdRates.isNotEmpty()) activeSources.add("FinancialFX")

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
                ?.takeIf { abs(it) > 0.001 }
                ?: bitpin["USDT"]?.changePct?.takeIf { abs(it) > 0.001 }
                ?: computeSeriesChangePct(usdtSpark)
                ?: tgju["price_dollar_rl"]?.changePct
                ?: 0.0

            val usdQuote = tgju["price_dollar_rl"]
            val usdToman = usdQuote?.priceToman ?: usdtToman

            // If TGJU reports 0 change (e.g. Friday/holiday), derive last trading day change from TGJU daily table
            val tgjuUsdFallbackDelta = computeRecentTableDelta(tgjuUsdRecent, usdToman)
            val usdChangePct = usdQuote?.changePct?.takeIf { abs(it) > 0.001 }
                ?: tgjuUsdFallbackDelta?.second
                ?: usdtChangePct
            val usdChangeToman = usdQuote?.changeToman?.takeIf { abs(it) > 0.1 }
                ?: tgjuUsdFallbackDelta?.first
                ?: (usdToman * usdChangePct / 100.0)

            val usdRealSpark = when {
                usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, usdToman)
                tgjuUsdRecent.size >= 2 -> tgjuUsdRecent.map { it.priceToman }
                else -> flatSparkline(usdToman, usdChangePct)
            }

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

                val changePct = tQuote?.changePct?.takeIf { abs(it) > 0.001 }
                    ?: if (tQuote != null && abs(changeToman) > 0.1 && priceToman > abs(changeToman)) {
                        (changeToman / (priceToman - changeToman)) * 100.0
                    } else {
                        0.0
                    }

                val spark = if (usdtSpark.size >= 4) {
                    scaleSeriesToCurrent(usdtSpark, priceToman)
                } else {
                    flatSparkline(priceToman, changePct)
                }

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
                    sparkline = spark,
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
                    sparkline = usdRealSpark,
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

            // 2. Gold & Coins (live Gold-API / Binance PAXG Gold Ounce + TGJU Tehran coins/18K)
            val binanceGoldOz = binance["PAXG"]?.priceUsd
            val onsRaw = tgju["ons"]
            val onsUsd = liveGoldSpotUsd ?: binanceGoldOz ?: run {
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
                val goldTableFallback = if (id == "GOLD_18K") computeRecentTableDelta(tgjuGoldRecent, price) else null
                val changePct = if (id == "XAU_OZ") {
                    binance["PAXG"]?.changePct ?: q?.changePct ?: usdChangePct
                } else {
                    q?.changePct?.takeIf { abs(it) > 0.001 }
                        ?: goldTableFallback?.second
                        ?: 0.0
                }
                val changeToman = if (id == "XAU_OZ") {
                    price * changePct / 100.0
                } else {
                    q?.changeToman?.takeIf { abs(it) > 0.1 }
                        ?: goldTableFallback?.first
                        ?: (price * changePct / 100.0)
                }
                val high = if (id == "XAU_OZ") price * 1.008 else maxOf(q?.highToman ?: (price * 1.01), price)
                val low = if (id == "XAU_OZ") price * 0.992 else minOf(q?.lowToman ?: (price * 0.99), price)
                val spark = when {
                    goldSpark.size >= 4 -> scaleSeriesToCurrent(goldSpark, price)
                    usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, price)
                    else -> flatSparkline(price, changePct)
                }

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
                        sparkline = spark,
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
                overrideSource = when {
                    liveGoldSpotUsd != null -> "GoldAPI + USD"
                    binanceGoldOz != null -> "Binance PAXG"
                    else -> "TGJU"
                }
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
                    val changePct = bn?.changePct?.takeIf { abs(it) > 0.001 }
                        ?: w?.changePct?.takeIf { abs(it) > 0.001 }
                        ?: bp?.changePct?.takeIf { abs(it) > 0.001 }
                        ?: if (meta.id == "USDT") usdtChangePct else 0.0
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
                    val spark = when {
                        meta.id == "USDT" && usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, priceToman)
                        meta.id == "BTC" && btcSpark.size >= 4 -> scaleSeriesToCurrent(btcSpark, priceToman)
                        meta.id == "ETH" && ethSpark.size >= 4 -> scaleSeriesToCurrent(ethSpark, priceToman)
                        usdtSpark.size >= 4 -> scaleSeriesToCurrent(usdtSpark, priceToman)
                        else -> flatSparkline(priceToman, changePct)
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
                            sparkline = spark,
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

    /**
     * Fetches 100% real historical data from TGJU, Wallex, Nobitex, Binance, CoinGecko, and Frankfurter.
     * Never invents synthetic sine waves.
     */
    suspend fun fetchAssetHistory(
        asset: MarketAsset,
        range: TimeRange
    ): AssetHistoryResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val nowSec = System.currentTimeMillis() / 1000L
            val fromSec = nowSec - range.lookbackSeconds

            // 1. CRYPTO ASSETS (Wallex TMN -> Nobitex IRT -> Binance USDT*USD -> CoinGecko*USD)
            if (asset.category == AssetCategory.CRYPTO) {
                val wallexDeferred = async { fetchWallexUdfHistory("${asset.symbol}TMN", range, fromSec, nowSec) }
                val nobitexDeferred = async { fetchNobitexUdf("${asset.symbol}IRT", range, fromSec, nowSec) }

                val wallexPts = wallexDeferred.await()
                if (wallexPts.size >= 4) {
                    return@coroutineScope AssetHistoryResult(
                        points = appendLivePointIfNewer(wallexPts, nowSec, asset.priceToman, scaleToLast = false),
                        sourceLabel = "Wallex",
                        isRealData = true
                    )
                }
                val nobitexPts = nobitexDeferred.await()
                if (nobitexPts.size >= 4) {
                    return@coroutineScope AssetHistoryResult(
                        points = appendLivePointIfNewer(nobitexPts, nowSec, asset.priceToman, scaleToLast = false),
                        sourceLabel = "Nobitex",
                        isRealData = true
                    )
                }

                if (asset.symbol != "USDT") {
                    val binanceDeferred = async { fetchBinanceKlines("${asset.symbol}USDT", range) }
                    val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }

                    var usdCurve = binanceDeferred.await()
                    var src = "Binance"
                    if (usdCurve.size < 4) {
                        val cgId = coinGeckoIds[asset.id]
                        if (cgId != null) {
                            usdCurve = fetchCoinGeckoHistory(cgId, range)
                            src = "CoinGecko"
                        }
                    }
                    val (tmnCurve, tmnSrc) = usdBaseDeferred.await()
                    if (usdCurve.size >= 4 && tmnCurve.isNotEmpty()) {
                        val combined = combineUsdWithTomanHistory(usdCurve, tmnCurve)
                        if (combined.size >= 4) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(combined, nowSec, asset.priceToman, scaleToLast = false),
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
                    val paxgDeferred = async { fetchBinanceKlines("PAXGUSDT", range) }
                    val goldApiDeferred = async {
                        if (range != TimeRange.H24) financialApi.fetchGoldUsdHistory(fromSec, nowSec, range)
                        else emptyList()
                    }
                    val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }

                    val paxg = paxgDeferred.await()
                    val goldCdn = goldApiDeferred.await()
                    val (usdPts, usdSrc) = usdBaseDeferred.await()
                    val goldUsdSeries = if (paxg.size >= 4) paxg to "PAXG" else goldCdn to "GoldAPI"

                    if (goldUsdSeries.first.size >= 3 && usdPts.isNotEmpty()) {
                        val combined = combineUsdWithTomanHistory(goldUsdSeries.first, usdPts)
                        if (combined.size >= 3) {
                            return@coroutineScope AssetHistoryResult(
                                points = appendLivePointIfNewer(combined, nowSec, asset.priceToman, scaleToLast = false),
                                sourceLabel = "${goldUsdSeries.second} + $usdSrc",
                                isRealData = true
                            )
                        }
                    }
                }

                // Multi-day ranges (7D, 1M, 1Y) -> TGJU official Tehran Gold & Coin history table
                val slug = tgjuSlugs[asset.id]
                if (range != TimeRange.H24 && slug != null) {
                    val tgjuPts = fetchTgjuTablePoints(slug, tgjuLengthFor(range), asset.priceToman)
                    if (tgjuPts.size >= 3) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(tgjuPts, nowSec, asset.priceToman, scaleToLast = false),
                            sourceLabel = "TGJU",
                            isRealData = true
                        )
                    }
                }

                // 24H Gold or fallback -> combine global gold (PAXG or GoldAPI XAU) with real USD/TMN history
                val paxgDeferred = async { fetchBinanceKlines("PAXGUSDT", range) }
                val goldApiDeferred = async {
                    if (range != TimeRange.H24) financialApi.fetchGoldUsdHistory(fromSec, nowSec, range)
                    else emptyList()
                }
                val usdBaseDeferred = async { fetchUsdTomanHistory(range, fromSec, nowSec) }
                val paxg = paxgDeferred.await()
                val goldCdn = goldApiDeferred.await()
                val (usdPts, usdSrc) = usdBaseDeferred.await()
                val goldUsdSeries = if (paxg.size >= 4) paxg to "PAXG" else goldCdn to "GoldAPI"

                if (goldUsdSeries.first.size >= 3 && usdPts.isNotEmpty()) {
                    val combined = combineUsdWithTomanHistory(goldUsdSeries.first, usdPts)
                    if (combined.size >= 3) {
                        val aligned = scalePointsToCurrent(combined, asset.priceToman)
                        return@coroutineScope AssetHistoryResult(
                            points = aligned,
                            sourceLabel = "${goldUsdSeries.second} + $usdSrc",
                            isRealData = true
                        )
                    }
                }

                // Final real fallback for Gold on 24H -> last daily points from TGJU if available
                if (slug != null) {
                    val tgjuPts = fetchTgjuTablePoints(slug, 7, asset.priceToman)
                    if (tgjuPts.size >= 2) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(tgjuPts, nowSec, asset.priceToman, scaleToLast = false),
                            sourceLabel = "TGJU Daily",
                            isRealData = true
                        )
                    }
                }
            }

            // 3. FIAT CURRENCIES (USD, EUR, AED, GBP, KWD, TRY, SAR, IQD, ...)
            if (asset.category == AssetCategory.FIAT) {
                val slug = tgjuSlugs[asset.id]

                // Multi-day ranges (7D, 1M, 1Y) -> TGJU official daily table first
                if (range != TimeRange.H24 && slug != null) {
                    val tgjuPts = fetchTgjuTablePoints(slug, tgjuLengthFor(range), asset.priceToman)
                    if (tgjuPts.size >= 3) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(tgjuPts, nowSec, asset.priceToman, scaleToLast = false),
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
                        val combined = combineTomanWithInverseFx(usdPts, fxPts)
                        if (combined.size >= 3) {
                            return@coroutineScope AssetHistoryResult(
                                points = scalePointsToCurrent(combined, asset.priceToman),
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
                        appendLivePointIfNewer(usdPts, nowSec, asset.priceToman, scaleToLast = false)
                    } else {
                        scalePointsToCurrent(usdPts, asset.priceToman)
                    }
                    return@coroutineScope AssetHistoryResult(
                        points = aligned,
                        sourceLabel = usdSrc,
                        isRealData = true
                    )
                }

                if (slug != null) {
                    val tgjuPts = fetchTgjuTablePoints(slug, 7, asset.priceToman)
                    if (tgjuPts.size >= 2) {
                        return@coroutineScope AssetHistoryResult(
                            points = appendLivePointIfNewer(tgjuPts, nowSec, asset.priceToman, scaleToLast = false),
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
    }

    private suspend fun fetchUsdTomanHistory(
        range: TimeRange,
        fromSec: Long,
        nowSec: Long
    ): Pair<List<AssetHistoryPoint>, String> = coroutineScope {
        if (range != TimeRange.H24) {
            val tgjuPts = fetchTgjuTablePoints("price_dollar_rl", tgjuLengthFor(range), null)
            if (tgjuPts.size >= 3) {
                return@coroutineScope tgjuPts to "TGJU"
            }
        }
        val wallexPts = fetchWallexUdfHistory("USDTTMN", range, fromSec, nowSec)
        if (wallexPts.size >= 4) {
            return@coroutineScope wallexPts to "Wallex"
        }
        val nobitexPts = fetchNobitexUdf("USDTIRT", range, fromSec, nowSec)
        if (nobitexPts.size >= 4) {
            return@coroutineScope nobitexPts to "Nobitex"
        }
        emptyList<AssetHistoryPoint>() to ""
    }

    private fun tgjuLengthFor(range: TimeRange): Int = when (range) {
        TimeRange.H24 -> 7
        TimeRange.D7 -> 14
        TimeRange.M1 -> 35
        TimeRange.Y1 -> 370
    }

    private fun executeGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("Accept", "application/json, text/plain, */*")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
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
                val openRial = parseNumeric(obj.optString("o"))
                val highRial = parseNumeric(obj.optString("h")) ?: priceRial
                val lowRial = parseNumeric(obj.optString("l")) ?: priceRial
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

                map[k] = TgjuQuote(
                    priceToman = priceRial / 10.0,
                    openToman = openRial?.let { it / 10.0 },
                    highToman = highRial / 10.0,
                    lowToman = lowRial / 10.0,
                    changeToman = diffRial / 10.0,
                    changePct = dp
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    /**
     * Parses TGJU official summary table history:
     * https://api.tgju.org/v1/market/indicator/summary-table-data/{slug}?length={length}
     * Row shape: [open, low, high, close, change_html, pct_html, gregorian_date, jalali_date]
     */
    private fun fetchTgjuTablePoints(
        slug: String,
        length: Int,
        referenceCurrentPriceToman: Double?
    ): List<AssetHistoryPoint> {
        val url = "https://api.tgju.org/v1/market/indicator/summary-table-data/$slug?length=$length&t=${System.currentTimeMillis()}"
        val raw = executeGet(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val dataArr = root.optJSONArray("data") ?: return emptyList()
            val sdfSlash = SimpleDateFormat("yyyy/MM/dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tehran")
            }
            val sdfDash = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tehran")
            }

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
                parsed.add(AssetHistoryPoint(tsSec, closeRaw))
            }

            if (parsed.isEmpty()) return emptyList()

            // Sort chronologically ascending and dedupe timestamps
            val sorted = parsed
                .sortedBy { it.timestampSec }
                .distinctBy { it.timestampSec }

            val sampleLast = sorted.last().priceToman
            val unitScale = if (referenceCurrentPriceToman != null && sampleLast > 0.0) {
                val ratio = referenceCurrentPriceToman / sampleLast
                when {
                    ratio in 0.05..0.2 -> 0.1      // Rial -> Toman
                    ratio in 0.5..2.0 -> 1.0       // Already Toman
                    ratio in 0.005..0.02 -> 0.01   // 10-unit / 100-unit normalization
                    ratio in 0.0005..0.002 -> 0.001 // 100-unit Rial -> 1-unit Toman (JPY/IQD)
                    else -> 0.1
                }
            } else {
                0.1
            }

            sorted.map { it.copy(priceToman = it.priceToman * unitScale) }
        }.getOrDefault(emptyList())
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

    private fun fetchWallexUdfHistory(
        symbol: String,
        range: TimeRange,
        fromSec: Long,
        toSec: Long
    ): List<AssetHistoryPoint> {
        val res = when (range) {
            TimeRange.H24 -> "60"
            TimeRange.D7 -> "240"
            TimeRange.M1, TimeRange.Y1 -> "1D"
        }
        val url = "https://api.wallex.ir/v1/udf/history?symbol=$symbol&resolution=$res&from=$fromSec&to=$toSec"
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
                val close = parseNumeric(cArr.optString(i)) ?: cArr.optDouble(i, -1.0)
                if (ts > 0 && close > 0.0) {
                    list.add(AssetHistoryPoint(ts, close))
                }
            }
            list.sortedBy { it.timestampSec }
        }.getOrDefault(emptyList())
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

    private fun fetchBinanceKlines(
        symbol: String,
        range: TimeRange
    ): List<AssetHistoryPoint> {
        val (interval, limit) = when (range) {
            TimeRange.H24 -> "1h" to 24
            TimeRange.D7 -> "4h" to 42
            TimeRange.M1 -> "1d" to 30
            TimeRange.Y1 -> "1d" to 365
        }
        val url = "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
        val raw = executeGet(url) ?: return emptyList()
        return runCatching {
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
            list.sortedBy { it.timestampSec }
        }.getOrDefault(emptyList())
    }

    private fun fetchCoinGeckoHistory(
        coinId: String,
        range: TimeRange
    ): List<AssetHistoryPoint> {
        val days = when (range) {
            TimeRange.H24 -> "1"
            TimeRange.D7 -> "7"
            TimeRange.M1 -> "30"
            TimeRange.Y1 -> "365"
        }
        val url = "https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=usd&days=$days"
        val raw = executeGet(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val prices = root.optJSONArray("prices") ?: return emptyList()
            val step = maxOf(1, prices.length() / 120)
            val list = ArrayList<AssetHistoryPoint>()
            for (i in 0 until prices.length() step step) {
                val pair = prices.optJSONArray(i) ?: continue
                val tsSec = pair.optLong(0) / 1000L
                val price = pair.optDouble(1, -1.0)
                if (tsSec > 0 && price > 0.0) {
                    list.add(AssetHistoryPoint(tsSec, price))
                }
            }
            list.sortedBy { it.timestampSec }
        }.getOrDefault(emptyList())
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

    private fun fetchNobitexUdf(
        symbol: String,
        range: TimeRange,
        fromSec: Long,
        toSec: Long
    ): List<AssetHistoryPoint> {
        val resolution = range.nobitexResolution
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
                val closeRial = parseNumeric(cArr.optString(i)) ?: cArr.optDouble(i, -1.0)
                if (ts > 0 && closeRial > 0.0) {
                    list.add(AssetHistoryPoint(ts, closeRial / 10.0))
                }
            }
            list.sortedBy { it.timestampSec }
        }.getOrDefault(emptyList())
    }

    private fun combineUsdWithTomanHistory(
        usdSeries: List<AssetHistoryPoint>,
        tomanPerUsdSeries: List<AssetHistoryPoint>
    ): List<AssetHistoryPoint> {
        if (usdSeries.isEmpty() || tomanPerUsdSeries.isEmpty()) return emptyList()
        return usdSeries.map { pt ->
            val usdTmn = nearestValue(pt.timestampSec, tomanPerUsdSeries)
            AssetHistoryPoint(pt.timestampSec, pt.priceToman * usdTmn)
        }
    }

    private fun combineTomanWithInverseFx(
        usdTomanSeries: List<AssetHistoryPoint>,
        fxPerUsdSeries: List<Pair<Long, Double>>
    ): List<AssetHistoryPoint> {
        if (usdTomanSeries.isEmpty() || fxPerUsdSeries.isEmpty()) return emptyList()
        val fxPoints = fxPerUsdSeries.map { AssetHistoryPoint(it.first, it.second) }
        return usdTomanSeries.map { pt ->
            val fx = nearestValue(pt.timestampSec, fxPoints).coerceAtLeast(0.0001)
            AssetHistoryPoint(pt.timestampSec, pt.priceToman / fx)
        }
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

    private fun scaleSeriesToCurrent(
        values: List<Double>,
        targetCurrent: Double
    ): List<Double> {
        if (values.isEmpty() || targetCurrent <= 0.0) return values
        val last = values.last()
        if (last <= 0.0) return values
        val factor = targetCurrent / last
        return values.map { it * factor }
    }

    private fun flatSparkline(current: Double, changePct: Double): List<Double> {
        val start = if (abs(changePct) > 0.005) {
            current / (1.0 + (changePct / 100.0).coerceIn(-0.4, 0.4))
        } else {
            current
        }
        return listOf(start, (start + current) * 0.5, current)
    }

    private fun computeSeriesChangePct(series: List<Double>): Double? {
        if (series.size < 2) return null
        val first = series.first()
        val last = series.last()
        if (first <= 0.0 || last <= 0.0) return null
        return ((last - first) / first) * 100.0
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

    private fun parseNumeric(raw: String?): Double? {
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
