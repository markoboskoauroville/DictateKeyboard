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

import dev.patrickgold.florisboard.app.FlorisPreferenceStore

/**
 * The instructions spoken to the little man, kept so they can be used again without speaking them.
 *
 * The same handful of instructions get spoken over and over: rewrite this as numbers, make it
 * shorter, translate it, fix the punctuation. Saying one aloud costs a recording, an upload and a
 * wait, every single time, to send a sentence that was sent yesterday. Long-pressing the little man
 * brings the list up and one tap runs it against whatever is in the field now.
 *
 * Deliberately not the same thing as the transcription history. That archive is about the words
 * produced; this is about the instructions given, which are short, repeat constantly and are useless
 * mixed in among transcripts.
 */
object MaLivePrompts {

    /** Kept at twenty. Beyond that the list stops being scannable and becomes a search problem. */
    private const val LIMIT = 20

    private const val SEP = "\u001F"

    /** Most recent first. */
    fun list(): List<String> {
        val prefs by FlorisPreferenceStore
        return prefs.dictate.maLivePromptHistory.get()
            .split(SEP)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Records an instruction, newest first, without duplicates.
     *
     * An instruction used again moves back to the top rather than appearing twice, because a list
     * where the same sentence occupies four of twenty slots is a worse list.
     */
    suspend fun remember(instruction: String) {
        val text = instruction.trim()
        if (text.isEmpty()) return
        // Very long dictations are not instructions; they are someone forgetting to switch modes.
        if (text.length > 200) return
        val prefs by FlorisPreferenceStore
        val kept = LinkedHashSet<String>()
        kept.add(text)
        list().forEach { if (kept.size < LIMIT) kept.add(it) }
        prefs.dictate.maLivePromptHistory.set(kept.joinToString(SEP))
    }

    /**
     * Writes the whole line back, in the order given.
     *
     * Needed because editing a wagon must keep its place, and every other path here either prepends
     * or removes. Trimmed, emptied of blanks and de-duplicated on the way in, so a caller cannot
     * store a list this object would not have produced itself.
     */
    suspend fun replaceAll(instructions: List<String>) {
        val prefs by FlorisPreferenceStore
        val kept = instructions.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(LIMIT)
        prefs.dictate.maLivePromptHistory.set(kept.joinToString(SEP))
    }

    /** Forgets one instruction. */
    suspend fun forget(instruction: String) {
        val prefs by FlorisPreferenceStore
        val kept = list().filterNot { it == instruction }
        prefs.dictate.maLivePromptHistory.set(kept.joinToString(SEP))
    }

    suspend fun clear() {
        val prefs by FlorisPreferenceStore
        prefs.dictate.maLivePromptHistory.set("")
    }
}
