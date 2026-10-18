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

// Fired by the 7:00 AM exact alarm.
// 1. Re-arms tomorrow's alarm (rolling, one shot at a time).
// 2. If today's affirmation isn't written: enables the AccessibilityService
//    via WRITE_SECURE_SETTINGS — the service then force-launches the
//    AffirmationScreen on connect and keeps this app in front.
class AffirmationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        // Rolling schedule: always have the next 7 AM armed.
        AffirmationScheduler.schedule(ctx)

        if (AffirmationStore.isDoneToday(ctx)) return
        if (!AffirmationStore.enforceEnabled(ctx)) return

        // Flip accessibility ON for today's enforcement window only.
        SecureSettings.setServiceEnabled(ctx, enable = true)

        // Belt & braces: accessibility binds asynchronously; try a direct
        // launch too (allowed for exact-alarm delivery / recent activity).
        if (SecureSettings.isServiceEnabled(ctx)) {
            Handler(Looper.getMainLooper()).postDelayed({
                val svc = EnforcerAccessibilityService.instance
                svc?.launchAffirmationScreen()
                    ?: runCatching {
                        ctx.startActivity(
                            Intent(ctx, AffirmationActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
            }, 1_500)
        }

        notifyUser(ctx)
    }

    private fun notifyUser(ctx: Context) {
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
            ctx, 4211,
            Intent(ctx, AffirmationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(ctx.getString(R.string.enforcer_notification_title))
            .setContentText(ctx.getString(R.string.enforcer_notification_text))
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
        if (canPost) nm.notify(4211, builder.build())
    }
}
