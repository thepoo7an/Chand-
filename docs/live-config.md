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

The config **is signed** (ECDSA P-256 / SHA-256) and the app verifies the signature before applying
anything — see [Signing and verification](#signing-and-verification). Once a trust store is built
in, an unsigned or tampered config is rejected and the previously applied config is kept.

Even with signatures in place, keep treating the config repository/CDN path as a privileged
deployment target (branch protection, reviewed commits, no public forks accepting unreviewed
changes): a valid signature only proves *who* published the config, not that the endpoints are
correct.
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
  "expiresAtIso": "",              // optional; once past, the config is ignored (and dropped if applied)
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

## Signing and verification

Every published config must carry a detached signature:

```jsonc
{
  "configVersion": 2,
  /* …the normal config… */
  "signature": "MEUCIQ…",     // base64(DER ECDSA signature) over the canonical payload
  "keyId": "prod-2026-11"     // must match an entry of the build's trust store
}
```

### Canonical payload (what exactly is signed)

`scripts/live_config_canonical.py` and the Android verifier
(`LiveConfigSignature.canonicalPayload`) implement the same four rules:

1. Parse the document; numbers keep their **literal source text** (`4.0` never becomes `4`).
2. Drop the top-level `signature` and `keyId` members.
3. Emit compact JSON: object keys sorted ascending (ASCII keys), no whitespace, strings escaped
   minimally (`"`, `\` and C0 controls only), everything else as raw UTF-8.
4. UTF-8 encode → those bytes are signed with **ECDSA P-256 + SHA-256** and stored base64/DER.

Consequences: reformatting, reordering keys, or re-indenting the file keeps the signature valid;
changing any value invalidates it. Comments/`jsonc` are not allowed in the published file.

`LiveConfigSignatureTest` verifies the committed `chand-live-config.json` against the embedded
key, so a divergence between the Python tooling and the Kotlin verifier fails CI immediately.

### Trust store

Public keys live in [`gradle/live-config-keys.properties`](../gradle/live-config-keys.properties)
format `keyId:base64(SubjectPublicKeyInfo)[,keyId2:…]` and are compiled into
`BuildConfig.LIVE_CONFIG_PUBLIC_KEYS`. Override per build with `LIVE_CONFIG_PUBLIC_KEYS` (env) or
`-PliveConfigPublicKeys=…`.

Behaviour after the signature check:

| Situation | Result |
| --- | --- |
| Valid signature, known `keyId` | config applied (subject to version compatibility + sanitisation) |
| Signature mismatch, unknown `keyId`, unparsable material | **rejected**, previous config kept, Settings shows a signature error |
| No `signature`/`keyId`, trust store **non-empty** | rejected (`config_unsigned`) |
| No trust store configured (fork/experimental build) | unsigned config still accepted, a debug log line is emitted |

### Signing workflow

```bash
# one-time: create the key pair (the private key never enters the repository)
openssl ecparam -name prime256v1 -genkey -noout -out .live-config-signing-key.pem
openssl ec -in .live-config-signing-key.pem -pubout -outform DER | base64 -w0   # → trust store

# every publish
python3 scripts/sign-live-config.py \
  --key .live-config-signing-key.pem --key-id prod-2026-11 \
  --in chand-live-config.json --out chand-live-config.json

# verify offline (same rules as the app)
python3 scripts/verify-live-config.py --config chand-live-config.json \
  --keys "prod-2026-11:<base64 from gradle/live-config-keys.properties>"
```

CI runs `scripts/test-live-config-signature.sh` (sign → verify → reformat → tamper → unknown key →
unsigned → verify the committed file). Store the private key as the `LIVE_CONFIG_SIGNING_KEY`
Actions secret and use the **Sign live config** workflow (`.github/workflows/sign-live-config.yml`)
to re-sign after editing.

### Key rotation

1. Add the new public key to `gradle/live-config-keys.properties` **next to** the current one and
   ship a release that trusts both.
2. Re-sign the config with the new `keyId` (`scripts/sign-live-config.py --key-id prod-…`) and
   publish it — clients on the new release already accept it.
3. After the adoption window (or when `minAppVersionCode` guarantees the fresh builds), delete the
   old entry.

If a private key leaks: add the replacement key (step 1), publish a config signed with it, raise
`minAppVersionCode` so old clients stop trusting anything, and rotate the CDN path.

## Self-healing and expiry

Two independent safety nets keep a bad publication from bricking the price feed:

**1. Expiry (`expiresAtIso`).** After the timestamp passes, a config is no longer applied and an
already-applied one is discarded on the next hydration (`PreferencesRepository.hydrateLiveConfig`).
Blank or unparsable values mean "no expiry" — a typo must never silently disable the config. The
field is inside the signed payload, so changing the expiry requires re-signing.

**2. Automatic rollback.** Every full market refresh reports whether it produced any live data:

- a live snapshot clears the failure streak;
- a refresh with **no live data and no usable cache** increments it;
- on the **3rd consecutive** failure while a remote config is active, the app reverts to the
  built-in endpoints, clears the persisted config, and remembers the rolled-back
  `configVersion` (`LIVE_CONFIG_REVERTED_VERSION`).

While that marker is set, any config whose `configVersion` is **less than or equal to** it is
suppressed (`config_suppressed` in Settings) — a mirror still serving the broken document cannot
undo the recovery. **Bump `configVersion` when you republish after a fix**, or use
**Settings → Reset live config**, which clears the marker.

Rollbacks and suppressions are logged through `AppLog` (debug builds) and covered by
`LiveConfigSelfHealingTest`.

## Operational notes

- A published config is persisted in DataStore and re-applied on every launch until it is replaced
  or reset (**Settings → Reset live config**). There is no expiry.
- Bad JSON or a bad signature is ignored silently (it never bricks the app); keep the file valid and **signed** before publishing.
- `enableInAppApkUpdates: false` is the kill switch for the self-updater if a release is broken.
- After editing, verify with a unit test or by running the app in debug and watching logcat
  (`Nerkhnama` tag) — sync failures are logged there in debug builds.
