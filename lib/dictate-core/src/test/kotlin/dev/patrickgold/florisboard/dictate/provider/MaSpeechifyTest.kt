/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Speechify and the usage ledger, tested where the value is: the parsing and the arithmetic.
 *
 * Nothing here touches the network. Every one of these assertions exists because getting it wrong
 * produces a failure that looks like something else: a key filed under the wrong provider looks like
 * a dead key, a lost speech mark looks like a highlight that skips, and a week that starts on the
 * wrong day is right six days out of seven.
 */
class MaSpeechifyTest {

    /**
     * A keyring shaped like Marko's: prose, headings, four services, and a comment line.
     *
     * The fake Speechify keys deliberately do **not** start `sk_live_` or `sk_test_`. Those are
     * Stripe's prefixes, GitHub's push protection scans for them, and it rejected this file on its
     * first push even though every string in it is invented. Keep the fixtures shaped like a
     * Speechify key without wearing another company's prefix; the parser only needs `sk_` and
     * sixteen more characters.
     */
    private val keyring = """
        # my keys, do not commit
        Nova TV notes, nothing here

        AssemblyAI (transcription)
        3f9a2b7c8d1e4f60a5b3c7d9e1f20a4b
        second assembly key  a1b2c3d4e5f60718293a4b5c6d7e8f90

        Speechify (reader voice)
        sk_aB3dE5fG7hJ9kL1mN3pQ5rS7
        sk_zY8xW6vU4tS2rQ0pO9nM7lK5

        Anthropic
        sk-ant-api03-Xq7Lm2Pv9Rt4Wy6Zb8Nc1Df3Gh5Jk7

        Gemini
        AIzaSyD9mK3pQ7rT2vX5yB8nC1eF4hJ6lN0oP2s
    """.trimIndent()

    @Test
    fun eachProviderTakesOnlyItsOwnKeysOutOfOneFile() {
        val speechify = MaKeys.extract(keyring, "speechify")
        assertEquals(2, speechify.size)
        assertTrue(speechify.all { it.startsWith("sk_") })

        assertEquals(2, MaKeys.extract(keyring, "assemblyai").size)
        assertEquals(1, MaKeys.extract(keyring, "anthropic").size)
        assertEquals(1, MaKeys.extract(keyring, "gemini").size)
    }

    /**
     * `sk_` and `sk-` differ by one character and belong to different companies. This is the whole
     * reason Speechify has a shape of its own rather than falling through to the generic tier.
     */
    @Test
    fun anthropicsKeyIsNeverFiledAsSpeechifys() {
        val noSpeechifyKey = """
            AssemblyAI 3f9a2b7c8d1e4f60a5b3c7d9e1f20a4b
            Anthropic sk-ant-api03-Xq7Lm2Pv9Rt4Wy6Zb8Nc1Df3Gh5Jk7
        """.trimIndent()
        val found = MaKeys.extract(noSpeechifyKey, "speechify")
        assertTrue(found.none { it.startsWith("sk-ant") })

        val warning = MaKeys.mismatchWarning(noSpeechifyKey, "speechify", emptyList())
        assertNotNull(warning)
        assertTrue(warning.contains("Anthropic"))
    }

    @Test
    fun importingTheSameFileTwiceChangesNothing() {
        val keys = MaKeys.extract(keyring, "speechify")
        val (merged, added) = MaKeys.merge(keys, keys)
        assertEquals(0, added)
        assertEquals(2, merged.size)
    }

