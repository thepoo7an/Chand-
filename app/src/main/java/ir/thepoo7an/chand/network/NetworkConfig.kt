package ir.thepoo7an.chand.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class TomanRateBasis(
    val id: String,
    val labelEn: String,
    val labelFa: String
) {
    TEHRAN_FREE_MARKET(
        id = "free_market_tgju",
        labelEn = "Free Market (Tehran)",
        labelFa = "نرخ آزاد تهران"
    ),
    USDT_FREE_MARKET_MEDIAN(
        id = "free_market_usdt",
        labelEn = "Free Market (USDT 24/7)",
        labelFa = "تتر آزاد (۲۴ ساعته)"
    ),
    CACHED_USD_ANCHOR(
        id = "cached_usd_anchor",
        labelEn = "Global FX + Cached USD",
        labelFa = "ارز جهانی + دلار ذخیره‌شده"
    ),
    OFFLINE_REFERENCE(
        id = "offline_reference",
        labelEn = "Offline Reference",
        labelFa = "مرجع آفلاین"
    );

    companion object {
        fun localizeSummary(summary: String, isFa: Boolean): String {
            if (!isFa || summary.isBlank()) return summary
            var localized = summary
            entries.forEach { basis ->
                localized = localized.replace(basis.labelEn, basis.labelFa)
            }
            return localized
        }
    }
}

sealed interface NetworkOutcome<out T> {
    data class Success<T>(val data: T) : NetworkOutcome<T>
    data class HttpError(val code: Int, val url: String) : NetworkOutcome<Nothing>
    data class Failure(val cause: Throwable, val url: String) : NetworkOutcome<Nothing>
}

/**
 * Shared OkHttp client, endpoint-differentiated timeout profiles, non-blocking exponential
 * backoff retries, and single-flight GET request deduplication across all network services.
 */
object NetworkConfig {
    const val CONNECT_TIMEOUT_SEC = 8L
    const val READ_TIMEOUT_SEC = 10L
    const val WRITE_TIMEOUT_SEC = 10L
    const val CALL_TIMEOUT_SEC = 14L
    const val DEFAULT_MAX_ATTEMPTS = 2
    const val DEFAULT_RETRY_BASE_DELAY_MS = 250L
    const val DEFAULT_RETRY_MAX_DELAY_MS = 2_000L

