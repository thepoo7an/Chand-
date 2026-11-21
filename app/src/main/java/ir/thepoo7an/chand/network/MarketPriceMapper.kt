package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Pure, deterministic price-mapping & normalization engine for Chand?!.
 *
 * Primary Toman benchmark: **Tehran Free Market (نرخ آزاد بازار تهران)** via TGJU (`price_dollar_rl`),
 * with 24/7 median Tether (`Wallex` / `Bitpin` / `Nobitex`) as real-time free-market backup, and
 * last cached USD/TMN anchor when only global FX/Crypto APIs are reachable.
 */
object MarketPriceMapper {

    const val DEFAULT_OFFLINE_USD_TOMAN = 68_500.0
    const val DEFAULT_OFFLINE_ONS_USD = 2_650.0

    data class TgjuQuote(
        val priceToman: Double,
        val openToman: Double?,
        val highToman: Double,
        val lowToman: Double,
        val changeToman: Double,
        val changePct: Double
    )

    data class ExchangeQuote(
        val priceToman: Double?,
        val priceUsd: Double?,
        val changePct: Double
    )

    data class ResolvedUsdRate(
        val usdToman: Double,
        val usdtToman: Double,
        val usdChangePct: Double,
        val usdChangeToman: Double,
        val usdtChangePct: Double,
        val rateBasis: TomanRateBasis,
        val usdSourceLabel: String
    )

    data class FiatSpec(
        val id: String,
        val symbol: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val accentHex: Long,
        val tgjuKey: String,
        val fxCode: String
    )

    data class CryptoSpec(
        val id: String,
        val nameFa: String,
        val nameEn: String,
        val badge: String,
        val accentHex: Long
    )

    val SUPPORTED_FIATS = listOf(
        FiatSpec("EUR", "EUR", "یورو", "Euro", "🇪🇺", 0xFF3B82F6, "price_eur", "EUR"),
        FiatSpec("AED", "AED", "درهم امارات", "UAE Dirham", "🇦🇪", 0xFF14B8A6, "price_aed", "AED"),
        FiatSpec("GBP", "GBP", "پوند انگلیس", "British Pound", "🇬🇧", 0xFF6366F1, "price_gbp", "GBP"),
        FiatSpec("KWD", "KWD", "دینار کویت", "Kuwaiti Dinar", "🇰🇼", 0xFF059669, "price_kwd", "KWD"),
        FiatSpec("TRY", "TRY", "لیر ترکیه", "Turkish Lira", "🇹🇷", 0xFFEF4444, "price_try", "TRY"),
        FiatSpec("CAD", "CAD", "دلار کانادا", "Canadian Dollar", "🇨🇦", 0xFFF43F5E, "price_cad", "CAD"),
        FiatSpec("CHF", "CHF", "فرانک سوئیس", "Swiss Franc", "🇨🇭", 0xFFEC4899, "price_chf", "CHF"),
        FiatSpec("CNY", "CNY", "یوان چین", "Chinese Yuan", "🇨🇳", 0xFFF59E0B, "price_cny", "CNY"),
        FiatSpec("AUD", "AUD", "دلار استرالیا", "Australian Dollar", "🇦🇺", 0xFF06B6D4, "price_aud", "AUD"),
        FiatSpec("JPY", "JPY", "ین ژاپن", "Japanese Yen", "🇯🇵", 0xFFFB7185, "price_jpy", "JPY"),
        FiatSpec("SAR", "SAR", "ریال عربستان", "Saudi Riyal", "🇸🇦", 0xFF22C55E, "price_sar", "SAR"),
        FiatSpec("IQD", "IQD", "دینار عراق", "Iraqi Dinar", "🇮🇶", 0xFF84CC16, "price_iqd", "IQD")
    )

    val SUPPORTED_CRYPTOS = listOf(
        CryptoSpec("USDT", "تتر", "Tether", "₮", 0xFF26A17B),
        CryptoSpec("BTC", "بیت‌کوین", "Bitcoin", "₿", 0xFFF7931A),
        CryptoSpec("ETH", "اتریوم", "Ethereum", "Ξ", 0xFF627EEA),
        CryptoSpec("TON", "تون‌کوین", "Toncoin", "◆", 0xFF0098EA),
        CryptoSpec("SOL", "سولانا", "Solana", "◎", 0xFF9945FF),
        CryptoSpec("BNB", "بایننس‌کوین", "BNB", "◈", 0xFFF3BA2F),
        CryptoSpec("XRP", "ریپل", "XRP", "✕", 0xFF38BDF8),
        CryptoSpec("DOGE", "دوج‌کوین", "Dogecoin", "Ð", 0xFFC2A633)
    )

