package com.example.goenforcer

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

// ─── Reading modes ──────────────────────────────────────────────────────
// One interchangeable interface, five implementations. The gate chunk is
// never SLIDE (weakest proof) and never FLASH (no active verification) —
// those are optional/flow modes. Quiet mode drops ALOUD.
enum class ReadingMode(
    val label: String,
    val requiresMic: Boolean = false,
    val eligibleForGate: Boolean = false
) {
    RETYPE("Retype", eligibleForGate = true),
    ALOUD("Read aloud", requiresMic = true, eligibleForGate = true),
    SLIDE("Slide"),
    DRAG("Drag & drop", eligibleForGate = true),
    FLASH("Flash");

    /** Modes quiet mode (meetings/train/night) may still use. */
    val quietSafe: Boolean get() = this == RETYPE || this == SLIDE || this == DRAG || this == FLASH

    companion object {
        /** Gate picks randomly between read-aloud and retype (spec default). */
        fun randomGate(quiet: Boolean): ReadingMode {
            val pool = if (quiet) listOf(RETYPE) else listOf(RETYPE, ALOUD)
            return pool.random()
        }
    }
}

sealed interface ReadingEffect {
    data object WordCorrect : ReadingEffect
    data object WordWrong : ReadingEffect
    data object ChunkPassed : ReadingEffect
    data class Error(val message: String) : ReadingEffect
}

// ─── UI state ──────────────────────────────────────────────────────────
data class ReadingUiState(
    val documentId: String? = null,
    val documentTitle: String = "",
    val chunk: ReadingChunk? = null,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val mode: ReadingMode = ReadingMode.RETYPE,
    val wordStates: List<WordState> = emptyList(),
    val cursor: Int = 0,
    val errors: Int = 0,
    val matched: Int = 0,
    val passed: Boolean = false,
    val showingContext: Boolean = true,
    val retypeTyped: String = "",
    val retypeWrong: Boolean = false,
    // Slide
    val slideVariant: ReadingStore.SlideVariant = ReadingStore.SlideVariant.OUT,
    val lastSlideAtMs: Long = 0L,
    // Drag
    val tray: List<String> = emptyList(),
    // Flash
    val flashIndex: Int = 0,
    val flashPlaying: Boolean = false,
    val wpm: Int = Rsvp.DEFAULT_WPM,
    // Read aloud
    val listening: Boolean = false,
    val lastPartial: String = "",
    val documentFinished: Boolean = false
) {
    val wordsTotal: Int get() = chunk?.words?.size ?: 0
    val wordsToGo: Int get() = (wordsTotal - cursor).coerceAtLeast(0)
    val progress: Float get() = if (wordsTotal == 0) 0f else cursor.toFloat() / wordsTotal
    val matchedPct: Int get() = if (wordsTotal == 0) 0 else (matched * 100 / wordsTotal)
}

