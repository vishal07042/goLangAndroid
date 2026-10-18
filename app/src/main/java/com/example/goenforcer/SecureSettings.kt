package com.example.goenforcer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

// ─── WRITE_SECURE_SETTINGS bridge ────────────────────────────────────────
// Grant once via:
//   adb shell pm grant com.example.goenforcer android.permission.WRITE_SECURE_SETTINGS
//
// With that, the 7 AM alarm can flip our AccessibilityService on, and the
// service can flip itself off the moment the affirmation is rewritten —
// so accessibility is NEVER left on outside the enforcement window.
//
// Component identity is derived from the context + the service class
// itself — no hardcoded package strings, so an applicationId change can
// never silently break enforcement.
object SecureSettings {

    private fun component(ctx: Context): ComponentName =
        ComponentName(ctx, EnforcerAccessibilityService::class.java)

    private fun componentFlat(ctx: Context): String = component(ctx).flattenToString()

    fun hasWriteSecureSettings(ctx: Context): Boolean =
        ctx.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun enabledServices(ctx: Context): String =
        Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

    fun isServiceEnabled(ctx: Context): Boolean {
        val flat = componentFlat(ctx)
        val legacy = "${ctx.packageName}/.EnforcerAccessibilityService"
        return enabledServices(ctx).split(':').any {
            it.equals(flat, ignoreCase = true) || it.equals(legacy, ignoreCase = true)
        }
    }

    /** Toggle ONLY our service, preserving every other enabled service. */
    fun setServiceEnabled(ctx: Context, enable: Boolean) {
        if (!hasWriteSecureSettings(ctx)) return

        val flat = componentFlat(ctx)
        val legacy = "${ctx.packageName}/.EnforcerAccessibilityService"

        val parts = enabledServices(ctx)
            .split(':')
            .filter { it.isNotEmpty() }
            .toMutableList()

        parts.removeAll { it.equals(flat, true) || it.equals(legacy, true) }
        if (enable) parts.add(flat)

        Settings.Secure.putString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            parts.joinToString(":")
        )
        Settings.Secure.putInt(
            ctx.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            if (parts.isEmpty()) 0 else 1
        )
    }
}
