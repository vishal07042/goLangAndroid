package com.example.goenforcer

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val CODE_BASE = 18.sp
private val CODE_BIG = 22.sp
private val CODE_LINE = 30.sp

private val GO_KEYWORDS = setOf(
    "package", "import", "func", "return", "for", "range", "if", "else",
    "var", "const", "type", "struct", "interface", "map", "chan", "go",
    "defer", "select", "switch", "case", "break", "continue", "default",
    "fallthrough", "goto"
)
private val GO_TYPES = setOf(
    "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16",
    "uint32", "uint64", "float32", "float64", "bool", "byte", "rune",
    "string", "error", "nil", "true", "false"
)

private fun wordColor(word: String): Color = when {
    GO_KEYWORDS.contains(word) -> GoColors.Keyword
    GO_TYPES.contains(word) -> GoColors.Type
    word.startsWith("\"") && word.endsWith("\"") -> Color(0xFFE67E22) // String orange
    word.all { it.isDigit() } -> Color(0xFFD4AC0D) // Literal gold
    else -> GoColors.PlainText
}

private sealed class Piece {
    class Word(val text: String, val wordIdx: Int) : Piece()
    class Space(val text: String) : Piece()
    class Comment(val text: String) : Piece()
    class Boilerplate(val text: String) : Piece()
}

private typealias CodeLine = List<Piece>

private fun buildLines(segments: List<Segment>): List<CodeLine> {
    val lines = mutableListOf<CodeLine>()
    var cur = mutableListOf<Piece>()
    var wordIdx = 0

    fun flush() {
        if (cur.isEmpty()) cur.add(Piece.Space(" "))
        lines.add(cur)
        cur = mutableListOf()
    }

    for (seg in segments) {
        if (seg.comment) {
            val parts = seg.text.split('\n')
            parts.forEachIndexed { pi, part ->
                if (part.isNotEmpty()) cur.add(Piece.Comment(part))
                val endsWithNl = seg.text.endsWith('\n')
                val isLast = pi == parts.size - 1
                if (isLast && !endsWithNl) return@forEachIndexed
                flush()
            }
        } else if (seg.boilerplate) {
            val text = seg.text
            var j = 0
            while (j < text.length) {
                val c = text[j]
                if (c == '\n') {
                    flush()
                    j++
                    continue
                }
                if (c.isWhitespace()) {
                    val s = j
                    while (j < text.length && text[j].isWhitespace() && text[j] != '\n') j++
                    cur.add(Piece.Space(text.substring(s, j)))
                } else {
                    val s = j
                    while (j < text.length && !text[j].isWhitespace()) j++
                    cur.add(Piece.Boilerplate(text.substring(s, j)))
                }
            }
        } else {
            val text = seg.text
            var j = 0
            while (j < text.length) {
                val c = text[j]
                if (c == '\n') {
                    flush()
                    j++
                    continue
                }
                if (c.isWhitespace()) {
                    val s = j
                    while (j < text.length && text[j].isWhitespace() && text[j] != '\n') j++
                    cur.add(Piece.Space(text.substring(s, j)))
                } else {
                    val s = j
                    while (j < text.length && !text[j].isWhitespace()) j++
                    cur.add(Piece.Word(text.substring(s, j), wordIdx++))
                }
            }
        }
    }
    if (cur.isNotEmpty()) lines.add(cur)
    return lines
}

