/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule: go in order, take the first key that works, stay on it until it stops, then flag it and
 * move on. Never ask them all.
 *
 * Most of these exist because the tempting shortcut is wrong in a way that only shows up later. The
 * two that matter most are [aDeadConnectionNeverFlagsAKey] and [everyKeyFlaggedStillTriesThemAll]:
 * the first stops a good key being lost to a tunnel, the second stops a stale flag silencing the
 * reader for good.
 */
class MaKeyRingTest {

    private val keys = listOf("sk_aaaaaaaaaaaaaaaaaaaa0001", "sk_bbbbbbbbbbbbbbbbbbbb0002", "sk_cccccccccccccccccccc0003")
    private val now = 1_770_000_000_000L // some fixed moment in 2026

    private fun bad(kind: DictateApiException.Kind) = DictateApiException(kind, "no", null, null)

    @Test
    fun anUntouchedRingTriesThemInTheOrderMarkoArrangedThem() {
        assertEquals(keys, MaKeyRing.order(keys, MaKeyRing.Ring(), now))
    }

    @Test
    fun theFirstWorkingKeyWinsAndTheOnesBeforeItAreNotAskedAgain() {
        var asked = mutableListOf<String>()
        var ring = MaKeyRing.Ring()

        // First run: key one is refused, key two answers.
        val (result, r1) = MaKeyRing.run(keys, ring, now) { key ->
            asked += key
            if (key == keys[0]) throw bad(DictateApiException.Kind.INVALID_API_KEY)
            "spoken"
        }
        ring = r1
        assertEquals("spoken", result)
        assertEquals(listOf(keys[0], keys[1]), asked)

        // Second run: key one is not asked at all, and key two is asked first.
        asked = mutableListOf()
        val (_, r2) = MaKeyRing.run(keys, ring, now) { key -> asked += key; "spoken" }
        ring = r2
        assertEquals(listOf(keys[1]), asked)
        assertEquals(MaKeyRing.tail(keys[1]), ring.current)
    }

    @Test
    fun aRejectedKeyStaysRejectedBecauseTimeDoesNotHealIt() {
        val ring = MaKeyRing.onFailure(MaKeyRing.Ring(), keys[0], DictateApiException.Kind.INVALID_API_KEY, "", now)
        assertEquals(MaKeyRing.Health.REJECTED, MaKeyRing.stateOf(ring, keys[0]).health)
        assertFalse(MaKeyRing.isUsable(MaKeyRing.stateOf(ring, keys[0]), now))
        // A year later it is still rejected. Only a manual test clears it.
        assertFalse(MaKeyRing.isUsable(MaKeyRing.stateOf(ring, keys[0]), now + 365L * 86_400_000))
    }

    @Test
    fun anExhaustedKeyIsTriedAgainLaterBecauseCapsAndBalancesRecover() {
        val ring = MaKeyRing.onFailure(MaKeyRing.Ring(), keys[0], DictateApiException.Kind.QUOTA_EXCEEDED, "", now)
        val state = MaKeyRing.stateOf(ring, keys[0])
        assertEquals(MaKeyRing.Health.EXHAUSTED, state.health)
        assertFalse(MaKeyRing.isUsable(state, now + 60_000))
        assertTrue(MaKeyRing.isUsable(state, now + MaKeyRing.EXHAUSTED_RETRY_MS))
    }

    /** Spend caps and workspace budgets reset at 00:00 UTC on the 1st, so a month roll re-arms. */
    @Test
    fun aMonthRollReArmsAnExhaustedKeyEvenInsideTheWindow() {
        assertTrue(MaKeyRing.monthRolled(isoUtc(2026, 7, 31, 23), isoUtc(2026, 8, 1, 1)))
        assertFalse(MaKeyRing.monthRolled(isoUtc(2026, 8, 1, 1), isoUtc(2026, 8, 31, 23)))
        assertTrue(MaKeyRing.monthRolled(isoUtc(2025, 12, 31, 23), isoUtc(2026, 1, 1, 1)))

        val lateInMonth = isoUtc(2026, 7, 31, 23)
        val ring = MaKeyRing.onFailure(
            MaKeyRing.Ring(), keys[0], DictateApiException.Kind.QUOTA_EXCEEDED, "", lateInMonth,
        )
        // Two hours later is well inside the six hour window, but the month has rolled.
        assertTrue(MaKeyRing.isUsable(MaKeyRing.stateOf(ring, keys[0]), isoUtc(2026, 8, 1, 1)))
    }

    /**
     * The one that protects a good key. A timeout says nothing about the key, so nothing is
     * recorded and the call fails outright rather than burning through the rest of the ring against
     * a connection that is not there.
     */
    @Test
    fun aDeadConnectionNeverFlagsAKey() {
        for (kind in listOf(
            DictateApiException.Kind.TIMEOUT,
            DictateApiException.Kind.NETWORK,
            DictateApiException.Kind.SERVER_ERROR,
            DictateApiException.Kind.UNKNOWN,
        )) {
            val ring = MaKeyRing.onFailure(MaKeyRing.Ring(), keys[0], kind, "", now)
            assertTrue(ring.states.isEmpty(), "$kind must not flag a key")
        }

        val asked = mutableListOf<String>()
        val thrown = assertFailsWith<MaKeyRing.NoKeyLeft> {
            MaKeyRing.run(keys, MaKeyRing.Ring(), now) { key ->
                asked += key
                throw bad(DictateApiException.Kind.NETWORK)
            }
        }
        assertEquals(1, asked.size, "a dead connection must not be retried against every key")
        assertTrue(thrown.ring.states.isEmpty(), "and it must leave the ring exactly as it found it")
    }

