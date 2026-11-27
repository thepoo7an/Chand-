package ir.thepoo7an.chand.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.thepoo7an.chand.MainActivity
import ir.thepoo7an.chand.R
import ir.thepoo7an.chand.model.AlertTriggerMatch
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.stringsFor

/**
 * Posts high-priority price alert notifications without requiring any extra manifest permissions.
 * Respects OS-level notification settings gracefully via [NotificationManagerCompat.areNotificationsEnabled].
 */
object PriceAlertNotifier {

    const val CHANNEL_ID = "price_alerts"
    private const val TEST_NOTIFICATION_ID = 70_000

    fun ensureChannel(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val strings = stringsFor(language)
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.alertNotificationChannelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = strings.alertNotificationChannelDesc
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)

    fun postAlertNotification(
        context: Context,
        match: AlertTriggerMatch,
        asset: MarketAsset?,
        prefs: AppPreferences
    ): Boolean {
        val strings = stringsFor(prefs.language)
        ensureChannel(context, prefs.language)
        if (!areNotificationsEnabled(context)) return false

        val assetName = if (prefs.language == AppLanguage.FA) {
            asset?.nameFa ?: match.alert.assetId
        } else {
            asset?.nameEn ?: match.alert.assetId
        }
        val symbol = asset?.symbol ?: match.alert.assetId
        val priceText = NumberFormatters.formatToman(match.currentPriceToman, prefs.usePersianDigits)

        val reasonText = when {
            match.matchedUpper && match.alert.upperBoundToman != null -> {
                val bound = NumberFormatters.formatToman(match.alert.upperBoundToman, prefs.usePersianDigits)
                "${strings.alertNotificationUpperText} ($bound ${strings.tomanUnit})"
            }
            match.matchedLower && match.alert.lowerBoundToman != null -> {
                val bound = NumberFormatters.formatToman(match.alert.lowerBoundToman, prefs.usePersianDigits)
                "${strings.alertNotificationLowerText} ($bound ${strings.tomanUnit})"
            }
            else -> ""
        }

        val title = "$assetName ($symbol) • $priceText ${strings.tomanUnit}"
        return postNotification(
            context = context,
            notificationId = 70_100 + (match.alert.id.hashCode() and 0x7FFF),
            title = title,
            body = reasonText
        )
    }

    fun postTestNotification(
        context: Context,
        prefs: AppPreferences
    ): Boolean {
        val strings = stringsFor(prefs.language)
        ensureChannel(context, prefs.language)
        if (!areNotificationsEnabled(context)) return false
        return postNotification(
            context = context,
            notificationId = TEST_NOTIFICATION_ID,
            title = strings.alertTestNotificationTitle,
            body = strings.alertTestNotificationBody
        )
    }

    private fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ): Boolean {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
