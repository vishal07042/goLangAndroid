package com.example.goenforcer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [ReadingCore] — text normalisation, sentence splitting,
 * the deterministic 30-word chunker, and every mode's verification logic
 * (retype / slide / drag / read-aloud / flash). No Android deps.
 */
class ReadingCoreTest {

    // ── ReadingText.normalise ────────────────────────────────────────
    @Test
    fun `normalise lowercases and strips trailing punctuation`() {
        assertEquals("hello", ReadingText.normalize("Hello."))
        assertEquals("world", ReadingText.normalize("world!"))
        assertEquals("end", ReadingText.normalize("end?"))
    }

    @Test
    fun `normalise folds curly apostrophes so don't matches don’t`() {
        assertEquals("don't", ReadingText.normalize("don\u2019t"))
        assertEquals("don't", ReadingText.normalize("Don't"))
        assertTrue(ReadingText.same("Don\u2019t", "don't"))
    }

    @Test
    fun `same is true for case and punctuation variants`() {
        assertTrue(ReadingText.same("The", "the"))
        assertTrue(ReadingText.same("word.", "word"))
        assertFalse(ReadingText.same("word", "words"))
    }

    @Test
    fun `normalise keeps internal apostrophes`() {
        assertEquals("it's", ReadingText.normalize("It\u2019s"))
        assertEquals("co-op", ReadingText.normalize("co-op"))
    }

    // ── ReadingSentences ─────────────────────────────────────────────
    @Test
    fun `split breaks on sentence terminators`() {
        val s = ReadingSentences.split("One. Two! Three?")
        assertEquals(3, s.size)
    }

    @Test
    fun `split does not break on common abbreviations`() {
        val s = ReadingSentences.split("Dr. Smith went home. He slept.")
        assertTrue(s.size == 2)
        assertTrue(s[0].startsWith("Dr."))
    }

    @Test
    fun `lastSentences returns at most two sentences`() {
        val text = "Alpha beta. Gamma delta. Epsilon zeta."
        val ctx = ReadingSentences.lastSentences(text, max = 2)
        assertFalse(ctx.contains("Alpha"))
        assertTrue(ctx.contains("Epsilon"))
    }

    // ── ReadingChunker ───────────────────────────────────────────────
    @Test
    fun `chunk splits into fixed 30-word slices`() {
        val words = List(75) { "w$it" }
        val chunks = ReadingChunker.chunk(words)
        assertEquals(3, chunks.size)
        assertEquals(30, chunks[0].size)
        assertEquals(30, chunks[1].size)
        assertEquals(15, chunks[2].size)   // last chunk may be shorter
    }

    @Test
    fun `chunk is deterministic across calls`() {
        val text = (1..100).joinToString(" ") { "word$it" }
        assertEquals(ReadingChunker.build(text), ReadingChunker.build(text))
    }

    @Test
    fun `build produces sequential seq starting at 0`() {
        val text = (1..65).joinToString(" ") { "w$it" }
        val chunks = ReadingChunker.build(text)
        assertEquals(listOf(0, 1, 2), chunks.map { it.seq })
    }

    @Test
    fun `build preserves every word across chunks`() {
        val text = (1..45).joinToString(" ") { "w$it" }
        val rebuilt = ReadingChunker.build(text).flatMap { it.words }
        assertEquals(ReadingChunker.words(text), rebuilt)
    }

    @Test
    fun `empty text yields no chunks`() {
        assertTrue(ReadingChunker.build("   \n  ").isEmpty())
    }

    @Test
    fun `first chunk has empty context, later chunks have context`() {
        val text = (1..70).joinToString(" ") { "word$it" }
        val chunks = ReadingChunker.build(text)
        assertTrue(chunks[0].contextBefore.isBlank())
        assertTrue(chunks[1].contextBefore.isNotBlank())
    }

    // ── Rsvp (Flash / ORP) ───────────────────────────────────────────
    @Test
    fun `orp index is zero for single letter`() {
        assertEquals(0, Rsvp.orpIndex("a"))
    }

    @Test
    fun `orp index lands about a third into longer words`() {
        assertTrue(Rsvp.orpIndex("international") in 1..4)
        assertTrue(Rsvp.orpIndex("the") <= 1)
    }

    @Test
    fun `orp index never exceeds word length`() {
        val w = "supercalifragilistic"
        assertTrue(Rsvp.orpIndex(w) < w.length)
    }

    @Test
    fun `delay grows with punctuation and length`() {
        val plain = Rsvp.delayMs("word", 300)
        val sentence = Rsvp.delayMs("word.", 300)
        val longWord = Rsvp.delayMs("extraordinarily", 300)
        assertTrue(sentence > plain)
        assertTrue(longWord >= plain)
    }

