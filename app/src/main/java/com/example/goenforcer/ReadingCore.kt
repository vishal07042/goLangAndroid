package com.example.goenforcer

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ─── Reading Enforcement: pure domain core ───────────────────────────────
// Everything in this file is Android-free so it runs as plain JVM unit
// tests. It owns: text normalisation, sentence splitting, the deterministic
// 30-word chunker, and the per-mode verification logic (retype / slide /
// drag / read-aloud / flash). The Compose layer only renders state and
// forwards events — no logic lives there.

// ── Word states shared by every paragraph mode ──────────────────────────
enum class WordState { DONE, CURRENT, MISSED, UPCOMING }

/** A fixed 30-word slice of the document plus its greyed context. */
data class ReadingChunk(
    val seq: Int,                 // 0-based, monotonic, never a file offset
    val words: List<String>,      // exactly 30 (last chunk of doc may be shorter)
    val contextBefore: String     // previous 1-2 sentences, not verified
) {
    val wordCount: Int get() = words.size
    val isChapterEnd: Boolean = false // reserved for the EPUB source
}

data class ReadingDocument(
    val id: String,               // sha256 of normalised text → re-import never duplicates
    val title: String,
    val text: String,
    val addedAt: Long,
    /** True for the Markdown cheat sheets bundled in the APK (read-only). */
    val builtIn: Boolean = false
)

// ─── Text normalisation ────────────────────────────────────────────────
// Case- and trailing-punctuation-insensitive, plus smart-quote folding so
// "don’t" and "don't" and "Don't" all compare equal.
object ReadingText {

    private val TRAILING = ".,;:!?\"'“”’‘)]}>…"
    private val SMART_MAP = mapOf(
        '\u2018' to '\'', '\u2019' to '\'', '\u02BC' to '\'', '\u201B' to '\'',
        '\u201C' to '"', '\u201D' to '"', '\u201E' to '"',
        '\u2013' to '-', '\u2014' to '-', '\u2212' to '-',
        '\u00A0' to ' ', '\u2026' to '.'
    )

    /** Fold smart punctuation to ASCII, lowercase, strip trailing punctuation. */
    fun normalize(raw: String): String {
        val folded = buildString(raw.length) {
            for (ch in raw) append(SMART_MAP[ch] ?: ch)
        }
        return folded.trim()
            .lowercase(Locale.US)
            .trimEnd { it in TRAILING }
            .trim()
    }

    /** Cheap equality used by every mode's match rule. */
    fun same(a: String, b: String): Boolean = normalize(a) == normalize(b)
}

// ─── Sentence splitting (deterministic, abbreviation-aware) ────────────
// A hand-rolled splitter instead of ICU so the SAME input always yields the
// SAME context on every device and on the JVM test runner.
object ReadingSentences {

    private val ABBREV = setOf(
        "dr", "mr", "mrs", "ms", "prof", "st", "jr", "sr", "vs", "etc",
        "approx", "dept", "est", "fig", "inc", "ltd", "no", "p", "pp",
        "resp", "vol", "min", "max", "e.g", "i.e", "u.s", "u.k", "a.m", "p.m"
    )

    /** Split [text] into sentences on [.!?] followed by whitespace + capital/end. */
    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '!' || c == '?') {
                // Consume trailing closing quotes/brackets into this sentence.
                var j = i + 1
                while (j < n && text[j] in "\"'\u2019\u201D)]}") {
                    sb.append(text[j]); j++
                }
                val nextIsBoundary = j >= n || text[j] == '\n'
                val followedBySpace = j < n && text[j].isWhitespace()
                if (nextIsBoundary || followedBySpace) {
                    // Look back at the last token; don't break on abbreviations.
                    val lastToken = lastTokenBefore(sb)
                    val isAbbrev = c == '.' && lastToken.lowercase(Locale.US) in ABBREV
                    val isSingleLetter = c == '.' && lastToken.length == 1 && lastToken[0].isUpperCase()
                    if (!isAbbrev && !isSingleLetter) {
                        val s = sb.toString().trim()
                        if (s.isNotEmpty()) out.add(s)
                        sb.setLength(0)
                        i = j
                        continue
                    }
                }
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    private fun lastTokenBefore(sb: StringBuilder): String {
        var end = sb.length
        // Step back over the terminator we just appended.
        while (end > 0 && sb[end - 1] in ".!?\"'\u2019\u201D)]}") end--
        var start = end
        while (start > 0 && !sb[start - 1].isWhitespace()) start--
        return sb.substring(start, end)
    }

    /** The last [max] sentences of [text] — the greyed, unverified context. */
    fun lastSentences(text: String, max: Int = 2): String {
        if (text.isBlank()) return ""
        val all = split(text)
        return all.takeLast(max).joinToString(" ").trim()
    }
}

