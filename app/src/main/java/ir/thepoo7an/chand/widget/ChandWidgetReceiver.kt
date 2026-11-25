package ir.thepoo7an.chand.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseChandWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        WidgetSyncCoordinator.registerConnectivityMonitor(appContext)
        WidgetSyncCoordinator.schedulePeriodicWidgetSync(appContext)
        WidgetSyncCoordinator.triggerAsyncWidgetSync(appContext, force = true)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val appContext = context.applicationContext
        WidgetSyncCoordinator.registerConnectivityMonitor(appContext)
        WidgetSyncCoordinator.schedulePeriodicWidgetSync(appContext)
        WidgetSyncCoordinator.triggerAsyncWidgetSync(appContext, force = false)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSyncCoordinator.ensurePeriodicSyncIfWidgetsExist(context.applicationContext)
    }
}

class ChandWidgetReceiver : BaseChandWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandWideListWidget()
}

class ChandSingleWidgetReceiver : BaseChandWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandSingleAssetWidget()
}

class ChandCryptoWidgetReceiver : BaseChandWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandCompactCryptoWidget()
}

class ChandCompact2x1WidgetReceiver : BaseChandWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandCompact2x1Widget()
}

class ChandLockScreenWidgetReceiver : BaseChandWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandLockScreenWidget()
}

/**
 * Re-publishes Glance RemoteViews and schedules sync right after an APK upgrade,
 * because Android resets launcher widgets to `android:initialLayout` on MY_PACKAGE_REPLACED.
 */
class ChandPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetSyncCoordinator.registerConnectivityMonitor(appContext)
                if (WidgetSyncCoordinator.hasAnyActiveWidgets(appContext)) {
                    WidgetSyncCoordinator.schedulePeriodicWidgetSync(appContext)
                    WidgetSyncCoordinator.updateAllWidgets(appContext)
                    WidgetSyncCoordinator.triggerAsyncWidgetSync(appContext, force = true)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Keep receiver resilient on restricted OEM background states
            } finally {
                pendingResult.finish()
            }
        }
    }
}
