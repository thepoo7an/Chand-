package ir.thepoo7an.chand.util

import android.util.Log
import ir.thepoo7an.chand.BuildConfig

/**
 * Minimal logging facade for failure paths that are intentionally swallowed for the user
 * (provider outages, parse failures, background sync). Debug builds log to logcat so field issues
 * can be diagnosed; release builds are a strict no-op and never write market data or payloads.
 */
object AppLog {

    private const val TAG = "Nerkhnama"

    fun d(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable)
    }
}
