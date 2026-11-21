package ir.thepoo7an.chand.ui.strings

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AssetCategory
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val layoutSection: String,
    val layoutGrid: String,
    val layoutList: String,
    val watchlistTitle: String,
    val watchlistSubtitle: String,
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
    val chartInteractiveHint: String,
    val widgetSection: String,
    val widgetTranslucentLabel: String,
    val widgetOpacityLabel: String,
    val widgetPreviewTitle: String,
    val widgetLockPreviewTitle: String,
    val rangeHighLabel: String,
    val rangeLowLabel: String,
    val historyUnavailable: String,
    val updateSection: String,
    val currentVersionLabel: String,
    val checkUpdatesButton: String,
    val checkingUpdatesLabel: String,
    val upToDateLabel: String,
    val updateAvailableTitle: String,
    val downloadUpdateButton: String,
    val viewReleaseButton: String,
    val githubRepoLabel: String,
    val githubRepoPlaceholder: String,
    val updateRepoMissingHint: String,
    val updateCheckErrorLabel: String,
    val laterButton: String
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
    appTitle = "Chand?!",
    marketTab = "بازار",
    converterTab = "مبدل",
    liveBadge = "زنده",
    updating = "بروزرسانی...",
    offlineCachedBanner = "حالت آفلاین — نمایش آخرین نرخ ذخیره شده",
    searchPlaceholder = "جستجوی دلار، یورو، طلا، بیت‌کوین...",
    pinnedSectionTitle = "برگزیده‌ها",
    allMarketsTitle = "همه نرخ‌ها",
    emptySearchTitle = "موردی پیدا نشد",
    emptySearchSubtitle = "نام فارسی، انگلیسی یا نماد ارز دیگری را امتحان کنید.",
    emptyPinnedTitle = "هیچ ارزی پین نشده است",
    emptyPinnedSubtitle = "با دکمه + ارزهای مورد علاقه خود را انتخاب کنید.",
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
    layoutSection = "نحوه نمایش کارت‌ها",
    layoutGrid = "کارت دوتایی",
    layoutList = "لیست ردیفی",
    watchlistTitle = "مدیریت کارت‌های برگزیده",
    watchlistSubtitle = "ارزهایی را که می‌خواهید در صفحه اصلی و ویجت‌ها نمایش داده شوند انتخاب کنید.",
    autoRefreshSection = "بروزرسانی خودکار",
    manualRefreshOnly = "دستی",
    secondsUnit = "ثانیه",
    retryLabel = "تلاش مجدد",
    lastUpdatePrefix = "بروزرسانی:",
    ratePerOnePrefix = "هر ۱ واحد =",
    categoryAll = "همه",
    categoryPinned = "پین‌شده",
    categoryFiat = "ارزها",
    categoryGold = "طلا و سکه",
    categoryCrypto = "رمزارز",
    tomanAssetName = "تومان ایران",
    chartInteractiveHint = "برای مشاهده قیمت دقیق روی نمودار بکشید",
    widgetSection = "ویجت صفحه اصلی",
    widgetTranslucentLabel = "حالت نیمه‌شفاف (Translucent)",
    widgetOpacityLabel = "شفافیت شیشه‌ای ویجت",
    widgetPreviewTitle = "پیش‌نمایش ویجت 2×1 (دلار، یورو، طلا)",
    widgetLockPreviewTitle = "پیش‌نمایش ویجت لاک‌اسکرین (بدون پس‌زمینه)",
    rangeHighLabel = "سقف بازه",
    rangeLowLabel = "کف بازه",
    historyUnavailable = "تاریخچه واقعی در دسترس نیست",
    updateSection = "بروزرسانی برنامه",
    currentVersionLabel = "نسخه فعلی",
    checkUpdatesButton = "بررسی نسخه جدید",
    checkingUpdatesLabel = "در حال بررسی...",
    upToDateLabel = "برنامه به آخرین نسخه بروز است",
    updateAvailableTitle = "نسخه جدید آماده دانلود است",
    downloadUpdateButton = "دانلود نسخه جدید (مرورگر)",
    viewReleaseButton = "صفحه انتشار گیت‌هاب",
    githubRepoLabel = "مخزن گیت‌هاب (owner/repo)",
    githubRepoPlaceholder = "thepoo7an/Chand",
    updateRepoMissingHint = "نام مخزن گیت‌هاب را برای بررسی نسخه جدید وارد کنید.",
    updateCheckErrorLabel = "خطا در بررسی نسخه جدید",
    laterButton = "بعداً"
)

