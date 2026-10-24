package com.example.goenforcer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

// ─── Reading Enforcement screen ────────────────────────────────────────
// MVVM view: renders ReadingViewModel.state, forwards input, plays one-shot
// ReadingEffects. Two sub-screens: the library (documents + settings) and the
// reader session (5 interchangeable modes over one 30-word chunk).

@Composable
fun ReadingScreen(onBack: () -> Unit) {
    val vm: ReadingViewModel = viewModel(factory = ReadingViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.effects.collect { e ->
            when (e) {
                ReadingEffect.WordCorrect -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                ReadingEffect.WordWrong -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                ReadingEffect.ChunkPassed -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Chunk passed", Toast.LENGTH_SHORT).show()
                }
                is ReadingEffect.Error -> Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoColors.Background)
            // Safe area: status bar / nav bar / cutout / keyboard.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (sessionId == null) {
            ReadingLibraryScreen(
                vm = vm,
                onBack = onBack,
                onOpen = { id -> vm.openDocument(id); sessionId = id }
            )
        } else {
            ReadingReaderScreen(
                vm = vm,
                state = state,
                onClose = { sessionId = null }
            )
        }
    }
}

// ─── Library ───────────────────────────────────────────────────────────
@Composable
private fun ReadingLibraryScreen(
    vm: ReadingViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val docs = remember { mutableStateOf(vm.documents()) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ReadingHeader(
            title = "Reading Enforcement",
            subtitle = "${docs.value.size} document${if (docs.value.size == 1) "" else "s"}",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = "Settings", tint = GoColors.TextMuted)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
        ) {
            item {
                TactileButton(
                    text = "Add text",
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (docs.value.isEmpty()) {
                item { EmptyReadingState() }
            }
            items(docs.value, key = { it.id }) { doc ->
                DocumentCard(
                    doc = doc,
                    lastPassed = ReadingStore.lastPassedSeq(LocalContext.current, doc.id),
                    onOpen = { onOpen(doc.id) },
                    onDelete = {
                        vm.removeDocument(doc.id)
                        docs.value = vm.documents()
                    }
                )
            }
        }
    }

    if (showAdd) AddTextDialog(
        onDismiss = { showAdd = false },
        onAdd = { title, text ->
            val id = vm.addDocument(title, text)
            docs.value = vm.documents()
            showAdd = false
            onOpen(id)
        }
    )

    if (showSettings) ReadingSettingsDialog(vm = vm, onDismiss = { showSettings = false })
}

@Composable
private fun EmptyReadingState() {    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Keyboard,
            contentDescription = null,
            tint = GoColors.TextFaint,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No reading yet", style = MaterialTheme.typography.titleMedium, color = GoColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste any text and read it back in 30-word chunks to build the habit.",
            style = MaterialTheme.typography.bodyMedium,
            color = GoColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BuiltInBadge() {
    Text(
        text = "BUNDLED",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = GoColors.AccentTeal,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GoColors.AccentTeal.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun DocumentCard(
    doc: ReadingDocument,
    lastPassed: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val chunks = remember(doc.text) { ReadingChunker.chunk(ReadingChunker.words(doc.text)).size }
    val read = (lastPassed + 1).coerceAtMost(chunks)
    val pct = if (chunks == 0) 0f else read.toFloat() / chunks

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GoColors.Card),
        border = BorderStroke(1.dp, GoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            doc.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = GoColors.TextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (doc.builtIn) {
                            Spacer(Modifier.width(8.dp))
                            BuiltInBadge()
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$read / $chunks chunks",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint
                    )
                }
                // Bundled cheat sheets are read-only — no delete affordance.
                if (!doc.builtIn) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GoColors.TextFaint)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = GoColors.Primary,
                trackColor = GoColors.Surface
            )
            Spacer(Modifier.height(14.dp))
            TactileButton(
                text = if (read >= chunks && chunks > 0) "Read again" else if (read > 0) "Resume" else "Start reading",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Reader session ────────────────────────────────────────────────────
@Composable
private fun ReadingReaderScreen(
    vm: ReadingViewModel,
    state: ReadingUiState,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showModes by remember { mutableStateOf(false) }

    // Mic permission for read-aloud.
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.restartAloud()
        } else {
            Toast.makeText(context, "Mic needed for read-aloud", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.mode) {
        if (state.mode.requiresMic &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (state.documentFinished) {
        ReadingCompleteScreen(title = state.documentTitle, onClose = onClose)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ReadingHeader(
            title = state.documentTitle,
            subtitle = "Chunk ${state.chunkIndex + 1} of ${state.totalChunks} · ${state.mode.label}",
            onClose = onClose,
            trailing = {
                IconButton(onClick = { showModes = true }) {
                    Icon(Icons.Filled.TouchApp, contentDescription = "Mode", tint = GoColors.TextMuted)
                }
            }
        )

        // Progress + counters.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = GoColors.Primary,
                trackColor = GoColors.Surface
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode never hides behind an icon — an explicit pill opens the picker.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GoColors.Surface)
                        .clickable { showModes = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(modeIcon(state.mode), contentDescription = null, tint = GoColors.Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GoColors.TextPrimary
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change mode", tint = GoColors.TextFaint)
                }
                Spacer(Modifier.weight(1f))
                if (state.errors > 0) {
                    Text(
                        "${state.errors} error${if (state.errors == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.Error
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    "${state.wordsToGo} words to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
            }
        }

        // Greyed context (not verified).
        if (state.showingContext && state.chunk?.contextBefore?.isNotBlank() == true) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.chunk!!.contextBefore,
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextFaint,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Mode body — one of five, interchangeable behind ReadingMode.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.mode) {
                ReadingMode.FLASH -> FlashBody(vm, state)
                ReadingMode.DRAG -> DragBody(vm, state)
                else -> ParagraphBody(vm, state)
            }
        }

        // Mode-specific footer.
        when (state.mode) {
            ReadingMode.RETYPE -> RetypeFooter(vm, state)
            ReadingMode.FLASH -> FlashFooter(vm, state)
            ReadingMode.ALOUD -> AloudFooter(state)
            else -> {}
        }

        Spacer(Modifier.height(12.dp))
        ModeActionBar(vm, state)
        Spacer(Modifier.height(24.dp))
    }

    if (showModes) ModePickerDialog(
        current = state.mode,
        quiet = ReadingStore.quietMode(context),
        onPick = { vm.setMode(it); showModes = false },
        onDismiss = { showModes = false }
    )
}

// ─── Paragraph body (retype / slide / drag / aloud) ────────────────────
@Composable
private fun ParagraphBody(vm: ReadingViewModel, state: ReadingUiState) {
    val chunk = state.chunk ?: return

    // Original Go Enforcer canvas: monospace code chips with per-word states,
    // flowing and wrapping naturally.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ReadingWordFlow(
            words = chunk.words,
            states = state.wordStates,
            modifier = Modifier.fillMaxWidth(),
            onWordTap = if (state.mode == ReadingMode.SLIDE) {
                ({ idx -> if (idx == state.cursor) vm.onSlideGesture(SystemClock.elapsedRealtime()) })
            } else null
        )
    }
}

// ─── Flash body (SwiftRead-style RSVP) ─────────────────────────────────
@Composable
private fun FlashBody(vm: ReadingViewModel, state: ReadingUiState) {
    val chunk = state.chunk ?: return
    val currentWord = chunk.words.getOrNull(state.flashIndex) ?: chunk.words.lastOrNull() ?: ""

    // ORP letter highlighted inside ONE text run — a single baseline, no skew,
    // real kerning between the letters (splitting into separate Texts broke both).
    val word = remember(currentWord) {
        val orp = Rsvp.orpIndex(currentWord)
        buildAnnotatedString {
            if (orp > 0) {
                withStyle(SpanStyle(color = GoColors.TextPrimary)) { append(currentWord.take(orp)) }
            }
            withStyle(SpanStyle(color = GoColors.Primary, fontWeight = FontWeight.ExtraBold)) {
                append(currentWord.getOrElse(orp) { ' ' })
            }
            if (orp + 1 < currentWord.length) {
                withStyle(SpanStyle(color = GoColors.TextPrimary)) { append(currentWord.substring(orp + 1)) }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = word,
            fontSize = 48.sp,
            fontFamily = CodeFont,
            textAlign = TextAlign.Center,
            lineHeight = 56.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        // WPM read-out + progress within the chunk.
        Text(
            "${state.flashIndex + 1} / ${chunk.words.size} · ${state.wpm} wpm",
            style = MaterialTheme.typography.labelMedium,
            color = GoColors.TextFaint
        )
    }
}

// ─── Footers ───────────────────────────────────────────────────────────
@Composable
private fun RetypeFooter(vm: ReadingViewModel, state: ReadingUiState) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // Open the keyboard when the chunk/mode starts, and keep it open on advance.
    LaunchedEffect(state.chunkIndex, state.mode, state.passed) {
        if (!state.passed) runCatching { focusRequester.requestFocus() }
    }

    // Hidden field: paste disabled by prefix-filtering, no suggestions/autocorrect.
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        BasicTextField(
            value = state.retypeTyped,
            onValueChange = { new ->
                val old = state.retypeTyped
                if (new.length > old.length) {
                    // Ignore multi-char inserts (paste): only accept one char at a time.
                    if (new.length - old.length == 1) vm.onRetypeChar(new.last())
                } else if (new.length < old.length) {
                    repeat(old.length - new.length) { vm.onRetypeBackspace() }
                }
            },
            textStyle = TextStyle(
                color = GoColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            cursorBrush = SolidColor(GoColors.Primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        if (state.retypeTyped.isEmpty()) {
            Text(
                "Type the highlighted word…",
                color = GoColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}

@Composable
private fun AloudFooter(state: ReadingUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.listening) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoColors.Primary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = GoColors.TextFaint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = state.lastPartial.ifBlank { "Listening… say the words in order" },
            style = MaterialTheme.typography.bodyMedium,
            color = GoColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun FlashFooter(vm: ReadingViewModel, state: ReadingUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = state.wpm.toFloat(),
                onValueChange = { vm.setWpm(it.roundToInt()) },
                valueRange = Rsvp.MIN_WPM.toFloat()..Rsvp.MAX_WPM.toFloat(),
                colors = SliderDefaults.colors(thumbColor = GoColors.Primary, activeTrackColor = GoColors.Primary),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${state.wpm}", style = MaterialTheme.typography.labelMedium, color = GoColors.TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        // Manual navigation: step back/forward through the chunk's words.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTactileButton(
                text = "‹ Word",
                onClick = { vm.stepWord(-1) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTactileButton(
                text = "Word ›",
                onClick = { vm.stepWord(1) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        TactileButton(
            text = if (state.flashPlaying) "Pause" else "Play",
            onClick = { vm.toggleFlash() },
            modifier = Modifier.fillMaxWidth(),
            topColor = if (state.flashPlaying) GoColors.Streak else GoColors.Primary,
            bottomColor = if (state.flashPlaying) Color(0xFFCC7700) else GoColors.PrimaryPress,
            edgeColor = if (state.flashPlaying) Color(0xFFCC7700) else Color(0xFF3A7D01)
        )
    }
}

// ─── Drag & drop body ("fill the gap") ─────────────────────────────────
// The current word is blanked in the flow; the tray holds the correct tile
// plus two distractors. Tiles are dragged with a floating ghost; a drop is
// accepted only when it lands over the highlighted slot. Tapping a tile also
// drops it (accessibility fallback).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DragBody(vm: ReadingViewModel, state: ReadingUiState) {
    val chunk = state.chunk ?: return

    // Slot bounds captured in root coordinates.
    var slotBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val tileOrigins = remember { mutableStateMapOf<String, Offset>() }
    var dragTile by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    /** Pointer position in root coords, tracked live during a drag. */
    var dragPointerRoot by remember { mutableStateOf<Offset?>(null) }
    var tileSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val haptic = LocalHapticFeedback.current

    // Reset drag state whenever the gap changes.
    LaunchedEffect(state.cursor) { dragTile = null; dragOffset = Offset.Zero }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                chunk.words.forEachIndexed { idx, word ->
                    val isSlot = idx == state.cursor
                    ReadingWordChip(
                        word = word,
                        state = state.wordStates.getOrElse(idx) { WordState.UPCOMING },
                        isBlank = isSlot,
                        blankActive = dragTile != null,
                        modifier = if (isSlot) {
                            Modifier.onGloballyPositioned { slotBounds = it.boundsInRoot() }
                        } else Modifier
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.tray.forEach { tile ->
                Box(
                    modifier = Modifier
                        .zIndex(if (dragTile == tile) 10f else 0f)
                        .graphicsLayer {
                            if (dragTile == tile) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                            }
                        }
                        .onGloballyPositioned {
                            tileOrigins[tile] = it.positionInRoot()
                            tileSize = it.size
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dragTile == tile) GoColors.ChipCurrent else GoColors.Card)
                        .border(
                            2.dp,
                            if (dragTile == tile) GoColors.Primary else GoColors.Border,
                            RoundedCornerShape(12.dp)
                        )
                        // Tap fallback: one quick tap drops the tile.
                        .clickable { vm.onTileDropped(tile) }
                        .pointerInput(tile) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    dragTile = tile
                                    dragOffset = Offset.Zero
                                    dragPointerRoot =
                                        (tileOrigins[tile] ?: Offset.Zero) + startOffset
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    // Drop accepts the POINTER landing anywhere over
                                    // the slot (generous), or the tile rect overlapping it.
                                    val slot = slotBounds
                                    if (slot != null) {
                                        val pointer = dragPointerRoot
                                        val origin = tileOrigins[tile]
                                        val pointerHit = pointer != null && slot.contains(pointer)
                                        val rectHit = origin != null && androidx.compose.ui.geometry.Rect(
                                            offset = origin + dragOffset,
                                            size = androidx.compose.ui.geometry.Size(
                                                tileSize.width.toFloat(),
                                                tileSize.height.toFloat()
                                            )
                                        ).overlaps(slot)
                                        if (pointerHit || rectHit) vm.onTileDropped(tile)
                                    }
                                    dragTile = null
                                    dragPointerRoot = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    dragTile = null
                                    dragPointerRoot = null
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta
                                    dragPointerRoot =
                                        (tileOrigins[tile] ?: Offset.Zero) + change.position
                                }
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tile,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = GoColors.TextPrimary
                    )
                }
            }
        }
    }
}

// ─── Action bar ────────────────────────────────────────────────────────
@Composable
private fun ModeActionBar(vm: ReadingViewModel, state: ReadingUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Review a previous chunk (progress never regresses).
        if (state.chunkIndex > 0) {
            OutlinedTactileButton(
                text = "‹ Prev",
                onClick = { vm.previousChunk() },
                modifier = Modifier.weight(1f)
            )
        }
        when {
            state.passed -> TactileButton(
                text = "Next chunk",
                onClick = { vm.advanceChunk() },
                modifier = Modifier.weight(1f)
            )
            state.mode == ReadingMode.ALOUD && state.cursor >= state.wordsTotal ->
                TactileButton(
                    text = "Retry chunk",
                    onClick = { vm.retryChunk() },
                    modifier = Modifier.weight(1f),
                    topColor = GoColors.Streak,
                    bottomColor = Color(0xFFCC7700),
                    edgeColor = Color(0xFFCC7700)
                )
            else -> OutlinedTactileButton(
                text = "Skip chunk",
                onClick = { vm.advanceChunk() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OutlinedTactileButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GoColors.Surface)
            .border(1.dp, GoColors.Border, RoundedCornerShape(16.dp))
            .pointerInput(text) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = GoColors.TextMuted,
            letterSpacing = 1.2.sp
        )
    }
}

// ─── Completion ────────────────────────────────────────────────────────
@Composable
private fun ReadingCompleteScreen(title: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GoColors.Success, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Document finished", style = MaterialTheme.typography.titleLarge, color = GoColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = GoColors.TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        TactileButton(text = "Back to library", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

// ─── Shared header ─────────────────────────────────────────────────────
@Composable
private fun ReadingHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onBack?.invoke() ?: onClose?.invoke() }) {
            Icon(
                imageVector = if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                contentDescription = "Back",
                tint = GoColors.TextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GoColors.TextPrimary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint, maxLines = 1)
        }
        trailing()
    }
}

// ─── Mode picker ───────────────────────────────────────────────────────
fun modeIcon(mode: ReadingMode) = when (mode) {
    ReadingMode.RETYPE -> Icons.Filled.Keyboard
    ReadingMode.ALOUD -> Icons.Filled.Mic
    ReadingMode.SLIDE -> Icons.Filled.Swipe
    ReadingMode.DRAG -> Icons.Filled.TouchApp
    ReadingMode.FLASH -> Icons.Filled.FlashOn
}

@Composable
private fun ModePickerDialog(
    current: ReadingMode,
    quiet: Boolean,
    onPick: (ReadingMode) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoColors.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Choose mode", style = MaterialTheme.typography.titleMedium, color = GoColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (quiet) "Quiet mode: read-aloud is hidden" else "Each mode verifies the chunk differently",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
                Spacer(Modifier.height(16.dp))
                ReadingMode.entries
                    .filter { !quiet || it.quietSafe }
                    .forEach { mode ->
                        ModeRow(
                            mode = mode,
                            selected = mode == current,
                            onClick = { onPick(mode) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can switch any time — progress follows the chunk, not the mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
            }
        }
    }
}

@Composable
private fun ModeRow(mode: ReadingMode, selected: Boolean, onClick: () -> Unit) {
    val icon = modeIcon(mode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GoColors.ChipCurrent else GoColors.Surface)
            .pointerInput(mode) { detectTapGestures { onClick() } }
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) GoColors.Primary else GoColors.TextMuted)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = GoColors.TextPrimary)
            Text(
                modeDescription(mode),
                style = MaterialTheme.typography.labelSmall,
                color = GoColors.TextFaint
            )
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GoColors.Primary)
    }
}