@Composable
private fun WordChip(
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
                fontSize = CODE_BASE,
                lineHeight = CODE_LINE,
                fontWeight = FontWeight.SemiBold,
                color = GoColors.ChipDoneText
            )
        }

        1 -> {
            val typed = session.typedInWord.coerceAtMost(word.length)
            val untyped = word.length - typed
            val fillBg = lerp(GoColors.ChipCurrent, GoColors.ChipDone, fillAmount)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.shadow(if (fillAmount < 1f) 4.dp else 0.dp, RoundedCornerShape(8.dp))
            ) {
                if (typed > 0) {
                    Text(
                        text = word.substring(0, typed),
                        fontFamily = CodeFont,
                        fontSize = CODE_BIG,
                        lineHeight = CODE_LINE,
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
                    fontSize = CODE_BIG,
                    lineHeight = CODE_LINE,
                    fontWeight = FontWeight.Bold,
                    color = GoColors.TextPrimary.copy(alpha = caretAlpha)
                )
                if (untyped > 0) {
                    Text(
                        text = word.substring(typed),
                        fontFamily = CodeFont,
                        fontSize = CODE_BIG,
                        lineHeight = CODE_LINE,
                        fontWeight = FontWeight.Bold,
                        color = if (session.wrong) GoColors.Error else GoColors.ChipCurrentText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (session.wrong) GoColors.ErrorBg else GoColors.ChipCurrent
                            )
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
                fontSize = CODE_BASE,
                lineHeight = CODE_LINE,
                color = wordColor(word)
            )
        }
    }
}