    /**
     * A stale flag must never be able to silence the reader. Being wrong about a key costs one
     * failed request; refusing to try costs the feature.
     */
    @Test
    fun everyKeyFlaggedStillTriesThemAll() {
        var ring = MaKeyRing.Ring()
        keys.forEach {
            ring = MaKeyRing.onFailure(ring, it, DictateApiException.Kind.INVALID_API_KEY, "", now)
        }
        val ordered = MaKeyRing.order(keys, ring, now)
        assertEquals(keys.toSet(), ordered.toSet())
        assertEquals(3, ordered.size)

        // And one of them working again clears its flag and makes it current.
        val (_, after) = MaKeyRing.run(keys, ring, now) { key ->
            if (key == keys[2]) "spoken" else throw bad(DictateApiException.Kind.INVALID_API_KEY)
        }
        assertEquals(MaKeyRing.Health.WORKING, MaKeyRing.stateOf(after, keys[2]).health)
        assertEquals(MaKeyRing.tail(keys[2]), after.current)
    }

    @Test
    fun flaggedKeysGoLastRatherThanDisappearing() {
        val ring = MaKeyRing.onFailure(MaKeyRing.Ring(), keys[0], DictateApiException.Kind.INVALID_API_KEY, "", now)
        assertEquals(listOf(keys[1], keys[2], keys[0]), MaKeyRing.order(keys, ring, now))
    }

    @Test
    fun theKeyInUseIsDroppedTheMomentItStops() {
        var ring = MaKeyRing.onSuccess(MaKeyRing.Ring(), keys[1], now = now)
        assertEquals(MaKeyRing.tail(keys[1]), ring.current)
        ring = MaKeyRing.onFailure(ring, keys[1], DictateApiException.Kind.QUOTA_EXCEEDED, "", now)
        assertEquals(null, ring.current)
    }

    @Test
    fun aManualTestClearsAFlag() {
        var ring = MaKeyRing.onFailure(MaKeyRing.Ring(), keys[0], DictateApiException.Kind.INVALID_API_KEY, "", now)
        assertFalse(MaKeyRing.isUsable(MaKeyRing.stateOf(ring, keys[0]), now))
        ring = MaKeyRing.forget(ring, keys[0])
        assertTrue(MaKeyRing.isUsable(MaKeyRing.stateOf(ring, keys[0]), now))
        assertEquals(keys, MaKeyRing.order(keys, ring, now))
    }

    @Test
    fun runningOutOfKeysReportsTheLastRealReason() {
        val e = assertFailsWith<MaKeyRing.NoKeyLeft> {
            MaKeyRing.run(keys, MaKeyRing.Ring(), now) { throw bad(DictateApiException.Kind.QUOTA_EXCEEDED) }
        }
        assertEquals(DictateApiException.Kind.QUOTA_EXCEEDED, e.last?.kind)
        // The run that discovers the whole keyring is exhausted is the most expensive one there is,
        // so what it learned has to come back out with the failure. Losing it means paying for the
        // same discovery again on the next sentence.
        assertEquals(3, e.ring.states.size)
        assertTrue(e.ring.states.values.all { it.health == MaKeyRing.Health.EXHAUSTED })
    }

    @Test
    fun theRingSurvivesARoundTripAndACorruptFile() {
        val ring = MaKeyRing.onSuccess(MaKeyRing.Ring(), keys[0], "works", now)
        val back = MaKeyRing.parse(MaKeyRing.serialize(ring))
        assertEquals(ring.current, back.current)
        assertEquals(MaKeyRing.Health.WORKING, MaKeyRing.stateOf(back, keys[0]).health)
        assertTrue(MaKeyRing.parse("{ not json").states.isEmpty())
        assertTrue(MaKeyRing.parse(null).states.isEmpty())
    }

    /** The ring holds four characters of a key and no more, as the ledger does. */
    @Test
    fun theRingNeverHoldsAKey() {
        val ring = MaKeyRing.onSuccess(MaKeyRing.Ring(), keys[0], "works", now)
        assertFalse(MaKeyRing.serialize(ring).contains("sk_aaaa"))
        assertTrue(MaKeyRing.serialize(ring).contains(MaKeyRing.tail(keys[0])))
    }

    /** Sixty keys, one working, and only the ones before it are ever asked. */
    @Test
    fun sixtyKeysAskOnlyAsFarAsTheFirstWorkingOne() {
        val many = (1..60).map { "sk_" + "x".repeat(20) + "%04d".format(it) }
        val asked = mutableListOf<String>()
        val (_, ring) = MaKeyRing.run(many, MaKeyRing.Ring(), now) { key ->
            asked += key
            if (key == many[6]) "spoken" else throw bad(DictateApiException.Kind.INVALID_API_KEY)
        }
        assertEquals(7, asked.size, "must stop at the first working key, not walk all sixty")

        val again = mutableListOf<String>()
        MaKeyRing.run(many, ring, now) { key -> again += key; "spoken" }
        assertEquals(1, again.size, "the second run must go straight to the working key")
    }

    private fun isoUtc(year: Int, month: Int, day: Int, hour: Int): Long {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        c.set(year, month - 1, day, hour, 0, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
