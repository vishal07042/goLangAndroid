package com.example.goenforcer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

// Fired by the 7:00 AM / 7:00 PM exact alarms.
// 1. Re-arms both windows (rolling, one shot each at a time).
// 2. If this window's affirmations aren't written yet: enables the
//    AccessibilityService via WRITE_SECURE_SETTINGS — the service then
//    force-launches the AffirmationScreen on connect and keeps this app
//    in front.
class AffirmationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val pm = intent.getBooleanExtra(AffirmationScheduler.EXTRA_PM, false)

        // Rolling schedule: always have the next AM + PM alarms armed.
        AffirmationScheduler.schedule(ctx)

        // This window already finished → nothing to enforce.
        if (AffirmationStore.isDone(ctx, pm)) return
        if (!AffirmationStore.enforceEnabled(ctx)) return

        // Flip accessibility ON for this window only.
        SecureSettings.setServiceEnabled(ctx, enable = true)

        // Belt & braces: accessibility binds asynchronously; try a direct
        // launch too (setAlarmClock delivery is BAL-exempt).
        if (SecureSettings.isServiceEnabled(ctx)) {
            Handler(Looper.getMainLooper()).postDelayed({
                val svc = EnforcerAccessibilityService.instance
                svc?.launchAffirmationScreen(pm)
                    ?: runCatching {
                        ctx.startActivity(
                            Intent(ctx, AffirmationActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(AffirmationScheduler.EXTRA_PM, pm)
                        )
                    }
            }, 1_500)
        }

        notifyUser(ctx, pm)
    }

    private fun notifyUser(ctx: Context, pm: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "affirmation_enforcer"
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                ctx.getString(R.string.enforcer_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val fullScreen = PendingIntent.getActivity(
            ctx, if (pm) 4215 else 4216,
            Intent(ctx, AffirmationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AffirmationScheduler.EXTRA_PM, pm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val windowLabel = if (pm) "Evening (7 PM)" else "Morning (7 AM)"
        val builder = Notification.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(ctx.getString(R.string.enforcer_notification_title))
            .setContentText("$windowLabel \u00B7 ${AffirmationStore.PICK_COUNT} affirmations \u2014 " +
                ctx.getString(R.string.enforcer_notification_text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)

        if (Build.VERSION.SDK_INT >= 31) {
            builder.setFullScreenIntent(fullScreen, true)
        } else {
            builder.setContentIntent(fullScreen)
        }

        val canPost = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) nm.notify(if (pm) 4215 else 4216, builder.build())
    }
}