    fun computeUsdtMedianToman(
        wallexUsdtToman: Double?,
        bitpinUsdtToman: Double?,
        nobitexUsdtToman: Double?
    ): Double? {
        val candidates = listOfNotNull(wallexUsdtToman, bitpinUsdtToman, nobitexUsdtToman)
            .filter { it > 1_000.0 }
            .sorted()
        return when {
            candidates.size >= 3 -> candidates[candidates.size / 2]
            candidates.isNotEmpty() -> candidates.average()
            else -> null
        }
    }

    fun resolveFreeMarketUsd(
        tgjuUsdQuote: TgjuQuote?,
        wallexUsdt: ExchangeQuote?,
        bitpinUsdt: ExchangeQuote?,
        nobitexUsdtToman: Double?,
        usdtSpark: List<Double>,
        tgjuTableFallbackDelta: Pair<Double, Double>?,
        lastKnownUsdToman: Double?
    ): ResolvedUsdRate {
        val usdtMedian = computeUsdtMedianToman(
            wallexUsdt?.priceToman,
            bitpinUsdt?.priceToman,
            nobitexUsdtToman
        )
        val anchorUsd = lastKnownUsdToman?.takeIf { it > 1_000.0 } ?: DEFAULT_OFFLINE_USD_TOMAN

        val usdtToman = usdtMedian ?: tgjuUsdQuote?.priceToman ?: anchorUsd
        val usdtChangePct = wallexUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpinUsdt?.changePct?.takeIf { abs(it) > 0.001 }
            ?: computeSeriesChangePct(usdtSpark)
            ?: tgjuUsdQuote?.changePct
            ?: 0.0

        val (usdToman, basis, sourceLabel) = when {
            tgjuUsdQuote != null -> Triple(
                tgjuUsdQuote.priceToman,
                TomanRateBasis.TEHRAN_FREE_MARKET,
                "TGJU (Free)"
            )
            usdtMedian != null -> Triple(
                usdtMedian,
                TomanRateBasis.USDT_FREE_MARKET_MEDIAN,
                "USDT Median (Free)"
            )
            lastKnownUsdToman != null && lastKnownUsdToman > 1_000.0 -> Triple(
                lastKnownUsdToman,
                TomanRateBasis.CACHED_USD_ANCHOR,
                "Cached USD Anchor"
            )
            else -> Triple(
                DEFAULT_OFFLINE_USD_TOMAN,
                TomanRateBasis.OFFLINE_REFERENCE,
                "Offline Reference"
            )
        }

        val usdChangePct = tgjuUsdQuote?.changePct?.takeIf { abs(it) > 0.001 }
            ?: tgjuTableFallbackDelta?.second
            ?: usdtChangePct
        val usdChangeToman = tgjuUsdQuote?.changeToman?.takeIf { abs(it) > 0.1 }
            ?: tgjuTableFallbackDelta?.first
            ?: (usdToman * usdChangePct / 100.0)

        return ResolvedUsdRate(
            usdToman = usdToman,
            usdtToman = usdtToman,
            usdChangePct = usdChangePct,
            usdChangeToman = usdChangeToman,
            usdtChangePct = usdtChangePct,
            rateBasis = basis,
            usdSourceLabel = sourceLabel
        )
    }

