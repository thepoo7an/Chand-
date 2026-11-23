package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Cryptocurrency price mapping (USDT cross-exchange median and non-USDT TMN/USDT consensus).
 */
internal object CryptoPriceMapper {

    val SUPPORTED_CRYPTOS: List<MarketPriceMapper.CryptoSpec> = listOf(
        MarketPriceMapper.CryptoSpec("USDT", "تتر", "Tether", "₮", 0xFF26A17B),
        MarketPriceMapper.CryptoSpec("BTC", "بیت‌کوین", "Bitcoin", "₿", 0xFFF7931A),
        MarketPriceMapper.CryptoSpec("ETH", "اتریوم", "Ethereum", "Ξ", 0xFF627EEA),
        MarketPriceMapper.CryptoSpec("TON", "تون‌کوین", "Toncoin", "◆", 0xFF0098EA),
        MarketPriceMapper.CryptoSpec("SOL", "سولانا", "Solana", "◎", 0xFF9945FF),
        MarketPriceMapper.CryptoSpec("BNB", "بایننس‌کوین", "BNB", "◈", 0xFFF3BA2F),
        MarketPriceMapper.CryptoSpec("XRP", "ریپل", "XRP", "✕", 0xFF38BDF8),
        MarketPriceMapper.CryptoSpec("DOGE", "دوج‌کوین", "Dogecoin", "Ð", 0xFFC2A633)
    )

