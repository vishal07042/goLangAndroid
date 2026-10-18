package com.example.goenforcer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Tests for the 7:00 AM trigger math and UI-state reducers. */
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

    @Test
    fun `before 7am triggers same day at 7am`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(calendarAt(6, 59))
        assertEquals(7, hourOf(trigger))
    }

    @Test
    fun `exactly 7am rolls to tomorrow`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(calendarAt(7, 0, 30))
        assertEquals(7, hourOf(trigger))
        val days = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_MONTH)
        assertEquals(19, days) // Oct 18 + 1
    }

    @Test
    fun `evening rolls to next morning`() {
        val trigger = AffirmationScheduler.nextTriggerMillis(calendarAt(20, 0))
        assertEquals(7, hourOf(trigger))
    }

    @Test
    fun `trigger is always in the future`() {
        for (h in 0..23) {
            assertTrue(
                "trigger must be future for hour $h",
                AffirmationScheduler.nextTriggerMillis(calendarAt(h, 30)) >
                    calendarAt(h, 30).timeInMillis
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
    fun `enforcement status text reflects permission service and done state`() {
        val locked = HomeUiState(
            affirmationDone = false, wssGranted = false, serviceEnabled = false
        )
        assertTrue(locked.enforcementStatus.contains("adb shell pm grant"))

        val armed = HomeUiState(
            affirmationDone = false, wssGranted = true, serviceEnabled = false
        )
        assertTrue(armed.enforcementStatus.contains("7:00 AM"))

        val enforcing = HomeUiState(
            affirmationDone = false, wssGranted = true, serviceEnabled = true
        )
        assertTrue(enforcing.enforcementStatus.contains("Enforcing now"))

        val done = HomeUiState(
            affirmationDone = true, wssGranted = true, serviceEnabled = false
        )
        assertTrue(done.enforcementStatus.contains("written today"))
    }
}