    fun mapFiatAsset(
        spec: FiatSpec,
        usdToman: Double,
        usdChangePct: Double,
        tgjuQuote: TgjuQuote?,
        fxPerUsd: Double?,
        usdtSpark: List<Double>,
        nowMillis: Long
    ): MarketAsset? {
        val crossPriceToman = if (fxPerUsd != null && fxPerUsd > 0.0) usdToman / fxPerUsd else null
        var priceToman = tgjuQuote?.priceToman ?: crossPriceToman ?: return null
        var highToman = tgjuQuote?.highToman ?: (priceToman * 1.006)
        var lowToman = tgjuQuote?.lowToman ?: (priceToman * 0.994)
        var changeToman = tgjuQuote?.changeToman ?: (priceToman * usdChangePct / 100.0)

        // Normalize 100-unit (JPY) and 1000-unit (IQD) TGJU quotes against real FX cross-rate
        if (crossPriceToman != null && crossPriceToman > 0.0) {
            val ratio = priceToman / crossPriceToman
            if (ratio in 50.0..200.0) {
                priceToman /= 100.0
                highToman /= 100.0
                lowToman /= 100.0
                changeToman /= 100.0
            } else if (ratio in 500.0..2000.0) {
                priceToman /= 1000.0
                highToman /= 1000.0
                lowToman /= 1000.0
                changeToman /= 1000.0
            }
        }

        val changePct = tgjuQuote?.changePct?.takeIf { abs(it) > 0.001 }
            ?: if (tgjuQuote != null && abs(changeToman) > 0.1 && priceToman > abs(changeToman)) {
                (changeToman / (priceToman - changeToman)) * 100.0
            } else if (tgjuQuote == null) {
                usdChangePct
            } else {
                0.0
            }

        val spark = if (usdtSpark.size >= 4) {
            scaleSeriesToCurrent(usdtSpark, priceToman)
        } else {
            flatSparkline(priceToman, changePct)
        }

        val src = if (tgjuQuote != null) "TGJU" else "FX + USD"

        return MarketAsset(
            id = spec.id,
            symbol = spec.symbol,
            nameFa = spec.nameFa,
            nameEn = spec.nameEn,
            category = AssetCategory.FIAT,
            badgeText = spec.badge,
            accentHex = spec.accentHex,
            priceToman = priceToman,
            priceUsd = if (fxPerUsd != null && fxPerUsd > 0.0) 1.0 / fxPerUsd else null,
            change24hPercent = changePct,
            change24hToman = changeToman,
            high24hToman = maxOf(highToman, priceToman),
            low24hToman = minOf(lowToman, priceToman),
            sparkline = spark,
            sourceLabel = src,
            updatedAtMillis = nowMillis
        )
    }

    fun mapCryptoAsset(
        spec: CryptoSpec,
        usdtToman: Double,
        usdtChangePct: Double,
        wallex: ExchangeQuote?,
        bitpin: ExchangeQuote?,
        binance: ExchangeQuote?,
        nobitexUsdtToman: Double?,
        sparkInput: List<Double>,
        nowMillis: Long
    ): MarketAsset? {
        val priceUsd = if (spec.id == "USDT") 1.0 else (binance?.priceUsd ?: wallex?.priceUsd ?: bitpin?.priceUsd)
        val tmnCandidates = listOfNotNull(wallex?.priceToman, bitpin?.priceToman).filter { it > 0.0 }
        val priceToman = when {
            spec.id == "USDT" -> usdtToman
            tmnCandidates.isNotEmpty() -> tmnCandidates.average()
            priceUsd != null && priceUsd > 0.0 -> priceUsd * usdtToman
            else -> null
        } ?: return null

        val changePct = binance?.changePct?.takeIf { abs(it) > 0.001 }
            ?: wallex?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpin?.changePct?.takeIf { abs(it) > 0.001 }
            ?: if (spec.id == "USDT") usdtChangePct else 0.0
        val changeToman = priceToman * (changePct / 100.0)

        val source = when {
            spec.id == "USDT" -> listOfNotNull(
                if (wallex?.priceToman != null) "Wallex" else null,
                if (bitpin?.priceToman != null) "Bitpin" else null,
                if (nobitexUsdtToman != null) "Nobitex" else null
            ).joinToString("+").ifEmpty { "Live" }
            wallex?.priceToman != null && bitpin?.priceToman != null -> "Wallex+Bitpin"
            wallex?.priceToman != null -> "Wallex"
            bitpin?.priceToman != null -> "Bitpin"
            binance != null -> "Binance"
            else -> "Live"
        }

        val spark = if (sparkInput.size >= 4) {
            scaleSeriesToCurrent(sparkInput, priceToman)
        } else {
            flatSparkline(priceToman, changePct)
        }

        return MarketAsset(
            id = spec.id,
            symbol = spec.id,
            nameFa = spec.nameFa,
            nameEn = spec.nameEn,
            category = AssetCategory.CRYPTO,
            badgeText = spec.badge,
            accentHex = spec.accentHex,
            priceToman = priceToman,
            priceUsd = priceUsd,
            change24hPercent = changePct,
            change24hToman = changeToman,
            high24hToman = priceToman * (1.0 + maxOf(0.008, abs(changePct) / 100.0)),
            low24hToman = priceToman * (1.0 - maxOf(0.008, abs(changePct) / 100.0)),
            sparkline = spark,
            sourceLabel = source,
            updatedAtMillis = nowMillis
        )
    }

