package com.example.goenforcer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

// ─── 7:00 AM + 7:00 PM exact alarms ──────────────────────────────────────
// Uses setAlarmClock() with USE_EXACT_ALARM (auto-granted at install).
//
// setAlarmClock() is the ONE exact-alarm API the system never defers: it
// exits Doze shortly before firing and delivers at the requested moment,
// same reliability class as the user's own alarm clock. A small system
// alarm indicator appears in the status bar, which fits this feature
// (the user SHOULD see their obligation coming). It also exempts the
// alarm from background activity-launch restrictions, so the
// AffirmationScreen launch is allowed straight from the receiver/service.
//
// Two windows per day: 7:00 AM (am) and 7:00 PM (pm), each enforcing a
// fresh random pick of 3 affirmations.
object AffirmationScheduler {

    const val ENFORCE_HOUR_AM = 7
    const val ENFORCE_HOUR_PM = 19
    const val ENFORCE_MINUTE = 0

    private const val ACTION_FIRED = "com.example.goenforcer.AFFIRMATION_ALARM"
    const val EXTRA_PM = "pm"

    private fun pi(ctx: Context, pm: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, if (pm) 4210 else 4214,
            Intent(ctx, AffirmationAlarmReceiver::class.java)
                .setAction(ACTION_FIRED)
                .putExtra(EXTRA_PM, pm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Next [hour]:00 relative to [now]; rolls to tomorrow if past. */
    fun nextTriggerMillis(pm: Boolean, now: Calendar = Calendar.getInstance()): Long {
        val hour = if (pm) ENFORCE_HOUR_PM else ENFORCE_HOUR_AM
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, ENFORCE_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis + 30_000) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    /** Arms both the AM and the PM window. Safe to call repeatedly. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // USE_EXACT_ALARM is install-granted, but honor revocation anyway —
        // scheduling without the permission would throw SecurityException.
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return

        // Shown in the system alarm UI as the enforcement reminder.
        val showAm = PendingIntent.getActivity(
            ctx, 4212,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PM, false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPm = PendingIntent.getActivity(
            ctx, 4213,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PM, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextTriggerMillis(pm = false), showAm),
            pi(ctx, pm = false)
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(nextTriggerMillis(pm = true), showPm),
            pi(ctx, pm = true)
        )
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, pm = false))
        am.cancel(pi(ctx, pm = true))
    }
}
