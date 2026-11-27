package ir.thepoo7an.chand.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.thepoo7an.chand.model.PriceAlert
import java.util.concurrent.TimeUnit

/**
 * Schedules or cancels the 30-minute [PriceAlertWorker] periodic job.
 * Never schedules background work unless at least one alert is enabled.
 */
object PriceAlertScheduler {

    const val WORK_NAME = "chand_price_alerts_periodic"
    const val CHECK_INTERVAL_MINUTES = 30L

    fun syncSchedule(context: Context, alerts: List<PriceAlert>) {
        if (alerts.any { it.isEnabled }) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
