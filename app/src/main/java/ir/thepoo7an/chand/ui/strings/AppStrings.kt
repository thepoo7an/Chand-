package ir.thepoo7an.chand.ui.strings

import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AssetCategory
import ir.thepoo7an.chand.network.UpdateErrorReason
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
    val delayedBadge: String,
    val cachedBadge: String,
    val unavailableBadge: String,
    val disputedLabel: String,
    val singleSourceLabel: String,
    val updating: String,
    val delayedBanner: String,
    val offlineCachedBanner: String,
    val offlineReferenceBanner: String,
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
    val intrinsicEstimateLabel: String,
    val bubbleEstimateLabel: String,
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
    val categoryOil: String,
    val tomanAssetName: String,
    val chartInteractiveHint: String,
    val widgetSection: String,
    val widgetTranslucentLabel: String,
    val widgetOpacityLabel: String,
    val widgetHeroAssetLabel: String,
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
    val laterButton: String,
    val updateCatalogTitle: String,
    val changelogTitle: String,
    val changelogEmptyFallback: String,
    val newVersionLabel: String,
    val downloadingUpdateLabel: String,
    val downloadCompleteLabel: String,
    val installUpdateButton: String,
    val grantInstallPermissionButton: String,
    val installPermissionHint: String,
    val cancelDownloadButton: String,
    val downloadFailedLabel: String,
    val retryDownloadButton: String,
    val openUpdateCatalogButton: String,
    val openInBrowserFallback: String,
    val publishedAtPrefix: String,
    val networkUnavailableBanner: String,
    val liveConfigSection: String,
    val liveConfigSubtitle: String,
    val liveConfigSyncButton: String,
    val liveConfigSyncingLabel: String,
    val liveConfigResetButton: String,
    val liveConfigBuiltInLabel: String,
    val liveConfigVersionPrefix: String,
    val liveConfigLastSyncPrefix: String,
    val liveConfigUrlLabel: String,
    val liveConfigUrlPlaceholder: String,
    val liveConfigSyncedToast: String,
    val liveConfigNoRemoteLabel: String,
    val liveConfigIncompatibleLabel: String,
    val liveConfigErrorLabel: String,
    val liveConfigChangesTitle: String,
    val liveConfigOverridesActiveLabel: String,
    val liveConfigDefaultSummary: String,
    val chartsDisabledNotice: String,
    val updateErrorNetwork: String,
    val updateErrorInvalidUrl: String,
    val updateErrorHttpsFailed: String,
    val updateErrorChecksumMismatch: String,
    val updateErrorMetadataInvalid: String,
    val updateErrorCorruptedApk: String,
    val updateErrorWrongPackage: String,
    val updateErrorOlderVersion: String,
    val updateErrorUnexpectedSigner: String,
    val updateErrorInsufficientStorage: String,
    val updateErrorUnreasonableSize: String,
    val updateErrorIncompleteDownload: String,
    val updateErrorInstallFailed: String,
    val sourceBreakdownTitle: String,
    val sourceBreakdownSubtitle: String,
    val calculatedMainPriceLabel: String,
    val primaryMainPriceLabel: String,
    val lastSuccessfulUpdateLabel: String,
    val configuredSourcesCountPrefix: String,
    val sourceTimestampLabel: String,
    val sourceTimestampUnavailable: String,
    val sourceContributedYes: String,
    val sourceContributedNo: String,
    val sourcePrimaryTag: String,
    val sourceRawQuotePrefix: String,
    val sourceUnitCurrencyToman: String,
    val sourceStatusIncluded: String,
    val sourceStatusRejected: String,
    val sourceStatusDelayed: String,
    val sourceStatusUnavailable: String,
    val sourceStatusInvalid: String,
    val exclusionReasonValidationFailure: String,
    val exclusionReasonDisagreement: String,
    val exclusionReasonStale: String,
    val exclusionReasonInvalid: String,
    val exclusionReasonMissingData: String,
    val exclusionReasonProviderFailure: String,
    val sourceNoRecordsNotice: String,
    val sourceTapHint: String,
    val usdPerBarrelUnit: String,
    val usdPerBarrelShortUnit: String,
    val oilTomanEstimatePrefix: String,
    val change24hUnavailable: String,
    val sourceUnitCurrencyUsdPerBarrel: String,
    val chartModeLine: String,
    val chartModeBar: String,
    val chartPointsSuffix: String,
    val chartGapDetectedNotice: String,
    val periodChangeLabel: String,
    val change24hLabel: String,
    val supportDevCardTitle: String,
    val supportDevCardSubtitle: String,
    val supportDevOpenRepo: String,
    val supportCardToggleLabel: String,
    val lockScreenPreAndroid12Notice: String,
    val alertsTitle: String,
    val alertsSubtitle: String,
    val alertsEmptyTitle: String,
    val alertsEmptySubtitle: String,
    val addAlertButton: String,
    val createAlertTitle: String,
    val editAlertTitle: String,
    val alertUpperBoundLabel: String,
    val alertLowerBoundLabel: String,
    val alertBoundHint: String,
    val alertInvalidBoundsError: String,
    val alertEnabledLabel: String,
    val alertRearmButton: String,
    val alertArmedStatus: String,
    val alertTriggeredStatus: String,
    val alertUpperShort: String,
    val alertLowerShort: String,
    val alertTestButton: String,
    val alertTestSentNotice: String,
    val alertNotificationsDisabledBanner: String,
    val alertSaveButton: String,
    val alertDeleteButton: String,
    val setPriceAlertButton: String,
    val alertNotificationChannelName: String,
    val alertNotificationChannelDesc: String,
    val alertNotificationUpperText: String,
    val alertNotificationLowerText: String,
    val alertTestNotificationTitle: String,
    val alertTestNotificationBody: String,
    val comparisonSectionTitle: String,
    val comparisonSectionSubtitle: String,
    val comparisonDivergenceTag: String,
    val fetchedAtTimestampLabel: String,
    val comparisonVsPrefix: String,
    val freshnessPrefix: String,
    val freshnessJustNow: String,
    val freshnessMinutesAgoSuffix: String,
    val freshnessHoursAgoSuffix: String,
    val freshnessDaysAgoSuffix: String
) {
    fun categoryLabel(category: AssetCategory): String = when (category) {
        AssetCategory.ALL -> categoryAll
        AssetCategory.PINNED -> categoryPinned
        AssetCategory.FIAT -> categoryFiat
        AssetCategory.GOLD -> categoryGold
        AssetCategory.CRYPTO -> categoryCrypto
        AssetCategory.OIL -> categoryOil
    }

    fun updateErrorMessage(reason: UpdateErrorReason): String = when (reason) {
        UpdateErrorReason.NETWORK_FAILURE -> updateErrorNetwork
        UpdateErrorReason.INVALID_URL -> updateErrorInvalidUrl
        UpdateErrorReason.HTTPS_VALIDATION_FAILED -> updateErrorHttpsFailed
        UpdateErrorReason.CHECKSUM_MISMATCH -> updateErrorChecksumMismatch
        UpdateErrorReason.METADATA_INVALID -> updateErrorMetadataInvalid
        UpdateErrorReason.CORRUPTED_APK -> updateErrorCorruptedApk
        UpdateErrorReason.WRONG_PACKAGE_NAME -> updateErrorWrongPackage
        UpdateErrorReason.OLDER_OR_EQUAL_VERSION -> updateErrorOlderVersion
        UpdateErrorReason.UNEXPECTED_SIGNER -> updateErrorUnexpectedSigner
        UpdateErrorReason.INSUFFICIENT_STORAGE -> updateErrorInsufficientStorage
        UpdateErrorReason.UNREASONABLE_FILE_SIZE -> updateErrorUnreasonableSize
        UpdateErrorReason.INCOMPLETE_DOWNLOAD -> updateErrorIncompleteDownload
        UpdateErrorReason.INSTALL_FAILED -> updateErrorInstallFailed
    }
}

