package ir.thepoo7an.chand.network

import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.model.AssetHistoryPoint
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import kotlin.math.abs

/**
 * Gold spot (XAU), domestic benchmark gold (18K / 24K / Mesghal), and CBI physical coin mapping.
 */
internal object GoldPriceMapper {

    val SUPPORTED_GOLDS: List<MarketPriceMapper.GoldSpec> = listOf(
        MarketPriceMapper.GoldSpec("GOLD_18K", "GRAM", "طلای ۱۸ عیار", "18K Gold", "✨", "geram18"),
        MarketPriceMapper.GoldSpec("GOLD_24K", "GRAM24", "طلای ۲۴ عیار", "24K Gold", "✨", "geram24"),
        MarketPriceMapper.GoldSpec("GOLD_MESGHAL", "MESGHAL", "مثقال طلا", "Gold Mesghal", "⚖️", "mesghal"),
        MarketPriceMapper.GoldSpec("COIN_EMAMI", "SEKE", "سکه امامی", "Emami Coin", "🪙", "sekee"),
        MarketPriceMapper.GoldSpec("COIN_BAHAR", "BAHAR", "سکه بهار آزادی", "Bahar Azadi", "🪙", "sekeb"),
        MarketPriceMapper.GoldSpec("COIN_HALF", "HALF", "نیم سکه", "1/2 Azadi", "🥇", "nim"),
        MarketPriceMapper.GoldSpec("COIN_QUARTER", "QUARTER", "ربع سکه", "1/4 Azadi", "🏅", "rob"),
        MarketPriceMapper.GoldSpec("COIN_GERAMI", "GERAMI", "سکه گرمی", "Gerami Coin", "🟡", "gerami"),
        MarketPriceMapper.GoldSpec("XAU_OZ", "XAU", "انس جهانی طلا", "Gold Ounce", "🏆", "ons")
    )

