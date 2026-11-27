package ir.thepoo7an.chand

import ir.thepoo7an.chand.network.LiveConfigRegistry
import ir.thepoo7an.chand.network.WallexMarketService
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
 * Provider contract test for Wallex: recorded `/v1/markets` and `/v1/udf/history` payloads under
 * `app/src/test/resources/fixtures/wallex/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WallexProviderContractTest {

    private companion object {
        const val NOW = 1_764_244_800_000L

        /** `2025-11-27T10:15:00Z` — the item-level `updated_at` of the BTC/TMN pair. */
        const val BTC_SOURCE_TS = 1_764_238_500_000L

        fun fixture(name: String): String = requireNotNull(
            WallexProviderContractTest::class.java.classLoader
                ?.getResourceAsStream("fixtures/wallex/$name")
        ) { "fixtures/wallex/$name is missing from the test resources" }
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
    fun `the recorded markets payload merges the TMN and USDT sides of one asset`() {
        val quotes = WallexMarketService.parseQuotesPayload(fixture("markets.json"), NOW)

        val btc = requireNotNull(quotes["BTC"])
        assertEquals(61_250_000_000.0, btc.priceToman ?: 0.0, 0.01)
        assertEquals(93_500.5, btc.priceUsd ?: 0.0, 0.01)
        assertEquals(61_900_000_000.0, btc.highToman ?: 0.0, 0.01)
        assertEquals(60_400_000_000.0, btc.lowToman ?: 0.0, 0.01)
        assertEquals(1.42, btc.changePct, 0.001)
        assertEquals(BTC_SOURCE_TS, btc.timestampMillis)
    }

    @Test
    fun `a TMN-only pair is a Toman quote with no fabricated USD side`() {
        val quotes = WallexMarketService.parseQuotesPayload(fixture("markets.json"), NOW)

        val eth = requireNotNull(quotes["ETH"])
        assertEquals("thousands separators are tolerated", 2_510_000_000.0, eth.priceToman ?: 0.0, 0.01)
        assertNull(eth.priceUsd)
        assertEquals(-0.8, eth.changePct, 0.001)
        assertEquals("no timestamp in the payload means 0L", 0L, eth.timestampMillis)
    }

    @Test
    fun `a pair quoted in IRT instead of TMN is not treated as a Toman quote`() {
        val quotes = WallexMarketService.parseQuotesPayload(fixture("markets.json"), NOW)

        assertFalse(quotes.containsKey("USDT"))
        assertEquals(setOf("BTC", "ETH"), quotes.keys)
    }

    @Test
    fun `a root timestamp is only a fallback for items that carry none`() {
        val withRootTs = """
            {
              "result": {
                "updated_at": 1764240000,
                "markets": {
                  "ETHTMN": { "baseAsset": "ETH", "quoteAsset": "TMN", "price": "2510000000" }
                }
              }
            }
        """.trimIndent()

        val quotes = WallexMarketService.parseQuotesPayload(withRootTs, NOW)

        assertEquals(1_764_240_000_000L, quotes.getValue("ETH").timestampMillis)
    }

    @Test
    fun `malformed markets payloads degrade to no quotes`() {
        assertTrue(WallexMarketService.parseQuotesPayload("", NOW).isEmpty())
        assertTrue(WallexMarketService.parseQuotesPayload("{}", NOW).isEmpty())
        assertTrue(WallexMarketService.parseQuotesPayload("""{"result": {}}""", NOW).isEmpty())
        assertTrue(WallexMarketService.parseQuotesPayload("<html>502</html>", NOW).isEmpty())
    }

    @Test
    fun `the recorded UDF payload becomes a sorted Toman series`() {
        val points = WallexMarketService.parseUdfPayload(fixture("udf_history.json"))

        assertEquals(6, points.size)
        assertEquals(1_764_238_800L, points.first().timestampSec)
        assertEquals(1_764_256_800L, points.last().timestampSec)
        assertEquals(71_200.0, points.first().priceToman, 0.01)
        assertEquals(71_330.0, points.last().priceToman, 0.01)
        assertTrue(points.zipWithNext().all { (a, b) -> a.timestampSec < b.timestampSec })
    }

    @Test
    fun `a UDF payload without usable points is empty, not a zero-filled series`() {
        assertTrue(WallexMarketService.parseUdfPayload("""{"s":"no_data"}""").isEmpty())
        assertTrue(WallexMarketService.parseUdfPayload("""{"s":"ok"}""").isEmpty())
        assertTrue(WallexMarketService.parseUdfPayload("""{"s":"ok","t":[1,2],"c":["0","-5"]}""").isEmpty())
        assertTrue(WallexMarketService.parseUdfPayload("not json").isEmpty())
    }

    @Test
    fun `fetchQuotes returns the live payload through the retry pipeline`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(fixture("markets.json").toResponseBody(null))
                        .build()
                }
            )
            .build()

        val quotes = WallexMarketService(client).fetchQuotes(NOW)

        assertEquals(setOf("BTC", "ETH"), quotes.keys)
        assertEquals(61_250_000_000.0, quotes.getValue("BTC").priceToman ?: 0.0, 0.01)
    }
}