@Composable
private fun CodeDisplay(
    segments: List<Segment>,
    session: Session,
    fillAmount: Float,
    caretAlpha: Float
) {
    val lines = remember(segments) { buildLines(segments) }
    Column {
        for (line in lines) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                for (piece in line) {
                    when (piece) {
                        is Piece.Space -> Text(
                            text = piece.text,
                            fontFamily = CodeFont,
                            fontSize = CODE_BASE,
                            lineHeight = CODE_LINE
                        )
                        is Piece.Comment -> Text(
                            text = piece.text,
                            fontFamily = CodeFont,
                            fontSize = 14.sp,
                            lineHeight = CODE_LINE,
                            fontStyle = FontStyle.Italic,
                            color = GoColors.Comment
                        )
                        is Piece.Boilerplate -> Text(
                            text = piece.text,
                            fontFamily = CodeFont,
                            fontSize = CODE_BASE,
                            lineHeight = CODE_LINE,
                            color = wordColor(piece.text)
                        )
                        is Piece.Word -> WordChip(
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

fun Modifier.drawVerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    color: Color = Color.Gray.copy(alpha = 0.3f)
): Modifier = drawWithContent {
    drawContent()
    val viewportHeight = size.height
    val totalHeight = scrollState.maxValue + viewportHeight
    if (totalHeight > viewportHeight) {
        val scrollbarHeight = (viewportHeight / totalHeight) * viewportHeight
        val scrollbarOffsetY = (scrollState.value.toFloat() / totalHeight) * viewportHeight
        drawRect(
            color = color,
            topLeft = Offset(size.width - 4.dp.toPx(), scrollbarOffsetY),
            size = Size(4.dp.toPx(), scrollbarHeight)
        )
    }
}

@Composable
fun ChallengeScreen(
    segments: List<Segment>,
    initialSession: Session,
    onComplete: (correctChars: Int, wrongKeys: Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // ── Audio Feedback System ──
    val correctPlayer = remember { MediaPlayer.create(context, R.raw.correct) }
    val incorrectPlayer = remember { MediaPlayer.create(context, R.raw.incorrect) }
    
    DisposableEffect(Unit) {
        onDispose {
            correctPlayer?.release()
            incorrectPlayer?.release()
        }
    }
    
    fun playSound(player: MediaPlayer?) {
        player?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                    it.seekTo(0)
                }
                it.start()
            } catch (e: Exception) {
                // Audio safety
            }
        }
    }

    var session by remember { mutableStateOf(initialSession) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var correctChars by rememberSaveable { mutableStateOf(0) }
    var wrongKeys by rememberSaveable { mutableStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }

    // State Machine Mood
    var mood by remember { mutableStateOf(MascotMood.Idle) }
    val shake = remember { Animatable(0f) }
    
    // ── Shared Scroll States for bidirectional navigation ──
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    val processChange: (String) -> Unit = { newValue ->
        val old = inputText
        if (newValue.length > old.length) {
            mood = MascotMood.Typing
            var s = session
            var c = correctChars
            var w = wrongKeys
            for (i in old.length until newValue.length) {
                val ch = newValue[i]
                if (ch == ' ') {
                    val before = s
                    s = Core.typeSpace(s)
                    if (s.wordIndex > before.wordIndex) {
                        playSound(correctPlayer)
                    }
                } else {
                    val before = s
                    s = Core.typeChar(s, ch)
                    if (s.wrong && !before.wrong) {
                        w++
                        mood = MascotMood.Error
                        playSound(incorrectPlayer)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            for (dx in listOf(-10f, 10f, -7f, 7f, -4f, 4f, 0f)) {
                                shake.animateTo(dx, tween(40, easing = LinearEasing))
                            }
                            if (!s.wrong) mood = MascotMood.Typing
                        }
                    } else if (!s.wrong && s.typedInWord > before.typedInWord) {
                        c++
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
            session = s
            correctChars = c
            wrongKeys = w
        } else if (newValue.length < old.length) {
            var s = session
            repeat(old.length - newValue.length) { s = Core.backspace(s) }
            session = s
            if (!s.wrong) mood = MascotMood.Typing
        }
        inputText = newValue

        if (Core.isComplete(session) && !fired) {
            fired = true
            mood = MascotMood.Success
            onComplete(correctChars, wrongKeys)
            celebrating = true
        }
    }

    val currentWord = session.words.getOrNull(session.wordIndex)
    val targetFill = if (currentWord != null && currentWord.isNotEmpty())
        session.typedInWord.toFloat() / currentWord.length else 0f
    val fillAmount by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(150),
        label = "wordFill"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    val wordsTotal = session.words.size
    val wordsDone = session.wordIndex.coerceAtMost(wordsTotal)
    val progress = if (wordsTotal == 0) 0f else wordsDone.toFloat() / wordsTotal
    val wordsToGo = wordsTotal - wordsDone

    Box(modifier = Modifier.fillMaxSize().background(GoColors.Background).imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Header with Mascot (Pinned) ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GoColors.TextMuted
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Challenge",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoColors.TextPrimary
                    )
                    Text(
                        text = Core.today().let { "${it.substring(4, 6)}/${it.substring(6)}/${it.substring(0, 4)}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint
                    )
                }
                GopherMascot(mood = mood)
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
                        progress = { progress },
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
                            text = if (wordsToGo == 0) "Completed!" else "$wordsToGo words remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (wordsToGo == 0) GoColors.Success else GoColors.AccentBlue
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Accuracy: ${(correctChars.toFloat() / (correctChars + wrongKeys).coerceAtLeast(1) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoColors.TextMuted
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Scrollable Code Section ──
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .drawVerticalScrollbar(vScrollState)
                    ) {
                        // Gutter (Synced Scroll)
                        val lineCount = remember(segments) {
                            segments.joinToString("") { it.text }.count { it == '\n' } + 1
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(30.dp)
                                .background(GoColors.Surface, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                .graphicsLayer { translationY = -vScrollState.value.toFloat() } // Direct Sync
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (n in 1..lineCount) {
                                Text(
                                    text = n.toString(),
                                    fontFamily = CodeFont,
                                    fontSize = 14.sp,
                                    lineHeight = CODE_LINE,
                                    color = GoColors.TextFaint
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                                .horizontalScroll(hScrollState) // Horizontal scroll only for the code
                                .verticalScroll(vScrollState)   // Vertical scroll synced with gutter
                        ) {
                            CodeDisplay(
                                segments = segments,
                                session = session,
                                fillAmount = fillAmount,
                                caretAlpha = caretAlpha
                            )

                            BasicTextField(
                                value = inputText,
                                onValueChange = processChange,
                                textStyle = TextStyle(
                                    color = Color.Transparent,
                                    fontFamily = CodeFont,
                                    fontSize = CODE_BASE,
                                    lineHeight = CODE_LINE,
                                    letterSpacing = 0.sp
                                ),
                                cursorBrush = SolidColor(Color.Transparent),
                                modifier = Modifier.fillMaxSize().width(2000.dp), // Large width for horizontal scrolling
                                singleLine = false,
                                readOnly = finished || celebrating
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (finished) {
                TactileButton(
                    text = "Return to Dashboard",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }
        }

        if (celebrating) {
            CelebrationOverlay(onFinished = { celebrating = false; finished = true })
        }
    }
}
