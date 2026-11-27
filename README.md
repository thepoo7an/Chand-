<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="96" height="96" alt="Nerkhnama App Icon" />

# Nerkhnama (نرخ‌نما) — Iran Currency, Gold, Oil & Crypto Tracker

**AMOLED live currency, gold, coin, crude oil & crypto rate tracker in Iranian Toman (TMN)** — zero ads, zero trackers, native Kotlin & Jetpack Compose with Home & Lock Screen Widgets.

**اپلیکیشن متن‌باز و بدون تبلیغات نرخ‌نما برای ردیابی لحظه‌ای قیمت دلار، ارز، طلا، سکه، نفت خام و رمزارز به تومان — همراه با نمودار خطی و میله‌ای با تاریخچه شمسی، مبدل سریع و ویجت‌های صفحه اصلی و لاک‌اسکرین.**

[🇬🇧 English Overview](#-overview) • [🇮🇷 معرفی و راهنمای فارسی](#-معرفی-فارسی-برنامه-نرخنما) • [⬇️ Download APK](#-download-latest-apk) • [📸 Screenshots](#-screenshots)

<p>
  <a href="https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/latest">
    <img src="https://img.shields.io/github/v/release/thepoo7an/chand-iran-currency-gold-crypto?style=for-the-badge&color=10B981&label=Latest%20APK" alt="Latest Release" />
  </a>
  <a href="https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases">
    <img src="https://img.shields.io/github/downloads/thepoo7an/chand-iran-currency-gold-crypto/total?style=for-the-badge&color=3B82F6&label=Downloads" alt="Total Downloads" />
  </a>
  <a href="https://github.com/thepoo7an/chand-iran-currency-gold-crypto/stargazers">
    <img src="https://img.shields.io/github/stars/thepoo7an/chand-iran-currency-gold-crypto?style=for-the-badge&color=F59E0B" alt="GitHub Stars" />
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Android-7.0%2B%20(API%2024--36)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 24+" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose%20%2B%20Glance-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin & Jetpack Compose" />
  <img src="https://img.shields.io/badge/Ads%20%26%20Trackers-Zero%20(100%25%20Clean)-10B981?style=flat-square" alt="Zero Ads" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="MIT License" />
  <a href="../../actions/workflows/release-apk.yml">
    <img src="../../actions/workflows/release-apk.yml/badge.svg" alt="Android CI / Release APKs" />
  </a>
</p>

</div>

---

## ⬇️ Download Latest APK

Grab the latest signed release directly from **[GitHub Releases](https://github.com/thepoo7an/chand-iran-currency-gold-crypto/releases/latest)**:

| Build Variant | Recommended For | Filename |
| :--- | :--- | :--- |
| **`arm64-v8a`** *(Recommended)* | 99% of modern 64-bit Android phones (Android 10–17) | `Chand-arm64-v8a-release.apk` |
| **`armeabi-v7a`** | Older 32-bit Android phones | `Chand-armeabi-v7a-release.apk` |
| **`universal`** | Compatible with every Android CPU architecture | `Chand-universal-release.apk` |

> **Tip for clean updates:** Install the same ABI variant (`arm64-v8a` recommended) for subsequent updates. The app also checks GitHub Releases natively inside **Settings → App Updates** and opens your matching ABI APK in the browser with zero extra permissions.

---

## 📸 Screenshots

| Live App (AMOLED & Interactive 1Y Chart) | Market Bento Grid | Home Screen Widgets | Lock Screen Monochrome Widget |
| :---: | :---: | :---: | :---: |
| <img src="public/assets/aistudio/Screenshot_20261120-210120_fe55c11b.png" width="210" alt="Chand?! Android AMOLED Live Chart" /> | <img src="public/assets/aistudio/Screenshot_20261120-193949_9e4139ba.png" width="210" alt="Bento Market Grid" /> | <img src="public/assets/aistudio/Screenshot_20261120-194010_0b57bed1.png" width="210" alt="Home Screen Widgets" /> | <img src="public/assets/aistudio/Screenshot_20261120-214514_abb7dc2e.png" width="210" alt="Lock Screen Transparent Widget" /> |

---

## ✨ Overview

**Nerkhnama (نرخ‌نما)** (package `ir.thepoo7an.chand` preserved for seamless updates) is a lightweight, privacy-respecting open-source Android tracker built for checking live **Iranian Toman (TMN)** prices across Tehran's free currency market, Iranian gold & coins, global crude oil, and cryptocurrencies.

### Why Nerkhnama?
- **100% Free, Open Source & Zero Ads:** No banner ads, no forced interstitials, no analytics trackers, and **zero dangerous Android permissions** (only `INTERNET` and `ACCESS_NETWORK_STATE`).
- **True Multi-Provider Live Aggregation:** Combines **TGJU** (Tehran Free Market), **Wallex**, **Bitpin**, **Nobitex**, **Binance**, **Frankfurter (ECB)**, **Currency-API**, and **Gold-API** — with transparent rate-basis badges so you always know the exact pricing source.
- **Complete Market Coverage:**
  - **Fiat Currencies:** US Dollar (`USD`), Euro (`EUR`), UAE Dirham (`AED`), British Pound (`GBP`), Turkish Lira (`TRY`), Kuwaiti Dinar (`KWD`), Canadian Dollar (`CAD`), Swiss Franc (`CHF`), Chinese Yuan (`CNY`), Australian Dollar (`AUD`), Japanese Yen (`JPY`), Saudi Riyal (`SAR`), Iraqi Dinar (`IQD`).
  - **Gold & Iranian Coins:** 18K Gold (`GRAM`), 24K Gold (`GRAM24`), Gold Mesghal (`MESGHAL`), Emami Coin (`SEKE`), Bahar Azadi (`BAHAR`), Half Coin (`HALF`), Quarter Coin (`QUARTER`), Gerami Coin (`GERAMI`), Global Gold Ounce (`XAU`).
  - **Cryptocurrencies:** Tether (`USDT`), Bitcoin (`BTC`), Ethereum (`ETH`), Toncoin (`TON`), Solana (`SOL`), BNB (`BNB`), XRP (`XRP`), Dogecoin (`DOGE`).
- **Real Historical Charts (`24H` / `7D` / `1M` / `1Y`) & Per-Currency Sparklines:** Interactive touch scrubbing with exact **Jalali** dates/times, range high/low metrics, and currency-specific 7-day ECB/TGJU card sparklines.
- **5 Native Jetpack Glance Widgets (Home & Lock Screen):**
  - **1×1 Hero Widget** *(customizable asset in Settings — USD by default)*
  - **2×1 Compact Glass Widget** (`USD • EUR • 18K Gold` with **Translucent Glass** opacity presets: `50%`, `70%`, `85%`)
  - **4×2 Live Watchlist Widget** (pinned favorites)
  - **2×2 Crypto Watch Widget** (`BTC • ETH • USDT`)
  - **Transparent Lock Screen Widget** (`keyguard|home_screen`, monochrome background-free typography inspired by Nothing OS)
- **Instant Connectivity & 30-Minute Periodic Widget Sync:** Automatically refreshes widgets when moving from offline to online (`ConnectivityManager.NetworkCallback`) and schedules periodic `JobScheduler` updates while widgets are placed.
- **AMOLED Pure Black, Dark & Light Themes + Bilingual UI:** Built natively in **Persian (RTL)** and **English (LTR)** with optional Persian digits (`۱۲۳`), 2-column Bento Grid or List view, and a built-in quick currency converter keypad.

---

## 🇮🇷 معرفی فارسی برنامه نرخ‌نما

**نرخ‌نما (Nerkhnama)** یک اپلیکیشن **رایگان، متن‌باز و بدون تبلیغات** برای پیگیری لحظه‌ای **قیمت دلار آزاد، ارزهای رایج، طلای ۱۸ و ۲۴ عیار، مثقال طلا، انواع سکه، نفت خام جهانی (Brent و WTI) و رمزارزها** به **تومان** است. (شناسه بسته `ir.thepoo7an.chand` برای حفظ سازگاری بروزرسانی‌ها بدون تغییر باقی مانده است.)

### امکانات کلیدی نرخ‌نما
- **بدون تبلیغات و کاملاً امن:** بدون هیچ دسترسی حساس یا ردیاب تبلیغاتی؛ فقط دسترسی اینترنت برای دریافت نرخ‌های لحظه‌ای.
- **منابع معتبر و شفاف بازار آزاد:** تجمیع خودکار نرخ‌ها از **TGJU، والکس، بیت‌پین، نوبیتکس، بایننس، بانک مرکزی اروپا (Frankfurter) و Gold-API** با نمایش دقیق منبع نرخ‌گذاری در بالای صفحه.
- **۵ ویجت حرفه‌ای صفحه اصلی و لاک‌اسکرین:**
  - **ویجت ۱×۱ تک‌نرخی:** امکان انتخاب دارایی دلخواه از تنظیمات (دلار، یورو، طلا، مثقال، سکه امامی، بیت‌کوین و...).
  - **ویجت ۲×۱ شیشه‌ای (Translucent):** نمایش هم‌زمان دلار، یورو و طلای ۱۸ عیار با تنظیم شفافیت (`۵۰٪`، `۷۰٪`، `۸۵٪`).
  - **ویجت ۴×۲ برگزیده‌ها** و **ویجت ۲×۲ رمزارزها** همراه با نمایش ساعت آخرین بروزرسانی.
  - **ویجت شفاف لاک‌اسکرین (Nothing Style):** بدون پس‌زمینه و با خوانایی بالا روی والپیپر صفحه قفل.
- **همگام‌سازی خودکار ویجت‌ها:** به‌محض اتصال گوشی به اینترنت یا هر ۳۰ دقیقه در پس‌زمینه (فقط در صورت وجود ویجت فعال) نرخ‌ها بروزرسانی می‌شوند.
- **نمودار واقعی بازه‌های `۲۴ ساعت`، `۷ روز`، `۱ ماه` و `۱ سال`:** لمس روی نمودار برای مشاهده قیمت دقیق با **تاریخ شمسی** و نمایش روند اختصاصی هر ارز روی کارت‌ها.
- **مبدل سریع ارز و طلا:** تبدیل فوری هر ارز، طلا، سکه یا رمزارز به تومان و سایر دارایی‌ها با صفحه‌کلید اختصاصی.
- **تم مشکی مطلق AMOLED + فارسی (راست‌چین) و انگلیسی:** همراه با پشتیبانی از ارقام فارسی (`۱۲۳`) و قابلیت بررسی و دانلود نسخه جدید مستقیم از داخل تنظیمات.

---

## 🛠 Building from Source

### Prerequisites
- **JDK 21** (Temurin / OpenJDK)
- **Android SDK** (`compileSdk 36`, `minSdk 24`, `targetSdk 36`)
- **Gradle 9.3.1** (installed on `PATH`)

### Commands

1. **Run Unit & Robolectric Tests**
   ```bash
   gradle :app:testDebugUnitTest
   ```

2. **Build Debug APK**
   ```bash
   gradle :app:assembleDebug
   ```

3. **Build ABI-Split Release APKs (Universal + `arm64-v8a` + `armeabi-v7a`)**
   ```bash
   KEYSTORE_PATH=/path/to/your-upload-key.jks \
   STORE_PASSWORD=your_store_password \
   KEY_PASSWORD=your_key_password \
   gradle :app:assembleRelease -PenableAbiSplits=true
   ```
   Output APKs are written to `app/build/outputs/apk/release/`.

### Automated GitHub Actions Releases
The workflow in `.github/workflows/release-apk.yml` automatically runs unit & Robolectric tests, increments `versionCode` from `github.run_number` (and `versionName` from `v*` tags or `1.0.<run_number>`), builds all three ABI APKs (`arm64-v8a`, `armeabi-v7a`, `universal`), and publishes a GitHub Release on every push to `main`/`master` or `v*` tag.

Add these Repository Secrets under **Settings → Secrets and variables → Actions** (alias `upload`):
- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

---

## ⭐ Support & Feedback
If **Nerkhnama (نرخ‌نما)** helps you track daily rates faster without ads, please **give this repository a Star ⭐** so more people can discover it!
Found a bug or want another currency/coin added? **[Open an Issue](https://github.com/thepoo7an/chand-iran-currency-gold-crypto/issues/new/choose)**.

---

## 🧰 Tech Stack
- **Language:** Kotlin + Coroutines & Flow
- **UI:** Jetpack Compose, Material 3, Navigation Compose (Type-Safe Routes), Jetpack Glance AppWidgets
- **Networking & Serialization:** Retrofit 2, OkHttp, Moshi KSP Codegen (`kotlin-reflect`-free), Kotlinx Serialization
- **Persistence:** Jetpack DataStore Preferences
- **Testing:** JUnit 4, Robolectric, Compose UI Test
- **License:** [MIT License](LICENSE)
