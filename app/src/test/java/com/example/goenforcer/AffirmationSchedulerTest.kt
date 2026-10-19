package com.example.goenforcer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Tests for the 7:00 AM / 7:00 PM trigger math and UI-state reducers. */
class AffirmationSchedulerTest {

    private fun calendarAt(hour: Int, minute: Int, second: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 18)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    // ── Morning window (7 AM) ────────────────────────────────────────
    @Test
    fun `before 7am triggers same day at 7am`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(pm = false, now = calendarAt(6, 59))
        assertEquals(7, hourOf(trigger))
    }

    @Test
    fun `exactly 7am rolls to tomorrow`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(pm = false, now = calendarAt(7, 0, 30))
        assertEquals(7, hourOf(trigger))
        val days = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_MONTH)
        assertEquals(19, days) // Oct 18 + 1
    }

    @Test
    fun `evening rolls to next morning`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(pm = false, now = calendarAt(20, 0))
        assertEquals(7, hourOf(trigger))
    }

    // ── Evening window (7 PM) ─────────────────────────────────────────
    @Test
    fun `before 7pm triggers same day at 19h`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(pm = true, now = calendarAt(12, 0))
        assertEquals(19, hourOf(trigger))
    }

    @Test
    fun `after 7pm rolls to tomorrow evening`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(pm = true, now = calendarAt(21, 0))
        assertEquals(19, hourOf(trigger))
        val days = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_MONTH)
        assertEquals(19, days)
    }

    @Test
    fun `am and pm triggers are distinct`() {
        val now = calendarAt(6, 0)
        val am = AffirmationScheduler.nextTriggerMillis(pm = false, now = now)
        val pm = AffirmationScheduler.nextTriggerMillis(pm = true, now = now)
        assertTrue(pm > am)
        assertEquals(7, hourOf(am))
        assertEquals(19, hourOf(pm))
    }

    @Test
    fun `trigger is always in the future for every hour`() {
        for (h in 0..23) {
            val now = calendarAt(h, 30)
            assertTrue(
                "AM trigger must be future for hour $h",
                AffirmationScheduler.nextTriggerMillis(pm = false, now = now) > now.timeInMillis
            )
            assertTrue(
                "PM trigger must be future for hour $h",
                AffirmationScheduler.nextTriggerMillis(pm = true, now = now) > now.timeInMillis
            )
        }
    }
}

/** Pure reducer/derived-state tests for the MVVM UI states. */
class UiStateTest {

    @Test
    fun `affirmation state progress and counts derive from session`() {
        val words = listOf("I", "am", "focused")
        val mid = AffirmationUiState(
            session = Session(words = words, wordIndex = 1, typedInWord = 2),
            correctChars = 3, wrongKeys = 1
        )
        assertEquals(3, mid.wordsTotal)
        assertEquals(1, mid.wordsDone)
        assertEquals(2, mid.wordsToGo)
        assertEquals(1f / 3f, mid.progress, 0.0001f)
        assertEquals(75, mid.accuracyPercent) // 3 / (3+1)

        val done = AffirmationUiState(session = Session(words = words, wordIndex = 3))
        assertEquals(3, done.wordsDone)
        assertEquals(0, done.wordsToGo)
        assertEquals(1f, done.progress, 0.0001f)
    }

    @Test
    fun `accuracy is 100 with no mistakes and 0 with no input`() {
        assertEquals(100, AffirmationUiState(correctChars = 5, wrongKeys = 0).accuracyPercent)
        assertEquals(0, AffirmationUiState().accuracyPercent)
    }

    @Test
    fun `activePm resolves am first then pm then null`() {
        val noneDone = HomeUiState(morningDone = false, eveningDone = false)
        assertEquals(false, noneDone.activePm)

        val amDone = HomeUiState(morningDone = true, eveningDone = false)
        assertEquals(true, amDone.activePm)

        val allDone = HomeUiState(morningDone = true, eveningDone = true)
        assertEquals(null, allDone.activePm)
    }

    @Test
    fun `enforcement status reflects permission service and windows`() {
        val pending = HomeUiState(
            morningDone = false, eveningDone = false, wssGranted = false, serviceEnabled = false
        )
        assertTrue(pending.enforcementStatus.contains("adb shell pm grant"))
        assertTrue(pending.windowLabel.contains("7:00 AM"))

        val armed = HomeUiState(
            morningDone = true, eveningDone = false, wssGranted = true, serviceEnabled = false
        )
        assertTrue(armed.enforcementStatus.contains("7:00 PM"))
        assertTrue(armed.windowLabel.contains("7:00 PM"))

        val enforcing = HomeUiState(
            morningDone = false, eveningDone = false, wssGranted = true, serviceEnabled = true
        )
        assertTrue(enforcing.enforcementStatus.contains("Enforcing now"))

        val done = HomeUiState(
            morningDone = true, eveningDone = true, wssGranted = true, serviceEnabled = false
        )
        assertTrue(done.enforcementStatus.contains("Both sessions"))
        assertTrue(done.windowLabel.contains("complete"))
    }
}

/** The bank itself: user-authored list, pick mechanics. */
class AffirmationBankTest {

    @Test
    fun `bank has the authored affirmations and no empties`() {
        // Count-free on purpose: the user keeps authoring new affirmations.
        assertTrue(AffirmationStore.BANK.size >= 3)
        assertTrue(AffirmationStore.BANK.all { it.isNotBlank() })
        assertTrue(AffirmationStore.BANK.distinct().size == AffirmationStore.BANK.size)
    }

    @Test
    fun `pick count is three`() {
        assertEquals(3, AffirmationStore.PICK_COUNT)
        assertTrue(AffirmationStore.PICK_COUNT <= AffirmationStore.BANK.size)
    }
}
