package ir.thepoo7an.chand.widget

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * System-managed JobService triggered automatically as soon as internet connectivity is restored.
 * Fetches live market quotes and updates all active Chand?! home & lock screen widgets.
 */
class WidgetConnectivityJobService : JobService() {

    private var serviceScope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        val isOneShot = params?.jobId == WidgetSyncCoordinator.CONNECTIVITY_JOB_ID
        scope.launch {
            val ok = try {
                val hasWidgets = WidgetSyncCoordinator.hasAnyActiveWidgets(applicationContext)
                if (!hasWidgets) {
                    WidgetSyncCoordinator.cancelPeriodicWidgetSync(applicationContext)
                    true
                } else {
                    WidgetSyncCoordinator.syncWidgetsNow(applicationContext, force = true)
                }
            } catch (_: Throwable) {
                false
            }
            jobFinished(params, !ok && isOneShot)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope?.cancel()
        serviceScope = null
        return true
    }
}
