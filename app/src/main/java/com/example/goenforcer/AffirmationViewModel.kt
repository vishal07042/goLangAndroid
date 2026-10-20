package com.example.goenforcer

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── MVVM: Affirmation Enforcer ──────────────────────────────────────────
// All typing/enforcement logic lives here — the composable only renders
// [AffirmationUiState] and forwards user input + one-shot [TypingEffect]s
// (haptics / shake / toast). Survives rotation; no logic in composition.

data class AffirmationUiState(
    val affirmations: List<String> = emptyList(),
    val segments: List<Segment> = emptyList(),
    val session: Session = Session(words = emptyList()),
    val inputText: String = "",
    val correctChars: Int = 0,
    val wrongKeys: Int = 0,
    val mood: MascotMood = MascotMood.Idle,
    val celebrating: Boolean = false,
    val finished: Boolean = false
) {
    val wordsTotal: Int get() = session.words.size
    val wordsDone: Int get() = session.wordIndex.coerceAtMost(wordsTotal)
    val wordsToGo: Int get() = wordsTotal - wordsDone
    val progress: Float get() = if (wordsTotal == 0) 0f else wordsDone.toFloat() / wordsTotal
    val accuracyPercent: Int
        get() = (correctChars.toFloat() / (correctChars + wrongKeys).coerceAtLeast(1) * 100).toInt()
}

/** One-shot UI events. Collected once — never stored in state. */
sealed interface TypingEffect {
    /** Correct character: light haptic tick. */
    data object Keypress : TypingEffect
    /** Wrong character: long-press haptic + shake. */
    data object Error : TypingEffect
    /** Whole affirmation finished: celebration + toast. */
    data object Completed : TypingEffect
}

class AffirmationViewModel(app: Application, private val pm: Boolean) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AffirmationUiState> = _state.asStateFlow()

    private val _effects = Channel<TypingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Which window this session fulfils (false = 7 AM, true = 7 PM). */
    val eveningWindow: Boolean get() = pm

    // ── Audio owned by the VM: created off the main thread, released with
    //    the VM instead of leaking across config changes. ──
    private var correctPlayer: MediaPlayer? = null
    private var incorrectPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val c = runCatching {
                MediaPlayer.create(getApplication<Application>(), R.raw.correct)
            }.getOrNull()
            val i = runCatching {
                MediaPlayer.create(getApplication<Application>(), R.raw.incorrect)
            }.getOrNull()
            correctPlayer = c
            incorrectPlayer = i
        }
    }

    private fun loadInitialState(): AffirmationUiState {
        val picks = AffirmationStore.windowPicks(getApplication(), pm)
        val joined = picks.joinToString("\n")
        return AffirmationUiState(
            affirmations = picks,
            segments = AffirmationStore.wrappedSegments(joined),
            session = Core.newSession(AffirmationStore.wrappedSegments(joined))
        )
    }

    /** Single entry point for typing — mirrors the prebuilt challenge reducer. */
    fun onInputChanged(newValue: String) {
        val old = _state.value
        if (old.finished || old.celebrating || newValue == old.inputText) return

        if (newValue.length > old.inputText.length) {
            var s = old.session
            var c = old.correctChars
            var w = old.wrongKeys
            for (i in old.inputText.length until newValue.length) {
                val ch = newValue[i]
                if (ch == ' ' || ch == '\n') {
                    val before = s
                    s = Core.typeSpace(s)
                    if (s.wordIndex > before.wordIndex) play(correctPlayer)
                } else {
                    val before = s
                    s = Core.typeChar(s, ch)
                    if (s.wrong && !before.wrong) {
                        w++
                        play(incorrectPlayer)
                        emit(TypingEffect.Error)
                    } else if (!s.wrong && s.typedInWord > before.typedInWord) {
                        c++
                        emit(TypingEffect.Keypress)
                    }
                }
            }
            _state.update {
                it.copy(
                    session = s,
                    inputText = newValue,
                    correctChars = c,
                    wrongKeys = w,
                    mood = if (s.wrong) MascotMood.Error else MascotMood.Typing
                )
            }
        } else {
            var s = old.session
            repeat(old.inputText.length - newValue.length) { s = Core.backspace(s) }
            _state.update {
                it.copy(
                    session = s,
                    inputText = newValue,
                    mood = if (!s.wrong) MascotMood.Typing else MascotMood.Error
                )
            }
        }

        if (Core.isComplete(_state.value.session)) {
            complete()
        } else {
            // Auto-finish: the engine completes on the space AFTER the last
            // word (prebuilt IME semantics). On this canvas the last word is
            // the end of the text — advance the final space for the user so
            // finishing the last word actually completes the session.
            val s = _state.value.session
            val last = s.words.lastOrNull()
            if (last != null && !s.wrong &&
                s.wordIndex == s.words.size - 1 && s.typedInWord == last.length
            ) {
                val advanced = Core.typeSpace(s)
                if (Core.isComplete(advanced)) {
                    _state.update { it.copy(session = advanced) }
                    complete()
                }
            }
        }
    }

    /** Enforcement handoff: session done → a11y OFF right away, next alarm armed. */
    private fun complete() {
        val app = getApplication<Application>()
        AffirmationStore.markDone(app, pm)
        // Each session closes its own window: accessibility off immediately.
        // The next alarm (7 PM or tomorrow 7 AM) turns it back on.
        SecureSettings.setServiceEnabled(app, enable = false)
        AffirmationScheduler.schedule(app)
        _state.update { it.copy(mood = MascotMood.Success, celebrating = true) }
        emit(TypingEffect.Completed)
    }

    fun onCelebrationFinished() {
        _state.update { it.copy(celebrating = false, finished = true) }
    }

    private fun emit(effect: TypingEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun play(player: MediaPlayer?) {
        player ?: return
        try {
            if (player.isPlaying) { player.pause(); player.seekTo(0) }
            player.start()
        } catch (_: Exception) { /* audio safety */ }
    }

    override fun onCleared() {
        correctPlayer?.release()
        incorrectPlayer?.release()
        correctPlayer = null
        incorrectPlayer = null
        super.onCleared()
    }

    companion object {
        /** [pm] selects the window: false = 7 AM, true = 7 PM. */
        fun factory(pm: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AffirmationViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    pm
                )
            }
        }
    }
}
