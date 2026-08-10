/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

/**
 * Several API keys per provider, and a parser that digs them out of an arbitrary text file.
 *
 * The keyring upstream stores one key string per provider. Rather than change that storage, the
 * keys are kept in that same field one per line, which older builds still read as a single key and
 * which the editor shows as an ordinary multi-line value. [split] turns it back into a list.
 *
 * [extract] is the file import: it accepts whatever the user picked and keeps only what looks like
 * a key, so a notes file full of prose and other services' credentials imports cleanly.
 */
object MaKeys {

    /** AssemblyAI keys are 32 hex characters standing alone. */
    private val HEX32 = Regex("(?<![0-9A-Za-z])[0-9a-fA-F]{32}(?![0-9A-Za-z])")

    /**
     * Google keys, both generations. Google moved Gemini from Standard keys beginning AIza to
     * Auth keys beginning AQ.Ab during 2026, so a parser that knows only AIza throws away every
     * key issued today. The AQ form contains dots, which the character class has to allow.
     */
    private val GEMINI = Regex("(AQ\\.[0-9A-Za-z._-]{20,}|AIza[0-9A-Za-z_-]{20,})")

    /** Anthropic keys: sk-ant- then the body. */
    private val ANTHROPIC = Regex("sk-ant-[0-9A-Za-z_-]{20,}")

    /** OpenAI style, kept for custom endpoints. */
    private val OPENAI = Regex("sk-(?!ant-)[0-9A-Za-z_-]{20,}")

    /**
     * Speechify keys: `sk_` then the body.
     *
     * The separator is the whole difference from OpenAI's `sk-`, one character, and it is the only
     * thing keeping a file that holds both from filing them under each other. [OPENAI] requires the
     * hyphen literally, so the two patterns cannot both match the same token; that is deliberate and
     * should survive any edit to either.
     */
    private val SPEECHIFY = Regex("sk_[0-9A-Za-z_-]{16,}")

    /**
     * Groq keys: `gsk_` then the body.
     *
     * Note the order this is tested in below matters, because `gsk_...` also satisfies the loose
     * tier and, read carelessly, looks like a Speechify `sk_` key with a letter in front. It does not
     * match [SPEECHIFY], which anchors on `sk_` at a token boundary, but the resemblance is close
     * enough to be worth a line here.
     */
    private val GROQ = Regex("gsk_[0-9A-Za-z_-]{20,}")

    /** Fallback shape for anything else key-like, used only when no hex key is present. */
    private val LOOSE = Regex("[A-Za-z0-9._-]{24,120}")

    /** Prefixes belonging to other services, never imported. */
    private val FOREIGN = listOf(
        "gsk_", "xai-", "hf_", "ghp_", "gho_", "github_pat_",
        "pk_", "rk_", "xoxb-", "xoxp-", "akia", "eyj",
    )

    /** Words that mark a line as belonging to another service, unless it also says assembly. */
    private val FOREIGN_WORDS = listOf(
        "groq", "gemini", "google", "openai", "anthropic", "claude", "elevenlabs",
        "deepgram", "huggingface", "replicate", "tidal", "spotify", "speechify",
    )

    /** The stored field, one key per line, back into a list. Blank and # lines are ignored. */
    fun split(stored: String): List<String> {
        val keys = stored.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
        return if (keys.isEmpty()) listOf(stored.trim()) else keys
    }

    /** How the list is written back into the single stored field. */
    fun join(keys: List<String>): String = keys.joinToString("\n")

    /**
     * A single key, masked for display. Enough of both ends to recognise which key a row is, never
     * enough to use. Short strings collapse entirely rather than leaking most of themselves.
     */
    fun mask(key: String): String {
        val k = key.trim()
        return when {
            k.length > 14 -> k.take(8) + "\u2026" + k.takeLast(4)
            k.length > 8 -> k.take(4) + "\u2026" + k.takeLast(2)
            else -> "\u2026"
        }
    }

    /**
     * Merges freshly imported keys into the ones already stored, keeping order and dropping exact
     * duplicates. Importing the same file twice therefore changes nothing, which is the point: a
     * long list of keys is only manageable if re-importing is safe.
     *
     * @return the merged list and how many of [incoming] were genuinely new.
     */
    fun merge(existing: List<String>, incoming: List<String>): Pair<List<String>, Int> {
        val seen = LinkedHashSet<String>()
        existing.map { it.trim() }.filter { it.isNotEmpty() }.forEach { seen.add(it) }
        var added = 0
        for (key in incoming.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (seen.add(key)) added++
        }
        return seen.toList() to added
    }

