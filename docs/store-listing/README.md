# Store-Listing Package — Nerkhnama (نرخ‌نما)

This directory contains ready-to-upload, store-agnostic branding copy and visual assets for Iranian Android app stores (**Cafe Bazaar** and **Myket**) without introducing any proprietary store SDKs, ads, analytics, or extra Android permissions.

## Directory Inventory

| File / Path | Type | Dimensions / Spec | Purpose |
| --- | --- | --- | --- |
| `cafebazaar-fa.md` | Markdown | Title <= 30 chars, Short <= 80 chars | Persian Cafe Bazaar listing copy & tags |
| `myket-fa.md` | Markdown | Title <= 30 chars, Short <= 80 chars | Persian Myket listing copy & tags |
| `icon-512.png` | PNG (RGBA) | `512 x 512` | High-res store app icon |
| `feature-graphic-1-1280x720.png` | PNG (RGBA) | `1280 x 720` | Primary feature graphic (market tracking & transparency) |
| `feature-graphic-2-1280x720.png` | PNG (RGBA) | `1280 x 720` | Secondary feature graphic (widgets, alerts & freshness) |
| `screenshots/01-market-bento-amoled.png` | PNG (RGBA) | `1080 x 2400` | AMOLED Persian Bento grid with freshness & spread deltas |
| `screenshots/02-asset-chart-jalali.png` | PNG (RGBA) | `1080 x 2400` | Asset detail sheet with honest Jalali history chart |
| `screenshots/03-source-breakdown.png` | PNG (RGBA) | `1080 x 2400` | Multi-source breakdown showing source vs fetch timestamps |
| `screenshots/04-price-alerts.png` | PNG (RGBA) | `1080 x 2400` | Per-asset TMN upper/lower price alerts & re-arm controls |
| `screenshots/05-currency-converter.png` | PNG (RGBA) | `1080 x 2400` | Currency/gold/crypto converter & immediate market equivalents |
| `screenshots/06-widgets-and-lockscreen.png` | PNG (RGBA) | `1080 x 2400` | Home-screen & lock-screen (API 31+) Glance widgets showcase |

## Zero-SDK & Policy Checklist

- **No Store SDKs:** `app/build.gradle.kts` does NOT include `com.farsitel.bazaar`, `ir.myket`, or any store billing/update/analytics SDK.
- **No Ads or Trackers:** Zero ad networks, zero Firebase/Crashlytics/telemetry dependencies, and the optional Support Development card is hidden by default (`showSupportCard = false`).
- **Minimal Permissions:** `AndroidManifest.xml` declares only `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (price alerts on Android 13+), and the pre-existing `REQUEST_INSTALL_PACKAGES` (required solely for optional GitHub release in-app updates). No location, camera, contacts, storage or telephony permissions. Note that `cafebazaar-fa.md` and `myket-fa.md` publish standard release APKs directly via the CI workflow (`.github/workflows/release-apk.yml`).
- **License:** MIT (`thepoo7an/chand-iran-currency-gold-crypto`).