    /** The reply nests words inside the utterance; the reader wants the words, in order. */
    @Test
    fun speechMarksFlattenToWordsWithCharacterOffsets() {
        val sentence = "Hello, welcome to Speechify"
        val root = MaSpeechify.SpeechMark(
            start = 0, end = 27, startTime = 0, endTime = 1850,
            value = sentence, type = "sentence",
            chunks = listOf(
                MaSpeechify.SpeechMark(0, 6, 125, 375, "Hello,", "word"),
                MaSpeechify.SpeechMark(7, 14, 375, 750, "welcome", "word"),
                MaSpeechify.SpeechMark(15, 17, 750, 875, "to", "word"),
                MaSpeechify.SpeechMark(18, 27, 875, 1850, "Speechify", "word"),
            ),
        )
        val marks = MaSpeechify.flatten(root)
        assertEquals(4, marks.size)
        // The point of the whole integration: the offsets index the string that was sent, so the
        // highlight needs no alignment pass to work out which characters to light.
        assertEquals("to", sentence.substring(marks[2].start, marks[2].end))
        assertEquals("Speechify", sentence.substring(marks[3].start, marks[3].end))
        assertEquals(125, marks[0].startMs)
        assertEquals(1850, marks[3].endMs)
    }

    /**
     * The docs show two levels and do not promise there will never be three, so the walk keeps
     * leaves rather than trusting `type`. A third level appearing later would otherwise drop every
     * word silently, which reads on the phone as a reader that has gone mute.
     */
    @Test
    fun aDeeperTreeStillYieldsItsWords() {
        val word = MaSpeechify.SpeechMark(0, 2, 0, 100, "hi", "word")
        val sentence = MaSpeechify.SpeechMark(0, 2, 0, 100, "hi", "sentence", listOf(word))
        val paragraph = MaSpeechify.SpeechMark(0, 2, 0, 100, "hi", "paragraph", listOf(sentence))
        assertEquals(1, MaSpeechify.flatten(paragraph).size)
        assertTrue(MaSpeechify.flatten(null).isEmpty())
    }

    /**
     * 402 means three different things here and they are fixed in three different places, so the
     * sentence under the light has to say which. All three are QUOTA_EXCEEDED so the key fallback
     * still rolls on to the next key.
     */
    @Test
    fun theThreeMeaningsOf402AreKeptApart() {
        val cap = MaSpeechify.classify(402, "spend_cap_exceeded", "x")
        assertEquals(DictateApiException.Kind.QUOTA_EXCEEDED, cap.kind)
        assertTrue(cap.message!!.contains("own monthly spend cap"))

        val budget = MaSpeechify.classify(402, "spend_budget_exceeded", "x")
        assertTrue(budget.message!!.contains("workspace monthly budget"))

        val balance = MaSpeechify.classify(402, "payment_required", "x")
        assertTrue(balance.message!!.contains("balance"))

        assertEquals(
            DictateApiException.Kind.INVALID_API_KEY,
            MaSpeechify.classify(401, null, "x").kind,
        )
        assertEquals(
            DictateApiException.Kind.QUOTA_EXCEEDED,
            MaSpeechify.classify(429, null, "x").kind,
        )
    }

    /** Four voices and no more, and the ids are the curated simba-3.2 set, not invented. */
    @Test
    fun theVoiceListIsTheFourOnTheScreen() {
        assertEquals(
            listOf("geffen_32", "beatrice_32", "dominic_32", "edmund_32"),
            MaSpeechify.ENGLISH_VOICES.map { it.id },
        )
    }
}

class MaUsageTest {

    private val key = "sk_aB3dE5fG7hJ9kL1mN3pQ5rS7"

    private fun ledger(): MaUsage.Ledger {
        var l = MaUsage.Ledger()
        l = MaUsage.record(l, "speechify", key, 1200, MaUsage.Unit.CHARACTER)
        l = MaUsage.record(l, "speechify", key, 800, MaUsage.Unit.CHARACTER)
        l = MaUsage.record(l, "assemblyai", "abcd1234", 90_000, MaUsage.Unit.MILLISECOND)
        return l
    }

    @Test
    fun charactersAndMinutesAddUpPerProvider() {
        val l = ledger()
        val speechify = MaUsage.totals(l, "speechify", MaUsage.tail(key))
        assertEquals(2000L, speechify.characters)
        assertEquals(2, speechify.calls)

        val assembly = MaUsage.totals(l, "assemblyai", rate = 0.15)
        assertTrue(abs(assembly.minutes - 1.5) < 1e-9)
        // 1.5 minutes at $0.15 an hour.
        assertTrue(abs(assembly.cost - 0.00375) < 1e-9)
    }

