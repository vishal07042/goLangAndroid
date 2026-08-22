package com.example.goenforcer

import android.content.Context
import android.content.Intent
import java.util.Calendar

// ─── Data ───────────────────────────────────────────────────────────────
// comment = shown as a comment, not typed.
// boilerplate (package/imports) = shown as code but not typed.
data class Segment(val comment: Boolean = false, val boilerplate: Boolean = false, val text: String)

data class Session(
    val words: List<String>,
    val wordIndex: Int = 0,
    val typedInWord: Int = 0,
    val wrong: Boolean = false
)

// ─── Core (pure functions, no Android deps) ─────────────────────────────
object Core {

    private const val SEP = '\u001f'

    // Daily rotating bank. Packed content is generated from the plain-text
    // snippets (goenforcer/snippets/*.txt) by goenforcer/genbank.go — never
    // hand-edit the packed strings.
    val QUESTIONS = Array(GeneratedQuestionBank.size) { GeneratedQuestionBank.get(it) }

    fun decodeSegments(packed: String): List<Segment> {
        if (packed.isEmpty()) return emptyList()
        val out = mutableListOf<Segment>()
        var i = 0
        while (i < packed.length) {
            val isComment = packed[i] == 'C'
            val isBoilerplate = packed[i] == 'B'
            i++
            var end = i
            while (end < packed.length && packed[end] != SEP) end++
            out.add(Segment(isComment, isBoilerplate, packed.substring(i, end)))
            i = end + 1
        }
        return out
    }

    fun expectedInput(segments: List<Segment>): String =
        segments.filter { !it.comment && !it.boilerplate }.joinToString("") { it.text }

    fun splitWords(text: String): List<String> =
        text.split("\\s+".toRegex()).filter { it.isNotEmpty() }

    fun newSession(segments: List<Segment>): Session =
        Session(words = splitWords(expectedInput(segments)))

    fun typeChar(s: Session, key: Char): Session {
        if (isComplete(s) || s.wrong) return s
        val word = s.words[s.wordIndex] ?: return s
        if (s.typedInWord >= word.length) return s.copy(wrong = true)
        return if (word[s.typedInWord] == key) {
            s.copy(typedInWord = s.typedInWord + 1)
        } else {
            s.copy(wrong = true)
        }
    }

    fun typeSpace(s: Session): Session {
        if (s.wrong) return s
        if (s.wordIndex >= s.words.size) return s
        if (s.typedInWord < s.words[s.wordIndex].length) return s
        return s.copy(wordIndex = s.wordIndex + 1, typedInWord = 0)
    }

    fun backspace(s: Session): Session {
        if (s.wrong) return s.copy(wrong = false)
        if (s.typedInWord > 0) return s.copy(typedInWord = s.typedInWord - 1)
        if (s.wordIndex > 0) {
            val prev = s.words[s.wordIndex - 1]
            return s.copy(wordIndex = s.wordIndex - 1, typedInWord = prev.length)
        }
        return s
    }

    fun isComplete(s: Session): Boolean =
        !s.wrong && s.wordIndex >= s.words.size && s.words.isNotEmpty() && s.typedInWord == 0

    fun today(): String {
        val c = Calendar.getInstance()
        return ymd(c)
    }

    fun ymd(c: Calendar): String =
        String.format(java.util.Locale.US, "%04d%02d%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /** Short human title for the home teaser, e.g. "func sum(...)". */
    fun questionTitle(packed: String): String {
        val firstLine = decodeSegments(packed).filter { !it.comment && !it.boilerplate }
            .joinToString("") { it.text }.lineSequence()
            .firstOrNull { it.trim().isNotEmpty() } ?: "Go snippet"
        val m = Regex("func\\s+(\\w+)").find(firstLine)
        return m?.let { "func ${it.groupValues[1]}(...)" } ?: firstLine.trim()
    }
}

// ─── Persistence + broadcast (Android deps) ─────────────────────────────
object Store {
    private const val PREFS = "go_enforcer_prefs"
    private const val KEY_DONE = "isDoneToday"
    private const val KEY_QUESTION = "today_question_id"
    private const val KEY_DATES = "completed_dates"
    private const val KEY_SNIPPETS = "total_snippets"
    private const val KEY_WORDS = "total_words"
    private const val KEY_CORRECT = "total_correct_chars"
    private const val KEY_WRONG = "total_wrong_keys"
    private const val KEY_BEST = "best_streak"

