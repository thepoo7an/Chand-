# Changelog

All notable changes to **Nerkhnama (نرخ‌نما)** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Markets-screen clarity pass**: the brand title can no longer wrap mid-word (`maxLines = 1` + a size ladder that steps down to 23sp at 320dp), every price surface shares **one** formatting policy (`< 1M` full integer with separators, `≥ 1M` compact with two decimals — `255.56M`, `12.9M`) including sparkline axis labels, widgets and the converter, delta chips show the absolute move **and** its percentage (`↑322.3K • +1.2%`), the header clock is replaced by a functional `Last sync: 09:37` / «آخرین همگام‌سازی: ۰۹:۳۷» line, long provider attributions (`Calculated Median (Frankfurter)`) get short labels (`Median (ECB)` / «میانگین (ECB)»), the category chip row keeps its 16dp insets so the first and last chip are never clipped, and the long provider sentence is replaced by `Sources: N` plus an ℹ️ button that opens a **data sources sheet** (each provider, when it was last fetched, and the assets it feeds) — correct in RTL and LTR.
- **Gold unit benchmark**: every gold and coin quote is cross-checked against a benchmark derived from spot XAU and the USD rate the app already fetched (`gram24 = XAU/31.1035 × USD_TMN`, `gram18 = gram24 × 0.750`, coins and the mesghal as documented multiples of the 18K gram from `COIN_SPECS`). A deviation above 20% sets the existing `disputed` flag **and** the new `unitMismatch` flag, and the card shows a «بازبینی واحد» / "Check unit" badge instead of presenting the number as fact. The check is skipped silently unless both XAU and USD are fresh and non-zero, and the price itself is never rewritten.
- **Cache-first feed and widgets (stale-while-revalidate)**: the persisted snapshot is always rendered first and the network is only consulted when it is actually stale. A snapshot younger than a 30-second soft TTL (`SnapshotPolicy.SOFT_TTL_MS`) is served without any provider call, so a cold start after Android killed the backgrounded process no longer re-runs a full multi-provider fetch for rates that are seconds old; older snapshots are shown immediately with the refresh indicator while the revalidation runs. The five widgets share the same policy instead of three separate `30_000L` literals, and widget-only refreshes now report success/failure to the live-config self-healing streak (previously only in-app refreshes did, so a widget-only user could never trigger a rollback).
- **Price Alerts (WorkManager)**: Per-asset upper and/or lower TMN alert thresholds stored in DataStore, a dedicated Alerts management screen (`Alerts`), per-crossing notifications (with the Android 13+ `POST_NOTIFICATIONS` runtime-permission flow), manual re-arm, and a "Test alert" action that reports the real delivery result.
- **Multi-Source Price Comparison**: Preserved individual Tehran free-market provider quotes (`TGJU`, `Wallex`, `Bitpin`, `Nobitex`) and global benchmark cross-checks, surfaced subtle comparison delta chips on asset cards/rows with divergence flagging (`> 0.5%`), and linked directly to the source breakdown bottom sheet showing both source and fetch timestamps.
- **Per-Asset Data Freshness Indicator**: Relative freshness age chips across asset cards, list rows, and all 5 Glance widgets (`<= 30m` normal, `> 30m` amber, `> 2h` red) backed by genuine fetch timestamps.
- **Support Development Card**: Optional open-source repository link card hidden by default behind a Settings toggle (zero ads/analytics preserved).
- **Iranian Store-Listing Prep**: Added Cafe Bazaar and Myket localized store copy, icons, feature graphics, and screenshots under `docs/store-listing/`.

### Fixed
- **Contradictory trend data on asset cards**: a missing or stale history no longer surfaces as `DELAYED` + `Updated just now` + a sparkline + a `• 0` delta chip. A new `AssetTrendPresentation` hides the sparkline and the delta chip unless there is real evidence (two or more usable points on a non-delayed quote; a non-zero change, or a genuinely flat series where `0` is the honest answer), and `DELAYED`/`CACHED` badges are suppressed while the age chip still says the data is fresh.
- **Price alerts on Android 13+**: Declared and request the `POST_NOTIFICATIONS` runtime permission, added an actionable "Enable notifications" banner (system settings deep link), and made the test-alert action surface failures instead of always reporting success.
- **Widget background sync after reboot**: Moved the 30-minute periodic widget refresh from a non-persisted `JobScheduler` job to persisted `WorkManager`, so it survives reboots and app upgrades.
- **Backup / restore**: Backup and device-transfer rules now include the DataStore file that actually holds theme, language, pins, watchlist and price alerts (they previously pointed only at `sharedpref`).
- **Fiat price safety**: The 100×/1000× TGJU unit normalisation is now applied only to `JPY`/`IQD` (the two symbols TGJU quotes in 100/1000 units). Other currencies keep their raw quote so a genuine cross-source divergence is flagged as `disputed` instead of being silently rescaled into a plausible-but-wrong price.
- **`EIA_API_KEY` is now build-injected**: Android apps cannot read shell environment variables, so the oil spot validation key is baked into `BuildConfig` from `EIA_API_KEY` / `-PeiaApiKey=` instead of an always-empty `System.getenv` lookup.
- Notification small icon now uses the monochrome vector instead of a launcher mipmap; removed a dead `&& false` expression in the update metadata fallback path.
- **Language switch no longer overrides the digit style**: an explicit Persian/Latin digit choice is remembered (new `use_persian_digits_explicit` flag) instead of being reset every time the UI language changes.
- **Silent failures are now diagnosable**: provider, live-config, widget-sync and background refresh errors are logged through a debug-only `AppLog` facade (release builds stay silent).