val FaStrings = ChandStrings(
    appTitle = "نرخ‌نما",
    marketTab = "بازار",
    converterTab = "مبدل",
    liveBadge = "زنده",
    delayedBadge = "با تأخیر",
    cachedBadge = "ذخیره‌شده",
    unavailableBadge = "ناموجود",
    disputedLabel = "اختلاف منبع",
    singleSourceLabel = "تک‌منبع",
    updating = "بروزرسانی...",
    delayedBanner = "برخی نرخ‌ها با تأخیر منبع دریافت شده‌اند",
    offlineCachedBanner = "حالت آفلاین — نمایش آخرین نرخ ذخیره شده",
    offlineReferenceBanner = "حالت آفلاین — نرخ مرجع پایه (برای نرخ لحظه‌ای آنلاین شوید)",
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
    intrinsicEstimateLabel = "ارزش ذاتی (تخمین)",
    bubbleEstimateLabel = "حباب سکه (تخمین)",
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
    categoryOil = "نفت خام",
    tomanAssetName = "تومان ایران",
    chartInteractiveHint = "برای مشاهده قیمت دقیق روی نمودار بکشید",
    widgetSection = "ویجت صفحه اصلی",
    widgetTranslucentLabel = "حالت نیمه‌شفاف (Translucent)",
    widgetOpacityLabel = "شفافیت شیشه‌ای ویجت",
    widgetHeroAssetLabel = "دارایی اصلی ویجت ۱×۱",
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
    downloadUpdateButton = "دانلود و نصب آپدیت",
    viewReleaseButton = "صفحه انتشار گیت‌هاب",
    githubRepoLabel = "مخزن گیت‌هاب (owner/repo)",
    githubRepoPlaceholder = "thepoo7an/chand-iran-currency-gold-crypto",
    updateRepoMissingHint = "نام مخزن گیت‌هاب را برای بررسی نسخه جدید وارد کنید.",
    updateCheckErrorLabel = "خطا در بررسی نسخه جدید",
    laterButton = "بعداً",
    updateCatalogTitle = "کاتالوگ بروزرسانی",
    changelogTitle = "تغییرات نسخه جدید",
    changelogEmptyFallback = "بهبود عملکرد، افزایش پایداری و رفع اشکالات جزئی.",
    newVersionLabel = "نسخه جدید",
    downloadingUpdateLabel = "در حال دانلود فایل نصب داخل برنامه...",
    downloadCompleteLabel = "دانلود کامل شد و آماده نصب است",
    installUpdateButton = "نصب نسخه جدید",
    grantInstallPermissionButton = "اجازه نصب از این برنامه",
    installPermissionHint = "برای نصب مستقیم، گزینه «اجازه نصب از این منبع» را در تنظیمات گوشی فعال کنید.",
    cancelDownloadButton = "لغو دانلود",
    downloadFailedLabel = "خطا در دانلود فایل بروزرسانی",
    retryDownloadButton = "تلاش مجدد دانلود",
    openUpdateCatalogButton = "مشاهده کاتالوگ تغییرات و آپدیت",
    openInBrowserFallback = "دانلود از مرورگر",
    publishedAtPrefix = "انتشار:",
    networkUnavailableBanner = "اتصال اینترنت برقرار نیست و نرخ ذخیره‌شده‌ای وجود ندارد",
    liveConfigSection = "بروزرسانی آنلاین قوانین و منابع (بدون نصب APK)",
    liveConfigSubtitle = "دریافت خودکار آدرس سرورها، ارزهای جدید، آستانه اعتبارسنجی و اصلاحات فوری بدون دانلود نسخه جدید",
    liveConfigSyncButton = "همگام‌سازی آنلاین",
    liveConfigSyncingLabel = "در حال دریافت...",
    liveConfigResetButton = "بازگشت به تنظیم پایه",
    liveConfigBuiltInLabel = "تنظیمات پایه داخلی فعال است",
    liveConfigVersionPrefix = "نسخه پیکربندی آنلاین:",
    liveConfigLastSyncPrefix = "آخرین همگام‌سازی:",
    liveConfigUrlLabel = "آدرس مستقیم پیکربندی آنلاین (HTTPS اختیاری)",
    liveConfigUrlPlaceholder = "خالی = دریافت خودکار از مخزن گیت‌هاب / CDN",
    liveConfigSyncedToast = "اصلاحات و تنظیمات آنلاین با موفقیت اعمال شد",
    liveConfigNoRemoteLabel = "فایل پیکربندی آنلاین در مخزن پیدا نشد — تنظیم پایه فعال است",
    liveConfigIncompatibleLabel = "این پیکربندی آنلاین برای نسخه جدیدتر برنامه است",
    liveConfigErrorLabel = "خطا در دریافت تنظیمات آنلاین",
    liveConfigChangesTitle = "تغییرات اعمال‌شده روی این نسخه",
    liveConfigOverridesActiveLabel = "قانون فعال",
    liveConfigDefaultSummary = "تنظیمات لحظه‌ای منابع و دارایی‌ها به‌روز است",
    chartsDisabledNotice = "نمایش نمودار تاریخی موقتاً توسط تنظیمات آنلاین غیرفعال شده است",
    updateErrorNetwork = "خطای شبکه هنگام دریافت بروزرسانی. اتصال اینترنت را بررسی و دوباره تلاش کنید.",
    updateErrorInvalidUrl = "نشانی دانلود بروزرسانی نامعتبر یا از دامنه غیرمطمئن است.",
    updateErrorHttpsFailed = "ارتباط امن HTTPS برقرار نشد یا تغییر مسیر ناامن به HTTP شناسایی شد.",
    updateErrorChecksumMismatch = "عدم تطابق اثر انگشت SHA-256 فایل؛ بسته دانلودشده ممکن است دستکاری شده باشد.",
    updateErrorMetadataInvalid = "اطلاعات امنیتی و هش انتشار ناقص یا نامعتبر است؛ دانلود مستقیم انجام نشد.",
    updateErrorCorruptedApk = "فایل APK دانلودشده آسیب دیده یا ساختار معتبری ندارد.",
    updateErrorWrongPackage = "شناسه بسته فایل دانلودشده با شناسه برنامه نرخ‌نما (ir.thepoo7an.chand) مطابقت ندارد.",
    updateErrorOlderVersion = "نسخه فایل دانلودشده جدیدتر از نسخه فعلی نیست و نصب مسدود شد.",
    updateErrorUnexpectedSigner = "امضای دیجیتال فایل با گواهی رسمی انتشار مطابقت ندارد.",
    updateErrorInsufficientStorage = "فضای ذخیره‌سازی دستگاه برای دانلود و بررسی بسته بروزرسانی کافی نیست.",
    updateErrorUnreasonableSize = "حجم فایل بروزرسانی خارج از محدوده مجاز و غیرعادی است.",
    updateErrorIncompleteDownload = "دانلود فایل بروزرسانی ناقص یا خالی است. لطفاً دوباره تلاش کنید.",
    updateErrorInstallFailed = "باز کردن نصب‌کننده اندروید با خطا مواجه شد.",
    sourceBreakdownTitle = "جزئیات منابع نرخ بازار",
    sourceBreakdownSubtitle = "نرخ‌های گزارش‌شده از منابع فعال و وضعیت مشارکت در قیمت نهایی",
    calculatedMainPriceLabel = "قیمت نهایی محاسبه‌شده",
    primaryMainPriceLabel = "قیمت نهایی نمایش‌داده‌شده",
    lastSuccessfulUpdateLabel = "آخرین بروزرسانی موفق",
    configuredSourcesCountPrefix = "منابع پیکربندی‌شده:",
    sourceTimestampLabel = "زمان منبع",
    sourceTimestampUnavailable = "زمان منبع در دسترس نیست",
    sourceContributedYes = "موثر در قیمت نهایی",
    sourceContributedNo = "بدون اثر در قیمت نهایی",
    sourcePrimaryTag = "منبع اصلی",
    sourceRawQuotePrefix = "نرخ پایه:",
    sourceUnitCurrencyToman = "تومان (TMN)",
    sourceStatusIncluded = "پذیرفته‌شده",
    sourceStatusRejected = "ردشده",
    sourceStatusDelayed = "با تأخیر",
    sourceStatusUnavailable = "ناموجود",
    sourceStatusInvalid = "نامعتبر",
    exclusionReasonValidationFailure = "عدم عبور از اعتبارسنجی",
    exclusionReasonDisagreement = "اختلاف بیش از حد مجاز با سایر منابع",
    exclusionReasonStale = "داده قدیمی و منقضی (تأخیر منبع)",
    exclusionReasonInvalid = "پاسخ نامعتبر یا خارج از محدوده مجاز",
    exclusionReasonMissingData = "عدم وجود داده برای این دارایی",
    exclusionReasonProviderFailure = "عدم پاسخگویی ارائه‌دهنده",
    sourceNoRecordsNotice = "جزئیات منابع برای این نرخ پایه آفلاین در دسترس نیست.",
    sourceTapHint = "مشاهده منابع",
    usdPerBarrelUnit = "دلار / بشکه",
    usdPerBarrelShortUnit = "USD/bbl",
    oilTomanEstimatePrefix = "تخمین بر اساس دلار:",
    change24hUnavailable = "تغییر ۲۴ساعت ناموجود",
    sourceUnitCurrencyUsdPerBarrel = "دلار / بشکه (USD/bbl)",
    chartModeLine = "خطی",
    chartModeBar = "میله‌ای",
    chartPointsSuffix = "نقطه",
    chartGapDetectedNotice = "وقفه داده",
    periodChangeLabel = "تغییر بازه",
    change24hLabel = "تغییر ۲۴ساعت",
    supportDevCardTitle = "حمایت از توسعه متن‌باز",
    supportDevCardSubtitle = "نرخ‌نما همیشه رایگان و بدون تبلیغات است. با ستاره در گیت‌هاب از توسعه پروژه حمایت کنید.",
    supportDevOpenRepo = "مشاهده مخزن گیت‌هاب",
    supportCardToggleLabel = "نمایش کارت حمایت از توسعه در صفحه بازار",
    lockScreenPreAndroid12Notice = "ویجت صفحه قفل نیازمند اندروید ۱۲ به بالا (API 31+) است. در این دستگاه این ویجت روی صفحه اصلی قرار می‌گیرد.",
    alertsTitle = "هشدارهای قیمت",
    alertsSubtitle = "بررسی پس‌زمینه هر ۳۰ دقیقه هنگام اتصال اینترنت بدون دسترسی اضافه",
    alertsEmptyTitle = "هشداری ثبت نشده است",
    alertsEmptySubtitle = "برای دلار، طلا، سکه یا رمزارز حد بالا یا پایین تومانی تعریف کنید.",
    addAlertButton = "افزودن هشدار",
    createAlertTitle = "ایجاد هشدار قیمت",
    editAlertTitle = "ویرایش هشدار قیمت",
    alertUpperBoundLabel = "حد بالای قیمت (تومان)",
    alertLowerBoundLabel = "حد پایین قیمت (تومان)",
    alertBoundHint = "حداقل یکی از دو حد بالا یا پایین را به تومان وارد کنید.",
    alertInvalidBoundsError = "لطفاً مبلغ معتبر وارد کنید (حد بالا باید بیشتر از حد پایین باشد).",
    alertEnabledLabel = "فعال",
    alertRearmButton = "فعال‌سازی مجدد",
    alertArmedStatus = "در حال پایش",
    alertTriggeredStatus = "اعلام‌شده",
    alertUpperShort = "بالا:",
    alertLowerShort = "پایین:",
    alertTestButton = "تست هشدار",
    alertTestSentNotice = "اعلان آزمایشی ارسال شد",
    alertNotificationsDisabledBanner = "اعلان‌های برنامه در تنظیمات اندروید غیرفعال است؛ برای دریافت هشدار در پس‌زمینه آن را فعال کنید.",
    alertSaveButton = "ذخیره هشدار",
    alertDeleteButton = "حذف هشدار",
    setPriceAlertButton = "تنظیم هشدار قیمت",
    alertNotificationChannelName = "هشدارهای قیمت نرخ‌نما",
    alertNotificationChannelDesc = "اعلان عبور قیمت دارایی‌ها از آستانه‌های انتخاب‌شده",
    alertNotificationUpperText = "به حد بالای تعیین‌شده رسید",
    alertNotificationLowerText = "به حد پایین تعیین‌شده رسید",
    alertTestNotificationTitle = "تست هشدار قیمت نرخ‌نما",
    alertTestNotificationBody = "اعلان‌های هشدار قیمت به‌درستی پیکربندی شده‌اند.",
    comparisonSectionTitle = "مقایسه چندمنبعی نرخ‌ها",
    comparisonSectionSubtitle = "نمایش اختلاف لحظه‌ای بین منابع معتبر بازار آزاد روی کارت‌ها",
    comparisonDivergenceTag = "اختلاف > ۰.۵٪",
    fetchedAtTimestampLabel = "زمان دریافت",
    comparisonVsPrefix = "نسبت به",
    freshnessPrefix = "بروزرسانی:",
    freshnessJustNow = "هم‌اکنون",
    freshnessMinutesAgoSuffix = "دقیقه پیش",
    freshnessHoursAgoSuffix = "ساعت پیش",
    freshnessDaysAgoSuffix = "روز پیش"
)