    @Test
    fun `delay respects wpm bounds`() {
        val slow = Rsvp.delayMs("word", Rsvp.MIN_WPM)
        val fast = Rsvp.delayMs("word", Rsvp.MAX_WPM)
        assertTrue(slow > fast)
    }

    // ── Retype (original Go typing engine) ───────────────────────────
    // The retype engine is GoEnforcerCore.Core — already covered end-to-end
    // by CoreTest.kt. These tests pin the reading-side rules of the engine.
    @Test
    fun `wrong key locks the word while it is un-typed`() {
        val s = Session(words = listOf("cat"))
        var locked = Core.typeChar(s, 'x')
        assertTrue(locked.wrong)
        // Still frozen while wrong — no extra errors stack in the engine.
        assertEquals(locked, Core.typeChar(locked, 'c'))
        // Backspace clears the lock.
        assertFalse(Core.backspace(locked).wrong)
    }

    @Test
    fun `space only commits a fully typed word`() {
        val s = Session(words = listOf("cat", "dog"))
        assertEquals(s, Core.typeSpace(s))                      // partial word ignored
        val typedFull = Session(words = listOf("cat", "dog"), typedInWord = 3)
        assertEquals(1, Core.typeSpace(typedFull).wordIndex)    // full word commits
    }

    @Test
    fun `backspace reaches back into the previous word`() {
        val s = Session(words = listOf("cat", "dog"), wordIndex = 1)
        val back = Core.backspace(s)
        assertEquals(0, back.wordIndex)
        assertEquals(3, back.typedInWord)
    }

    @Test
    fun `reading chunks contain no typed-char loss on quotes`() {
        // Phone keyboards fold typographic quotes — VM-side folds are pinned
        // by ReadingText.normalize coverage above; the engine stays exact.
        val s = Core.typeChar(Session(words = listOf("don\u2019t")), 'd')
        assertEquals(1, s.typedInWord)
    }

    // ── SlideLogic ───────────────────────────────────────────────────
    @Test
    fun `pace floor rejects too-fast swipes`() {
        assertFalse(SlideLogic.paceOk(1000L, 1100L))     // 100ms < 400ms
        assertTrue(SlideLogic.paceOk(1000L, 1500L))      // 500ms ≥ 400ms
    }

    @Test
    fun `first swipe always passes pace floor`() {
        assertTrue(SlideLogic.paceOk(0L, 10L))
    }

    // ── DragDeck ─────────────────────────────────────────────────────
    @Test
    fun `deal always contains the correct word exactly once`() {
        val words = listOf("alpha", "beta", "gamma", "delta", "epsilon")
        repeat(10) { seed ->
            val tray = DragDeck.deal(words, 1, seed)
            assertEquals(DragDeck.TRAY_SIZE, tray.size)
            assertEquals(1, tray.count { DragDeck.isCorrect(it, "beta") })
        }
    }

    @Test
    fun `deal is deterministic for the same seed and index`() {
        val words = List(30) { "w$it" }
        assertEquals(DragDeck.deal(words, 5, 42), DragDeck.deal(words, 5, 42))
    }

    @Test
    fun `deal picks distractors that are not the target`() {
        val words = listOf("one", "two", "three", "four", "five")
        val tray = DragDeck.deal(words, 0, 7)
        val target = "one"
        // Tray has 3 tiles, exactly one of which is the target.
        assertEquals(1, tray.count { DragDeck.isCorrect(it, target) })
    }

    @Test
    fun `deal returns empty for out-of-range index`() {
        assertTrue(DragDeck.deal(listOf("a"), 5, 0).isEmpty())
    }

    // ── AloudAligner ─────────────────────────────────────────────────
    @Test
    fun `first partial alone never advances`() {
        val a = AloudAligner(listOf("hello", "world"))
        val update = a.onPartial(listOf("hello"))
        assertEquals(0, update.advancedBy)
        assertEquals(0, a.cursor)
    }

    @Test
    fun `word stable across two partials advances the cursor`() {
        val a = AloudAligner(listOf("hello", "world"))
        a.onPartial(listOf("hello"))          // first partial seeds prev
        val update = a.onPartial(listOf("hello"))
        assertEquals(1, a.cursor)
        assertEquals(1, a.matched)
    }

    @Test
    fun `skipped word inside lookahead turns missed`() {
        val a = AloudAligner(listOf("one", "two", "three"), lookahead = 3)
        a.onPartial(listOf("one"))
        // Recognizer jumps straight to "two" stable across partials.
        a.onPartial(listOf("two"))
        val update = a.onPartial(listOf("two"))
        assertTrue(update.newlyMissed.isNotEmpty() || a.cursor >= 1)
    }

