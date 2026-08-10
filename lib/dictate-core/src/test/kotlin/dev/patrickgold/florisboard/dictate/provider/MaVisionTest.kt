package dev.patrickgold.florisboard.dictate.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reply cleaning. Every case here is something a vision model actually does when asked to read a
 * screenshot, and every one of them would otherwise be spoken aloud.
 */
class MaVisionTest {

    @Test
    fun theHabitualPreambleIsStripped() {
        assertEquals(
            "Dobar dan.",
            MaVision.extractText("Here is the text from the image:\nDobar dan."),
        )
        assertEquals(
            "Dobar dan.",
            MaVision.extractText("The extracted text:\nDobar dan."),
        )
    }

    /**
     * A long opening line is the text, not a preamble. This is the case that makes a naive
     * "drop the first line" rule eat the first sentence of an article.
     */
    @Test
    fun arealFirstLineIsNeverMistakenForAPreamble() {
        val article = "The text of a screenshot is not always short and this first line is the " +
            "beginning of the body itself:\nand it continues here."
        assertEquals(article, MaVision.extractText(article))

        // No colon, so not a preamble however short.
        assertEquals("Here is Zagreb\nand Split.", MaVision.extractText("Here is Zagreb\nand Split."))
    }

    @Test
    fun aFencedReplyIsUnwrappedRatherThanRead() {
        assertEquals(
            "fun main() {\n    println(\"hi\")\n}",
            MaVision.extractText("```kotlin\nfun main() {\n    println(\"hi\")\n}\n```"),
        )
    }

    /** Told to return nothing, models often say "nothing" instead. Both mean nothing. */
    @Test
    fun anEmptyScreenshotComesBackEmpty() {
        assertEquals("", MaVision.extractText(null))
        assertEquals("", MaVision.extractText("   "))
        assertEquals("", MaVision.extractText("Nothing"))
        assertEquals("", MaVision.extractText("no readable text"))
    }

    @Test
    fun ordinaryTextIsLeftCompletelyAlone() {
        val text = "Prvi red.\n\nDrugi red, s praznim redom iznad."
        assertEquals(text, MaVision.extractText(text))
    }

    /**
     * The prompt is the feature, so its content is asserted rather than left to drift. Each of these
     * words is load bearing: without them the model reads the clock, the keyboard and the nav bar.
     */
    @Test
    fun thePromptStillForbidsTheInterface() {
        val p = MaVision.OCR_PROMPT.lowercase()
        for (word in listOf("status bar", "keyboard", "navigation bar", "battery", "eng", "clock")) {
            assertTrue(p.contains(word), "prompt no longer excludes: $word")
        }
        // And still forbids the two things that ruin a reading aloud.
        assertTrue(p.contains("do not write any introduction"))
        assertTrue(p.contains("do not describe the image"))
        assertTrue(p.contains("do not translate"))
    }
}
