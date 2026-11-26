package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.CandidateExclusionReason
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.SourceCandidateRecord
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
        usdtSourceTimestampMillis: Long = 0L,
        nobitexTimestampMillis: Long = 0L,
        wallexFailed: Boolean = false,
        bitpinFailed: Boolean = false,
        binanceFailed: Boolean = false,
        nobitexFailed: Boolean = false,
        usdtBasisSourceLabel: String = "",
        usdtBasisIsCalculated: Boolean = false,
        usdtBasisCandidates: List<SourceCandidateRecord> = emptyList()
    ): MarketAsset? {
        if (spec.id == "USDT") {
            val wallexValid = wallex?.priceToman?.takeIf { it.isFinite() && it > 1_000.0 }
            val bitpinValid = bitpin?.priceToman?.takeIf { it.isFinite() && it > 1_000.0 }
            val nobitexValid = nobitexUsdtToman?.takeIf { it.isFinite() && it > 1_000.0 }

            val usdtCandidates = buildList {
                when {
                    wallexValid != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Wallex",
                            price = wallexValid,
                            isPrimary = true,
                            timestampMillis = wallex.timestampMillis.coerceAtLeast(0L),
                            changePct = wallex.changePct,
                            highToman = wallex.highToman,
                            lowToman = wallex.lowToman
                        )
                    )
                    wallex?.priceToman != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Wallex",
                            price = wallex.priceToman,
                            isPrimary = false,
                            timestampMillis = wallex.timestampMillis.coerceAtLeast(0L),
                            unavailabilityReason = CandidateExclusionReason.INVALID
                        )
                    )
                    else -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Wallex",
                            price = 0.0,
                            isPrimary = false,
                            unavailabilityReason = if (wallexFailed) {
                                CandidateExclusionReason.PROVIDER_FAILURE
                            } else {
                                CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                }

                when {
                    bitpinValid != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Bitpin",
                            price = bitpinValid,
                            isPrimary = wallexValid == null,
                            timestampMillis = bitpin.timestampMillis.coerceAtLeast(0L),
                            changePct = bitpin.changePct,
                            highToman = bitpin.highToman,
                            lowToman = bitpin.lowToman
                        )
                    )
                    bitpin?.priceToman != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Bitpin",
                            price = bitpin.priceToman,
                            isPrimary = false,
                            timestampMillis = bitpin.timestampMillis.coerceAtLeast(0L),
                            unavailabilityReason = CandidateExclusionReason.INVALID
                        )
                    )
                    else -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Bitpin",
                            price = 0.0,
                            isPrimary = false,
                            unavailabilityReason = if (bitpinFailed) {
                                CandidateExclusionReason.PROVIDER_FAILURE
                            } else {
                                CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                }

                when {
                    nobitexValid != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Nobitex",
                            price = nobitexValid,
                            isPrimary = wallexValid == null && bitpinValid == null,
                            timestampMillis = nobitexTimestampMillis.coerceAtLeast(0L)
                        )
                    )
                    nobitexUsdtToman != null -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Nobitex",
                            price = nobitexUsdtToman,
                            isPrimary = false,
                            timestampMillis = nobitexTimestampMillis.coerceAtLeast(0L),
                            unavailabilityReason = CandidateExclusionReason.INVALID
                        )
                    )
                    else -> add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Nobitex",
                            price = 0.0,
                            isPrimary = false,
                            unavailabilityReason = if (nobitexFailed) {
                                CandidateExclusionReason.PROVIDER_FAILURE
                            } else {
                                CandidateExclusionReason.MISSING_DATA
                            }
                        )
                    )
                }
            }

            val consensus = MarketValidationHelpers.validateCrossSourcePrices(
                candidates = usdtCandidates,
                thresholdPct = MarketValidationConfig.effectiveCryptoDisagreementThresholdPct(),
                nowMillis = nowMillis,
                staleMaxAgeMillis = MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
            )

            val priceToman = consensus?.price ?: usdtToman.takeIf { it.isFinite() && it > 0.0 } ?: return null
            val changePct = usdtChangePct
            val changeToman = priceToman * (changePct / 100.0)
            val source = consensus?.sourceAttributionLabel
                ?: usdtBasisSourceLabel.ifBlank { "Cached" }
            val isCalculatedPrice = consensus?.isCalculated ?: usdtBasisIsCalculated
            val candidateRecords = consensus?.candidateRecords ?: usdtBasisCandidates

            val sourceTs = consensus?.sourceTimestampMillis ?: usdtSourceTimestampMillis.coerceAtLeast(0L)
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
                singleSource = consensus?.singleSource ?: true,
                isCalculatedPrice = isCalculatedPrice,
                candidates = candidateRecords
            )
        }

        val priceUsd = binance?.priceUsd ?: wallex?.priceUsd ?: bitpin?.priceUsd
        val wallexValid = wallex?.priceToman?.takeIf { it.isFinite() && it > 0.0 }
        val bitpinValid = bitpin?.priceToman?.takeIf { it.isFinite() && it > 0.0 }
        val binanceValidUsd = binance?.priceUsd?.takeIf { it.isFinite() && it > 0.0 }
        val safeUsdtTs = usdtSourceTimestampMillis.coerceAtLeast(0L)

        val candidates = buildList {
            when {
                wallexValid != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = wallexValid,
                        isPrimary = true,
                        timestampMillis = wallex.timestampMillis.coerceAtLeast(0L),
                        changePct = wallex.changePct,
                        highToman = wallex.highToman,
                        lowToman = wallex.lowToman
                    )
                )
                wallex?.priceToman != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = wallex.priceToman,
                        isPrimary = false,
                        timestampMillis = wallex.timestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Wallex",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (wallexFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }

            when {
                bitpinValid != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = bitpinValid,
                        isPrimary = wallexValid == null,
                        timestampMillis = bitpin.timestampMillis.coerceAtLeast(0L),
                        changePct = bitpin.changePct,
                        highToman = bitpin.highToman,
                        lowToman = bitpin.lowToman
                    )
                )
                bitpin?.priceToman != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = bitpin.priceToman,
                        isPrimary = false,
                        timestampMillis = bitpin.timestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Bitpin",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (bitpinFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }

            when {
                binanceValidUsd != null && usdtToman > 0.0 -> {
                    val binanceTs = binance.timestampMillis.coerceAtLeast(0L)
                    val combinedTs = if (binanceTs > 0L && safeUsdtTs > 0L) minOf(binanceTs, safeUsdtTs) else 0L
                    add(
                        MarketPriceMapper.PriceCandidate(
                            sourceName = "Binance",
                            price = binanceValidUsd * usdtToman,
                            isPrimary = wallexValid == null && bitpinValid == null,
                            timestampMillis = combinedTs,
                            changePct = binance.changePct,
                            highToman = binance.highUsd?.takeIf { it > 0.0 }?.let { it * usdtToman },
                            lowToman = binance.lowUsd?.takeIf { it > 0.0 }?.let { it * usdtToman }
                        )
                    )
                }
                binance?.priceUsd != null -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Binance",
                        price = binance.priceUsd,
                        isPrimary = false,
                        timestampMillis = binance.timestampMillis.coerceAtLeast(0L),
                        unavailabilityReason = CandidateExclusionReason.INVALID
                    )
                )
                else -> add(
                    MarketPriceMapper.PriceCandidate(
                        sourceName = "Binance",
                        price = 0.0,
                        isPrimary = false,
                        unavailabilityReason = if (binanceFailed) {
                            CandidateExclusionReason.PROVIDER_FAILURE
                        } else {
                            CandidateExclusionReason.MISSING_DATA
                        }
                    )
                )
            }
        }

        val consensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = candidates,
            thresholdPct = MarketValidationConfig.effectiveCryptoDisagreementThresholdPct(),
            nowMillis = nowMillis,
            staleMaxAgeMillis = MarketValidationConfig.effectiveCryptoLiveMaxAgeMillis()
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

        val source = if (usesDerivedBinance && usdtBasisIsCalculated && !consensus.isCalculated && consensus.singleSource && usdtBasisSourceLabel.isNotBlank()) {
            "Binance * $usdtBasisSourceLabel"
        } else {
            consensus.sourceAttributionLabel
        }
        val isCalculatedPrice = consensus.isCalculated || (usesDerivedBinance && usdtBasisIsCalculated)
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
            singleSource = consensus.singleSource,
            isCalculatedPrice = isCalculatedPrice,
            candidates = consensus.candidateRecords
        )
    }
}

