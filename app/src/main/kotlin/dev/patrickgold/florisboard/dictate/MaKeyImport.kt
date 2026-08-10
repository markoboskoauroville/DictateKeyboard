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

import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry

/**
 * One file in, every provider sorted out of it.
 *
 * Both the setup wizard and the key manager import keys, and both used to ask which provider a file
 * was for before reading it, which is backwards: the parser already knows which key belongs where.
 * They now share this, so there is one picker in each place and one behaviour to reason about.
 */
object MaKeyImport {

    /**
     * Providers this build uses, in the order they are shown, with what each key actually does here.
     * The first three are the ones that matter; the last two exist for custom endpoints and are only
     * shown once they hold a key.
     */
    val PROVIDERS = listOf(
        "assemblyai" to "speech to text, the transcription engine",
        "speechify" to "the reader's voice, English, with word timings",
        "anthropic" to "rewording and the restyle prompts",
        "gemini" to "optional second engine, transcription and rewording",
        "groq" to "reads the words out of a screenshot",
        "openai" to "optional, for custom endpoints",
    )

    /** What an import did: the new keyring, a human summary, and how many keys were genuinely new. */
    data class Result(
        val accounts: ProviderAccounts,
        val summary: String,
        val added: Int,
    )

    /**
     * Reads every provider's keys out of one file and merges them into [accounts].
     *
     * Duplicates are dropped, so importing a file that has already been imported changes nothing and
     * reports so rather than silently appearing to work. Keys wearing another provider's prefix are
     * never filed under the wrong one; that is handled inside [MaKeys.extract].
     */
    fun importAll(text: String, accounts: ProviderAccounts): Result {
        var working = accounts
        val parts = mutableListOf<String>()
        var total = 0
        for ((id, _) in PROVIDERS) {
            val preset = ProviderRegistry.byId(id) ?: continue
            val found = MaKeys.extract(text, id)
            if (found.isEmpty()) continue
            val existing = working.accounts[id] ?: ProviderAccount(providerId = id)
            val current = MaKeys.split(existing.apiKey).filter { it.isNotBlank() }
            val (merged, added) = MaKeys.merge(current, found)
            if (added > 0) {
                working = working.put(existing.copy(apiKey = MaKeys.join(merged)))
                parts += "${preset.displayName} +$added"
                total += added
            }
        }
        val summary = when {
            total > 0 -> "Imported: " + parts.joinToString(", ")
            else -> "Nothing new. Every key in that file is already here."
        }
        return Result(working, summary, total)
    }
}
