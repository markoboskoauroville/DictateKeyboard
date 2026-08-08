/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
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

/**
 * What each digit on the number row types when it is held.
 *
 * Ten slots, one per digit, in printed order: 1 to 9 and then 0. A slot may hold any string, or be
 * empty for a key with nothing behind it.
 *
 * Stored as the ten slots joined by a unit separator rather than by a comma. A second symbol is
 * exactly the kind of thing somebody assigns a comma to, and a separator that can appear inside a
 * value is a corruption waiting to happen. U+001F cannot be typed, so it can never collide with
 * what is stored.
 *
 * Parsing never fails and never throws. A stored string of the wrong length is padded or trimmed to
 * ten, because a preference written by an older build, or by hand, should degrade to something
 * usable rather than take the keyboard down with it.
 */
object MaNumericSecondary {
    /** How many slots there are, one per digit. */
    const val COUNT = 10

    /** The digits in printed order, so an editor can label its fields without guessing. */
    val LABELS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private const val SEPARATOR = '\u001F'

    /**
     * The underscore on all ten, which is the reason this exists.
     *
     * It is the character that costs three layout switches to reach and appears constantly in
     * scripts and file names. Putting it under every digit means it is always under a thumb
     * wherever that thumb happens to be, and there is no tenth key to remember.
     */
    val DEFAULT: String = List(COUNT) { "_" }.joinToString(SEPARATOR.toString())

    /** The ten slots, always exactly ten, whatever [raw] holds. */
    fun parse(raw: String): List<String> {
        val parts = raw.split(SEPARATOR)
        return List(COUNT) { index -> parts.getOrNull(index)?.let { sanitize(it) } ?: "" }
    }

    /** [slots] back to a stored string, padded or trimmed to exactly ten. */
    fun serialize(slots: List<String>): String =
        List(COUNT) { index -> sanitize(slots.getOrNull(index) ?: "") }
            .joinToString(SEPARATOR.toString())

    /**
     * One slot's value, cleaned.
     *
     * Line breaks and the separator itself are removed, since neither can survive a round trip, and
     * the length is capped so a paste of a whole paragraph cannot become a key. Everything else is
     * left exactly as typed.
     */
    fun sanitize(value: String): String =
        value.replace("\n", "").replace("\r", "").replace(SEPARATOR.toString(), "").take(16)
}
