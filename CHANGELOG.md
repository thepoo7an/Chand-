# Changelog

All notable changes to **Nerkhnama (نرخ‌نما)** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Price Alerts (WorkManager)**: Per-asset upper and/or lower TMN alert thresholds stored in DataStore, a dedicated Alerts management screen (`Alerts`), per-crossing notification alerts without extra manifest permissions, manual re-arm, and a "Test alert" action.
- **Multi-Source Price Comparison**: Preserved individual Tehran free-market provider quotes (`TGJU`, `Wallex`, `Bitpin`, `Nobitex`) and global benchmark cross-checks, surfaced subtle comparison delta chips on asset cards/rows with divergence flagging (`> 0.5%`), and linked directly to the source breakdown bottom sheet showing both source and fetch timestamps.
- **Per-Asset Data Freshness Indicator**: Relative freshness age chips across asset cards, list rows, and all 5 Glance widgets (`<= 30m` normal, `> 30m` amber, `> 2h` red) backed by genuine fetch timestamps.
- **Support Development Card**: Optional open-source repository link card hidden by default behind a Settings toggle (zero ads/analytics preserved).
- **Iranian Store-Listing Prep**: Added Cafe Bazaar and Myket localized store copy, icons, feature graphics, and screenshots under `docs/store-listing/`.

### Changed
- **Lock-Screen Widget Compatibility**: Split `res/xml/chand_widget_lockscreen_info.xml` (pre-Android 12 home-screen fallback) and `res/xml-v31/chand_widget_lockscreen_info.xml` (`keyguard|home_screen`), plus an Android 12+ notice in Settings on older OS versions.
- **History Gap Detection & Documentation**: Required at least 5 points before inferring historical chart gaps, and documented category/range spike-removal thresholds in code.
- **Versioning & Release Automation**: Aligned local fallback `versionName` to `1.0.0-dev`, logged resolved build version metadata during Gradle configuration, fetched full git history in GitHub Actions, and appended commit ranges to GitHub Release bodies.

## [1.0.0] - 2026-11-20

### Added
- Initial **Nerkhnama (نرخ‌نما)** release tracking Iranian Toman (`TMN`) rates for 13 fiat currencies, 9 gold/coin assets, global crude oil benchmarks (Brent & WTI), and 8 cryptocurrencies.
- Multi-provider aggregation (`TGJU`, `Wallex`, `Bitpin`, `Nobitex`, `Binance`, `Frankfurter`, `Currency-API`, `Gold-API`) with transparent source attribution and breakdown modal.
- Interactive `24H` / `7D` / `1M` / `1Y` historical charts (Line & Bar modes) with Jalali dates and per-card 7-day sparklines.
- 5 Jetpack Glance widgets (1×1 Hero, 2×1 Translucent Glass, 4×2 Watchlist, 2×2 Crypto, Transparent Lock-Screen) with connectivity-aware sync.
- Bilingual Persian (RTL) & English (LTR) interface, AMOLED Pure Black / Dark / Light themes, built-in currency converter, and GitHub Releases in-app update checker.
