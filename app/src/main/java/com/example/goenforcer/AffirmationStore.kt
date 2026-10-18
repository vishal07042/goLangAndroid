package com.example.goenforcer

import android.content.Context
import java.util.Calendar

// ─── Affirmation Enforcer ────────────────────────────────────────────────
// State for the 7:00 AM "rewrite your affirmation" enforcement loop.
// Completely separate from the Go snippet Store — nothing shared, nothing
// overwritten.
object AffirmationStore {
    private const val PREFS = "affirmation_enforcer_prefs"
    private const val KEY_DONE_DATE = "aff_done_date"
    private const val KEY_CUSTOM = "aff_custom_text"
    private const val KEY_ENFORCE = "aff_enforce_enabled"

    /** Daily rotating affirmation bank. */
    val BANK: List<String> = listOf(
        "I am disciplined and I finish what I start.",
        "Today I choose focus over distraction.",
        "I build the life I want, one morning at a time.",
        "My goals are bigger than my excuses.",
        "I am calm, confident, and in control.",
        "Every day I am getting stronger and smarter.",
        "I do not negotiate with my procrastination.",
        "I deserve the success I am working for.",
        "I turn my ideas into action immediately.",
        "I am grateful, focused, and unstoppable today."
    )

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** User's own affirmation overrides the rotating bank when set. */
    fun customText(ctx: Context): String? =
        sp(ctx).getString(KEY_CUSTOM, null)?.takeIf { it.isNotBlank() }

    fun setCustomText(ctx: Context, text: String) {
        sp(ctx).edit().putString(KEY_CUSTOM, text.trim()).apply()
    }

    /** Today's affirmation: custom if set, otherwise deterministic daily pick. */
    fun todayText(ctx: Context): String =
        customText(ctx) ?: BANK[dayOfYear() % BANK.size]

    fun isDoneToday(ctx: Context): Boolean =
        sp(ctx).getString(KEY_DONE_DATE, null) == Core.today()

    fun markDone(ctx: Context) {
        sp(ctx).edit().putString(KEY_DONE_DATE, Core.today()).apply()
    }

    fun enforceEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_ENFORCE, true)

    fun setEnforceEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENFORCE, enabled).apply()
    }

    /** Wrap long affirmations so the canvas renders readable lines. */
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