    fun mapGoldAssets(
        tgju: Map<String, MarketPriceMapper.TgjuQuote>,
        usdToman: Double,
        usdChangePct: Double,
        usdBasisFreshness: DataFreshness,
        usdSourceTimestampMillis: Long,
        goldSpotCandidates: List<MarketPriceMapper.PriceCandidate>,
        paxgQuote: MarketPriceMapper.ExchangeQuote?,
        tgjuGoldRecent: List<AssetHistoryPoint>,
        goldSpark: List<Double>,
        usdtSpark: List<Double>,
        cachedAssetsById: Map<String, MarketAsset>,
        nowMillis: Long,
        computeTableDelta: (List<AssetHistoryPoint>, Double) -> Pair<Double, Double>? = { _, _ -> null }
    ): List<MarketAsset> {
        val onsRaw = tgju["ons"]
        val allSpotCandidates = mutableListOf<MarketPriceMapper.PriceCandidate>()
        goldSpotCandidates.filter { it.price in 1000.0..10_000.0 }.forEach { allSpotCandidates.add(it) }
        paxgQuote?.priceUsd?.takeIf { it in 1000.0..10_000.0 }?.let { paxgPrice ->
            allSpotCandidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "Binance PAXG",
                    price = paxgPrice,
                    isPrimary = allSpotCandidates.none { it.isPrimary },
                    timestampMillis = paxgQuote.timestampMillis.takeIf { it > 0L } ?: nowMillis,
                    changePct = paxgQuote.changePct,
                    highToman = paxgQuote.highUsd?.let { it * usdToman },
                    lowToman = paxgQuote.lowUsd?.let { it * usdToman }
                )
            )
        }
        val tgjuOnsUsd = (onsRaw?.priceToman ?: 0.0) * 10.0
        if (tgjuOnsUsd in 1000.0..10_000.0) {
            allSpotCandidates.add(
                MarketPriceMapper.PriceCandidate(
                    sourceName = "TGJU",
                    price = tgjuOnsUsd,
                    isPrimary = allSpotCandidates.none { it.isPrimary },
                    timestampMillis = onsRaw?.timestampMillis?.takeIf { it > 0L } ?: nowMillis,
                    changePct = onsRaw?.changePct ?: 0.0
                )
            )
        }

        val onsConsensus = MarketValidationHelpers.validateCrossSourcePrices(
            candidates = allSpotCandidates,
            thresholdPct = MarketValidationConfig.FIAT_DISAGREEMENT_THRESHOLD_PCT,
            nowMillis = nowMillis
        )
        val hasRealOns = onsConsensus != null
        val onsUsd = onsConsensus?.price ?: MarketPriceMapper.DEFAULT_OFFLINE_ONS_USD
        val onsTs = onsConsensus?.sourceTimestampMillis ?: 0L
        val onsRawFreshness = MarketValidationHelpers.deriveFreshness(
            category = AssetCategory.GOLD,
            fetchedThisRefresh = hasRealOns,
            hasUsableValue = hasRealOns,
            isCachedSource = !hasRealOns,
            sourceTimestampMillis = onsTs,
            nowMillis = nowMillis
        )
        val derivedGoldFreshness = DataFreshness.stalest(onsRawFreshness, usdBasisFreshness)
        val derivedGoldTs = minOf(
            onsTs.takeIf { it > 0L } ?: nowMillis,
            usdSourceTimestampMillis.takeIf { it > 0L } ?: nowMillis
        )

        val gram24Theoretical = (onsUsd / MarketValidationConfig.TROY_OUNCE_GRAMS) * usdToman
        val gram18Theoretical = gram24Theoretical * (MarketValidationConfig.GOLD_18K_FINENESS_PER_MILLE / 1000.0)
        val canComputeIntrinsic = hasRealOns && usdToman > 1_000.0 && usdBasisFreshness != DataFreshness.UNAVAILABLE

        val result = ArrayList<MarketAsset>(SUPPORTED_GOLDS.size)

        for (spec in SUPPORTED_GOLDS) {
            val q = tgju[spec.tgjuKey]
            val coinSpec = MarketValidationConfig.COIN_SPECS[spec.id]

            if (spec.id == "XAU_OZ") {
                val xauPrice = onsUsd * usdToman
                val changePct = paxgQuote?.changePct ?: onsRaw?.changePct ?: usdChangePct
                val changeToman = xauPrice * changePct / 100.0
                val xauSource = onsConsensus?.acceptedSources?.joinToString("+")?.let { "$it + USD" }
                    ?: "Offline Reference"
                val xauHigh = paxgQuote?.highUsd?.takeIf { it > 0.0 }?.let { it * usdToman }
                val xauLow = paxgQuote?.lowUsd?.takeIf { it > 0.0 }?.let { it * usdToman }
                val spark = when {
                    goldSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(goldSpark, xauPrice)
                    usdtSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(usdtSpark, xauPrice)
                    else -> emptyList()
                }

                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = xauPrice,
                        priceUsd = onsUsd,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = xauHigh,
                        low24hToman = xauLow,
                        sparkline = spark,
                        sourceLabel = xauSource,
                        updatedAtMillis = nowMillis,
                        freshness = derivedGoldFreshness,
                        sourceTimestampMillis = derivedGoldTs,
                        fetchedAtMillis = nowMillis,
                        disputed = onsConsensus?.disputed == true,
                        singleSource = onsConsensus?.singleSource ?: true
                    )
                )
                continue
            }

            if (coinSpec != null) {
                val intrinsic = if (canComputeIntrinsic) {
                    coinSpec.pureGoldGrams * gram24Theoretical
                } else {
                    cachedAssetsById[spec.id]?.intrinsicValueToman
                }

                if (q != null && q.priceToman > 0.0) {
                    val price = q.priceToman
                    val changePct = q.changePct.takeIf { abs(it) > 0.001 } ?: 0.0
                    val changeToman = q.changeToman.takeIf { abs(it) > 0.1 } ?: (price * changePct / 100.0)
                    val bubbleToman = intrinsic?.let { price - it }
                    val bubblePct = if (intrinsic != null && intrinsic > 0.0) {
                        ((price - intrinsic) / intrinsic) * 100.0
                    } else {
                        null
                    }
                    val ts = q.timestampMillis.takeIf { it > 0L } ?: nowMillis
                    val freshness = MarketValidationHelpers.deriveFreshness(
                        category = AssetCategory.GOLD,
                        fetchedThisRefresh = true,
                        hasUsableValue = true,
                        isCachedSource = false,
                        sourceTimestampMillis = ts,
                        nowMillis = nowMillis
                    )
                    val spark = when {
                        goldSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(goldSpark, price)
                        usdtSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(usdtSpark, price)
                        else -> emptyList()
                    }
                    result.add(
                        MarketAsset(
                            id = spec.id,
                            symbol = spec.symbol,
                            nameFa = spec.nameFa,
                            nameEn = spec.nameEn,
                            category = AssetCategory.GOLD,
                            badgeText = spec.badge,
                            accentHex = 0xFFF59E0B,
                            priceToman = price,
                            priceUsd = if (usdToman > 0.0) price / usdToman else null,
                            change24hPercent = changePct,
                            change24hToman = changeToman,
                            high24hToman = q.highToman?.takeIf { it > 0.0 }?.let { maxOf(it, price) },
                            low24hToman = q.lowToman?.takeIf { it > 0.0 }?.let { minOf(it, price) },
                            sparkline = spark,
                            sourceLabel = "TGJU",
                            updatedAtMillis = nowMillis,
                            freshness = freshness,
                            sourceTimestampMillis = ts,
                            fetchedAtMillis = nowMillis,
                            disputed = false,
                            singleSource = true,
                            intrinsicValueToman = intrinsic,
                            bubbleToman = bubbleToman,
                            bubblePercent = bubblePct
                        )
                    )
                } else {
                    val cachedCoin = cachedAssetsById[spec.id]
                        ?.takeIf { it.priceToman > 0.0 && it.freshness != DataFreshness.UNAVAILABLE }
                    if (cachedCoin != null) {
                        val bubbleToman = intrinsic?.let { cachedCoin.priceToman - it } ?: cachedCoin.bubbleToman
                        val bubblePct = if (intrinsic != null && intrinsic > 0.0) {
                            ((cachedCoin.priceToman - intrinsic) / intrinsic) * 100.0
                        } else {
                            cachedCoin.bubblePercent
                        }
                        result.add(
                            cachedCoin.copy(
                                freshness = DataFreshness.CACHED,
                                intrinsicValueToman = intrinsic,
                                bubbleToman = bubbleToman,
                                bubblePercent = bubblePct
                            )
                        )
                    } else {
                        result.add(
                            MarketAsset(
                                id = spec.id,
                                symbol = spec.symbol,
                                nameFa = spec.nameFa,
                                nameEn = spec.nameEn,
                                category = AssetCategory.GOLD,
                                badgeText = spec.badge,
                                accentHex = 0xFFF59E0B,
                                priceToman = 0.0,
                                priceUsd = null,
                                change24hPercent = 0.0,
                                change24hToman = 0.0,
                                high24hToman = null,
                                low24hToman = null,
                                sparkline = emptyList(),
                                sourceLabel = "Unavailable",
                                updatedAtMillis = nowMillis,
                                freshness = DataFreshness.UNAVAILABLE,
                                sourceTimestampMillis = 0L,
                                fetchedAtMillis = nowMillis,
                                disputed = false,
                                singleSource = false,
                                intrinsicValueToman = intrinsic,
                                bubbleToman = null,
                                bubblePercent = null
                            )
                        )
                    }
                }
                continue
            }

            // Benchmark gold units (18K gram, 24K gram, Mesghal)
            val fallbackTheoretical = when (spec.id) {
                "GOLD_18K" -> gram18Theoretical
                "GOLD_24K" -> gram24Theoretical
                "GOLD_MESGHAL" -> gram18Theoretical * MarketValidationConfig.MESGHAL_18K_EQUIVALENT_GRAMS
                else -> gram18Theoretical
            }

            if (q != null && q.priceToman > 0.0) {
                val price = q.priceToman
                val tableFallback = if (spec.id == "GOLD_18K") computeTableDelta(tgjuGoldRecent, price) else null
                val changePct = q.changePct.takeIf { abs(it) > 0.001 } ?: tableFallback?.second ?: 0.0
                val changeToman = q.changeToman.takeIf { abs(it) > 0.1 }
                    ?: tableFallback?.first
                    ?: (price * changePct / 100.0)
                val ts = q.timestampMillis.takeIf { it > 0L } ?: nowMillis
                val freshness = MarketValidationHelpers.deriveFreshness(
                    category = AssetCategory.GOLD,
                    fetchedThisRefresh = true,
                    hasUsableValue = true,
                    isCachedSource = false,
                    sourceTimestampMillis = ts,
                    nowMillis = nowMillis
                )
                val spark = when {
                    goldSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(goldSpark, price)
                    usdtSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(usdtSpark, price)
                    else -> emptyList()
                }
                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = price,
                        priceUsd = if (usdToman > 0.0) price / usdToman else null,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = q.highToman?.takeIf { it > 0.0 }?.let { maxOf(it, price) },
                        low24hToman = q.lowToman?.takeIf { it > 0.0 }?.let { minOf(it, price) },
                        sparkline = spark,
                        sourceLabel = "TGJU",
                        updatedAtMillis = nowMillis,
                        freshness = freshness,
                        sourceTimestampMillis = ts,
                        fetchedAtMillis = nowMillis,
                        disputed = false,
                        singleSource = true
                    )
                )
            } else if (hasRealOns && usdToman > 1_000.0) {
                val price = fallbackTheoretical
                val changePct = paxgQuote?.changePct ?: usdChangePct
                val changeToman = price * changePct / 100.0
                val spark = when {
                    goldSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(goldSpark, price)
                    usdtSpark.size >= 4 -> MarketValidationHelpers.scaleSeriesToCurrent(usdtSpark, price)
                    else -> emptyList()
                }
                result.add(
                    MarketAsset(
                        id = spec.id,
                        symbol = spec.symbol,
                        nameFa = spec.nameFa,
                        nameEn = spec.nameEn,
                        category = AssetCategory.GOLD,
                        badgeText = spec.badge,
                        accentHex = 0xFFF59E0B,
                        priceToman = price,
                        priceUsd = price / usdToman,
                        change24hPercent = changePct,
                        change24hToman = changeToman,
                        high24hToman = null,
                        low24hToman = null,
                        sparkline = spark,
                        sourceLabel = "${onsConsensus.selectedCandidate.sourceName} + USD",
                        updatedAtMillis = nowMillis,
                        freshness = derivedGoldFreshness,
                        sourceTimestampMillis = derivedGoldTs,
                        fetchedAtMillis = nowMillis,
                        disputed = onsConsensus.disputed,
                        singleSource = onsConsensus.singleSource
                    )
                )
            } else {
                val cachedGold = cachedAssetsById[spec.id]
                    ?.takeIf { it.priceToman > 0.0 && it.freshness != DataFreshness.UNAVAILABLE }
                if (cachedGold != null) {
                    result.add(cachedGold.copy(freshness = DataFreshness.CACHED))
                } else {
                    result.add(
                        MarketAsset(
                            id = spec.id,
                            symbol = spec.symbol,
                            nameFa = spec.nameFa,
                            nameEn = spec.nameEn,
                            category = AssetCategory.GOLD,
                            badgeText = spec.badge,
                            accentHex = 0xFFF59E0B,
                            priceToman = 0.0,
                            priceUsd = null,
                            change24hPercent = 0.0,
                            change24hToman = 0.0,
                            high24hToman = null,
                            low24hToman = null,
                            sparkline = emptyList(),
                            sourceLabel = "Unavailable",
                            updatedAtMillis = nowMillis,
                            freshness = DataFreshness.UNAVAILABLE,
                            sourceTimestampMillis = 0L,
                            fetchedAtMillis = nowMillis
                        )
                    )
                }
            }
        }

        return result
    }
}