val EnStrings = ChandStrings(
    appTitle = "Nerkhnama",
    marketTab = "Markets",
    converterTab = "Converter",
    liveBadge = "LIVE",
    delayedBadge = "DELAYED",
    cachedBadge = "CACHED",
    unavailableBadge = "N/A",
    disputedLabel = "Disputed",
    singleSourceLabel = "1 Source",
    updating = "Updating...",
    delayedBanner = "Delayed quotes — source timestamps are older than threshold",
    offlineCachedBanner = "Offline — showing last cached rates",
    offlineReferenceBanner = "Offline — baseline reference rates (connect to sync live)",
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
    intrinsicEstimateLabel = "Intrinsic (est.)",
    bubbleEstimateLabel = "Bubble (est.)",
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
    categoryOil = "Crude Oil",
    tomanAssetName = "Iranian Toman",
    chartInteractiveHint = "Drag across the chart to inspect price points",
    widgetSection = "Home Screen Widgets",
    widgetTranslucentLabel = "Translucent Glass Style",
    widgetOpacityLabel = "Glass Opacity",
    widgetHeroAssetLabel = "1×1 Hero Widget Asset",
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
    downloadUpdateButton = "Download & Install",
    viewReleaseButton = "Release Page",
    githubRepoLabel = "GitHub Repository (owner/repo)",
    githubRepoPlaceholder = "thepoo7an/chand-iran-currency-gold-crypto",
    updateRepoMissingHint = "Enter your GitHub repository (owner/repo) to check releases.",
    updateCheckErrorLabel = "Could not check GitHub Releases",
    laterButton = "Later",
    updateCatalogTitle = "Update Catalog",
    changelogTitle = "What's New in This Version",
    changelogEmptyFallback = "Performance improvements, stability enhancements, and bug fixes.",
    newVersionLabel = "New Version",
    downloadingUpdateLabel = "Downloading update inside app...",
    downloadCompleteLabel = "Download complete — ready to install",
    installUpdateButton = "Install Update",
    grantInstallPermissionButton = "Allow App Installs",
    installPermissionHint = "Enable \"Install unknown apps\" in Android settings to finish installing.",
    cancelDownloadButton = "Cancel",
    downloadFailedLabel = "Could not download update package",
    retryDownloadButton = "Retry Download",
    openUpdateCatalogButton = "View Changelog & Update",
    openInBrowserFallback = "Download in Browser",
    publishedAtPrefix = "Published:",
    networkUnavailableBanner = "No internet connection and no cached rates available",
    liveConfigSection = "Online Live Patch & Rules (No APK Install)",
    liveConfigSubtitle = "Apply API mirror fixes, new currencies, validation thresholds, and feature flags live without downloading a new APK",
    liveConfigSyncButton = "Sync Online Rules",
    liveConfigSyncingLabel = "Syncing...",
    liveConfigResetButton = "Reset Built-in",
    liveConfigBuiltInLabel = "Using built-in default rules",
    liveConfigVersionPrefix = "Live Config v",
    liveConfigLastSyncPrefix = "Last synced:",
    liveConfigUrlLabel = "Custom Live Config URL (Optional HTTPS)",
    liveConfigUrlPlaceholder = "Blank = auto-fetch from GitHub / jsDelivr CDN",
    liveConfigSyncedToast = "Online rules applied cleanly to current version",
    liveConfigNoRemoteLabel = "No remote chand-live-config.json published yet — using built-in rules",
    liveConfigIncompatibleLabel = "Remote config targets a newer app version",
    liveConfigErrorLabel = "Could not fetch online configuration",
    liveConfigChangesTitle = "Live Changes Applied Now",
    liveConfigOverridesActiveLabel = "active rule(s)",
    liveConfigDefaultSummary = "Online endpoints and market rules are up to date",
    chartsDisabledNotice = "Historical charts are temporarily paused via online configuration",
    updateErrorNetwork = "Network error while fetching update. Check your connection and retry.",
    updateErrorInvalidUrl = "Invalid or untrusted update download URL.",
    updateErrorHttpsFailed = "HTTPS verification failed or an insecure HTTP redirect downgrade was blocked.",
    updateErrorChecksumMismatch = "SHA-256 checksum mismatch — downloaded APK does not match trusted release metadata.",
    updateErrorMetadataInvalid = "Release verification metadata is missing, incomplete, or inconsistent.",
    updateErrorCorruptedApk = "Downloaded APK archive is corrupted or structurally invalid.",
    updateErrorWrongPackage = "APK package name does not match ir.thepoo7an.chand.",
    updateErrorOlderVersion = "APK version code is not newer than the currently installed build.",
    updateErrorUnexpectedSigner = "APK signing certificate does not match the trusted production release key.",
    updateErrorInsufficientStorage = "Insufficient device storage available to download and verify the update.",
    updateErrorUnreasonableSize = "Update APK size is outside allowed safety bounds.",
    updateErrorIncompleteDownload = "Downloaded APK was empty or incomplete. Please retry.",
    updateErrorInstallFailed = "Could not launch the Android package installer.",
    sourceBreakdownTitle = "Market Source Breakdown",
    sourceBreakdownSubtitle = "Reported prices, source timestamps, and inclusion status across active providers",
    calculatedMainPriceLabel = "Calculated Main Price",
    primaryMainPriceLabel = "Displayed Main Price",
    lastSuccessfulUpdateLabel = "Last Successful Update",
    configuredSourcesCountPrefix = "Configured Sources:",
    sourceTimestampLabel = "Source Timestamp",
    sourceTimestampUnavailable = "Source timestamp unavailable",
    sourceContributedYes = "Contributed to final price",
    sourceContributedNo = "Did not contribute to final price",
    sourcePrimaryTag = "Primary",
    sourceRawQuotePrefix = "Raw quote:",
    sourceUnitCurrencyToman = "Toman (TMN)",
    sourceStatusIncluded = "INCLUDED",
    sourceStatusRejected = "REJECTED",
    sourceStatusDelayed = "DELAYED",
    sourceStatusUnavailable = "UNAVAILABLE",
    sourceStatusInvalid = "INVALID",
    exclusionReasonValidationFailure = "Validation failure",
    exclusionReasonDisagreement = "Excessive disagreement",
    exclusionReasonStale = "Stale data",
    exclusionReasonInvalid = "Invalid response",
    exclusionReasonMissingData = "Missing data",
    exclusionReasonProviderFailure = "No provider response",
    sourceNoRecordsNotice = "Per-source details are unavailable for offline reference baseline rates.",
    sourceTapHint = "View sources",
    usdPerBarrelUnit = "USD / barrel",
    usdPerBarrelShortUnit = "USD/bbl",
    oilTomanEstimatePrefix = "Est. via USD/TMN:",
    change24hUnavailable = "24h chg N/A",
    sourceUnitCurrencyUsdPerBarrel = "USD / barrel",
    chartModeLine = "Line",
    chartModeBar = "Bar",
    chartPointsSuffix = "pts",
    chartGapDetectedNotice = "Data gap",
    periodChangeLabel = "Period Change",
    change24hLabel = "24h Change",
    supportDevCardTitle = "Support Development",
    supportDevCardSubtitle = "Nerkhnama is 100% open-source with zero ads or trackers. Star the repository on GitHub.",
    supportDevOpenRepo = "Open GitHub Repo",
    supportCardToggleLabel = "Show \"Support development\" card on Market feed",
    lockScreenPreAndroid12Notice = "Lock-screen (keyguard) widgets require Android 12+ (API 31+). On this OS version, the widget installs on your Home screen.",
    alertsTitle = "Price Alerts",
    alertsSubtitle = "Checked every 30 min on network connection while active alerts exist",
    alertsEmptyTitle = "No price alerts yet",
    alertsEmptySubtitle = "Set an upper or lower Toman target for USD, Gold, Coins, or Crypto.",
    addAlertButton = "Add Alert",
    createAlertTitle = "New Price Alert",
    editAlertTitle = "Edit Price Alert",
    alertUpperBoundLabel = "Upper Target (TMN)",
    alertLowerBoundLabel = "Lower Target (TMN)",
    alertBoundHint = "Enter an upper target, a lower target, or both in Toman.",
    alertInvalidBoundsError = "Enter positive Toman thresholds (upper must exceed lower).",
    alertEnabledLabel = "Enabled",
    alertRearmButton = "Re-arm",
    alertArmedStatus = "Monitoring",
    alertTriggeredStatus = "Triggered",
    alertUpperShort = "Above:",
    alertLowerShort = "Below:",
    alertTestButton = "Test alert",
    alertTestSentNotice = "Test alert notification sent",
    alertNotificationsDisabledBanner = "Notifications are disabled for Nerkhnama in Android settings; enable them to receive background alerts.",
    alertSaveButton = "Save Alert",
    alertDeleteButton = "Delete",
    setPriceAlertButton = "Set Price Alert",
    alertNotificationChannelName = "Nerkhnama Price Alerts",
    alertNotificationChannelDesc = "Notifies when tracked market rates cross your target bounds",
    alertNotificationUpperText = "Crossed above target",
    alertNotificationLowerText = "Crossed below target",
    alertTestNotificationTitle = "Nerkhnama Test Alert",
    alertTestNotificationBody = "Price alert notifications are working on this device.",
    comparisonSectionTitle = "Multi-Source Price Comparison",
    comparisonSectionSubtitle = "Show subtle live spread badges across Tehran providers & benchmarks",
    comparisonDivergenceTag = "Spread > 0.5%",
    fetchedAtTimestampLabel = "Fetched At",
    comparisonVsPrefix = "vs",
    freshnessPrefix = "Updated",
    freshnessJustNow = "just now",
    freshnessMinutesAgoSuffix = "m ago",
    freshnessHoursAgoSuffix = "h ago",
    freshnessDaysAgoSuffix = "d ago"
)

