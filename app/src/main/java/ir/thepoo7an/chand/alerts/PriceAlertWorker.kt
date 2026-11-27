package ir.thepoo7an.chand.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.thepoo7an.chand.data.MarketRepository
import ir.thepoo7an.chand.data.PreferencesRepository
import ir.thepoo7an.chand.model.DataFreshness
import kotlinx.coroutines.CancellationException

/**
 * Periodic WorkManager task (30-minute interval, NetworkType.CONNECTED) that evaluates
 * enabled price alerts against freshly fetched market rates.
 * Silently skips if offline or if providers return empty/unavailable data.
 */
class PriceAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runAlertCheck(applicationContext)

    companion object {
        internal suspend fun runAlertCheck(appContext: Context): Result {
            val prefsRepo = PreferencesRepository(appContext)
            val alerts = runCatching { prefsRepo.getPriceAlerts() }.getOrDefault(emptyList())
            if (alerts.none { it.isEnabled }) {
                PriceAlertScheduler.cancel(appContext)
                return Result.success()
            }

            val repository = MarketRepository(appContext, prefsRepo)
            val syncResult = try {
                repository.refreshMarket()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                return Result.success()
            }

            val usableAssets = syncResult.assets.filter {
                it.hasUsablePrimaryPrice && it.freshness != DataFreshness.UNAVAILABLE
            }
            if (!syncResult.isLive || usableAssets.isEmpty()) {
                // Offline or stale cached fallback — skip silently without firing stale alerts
                return Result.success()
            }

            val outcome = PriceAlertEvaluator.evaluate(
                alerts = alerts,
                assets = usableAssets,
                nowMillis = System.currentTimeMillis()
            )
            if (outcome.updatedAlerts != alerts) {
                runCatching { prefsRepo.savePriceAlerts(outcome.updatedAlerts) }
            }
            if (outcome.triggeredMatches.isNotEmpty()) {
                val prefs = runCatching { prefsRepo.getPreferences() }.getOrDefault(
                    ir.thepoo7an.chand.model.AppPreferences()
                )
                val assetsById = usableAssets.associateBy { it.id }
                outcome.triggeredMatches.forEach { match ->
                    PriceAlertNotifier.postAlertNotification(
                        context = appContext,
                        match = match,
                        asset = assetsById[match.alert.assetId],
                        prefs = prefs
                    )
                }
            }
            return Result.success()
        }
    }
}
