package com.example.goenforcer

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Affirmation Enforcer screen ────────────────────────────────────────
// Thin MVVM view: renders AffirmationViewModel.state, forwards keystrokes
// to the VM, and plays one-shot TypingEffects (haptics/shake/toast).
// The word-chip canvas is a direct copy of the prebuilt GoEnforcer
// ChallengeScreen canvas (Ui.kt): same Core engine, same chip states,
// same hidden BasicTextField input.

private val AFF_BASE = 18.sp
private val AFF_BIG = 22.sp
private val AFF_LINE = 30.sp

private sealed class AffPiece {
    class Word(val text: String, val wordIdx: Int) : AffPiece()
    class Space(val text: String) : AffPiece()
}

private fun affBuildLines(segments: List<Segment>): List<List<AffPiece>> {
    val lines = mutableListOf<List<AffPiece>>()
    var cur = mutableListOf<AffPiece>()
    var wordIdx = 0

    fun flush() {
        if (cur.isEmpty()) cur.add(AffPiece.Space(" "))
        lines.add(cur)
        cur = mutableListOf()
    }

    for (seg in segments) {
        val text = seg.text
        var j = 0
        while (j < text.length) {
            val c = text[j]
            if (c == '\n') { flush(); j++; continue }
            if (c.isWhitespace()) {
                val s = j
                while (j < text.length && text[j].isWhitespace() && text[j] != '\n') j++
                cur.add(AffPiece.Space(text.substring(s, j)))
            } else {
                val s = j
                while (j < text.length && !text[j].isWhitespace()) j++
                cur.add(AffPiece.Word(text.substring(s, j), wordIdx++))
            }
        }
    }
    if (cur.isNotEmpty()) lines.add(cur)
    return lines
}

