# Splitting `MarketRemoteDataSource`

`MarketRemoteDataSource` is the single largest file in the project. It is being split **one
provider per change**, not in one big rewrite, so every step stays reviewable and CI-verifiable.

## The shape of a provider service

Each provider becomes one class in `ir.thepoo7an.chand.network`:

```kotlin
class XMarketService(private val client: OkHttpClient = NetworkConfig.sharedClient) {
    suspend fun fetchQuotes(nowMillis: Long = System.currentTimeMillis()): Map<String, ExchangeQuote>
    companion object { fun parseQuotesPayload(raw: String, nowMillis: Long = …): Map<…> }
}
```

Rules that the extracted services follow:

- **No dependency back on the aggregator.** Shared payload helpers live in `ProviderParsing`;
  the aggregator keeps thin delegating shims only until the last provider has moved.
- **Endpoint from the live config, with a built-in default.** `LiveConfigRegistry.currentConfig()
  .endpoints.…Url.ifBlank { DEFAULT }`, never a hard-coded URL at the call site.
- **Fetching goes through `NetworkConfig.executeGetWithRetry`** with an explicit
  `RequestProfile`, so timeouts, retries, and in-flight dedupe stay identical to the old path.
- **Degrade to nothing, never to zero.** An unreachable or malformed payload returns an empty map;
  a zero or non-numeric price is skipped. The aggregator treats "no quotes" as a failed provider.
- **`nowMillis` is never used as a source timestamp.** A quote without its own timestamp reports
  `0L` (unknown) so freshness chips cannot claim data is newer than it is.
- The aggregator only gains a constructor parameter and a one-line delegation:
  `private suspend fun fetchXQuotes(nowMillis: Long) = xApi.fetchQuotes(nowMillis)`.

## Contract tests come with the provider

Every extracted provider ships with a **contract test driven by a recorded payload** in
`app/src/test/resources/fixtures/<provider>/…`:

- the recorded response is parsed once and asserted field by field (Toman side, USD side, change,
  source timestamp in seconds *and* millis, unknown quote currency, zero price, malformed JSON);
- one end-to-end case feeds the same fixture through an OkHttp interceptor to prove that the
  fetch path and the parser agree.

When upstream changes a field, exactly one contract test fails — instead of prices silently
degrading across the whole app.

## Progress

| Provider | Status | Notes |
| --- | --- | --- |
| Frankfurter / gold spot (`FinancialApiService`) | done before this effort | already a separate service |
| Crude oil (`OilApiService`) | done before this effort | already a separate service |
| Bitpin | **done** | `BitpinMarketService`, fixture `fixtures/bitpin/markets.json` |
| Wallex | **done** | `WallexMarketService` (quotes + UDF history, incl. the USDT/BTC/ETH sparklines), fixtures `fixtures/wallex/{markets,udf_history}.json` |
| Nobitex | next | orderbook + UDF history |
| Binance | pending | quotes, klines, PAXG gold sparkline |
| CoinGecko | pending | history only |
| TGJU | last | the largest surface: quotes, table points, scale rules |
