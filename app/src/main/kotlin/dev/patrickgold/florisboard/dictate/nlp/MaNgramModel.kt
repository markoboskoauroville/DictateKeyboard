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

import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A word predictor that knows only what this phone has written.
 *
 * Every shipped prediction model is trained on everybody, which is why they are confident about
 * "how are you" and useless on Kukljica, montaža, binovi, Auroville. This one has the opposite
 * shape: it knows nothing about language in general and a great deal about the words actually used
 * here, including the ones no dictionary contains.
 *
 * ## How it predicts
 *
 * Three tables of counts, and a walk down them.
 *
 *  - the trigram table: what followed these two words
 *  - the bigram table: what followed this one word
 *  - the unigram table: what gets written at all
 *
 * A prediction tries the trigram first, because two words of context is the most specific evidence
 * available. If that yields too little it falls back to the bigram, then to the unigram. This is
 * stupid backoff: cruder than the smoothing a language model would use, and the right choice here,
 * because it needs no tuning, degrades predictably on a model that starts empty, and costs a map
 * lookup rather than a matrix.
 *
 * Longer context is preferred rather than merely added: a trigram hit outranks any bigram hit, and
 * a bigram hit outranks any unigram. Without that a common word would drown out the specific one
 * every time, and the specific one is the entire reason this exists.
 *
 * ## What it learns from
 *
 * Typing and dictation both. Dictation matters more: this phone produces far more words by speaking
 * than by tapping, so most of what the model knows will arrive as transcripts.
 *
 * ## What it does not do
 *
 * Nothing leaves the device, and nothing is learned in incognito mode.
 *
 * Croatian case endings are a known weakness and are deliberately not addressed. Kuću, kući and
 * kuća are three unrelated strings to a counter, so the counts spread thinner than they would in
 * English. Fixing that means stemming, stemming means a Croatian morphology table, and a wrong
 * table would merge words that are genuinely different. Thin counts recover with use; wrong merges
 * do not.
 *
 * ## Thread safety
 *
 * Learning happens on whatever thread committed the text and prediction happens on the NLP scope, so
 * every table is guarded by one read-write lock. Predictions take the read lock and can overlap;
 * learning takes the write lock. The lock is held only around the map operations, never across file
 * access.
 */
class MaNgramModel {

    private val lock = ReentrantReadWriteLock()

    /** word -> times written. */
    private val unigrams = HashMap<String, Int>()

    /** previous word -> next word -> times that pair was written. */
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    /** the two previous words joined -> next word -> times that triple was written. */
    private val trigrams = HashMap<String, HashMap<String, Int>>()

    /** Set whenever a table changes, cleared by a successful save. */
    @Volatile
    var isDirty: Boolean = false
        private set

    /** How many distinct words are known. Used by the settings screen and by [prune]. */
    val vocabularySize: Int
        get() = lock.read { unigrams.size }

    /** How many words have been read in total. */
    var totalWords: Long = 0L
        private set

