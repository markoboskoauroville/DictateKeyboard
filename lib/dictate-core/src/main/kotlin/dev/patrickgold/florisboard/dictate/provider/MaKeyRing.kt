/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.TimeZone

/**
 * Which key to use, and which ones to stop trying.
 *
 * **The rule, in Marko's words.** Go down the list in order. Find the first key that works. Use that
 * one until it stops working. When it stops, flag it and move to the next. Never test them all at
 * once.
 *
 * That is not what the app did before. [maWithKeyFallback] walks the list in order on **every**
 * call, which is correct but has no memory: a keyring whose first four keys are dead pays four
 * failed round trips before every single sentence the reader speaks, forever. And the key manager's
 * bulk test checked all of them, which on a per-character bill means paying to learn something about
 * keys that were never going to be needed. This object is the memory that was missing.
 *
 * **What it does not do is decide a key is dead too eagerly.** That is the whole difficulty, and the
 * distinction is between a failure *of the key* and a failure *around it*:
 *
 * | what came back | is it the key's fault | what happens |
 * |---|---|---|
 * | 401, rejected | yes, and permanently | flagged, never re-armed by time |
 * | 402, cap or balance | yes, and temporarily | flagged, re-armed after six hours or at the month roll |
 * | 429, too many requests | **no** | nothing recorded, try again |
 * | timeout, no signal | **no** | nothing recorded, try again |
 *
 * Flagging a good key because the train went into a tunnel is worse than not flagging at all: the
 * key is skipped, the next one is used, and the meter starts filling up somewhere Marko did not
 * choose. So the middle two rows of that table are the important ones.
 *
 * **The ring can never return nothing.** If every key is flagged, [order] still returns them, worst
 * last. A stale flag must not be able to silence the reader, and a key marked dead in March may well
 * work in August. Being wrong about a key costs one failed request; refusing to try costs the
 * feature.
 *
 * Keys are held by their last four characters, as in [MaUsage]: enough to identify a row, useless to
 * anyone reading the file.
 */
object MaKeyRing {

    enum class Health {
        /** Never tried. */
        UNKNOWN,

        /** Answered properly the last time it was asked. */
        WORKING,

        /** The service refused it. This does not heal on its own. */
        REJECTED,

        /** Accepted but out of cap, budget or balance. This does heal, so it is re-armed by time. */
        EXHAUSTED,
    }

    @Serializable
    data class KeyState(
        val tail: String,
        val health: Health = Health.UNKNOWN,
        /** When this verdict was reached. */
        val at: Long = 0L,
        /** One short sentence for the row under the key. */
        val detail: String = "",
    )

