package com.example.goenforcer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

// ─── Affirmation Enforcer service ───────────────────────────────────────
// Enabled by the 7 AM / 7 PM exact alarms via WRITE_SECURE_SETTINGS
// (SecureSettings) and disables ITSELF the moment that session's
// affirmations are rewritten — accessibility is only ever on inside a
// single enforcement window, never between them.
//
// Loop: while the current window's affirmations are unfinished, any
// non-whitelisted app in the foreground gets pushed to Home and the
// AffirmationScreen is pulled straight back to the front.
class EnforcerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: EnforcerAccessibilityService? = null
            private set

        /** Apps the user always needs — closing these would trap them.
         *  com.cron.calendar = hourly logging app the user must be able to
         *  use freely; enforcement resumes the moment they leave it. */
        private val PASS = setOf(
            BuildConfig.APPLICATION_ID,
            "com.cron.calendar",
            "com.android.systemui",
            "com.android.settings",
            "android",
            "com.miui.home", "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            // Phone: dialer + in-call UI (stock AOSP, Google, Samsung) —
            // taking a call must never be interrupted by enforcement.
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.phone",
            "com.android.mms",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastActionAt = 0L

    override fun onServiceConnected() {
        instance = this
        // Freshly enabled → make sure the affirmation screen is up.
        handler.postDelayed({ enforce("connect") }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> enforce("event")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun enforce(reason: String) {
        val ctx = applicationContext

        // Both windows done → shut ourselves off. Accessibility stays off
        // until the next 7 AM / 7 PM alarm re-enables it.
        if (AffirmationStore.activeWindow(ctx) == null) {
            SecureSettings.setServiceEnabled(ctx, enable = false)
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        if (!AffirmationStore.enforceEnabled(ctx)) return

        // Each session closes its own window (the VM disables this service
        // the moment the session is rewritten). A briefly-still-bound
        // service must NOT jump to enforcing the next window — only
        // enforce the window that matches the current time.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val currentWindow = hour >= AffirmationScheduler.ENFORCE_HOUR_PM // false = 7 AM, true = 7 PM
        if (AffirmationStore.isDone(ctx, currentWindow)) return

        // Don't fight ourselves or the launcher/settings.
        val fg = currentForeground()
        if (fg != null && fg in PASS) return

        // Throttle: never thrash more than once per second.
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 1000) return
        lastActionAt = now

        // Kick whatever foreign app is open to Home…
        performGlobalAction(GLOBAL_ACTION_HOME)
        // …and pull our screen back up.
        handler.postDelayed({ launchAffirmationScreen(currentWindow) }, 350)
    }

    /** Launch (or re-focus) the AffirmationScreen for [pm] window. */
    fun launchAffirmationScreen(pm: Boolean) {
        if (AffirmationStore.isDone(applicationContext, pm)) return
        runCatching {
            startActivity(
                Intent(this, AffirmationActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(AffirmationScheduler.EXTRA_PM, pm)
            )
        }
    }

    private fun currentForeground(): String? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()
        if (pkg != null) return pkg
        val id = root.viewIdResourceName ?: return null
        return id.substringBefore('/')
    }
}

// Re-arm the 7 AM / 7 PM alarms after reboot / update / permission re-grant.
class EnforcerBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Strict action match — anything else is ignored.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                // schedule() itself re-checks canScheduleExactAlarms() and
                // no-ops safely if exact alarms were revoked.
                AffirmationScheduler.schedule(ctx)
        }
    }
}
