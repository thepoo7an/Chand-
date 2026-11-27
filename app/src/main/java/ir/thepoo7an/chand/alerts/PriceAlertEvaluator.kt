package ir.thepoo7an.chand.alerts

import ir.thepoo7an.chand.model.AlertEvaluationOutcome
import ir.thepoo7an.chand.model.AlertTriggerMatch
import ir.thepoo7an.chand.model.DataFreshness
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.model.PriceAlert

/**
 * Pure threshold evaluator implementing once-per-crossing hysteresis:
 * - Fires when price reaches/crosses an enabled bound and has not already fired for that crossing.
 * - Automatically re-arms once price returns strictly inside the bound (or when manually re-armed).
 * - Skips UNAVAILABLE or non-positive/non-finite asset quotes without losing armed state.
 */
object PriceAlertEvaluator {

    fun evaluate(
        alerts: List<PriceAlert>,
        assets: List<MarketAsset>,
        nowMillis: Long = System.currentTimeMillis()
    ): AlertEvaluationOutcome {
        if (alerts.isEmpty() || assets.isEmpty()) {
            return AlertEvaluationOutcome(updatedAlerts = alerts, triggeredMatches = emptyList())
        }

        val assetsById = assets.associateBy { it.id }
        val updated = ArrayList<PriceAlert>(alerts.size)
        val matches = mutableListOf<AlertTriggerMatch>()

        for (alert in alerts) {
            if (!alert.isEnabled || !alert.hasValidBounds) {
                updated.add(alert)
                continue
            }
            val asset = assetsById[alert.assetId]
            if (asset == null ||
                asset.freshness == DataFreshness.UNAVAILABLE ||
                !asset.priceToman.isFinite() ||
                asset.priceToman <= 0.0
            ) {
                updated.add(alert)
                continue
            }

            val price = asset.priceToman
            val upper = alert.upperBoundToman?.takeIf { it.isFinite() && it > 0.0 }
            val lower = alert.lowerBoundToman?.takeIf { it.isFinite() && it > 0.0 }

            var nextTriggeredUpper = alert.triggeredUpper
            var nextTriggeredLower = alert.triggeredLower
            var firedUpper = false
            var firedLower = false

            if (upper != null) {
                if (price >= upper) {
                    if (!alert.triggeredUpper) {
                        firedUpper = true
                        nextTriggeredUpper = true
                    }
                } else {
                    nextTriggeredUpper = false
                }
            }

            if (lower != null) {
                if (price <= lower) {
                    if (!alert.triggeredLower) {
                        firedLower = true
                        nextTriggeredLower = true
                    }
                } else {
                    nextTriggeredLower = false
                }
            }

            val nextAlert = alert.copy(
                triggeredUpper = nextTriggeredUpper,
                triggeredLower = nextTriggeredLower,
                lastTriggeredAtMillis = if (firedUpper || firedLower) nowMillis else alert.lastTriggeredAtMillis
            )
            updated.add(nextAlert)

            if (firedUpper || firedLower) {
                matches.add(
                    AlertTriggerMatch(
                        alert = nextAlert,
                        matchedUpper = firedUpper,
                        matchedLower = firedLower,
                        currentPriceToman = price
                    )
                )
            }
        }

        return AlertEvaluationOutcome(
            updatedAlerts = updated,
            triggeredMatches = matches
        )
    }

    fun rearm(alert: PriceAlert): PriceAlert =
        alert.copy(triggeredUpper = false, triggeredLower = false)
}
