package ir.thepoo7an.chand.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class BaseChandWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncCoordinator.registerConnectivityMonitor(context.applicationContext)
        WidgetSyncCoordinator.triggerAsyncWidgetSync(context.applicationContext, force = true)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSyncCoordinator.registerConnectivityMonitor(context.applicationContext)
        WidgetSyncCoordinator.triggerAsyncWidgetSync(context.applicationContext, force = false)
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
