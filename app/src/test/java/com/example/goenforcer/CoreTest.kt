package com.example.goenforcer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Core] — the pure typing engine shared by the Go
 * Challenge and the Affirmation Enforcer. No Android deps, so these run
 * as plain JVM tests (`./gradlew :app:testDebugUnitTest`).
 */
class CoreTest {

    private val SEP = '\u001f'

    // ── decodeSegments ───────────────────────────────────────────────
    @Test
    fun `decodes mixed comment boilerplate and code segments`() {
        val packed = "Bpackage main\n${SEP}C// a comment\n${SEP}Kfunc main() {}"
        val segs = Core.decodeSegments(packed)

        assertEquals(3, segs.size)
        assertTrue(segs[0].boilerplate)
        assertFalse(segs[0].comment)
        assertTrue(segs[1].comment)
        assertFalse(segs[1].boilerplate)
        assertFalse(segs[2].comment)
        assertFalse(segs[2].boilerplate)
        assertEquals("func main() {}", segs[2].text)
    }

    @Test
    fun `empty packed string yields no segments`() {
        assertTrue(Core.decodeSegments("").isEmpty())
    }

    // ── expectedInput ────────────────────────────────────────────────
    @Test
    fun `expectedInput skips comments and boilerplate`() {
        val packed = "Bpackage main\n${SEP}C// note\n${SEP}Kx := 1"
        val expected = Core.expectedInput(Core.decodeSegments(packed))
        assertEquals("x := 1", expected)
    }

    // ── splitWords ───────────────────────────────────────────────────
    @Test
    fun `splitWords collapses whitespace and drops empties`() {
        assertEquals(listOf("a", "b", "c"), Core.splitWords("  a \n\t b   c "))
        assertTrue(Core.splitWords("   ").isEmpty())
    }

    // ── typeChar / typeSpace / isComplete ────────────────────────────
    private fun sessionFor(words: String) =
        Core.newSession(listOf(Segment(text = words)))

    @Test
    fun `correct chars advance typedInWord`() {
        var s = sessionFor("hi")
        s = Core.typeChar(s, 'h')
        assertEquals(1, s.typedInWord)
        assertFalse(s.wrong)
        s = Core.typeChar(s, 'i')
        assertEquals(2, s.typedInWord)
    }

    @Test
    fun `wrong char marks session wrong and further typing is ignored`() {
        var s = sessionFor("hi")
        s = Core.typeChar(s, 'x')
        assertTrue(s.wrong)
        val after = Core.typeChar(s, 'h')
        assertEquals(0, after.typedInWord) // blocked while wrong
    }

    @Test
    fun `space only advances when word fully typed`() {
        var s = sessionFor("hi there")
        s = Core.typeChar(s, 'h')
        s = Core.typeSpace(s)
        assertEquals(0, s.wordIndex) // incomplete word: space is a no-op

        s = Core.typeChar(s, 'i')
        s = Core.typeSpace(s)
        assertEquals(1, s.wordIndex)
        assertEquals(0, s.typedInWord)
    }

    @Test
    fun `completes exactly when all words typed`() {
        var s = sessionFor("a b")
        assertFalse(Core.isComplete(s))
        s = Core.typeChar(s, 'a'); s = Core.typeSpace(s)
        assertFalse(Core.isComplete(s))
        s = Core.typeChar(s, 'b')
        assertTrue(Core.isComplete(s))
    }

    // ── backspace ────────────────────────────────────────────────────
    @Test
    fun `backspace clears the wrong flag first`() {
        var s = sessionFor("hi")
        s = Core.typeChar(s, 'x')   // wrong
        s = Core.backspace(s)
        assertFalse(s.wrong)
    }

    @Test
    fun `backspace walks back into the previous word`() {
        var s = sessionFor("ab cd")
        s = Core.typeChar(s, 'a'); s = Core.typeChar(s, 'b'); s = Core.typeSpace(s)
        s = Core.backspace(s)
        assertEquals(0, s.wordIndex)
        assertEquals(2, s.typedInWord) // full previous word restored
    }

    // ── affirmation wrapping ─────────────────────────────────────────
    @Test
    fun `wrappedSegments preserves every word of a long affirmation`() {
        val text = "I am disciplined and I finish what I start every single morning"
        val segs = AffirmationStore.wrappedSegments(text, width = 12)
        val rebuilt = segs.joinToString(" ") { it.text.replace('\n', ' ') }
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        assertEquals(text.split(" "), rebuilt)
    }

    @Test
    fun `wrappedSegments keeps short text on one line`() {
        val segs = AffirmationStore.wrappedSegments("I am calm.", width = 40)
        assertEquals(1, segs.size)
        assertFalse(segs[0].text.contains('\n'))
    }

    // ── full affirmation typing loop (integration on the engine) ─────
    @Test
    fun `typing an entire wrapped affirmation word by word completes`() {
        val text = "I build the life I want"
        val segs = AffirmationStore.wrappedSegments(text, width = 10)
        var s = Core.newSession(segs)
        for (word in text.split(" ")) {
            for (ch in word) s = Core.typeChar(s, ch)
            s = Core.typeSpace(s)
        }
        assertTrue(Core.isComplete(s))
    }
}
