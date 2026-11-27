# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems.
Report privately via GitHub's [Security Advisories](https://github.com/thepoo7an/chand-iran-currency-gold-crypto/security/advisories/new)
("Report a vulnerability") or by contacting the maintainer directly.

Include: affected version/build number, Android version and device, reproduction steps, and the
impact you believe it has. We aim to acknowledge reports within 7 days and to ship a fix or a
mitigation in the next release.

## Supported versions

Only the latest published release receives security fixes. Builds produced locally from a fork are
out of scope.

## Attack surface and current mitigations

| Surface | Mitigation |
| --- | --- |
| In-app APK updater (`REQUEST_INSTALL_PACKAGES`) | HTTPS-only with an allowlist of GitHub/CDN hosts, redirect-downgrade rejection, mandatory SHA-256 + size from `chand-update-metadata.json`, ZIP/APK structure checks, exact package name, strictly newer `versionCode`, and a **pinned release signing certificate** (`EXPECTED_RELEASE_CERT_SHA256`). Anything that fails verification is deleted. |
| Live configuration (`chand-live-config.json`) | HTTPS-only, DNS-host validation (no credentials in URL, no IP literals, no loopback/private/link-local hosts), numeric range clamping, `USD` cannot be disabled. **The config is not signed**, so it is trusted only as far as the repository/CDN that serves it. |
| Market data providers | No credentials are sent; prices are cross-checked across sources with divergence flagging, and stale/implausible quotes are excluded from consensus. |
| FileProvider | Exposes only `cache/updates/`, `exported="false"`, per-URI read grants for the installer intent. |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`. No analytics, ads, location, camera or storage access. |

## Known residual risks (documented, not fixed)

- **Unsigned live configuration** — whoever controls the published config URL controls the price
  endpoints and validation tolerances. Mitigation today is operational (protected branch, reviewed
  commits). A signed-config scheme is the planned hardening step.
- **Third-party data feeds** — provider outages, rate limits or upstream changes can make prices
  stale; the UI surfaces freshness and source attribution instead of hiding it.
- **Self-signed / ephemeral test builds** published from CI when release secrets are absent are
  explicitly pre-releases and cannot upgrade a properly signed install.
