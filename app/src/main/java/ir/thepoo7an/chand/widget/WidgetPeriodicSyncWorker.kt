package ir.thepoo7an.chand.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.thepoo7an.chand.util.AppLog
import kotlinx.coroutines.CancellationException

/**
 * 30-minute periodic widget refresh backed by **WorkManager** instead of `JobScheduler`.
 *
 * `JobScheduler` jobs created with `setPersisted(false)` are dropped on reboot, which silently
 * stopped the documented background widget sync until the app was opened again. WorkManager
 * merges its own `BOOT_COMPLETED` receiver, so the schedule survives reboots and app upgrades.
 */
class WidgetPeriodicSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            if (!WidgetSyncCoordinator.hasAnyActiveWidgets(context)) {
                WidgetSyncCoordinator.cancelPeriodicWidgetSync(context)
                return Result.success()
            }
            if (WidgetSyncCoordinator.syncWidgetsNow(context, force = false)) {
                Result.success()
            } else {
                // Offline or providers unreachable: let WorkManager back off and try again
                // instead of burning the next 30-minute window.
                Result.retry()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppLog.e("Periodic widget sync failed; asking WorkManager to back off", t)
            Result.retry()
        }
    }
}
