package com.example.goenforcer

import android.content.Context
import java.security.MessageDigest

// ─── Reading Enforcement: persistence ──────────────────────────────────
// Backed by SharedPreferences for now (same shape as the other stores). The
// seam is the object's public surface — swapping to Room later is an
// internal change only. Documents are keyed by a sha256 of their normalised
// text so re-pasting the same text never creates a duplicate.
object ReadingStore {

    private const val PREFS = "reading_enforcer_prefs"
    private const val KEY_DOCS = "reading_docs"
    private const val KEY_PREFIX_TITLE = "reading_title_"
    private const val KEY_PREFIX_TEXT = "reading_text_"
    private const val KEY_PREFIX_ADDED = "reading_added_"
    private const val KEY_PREFIX_PROGRESS = "reading_progress_"

    private const val KEY_SLIDE_VARIANT = "reading_slide_variant"
    private const val KEY_QUIET_MODE = "reading_quiet_mode"
    private const val KEY_WPM = "reading_wpm"
    private const val KEY_LAST_MODE = "reading_last_mode"
    private const val KEY_SLIDE_STREAK = "reading_slide_streak"

    enum class SlideVariant { OUT, STRIKE }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** sha256 of whitespace-normalised text — stable identity across re-imports. */
    fun hash(text: String): String {
        val normalized = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ").trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Add (or no-op if identical text already present). Returns the doc id. */
    fun addDocument(ctx: Context, title: String, text: String): String {
        val id = hash(text)
        val s = sp(ctx)
        val ids = s.getStringSet(KEY_DOCS, null)?.toMutableSet() ?: mutableSetOf()
        if (!ids.contains(id)) {
            s.edit()
                .putStringSet(KEY_DOCS, ids.apply { add(id) })
                .putString(KEY_PREFIX_TITLE + id, title.ifBlank { "Untitled" })
                .putString(KEY_PREFIX_TEXT + id, text)
                .putLong(KEY_PREFIX_ADDED + id, System.currentTimeMillis())
                .apply()
        }
        return id
    }

    fun documents(ctx: Context): List<ReadingDocument> {
        val s = sp(ctx)
        val ids = s.getStringSet(KEY_DOCS, null) ?: return emptyList()
        return ids.mapNotNull { id ->
            val text = s.getString(KEY_PREFIX_TEXT + id, null) ?: return@mapNotNull null
            ReadingDocument(
                id = id,
                title = s.getString(KEY_PREFIX_TITLE + id, "Untitled") ?: "Untitled",
                text = text,
                addedAt = s.getLong(KEY_PREFIX_ADDED + id, 0L)
            )
        }.sortedByDescending { it.addedAt }
    }

    fun document(ctx: Context, id: String): ReadingDocument? {
        val s = sp(ctx)
        val text = s.getString(KEY_PREFIX_TEXT + id, null) ?: return null
        return ReadingDocument(
            id = id,
            title = s.getString(KEY_PREFIX_TITLE + id, "Untitled") ?: "Untitled",
            text = text,
            addedAt = s.getLong(KEY_PREFIX_ADDED + id, 0L)
        )
    }

    /** Highest chunk seq passed for a doc (−1 = nothing yet). */
    fun lastPassedSeq(ctx: Context, docId: String): Int =
        sp(ctx).getInt(KEY_PREFIX_PROGRESS + docId, -1)

    /** Advance only on a first-time pass of the furthest chunk. */
    fun setLastPassedSeq(ctx: Context, docId: String, seq: Int) {
        if (seq > lastPassedSeq(ctx, docId)) {
            sp(ctx).edit().putInt(KEY_PREFIX_PROGRESS + docId, seq).apply()
        }
    }

    // ── Settings ──
    fun slideVariant(ctx: Context): SlideVariant =
        if (sp(ctx).getString(KEY_SLIDE_VARIANT, SlideVariant.OUT.name) == SlideVariant.STRIKE.name)
            SlideVariant.STRIKE else SlideVariant.OUT

    fun setSlideVariant(ctx: Context, v: SlideVariant) {
        sp(ctx).edit().putString(KEY_SLIDE_VARIANT, v.name).apply()
    }

    fun quietMode(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_QUIET_MODE, false)
    fun setQuietMode(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean(KEY_QUIET_MODE, on).apply() }

    fun wpm(ctx: Context): Int = sp(ctx).getInt(KEY_WPM, Rsvp.DEFAULT_WPM)
    fun setWpm(ctx: Context, wpm: Int) {
        sp(ctx).edit().putInt(KEY_WPM, wpm.coerceIn(Rsvp.MIN_WPM, Rsvp.MAX_WPM)).apply()
    }

    fun lastMode(ctx: Context): String? = sp(ctx).getString(KEY_LAST_MODE, null)
    fun setLastMode(ctx: Context, mode: String) { sp(ctx).edit().putString(KEY_LAST_MODE, mode).apply() }

    /** Consecutive slide-only chunks — used by the "force a real mode" guard. */
    fun slideStreak(ctx: Context): Int = sp(ctx).getInt(KEY_SLIDE_STREAK, 0)
    fun setSlideStreak(ctx: Context, n: Int) { sp(ctx).edit().putInt(KEY_SLIDE_STREAK, n).apply() }

    fun removeDocument(ctx: Context, id: String) {
        val s = sp(ctx)
        val ids = s.getStringSet(KEY_DOCS, null)?.toMutableSet() ?: mutableSetOf()
        ids.remove(id)
        s.edit()
            .putStringSet(KEY_DOCS, ids)
            .remove(KEY_PREFIX_TITLE + id)
            .remove(KEY_PREFIX_TEXT + id)
            .remove(KEY_PREFIX_ADDED + id)
            .remove(KEY_PREFIX_PROGRESS + id)
            .apply()
    }
}
