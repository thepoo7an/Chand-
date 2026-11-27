package ir.thepoo7an.chand.widget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.data.SnapshotPolicy
import ir.thepoo7an.chand.model.CachedMarketSnapshot
import ir.thepoo7an.chand.network.InFlightRequestDeduplicator
import ir.thepoo7an.chand.network.MarketRemoteDataSource
import ir.thepoo7an.chand.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates instant widget & market updates whenever the device connects to the internet.
 * Uses a persistent default NetworkCallback while the process is alive, schedules a one-shot
 * `JobScheduler` task with NETWORK_TYPE_ANY while offline so Android wakes the widget sync the
 * moment connectivity returns, and keeps the 30-minute periodic refresh in a **persisted
 * WorkManager** job so it survives reboots.
 */
object WidgetSyncCoordinator {

    const val CONNECTIVITY_JOB_ID = 7401
    const val PERIODIC_WORK_NAME = "chand_widget_periodic_sync"
    private const val PERIODIC_SYNC_INTERVAL_MINUTES = 30L
    private const val MIN_SYNC_GAP_MS = 8_000L

    private data class WidgetBinding(
        val receiverClass: Class<out GlanceAppWidgetReceiver>,
        val widgetClass: Class<out GlanceAppWidget>,
        val createWidget: () -> GlanceAppWidget
    )

    private val widgetBindings = listOf(
        WidgetBinding(
            ChandSingleWidgetReceiver::class.java,
            ChandSingleAssetWidget::class.java,
            ::ChandSingleAssetWidget
        ),
        WidgetBinding(
            ChandWidgetReceiver::class.java,
            ChandWideListWidget::class.java,
            ::ChandWideListWidget
        ),
        WidgetBinding(
            ChandCryptoWidgetReceiver::class.java,
            ChandCompactCryptoWidget::class.java,
            ::ChandCompactCryptoWidget
        ),
        WidgetBinding(
            ChandCompact2x1WidgetReceiver::class.java,
            ChandCompact2x1Widget::class.java,
            ::ChandCompact2x1Widget
        ),
        WidgetBinding(
            ChandLockScreenWidgetReceiver::class.java,
            ChandLockScreenWidget::class.java,
            ::ChandLockScreenWidget
        )
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncDeduplicator = InFlightRequestDeduplicator<String, Boolean>()
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
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
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
        // Only announce a genuine offline -> online transition. Emitting on every capability
        // change made flaky networks trigger a refresh chain on the in-app polling loop.
        if (transitionedFromOffline) {
            _networkRestoredEvents.tryEmit(System.currentTimeMillis())
        }

        scope.launch {
            val prefsRepo = PreferencesRepository(appContext)
            val snapshot = prefsRepo.loadCachedSnapshot()

            if (
                transitionedFromOffline ||
                SnapshotPolicy.shouldRevalidate(snapshot?.updatedAtMillis, System.currentTimeMillis())
            ) {
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

    fun hydrateLiveConfigAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { PreferencesRepository(appContext).hydrateLiveConfig() }
        }
    }

    fun refreshWidgetsOnStartupIfNeeded(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val prefsRepo = PreferencesRepository(appContext)
            runCatching { prefsRepo.hydrateLiveConfig() }
            if (!hasAnyActiveWidgets(appContext)) return@launch
            // Paint from cache first, then decide whether the cache is still worth trusting.
            updateAllWidgets(appContext)
            val snapshot = prefsRepo.loadCachedSnapshot()
            if (SnapshotPolicy.shouldRevalidate(snapshot?.updatedAtMillis, System.currentTimeMillis())) {
                syncWidgetsNow(appContext, force = false)
            }
        }
    }