// ─── Deterministic 30-word chunker ─────────────────────────────────────
object ReadingChunker {

    const val CHUNK_WORDS = 30

    fun words(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Cut [words] into fixed slices. Deterministic: same input → same chunks. */
    fun chunk(words: List<String>, perChunk: Int = CHUNK_WORDS): List<List<String>> =
        if (words.isEmpty()) emptyList() else words.chunked(perChunk.coerceAtLeast(1))

    /**
     * Build full chunks with context. [contextChars] of raw source before a
     * chunk's first word feed the greyed sentence(s).
     */
    fun build(text: String, perChunk: Int = CHUNK_WORDS): List<ReadingChunk> {
        val all = words(text)
        if (all.isEmpty()) return emptyList()
        // Character offsets of every word so context can be sliced precisely.
        val offsets = wordOffsets(text)
        val slices = chunk(all, perChunk)
        return slices.mapIndexed { seq, slice ->
            val firstIndex = seq * perChunk
            val startChar = offsets[firstIndex]
            val context = ReadingSentences.lastSentences(text.substring(0, startChar))
            ReadingChunk(seq = seq, words = slice, contextBefore = context)
        }
    }

    /** Start offset (in [text]) of each whitespace-delimited word, in order. */
    private fun wordOffsets(text: String): List<Int> {
        val out = mutableListOf<Int>()
        var inWord = false
        for (i in text.indices) {
            val ws = text[i].isWhitespace()
            if (!ws && !inWord) { out.add(i); inWord = true }
            else if (ws) inWord = false
        }
        return out
    }
}

// ─── Flash mode: SwiftRead-style RSVP with ORP highlighting ────────────
// Rapid Serial Visual Presentation: one word at a time at a fixed point,
// with the Optimal Recognition Point (≈1/3 into the word) highlighted so the
// eye never has to travel. Smart pacing adds pauses on long words and
// punctuation, exactly like SwiftRead.
object Rsvp {

    const val MIN_WPM = 100
    const val MAX_WPM = 1000
    const val DEFAULT_WPM = 400

    /** Scheller ORP formula: where the eye should land inside the word. */
    fun orpIndex(word: String): Int {
        val len = word.length
        return when {
            len <= 1 -> 0
            else -> max(0, ((len - 2) / 4) + 1).coerceAtMost(len - 1)
        }
    }

    /** Base milliseconds per word for [wpm]. */
    fun baseDelayMs(wpm: Int): Long {
        val w = wpm.coerceIn(MIN_WPM, MAX_WPM)
        return (60_000.0 / w).roundToInt().toLong().coerceAtLeast(20L)
    }

