package com.example.goenforcer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// ─── Reading Enforcement host ──────────────────────────────────────────
// Separate activity (same pattern as AffirmationActivity) so the reader can
// be launched from the Enforcer dashboard. No enforcement is wired yet —
// this is the reading surface only.
class ReadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoEnforcerTheme {
                ReadingScreen(onBack = { finish() })
            }
        }
    }
}
