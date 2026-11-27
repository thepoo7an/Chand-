# Live configuration (`chand-live-config.json`)

Nerkhnama reads an optional JSON file at startup and whenever the user taps **Sync live config**.
It lets an operator change provider endpoints, validation bounds, feature flags, asset labels and
the visible asset catalog **without shipping a new APK**.

Model definitions: `app/src/main/java/ir/thepoo7an/chand/model/RemoteConfigModels.kt`
Validation/sanitisation: `LiveConfigRegistry.sanitizeConfig` in
`app/src/main/java/ir/thepoo7an/chand/network/RemoteConfigService.kt`
Fetcher: `RemoteConfigService.fetchLiveConfig`

## Where it is fetched from

Candidates are tried in this order (first success wins):

1. The custom HTTPS URL entered in **Settings → Live config** (optional).
2. `https://cdn.jsdelivr.net/gh/<owner>/<repo>@main/chand-live-config.json`
3. `https://raw.githubusercontent.com/<owner>/<repo>/main/chand-live-config.json`

`<owner>/<repo>` defaults to the `GITHUB_REPOSITORY` build constant (overridable in Settings).
The `@main` ref is hard-coded: renaming the default branch silently breaks candidates 2 and 3.
HTTP 404 on every candidate is treated as "no config published" (not an error).

## Trust model (read this before editing)

The config is **not signed**. Anyone who can write to the configured URL can change the endpoints
that supply every price, the validation tolerances, and the asset catalog. Consequently:

- Treat the config repository/CDN path as a privileged deployment target (branch protection,
  reviewed commits, no public forks accepting unreviewed changes).
- Only `https://` URLs with a real public DNS host survive sanitisation. URLs carrying
  `user:pass@`, IP literals, IPv4 `localhost`/`10.`/`127.`/`169.254.`/`172.16-31.`/`192.168.`
  literals, IPv6 literals, `.local`/`.internal` hosts and malformed DNS labels are discarded.
- Never point a custom config URL at a host you do not control, and remember that a user-supplied
  URL takes precedence over the project's own CDN copy.

## Schema

```jsonc
{
  "configVersion": 1,              // bumped on every publish; UI shows "updated" when it changes
  "minAppVersionCode": 1,          // config ignored below this versionCode
  "maxAppVersionCode": null,       // config ignored above this versionCode (null = no cap)
  "publishedAtIso": "2026-11-25T06:00:00Z",
  "summaryFa": "…",
  "summaryEn": "…",

  "endpoints": {                   // blank string / empty list = keep the built-in default
    "tgjuAjaxUrl": "",
    "tgjuTableUrlTemplate": "",    // placeholders: {slug}, {from}, {to}
    "wallexMarketsUrl": "",
    "wallexUdfUrlTemplate": "",
    "bitpinMarketsUrl": "",
    "nobitexOrderbookUrlTemplate": "",  // {symbol}
    "nobitexUdfUrlTemplate": "",        // {symbol}, {resolution}, {from}, {to}
    "binanceTickerUrl": "",
    "binanceKlinesUrlTemplate": "",
    "coinGeckoHistoryUrlTemplate": "",
    "frankfurterLatestUrls": [],
    "frankfurterSeriesUrlTemplates": [],
    "openErLatestUrl": "",
    "goldSpotXauUrl": "",
    "currencyCdnUrlTemplates": [],
    "oilYahooChartUrlTemplates": [],
    "oilCnbcQuotesUrl": "",
    "oilEiaSpotUrl": ""
  },

  "validation": {                  // null = built-in default; values are clamped to safe ranges
    "cryptoDisagreementThresholdPct": 4.0,   // 0.5 – 25.0
    "fiatDisagreementThresholdPct": 3.0,     // 0.5 – 25.0
    "cryptoLiveMaxAgeMinutes": 5,            // 1 – 180
    "fiatGoldLiveMaxAgeMinutes": 30,         // 1 – 360
    "spikeThreshold24hPct": 15.0,            // 5.0 – 100.0
    "spikeThresholdCryptoMultiDayPct": 40.0, // 5.0 – 150.0
    "spikeThresholdFiatMultiDayPct": 15.0,   // 5.0 – 100.0
    "spikeThresholdGoldMultiDayPct": 15.0    // 5.0 – 100.0
  },

  "features": {
    "enableConverterEquivalents": true,
    "enableCoinBubbleEstimate": true,
    "enableHistoricalCharts": true,
    "enableInAppApkUpdates": true,           // false = disable the whole self-updater
    "minAutoRefreshSeconds": 10              // 5 – 300; lower bound for the user refresh setting
  },

  "disabledAssetIds": ["DOGE"],    // "USD" can never be disabled; ids are upper-cased

  "assetOverrides": [              // relabel / re-point a built-in asset (USD cannot be disabled)
    { "id": "EUR", "enabled": true, "nameFa": "یورو", "nameEn": "Euro",
      "symbol": "EUR", "badgeText": "🇪🇺", "accentHex": 4287863799,
      "tgjuKey": "price_eur", "fxCode": "EUR", "coinGeckoId": null }
  ],

  "extraAssets": [                 // add a new asset without an app update
    { "id": "USD_EMAMI", "symbol": "SEKE", "nameFa": "سکه امامی", "nameEn": "Emami Coin",
      "category": "GOLD", "badgeText": "🪙", "accentHex": 4294240026,
      "tgjuKey": "sekee", "fxCode": null, "coinGeckoId": null }
  ],                               // id: 2–24 chars [A-Z0-9_]; category FIAT/GOLD/CRYPTO/OIL,
                                   // and ALL/PINNED silently fall back to CRYPTO

  "liveNotice": {                  // optional dismissible banner; omit or null when unused
    "id": "notice-2026-11",
    "titleFa": "…", "titleEn": "…",
    "bodyFa": "…", "bodyEn": "…",
    "severity": "INFO",            // INFO | WARNING | SUCCESS
    "dismissible": true,
    "actionLabelFa": "…", "actionLabelEn": "…",
    "actionUrl": "https://…"       // same HTTPS validation as endpoints; blank = no action
  },

  "appliedChanges": [              // shown in the "what changed" list
    { "titleFa": "…", "titleEn": "…", "category": "CONFIG" }
  ]
}
```

## Operational notes

- A published config is persisted in DataStore and re-applied on every launch until it is replaced
  or reset (**Settings → Reset live config**). There is no expiry.
- Bad JSON is ignored silently (it never bricks the app); keep the file valid before publishing.
- `enableInAppApkUpdates: false` is the kill switch for the self-updater if a release is broken.
- After editing, verify with a unit test or by running the app in debug and watching logcat
  (`Nerkhnama` tag) — sync failures are logged there in debug builds.
