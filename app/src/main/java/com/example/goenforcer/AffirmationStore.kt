package com.example.goenforcer

import android.content.Context
import java.util.Calendar

// ─── Affirmation Enforcer ────────────────────────────────────────────────
// State for the 7:00 AM / 7:00 PM "rewrite your affirmations" enforcement
// windows. Each window picks 3 RANDOM affirmations from the bank (persisted
// for that day + window) that must be rewritten before apps unlock.
// Completely separate from the Go snippet Store — nothing shared.
object AffirmationStore {
    private const val PREFS = "affirmation_enforcer_prefs"
    private const val KEY_DONE_AM = "aff_done_am"
    private const val KEY_DONE_PM = "aff_done_pm"
    private const val KEY_PICK_AM = "aff_pick_am"
    private const val KEY_PICK_PM = "aff_pick_pm"
    private const val KEY_CUSTOM = "aff_custom_text"
    private const val KEY_ENFORCE = "aff_enforce_enabled"

    /** How many affirmations one enforcement window requires. */
    const val PICK_COUNT = 3

    /** Daily rotating affirmation bank. */
    val BANK: List<String> = listOf(
        "Your doubt doesn’t stop the growth. Skipping the work does.",
        "Muscle follows the work you do, not the story you tell.",
        "Believe it or not, put in the work and muscle grows.",
        "Muscle responds to load, not opinion.",
        "Your muscles never see your thoughts, only the weight.",
        "Strength training lifts your self-esteem along with the weights.",
        "Physical strength builds mental strength, confidence and resilience",
        "Your muscles don’t care about your doubts. They only respond to effort.",
        "work In, self doubt out.",
        "physical activity is strongly linked to increased resilience",
        "If you really want peace , prepare for war.",
        "Do it, then you’ll believe it.",
        "Lift the iron, lift the mood.",
        "Move the iron, move the mood.",
        "Iron up, mood up.",
        "Lift iron, lift self-esteem.",
        "use it or lose it.",
        "read aloud , retain longer",
        "reps builds confidence",
        "work not only builds skill but builds confidence too",
        "use sunk cost fallacy to your advantage"
    )

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** User's own affirmation overrides the random pick when set. */
    fun customText(ctx: Context): String? =
        sp(ctx).getString(KEY_CUSTOM, null)?.takeIf { it.isNotBlank() }

    fun setCustomText(ctx: Context, text: String) {
        sp(ctx).edit().putString(KEY_CUSTOM, text.trim()).apply()
    }

    /**
     * The affirmations for one window ([pm] = false → 7 AM, true → 7 PM):
     * 3 random picks from the bank, shuffled in random order, persisted so
     * the same 3 stay on screen for the whole window. Re-rolled each new day
     * and separately for each window.
     */
    fun windowText(ctx: Context, pm: Boolean): String {
        customText(ctx)?.let { return it }
        val key = if (pm) KEY_PICK_PM else KEY_PICK_AM
        val s = sp(ctx)
        val prefix = "${Core.today()}="
        val stored = s.getString(key, null)
        if (stored != null && stored.startsWith(prefix)) {
            return stored.substring(prefix.length)
        }
        val pick = BANK.shuffled().take(PICK_COUNT).joinToString("\n")
        s.edit().putString(key, prefix + pick).apply()
        return pick
    }

    fun isDone(ctx: Context, pm: Boolean): Boolean {
        val key = if (pm) KEY_DONE_PM else KEY_DONE_AM
        return sp(ctx).getString(key, null) == Core.today()
    }

    fun markDone(ctx: Context, pm: Boolean) {
        val key = if (pm) KEY_DONE_PM else KEY_DONE_AM
        sp(ctx).edit().putString(key, Core.today()).apply()
    }

    /** Which window still needs work: false = 7 AM, true = 7 PM, null = all done. */
    fun activeWindow(ctx: Context): Boolean? = when {
        !isDone(ctx, pm = false) -> false
        !isDone(ctx, pm = true) -> true
        else -> null
    }

    fun enforceEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_ENFORCE, true)

    fun setEnforceEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENFORCE, enabled).apply()
    }

    /** Wrap long text so the canvas renders readable lines. */
    fun wrappedSegments(text: String, width: Int = 22): List<Segment> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (word in text.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            if (cur.isEmpty()) cur = word
            else if (cur.length + 1 + word.length <= width) cur += " $word"
            else { lines.add(cur); cur = word }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        // Join with real newlines so Core.splitWords sees one continuous text.
        return listOf(Segment(text = lines.joinToString("\n")))
    }

    private fun dayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
}