    /**
     * Smart pacing: long words and sentence/clause punctuation get a longer
     * hold so comprehension keeps up with the flash.
     */
    fun delayMs(word: String, wpm: Int): Long {
        val base = baseDelayMs(wpm)
        val lenBonus = if (word.length > 6) (word.length - 6) * 12L else 0L
        val punctBonus = when {
            word.isNotEmpty() && word.last() in ".!?" -> (base * 1.5).roundToInt().toLong()
            word.isNotEmpty() && word.last() in ",;:" -> (base * 0.5).roundToInt().toLong()
            else -> 0L
        }
        return base + lenBonus + punctBonus
    }
}

// ─── Retype mode ───────────────────────────────────────────────────────
// The retype engine is the ORIGINAL Go Enforcer typing core (GoEnforcerCore
// Core.typeChar / typeSpace / backspace): per-letter exact matching, a wrong
// key locks the word until backspace, space commits a fully-typed word, and
// backspace reaches back into previous words. A chunk therefore passes 100%
// by construction. (The old normalised RetypeLogic was removed — typing is
// now identical to the Go challenge and the affirmation screen.)

// ─── Slide mode ────────────────────────────────────────────────────────
// One gesture moves one word, and a pace floor rejects flicks so the whole
// chunk can't be swiped through in a second.
object SlideLogic {
    const val PACE_FLOOR_MS = 400L

    /** True if enough time has elapsed since the last accepted word. */
    fun paceOk(lastAtMs: Long, nowMs: Long): Boolean =
        lastAtMs == 0L || (nowMs - lastAtMs) >= PACE_FLOOR_MS

    /** A drag must travel at least this far (dp→px is the UI's job). */
    const val SWIPE_THRESHOLD_FRACTION = 0.35f
}

// ─── Drag & drop mode ("fill the gap") ─────────────────────────────────
// The current word is blanked in the text; a tray holds the correct tile plus
// two distractors drawn from the same chunk. Dropping the right tile fills the
// slot and advances. Deterministic per (chunkSeed, index) so re-dealing the
// same gap yields the same tray.
object DragDeck {

    const val TRAY_SIZE = 3

    /** Deterministic shuffle seed for one gap. */
    fun seed(chunkSeed: Int, index: Int): Int = chunkSeed * 31 + index

    /**
     * Build the tray for the word at [index]: the correct word plus up to
     * [TRAY_SIZE-1] distractors pulled from elsewhere in the chunk, shuffled
     * deterministically. The correct tile always appears exactly once.
     */
    fun deal(words: List<String>, index: Int, chunkSeed: Int): List<String> {
        if (index !in words.indices) return emptyList()
        val correct = words[index]
        val rng = java.util.Random(seed(chunkSeed, index).toLong())

        // Candidate distractors: other words that don't match the target.
        val pool = words.filterIndexed { i, w -> i != index && !ReadingText.same(w, correct) }
        val distractors = pool.distinct().shuffled(rng).take(TRAY_SIZE - 1)

        return (distractors + correct).shuffled(rng)
    }

