package ir.thepoo7an.chand.alerts

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.thepoo7an.chand.MainActivity
import ir.thepoo7an.chand.R
import ir.thepoo7an.chand.model.AlertTriggerMatch
import ir.thepoo7an.chand.model.AppLanguage
import ir.thepoo7an.chand.model.AppPreferences
import ir.thepoo7an.chand.model.MarketAsset
import ir.thepoo7an.chand.ui.strings.NumberFormatters
import ir.thepoo7an.chand.ui.strings.stringsFor

/**
 * Posts high-priority price alert notifications and owns the `POST_NOTIFICATIONS` runtime
 * permission flow (Android 13+). Respects OS-level notification settings via
 * [NotificationManagerCompat.areNotificationsEnabled] and never reports success when the
 * notification was not actually handed to the system.
 */
object PriceAlertNotifier {

    const val CHANNEL_ID = "price_alerts"
    private const val TEST_NOTIFICATION_ID = 70_000
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9_301

    /** True when the app is allowed to post notifications (permission granted + app not muted). */
    fun areNotificationsEnabled(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        return runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
    }

    /** True when POST_NOTIFICATIONS is granted; always true below Android 13. */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * App notification settings screen. Used as the actionable fallback when the user disabled
     * notifications at OS level (permission request alone cannot re-enable a muted app).
     */
    fun createNotificationSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // API 24–25 has no dedicated notification-settings screen.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Requests POST_NOTIFICATIONS when it was never granted, otherwise opens the system
     * notification settings so the user can un-mute the app. Safe to call from a click handler.
     */
    fun ensureNotificationsAllowed(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context)
        ) {
            val activity = context.findActivity()
            if (activity != null && !activity.isFinishing) {
                runCatching {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
                return
            }
        }
        runCatching { context.startActivity(createNotificationSettingsIntent(context)) }
            .onFailure {
                // Some OEM launchers/ROMs do not expose the notification-settings screen.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

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
            // Monochrome vector is required for a correct status-bar silhouette; launcher
            // mipmaps render as a blank/grey blob as small icons.
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
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