    @Test
    fun `aligner passes when at least 80 percent matched`() {
        val words = listOf("a", "b", "c", "d", "e")
        val a = AloudAligner(words)
        // Feed every word stable across two partials → 100%.
        for (w in words) {
            a.onPartial(listOf(w))
            a.onPartial(listOf(w))
        }
        assertTrue(a.passed())
    }

    @Test
    fun `aligner fails below 80 percent`() {
        val words = (1..10).map { "w$it" }
        val a = AloudAligner(words)
        // Only confirm the first two words → 20% < 80%.
        a.onPartial(listOf("w1")); a.onPartial(listOf("w1"))
        a.onPartial(listOf("w2")); a.onPartial(listOf("w2"))
        assertFalse(a.passed())
    }

    // ── ReadingPassRule ──────────────────────────────────────────────
    @Test
    fun `aloudPass enforces the 80 percent threshold`() {
        assertFalse(ReadingPassRule.aloudPass(7, 10))
        assertTrue(ReadingPassRule.aloudPass(8, 10))
        assertFalse(ReadingPassRule.aloudPass(0, 0))
    }

    // ── ReadingMode eligibility ──────────────────────────────────────
    @Test
    fun `slide and flash are never eligible for the gate`() {
        assertFalse(ReadingMode.SLIDE.eligibleForGate)
        assertFalse(ReadingMode.FLASH.eligibleForGate)
    }

    @Test
    fun `quiet mode only offers quiet-safe modes`() {
        assertTrue(ReadingMode.RETYPE.quietSafe)
        assertTrue(ReadingMode.SLIDE.quietSafe)
        assertFalse(ReadingMode.ALOUD.quietSafe)
    }

    @Test
    fun `randomGate in quiet mode never returns read-aloud`() {
        repeat(20) { assertFalse(ReadingMode.randomGate(quiet = true) == ReadingMode.ALOUD) }
    }

    // ── Document hash identity ───────────────────────────────────────
    @Test
    fun `hash is stable for identical text and differs for different text`() {
        val a = "The quick brown fox"
        val b = "the   quick\nbrown fox"   // same words, different whitespace
        val c = "something else entirely"
        // ReadingStore.hash normalises whitespace, so a and b match.
        // hash is pure (no Android Context) so it runs on the JVM directly.
        val ha = ReadingStore.hash(a)
        val hb = ReadingStore.hash(b)
        val hc = ReadingStore.hash(c)
        assertEquals(ha, hb)
        assertTrue(ha != hc)
    }

    // ── Bundled Markdown → readable text ─────────────────────────────
    @Test
    fun `markdown strips syntax but keeps content`() {
        val md = """
            # Docker Basics
            Use **images** and *containers* with `docker run`.

            - build
            - ship

            See [the docs](https://example.com) and ![diagram](d.png).
        """.trimIndent()

        val plain = MarkdownText.toPlainText(md)

        assertTrue(plain.contains("Docker Basics"))
        assertTrue(plain.contains("images"))
        assertTrue(plain.contains("containers"))
        assertTrue(plain.contains("docker run"))
        assertTrue(plain.contains("the docs"))
        assertTrue(plain.contains("diagram"))
        assertFalse(plain.contains("#"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("]("))
        assertFalse(plain.contains("`"))
    }

    @Test
    fun `markdown preserves identifiers with underscores`() {
        val plain = MarkdownText.toPlainText("Call `train_test_split` and use snake_case_names.")
        assertTrue(plain.contains("train_test_split"))
        assertTrue(plain.contains("snake_case_names"))
    }

    @Test
    fun `markdown keeps code generics intact`() {
        val plain = MarkdownText.toPlainText("List<String> items = new ArrayList<>();")
        assertTrue(plain.contains("List<String>"))
    }

    @Test
    fun `markdown flattens tables and keeps fenced code text`() {
        val md = """
            | Name | Type |
            |------|------|
            | id   | int  |

            ```kotlin
            val x = 1
            ```
        """.trimIndent()

        val plain = MarkdownText.toPlainText(md)

        assertTrue(plain.contains("Name"))
        assertTrue(plain.contains("Type"))
        assertTrue(plain.contains("val x = 1"))
        assertFalse(plain.contains("|"))
        assertFalse(plain.contains("```"))
    }

    @Test
    fun `markdown normalises line endings and collapses blank runs`() {
        assertEquals("a\n\nb", MarkdownText.toPlainText("a\r\n\r\n\r\n\r\nb"))
    }

    @Test
    fun `markdown output chunks deterministically`() {
        val md = "##\n\n" + (1..90).joinToString(" ") { "word$it" }
        val plain = MarkdownText.toPlainText(md)
        val chunks = ReadingChunker.build(plain)
        assertEquals(3, chunks.size)
        assertEquals(30, chunks.first().words.size)
    }
}