    /** Masked summary of what is stored, shown where the paste field used to be. */
    fun describe(stored: String): String {
        val keys = split(stored).filter { it.isNotBlank() }
        if (keys.isEmpty()) return "No key yet. Import one from a file below."
        val first = keys.first()
        val masked = if (first.length > 12) {
            first.take(6) + "\u2026" + first.takeLast(4)
        } else {
            "key stored"
        }
        return if (keys.size == 1) {
            "1 key: " + masked
        } else {
            keys.size.toString() + " keys, first " + masked + ", the rest are fallbacks"
        }
    }

    /**
     * Turns whatever a provider or the HTTP stack threw into one short sentence.
     *
     * Raw messages carry JSON bodies, stack detail and library wording that means nothing to the
     * person holding the phone. The common cases are recognised by their fingerprints and given a
     * plain reply; anything unrecognised is trimmed to its first line and capped, so the dialog
     * never fills with a payload again.
     */
    fun tidyError(raw: String?, fallback: String): String {
        val text = raw.orEmpty().trim()
        if (text.isEmpty()) return fallback
        val low = text.lowercase()
        return when {
            low.contains("0x0a") || low.contains("unexpected char") ->
                "That key contains a line break. Re-import it from your file."
            low.contains("401") || low.contains("invalid x-api-key") ||
                low.contains("invalid api key") || low.contains("unauthorized") ->
                "The service rejected this key. Check you imported the right one for this provider."
            low.contains("429") || low.contains("rate limit") || low.contains("quota") ->
                "Out of quota or too many requests. A second key would cover this."
            low.contains("403") -> "This key is not allowed to use that model."
            low.contains("404") -> "The service has no such endpoint or model."
            low.contains("timeout") || low.contains("timed out") ->
                "The service did not answer in time."
            low.contains("unable to resolve host") || low.contains("failed to connect") ||
                low.contains("network") || low.contains("unreachable") ->
                "No connection to the service."
            low.contains("certificate") || low.contains("ssl") ->
                "The secure connection could not be verified."
            low.startsWith("{") || low.contains("\"error\"") ->
                "The service refused the request."
            else -> {
                val firstLine = text.lineSequence().first().trim()
                if (firstLine.length > 110) firstLine.take(107) + "\u2026" else firstLine
            }
        }
    }

    private fun foreign(token: String, line: String): Boolean {
        val low = token.lowercase()
        if (FOREIGN.any { low.startsWith(it) }) return true
        val context = line.lowercase()
        return FOREIGN_WORDS.any { context.contains(it) } && !context.contains("assembly")
    }

    /**
     * True when a token carries another provider's unmistakable prefix.
     *
     * This is the fix for a real failure: a keys file holding Anthropic, Gemini and AssemblyAI keys
     * was imported for AssemblyAI, no bare 32 hex token matched, the generic tier ran, and the
     * Anthropic key was handed to AssemblyAI. Transcription then failed on every attempt while the
     * dialog cheerfully reported a stored key.
     *
     * It does not contradict the rule that a key is never rejected for its shape. That rule protects
     * keys whose format is unknown or newly changed, and everything unrecognised still passes. What
     * is filtered here is the opposite case: a token that positively identifies itself as belonging
     * to a different service. "sk-ant-" is not an unfamiliar AssemblyAI key, it is Anthropic's.
     */
    private fun belongsToAnotherProvider(token: String, providerId: String): Boolean {
        if (providerId.isBlank()) return false
        if (providerId != "anthropic" && ANTHROPIC.matches(token)) return true
        if (providerId != "gemini" && GEMINI.matches(token)) return true
        if (providerId != "openai" && providerId != "groq" && OPENAI.matches(token)) return true
        if (providerId != "speechify" && SPEECHIFY.matches(token)) return true
        if (providerId != "groq" && GROQ.matches(token)) return true
        return false
    }

