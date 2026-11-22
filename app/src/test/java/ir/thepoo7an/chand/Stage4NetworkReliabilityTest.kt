package ir.thepoo7an.chand

import ir.thepoo7an.chand.network.AppUpdateService
import ir.thepoo7an.chand.network.AppUpdateState
import ir.thepoo7an.chand.network.InFlightRequestDeduplicator
import ir.thepoo7an.chand.network.NetworkConfig
import ir.thepoo7an.chand.network.NetworkConfig.RequestProfile
import ir.thepoo7an.chand.network.NetworkOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class Stage4NetworkReliabilityTest {

    private fun interceptorClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `endpoint profiles have differentiated timeouts and share connection pool`() {
        val fastClient = NetworkConfig.clientFor(RequestProfile.FAST_QUOTE)
        val historyClient = NetworkConfig.clientFor(RequestProfile.HISTORY)
        val updateClient = NetworkConfig.clientFor(RequestProfile.UPDATE_CHECK)

        assertTrue(fastClient.readTimeoutMillis < historyClient.readTimeoutMillis)
        assertTrue(fastClient.callTimeoutMillis < historyClient.callTimeoutMillis)
        assertEquals(RequestProfile.FAST_QUOTE.readTimeoutSec.toInt() * 1000, fastClient.readTimeoutMillis)
        assertEquals(RequestProfile.HISTORY.readTimeoutSec.toInt() * 1000, historyClient.readTimeoutMillis)
        assertEquals(RequestProfile.UPDATE_CHECK.readTimeoutSec.toInt() * 1000, updateClient.readTimeoutMillis)

        assertSame(NetworkConfig.sharedClient.connectionPool, fastClient.connectionPool)
        assertSame(NetworkConfig.sharedClient.connectionPool, historyClient.connectionPool)
        assertSame(NetworkConfig.sharedClient.dispatcher, updateClient.dispatcher)

        assertEquals(RequestProfile.FAST_QUOTE, NetworkConfig.profileForUrl("https://call5.tgju.org/ajax.json?t=1"))
        assertEquals(RequestProfile.FAST_QUOTE, NetworkConfig.profileForUrl("https://api.wallex.ir/v1/markets"))
        assertEquals(RequestProfile.FAST_QUOTE, NetworkConfig.profileForUrl("https://api.frankfurter.dev/v1/latest?base=USD"))
        assertEquals(RequestProfile.HISTORY, NetworkConfig.profileForUrl("https://api.tgju.org/v1/market/indicator/summary-table-data/geram18?length=14"))
        assertEquals(RequestProfile.HISTORY, NetworkConfig.profileForUrl("https://api.wallex.ir/v1/udf/history?symbol=USDTTMN"))
        assertEquals(RequestProfile.HISTORY, NetworkConfig.profileForUrl("https://api.binance.com/api/v3/klines?symbol=BTCUSDT"))
        assertEquals(RequestProfile.HISTORY, NetworkConfig.profileForUrl("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=7"))
        assertEquals(RequestProfile.HISTORY, NetworkConfig.profileForUrl("https://api.frankfurter.dev/v1/2025-01-01..2025-01-08"))
        assertEquals(RequestProfile.UPDATE_CHECK, NetworkConfig.profileForUrl("https://api.github.com/repos/thepoo7an/chand/releases/latest"))
    }

    @Test
    fun `computeBackoffMillis doubles exponentially and caps at maxDelayMillis`() {
        assertEquals(0L, NetworkConfig.computeBackoffMillis(0, 250L, 2_000L))
        assertEquals(250L, NetworkConfig.computeBackoffMillis(1, 250L, 2_000L))
        assertEquals(500L, NetworkConfig.computeBackoffMillis(2, 250L, 2_000L))
        assertEquals(1_000L, NetworkConfig.computeBackoffMillis(3, 250L, 2_000L))
        assertEquals(2_000L, NetworkConfig.computeBackoffMillis(4, 250L, 2_000L))
        assertEquals(2_000L, NetworkConfig.computeBackoffMillis(10, 250L, 2_000L))
    }

    @Test
    fun `executeGetWithRetry retries transient 5xx 429 and IOException then succeeds`() = runBlocking {
        val attempts = AtomicInteger(0)
        val client = interceptorClient { req ->
            when (attempts.incrementAndGet()) {
                1 -> throw IOException("simulated socket reset")
                2 -> jsonResponse(req, 503, "")
                3 -> jsonResponse(req, 429, "")
                else -> jsonResponse(req, 200, """{"ok":true}""")
            }
        }

        val outcome = NetworkConfig.executeGetWithRetry(
            client = client,
            url = "https://example.invalid/retry-test",
            maxAttempts = 4,
            baseDelayMillis = 1L,
            maxDelayMillis = 5L
        )

        assertEquals(4, attempts.get())
        assertTrue(outcome is NetworkOutcome.Success)
        assertEquals("""{"ok":true}""", (outcome as NetworkOutcome.Success).data)
    }

    @Test
    fun `executeGetWithRetry fails fast on permanent 4xx errors without retrying`() = runBlocking {
        for (code in listOf(400, 403, 404)) {
            val attempts = AtomicInteger(0)
            val client = interceptorClient { req ->
                attempts.incrementAndGet()
                jsonResponse(req, code, "error")
            }

            val outcome = NetworkConfig.executeGetWithRetry(
                client = client,
                url = "https://example.invalid/perm-$code",
                maxAttempts = 3,
                baseDelayMillis = 1L,
                maxDelayMillis = 5L
            )

            assertEquals("HTTP $code should not retry", 1, attempts.get())
            assertTrue(outcome is NetworkOutcome.HttpError)
            assertEquals(code, (outcome as NetworkOutcome.HttpError).code)
        }
    }

    @Test
    fun `retrySuspend handles Retrofit HttpException retryability and respects cancellation`() = runTest {
        var transientCalls = 0
        val recovered = NetworkConfig.retrySuspend(
            maxAttempts = 3,
            baseDelayMillis = 1L,
            maxDelayMillis = 4L
        ) {
            transientCalls++
            if (transientCalls == 1) {
                val resp = retrofit2.Response.error<String>(503, "busy".toResponseBody("text/plain".toMediaType()))
                throw HttpException(resp)
            }
            "ok"
        }
        assertEquals("ok", recovered)
        assertEquals(2, transientCalls)

        var permCalls = 0
        var caughtCode = -1
        try {
            NetworkConfig.retrySuspend(
                maxAttempts = 3,
                baseDelayMillis = 1L,
                maxDelayMillis = 4L
            ) {
                permCalls++
                val resp = retrofit2.Response.error<String>(404, "missing".toResponseBody("text/plain".toMediaType()))
                throw HttpException(resp)
            }
        } catch (e: HttpException) {
            caughtCode = e.code()
        }
        assertEquals(404, caughtCode)
        assertEquals(1, permCalls)
    }

    @Test
    fun `InFlightRequestDeduplicator coalesces concurrent calls and runs fresh sequentially`() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val gate = CompletableDeferred<Unit>()
        val invocations = AtomicInteger(0)

        val concurrentResults = coroutineScope {
            (1..8).map {
                async(Dispatchers.Default) {
                    deduplicator.execute("same-key") {
                        val n = invocations.incrementAndGet()
                        gate.await()
                        "result-$n"
                    }
                }
            }.also {
                delay(40)
                gate.complete(Unit)
            }.awaitAll()
        }

        assertEquals(1, invocations.get())
        assertTrue(concurrentResults.all { it == "result-1" })
        assertEquals(0, deduplicator.activeCount())

        // Sequential call after first completed must invoke again (not stale-cached)
        val second = deduplicator.execute("same-key") {
            "result-${invocations.incrementAndGet()}"
        }
        assertEquals("result-2", second)
        assertEquals(2, invocations.get())
    }

    @Test
    fun `InFlightRequestDeduplicator isolates single waiter cancellation and cancels when all waiters cancel`() = runBlocking {
        val deduplicator = InFlightRequestDeduplicator<String, String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelledFlag = CompletableDeferred<Boolean>()

        coroutineScope {
            val job1 = launch(Dispatchers.Default) {
                deduplicator.execute("k1") {
                    started.complete(Unit)
                    release.await()
                    "shared-value"
                }
            }
            val deferred2 = async(Dispatchers.Default) {
                deduplicator.execute("k1") {
                    started.complete(Unit)
                    release.await()
                    "shared-value"
                }
            }

            started.await()
            job1.cancel()
            job1.join()

            // Waiter 2 is still alive so underlying work completes for it
            release.complete(Unit)
            assertEquals("shared-value", deferred2.await())
        }

        // Now test cancelling ALL waiters aborts underlying block
        val startedAll = CompletableDeferred<Unit>()
        val gateAll = CompletableDeferred<Unit>()
        coroutineScope {
            val job = launch(Dispatchers.Default) {
                deduplicator.execute("k2") {
                    startedAll.complete(Unit)
                    try {
                        gateAll.await()
                        cancelledFlag.complete(false)
                        "done"
                    } catch (ce: CancellationException) {
                        cancelledFlag.complete(true)
                        throw ce
                    }
                }
            }
            startedAll.await()
            job.cancel()
            job.join()
        }
        assertTrue(cancelledFlag.await())
        assertEquals(0, deduplicator.activeCount())
    }

    @Test
    fun `executeGetWithRetry deduplicates concurrent identical GET URLs and AppUpdateService deduplicates checks`() = runBlocking {
        val getGate = CompletableDeferred<Unit>()
        val httpHits = AtomicInteger(0)
        val getClient = interceptorClient { req ->
            httpHits.incrementAndGet()
            runBlocking { getGate.await() }
            jsonResponse(req, 200, """{"status":"ok"}""")
        }

        val outcomes = coroutineScope {
            (1..6).map {
                async(Dispatchers.IO) {
                    NetworkConfig.executeGetWithRetry(
                        client = getClient,
                        url = "https://example.invalid/shared-endpoint",
                        maxAttempts = 2
                    )
                }
            }.also {
                delay(50)
                getGate.complete(Unit)
            }.awaitAll()
        }

        assertEquals(1, httpHits.get())
        assertTrue(outcomes.all { it is NetworkOutcome.Success && it.data == """{"status":"ok"}""" })

        val githubGate = CompletableDeferred<Unit>()
        val githubHits = AtomicInteger(0)
        val githubClient = interceptorClient { req ->
            githubHits.incrementAndGet()
            runBlocking { githubGate.await() }
            jsonResponse(
                req,
                200,
                """
                {
                  "tag_name": "v2.1.0",
                  "name": "Chand 2.1.0 (Build 99)",
                  "body": "Release notes",
                  "html_url": "https://github.com/thepoo7an/chand/releases/tag/v2.1.0",
                  "prerelease": false,
                  "draft": false,
                  "published_at": "2026-01-01T00:00:00Z",
                  "assets": [
                    {
                      "name": "Chand-arm64-v8a-release.apk",
                      "browser_download_url": "https://example.invalid/Chand-arm64-v8a-release.apk",
                      "size": 123456
                    }
                  ]
                }
                """.trimIndent()
            )
        }
        val updateService = AppUpdateService(githubClient)
        val updateStates = coroutineScope {
            (1..4).map {
                async(Dispatchers.IO) {
                    updateService.checkForUpdate(
                        repoSlugInput = "thepoo7an/chand",
                        currentVersionName = "1.0",
                        currentVersionCode = 10,
                        supportedAbis = arrayOf("arm64-v8a")
                    )
                }
            }.also {
                delay(50)
                githubGate.complete(Unit)
            }.awaitAll()
        }

        assertEquals(1, githubHits.get())
        assertFalse(updateStates.any { it !is AppUpdateState.UpdateAvailable })
    }
}
