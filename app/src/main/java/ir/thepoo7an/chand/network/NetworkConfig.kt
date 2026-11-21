package ir.thepoo7an.chand.network

import kotlinx.coroutines.CancellationException
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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
 * Shared OkHttp client & standardized timeouts / retry handling used across all network services.
 */
object NetworkConfig {
    const val CONNECT_TIMEOUT_SEC = 8L
    const val READ_TIMEOUT_SEC = 10L
    const val WRITE_TIMEOUT_SEC = 10L
    const val CALL_TIMEOUT_SEC = 14L
    const val DEFAULT_MAX_ATTEMPTS = 2

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

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

    fun executeGetWithRetry(
        client: OkHttpClient = sharedClient,
        url: String,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): NetworkOutcome<String> {
        var lastError: Throwable? = null
        var lastHttpCode: Int? = null
        val totalAttempts = maxAttempts.coerceAtLeast(1)

        for (attempt in 1..totalAttempts) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            return NetworkOutcome.Success(body)
                        }
                        return NetworkOutcome.HttpError(resp.code, url)
                    }
                    lastHttpCode = resp.code
                    // Retry only on transient 5xx / 429 server/rate-limit errors
                    if (resp.code !in 500..599 && resp.code != 429) {
                        return NetworkOutcome.HttpError(resp.code, url)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (io: IOException) {
                lastError = io
            } catch (t: Throwable) {
                return NetworkOutcome.Failure(t, url)
            }

            if (attempt < totalAttempts) {
                try {
                    Thread.sleep(250L * attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return NetworkOutcome.Failure(ie, url)
                }
            }
        }

        return if (lastHttpCode != null) {
            NetworkOutcome.HttpError(lastHttpCode, url)
        } else {
            NetworkOutcome.Failure(lastError ?: IOException("Request failed: $url"), url)
        }
    }
}