// ─── ViewModel ─────────────────────────────────────────────────────────
class ReadingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    private val _effects = Channel<ReadingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var chunks: List<ReadingChunk> = emptyList()
    private var docId: String? = null
    private var aligner: AloudAligner? = null

    // Flash playback
    private val flashHandler = Handler(Looper.getMainLooper())
    private var flashRunnable: Runnable? = null

    // Read aloud
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Library ──
    fun documents(): List<ReadingDocument> = ReadingStore.documents(getApplication())

    fun addDocument(title: String, text: String): String {
        val id = ReadingStore.addDocument(getApplication(), title, text)
        return id
    }

    fun removeDocument(id: String) = ReadingStore.removeDocument(getApplication(), id)

    /** Open a document at its frontier (or re-read from 0 if requested). */
    fun openDocument(id: String, fromStart: Boolean = false) {
        val doc = ReadingStore.document(getApplication(), id) ?: return
        chunks = ReadingChunker.build(doc.text)
        docId = id
        val resume = if (fromStart) -1 else ReadingStore.lastPassedSeq(getApplication(), id)
        val next = (resume + 1).coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                documentId = id,
                documentTitle = doc.title,
                totalChunks = chunks.size,
                documentFinished = chunks.isNotEmpty() && resume >= chunks.size - 1
            )
        }
        if (chunks.isEmpty()) {
            _state.update { it.copy(documentFinished = true) }
            emit(ReadingEffect.Error("That text has no readable words."))
        } else {
            startChunk(next)
        }
    }

    // ── Mode selection ──
    fun setMode(mode: ReadingMode) {
        if (_state.value.mode == mode) return
        stopFlash(); stopListening()
        ReadingStore.setLastMode(getApplication(), mode.name)
        _state.update { it.copy(mode = mode) }
        // Restart the current chunk under the new mode.
        startChunk(_state.value.chunkIndex)
    }

    fun setSlideVariant(v: ReadingStore.SlideVariant) {
        ReadingStore.setSlideVariant(getApplication(), v)
        _state.update { it.copy(slideVariant = v) }
    }

    fun setWpm(wpm: Int) {
        ReadingStore.setWpm(getApplication(), wpm)
        _state.update { it.copy(wpm = wpm) }
    }

    private fun startChunk(index: Int) {
        val chunk = chunks.getOrNull(index) ?: run {
            _state.update { it.copy(documentFinished = true) }
            return
        }
        missedSoFar.clear()
        val mode = _state.value.mode
        val wordStates = List(chunk.words.size) { WordState.UPCOMING }
            .toMutableList().apply { if (isNotEmpty()) this[0] = WordState.CURRENT }
        aligner = if (mode == ReadingMode.ALOUD) AloudAligner(chunk.words) else null

        _state.update {
            it.copy(
                chunk = chunk,
                chunkIndex = index,
                wordStates = wordStates,
                cursor = 0,
                errors = 0,
                matched = 0,
                passed = false,
                retypeTyped = "",
                retypeWrong = false,
                tray = if (mode == ReadingMode.DRAG) DragDeck.deal(chunk.words, 0, chunk.seq) else emptyList(),
                flashIndex = 0,
                flashPlaying = false,
                documentFinished = false
            )
        }
        when (mode) {
            ReadingMode.ALOUD -> startListening()
            ReadingMode.FLASH -> { /* waits for user to press play */ }
            else -> {}
        }
    }

    private fun setWordStates(cursor: Int, missed: Set<Int>) {
        _state.update { s ->
            val list = s.chunk?.words?.mapIndexed { i, _ ->
                when {
                    i in missed -> WordState.MISSED
                    i < cursor -> WordState.DONE
                    i == cursor -> WordState.CURRENT
                    else -> WordState.UPCOMING
                }
            } ?: emptyList()
            s.copy(wordStates = list, cursor = cursor)
        }
    }

    private val missedSoFar = mutableSetOf<Int>()

    /** Advance the shared cursor, refresh word states, detect the pass. */
    private fun advanceTo(newCursor: Int, newlyMissed: List<Int> = emptyList()) {
        missedSoFar.addAll(newlyMissed)
        val total = _state.value.wordsTotal
        setWordStates(newCursor.coerceAtMost(total), missedSoFar)
        val matchedNow = (newCursor - missedSoFar.size).coerceAtLeast(0)
        _state.update { it.copy(matched = matchedNow) }
        if (newCursor >= total) onChunkResolved()
    }

    private fun onChunkResolved() {
        stopListening(); stopFlash()
        val s = _state.value
        val mode = s.mode
        val pass = when (mode) {
            ReadingMode.ALOUD -> ReadingPassRule.aloudPass(s.matched, s.wordsTotal)
            ReadingMode.FLASH -> true           // flash is a flow/reading aid, no gate
            else -> true                        // retype/slide/drag advance only on success
        }
        if (pass) {
            docId?.let { ReadingStore.setLastPassedSeq(getApplication(), it, s.chunkIndex) }
            // Consecutive-slide guard bookkeeping.
            if (mode == ReadingMode.SLIDE) ReadingStore.setSlideStreak(getApplication(), ReadingStore.slideStreak(getApplication()) + 1)
            else ReadingStore.setSlideStreak(getApplication(), 0)
            val isLast = s.chunkIndex >= s.totalChunks - 1
            _state.update { it.copy(passed = true, documentFinished = isLast) }
            emit(ReadingEffect.ChunkPassed)
        } else {
            _state.update { it.copy(passed = false) }
            emit(ReadingEffect.WordWrong)
        }
    }

    fun advanceChunk() {
        val next = _state.value.chunkIndex + 1
        if (next >= _state.value.totalChunks) {
            _state.update { it.copy(documentFinished = true) }
            return
        }
        missedSoFar.clear()
        startChunk(next)
    }

    fun retryChunk() {
        missedSoFar.clear()
        startChunk(_state.value.chunkIndex)
    }

    /** Go back to the previous chunk (review/redo — progress can only advance). */
    fun previousChunk() {
        val prev = _state.value.chunkIndex - 1
        if (prev < 0) return
        missedSoFar.clear()
        startChunk(prev)
    }

    /** Flash navigation: step one word back/forward (pauses playback). */
    fun stepWord(delta: Int) {
        if (_state.value.mode != ReadingMode.FLASH) return
        stopFlash()
        val chunk = _state.value.chunk ?: return
        if (chunk.words.isEmpty()) return
        val idx = (_state.value.flashIndex + delta).coerceIn(0, chunk.words.size - 1)
        setWordStates(idx, missedSoFar)
        _state.update { it.copy(flashIndex = idx) }
    }

    /** Restart the mic listener (e.g. after RECORD_AUDIO is granted). */
    fun restartAloud() {
        if (_state.value.mode != ReadingMode.ALOUD) return
        stopListening()
        startListening()
    }

    // ── Retype ──
    fun onRetypeChar(ch: Char) {
        if (_state.value.mode != ReadingMode.RETYPE || _state.value.passed) return
        val chunk = _state.value.chunk ?: return
        var st = _state.value
        val before = st.cursor
        // Fold the char through RetypeLogic against the current word.
        val cur = chunk.words[st.cursor]
        val nextTyped = st.retypeTyped + ch
        val target = ReadingText.normalize(cur)
        val normNext = ReadingText.normalize(nextTyped)
        if (target.startsWith(normNext)) {
            _state.update { it.copy(retypeTyped = nextTyped, retypeWrong = false) }
            if (normNext == target) {
                // Exact match → advance.
                _state.update { it.copy(matched = it.matched + 1) }
                emit(ReadingEffect.WordCorrect)
                advanceTo(before + 1)
            }
        } else {
            _state.update { it.copy(retypeTyped = nextTyped, retypeWrong = true, errors = it.errors + 1) }
            emit(ReadingEffect.WordWrong)
        }
    }

    fun onRetypeSpace() {
        if (_state.value.mode != ReadingMode.RETYPE || _state.value.passed) return
        val chunk = _state.value.chunk ?: return
        val st = _state.value
        if (st.cursor >= chunk.words.size) return
        val target = ReadingText.normalize(chunk.words[st.cursor])
        val typed = ReadingText.normalize(st.retypeTyped)
        if (typed == target) {
            _state.update { it.copy(retypeTyped = "", retypeWrong = false, matched = it.matched + 1) }
            emit(ReadingEffect.WordCorrect)
            advanceTo(st.cursor + 1)
        } else {
            _state.update { it.copy(retypeWrong = true, errors = it.errors + 1) }
            emit(ReadingEffect.WordWrong)
        }
    }

    fun onRetypeBackspace() {
        if (_state.value.mode != ReadingMode.RETYPE || _state.value.passed) return
        _state.update { it.copy(retypeTyped = it.retypeTyped.dropLast(1), retypeWrong = false) }
    }

    // ── Slide ──
    fun onSlideGesture(nowMs: Long) {
        if (_state.value.mode != ReadingMode.SLIDE || _state.value.passed) return
        val st = _state.value
        if (!SlideLogic.paceOk(st.lastSlideAtMs, nowMs)) {
            emit(ReadingEffect.WordWrong)   // too fast — rejected
            return
        }
        _state.update { it.copy(lastSlideAtMs = nowMs) }
        emit(ReadingEffect.WordCorrect)
        advanceTo(st.cursor + 1)
    }

    // ── Drag & drop ──
    fun onTileDropped(tile: String) {
        if (_state.value.mode != ReadingMode.DRAG || _state.value.passed) return
        val chunk = _state.value.chunk ?: return
        val st = _state.value
        if (st.cursor >= chunk.words.size) return
        val target = chunk.words[st.cursor]
        if (DragDeck.isCorrect(tile, target)) {
            _state.update { it.copy(matched = it.matched + 1) }
            emit(ReadingEffect.WordCorrect)
            val next = st.cursor + 1
            advanceTo(next)
            // Re-deal the tray for the next gap.
            val nextTarget = chunks.getOrNull(st.chunkIndex)?.words?.getOrNull(next)
            if (nextTarget != null) {
                _state.update { it.copy(tray = DragDeck.deal(chunk.words, next, chunk.seq)) }
            }
        } else {
            _state.update { it.copy(errors = it.errors + 1) }
            emit(ReadingEffect.WordWrong)
        }
    }

    // ── Flash (SwiftRead-style RSVP) ──
    fun toggleFlash() {
        if (_state.value.mode != ReadingMode.FLASH) return
        if (_state.value.flashPlaying) stopFlash() else startFlash()
    }

    private fun startFlash() {
        val chunk = _state.value.chunk ?: return
        _state.update { it.copy(flashPlaying = true) }
        // Resume from the current index (startChunk resets it to 0 on a new chunk).
        if (_state.value.flashIndex >= chunk.words.size) {
            _state.update { it.copy(flashIndex = 0) }
        }
        scheduleFlashTick()
    }

    private fun scheduleFlashTick() {
        val chunk = _state.value.chunk ?: return
        val idx = _state.value.flashIndex
        if (idx >= chunk.words.size) {
            _state.update { it.copy(flashPlaying = false) }
            advanceTo(chunk.words.size)
            return
        }
        val delay = Rsvp.delayMs(chunk.words[idx], _state.value.wpm)
        flashRunnable = Runnable {
            if (!_state.value.flashPlaying) return@Runnable
            // Mark this word done and move on.
            setWordStates(idx + 1, missedSoFar)
            _state.update { it.copy(flashIndex = idx + 1) }
            scheduleFlashTick()
        }
        mainHandler.postDelayed(flashRunnable!!, delay)
    }

    private fun stopFlash() {
        flashRunnable?.let { mainHandler.removeCallbacks(it) }
        flashRunnable = null
        _state.update { it.copy(flashPlaying = false) }
    }

    // ── Read aloud ──
    private fun startListening() {
        val ctx: Context = getApplication()
        // Never stack recognizers — destroy any previous instance first.
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            emit(ReadingEffect.Error("Speech recognition unavailable on this device."))
            _state.update { it.copy(mode = ReadingMode.RETYPE, listening = false) }
            return
        }
        runCatching {
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = r
            r.setRecognitionListener(loudListener)
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _state.update { it.copy(listening = true) }
            r.startListening(intent)
        }.onFailure {
            _state.update { it.copy(listening = false) }
            emit(ReadingEffect.Error("Could not start microphone."))
        }
    }

    private val loudListener = object : RecognitionListener {
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            val text = list.firstOrNull() ?: return
            _state.update { it.copy(lastPartial = text) }
            val a = aligner ?: return
            val update = a.onPartial(text.split(Regex("\\s+")))
            if (update.advancedBy > 0 || update.newlyMissed.isNotEmpty()) {
                missedSoFar.addAll(update.newlyMissed)
                _state.update { it.copy(matched = a.matched) }
                setWordStates(a.cursor, missedSoFar)
                if (update.completed) onChunkResolved()
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            restartListeningSoon()
        }
        override fun onError(error: Int) {
            restartListeningSoon()
        }
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    private fun restartListeningSoon() {
        if (_state.value.passed || _state.value.mode != ReadingMode.ALOUD) return
        mainHandler.postDelayed({
            if (_state.value.mode == ReadingMode.ALOUD && !_state.value.passed) startListening()
        }, 400)
    }

    private fun stopListening() {
        runCatching { recognizer?.cancel() }   // ignore — recognizer already gone
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.update { it.copy(listening = false) }
    }

    private fun emit(effect: ReadingEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        stopFlash()
        stopListening()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReadingViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}
