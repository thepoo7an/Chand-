package ir.thepoo7an.chand.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlertDirection {
    ABOVE,
    BELOW,
    BOTH
}

@Serializable
data class PriceAlert(
    val id: String,
    val assetId: String,
    val upperBoundToman: Double? = null,
    val lowerBoundToman: Double? = null,
    val isEnabled: Boolean = true,
    val triggeredUpper: Boolean = false,
    val triggeredLower: Boolean = false,
    val lastTriggeredAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L
) {
    val hasValidBounds: Boolean
        get() {
            val upperOk = upperBoundToman == null || (upperBoundToman.isFinite() && upperBoundToman > 0.0)
            val lowerOk = lowerBoundToman == null || (lowerBoundToman.isFinite() && lowerBoundToman > 0.0)
            val hasAtLeastOne = (upperBoundToman != null && upperBoundToman > 0.0) ||
                (lowerBoundToman != null && lowerBoundToman > 0.0)
            val orderOk = if (upperBoundToman != null && lowerBoundToman != null) {
                upperBoundToman > lowerBoundToman
            } else {
                true
            }
            return upperOk && lowerOk && hasAtLeastOne && orderOk
        }

    val direction: AlertDirection
        get() = when {
            upperBoundToman != null && lowerBoundToman != null -> AlertDirection.BOTH
            upperBoundToman != null -> AlertDirection.ABOVE
            else -> AlertDirection.BELOW
        }

    val isTriggered: Boolean
        get() = triggeredUpper || triggeredLower
}

data class AlertTriggerMatch(
    val alert: PriceAlert,
    val matchedUpper: Boolean,
    val matchedLower: Boolean,
    val currentPriceToman: Double
)

data class AlertEvaluationOutcome(
    val updatedAlerts: List<PriceAlert>,
    val triggeredMatches: List<AlertTriggerMatch>
)