    fun mapCryptoAsset(
        spec: MarketPriceMapper.CryptoSpec,
        usdtToman: Double,
        usdtChangePct: Double,
        wallex: MarketPriceMapper.ExchangeQuote?,
        bitpin: MarketPriceMapper.ExchangeQuote?,
        binance: MarketPriceMapper.ExchangeQuote?,
        nobitexUsdtToman: Double?,
        sparkInput: List<Double>,
        nowMillis: Long,
        usdtBasisFreshness: DataFreshness = DataFreshness.LIVE,
        usdtSourceTimestampMillis: Long = nowMillis,
        nobitexTimestampMillis: Long = nowMillis
    ): MarketAsset? {
        if (spec.id == "USDT") {
            val usdtCandidates = listOfNotNull(
                wallex?.priceToman?.takeIf { it > 1_000.0 }?.let {
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = it,
                        isPrimary = true,
                        timestampMillis = wallex.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        changePct = wallex.changePct,
                        highToman = wallex.highToman,
                        lowToman = wallex.lowToman
                    )
                },
                bitpin?.priceToman?.takeIf { it > 1_000.0 }?.let {
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = it,
                        isPrimary = wallex?.priceToman == null,
                        timestampMillis = bitpin.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        changePct = bitpin.changePct,
                        highToman = bitpin.highToman,
                        lowToman = bitpin.lowToman
                    )
                },
                nobitexUsdtToman?.takeIf { it > 1_000.0 }?.let {
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Nobitex",
                        price = it,
                        isPrimary = wallex?.priceToman == null && bitpin?.priceToman == null,
                        timestampMillis = nobitexTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                    )
                }
            )

            val consensus = MarketValidationHelpers.validateCrossSourcePrices(
                candidates = usdtCandidates,
                thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
                nowMillis = nowMillis
            )

            val priceToman = consensus?.price ?: usdtToman.takeIf { it > 0.0 } ?: return null
            val changePct = usdtChangePct
            val changeToman = priceToman * (changePct / 100.0)
            val source = consensus?.acceptedSources?.joinToString("+")?.ifEmpty { "Live" }
                ?: listOfNotNull(
                    if (wallex?.priceToman != null) "Wallex" else null,
                    if (bitpin?.priceToman != null) "Bitpin" else null,
                    if (nobitexUsdtToman != null) "Nobitex" else null
                ).joinToString("+").ifEmpty { "Cached" }

            val sourceTs = consensus?.sourceTimestampMillis ?: usdtSourceTimestampMillis
            val directFreshness = if (consensus != null) {
                MarketValidationHelpers.deriveFreshness(
                    category = AssetCategory.CRYPTO,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = sourceTs,
                    nowMillis = nowMillis
                )
            } else {
                usdtBasisFreshness
            }
            val freshness = DataFreshness.stalest(directFreshness, usdtBasisFreshness)

            val spark = if (sparkInput.size >= 4) {
                MarketValidationHelpers.scaleSeriesToCurrent(sparkInput, priceToman)
            } else {
                emptyList()
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
                priceUsd = 1.0,
                change24hPercent = changePct,
                change24hToman = changeToman,
                high24hToman = consensus?.selectedCandidate?.highToman,
                low24hToman = consensus?.selectedCandidate?.lowToman,
                sparkline = spark,
                sourceLabel = source,
                updatedAtMillis = nowMillis,
                freshness = freshness,
                sourceTimestampMillis = sourceTs,
                fetchedAtMillis = nowMillis,
                disputed = consensus?.disputed == true,
                singleSource = consensus?.singleSource ?: true
            )
        }

        val priceUsd = binance?.priceUsd ?: wallex?.priceUsd ?: bitpin?.priceUsd
        val candidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
        wallex?.priceToman?.takeIf { it > 0.0 }?.let {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Wallex",
                    price = it,
                    isPrimary = true,
                    timestampMillis = wallex.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = wallex.changePct,
                    highToman = wallex.highToman,
                    lowToman = wallex.lowToman
                )
            )
        }
        bitpin?.priceToman?.takeIf { it > 0.0 }?.let {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Bitpin",
                    price = it,
                    isPrimary = wallex?.priceToman == null,
                    timestampMillis = bitpin.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                    changePct = bitpin.changePct,
                    highToman = bitpin.highToman,
                    lowToman = bitpin.lowToman
                )
            )
        }
        if (binance?.priceUsd != null && binance.priceUsd > 0.0 && usdtToman > 0.0) {
            candidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Binance",
                    price = binance.priceUsd * usdtToman,
                    isPrimary = wallex?.priceToman == null && bitpin?.priceToman == null,
                    timestampMillis = minOf(
                        binance.timestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis,
                        usdtSourceTimestampMillis.takeIf { ts -> ts > 0L } ?: nowMillis
                    ),
                    changePct = binance.changePct,
                    highToman = binance.highUsd?.takeIf { it > 0.0 }?.let { it * usdtToman },
                    lowToman = binance.lowUsd?.takeIf { it > 0.0 }?.let { it * usdtToman }
                )
            )
        }

        val consensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.CRYPTO_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = nowMillis
        ) ?: return null

        val priceToman = consensus.price
        val changePct = binance?.changePct?.takeIf { abs(it) > 0.001 }
            ?: wallex?.changePct?.takeIf { abs(it) > 0.001 }
            ?: bitpin?.changePct?.takeIf { abs(it) > 0.001 }
            ?: 0.0
        val changeToman = priceToman * (changePct / 100.0)

        val usesDerivedBinance = "Binance" in consensus.acceptedSources
        val directFreshness = MarketValidationHelpers.deriveFreshness(
            category = AssetCategory.CRYPTO,
            fetchedThisRefresh = true,
            hasUsableValue = true,
            isCachedSource = false,
            sourceTimestampMillis = consensus.sourceTimestampMillis,
            nowMillis = nowMillis
        )
        val freshness = if (usesDerivedBinance) {
            DataFreshness.stalest(directFreshness, usdtBasisFreshness)
        } else {
            directFreshness
        }

        val source = consensus.acceptedSources.joinToString("+").ifEmpty { "Live" }
        val highToman = consensus.selectedCandidate.highToman?.takeIf { it > 0.0 }
        val lowToman = consensus.selectedCandidate.lowToman?.takeIf { it > 0.0 }

        val spark = if (sparkInput.size >= 4) {
            MarketValidationHelpers.scaleSeriesToCurrent(sparkInput, priceToman)
        } else {
            emptyList()
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
            high24hToman = highToman,
            low24hToman = lowToman,
            sparkline = spark,
            sourceLabel = source,
            updatedAtMillis = nowMillis,
            freshness = freshness,
            sourceTimestampMillis = consensus.sourceTimestampMillis,
            fetchedAtMillis = nowMillis,
            disputed = consensus.disputed,
            singleSource = consensus.singleSource
        )
    }
}
