/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate

import java.util.Locale

/**
 * The four letter cases, used for two jobs that are really one.
 *
 * A case button decides how the next dictation is written, and rewrites whatever is already in the
 * field. Those look like separate features but they are the same question asked at two moments, so
 * they share one transformation: whatever the button would have done to text arriving is exactly
 * what it does to text already there.
 *
 * Locale-aware throughout. Croatian has no dotted-i problem but Turkish does, and a keyboard that
 * lowercases "I" into the wrong letter in one language has a bug waiting rather than a feature.
 */
object MaCase {

    const val AUTO = "auto"
    const val NONE = "none"
    const val LOWER = "lower"
    const val UPPER = "upper"
    const val SENTENCE = "sentence"
    const val TITLE = "title"

    /** Words that stay lowercase inside a title unless they open or close it. */
    private val TITLE_MINOR = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "into", "nor", "of", "on",
        "onto", "or", "over", "per", "the", "to", "up", "via", "with", "vs",
        // Croatian equivalents, since half of what is dictated here is Croatian.
        "i", "pa", "te", "ni", "ili", "a", "u", "na", "za", "od", "do", "sa", "iz", "po", "uz", "o",
    )

    /**
     * Applies [mode] to [text].
     *
     * [AUTO] and [NONE] return the text untouched: the first is decided at transcription time by
     * sentence count and means nothing once the words exist, the second means leave it alone.
     */
    fun transform(text: String, mode: String, locale: Locale = Locale.getDefault()): String =
        when (mode) {
            LOWER -> text.lowercase(locale)
            UPPER -> text.uppercase(locale)
            SENTENCE -> toSentenceCase(text, locale)
            TITLE -> toTitleCase(text, locale)
            else -> text
        }

    /**
     * First letter of every sentence capitalised, the rest lowered.
     *
     * A sentence starts at the beginning and after any of . ! ? or a line break. Runs of punctuation
     * and quotation marks are stepped over, so a quoted sentence opens with a capital on the letter
     * rather than on the quote mark.
     */
    private fun toSentenceCase(text: String, locale: Locale): String {
        val out = StringBuilder(text.length)
        var startOfSentence = true
        for (ch in text) {
            when {
                ch == '.' || ch == '!' || ch == '?' || ch == '\n' -> {
                    startOfSentence = true
                    out.append(ch)
                }
                ch.isLetter() || ch.isDigit() -> {
                    if (startOfSentence) {
                        out.append(ch.uppercaseChar())
                        startOfSentence = false
                    } else {
                        out.append(ch.lowercaseChar())
                    }
                }
                // Spaces, quotes and brackets between the full stop and the first letter do not end
                // the wait for that letter.
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Every word capitalised except the short joining words, which stay lowercase unless they are
     * first or last.
     *
     * Hyphenated words are capitalised on both halves, since "Well-Known" is what a title looks like
     * and "Well-known" reads as a sentence that wandered in.
     */
    private fun toTitleCase(text: String, locale: Locale): String {
        val tokens = text.split(" ")
        val lastRealIndex = tokens.indexOfLast { it.any { c -> c.isLetterOrDigit() } }
        return tokens.mapIndexed { index, token ->
            if (token.isEmpty()) return@mapIndexed token
            val bare = token.trim { !it.isLetterOrDigit() }.lowercase(locale)
            val minor = bare in TITLE_MINOR && index != 0 && index != lastRealIndex
            if (minor) token.lowercase(locale) else capitaliseParts(token, locale)
        }.joinToString(" ")
    }

    /** Capitalises the first letter of each hyphen- or slash-separated part of one word. */
    private fun capitaliseParts(token: String, locale: Locale): String {
        val out = StringBuilder(token.length)
        var atStart = true
        for (ch in token.lowercase(locale)) {
            if (ch == '-' || ch == '/' || ch == '\u2019' || ch == '\'') {
                // An apostrophe does not start a new word: "don't" must not become "Don'T".
                out.append(ch)
                atStart = ch == '-' || ch == '/'
            } else if (atStart && ch.isLetter()) {
                out.append(ch.uppercaseChar())
                atStart = false
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }
}
