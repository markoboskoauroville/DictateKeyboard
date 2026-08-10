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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The language of this app, in one place.
 *
 * There were two languages before, and they were allowed to disagree. One decided what the speech
 * service was told it was listening to, the other decided which dictionary the suggestions came
 * from, and each had its own control. Two controls for one intention is one too many: nobody speaks
 * Croatian while wanting English suggestions, and the setup where that is possible is the setup
 * where one of the two is quietly wrong and there is no way to see which.
 *
 * So there is one switch and it moves both. Setting the language here sets the transcription hint
 * and the keyboard subtype together, and every control that changes language goes through this
 * object rather than writing either of them directly.
 *
 * Two languages, Croatian and English, because that is what this app is for. Auto-detect is not one
 * of them: it exists in the catalog for the settings screen, but the toggle deliberately cannot land
 * on it, since a toggle whose third state means "I do not know" cannot be operated without looking.
 */
object MaLanguage {
    const val HR = "hr"
    const val EN = "en"

    /** The two, in the order the toggle moves through them. */
    val PAIR = listOf(HR, EN)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * The active language, always one of [PAIR].
     *
     * Anything else, including auto-detect left over from an older install, reads as Croatian rather
     * than as itself, so a caller never has to handle a third case.
     */
    fun active(): String {
        val prefs by FlorisPreferenceStore
        val code = prefs.dictate.activeInputLanguage.get().substringBefore('-').lowercase()
        return if (code == EN) EN else HR
    }

    /**
     * The badge shown wherever the language is displayed: **HR** or **ENG**.
     *
     * Three letters for English and two for Croatian, which is deliberately uneven. `EN` and `HR`
     * are the same shape and the same weight, so at a glance in the corner of a moving keyboard they
     * read as "some code" rather than as a word, and telling them apart takes a moment of actual
     * reading. `ENG` does not look like `HR`. Marko asked for these two spellings by name and this is
     * why they are right.
     *
     * Display only. The stored codes stay `en` and `hr`, and nothing branches on this string.
     */
    fun badge(): String = if (active() == EN) "ENG" else "HR"

    /**
     * Sets the language for everything at once.
     *
     * The transcription hint is a preference, the suggestion language is a keyboard subtype, and
     * they are written together here so they cannot drift. A missing subtype is not an error worth
     * reporting: the transcription language still changes, and the suggestions simply stay where
     * they were until the subtype exists.
     */
    fun set(context: Context, code: String) {
        val target = if (code.substringBefore('-').lowercase() == EN) EN else HR
        val prefs by FlorisPreferenceStore
        scope.launch { prefs.dictate.activeInputLanguage.set(target) }
        val subtypeManager by context.subtypeManager()
        subtypeManager.subtypes
            .firstOrNull { it.primaryLocale.language.lowercase() == target }
            ?.let { subtypeManager.switchToSubtypeById(it.id) }
    }

    /** Moves to the other one. */
    fun toggle(context: Context) {
        set(context, if (active() == HR) EN else HR)
    }
}