fun stringsFor(language: AppLanguage): ChandStrings =
    if (language == AppLanguage.FA) FaStrings else EnStrings

object NumberFormatters {
    private val usSymbols = DecimalFormatSymbols(Locale.US)
    private val integerFmt = DecimalFormat("#,##0", usSymbols)
    private val twoDecimalFmt = DecimalFormat("#,##0.##", usSymbols)
    private val fourDecimalFmt = DecimalFormat("#,##0.####", usSymbols)
    private val eightDecimalFmt = DecimalFormat("#,##0.########", usSymbols)

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
            !amount.isFinite() -> "0"
            abs(amount) >= 100.0 -> integerFmt.format(amount)
            abs(amount) >= 1.0 -> twoDecimalFmt.format(amount)
            else -> fourDecimalFmt.format(amount)
        }
        return applyDigits(formatted, usePersianDigits)
    }

    /**
     * High-precision formatter for Converter outputs (supports small fractional crypto units
     * like Toman -> 0.00001425 BTC while keeping clean grouping for large values).
     */
    fun formatConverterAmount(amount: Double, usePersianDigits: Boolean): String {
        val absAmt = abs(amount)
        val formatted = when {
            !amount.isFinite() || absAmt == 0.0 -> "0"
            absAmt >= 100.0 -> integerFmt.format(amount)
            absAmt >= 1.0 -> twoDecimalFmt.format(amount)
            absAmt >= 0.01 -> fourDecimalFmt.format(amount)
            else -> eightDecimalFmt.format(amount)
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

    fun formatUsdDeltaWithArrow(
        changeUsd: Double?,
        changePct: Double,
        usePersianDigits: Boolean
    ): String {
        val arrow = when {
            (changeUsd != null && changeUsd > 0.0001) || changePct > 0.005 -> "↑"
            (changeUsd != null && changeUsd < -0.0001) || changePct < -0.005 -> "↓"
            else -> "• "
        }
        val body = if (changeUsd != null && changeUsd.isFinite() && abs(changeUsd) >= 0.01) {
            "$${twoDecimalFmt.format(abs(changeUsd))}"
        } else if (changePct.isFinite() && abs(changePct) > 0.005) {
            "${String.format(Locale.US, "%.2f", abs(changePct))}%"
        } else {
            "0.00%"
        }
        return applyDigits("$arrow$body", usePersianDigits)
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