    /**
     * Warning shown after an import, or null when nothing looks wrong. Silence when a key is simply
     * unusual; a sentence only when the file plainly held keys for other services and none for this
     * one, which is the case that used to pass unnoticed.
     */
    fun mismatchWarning(text: String, providerId: String, imported: List<String>): String? {
        if (imported.isNotEmpty()) return null
        val others = buildSet {
            if (providerId != "anthropic" && ANTHROPIC.containsMatchIn(text)) add("Anthropic")
            if (providerId != "gemini" && GEMINI.containsMatchIn(text)) add("Gemini")
            if (providerId != "openai" && providerId != "groq" && OPENAI.containsMatchIn(text)) {
                add("OpenAI")
            }
            if (providerId != "speechify" && SPEECHIFY.containsMatchIn(text)) add("Speechify")
            if (providerId != "groq" && GROQ.containsMatchIn(text)) add("Groq")
        }
        if (others.isEmpty()) return null
        return "No key for this provider in that file. Keys for " +
            others.joinToString(", ") + " were found and left alone."
    }

    /**
     * Pulls keys out of an arbitrary text file, in order, without duplicates.
     *
     * Tier one takes the shape belonging to the provider being imported for: 32 bare hex characters
     * for AssemblyAI, `sk_` for Speechify, and so on. That walks straight past prose, dates and
     * other providers' keys. Only if that finds nothing does tier two accept any long token
     * containing a digit, so an unusual or newly changed key format is never silently lost.
     */
    fun extract(text: String, providerId: String = ""): List<String> {
        val lines = text.split('\n')
        val found = LinkedHashSet<String>()
        // Each provider has its own key shape. Matching the right one is what keeps a file
        // holding keys for all three services from handing Gemini's key to Anthropic.
        val shape = when (providerId) {
            "gemini" -> GEMINI
            "anthropic" -> ANTHROPIC
            "assemblyai" -> HEX32
            "speechify" -> SPEECHIFY
            "groq" -> GROQ
            "openai", "groq" -> OPENAI
            else -> null
        }
        if (shape != null) {
            for (line in lines) {
                if (line.trim().startsWith("#")) continue
                for (m in shape.findAll(line)) {
                    // Hex keys are case-insensitive, the prefixed ones are not.
                    found.add(if (shape === HEX32) m.value.lowercase() else m.value)
                }
            }
            if (found.isNotEmpty()) return found.toList()
            // Nothing matched the expected shape. That does NOT mean the file holds no key: a
            // provider can rebrand its format at any time, exactly as Google did. So fall through
            // to the generic scan rather than telling the user their good key was not found.
        }
        for (line in lines) {
            if (line.trim().startsWith("#")) continue
            for (m in HEX32.findAll(line)) {
                val token = m.value.lowercase()
                if (!foreign(token, line)) found.add(token)
            }
        }
        if (found.isNotEmpty()) return found.toList()
        for (line in lines) {
            if (line.trim().startsWith("#")) continue
            for (m in LOOSE.findAll(line)) {
                val token = m.value
                if (foreign(token, line)) continue
                // A token wearing another provider's prefix is not an unfamiliar key for this one.
                if (belongsToAnotherProvider(token, providerId)) continue
                if (!token.any { it.isDigit() }) continue
                found.add(token)
            }
        }
        return found.toList()
    }
}

/** Failures that mean this particular key is the problem, so the next one is worth trying. */
@PublishedApi
internal val KEY_PROBLEMS = setOf(
    DictateApiException.Kind.INVALID_API_KEY,
    DictateApiException.Kind.QUOTA_EXCEEDED,
)

/**
 * Runs [block] with each key in turn, moving on when a key is rejected or out of quota.
 *
 * **Superseded by [MaKeyRing.run] at build 144 and kept only so nothing outside this module breaks.**
 * The difference is memory: this walks the list from the top on every call, so a keyring whose first
 * few keys are dead pays those failed round trips before every recording, forever. Anything new goes
 * through the ring.
 *
 * Anything else, a timeout or a network drop, is thrown immediately: trying a second key against a
 * dead connection only makes the user wait twice for the same failure. If every key is refused the
 * last exception is thrown, so the message the user sees is a real one.
 */
inline fun <T> maWithKeyFallback(
    keys: List<String>,
    onKeyRejected: (index: Int, total: Int, reason: String) -> Unit = { _, _, _ -> },
    block: (String) -> T,
): T {
    var last: DictateApiException? = null
    for ((i, key) in keys.withIndex()) {
        try {
            return block(key)
        } catch (e: DictateApiException) {
            if (e.kind !in KEY_PROBLEMS) throw e
            last = e
            val reason = when (e.kind) {
                DictateApiException.Kind.QUOTA_EXCEEDED -> "out of quota"
                else -> "rejected"
            }
            onKeyRejected(i + 1, keys.size, reason)
        }
    }
    throw last ?: IllegalStateException("no API key configured")
}
