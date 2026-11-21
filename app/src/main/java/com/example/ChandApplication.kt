package com.example

import android.app.Application
import com.example.widget.WidgetSyncCoordinator

class ChandApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetSyncCoordinator.registerConnectivityMonitor(this)
    }
}