    private val whitelist = setOf(
        "com.google.android.inputmethod.latine",
        "com.android.systemui", "com.android.settings", "android",
        "com.miui.home", "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.googlequicksearchbox",
        "com.android.phone", "com.android.mms"
    )

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDoneToday(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_DONE, false)

    // Legacy entry point kept for compatibility; UI uses complete().
    fun markDone(ctx: Context) = complete(ctx, snippetWords = 0, correctChars = 0, wrongKeys = 0)

    /** Called once per finished snippet. Persists streak/stats and, crucially,
     *  fires the CHALLENGE_COMPLETE broadcast the Gboard IME listens for. */
    fun complete(ctx: Context, snippetWords: Int, correctChars: Int, wrongKeys: Int) {
        val s = sp(ctx)
        val ed = s.edit()

        val dates = completedDates(ctx)
        val today = Core.today()
        if (dates.add(today)) ed.putStringSet(KEY_DATES, dates)

        ed.putInt(KEY_SNIPPETS, s.getInt(KEY_SNIPPETS, 0) + 1)
        ed.putInt(KEY_WORDS, s.getInt(KEY_WORDS, 0) + snippetWords)
        ed.putInt(KEY_CORRECT, s.getInt(KEY_CORRECT, 0) + correctChars)
        ed.putInt(KEY_WRONG, s.getInt(KEY_WRONG, 0) + wrongKeys)
        ed.putInt(KEY_BEST, maxOf(s.getInt(KEY_BEST, 0), computeStreak(dates)))

        ed.putBoolean(KEY_DONE, true).apply()

        ctx.sendBroadcast(
            Intent("com.example.pf.CHALLENGE_COMPLETE")
                .setPackage("com.google.android.inputmethod.latine")
                .putExtra("date", today)
        )
    }

    fun completedDates(ctx: Context): MutableSet<String> =
        sp(ctx).getStringSet(KEY_DATES, null)?.toMutableSet() ?: mutableSetOf()

    /** Consecutive days ending today (or yesterday, if today not done yet). */
    fun streak(ctx: Context): Int = computeStreak(completedDates(ctx))

    fun bestStreak(ctx: Context): Int = sp(ctx).getInt(KEY_BEST, 0)

    fun totalSnippets(ctx: Context): Int = sp(ctx).getInt(KEY_SNIPPETS, 0)
    fun totalWords(ctx: Context): Int = sp(ctx).getInt(KEY_WORDS, 0)

    fun accuracy(ctx: Context): Float {
        val c = sp(ctx).getInt(KEY_CORRECT, 0)
        val w = sp(ctx).getInt(KEY_WRONG, 0)
        return if (c + w == 0) 0f else c.toFloat() / (c + w)
    }

    private fun computeStreak(dates: Set<String>): Int {
        val cal = Calendar.getInstance()
        var cur = Core.ymd(cal)
        if (cur !in dates) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
            cur = Core.ymd(cal)
        }
        var count = 0
        while (cur in dates) {
            count++
            cal.add(Calendar.DAY_OF_MONTH, -1)
            cur = Core.ymd(cal)
        }
        return count
    }

    fun isUnlocked(ctx: Context, pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        if (whitelist.contains(pkg)) return true
        return isDoneToday(ctx)
    }

    fun resolveTodayIndex(ctx: Context): Int {
        val sp = sp(ctx)
        val count = Core.QUESTIONS.size
        val tod = Core.today()
        val id = sp.getString(KEY_QUESTION, null)
        if (id != null && id.startsWith("$tod=")) {
            try {
                val idx = id.substring(tod.length + 1).toInt()
                if (idx in 0 until count) return idx
            } catch (_: Exception) {}
        }
        val idx = (Math.random() * count).toInt().coerceAtLeast(0)
        sp.edit().putString(KEY_QUESTION, "$tod=$idx").apply()
        return idx
    }
}