### Changed
- **Provider split**: Bitpin quotes into `BitpinMarketService` and Wallex quotes **plus** UDF history into `WallexMarketService`, both out of the 1,600-line `MarketRemoteDataSource` (endpoint, parsing and failure semantics unchanged), each with recorded-payload contract tests (`fixtures/bitpin/`, `fixtures/wallex/`) covering Toman/USD sides, `TMN`-vs-`IRT` quote currencies, root-timestamp fallback, epoch-seconds timestamps, zero prices, unusable series and malformed payloads. Shared payload helpers now live in `ProviderParsing`, and `docs/provider-split.md` documents the seam and the remaining providers.
- **Lock-Screen Widget Compatibility**: Split `res/xml/chand_widget_lockscreen_info.xml` (pre-Android 12 home-screen fallback) and `res/xml-v31/chand_widget_lockscreen_info.xml` (`keyguard|home_screen`), plus an Android 12+ notice in Settings on older OS versions.
- **History Gap Detection & Documentation**: Required at least 5 points before inferring historical chart gaps, and documented category/range spike-removal thresholds in code.
- **Signed live configuration**: `chand-live-config.json` now carries an ECDSA P-256 signature (`signature` + `keyId`) verified against a pinned trust store (`gradle/live-config-keys.properties`) before any endpoint, threshold or asset catalog change is applied; tampered, unknown-key and unsigned configs are rejected and the previous config is kept. Signing/verification tooling lives in `scripts/` and the committed config is verified by both CI and a unit test.
- **Live-config self-healing**: three consecutive market refreshes with no live data now roll an active remote config back to the built-in defaults, remember the rolled-back `configVersion` (so a mirror still serving the broken document cannot undo the recovery), and clear the state on an explicit reset; a successful refresh resets the failure streak.
- **Live-config expiry**: an optional signed `expiresAtIso` retires a publication — expired configs are neither applied nor kept after the next hydration. The check is fail-closed, so the expiry instant itself already counts as expired.
- **Live-config URL hardening**: Credential-bearing URLs, IP literals, loopback/private/link-local hosts and malformed DNS labels are now rejected before an endpoint override can be applied.
- **CI**: Pull requests now also build the minified release variant (R8 / keep-rule verification), run Android Lint, upload test/lint reports, and use the checked-in `./gradlew` wrapper (which is now executable).
- **Live-config signature operations**: `docs/live-config.md` documents the canonical payload, trust store, key rotation and the manual **Sign live config** workflow (`.github/workflows/sign-live-config.yml`).
- **Documentation**: Accurate permission list, alerts/comparison/freshness features, wrapper-based build commands, in-app update certificate requirements for forks, and a new `docs/live-config.md` schema reference plus `SECURITY.md`.
- **Versioning & Release Automation**: Aligned local fallback `versionName` to `1.0.0-dev`, logged resolved build version metadata during Gradle configuration, fetched full git history in GitHub Actions, and appended commit ranges to GitHub Release bodies.

## [1.0.0] - 2026-11-20

### Added
- Initial **Nerkhnama (نرخ‌نما)** release tracking Iranian Toman (`TMN`) rates for 13 fiat currencies, 9 gold/coin assets, global crude oil benchmarks (Brent & WTI), and 8 cryptocurrencies.
- Multi-provider aggregation (`TGJU`, `Wallex`, `Bitpin`, `Nobitex`, `Binance`, `Frankfurter`, `Currency-API`, `Gold-API`) with transparent source attribution and breakdown modal.
- Interactive `24H` / `7D` / `1M` / `1Y` historical charts (Line & Bar modes) with Jalali dates and per-card 7-day sparklines.
- 5 Jetpack Glance widgets (1×1 Hero, 2×1 Translucent Glass, 4×2 Watchlist, 2×2 Crypto, Transparent Lock-Screen) with connectivity-aware sync.
- Bilingual Persian (RTL) & English (LTR) interface, AMOLED Pure Black / Dark / Light themes, built-in currency converter, and GitHub Releases in-app update checker.
