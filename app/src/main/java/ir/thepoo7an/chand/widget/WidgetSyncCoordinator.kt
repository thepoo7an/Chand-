package ir.thepoo7an.chand.widget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates instant widget & market updates whenever the device connects to the internet.
 * Uses a persistent default NetworkCallback while the process is alive AND schedules a zero-dependency
 * system JobScheduler task with NETWORK_TYPE_ANY when offline so Android wakes the widget sync the
 * moment connectivity returns.
 */
object WidgetSyncCoordinator {

    private const val CONNECTIVITY_JOB_ID = 7401
    private const val MIN_SYNC_GAP_MS = 8_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = AtomicBoolean(false)
    private val lastSyncElapsedMs = AtomicLong(0L)
    private val monitorRegistered = AtomicBoolean(false)
    private val wasOffline = AtomicBoolean(false)

    private val _networkRestoredEvents = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val networkRestoredEvents: SharedFlow<Long> = _networkRestoredEvents.asSharedFlow()

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasInternet && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            hasInternet
        }
    }

    fun registerConnectivityMonitor(context: Context) {
        if (!monitorRegistered.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        runCatching {
            wasOffline.set(!isOnline(appContext))
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleNetworkConnected(appContext)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    } else {
                        true
                    }
                    if (hasInternet && validated) {
                        handleNetworkConnected(appContext)
                    }
                }

                override fun onLost(network: Network) {
                    if (!isOnline(appContext)) {
                        wasOffline.set(true)
                        schedulePendingConnectivitySync(appContext)
                    }
                }
            })
        }
    }

    private fun handleNetworkConnected(appContext: Context) {
        val transitionedFromOffline = wasOffline.getAndSet(false)
        _networkRestoredEvents.tryEmit(System.currentTimeMillis())

        scope.launch {
            val prefsRepo = PreferencesRepository(appContext)
            val snapshotAgeMs = prefsRepo.loadCachedSnapshot()?.let {
                System.currentTimeMillis() - it.updatedAtMillis
            } ?: Long.MAX_VALUE

            if (transitionedFromOffline || snapshotAgeMs > 30_000L) {
                syncWidgetsNow(appContext, force = transitionedFromOffline)
            }
        }
    }

    fun triggerAsyncWidgetSync(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        scope.launch {
            syncWidgetsNow(appContext, force = force)
        }
    }

    suspend fun syncWidgetsNow(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        if (!isOnline(appContext)) {
            wasOffline.set(true)
            schedulePendingConnectivitySync(appContext)
            return false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && (nowElapsed - lastSyncElapsedMs.get()) < MIN_SYNC_GAP_MS) {
            return true
        }
        if (!isSyncing.compareAndSet(false, true)) {
            return false
        }

        return try {
            val remote = MarketRemoteDataSource()
            val prefsRepo = PreferencesRepository(appContext)
            val result = remote.fetchLiveMarket()
            if (result.isLive && result.assets.isNotEmpty()) {
                prefsRepo.saveCachedSnapshot(
                    CachedMarketSnapshot(
                        assets = result.assets,
                        updatedAtMillis = result.fetchedAtMillis,
                        sourceSummary = result.sourceSummary
                    )
                )
                lastSyncElapsedMs.set(SystemClock.elapsedRealtime())
                cancelPendingConnectivitySync(appContext)
                updateAllWidgets(appContext)
                true
            } else {
                schedulePendingConnectivitySync(appContext)
                false
            }
        } catch (_: Throwable) {
            schedulePendingConnectivitySync(appContext)
            false
        } finally {
            isSyncing.set(false)
        }
    }

    suspend fun hasAnyActiveWidgets(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return runCatching {
            manager.getGlanceIds(ChandSingleAssetWidget::class.java).isNotEmpty() ||
                manager.getGlanceIds(ChandWideListWidget::class.java).isNotEmpty() ||
                manager.getGlanceIds(ChandCompactCryptoWidget::class.java).isNotEmpty() ||
                manager.getGlanceIds(ChandCompact2x1Widget::class.java).isNotEmpty() ||
                manager.getGlanceIds(ChandLockScreenWidget::class.java).isNotEmpty()
        }.getOrDefault(false)
    }

    fun schedulePendingConnectivitySync(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return
        val existing = scheduler.getPendingJob(CONNECTIVITY_JOB_ID)
        if (existing != null) return

        val jobInfo = JobInfo.Builder(
            CONNECTIVITY_JOB_ID,
            ComponentName(context.applicationContext, WidgetConnectivityJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(15_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()

        runCatching { scheduler.schedule(jobInfo) }
    }

    fun cancelPendingConnectivitySync(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            ?: return
        runCatching { scheduler.cancel(CONNECTIVITY_JOB_ID) }
    }

    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        runCatching { ChandSingleAssetWidget().updateAll(appContext) }
        runCatching { ChandWideListWidget().updateAll(appContext) }
        runCatching { ChandCompactCryptoWidget().updateAll(appContext) }
        runCatching { ChandCompact2x1Widget().updateAll(appContext) }
        runCatching { ChandLockScreenWidget().updateAll(appContext) }
    }
}