    enum class RequestProfile(
        val connectTimeoutSec: Long,
        val readTimeoutSec: Long,
        val writeTimeoutSec: Long,
        val callTimeoutSec: Long,
        val defaultMaxAttempts: Int,
        val baseDelayMillis: Long,
        val maxDelayMillis: Long
    ) {
        /** Fast live quote/ticker endpoints: fail promptly so fallback sources take over cleanly. */
        FAST_QUOTE(
            connectTimeoutSec = 5L,
            readTimeoutSec = 6L,
            writeTimeoutSec = 6L,
            callTimeoutSec = 9L,
            defaultMaxAttempts = 2,
            baseDelayMillis = DEFAULT_RETRY_BASE_DELAY_MS,
            maxDelayMillis = DEFAULT_RETRY_MAX_DELAY_MS
        ),
        /** Multi-day historical tables, klines, and archive series: tolerate larger payloads. */
        HISTORY(
            connectTimeoutSec = 6L,
            readTimeoutSec = 10L,
            writeTimeoutSec = 8L,
            callTimeoutSec = 15L,
            defaultMaxAttempts = 3,
            baseDelayMillis = DEFAULT_RETRY_BASE_DELAY_MS,
            maxDelayMillis = DEFAULT_RETRY_MAX_DELAY_MS
        ),
        /** GitHub Releases metadata check for app updates. */
        UPDATE_CHECK(
            connectTimeoutSec = 6L,
            readTimeoutSec = 8L,
            writeTimeoutSec = 6L,
            callTimeoutSec = 12L,
            defaultMaxAttempts = 2,
            baseDelayMillis = DEFAULT_RETRY_BASE_DELAY_MS,
            maxDelayMillis = DEFAULT_RETRY_MAX_DELAY_MS
        )
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val inFlightGetDeduplicator = InFlightRequestDeduplicator<String, NetworkOutcome<String>>()

    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(null)
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Cache-Control", "no-cache, no-store")
                    .header("Pragma", "no-cache")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private val fastQuoteClient: OkHttpClient by lazy { buildProfileClient(sharedClient, RequestProfile.FAST_QUOTE) }
    private val historyClient: OkHttpClient by lazy { buildProfileClient(sharedClient, RequestProfile.HISTORY) }
    private val updateCheckClient: OkHttpClient by lazy { buildProfileClient(sharedClient, RequestProfile.UPDATE_CHECK) }

    private fun buildProfileClient(base: OkHttpClient, profile: RequestProfile): OkHttpClient =
        base.newBuilder()
            .connectTimeout(profile.connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(profile.readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(profile.writeTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(profile.callTimeoutSec, TimeUnit.SECONDS)
            .build()

    fun clientFor(
        profile: RequestProfile,
        base: OkHttpClient = sharedClient
    ): OkHttpClient {
        if (base === sharedClient) {
            return when (profile) {
                RequestProfile.FAST_QUOTE -> fastQuoteClient
                RequestProfile.HISTORY -> historyClient
                RequestProfile.UPDATE_CHECK -> updateCheckClient
            }
        }
        return buildProfileClient(base, profile)
    }

    fun profileForUrl(url: String): RequestProfile {
        val lower = url.lowercase()
        return when {
            "api.github.com/repos/" in lower -> RequestProfile.UPDATE_CHECK
            "summary-table-data" in lower ||
                "/udf/history" in lower ||
                "/klines" in lower ||
                "market_chart" in lower ||
                ("frankfurter" in lower && ".." in lower) ||
                (("currency-api@" in lower || "currency-api.pages.dev" in lower) && "latest" !in lower) -> RequestProfile.HISTORY
            else -> RequestProfile.FAST_QUOTE
        }
    }

    fun computeBackoffMillis(
        attemptNumber: Int,
        baseDelayMillis: Long = DEFAULT_RETRY_BASE_DELAY_MS,
        maxDelayMillis: Long = DEFAULT_RETRY_MAX_DELAY_MS
    ): Long {
        if (attemptNumber <= 0 || baseDelayMillis <= 0L) return 0L
        val exponent = (attemptNumber - 1).coerceAtMost(10)
        val raw = baseDelayMillis * (1L shl exponent)
        return raw.coerceAtMost(maxDelayMillis.coerceAtLeast(baseDelayMillis))
    }

    fun isRetryableHttpCode(code: Int): Boolean =
        code in 500..599 || code == 429 || code == 408

    fun isTransientThrowable(t: Throwable): Boolean = when (t) {
        is CancellationException -> throw t
        is IOException -> true
        is HttpException -> isRetryableHttpCode(t.code())
        else -> false
    }

    /**
     * Non-blocking GET execution with endpoint-specific timeouts, exponential backoff retries on
     * transient network/server errors (never [Thread.sleep]), and concurrent single-flight deduplication.
     */
    suspend fun executeGetWithRetry(
        client: OkHttpClient = sharedClient,
        url: String,
        maxAttempts: Int? = null,
        profile: RequestProfile = profileForUrl(url),
        baseDelayMillis: Long = profile.baseDelayMillis,
        maxDelayMillis: Long = profile.maxDelayMillis
    ): NetworkOutcome<String> {
        val dedupeKey = "${System.identityHashCode(client)}|${profile.name}|$url"
        val attempts = (maxAttempts ?: profile.defaultMaxAttempts).coerceAtLeast(1)
        val profiledClient = clientFor(profile, client)

        return inFlightGetDeduplicator.execute(dedupeKey) {
            var lastError: Throwable? = null
            var lastHttpCode: Int? = null

            for (attempt in 1..attempts) {
                val attemptOutcome = try {
                    val request = Request.Builder().url(url).build()
                    executeCallAsync(profiledClient, request, url)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    return@execute NetworkOutcome.Failure(t, url)
                }

                when (attemptOutcome) {
                    is NetworkOutcome.Success -> return@execute attemptOutcome
                    is NetworkOutcome.HttpError -> {
                        lastHttpCode = attemptOutcome.code
                        if (!isRetryableHttpCode(attemptOutcome.code)) {
                            return@execute attemptOutcome
                        }
                    }
                    is NetworkOutcome.Failure -> {
                        if (attemptOutcome.cause !is IOException) {
                            return@execute attemptOutcome
                        }
                        lastError = attemptOutcome.cause
                    }
                }

                if (attempt < attempts) {
                    val backoffMs = computeBackoffMillis(attempt, baseDelayMillis, maxDelayMillis)
                    if (backoffMs > 0L) delay(backoffMs)
                }
            }

            if (lastHttpCode != null) {
                NetworkOutcome.HttpError(lastHttpCode, url)
            } else {
                NetworkOutcome.Failure(lastError ?: IOException("Request failed: $url"), url)
            }
        }
    }

    private suspend fun executeCallAsync(
        client: OkHttpClient,
        request: Request,
        url: String
    ): NetworkOutcome<String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            call.cancel()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(NetworkOutcome.Failure(e, url))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val outcome = try {
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                NetworkOutcome.Success(body)
                            } else {
                                NetworkOutcome.HttpError(resp.code, url)
                            }
                        } else {
                            NetworkOutcome.HttpError(resp.code, url)
                        }
                    }
                } catch (io: IOException) {
                    NetworkOutcome.Failure(io, url)
                } catch (t: Throwable) {
                    NetworkOutcome.Failure(t, url)
                }
                if (continuation.isActive) {
                    continuation.resume(outcome)
                }
            }
        })
    }

    /**
     * Non-blocking coroutine retry helper with exponential backoff for Retrofit and suspend calls.
     */
    suspend fun <T> retrySuspend(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        baseDelayMillis: Long = DEFAULT_RETRY_BASE_DELAY_MS,
        maxDelayMillis: Long = DEFAULT_RETRY_MAX_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = ::isTransientThrowable,
        block: suspend () -> T
    ): T {
        val totalAttempts = maxAttempts.coerceAtLeast(1)
        var lastThrowable: Throwable? = null
        for (attempt in 1..totalAttempts) {
            try {
                return block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastThrowable = t
                if (attempt >= totalAttempts || !shouldRetry(t)) {
                    throw t
                }
                val backoffMs = computeBackoffMillis(attempt, baseDelayMillis, maxDelayMillis)
                if (backoffMs > 0L) delay(backoffMs)
            }
        }
        throw lastThrowable ?: IOException("Retry exhausted")
    }
}