    /**
     * [current] is the key in use, held so the next call starts where the last one succeeded rather
     * than at the top of the list. That is the "use it until it stops working" half of the rule.
     */
    @Serializable
    data class Ring(
        val states: Map<String, KeyState> = emptyMap(),
        val current: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * How long an exhausted key is left alone.
     *
     * Six hours rather than a day: a spend cap can be raised in the console in a minute, and a
     * keyring that ignores a key Marko has just topped up until tomorrow is a keyring that looks
     * broken. Rather than six minutes: the point of flagging is to stop paying for failures, and a
     * short window gives most of that cost back.
     */
    const val EXHAUSTED_RETRY_MS = 6L * 60 * 60 * 1000

    fun parse(raw: String?): Ring =
        if (raw.isNullOrBlank()) Ring() else runCatching {
            json.decodeFromString(Ring.serializer(), raw)
        }.getOrElse { Ring() }

    fun serialize(ring: Ring): String = json.encodeToString(Ring.serializer(), ring)

    fun tail(key: String): String = key.trim().takeLast(4)

    fun stateOf(ring: Ring, key: String): KeyState =
        ring.states[tail(key)] ?: KeyState(tail(key))

    /**
     * Whether a key is worth trying right now.
     *
     * A rejected key is not, until somebody tests it by hand: a key the service refused does not
     * start working again because time passed, and retrying it costs a round trip before every
     * sentence. An exhausted one is, once the window has passed or the calendar month has rolled,
     * because both a per-key spend cap and a workspace budget reset at 00:00 UTC on the 1st.
     */
    fun isUsable(state: KeyState, now: Long = System.currentTimeMillis()): Boolean = when (state.health) {
        Health.UNKNOWN, Health.WORKING -> true
        Health.REJECTED -> false
        Health.EXHAUSTED -> now - state.at >= EXHAUSTED_RETRY_MS || monthRolled(state.at, now)
    }

    /** True when [now] is in a later UTC calendar month than [then]. Caps reset on the 1st, UTC. */
    internal fun monthRolled(then: Long, now: Long): Boolean {
        if (then <= 0L) return true
        val utc = TimeZone.getTimeZone("UTC")
        val a = Calendar.getInstance(utc).apply { timeInMillis = then }
        val b = Calendar.getInstance(utc).apply { timeInMillis = now }
        val am = a.get(Calendar.YEAR) * 12 + a.get(Calendar.MONTH)
        val bm = b.get(Calendar.YEAR) * 12 + b.get(Calendar.MONTH)
        return bm > am
    }

    /**
     * The order to try [keys] in: the one in use first, then the rest as Marko arranged them,
     * skipping the flagged ones, and then the flagged ones anyway so the list is never empty.
     *
     * The final group is the safety net and it matters more than it looks. Flags are a guess about
     * the future made from one past request. Trying a flagged key as a last resort costs one failed
     * call in the case where the flag was right, and rescues the whole feature in the case where it
     * was wrong.
     */
    fun order(
        keys: List<String>,
        ring: Ring,
        now: Long = System.currentTimeMillis(),
    ): List<String> {
        val clean = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return emptyList()
        val usable = clean.filter { isUsable(stateOf(ring, it), now) }
        val flagged = clean.filterNot { isUsable(stateOf(ring, it), now) }
        val currentKey = ring.current?.let { cur -> usable.firstOrNull { tail(it) == cur } }
        return buildList {
            currentKey?.let { add(it) }
            addAll(usable.filter { it !== currentKey && tail(it) != ring.current })
            addAll(flagged)
        }.distinct()
    }

    /** Records that [key] answered. It becomes the one in use. */
    fun onSuccess(
        ring: Ring,
        key: String,
        detail: String = "",
        now: Long = System.currentTimeMillis(),
    ): Ring {
        val t = tail(key)
        return ring.copy(
            states = ring.states + (t to KeyState(t, Health.WORKING, now, detail)),
            current = t,
        )
    }

    /**
     * Records a failure, and flags the key **only when the failure was the key's**.
     *
     * A rate limit or a dead connection returns the ring untouched, so nothing is learned and
     * nothing is lost. This is the function where being too clever costs Marko a good key.
     */
    fun onFailure(
        ring: Ring,
        key: String,
        kind: DictateApiException.Kind,
        detail: String = "",
        now: Long = System.currentTimeMillis(),
    ): Ring {
        val health = when (kind) {
            DictateApiException.Kind.INVALID_API_KEY -> Health.REJECTED
            DictateApiException.Kind.QUOTA_EXCEEDED -> Health.EXHAUSTED
            // Everything else says nothing about the key. Leave it exactly as it was.
            else -> return ring
        }
        val t = tail(key)
        return ring.copy(
            states = ring.states + (t to KeyState(t, health, now, detail)),
            // The key in use has stopped working, so there is no key in use until one answers.
            current = if (ring.current == t) null else ring.current,
        )
    }

    /** Clears a flag, which is what a manual test on a single key does before it runs. */
    fun forget(ring: Ring, key: String): Ring {
        val t = tail(key)
        return ring.copy(states = ring.states - t, current = if (ring.current == t) null else ring.current)
    }

    /**
     * Every key has been tried and none answered.
     *
     * It carries [ring] because the flags raised on the way here are the most valuable ones there
     * are: this is the run that learned the whole keyring is in trouble, and throwing that away
     * means learning it again, at the same cost, on the very next sentence. The caller persists
     * [ring] whether it succeeded or not.
     */
    class NoKeyLeft(val last: DictateApiException?, val ring: Ring) :
        Exception(last?.message ?: "no API key configured")

    /**
     * Runs [block] against the keys in ring order, stopping at the first that answers.
     *
     * This is the whole rule in one function: first working key wins, it is remembered, and it is
     * used again next time without the ones before it being asked. A failure that is the key's fault
     * moves on to the next key; a failure that is not is thrown straight out, because trying a
     * second key against a dead connection only makes Marko wait twice for the same answer.
     *
     * @return the block's result and the updated ring, which the caller persists.
     */
    fun <T> run(
        keys: List<String>,
        ring: Ring,
        now: Long = System.currentTimeMillis(),
        block: (String) -> T,
    ): Pair<T, Ring> {
        var working = ring
        var last: DictateApiException? = null
        val ordered = order(keys, ring, now)
        for (key in ordered) {
            try {
                val result = block(key)
                return result to onSuccess(working, key, now = now)
            } catch (e: DictateApiException) {
                last = e
                val flagged = onFailure(working, key, e.kind, MaKeys.tidyError(e.message, ""), now)
                if (flagged === working) {
                    // Nothing was learned about the key, so this is not a reason to burn the next
                    // one. Timeouts and rate limits belong to the moment, not to the keyring. The
                    // ring goes out with the exception anyway, so a caller has one place to read it
                    // from rather than two.
                    throw NoKeyLeft(e, working)
                }
                working = flagged
            }
        }
        throw NoKeyLeft(last, working)
    }
}