val EnStrings = ChandStrings(
    appTitle = "Chand?!",
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
    emptyPinnedSubtitle = "Tap + to pin your favorite currencies to the grid.",
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
    layoutSection = "Card Layout",
    layoutGrid = "2-Column Grid",
    layoutList = "List View",
    watchlistTitle = "Customize Watchlist",
    watchlistSubtitle = "Choose which currencies & assets stay pinned on your home grid and widgets.",
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
    chartInteractiveHint = "Drag across the chart to inspect price points",
    widgetSection = "Home Screen Widgets",
    widgetTranslucentLabel = "Translucent Glass Style",
    widgetOpacityLabel = "Glass Opacity",
    widgetPreviewTitle = "2×1 Compact Preview (USD • EUR • GOLD)",
    widgetLockPreviewTitle = "Lock Screen Widget Preview (No Background)",
    rangeHighLabel = "Range High",
    rangeLowLabel = "Range Low",
    historyUnavailable = "Real history unavailable offline",
    updateSection = "App Updates",
    currentVersionLabel = "Current Version",
    checkUpdatesButton = "Check for Updates",
    checkingUpdatesLabel = "Checking...",
    upToDateLabel = "You are on the latest version",
    updateAvailableTitle = "New Update Available",
    downloadUpdateButton = "Download Update (Browser)",
    viewReleaseButton = "Release Page",
    githubRepoLabel = "GitHub Repository (owner/repo)",
    githubRepoPlaceholder = "thepoo7an/Chand",
    updateRepoMissingHint = "Enter your GitHub repository (owner/repo) to check releases.",
    updateCheckErrorLabel = "Could not check GitHub Releases",
    laterButton = "Later"
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

    /**
     * Formats large Toman amounts (e.g., BTC in Billions / ETH in Millions) in crisp Chand?! card style
     * while preserving exact comma-separated digits for standard currencies and 18K gold.
     */
    fun formatCardPrice(amount: Double, usePersianDigits: Boolean): String {
        val absAmt = abs(amount)
        val raw = when {
            absAmt >= 1_000_000_000.0 -> {
                val b = amount / 1_000_000_000.0
                "${twoDecimalFmt.format(b)}B"
            }
            absAmt >= 100_000_000.0 -> {
                val m = amount / 1_000_000.0
                "${twoDecimalFmt.format(m)}M"
            }
            absAmt >= 100.0 -> integerFmt.format(amount)
            absAmt >= 1.0 -> twoDecimalFmt.format(amount)
            else -> fourDecimalFmt.format(amount)
        }
        return applyDigits(raw, usePersianDigits)
    }

    /**
     * Formats Chand?!-style Toman delta with arrow: e.g. ↑50, ↓5.35K, ↓2.2M
     */
    fun formatDeltaWithArrow(
        changeToman: Double,
        changePct: Double,
        usePersianDigits: Boolean
    ): String {
        val arrow = when {
            changeToman > 0.001 || changePct > 0.005 -> "↑"
            changeToman < -0.001 || changePct < -0.005 -> "↓"
            else -> "• "
        }
        val absDelta = abs(changeToman)
        val body = when {
            absDelta >= 1_000_000.0 -> "${twoDecimalFmt.format(absDelta / 1_000_000.0)}M"
            absDelta >= 1_000.0 -> "${twoDecimalFmt.format(absDelta / 1_000.0)}K"
            absDelta >= 1.0 -> integerFmt.format(absDelta)
            abs(changePct) > 0.005 -> "${String.format(Locale.US, "%.2f", abs(changePct))}%"
            else -> "0"
        }
        return applyDigits("$arrow$body", usePersianDigits)
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
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return applyDigits(sdf.format(Date(millis)), usePersianDigits)
    }

    fun formatJalaliDateTime(millis: Long, usePersianDigits: Boolean): String {
        if (millis <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))
        val dateStr = "$jm/$jd/$jy, $timeFmt"
        return applyDigits(dateStr, usePersianDigits)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
        val jd = 1 + if (days < 186) (days % 31) else ((days - 186) % 30)
        return Triple(jy, jm, jd)
    }

    fun formatChartDate(timestampSec: Long, usePersianDigits: Boolean, shortTimeOnly: Boolean): String {
        val pattern = if (shortTimeOnly) "HH:mm" else "yyyy/MM/dd"
        val sdf = SimpleDateFormat(pattern, Locale.US)
        return applyDigits(sdf.format(Date(timestampSec * 1000L)), usePersianDigits)
    }

    fun formatChartJalaliDate(
        timestampSec: Long,
        usePersianDigits: Boolean,
        includeTime: Boolean,
        includeYear: Boolean
    ): String {
        if (timestampSec <= 0L) return ""
        val millis = timestampSec * 1000L
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val datePart = if (includeYear) "$jm/$jd/$jy" else "$jm/$jd"
        val out = if (includeTime) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
            if (includeYear) "$datePart $timeStr" else timeStr
        } else {
            datePart
        }
        return applyDigits(out, usePersianDigits)
    }
}
