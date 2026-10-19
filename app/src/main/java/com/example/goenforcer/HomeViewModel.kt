package com.example.goenforcer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ─── MVVM: Home dashboard ────────────────────────────────────────────────
// Reads every stat through one immutable [HomeUiState]. The screen calls
// [refresh] on every ON_RESUME, so returning from the AffirmationScreen
// immediately shows the correct done/unlocked status.

data class HomeUiState(
    val doneToday: Boolean = false,
    val streak: Int = 0,
    val snippets: Int = 0,
    val words: Int = 0,
    val accuracy: Float = 0f,
    /** Affirmations of the NEXT pending window (AM first, then PM). */
    val affirmationText: String = "",
    val morningDone: Boolean = false,
    val eveningDone: Boolean = false,
    val wssGranted: Boolean = false,
    val serviceEnabled: Boolean = false
) {
    /** false = morning window pending, true = evening, null = all done. */
    val activePm: Boolean? = when {
        !morningDone -> false
        !eveningDone -> true
        else -> null
    }

    val windowLabel: String
        get() = when (activePm) {
            false -> "Morning \u00B7 7:00 AM"
            true -> "Evening \u00B7 7:00 PM"
            null -> "Today \u00B7 complete"
        }

    val enforcementStatus: String
        get() = when {
            activePm == null -> "Both sessions written today"
            !wssGranted -> "Enforcement off \u2014 grant via adb:\nadb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
            serviceEnabled -> "Enforcing now \u2014 finish your affirmations"
            else -> "Armed \u2014 accessibility turns on at ${if (activePm == true) "7:00 PM" else "7:00 AM"}"
        }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Idempotent re-read — call on ON_RESUME and after mutations. */
    fun refresh() {
        _state.update { readState() }
    }

    fun onAffirmationCompleted() = refresh()

    private fun readState(): HomeUiState {
        val ctx = getApplication<Application>()
        val amDone = AffirmationStore.isDone(ctx, pm = false)
        val pmDone = AffirmationStore.isDone(ctx, pm = true)
        val activePm = AffirmationStore.activeWindow(ctx)
        return HomeUiState(
            doneToday = Store.isDoneToday(ctx),
            streak = Store.streak(ctx),
            snippets = Store.totalSnippets(ctx),
            words = Store.totalWords(ctx),
            accuracy = Store.accuracy(ctx),
            affirmationText = activePm?.let { AffirmationStore.windowText(ctx, it) } ?: "",
            morningDone = amDone,
            eveningDone = pmDone,
            wssGranted = SecureSettings.hasWriteSecureSettings(ctx),
            serviceEnabled = SecureSettings.isServiceEnabled(ctx)
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                )
            }
        }
    }
}
