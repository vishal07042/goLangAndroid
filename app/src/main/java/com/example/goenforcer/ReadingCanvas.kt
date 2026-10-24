package com.example.goenforcer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Reading Enforcer chip canvas ───────────────────────────────────────
// Direct port of the Go ChallengeScreen monospace word-chip canvas
// (Ui.kt WordChip, re-mirrored in AffirmationScreen.AffWordChip): same
// 8dp corner chips, 6x2dp padding, CodeFont sizing (18sp base / 22sp big,
// 30sp line height) and the done / current / missed / upcoming palette.
// Reading adds a golden drag-and-drop blank slot and a wrapping FlowRow —
// chips size to content like natural words, never fixed columns.

private val READING_BASE = 18.sp
private val READING_BIG = 22.sp
private val READING_LINE = 30.sp

// ─── Tap handling (whole chip is the hit area, no ripple) ───────────────
private fun Modifier.readingTap(
    onTap: (() -> Unit)?,
    source: MutableInteractionSource
): Modifier =
    if (onTap != null) {
        clickable(
            interactionSource = source,
            indication = null,
            onClick = onTap
        )
    } else this

/**
 * One monospace "code chip" for the Reading flow.
 *
 * Word states are shared with the rest of the Reading feature:
 * [enum class WordState][WordState] is defined in ReadingCore.kt.
 *
 * [isBlank] renders the golden drop-zone slot ("?") instead of a word —
 * used while a vocabulary tile is being dragged over the document;
 * [blankActive] intensifies the highlight while dragging is in progress.
 */
@Composable
fun ReadingWordChip(
    word: String,
    state: WordState,
    modifier: Modifier = Modifier,
    isBlank: Boolean = false,       // drag & drop slot -> renders the "?" drop-zone
    blankActive: Boolean = false,   // a tile is being dragged over the doc
    onTap: (() -> Unit)? = null
) {
    // Blank slot overrides the word chip entirely (drag & drop target).
    if (isBlank) {
        ReadingBlankSlot(
            blankActive = blankActive,
            modifier = modifier,
            onTap = onTap
        )
        return
    }

    val tapSource = remember { MutableInteractionSource() }

    when (state) {
        WordState.DONE -> Box(
            modifier = modifier
                .readingTap(onTap, tapSource)
                .clip(RoundedCornerShape(8.dp))
                .background(GoColors.ChipDone)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = READING_BASE,
                lineHeight = READING_LINE,
                fontWeight = FontWeight.SemiBold,
                color = GoColors.ChipDoneText
            )
        }

        WordState.CURRENT -> Box(
            // BIG current variant, like the Go canvas's current word.
            modifier = modifier
                .readingTap(onTap, tapSource)
                .clip(RoundedCornerShape(8.dp))
                .background(GoColors.ChipCurrent)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = READING_BIG,
                lineHeight = READING_LINE,
                fontWeight = FontWeight.Bold,
                color = GoColors.ChipCurrentText
            )
        }

        WordState.MISSED -> Box(
            // Same error treatment as the Go canvas's wrong-typed word
            // (big variant + bold red text on soft red background).
            modifier = modifier
                .readingTap(onTap, tapSource)
                .clip(RoundedCornerShape(8.dp))
                .background(GoColors.ErrorBg)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = READING_BIG,
                lineHeight = READING_LINE,
                fontWeight = FontWeight.Bold,
                color = GoColors.Error
            )
        }

        WordState.UPCOMING -> Box(
            modifier = modifier
                .readingTap(onTap, tapSource)
                .border(1.dp, GoColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = word,
                fontFamily = CodeFont,
                fontSize = READING_BASE,
                lineHeight = READING_LINE,
                color = GoColors.TextMuted
            )
        }
    }
}

// ─── Blank drop-zone slot (drag & drop target) ──────────────────────────
@Composable
private fun ReadingBlankSlot(
    blankActive: Boolean,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val tapSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .readingTap(onTap, tapSource)
            .defaultMinSize(minWidth = 44.dp, minHeight = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GoColors.AccentGold.copy(alpha = if (blankActive) 0.40f else 0.25f))
            .border(
                width = if (blankActive) 2.dp else 1.dp,
                color = if (blankActive) GoColors.Primary else GoColors.AccentGold,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            fontFamily = CodeFont,
            fontSize = READING_BASE,
            lineHeight = READING_LINE,
            fontWeight = FontWeight.Bold,
            color = GoColors.AccentGold
        )
    }
}

// ─── Word flow (natural wrapping, no fixed columns) ─────────────────────
/**
 * Renders [words] as a natural chip word-flow: each chip sizes to content
 * and wraps between lines. [blankIndex] turns one chip into the "?" drop
 * zone; [blankActive] intensifies it; [onWordTap] forwards the word index
 * (Slide mode gesture).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingWordFlow(
    words: List<String>,
    states: List<WordState>,
    modifier: Modifier = Modifier,
    blankIndex: Int = -1,
    blankActive: Boolean = false,
    onWordTap: ((Int) -> Unit)? = null
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        words.forEachIndexed { i, word ->
            ReadingWordChip(
                word = word,
                state = states.getOrElse(i) { WordState.UPCOMING },
                isBlank = i == blankIndex,
                blankActive = blankActive,
                onTap = onWordTap?.let { cb -> { cb(i) } }
            )
        }
    }
}
