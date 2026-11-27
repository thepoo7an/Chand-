package ir.thepoo7an.chand

import ir.thepoo7an.chand.network.BitpinMarketService
import ir.thepoo7an.chand.network.LiveConfigRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Provider contract test for the first extracted provider service.
 *
 * The payload is a recorded `/mkt/markets/` response (`fixtures/bitpin/markets.json`) rather than
 * an inline literal, so a shape change in the upstream API fails one obvious test instead of
 * silently degrading prices across the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BitpinProviderContractTest {

    private companion object {
        /** 2025-11-27T12:00:00Z, only used as the "fetch time" argument. */
        const val NOW = 1_764_244_800_000L

        /** `2025-11-27T10:15:00Z`, the source timestamp Bitpin reports for the BTC/IRT pair. */
        const val BTC_SOURCE_TS = 1_764_238_500_000L

        /** `updated_at: 1764240000` is epoch **seconds**, so it must be scaled to millis. */
        const val USDT_SOURCE_TS = 1_764_240_000_000L

        fun fixture(): String = requireNotNull(
            BitpinProviderContractTest::class.java.classLoader
                ?.getResourceAsStream("fixtures/bitpin/markets.json")
        ) { "fixtures/bitpin/markets.json is missing from the test resources" }
            .bufferedReader()
            .use { it.readText() }
    }

    @Before
    fun setUp() {
        LiveConfigRegistry.resetToDefault()
    }

    @After
    fun tearDown() {
        LiveConfigRegistry.resetToDefault()
    }

    @Test
    fun `the recorded payload yields both Toman and USD sides with real source timestamps`() {
        val quotes = BitpinMarketService.parseQuotesPayload(fixture(), NOW)

        val btc = requireNotNull(quotes["BTC"]) { "BTC/IRT and BTC/USDT must merge into one quote" }
        assertEquals(61_250_000_000.0, btc.priceToman ?: 0.0, 0.01)
        assertEquals(93_500.5, btc.priceUsd ?: 0.0, 0.01)
        assertEquals("the Toman side owns the change and the timestamp", 1.42, btc.changePct, 0.001)
        assertEquals(BTC_SOURCE_TS, btc.timestampMillis)

        val usdt = requireNotNull(quotes["USDT"])
        assertEquals(71_280.0, usdt.priceToman ?: 0.0, 0.01)
        assertEquals("epoch seconds are scaled to millis", USDT_SOURCE_TS, usdt.timestampMillis)
        assertNull("a pair quoted in IRT has no USD side", usdt.priceUsd)
    }

    @Test
    fun `a pair with an unknown quote currency is ignored instead of being guessed`() {
        val quotes = BitpinMarketService.parseQuotesPayload(fixture(), NOW)

        // DOGE/BTC is neither an IRT nor a USDT pair: publishing it as a Toman price would be a lie.
        assertFalse(quotes.containsKey("DOGE"))
        assertTrue(quotes.keys.containsAll(setOf("BTC", "ETH", "USDT")))
    }

    @Test
    fun `a zero price never becomes a quote`() {
        val quotes = BitpinMarketService.parseQuotesPayload(fixture(), NOW)

        assertFalse(quotes.containsKey("TRX"))
        assertTrue(quotes.values.all { (it.priceToman ?: 0.0) > 0.0 || (it.priceUsd ?: 0.0) > 0.0 })
    }

    @Test
    fun `a missing source timestamp stays unknown instead of borrowing the fetch time`() {
        val quotes = BitpinMarketService.parseQuotesPayload(fixture(), NOW)

        val eth = requireNotNull(quotes["ETH"])
        assertEquals(3_520.75, eth.priceUsd ?: 0.0, 0.01)
        assertNull(eth.priceToman)
        assertEquals("no timestamp in the payload means 0L, not nowMillis", 0L, eth.timestampMillis)
        assertEquals(-0.8, eth.changePct, 0.001)
    }

    @Test
    fun `malformed or empty payloads degrade to no quotes`() {
        assertTrue(BitpinMarketService.parseQuotesPayload("", NOW).isEmpty())
        assertTrue(BitpinMarketService.parseQuotesPayload("not json at all", NOW).isEmpty())
        assertTrue(BitpinMarketService.parseQuotesPayload("""{"count": 0}""", NOW).isEmpty())
        assertTrue(BitpinMarketService.parseQuotesPayload("""{"results": []}""", NOW).isEmpty())
    }

    @Test
    fun `fetchQuotes returns the live payload through the retry pipeline`() = runBlocking {
        val body = fixture().toResponseBody(null)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
            )
            .build()

        val quotes = BitpinMarketService(client).fetchQuotes(NOW)

        assertEquals(3, quotes.size)
        assertEquals(61_250_000_000.0, quotes.getValue("BTC").priceToman ?: 0.0, 0.01)
    }
}
