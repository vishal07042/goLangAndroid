package com.example.goenforcer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Hosts the enforced morning-affirmation rewrite screen.
// Launched by the AccessibilityService (background-safe) at 7 AM.
class AffirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the enforcement screen awake while the user types.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GoEnforcerTheme {
                AffirmationScreen(onBack = { finish() })
            }
        }
    }
}
