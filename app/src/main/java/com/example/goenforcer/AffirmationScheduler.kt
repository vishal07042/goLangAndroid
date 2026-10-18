package com.example.goenforcer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

// ─── 7:00 AM exact alarm ─────────────────────────────────────────────────
// Uses setAlarmClock() with USE_EXACT_ALARM (auto-granted at install — no
// special-access settings screen).
//
// setAlarmClock() is the ONE exact-alarm API the system never defers: it
// exits Doze shortly before firing and delivers at the requested moment,
// same reliability class as the user's own alarm clock. A small system
// alarm indicator appears in the status bar, which fits this feature
// (the user SHOULD see their 7 AM obligation). It also exempts the alarm
// from background activity-launch restrictions, so the AffirmationScreen
// launch at 7 AM is allowed straight from the receiver/service.
//
// Alternatives: setExact() waits for the next maintenance window during
// Doze; setExactAndAllowWhileIdle() may fire up to ~15 min late in idle
// and can be rescheduled out of order. Neither guarantees 7:00 sharp.
object AffirmationScheduler {

    const val ENFORCE_HOUR = 7
    const val ENFORCE_MINUTE = 0

    private const val ACTION_FIRED = "com.example.goenforcer.AFFIRMATION_ALARM"

    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 4210,
            Intent(ctx, AffirmationAlarmReceiver::class.java).setAction(ACTION_FIRED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Next 7:00 AM relative to [now] (injectable for tests). */
    fun nextTriggerMillis(now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, ENFORCE_HOUR)
            set(Calendar.MINUTE, ENFORCE_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis + 30_000) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    /** Arms today's/tomorrow's 7 AM alarm. Safe to call repeatedly. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // USE_EXACT_ALARM is install-granted, but honor revocation anyway —
        // scheduling without the permission would throw SecurityException.
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        val trigger = nextTriggerMillis()
        // Shown in the system alarm UI as the 7:00 AM enforcement reminder.
        val showIntent = PendingIntent.getActivity(
            ctx, 4212,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(trigger, showIntent),
            pi(ctx)
        )
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx))
    }
}