    suspend fun syncWidgetsNow(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        if (!force && !isOnline(appContext)) {
            wasOffline.set(true)
            schedulePendingConnectivitySync(appContext)
            return false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && (nowElapsed - lastSyncElapsedMs.get()) < MIN_SYNC_GAP_MS) {
            return true
        }

        return syncDeduplicator.execute("widget_sync") {
            try {
                val remote = MarketRemoteDataSource()
                val prefsRepo = PreferencesRepository(appContext)
                prefsRepo.hydrateLiveConfig()
                val cached = prefsRepo.loadCachedSnapshot()
                val cachedById = cached?.assets?.associateBy { it.id }.orEmpty()
                val lastUsd = cachedById["USD"]?.priceToman?.takeIf { it > 1_000.0 }
                val result = remote.fetchLiveMarket(
                    lastKnownUsdToman = lastUsd,
                    cachedAssetsById = cachedById
                )
                if (result.isLive && result.assets.isNotEmpty()) {
                    prefsRepo.saveCachedSnapshot(
                        CachedMarketSnapshot(
                            assets = result.assets,
                            updatedAtMillis = result.fetchedAtMillis,
                            sourceSummary = result.sourceSummary,
                            freshness = result.freshness
                        )
                    )
                    lastSyncElapsedMs.set(SystemClock.elapsedRealtime())
                    cancelPendingConnectivitySync(appContext)
                    recordRefreshOutcome(prefsRepo, success = true)
                    updateAllWidgets(appContext)
                    true
                } else {
                    // A widget-only user never opens the app, so this path has to feed the same
                    // live-config self-healing counters the in-app refresh does.
                    val rolledBack = recordRefreshOutcome(prefsRepo, success = false)
                    schedulePendingConnectivitySync(appContext)
                    if (rolledBack) updateAllWidgets(appContext)
                    false
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                AppLog.e("Widget sync failed; scheduling a connectivity retry", t)
                val rolledBack = recordRefreshOutcome(prefsRepo, success = false)
                schedulePendingConnectivitySync(appContext)
                if (rolledBack) updateAllWidgets(appContext)
                false
            }
        }
    }

    /**
     * Reports a widget-only refresh to the live-config failure streak. Returns `true` when this
     * failure tripped the self-healing rollback, so the caller can repaint with the default
     * endpoints. Never throws: a bookkeeping problem must not abort a widget sync.
     */
    private suspend fun recordRefreshOutcome(
        prefsRepo: PreferencesRepository,
        success: Boolean
    ): Boolean = runCatching {
        prefsRepo.recordMarketRefreshOutcome(success)
    }.onFailure {
        AppLog.w("Could not record the widget refresh outcome", it)
    }.getOrDefault(false)

    suspend fun hasAnyActiveWidgets(context: Context): Boolean {
        val appContext = context.applicationContext
        val appWidgetManager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull()
        if (appWidgetManager != null) {
            val hasBound = runCatching {
                widgetBindings.any { binding ->
                    appWidgetManager.getAppWidgetIds(ComponentName(appContext, binding.receiverClass)).isNotEmpty()
                }
            }.getOrDefault(false)
            if (hasBound) return true
        }

        val manager = GlanceAppWidgetManager(appContext)
        return runCatching {
            widgetBindings.any { binding ->
                manager.getGlanceIds(binding.widgetClass).isNotEmpty()
            }
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

    /**
     * Schedules the 30-minute background widget refresh through WorkManager so it is persisted
     * across reboots and app upgrades (`JobScheduler` with `setPersisted(false)` is not).
     * `ExistingPeriodicWorkPolicy.KEEP` preserves the running window, matching the previous
     * "already scheduled -> do nothing" behaviour.
     */
    fun schedulePeriodicWidgetSync(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<WidgetPeriodicSyncWorker>(
                PERIODIC_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun cancelPeriodicWidgetSync(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }

    fun ensurePeriodicSyncIfWidgetsExist(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            if (hasAnyActiveWidgets(appContext)) {
                schedulePeriodicWidgetSync(appContext)
            } else {
                cancelPeriodicWidgetSync(appContext)
            }
        }
    }

    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull()
        val glanceManager = GlanceAppWidgetManager(appContext)

        widgetBindings.forEach { binding ->
            val widget = binding.createWidget()
            val updatedIds = linkedSetOf<GlanceId>()

            if (appWidgetManager != null) {
                val boundIds = runCatching {
                    appWidgetManager.getAppWidgetIds(ComponentName(appContext, binding.receiverClass))
                }.getOrDefault(IntArray(0))

                for (appWidgetId in boundIds) {
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) continue
                    val glanceId = runCatching { glanceManager.getGlanceIdBy(appWidgetId) }.getOrNull()
                        ?: continue
                    updatedIds += glanceId
                    try {
                        widget.update(appContext, glanceId)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        // Guard against uninitialized WorkManager in isolated unit tests
                    }
                }
            }

            val knownGlanceIds = try {
                glanceManager.getGlanceIds(binding.widgetClass)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                emptyList()
            }

            if (knownGlanceIds.isEmpty() && updatedIds.isEmpty()) {
                try {
                    widget.updateAll(appContext)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                }
            } else {
                for (glanceId in knownGlanceIds) {
                    if (glanceId !in updatedIds) {
                        try {
                            widget.update(appContext, glanceId)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }
}