    /** Which tile (if any) matches the blanked word. */
    fun isCorrect(tile: String, target: String): Boolean = ReadingText.same(tile, target)
}

// ─── Read-aloud mode ───────────────────────────────────────────────────
// Recognizer partials are jittery, so a word only advances when it stays
// stable across TWO consecutive partials. Words inside the look-ahead window
// that are jumped over turn red (missed). Pass is ≥80% matched in order.
class AloudAligner(
    private val expected: List<String>,
    private val lookahead: Int = 3,
    private val passThreshold: Float = 0.80f
) {
    var cursor: Int = 0
        private set
    var matched: Int = 0
        private set
    private val missed = BooleanArray(expected.size)
    private var prev: List<String>? = null

    val missedIndices: List<Int> get() = missed.withIndex().filter { it.value }.map { it.index }

    /** Result of feeding one partial hypothesis. */
    data class Update(
        val advancedBy: Int,
        val newlyMissed: List<Int>,
        val completed: Boolean,
        val passed: Boolean
    )

    /** Feed a partial hypothesis (already tokenised, raw). */
    fun onPartial(tokens: List<String>): Update {
        val cur = tokens.map { ReadingText.normalize(it) }.filter { it.isNotEmpty() }
        val before = cursor
        val newMissed = mutableListOf<Int>()
        val previous = prev

        if (previous != null && cursor < expected.size) {
            // Find the first expected word at/after cursor that appears (in
            // order) in BOTH the previous and current partial → it's stable.
            val stableIdx = firstStableIndex(cur, previous)
            if (stableIdx != null) {
                // Words skipped between cursor and stableIdx become missed.
                for (k in cursor until stableIdx) {
                    if (!missed[k]) { missed[k] = true; newMissed.add(k) }
                }
                matched++
                cursor = stableIdx + 1
            }
        }

        prev = cur
        val completed = cursor >= expected.size
        return Update(
            advancedBy = cursor - before,
            newlyMissed = newMissed,
            completed = completed,
            passed = completed && ratio() >= passThreshold
        )
    }

    /**
     * Earliest expected index ≥ cursor whose normalised word appears in both
     * [cur] and [prev] partials and within the look-ahead window.
     */
    private fun firstStableIndex(cur: List<String>, prev: List<String>): Int? {
        val curSet = cur.toSet()
        val prevSet = prev.toSet()
        val limit = minOf(cursor + lookahead, expected.size - 1)
        for (j in cursor..limit) {
            val w = ReadingText.normalize(expected[j])
            if (w.isNotEmpty() && curSet.contains(w) && prevSet.contains(w)) return j
        }
        return null
    }

    fun ratio(): Float = if (expected.isEmpty()) 0f else matched.toFloat() / expected.size

    fun passed(): Boolean = cursor >= expected.size && ratio() >= passThreshold
}

/** Stateless pass-rule helper shared by the VM and tests. */
object ReadingPassRule {
    const val ALOUD_THRESHOLD = 0.80f
    fun aloudPass(matched: Int, total: Int): Boolean =
        total > 0 && matched.toFloat() / total >= ALOUD_THRESHOLD
}

// ─── Markdown → readable text ──────────────────────────────────────────
// The bundled cheat sheets are Markdown. Reading enforcement needs a clean
// word stream, not syntax noise, so formatting is stripped while the actual
// content (including identifiers like train_test_split) is preserved.
object MarkdownText {

    private val FENCE = Regex("^\\s*```.*$", RegexOption.MULTILINE)
    private val HEADING = Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE)
    private val BULLET = Regex("^\\s{0,3}[-*+]\\s+", RegexOption.MULTILINE)
    private val BLOCKQUOTE = Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE)
    private val HR = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$", RegexOption.MULTILINE)
    private val IMAGE = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^]]*)]\\([^)]*\\)")
    private val INLINE_CODE = Regex("`([^`]*)`")
    // Word-boundary guards keep snake_case and identifiers intact.
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val BOLD_U = Regex("(?<![\\w])__(.+?)__(?![\\w])")
    private val ITALIC_STAR = Regex("(?<![\\w])\\*(.+?)\\*(?![\\w])")
    private val ITALIC_U = Regex("(?<![\\w])_(.+?)_(?![\\w])")
    private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    // Only real HTML tags — never generics like List<String> in code samples.
    private val HTML_TAG = Regex(
        "</?(?:br|p|div|span|a|img|b|i|em|strong|ul|ol|li|code|pre|blockquote|table|thead|tbody|tr|td|th|h[1-6])\\s*/?>",
        RegexOption.IGNORE_CASE
    )
    private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$", RegexOption.MULTILINE)
    private val MULTI_BLANK = Regex("\n{3,}")

    fun toPlainText(raw: String): String {
        var t = raw.replace("\r\n", "\n").replace('\r', '\n')
        t = FENCE.replace(t, "")
        t = TABLE_SEP.replace(t, "")
        t = IMAGE.replace(t, "$1")
        t = LINK.replace(t, "$1")
        t = HTML_COMMENT.replace(t, "")
        t = HTML_TAG.replace(t, " ")
        t = INLINE_CODE.replace(t, "$1")
        t = BOLD.replace(t, "$1")
        t = BOLD_U.replace(t, "$1")
        t = ITALIC_STAR.replace(t, "$1")
        t = ITALIC_U.replace(t, "$1")
        t = HEADING.replace(t, "")
        t = BULLET.replace(t, "")
        t = BLOCKQUOTE.replace(t, "")
        t = HR.replace(t, "")
        t = t.replace('|', ' ')
        t = MULTI_BLANK.replace(t, "\n\n")
        return t.lines().joinToString("\n") { it.trimEnd() }.trim()
    }
}

