package ir.thepoo7an.chand.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

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