    /**
     * Last-resort offline baseline used ONLY when all live APIs fail AND no persisted cache exists
     * (e.g., cold offline first boot). Clearly surfaced with Offline Reference label & banner.
     */
    fun offlineReferenceCatalog(nowMillis: Long = System.currentTimeMillis()): CachedMarketSnapshot {
        val usd = DEFAULT_OFFLINE_USD_TOMAN
        val eurPerUsd = 0.92
        val aedPerUsd = 3.6725
        val gbpPerUsd = 0.79
        val ons = DEFAULT_OFFLINE_ONS_USD
        val gram18 = (ons / 31.1035) * usd * 0.75

        val assets = listOf(
            MarketAsset(
                id = "USD",
                symbol = "USD",
                nameFa = "دلار آمریکا",
                nameEn = "US Dollar",
                category = AssetCategory.FIAT,
                badgeText = "🇺🇸",
                accentHex = 0xFF10B981,
                priceToman = usd,
                priceUsd = 1.0,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = usd,
                low24hToman = usd,
                sparkline = listOf(usd, usd, usd),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "EUR",
                symbol = "EUR",
                nameFa = "یورو",
                nameEn = "Euro",
                category = AssetCategory.FIAT,
                badgeText = "🇪🇺",
                accentHex = 0xFF3B82F6,
                priceToman = usd / eurPerUsd,
                priceUsd = 1.0 / eurPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = usd / eurPerUsd,
                low24hToman = usd / eurPerUsd,
                sparkline = listOf(usd / eurPerUsd, usd / eurPerUsd, usd / eurPerUsd),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "AED",
                symbol = "AED",
                nameFa = "درهم امارات",
                nameEn = "UAE Dirham",
                category = AssetCategory.FIAT,
                badgeText = "🇦🇪",
                accentHex = 0xFF14B8A6,
                priceToman = usd / aedPerUsd,
                priceUsd = 1.0 / aedPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = usd / aedPerUsd,
                low24hToman = usd / aedPerUsd,
                sparkline = listOf(usd / aedPerUsd, usd / aedPerUsd, usd / aedPerUsd),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "GBP",
                symbol = "GBP",
                nameFa = "پوند انگلیس",
                nameEn = "British Pound",
                category = AssetCategory.FIAT,
                badgeText = "🇬🇧",
                accentHex = 0xFF6366F1,
                priceToman = usd / gbpPerUsd,
                priceUsd = 1.0 / gbpPerUsd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = usd / gbpPerUsd,
                low24hToman = usd / gbpPerUsd,
                sparkline = listOf(usd / gbpPerUsd, usd / gbpPerUsd, usd / gbpPerUsd),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "GOLD_18K",
                symbol = "GRAM",
                nameFa = "طلای ۱۸ عیار",
                nameEn = "Gram",
                category = AssetCategory.GOLD,
                badgeText = "✨",
                accentHex = 0xFFF59E0B,
                priceToman = gram18,
                priceUsd = gram18 / usd,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = gram18,
                low24hToman = gram18,
                sparkline = listOf(gram18, gram18, gram18),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            ),
            MarketAsset(
                id = "USDT",
                symbol = "USDT",
                nameFa = "تتر",
                nameEn = "Tether",
                category = AssetCategory.CRYPTO,
                badgeText = "₮",
                accentHex = 0xFF26A17B,
                priceToman = usd,
                priceUsd = 1.0,
                change24hPercent = 0.0,
                change24hToman = 0.0,
                high24hToman = usd,
                low24hToman = usd,
                sparkline = listOf(usd, usd, usd),
                sourceLabel = TomanRateBasis.OFFLINE_REFERENCE.labelEn,
                updatedAtMillis = nowMillis
            )
        )

        return CachedMarketSnapshot(
            assets = assets,
            updatedAtMillis = 0L,
            sourceSummary = TomanRateBasis.OFFLINE_REFERENCE.labelEn
        )
    }

    fun scaleSeriesToCurrent(values: List<Double>, targetCurrent: Double): List<Double> {
        if (values.isEmpty() || targetCurrent <= 0.0) return values
        val last = values.last()
        if (last <= 0.0) return values
        val factor = targetCurrent / last
        return values.map { it * factor }
    }

    fun flatSparkline(current: Double, changePct: Double): List<Double> {
        val start = if (abs(changePct) > 0.005) {
            current / (1.0 + (changePct / 100.0).coerceIn(-0.4, 0.4))
        } else {
            current
        }
        return listOf(start, (start + current) * 0.5, current)
    }

    fun computeSeriesChangePct(series: List<Double>): Double? {
        if (series.size < 2) return null
        val first = series.first()
        val last = series.last()
        if (first <= 0.0 || last <= 0.0) return null
        return ((last - first) / first) * 100.0
    }
}
