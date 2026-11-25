package ir.thepoo7an.chand

import android.app.Application
import ir.thepoo7an.chand.widget.WidgetSyncCoordinator

class ChandApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetSyncCoordinator.hydrateLiveConfigAsync(this)
        WidgetSyncCoordinator.registerConnectivityMonitor(this)
        WidgetSyncCoordinator.ensurePeriodicSyncIfWidgetsExist(this)
        WidgetSyncCoordinator.refreshWidgetsOnStartupIfNeeded(this)
    }
}