    /** The file sits in app storage unencrypted, so it holds four characters of a key and no more. */
    @Test
    fun theLedgerNeverHoldsAKey() {
        val serialized = MaUsage.serialize(ledger())
        assertFalse(serialized.contains("sk_aB3dE5"))
        assertTrue(serialized.contains(MaUsage.tail(key)))
    }

    @Test
    fun aCorruptFileReadsAsEmptyRatherThanCrashing() {
        assertTrue(MaUsage.parse("{ not json at all").entries.isEmpty())
        assertTrue(MaUsage.parse(null).entries.isEmpty())
        assertEquals(3, MaUsage.parse(MaUsage.serialize(ledger())).entries.size)
    }

    /** With no verified price, the app shows the volume and says so instead of printing $0.00. */
    @Test
    fun noRateMeansNoInventedMoney() {
        assertTrue(MaUsage.describeKey(ledger(), "speechify", key, 0.0).contains("no rate set"))
        assertTrue(
            MaUsage.describeKey(ledger(), "speechify", "sk_never_used", 0.0)
                .contains("not used from this phone yet")
        )
        assertEquals("under \$0.01", MaUsage.money(0.004))
        assertEquals("\$1.24", MaUsage.money(1.239))
    }

    /**
     * Sixty keys is a real number for Marko, and the per-key path did not survive it: at sixty keys
     * and a year of entries it took 161 ms to draw the lines, on the main thread, on every
     * recomposition. [MaUsage.describeAll] walks the ledger twice instead of twice per key and came
     * back in 23 ms with identical answers. This asserts the identical part; the speed is what it is
     * as long as the shape stays a single pass.
     */
    @Test
    fun sixtyKeysGetTheSameAnswersFromTheSinglePass() {
        val keys = (1..60).map { "sk_" + "abcdefghijklmnopqrstuvwxyz".take(20) + "%04d".format(it) }
        var l = MaUsage.Ledger()
        keys.forEachIndexed { i, key ->
            repeat(5) { l = MaUsage.record(l, "speechify", key, (100L + i), MaUsage.Unit.CHARACTER) }
        }
        val map = MaUsage.describeAll(l, "speechify", 0.0)
        assertEquals(60, map.size)
        for (key in keys) {
            assertEquals(
                MaUsage.describeKey(l, "speechify", key, 0.0),
                map[MaUsage.tail(key)],
            )
        }
        // Tails must not collide, or two keys share one line and both are wrong.
        assertEquals(60, keys.map { MaUsage.tail(it) }.toSet().size)
    }

    /**
     * The Sunday case. Calendar numbers Sunday as 1, so the obvious subtraction sends Sunday back to
     * the week that has not started yet and the figure is wrong one day in seven, which is exactly
     * often enough never to be noticed.
     */
    @Test
    fun theWeekStartsOnMondayIncludingOnSunday() {
        val zone = TimeZone.getTimeZone("Europe/Zagreb")
        fun at(y: Int, m: Int, d: Int): Long {
            val c = Calendar.getInstance(zone)
            c.set(y, m - 1, d, 13, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        fun dayOf(t: Long): Int {
            val c = Calendar.getInstance(zone)
            c.timeInMillis = t
            return c.get(Calendar.DAY_OF_MONTH)
        }
        // 10.8.2026 is a Monday.
        assertEquals(10, dayOf(MaUsage.startOfWeek(at(2026, 8, 10), zone)))
        assertEquals(10, dayOf(MaUsage.startOfWeek(at(2026, 8, 15), zone)))
        assertEquals(10, dayOf(MaUsage.startOfWeek(at(2026, 8, 16), zone)))
        assertEquals(1, dayOf(MaUsage.startOfMonth(at(2026, 8, 22), zone)))
    }
}