    // ---------------------------------------------------------------------------------------------
    // Learning
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads [text] and folds it into the counts.
     *
     * Sentence boundaries are respected: the context is cleared at a full stop, a question mark, an
     * exclamation mark, a newline or a colon, so the last word of one sentence never predicts the
     * first word of the next. Without that the model learns to follow every "." with whatever
     * happens to be written most often afterwards, which is noise dressed as a pattern.
     */
    fun learn(text: String) {
        if (text.isBlank()) return
        val sentences = text.split(*SENTENCE_BREAKS)
        lock.write {
            for (sentence in sentences) {
                val words = tokenize(sentence)
                if (words.isEmpty()) continue
                totalWords += words.size
                for (index in words.indices) {
                    val word = words[index]
                    unigrams[word] = (unigrams[word] ?: 0) + 1
                    if (index >= 1) {
                        // Lowercased, because the lookup side lowercases too. Storing the surface
                        // form here would mean "The" and "the" were different contexts and half the
                        // bigrams would never be found.
                        val prev = words[index - 1].lowercase()
                        val row = bigrams.getOrPut(prev) { HashMap() }
                        row[word] = (row[word] ?: 0) + 1
                    }
                    if (index >= 2) {
                        val key = trigramKey(words[index - 2], words[index - 1])
                        val row = trigrams.getOrPut(key) { HashMap() }
                        row[word] = (row[word] ?: 0) + 1
                    }
                }
            }
            isDirty = true
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Prediction
    // ---------------------------------------------------------------------------------------------

    /**
     * The most likely next words after [previousTwo] and [previousOne], optionally starting with
     * [prefix].
     *
     * [prefix] is what has been typed of the word so far; empty means predict the whole next word.
     * Matching is case-insensitive but the stored casing is returned, so a name learned as Kerstin
     * comes back as Kerstin rather than kerstin.
     *
     * Results are ordered by tier first and count second, so anything the trigram knows outranks
     * everything the bigram knows.
     */
    fun predict(
        previousTwo: String?,
        previousOne: String?,
        prefix: String,
        limit: Int = 3,
    ): List<MaNgramSuggestion> {
        if (limit <= 0) return emptyList()
        val needle = prefix.lowercase()
        val out = LinkedHashMap<String, MaNgramSuggestion>()

        lock.read {
            fun harvest(row: Map<String, Int>?, tier: Int) {
                if (row == null) return
                val ranked = row.entries
                    .asSequence()
                    .filter { needle.isEmpty() || it.key.startsWith(needle, ignoreCase = true) }
                    // A word cannot usefully predict itself as the word being typed.
                    .filter { !it.key.equals(prefix, ignoreCase = true) }
                    .sortedByDescending { it.value }
                for (entry in ranked) {
                    if (out.size >= limit) return
                    val existing = out[entry.key.lowercase()]
                    if (existing != null) continue
                    out[entry.key.lowercase()] = MaNgramSuggestion(
                        word = entry.key,
                        count = entry.value,
                        tier = tier,
                    )
                }
            }

            if (previousTwo != null && previousOne != null) {
                harvest(trigrams[trigramKey(previousTwo, previousOne)], TIER_TRIGRAM)
            }
            if (out.size < limit && previousOne != null) {
                harvest(bigrams[previousOne.lowercase()], TIER_BIGRAM)
            }
            if (out.size < limit && needle.isNotEmpty()) {
                // Unigrams only help when there is a prefix to narrow them; offering the most
                // written word with no context at all is not a prediction, it is a guess.
                harvest(unigrams, TIER_UNIGRAM)
            }
        }
        return out.values.toList()
    }

    /** The last two words before the cursor, for [predict]. Either may be null near the start. */
    fun contextOf(textBeforeCursor: String): Pair<String?, String?> {
        val lastBreak = textBeforeCursor.indexOfLast { it in SENTENCE_BREAK_CHARS }
        val tail = if (lastBreak >= 0) textBeforeCursor.substring(lastBreak + 1) else textBeforeCursor
        val words = tokenize(tail)
        return when {
            words.size >= 2 -> words[words.size - 2] to words[words.size - 1]
            words.size == 1 -> null to words[0]
            else -> null to null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Housekeeping
    // ---------------------------------------------------------------------------------------------

    /**
     * Drops the rarest entries when the model outgrows [maxVocabulary].
     *
     * Words seen once are dropped first: a single sighting is as likely to be a typo, a URL or a
     * transcription error as a word. Anything seen twice or more is kept regardless of size, because
     * the words worth having here are exactly the ones no dictionary contains and they are rare by
     * definition.
     */
    fun prune(maxVocabulary: Int = MAX_VOCABULARY) {
        lock.write {
            if (unigrams.size <= maxVocabulary) return
            val doomed = unigrams.filterValues { it <= 1 }.keys.toSet()
            if (doomed.isEmpty()) return
            unigrams.keys.removeAll(doomed)
            bigrams.keys.removeAll(doomed)
            for (row in bigrams.values) row.keys.removeAll(doomed)
            bigrams.entries.removeAll { it.value.isEmpty() }
            for (row in trigrams.values) row.keys.removeAll(doomed)
            trigrams.entries.removeAll { it.value.isEmpty() }
            isDirty = true
        }
    }

    /** Forgets everything. */
    fun clear() {
        lock.write {
            unigrams.clear()
            bigrams.clear()
            trigrams.clear()
            totalWords = 0L
            isDirty = true
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    /**
     * Writes the model to [file], via a temporary file that is renamed into place.
     *
     * The rename is the point. A model written directly into its own file is a model that is
     * half-written when the process is killed, and this process is an input method, which the system
     * kills routinely and without warning. A torn file would then fail to parse on the next launch
     * and take every word ever learned with it.
     */
    fun save(file: File): Boolean {
        val snapshot = lock.read {
            buildString {
                append(FORMAT_VERSION).append('\n')
                append(totalWords).append('\n')
                for ((word, count) in unigrams) {
                    append("1\t").append(word).append('\t').append(count).append('\n')
                }
                for ((prev, row) in bigrams) {
                    for ((word, count) in row) {
                        append("2\t").append(prev).append('\t').append(word)
                            .append('\t').append(count).append('\n')
                    }
                }
                for ((key, row) in trigrams) {
                    for ((word, count) in row) {
                        append("3\t").append(key).append('\t').append(word)
                            .append('\t').append(count).append('\n')
                    }
                }
            }
        }
        return try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(snapshot)
            if (!temp.renameTo(file)) {
                // A failed rename leaves the previous model intact, which is the outcome to want.
                temp.delete()
                return false
            }
            isDirty = false
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads a model back from [file], replacing whatever is held.
     *
     * A malformed line is skipped rather than thrown, and an unreadable file leaves the model empty.
     * Losing learned words is a disappointment; refusing to start the keyboard is a fault.
     */
    fun load(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            val lines = file.readLines()
            if (lines.isEmpty() || lines[0] != FORMAT_VERSION) return false
            lock.write {
                unigrams.clear()
                bigrams.clear()
                trigrams.clear()
                totalWords = lines.getOrNull(1)?.toLongOrNull() ?: 0L
                for (index in 2 until lines.size) {
                    val parts = lines[index].split('\t')
                    when (parts.getOrNull(0)) {
                        "1" -> {
                            val word = parts.getOrNull(1) ?: continue
                            val count = parts.getOrNull(2)?.toIntOrNull() ?: continue
                            unigrams[word] = count
                        }
                        "2" -> {
                            val prev = parts.getOrNull(1) ?: continue
                            val word = parts.getOrNull(2) ?: continue
                            val count = parts.getOrNull(3)?.toIntOrNull() ?: continue
                            bigrams.getOrPut(prev) { HashMap() }[word] = count
                        }
                        "3" -> {
                            val key = parts.getOrNull(1) ?: continue
                            val word = parts.getOrNull(2) ?: continue
                            val count = parts.getOrNull(3)?.toIntOrNull() ?: continue
                            trigrams.getOrPut(key) { HashMap() }[word] = count
                        }
                    }
                }
                isDirty = false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun trigramKey(first: String, second: String) =
        first.lowercase() + '\u0000' + second.lowercase()

    companion object {
        private const val FORMAT_VERSION = "ma-ngram-1"

        /** Trigram beats bigram beats unigram, whatever the counts say. */
        const val TIER_TRIGRAM = 3
        const val TIER_BIGRAM = 2
        const val TIER_UNIGRAM = 1

        /** Roughly a large personal vocabulary, and a file of a few hundred kilobytes. */
        const val MAX_VOCABULARY = 40_000

        private val SENTENCE_BREAK_CHARS = charArrayOf('.', '!', '?', '\n', ':', ';')
        private val SENTENCE_BREAKS =
            arrayOf(".", "!", "?", "\n", ":", ";")

        /**
         * Splits a sentence into words.
         *
         * Letters, digits and the marks that live inside words: the apostrophe, and the hyphen for
         * things like "e-mail". Everything else separates. Stored lowercase for the context keys but
         * the surface form is kept in the tables, so casing survives.
         */
        fun tokenize(text: String): List<String> {
            val out = ArrayList<String>()
            val current = StringBuilder()
            for (ch in text) {
                if (ch.isLetterOrDigit() || ch == '\'' || ch == '\u2019' || ch == '-' || ch == '_') {
                    current.append(ch)
                } else if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.setLength(0)
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
            return out
        }
    }
}

/** One prediction: the word, how often it was seen, and which table it came from. */
data class MaNgramSuggestion(
    val word: String,
    val count: Int,
    val tier: Int,
)