@Composable
private fun AffWordChip(
    word: String,
    wordIdx: Int,
    session: Session,
    fillAmount: Float,
    caretAlpha: Float
) {
    val status = when {
        wordIdx < session.wordIndex -> 0   // done
        wordIdx == session.wordIndex -> 1  // current
        else -> 2                          // future
    }

    when (status) {
        0 -> Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(GoColors.ChipDone)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = AFF_BASE,
                lineHeight = AFF_LINE,
                fontWeight = FontWeight.SemiBold,
                color = GoColors.ChipDoneText
            )
        }

        1 -> {
            val typed = session.typedInWord.coerceAtMost(word.length)
            val untyped = word.length - typed
            val fillBg = androidx.compose.ui.graphics.lerp(
                GoColors.ChipCurrent, GoColors.ChipDone, fillAmount
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.shadow(if (fillAmount < 1f) 4.dp else 0.dp, RoundedCornerShape(8.dp))
            ) {
                if (typed > 0) {
                    Text(
                        text = word.substring(0, typed),
                        fontFamily = CodeFont,
                        fontSize = AFF_BIG,
                        lineHeight = AFF_LINE,
                        fontWeight = FontWeight.Bold,
                        color = GoColors.ChipDoneText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(fillBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "\u2502",
                    fontFamily = CodeFont,
                    fontSize = AFF_BIG,
                    lineHeight = AFF_LINE,
                    fontWeight = FontWeight.Bold,
                    color = GoColors.TextPrimary.copy(alpha = caretAlpha)
                )
                if (untyped > 0) {
                    Text(
                        text = word.substring(typed),
                        fontFamily = CodeFont,
                        fontSize = AFF_BIG,
                        lineHeight = AFF_LINE,
                        fontWeight = FontWeight.Bold,
                        color = if (session.wrong) GoColors.Error else GoColors.ChipCurrentText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (session.wrong) GoColors.ErrorBg else GoColors.ChipCurrent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        else -> Box(
            modifier = Modifier
                .border(1.dp, GoColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = AFF_BASE,
                lineHeight = AFF_LINE,
                color = GoColors.TextMuted
            )
        }
    }
}

@Composable
private fun AffCodeDisplay(
    segments: List<Segment>,
    session: Session,
    fillAmount: Float,
    caretAlpha: Float
) {
    val lines = remember(segments) { affBuildLines(segments) }
    Column {
        for (line in lines) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                for (piece in line) {
                    when (piece) {
                        is AffPiece.Space -> Text(
                            text = piece.text,
                            fontFamily = CodeFont,
                            fontSize = AFF_BASE,
                            lineHeight = AFF_LINE
                        )
                        is AffPiece.Word -> AffWordChip(
                            word = piece.text,
                            wordIdx = piece.wordIdx,
                            session = session,
                            fillAmount = fillAmount,
                            caretAlpha = caretAlpha
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AffirmationScreen(
    pm: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vm: AffirmationViewModel = viewModel(factory = AffirmationViewModel.Factory(pm))
    val state by vm.state.collectAsStateWithLifecycle()

    // ── One-shot effects: haptics, shake, toast (audio lives in the VM) ──
    val shake = remember { Animatable(0f) }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                TypingEffect.Keypress ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                TypingEffect.Error -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    for (dx in listOf(-10f, 10f, -7f, 7f, -4f, 4f, 0f)) {
                        shake.animateTo(dx, tween(40, easing = LinearEasing))
                    }
                    shake.animateTo(0f)
                }
                TypingEffect.Completed -> {
                    val stillPending = AffirmationStore.activeWindow(context) != null
                    Toast.makeText(
                        context,
                        if (stillPending) "Session done \u2014 next one enforced at ${if (AffirmationStore.activeWindow(context) == true) "7:00 PM" else "7:00 AM"}"
                        else "Affirmations written \u2014 apps unlocked",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Chip fill follows the VM's typedInWord.
    val currentWord = state.session.words.getOrNull(state.session.wordIndex)
    val targetFill = if (currentWord != null && currentWord.isNotEmpty())
        state.session.typedInWord.toFloat() / currentWord.length else 0f
    val fillAmount by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(150),
        label = "affWordFill"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "affCaret")
    val caretAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "affCaretAlpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(GoColors.Background).imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Header with Mascot ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onBack?.invoke() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GoColors.TextMuted
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pm) "Evening Affirmation" else "Morning Affirmation",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoColors.TextPrimary
                    )
                    Text(
                        text = "Rewrite all ${AffirmationStore.PICK_COUNT} affirmations to unlock your apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint
                    )
                }
                GopherMascot(mood = state.mood)
            }

            Spacer(Modifier.height(12.dp))

            // ── Containment card ──
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GoColors.Card),
                border = BorderStroke(1.dp, GoColors.Border)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GoColors.Primary,
                        trackColor = GoColors.Surface
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.wordsToGo == 0) "Completed!" else "${state.wordsToGo} words remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.wordsToGo == 0) GoColors.Success else GoColors.AccentBlue
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Accuracy: ${state.accuracyPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoColors.TextMuted
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Scrollable affirmation canvas (copied layout) ──
                    val vScrollState = rememberScrollState()
                    val hScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .drawVerticalScrollbar(vScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(hScrollState)
                                .verticalScroll(vScrollState)
                        ) {
                            AffCodeDisplay(
                                segments = state.segments,
                                session = state.session,
                                fillAmount = fillAmount,
                                caretAlpha = caretAlpha
                            )

                            BasicTextField(
                                value = state.inputText,
                                onValueChange = vm::onInputChanged,
                                textStyle = TextStyle(
                                    color = Color.Transparent,
                                    fontFamily = CodeFont,
                                    fontSize = AFF_BASE,
                                    lineHeight = AFF_LINE,
                                    letterSpacing = 0.sp
                                ),
                                cursorBrush = SolidColor(Color.Transparent),
                                modifier = Modifier.fillMaxSize().width(2000.dp),
                                singleLine = false,
                                readOnly = state.finished || state.celebrating
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.finished) {
                TactileButton(
                    text = "Return to Dashboard",
                    onClick = { onBack?.invoke() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }
        }

        if (state.celebrating) {
            CelebrationOverlay(onFinished = vm::onCelebrationFinished)
        }
    }
}
