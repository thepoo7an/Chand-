package com.example.ui.strings

import com.example.model.AppLanguage
import com.example.model.AssetCategory
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ChandStrings(
    val appTitle: String,
    val marketTab: String,
    val converterTab: String,
    val liveBadge: String,
    val updating: String,
    val offlineCachedBanner: String,
    val searchPlaceholder: String,
    val pinnedSectionTitle: String,
    val allMarketsTitle: String,
    val emptySearchTitle: String,
    val emptySearchSubtitle: String,
    val emptyPinnedTitle: String,
    val emptyPinnedSubtitle: String,
    val tomanUnit: String,
    val usdUnit: String,
    val high24h: String,
    val low24h: String,
    val sourceLabel: String,
    val convertThisAsset: String,
    val pinAsset: String,
    val unpinAsset: String,
    val converterTitle: String,
    val fromLabel: String,
    val toLabel: String,
    val swapLabel: String,
    val quickEquivalentTitle: String,
    val selectAssetTitle: String,
    val settingsTitle: String,
    val themeSection: String,
    val themeLight: String,
    val themeDark: String,
    val themeAmoled: String,
    val languageSection: String,
    val langPersian: String,
    val langEnglish: String,
    val persianDigitsLabel: String,
    val autoRefreshSection: String,
    val manualRefreshOnly: String,
    val secondsUnit: String,
    val retryLabel: String,
    val lastUpdatePrefix: String,
    val ratePerOnePrefix: String,
    val categoryAll: String,
    val categoryPinned: String,
    val categoryFiat: String,
    val categoryGold: String,
    val categoryCrypto: String,
    val tomanAssetName: String,
    val chartInteractiveHint: String
) {
    fun categoryLabel(category: AssetCategory): String = when (category) {
        AssetCategory.ALL -> categoryAll
        AssetCategory.PINNED -> categoryPinned
        AssetCategory.FIAT -> categoryFiat
        AssetCategory.GOLD -> categoryGold
        AssetCategory.CRYPTO -> categoryCrypto
    }
}

val FaStrings = ChandStrings(
    appTitle = "چند؟",
    marketTab = "نرخ لحظه‌ای",
    converterTab = "مبدل ارز",
    liveBadge = "زنده",
    updating = "در حال بروزرسانی...",
    offlineCachedBanner = "حالت آفلاین — نمایش آخرین نرخ ذخیره شده",
    searchPlaceholder = "جستجوی دلار، یورو، طلا، بیت‌کوین...",
    pinnedSectionTitle = "برگزیده‌ها",
    allMarketsTitle = "همه نرخ‌ها",
    emptySearchTitle = "موردی پیدا نشد",
    emptySearchSubtitle = "نام فارسی، انگلیسی یا نماد ارز دیگری را امتحان کنید.",
    emptyPinnedTitle = "هیچ ارزی پین نشده است",
    emptyPinnedSubtitle = "با لمس آیکون سنجاق، ارزهای مهم خود را به بالا اضافه کنید.",
    tomanUnit = "تومان",
    usdUnit = "دلار",
    high24h = "سقف ۲۴ساعت",
    low24h = "کف ۲۴ساعت",
    sourceLabel = "منبع",
    convertThisAsset = "تبدیل این دارایی",
    pinAsset = "سنجاق کردن",
    unpinAsset = "حذف سنجاق",
    converterTitle = "مبدل سریع",
    fromLabel = "مبدا",
    toLabel = "مقصد",
    swapLabel = "جابه‌جایی مبدا و مقصد",
    quickEquivalentTitle = "معادل لحظه‌ای در سایر بازارها",
    selectAssetTitle = "انتخاب ارز یا دارایی",
    settingsTitle = "ظاهر و تنظیمات",
    themeSection = "تم نمایش",
    themeLight = "روشن",
    themeDark = "تیره",
    themeAmoled = "مشکی AMOLED",
    languageSection = "زبان برنامه",
    langPersian = "فارسی (راست‌چین)",
    langEnglish = "English (LTR)",
    persianDigitsLabel = "نمایش ارقام فارسی (۱۲۳)",
    autoRefreshSection = "بروزرسانی خودکار",
    manualRefreshOnly = "دستی",
    secondsUnit = "ثانیه",
    retryLabel = "تلاش مجدد",
    lastUpdatePrefix = "بروزرسانی:",
    ratePerOnePrefix = "هر ۱ واحد =",
    categoryAll = "همه",
    categoryPinned = "پین‌شده",
    categoryFiat = "ارز آزاد",
    categoryGold = "طلا و سکه",
    categoryCrypto = "رمزارز",
    tomanAssetName = "تومان ایران",
    chartInteractiveHint = "برای مشاهده قیمت دقیق روی نمودار بکشید"
)

