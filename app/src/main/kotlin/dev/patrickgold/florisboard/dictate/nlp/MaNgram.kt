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

package dev.patrickgold.florisboard.dictate.nlp

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the personal n-gram model: its file, when it learns, and when it is written out.
 *
 * One instance for the process. The model is small enough to hold in memory and too small to be
 * worth a database, so it lives as one file read once at start and written back on a delay.
 */
object MaNgram {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val model = MaNgramModel()

    @Volatile
    private var file: File? = null

    @Volatile
    private var loaded = false

    private var saveJob: Job? = null

    /**
     * Reads the model from disk, once.
     *
     * Safe to call repeatedly; the second call does nothing. Failure is silent by design: an
     * unreadable model is an empty model, which costs the user their learned words but not their
     * keyboard.
     */
    fun initialize(context: Context) {
        if (loaded) return
        loaded = true
        val target = File(context.filesDir, FILE_NAME)
        file = target
        scope.launch { model.load(target) }
    }

    /**
     * The sentence being written, assembled from single-character commits.
     *
     * Typing arrives one character at a time, so learning each commit as it came would fill the
     * model with single letters and nothing else. Characters are collected here and handed to the
     * model as whole sentences, which is the unit the counts are actually about. Dictation does not
     * pass through here: a transcript arrives complete and is learned as it stands.
     */
    private val pending = StringBuilder()

    private val pendingLock = Any()

    /**
     * Everything committed to the editor passes through here.
     *
     * A commit longer than one character is already a unit worth learning: a transcript, or a
     * suggestion accepted whole. A single character is a keystroke, and keystrokes are collected
     * until a sentence ends.
     *
     * A known limitation, and an accepted one: text typed and then deleted is still learned. Undoing
     * a count needs an edit history this has no access to, and the cost of the odd wrong word is one
     * count among thousands, which the ranking absorbs.
     */
    fun onCommit(text: String, isIncognito: Boolean) {
        if (isIncognito || text.isEmpty()) return
        if (text.length > 1) {
            learn(text, isIncognito)
            return
        }
        val ch = text[0]
        val ready: String? = synchronized(pendingLock) {
            pending.append(ch)
            when {
                ch in SENTENCE_ENDS || pending.length >= MAX_PENDING -> {
                    val out = pending.toString()
                    pending.setLength(0)
                    out
                }
                else -> null
            }
        }
        if (ready != null) learn(ready, isIncognito)
    }

    /** Hands over whatever sentence is half-written, for when the keyboard is closing. */
    fun flushPending(isIncognito: Boolean) {
        val ready: String? = synchronized(pendingLock) {
            if (pending.isEmpty()) null else pending.toString().also { pending.setLength(0) }
        }
        if (ready != null) learn(ready, isIncognito)
    }

    /**
     * Folds [text] into the model.
     *
     * Refused in incognito mode, which is the whole promise of incognito mode; refused when the
     * feature is off; and refused for anything long enough to be a paste rather than a sentence,
     * because a pasted document would swamp the counts with somebody else's vocabulary.
     */
    fun learn(text: String, isIncognito: Boolean) {
        if (isIncognito || text.isBlank()) return
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return
        if (text.length > MAX_LEARN_LENGTH) return
        scope.launch {
            model.learn(text)
            scheduleSave()
        }
    }

    /**
     * The next-word predictions for the text before the cursor.
     *
     * Returns nothing when the feature is off, in incognito mode, or before the model has read
     * enough to be worth trusting. That last one matters: a model with a hundred words in it will
     * answer every query, and every answer will be noise, which teaches the user to ignore the strip
     * before the model is ever good enough to be worth reading.
     */
    fun predict(textBeforeCursor: String, currentWord: String, isIncognito: Boolean): List<MaNgramSuggestion> {
        if (isIncognito) return emptyList()
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.maNgramEnabled.get()) return emptyList()
        if (model.totalWords < MIN_WORDS_BEFORE_PREDICTING) return emptyList()
        val (two, one) = model.contextOf(textBeforeCursor)
        return model.predict(previousTwo = two, previousOne = one, prefix = currentWord)
    }

    /**
     * Writes the model out after a pause in typing.
     *
     * Debounced rather than written per word: learning happens on every commit, and a file write per
     * keystroke would be both wasteful and a good way to be killed mid-write. The delay is long
     * enough to batch a sentence and short enough that little is lost if the process dies.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            model.prune()
            file?.let { model.save(it) }
        }
    }

    /** Writes immediately, for when the keyboard is going away and the delay would not finish. */
    fun flush() {
        saveJob?.cancel()
        scope.launch {
            if (model.isDirty) {
                file?.let { model.save(it) }
            }
        }
    }

    /** Forgets everything and removes the file. */
    fun forgetEverything() {
        scope.launch {
            model.clear()
            file?.delete()
        }
    }

    private const val FILE_NAME = "ma_ngram.tsv"

    /** Long enough to batch a sentence, short enough to lose little if the process is killed. */
    private const val SAVE_DELAY_MS = 4_000L

    /** Beyond this a commit is a paste, not writing, and learning it would swamp the counts. */
    private const val MAX_LEARN_LENGTH = 2_000

    /** Below this the model answers everything and is right about nothing. */
    private const val MIN_WORDS_BEFORE_PREDICTING = 300L

    /** What ends a sentence for the purpose of handing it over. */
    private val SENTENCE_ENDS = charArrayOf('.', '!', '?', '\n', ':', ';')

    /** A sentence this long without punctuation is handed over anyway. */
    private const val MAX_PENDING = 400
}
