package com.example.goenforcer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Hosts the enforced affirmation rewrite screen.
// Launched by the AccessibilityService (background-safe) at 7 AM / 7 PM.
// The "pm" extra selects the window (false = morning, true = evening).
class AffirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the enforcement screen awake while the user types.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pm = intent?.getBooleanExtra(AffirmationScheduler.EXTRA_PM, false) ?: false
        setContent {
            GoEnforcerTheme {
                AffirmationScreen(pm = pm, onBack = { finish() })
            }
        }
    }
}