private fun modeDescription(mode: ReadingMode): String = when (mode) {
    ReadingMode.RETYPE -> "Type each word · 100% pass"
    ReadingMode.ALOUD -> "Say each word · ≥80% pass"
    ReadingMode.SLIDE -> "Swipe each word away · one gesture per word"
    ReadingMode.DRAG -> "Drop the right word into the gap"
    ReadingMode.FLASH -> "Words flash at your reading speed (RSVP)"
}

// ─── Settings ──────────────────────────────────────────────────────────
@Composable
private fun ReadingSettingsDialog(vm: ReadingViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var variant by remember { mutableStateOf(ReadingStore.slideVariant(context)) }
    var quiet by remember { mutableStateOf(ReadingStore.quietMode(context)) }
    var wpm by remember { mutableIntStateOf(ReadingStore.wpm(context)) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoColors.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Reading settings", style = MaterialTheme.typography.titleMedium, color = GoColors.TextPrimary)
                Spacer(Modifier.height(18.dp))

                Text("Slide variant", style = MaterialTheme.typography.labelMedium, color = GoColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VariantChip("Slide out", variant == ReadingStore.SlideVariant.OUT, Modifier.weight(1f)) {
                        variant = ReadingStore.SlideVariant.OUT; vm.setSlideVariant(variant)
                    }
                    VariantChip("Strikethrough", variant == ReadingStore.SlideVariant.STRIKE, Modifier.weight(1f)) {
                        variant = ReadingStore.SlideVariant.STRIKE; vm.setSlideVariant(variant)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quiet mode", style = MaterialTheme.typography.labelMedium, color = GoColors.TextPrimary)
                        Text("Hide read-aloud (meetings, train, night)", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)
                    }
                    Switch(
                        checked = quiet,
                        onCheckedChange = { quiet = it; ReadingStore.setQuietMode(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = GoColors.Primary)
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text("Flash speed · ${wpm} wpm", style = MaterialTheme.typography.labelMedium, color = GoColors.TextPrimary)
                Slider(
                    value = wpm.toFloat(),
                    onValueChange = { wpm = it.roundToInt(); vm.setWpm(wpm) },
                    valueRange = Rsvp.MIN_WPM.toFloat()..Rsvp.MAX_WPM.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = GoColors.Primary, activeTrackColor = GoColors.Primary)
                )

                Spacer(Modifier.height(8.dp))
                TactileButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VariantChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GoColors.ChipCurrent else GoColors.Surface)
            .border(2.dp, if (selected) GoColors.Primary else GoColors.Border, RoundedCornerShape(12.dp))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) GoColors.ChipCurrentText else GoColors.TextMuted
        )
    }
}

// ─── Add text ──────────────────────────────────────────────────────────
@Composable
private fun AddTextDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GoColors.Card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add text", style = MaterialTheme.typography.titleMedium, color = GoColors.TextPrimary)
                Spacer(Modifier.height(14.dp))

                DialogField(title, { title = it }, "Title")
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    DialogField(body, { body = it }, "Paste or type your text…")
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTactileButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TactileButton(
                        text = "Add",
                        onClick = { if (body.isNotBlank()) onAdd(title, body) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, hint: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoColors.Surface)
            .border(1.dp, GoColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (value.isEmpty()) {
            Text(hint, color = GoColors.TextFaint, style = MaterialTheme.typography.bodyMedium)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = GoColors.TextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(GoColors.Primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