val EnStrings = ChandStrings(
    appTitle = "Chand?",
    marketTab = "Markets",
    converterTab = "Converter",
    liveBadge = "LIVE",
    updating = "Updating...",
    offlineCachedBanner = "Offline — showing last cached rates",
    searchPlaceholder = "Search USD, EUR, Gold, Bitcoin...",
    pinnedSectionTitle = "Pinned",
    allMarketsTitle = "All Rates",
    emptySearchTitle = "No matching assets",
    emptySearchSubtitle = "Try searching another currency name or ticker.",
    emptyPinnedTitle = "No pinned assets yet",
    emptyPinnedSubtitle = "Tap the pin icon on any currency to keep it on top.",
    tomanUnit = "TMN",
    usdUnit = "USD",
    high24h = "24h High",
    low24h = "24h Low",
    sourceLabel = "Source",
    convertThisAsset = "Convert Asset",
    pinAsset = "Pin to Top",
    unpinAsset = "Unpin",
    converterTitle = "Quick Converter",
    fromLabel = "From",
    toLabel = "To",
    swapLabel = "Swap currencies",
    quickEquivalentTitle = "Live Equivalents",
    selectAssetTitle = "Choose Currency or Asset",
    settingsTitle = "Appearance & Settings",
    themeSection = "Theme",
    themeLight = "Light",
    themeDark = "Dark",
    themeAmoled = "AMOLED",
    languageSection = "Language",
    langPersian = "فارسی (RTL)",
    langEnglish = "English (LTR)",
    persianDigitsLabel = "Persian Digits (۱۲۳)",
    autoRefreshSection = "Auto Refresh",
    manualRefreshOnly = "Manual",
    secondsUnit = "s",
    retryLabel = "Retry",
    lastUpdatePrefix = "Updated:",
    ratePerOnePrefix = "1 unit =",
    categoryAll = "All",
    categoryPinned = "Pinned",
    categoryFiat = "Currencies",
    categoryGold = "Gold & Coin",
    categoryCrypto = "Crypto",
    tomanAssetName = "Iranian Toman",
    chartInteractiveHint = "Drag across the chart to inspect price points"
)

fun stringsFor(language: AppLanguage): ChandStrings =
    if (language == AppLanguage.FA) FaStrings else EnStrings

object NumberFormatters {
    private val usSymbols = DecimalFormatSymbols(Locale.US)
    private val integerFmt = DecimalFormat("#,##0", usSymbols)
    private val twoDecimalFmt = DecimalFormat("#,##0.##", usSymbols)
    private val fourDecimalFmt = DecimalFormat("#,##0.####", usSymbols)

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when (ch) {
                    '0' -> '۰'
                    '1' -> '۱'
                    '2' -> '۲'
                    '3' -> '۳'
                    '4' -> '۴'
                    '5' -> '۵'
                    '6' -> '۶'
                    '7' -> '۷'
                    '8' -> '۸'
                    '9' -> '۹'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    fun applyDigits(text: String, usePersianDigits: Boolean): String =
        if (usePersianDigits) toPersianDigits(text) else text

    fun formatToman(amount: Double, usePersianDigits: Boolean): String {
        val formatted = when {
            abs(amount) >= 100.0 -> integerFmt.format(amount)
            abs(amount) >= 1.0 -> twoDecimalFmt.format(amount)
            else -> fourDecimalFmt.format(amount)
        }
        return applyDigits(formatted, usePersianDigits)
    }

    fun formatUsd(amount: Double, usePersianDigits: Boolean): String {
        val formatted = when {
            abs(amount) >= 100.0 -> integerFmt.format(amount)
            abs(amount) >= 1.0 -> twoDecimalFmt.format(amount)
            else -> fourDecimalFmt.format(amount)
        }
        return "$" + applyDigits(formatted, usePersianDigits)
    }

    fun formatPercent(change: Double, usePersianDigits: Boolean): String {
        val sign = if (change > 0.005) "+" else if (change < -0.005) "-" else ""
        val raw = "$sign${String.format(Locale.US, "%.2f", abs(change))}%"
        return applyDigits(raw, usePersianDigits)
    }

    fun formatTime(millis: Long, usePersianDigits: Boolean): String {
        if (millis <= 0L) return ""
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return applyDigits(sdf.format(Date(millis)), usePersianDigits)
    }

    fun formatChartDate(timestampSec: Long, usePersianDigits: Boolean, shortTimeOnly: Boolean): String {
        val pattern = if (shortTimeOnly) "HH:mm" else "MM/dd"
        val sdf = SimpleDateFormat(pattern, Locale.US)
        return applyDigits(sdf.format(Date(timestampSec * 1000L)), usePersianDigits)
    }
}
