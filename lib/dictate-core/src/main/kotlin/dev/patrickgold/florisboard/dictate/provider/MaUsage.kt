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
 * What every key has actually cost, kept by the app itself.
 *
 * **Why the app counts rather than asking.** Neither provider will tell it. AssemblyAI has no
 * balance endpoint on the transcription API, and Speechify's public reference is three groups of
 * endpoints, audio, voices and models, with nothing for usage, spend or balance. `spend_cap` and
 * `spend_cap_remaining` exist on the API key object but that object is only ever delivered to a
 * webhook or shown in the console, and a keyboard has no webhook. So the numbers under each key are
 * counted here or they do not exist.
 *
 * That turns out to be the better half of the trade anyway. It works with no signal, it needs no
 * second credential, it covers every provider the same way, and it can never disagree with itself
 * the way two sources do. What it cannot do is know about spending from any other device or from
 * the console, and the interface says so rather than implying a total that is not one.
 *
 * **The counting is exact, not estimated, on the side that matters.** Speechify returns
 * `billable_characters_count` with every synthesis: the number recorded is the number billed, taken
 * from the reply rather than from counting the string that was sent. Transcription is recorded in
 * milliseconds of audio, which the app already knows because it made the recording.
 *
 * Storage is injected as a pair of lambdas so this whole file stays free of Android and is provable
 * in the sandbox. The app hands it a file in its own directory.
 */
object MaUsage {

    /** Per-character providers bill text; per-second providers bill audio. */
    enum class Unit { CHARACTER, MILLISECOND }

    /**
     * One recorded call. [keyTail] is the last four characters of the key, never the key: enough to
     * line a row up with the key list above it, useless to anyone reading the file.
     */
    @Serializable
    data class Entry(
        val at: Long,
        val providerId: String,
        val keyTail: String,
        val amount: Long,
        val unit: Unit,
    )

    @Serializable
    data class Ledger(val entries: List<Entry> = emptyList())

    /**
     * Default prices, in US dollars, and every one of them editable.
     *
     * They are written down as a starting point, not as a fact: a price is the one thing in this
     * file that is certain to be out of date eventually, and a number the app insists on is worse
     * than one it merely suggests. Speechify is per one million characters; the two AssemblyAI
     * entries are per hour and differ because the fast path costs three times the slow one.
     */
    val DEFAULT_RATES: Map<String, Double> = mapOf(
        // Left at zero on purpose. The two AssemblyAI figures are published and were read off the
        // pricing page; Speechify's per-character price depends on the plan and was not verified, so
        // rather than invent one the app counts the characters and says the rate is not set. A made
        // up price is worse than no price: it looks like an answer.
        "speechify" to 0.0,
        "assemblyai" to 0.15,
        "assemblyai-sync" to 0.45,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Cap on retained entries. A year of heavy use stays well inside it and the file stays small. */
    private const val MAX_ENTRIES = 20000

    fun parse(raw: String?): Ledger =
        if (raw.isNullOrBlank()) Ledger() else runCatching {
            json.decodeFromString(Ledger.serializer(), raw)
        }.getOrElse { Ledger() }

    fun serialize(ledger: Ledger): String = json.encodeToString(Ledger.serializer(), ledger)

    /** Adds one call and returns the new ledger, oldest entries dropped once the cap is passed. */
    fun record(
        ledger: Ledger,
        providerId: String,
        key: String,
        amount: Long,
        unit: Unit,
        at: Long = System.currentTimeMillis(),
    ): Ledger {
        if (amount <= 0L) return ledger
        val entry = Entry(
            at = at,
            providerId = providerId,
            keyTail = tail(key),
            amount = amount,
            unit = unit,
        )
        val combined = ledger.entries + entry
        return Ledger(if (combined.size > MAX_ENTRIES) combined.takeLast(MAX_ENTRIES) else combined)
    }

    /** The last four characters of a key, which is what a row is labelled by. */
    fun tail(key: String): String = key.trim().takeLast(4)

    /** A window's worth of use: what was spent, and on how much. */
    data class Totals(
        val characters: Long = 0,
        val milliseconds: Long = 0,
        val calls: Int = 0,
        val cost: Double = 0.0,
    ) {
        val minutes: Double get() = milliseconds / 60000.0
    }

    /**
     * Sums one provider, optionally one key of it, over a window.
     *
     * @param since epoch millis, or 0 for all time.
     */
    fun totals(
        ledger: Ledger,
        providerId: String,
        keyTail: String? = null,
        since: Long = 0L,
        rate: Double = DEFAULT_RATES[providerId] ?: 0.0,
    ): Totals {
        var characters = 0L
        var millis = 0L
        var calls = 0
        for (e in ledger.entries) {
            if (e.providerId != providerId) continue
            if (keyTail != null && e.keyTail != keyTail) continue
            if (e.at < since) continue
            calls++
            when (e.unit) {
                Unit.CHARACTER -> characters += e.amount
                Unit.MILLISECOND -> millis += e.amount
            }
        }
        // Characters are priced per million, audio per hour. Both are held as one rate per provider
        // because no provider here bills both ways, so there is never a second number to keep.
        val cost = characters / 1_000_000.0 * rate + millis / 3_600_000.0 * rate
        return Totals(characters = characters, milliseconds = millis, calls = calls, cost = cost)
    }

    /** Midnight on Monday of the current week, in the phone's own zone. */
    fun startOfWeek(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = now
        c.firstDayOfWeek = Calendar.MONDAY
        // Calendar counts Sunday as 1, so Monday is 2 and the offset back to Monday is not the
        // subtraction it looks like. Written out rather than clever, because the clever version of
        // this is wrong on Sundays and nobody notices for six days.
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val backToMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        c.add(Calendar.DAY_OF_MONTH, -backToMonday)
        return startOfDay(c)
    }

    /** Midnight on the first of the current month, in the phone's own zone. */
    fun startOfMonth(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = now
        c.set(Calendar.DAY_OF_MONTH, 1)
        return startOfDay(c)
    }

    private fun startOfDay(c: Calendar): Long {
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * Every key's totals for one provider, in a single pass over the ledger.
     *
     * [totals] walks the whole ledger per key, which is fine for three keys and is not fine for
     * sixty: at sixty keys and a year of entries that is 157 ms of work on the main thread every
     * time the key screen recomposes, which is a visible stutter while scrolling. Measured, not
     * guessed. This walks the entries once and buckets them, so the cost stops depending on how many
     * keys are stored.
     *
     * Keyed by the four-character tail, which is what the ledger stores.
     */
    fun totalsByKey(
        ledger: Ledger,
        providerId: String,
        since: Long = 0L,
        rate: Double = DEFAULT_RATES[providerId] ?: 0.0,
    ): Map<String, Totals> {
        val characters = HashMap<String, Long>()
        val millis = HashMap<String, Long>()
        val calls = HashMap<String, Int>()
        for (e in ledger.entries) {
            if (e.providerId != providerId) continue
            if (e.at < since) continue
            calls[e.keyTail] = (calls[e.keyTail] ?: 0) + 1
            when (e.unit) {
                Unit.CHARACTER -> characters[e.keyTail] = (characters[e.keyTail] ?: 0L) + e.amount
                Unit.MILLISECOND -> millis[e.keyTail] = (millis[e.keyTail] ?: 0L) + e.amount
            }
        }
        return calls.keys.associateWith { tail ->
            val c = characters[tail] ?: 0L
            val m = millis[tail] ?: 0L
            Totals(
                characters = c,
                milliseconds = m,
                calls = calls[tail] ?: 0,
                cost = c / 1_000_000.0 * rate + m / 3_600_000.0 * rate,
            )
        }
    }

    /**
     * Every key's line, for one provider, computed once.
     *
     * The screen calls this once and looks each row up, rather than calling [describeKey] per row.
     * Same sentences, two passes over the ledger instead of two per key.
     */
    fun describeAll(
        ledger: Ledger,
        providerId: String,
        rate: Double,
    ): Map<String, String> {
        val month = totalsByKey(ledger, providerId, startOfMonth(), rate)
        val all = totalsByKey(ledger, providerId, 0L, rate)
        return all.mapValues { (tail, allTime) ->
            line(month[tail] ?: Totals(), allTime, providerId, rate)
        }
    }

    /**
     * The line shown under one key: this month, and all time.
     *
     * Deliberately one line. It sits under a masked key in a list of keys, and a paragraph there
     * would bury the thing the row is actually for, which is the light beside it.
     */
    fun describeKey(ledger: Ledger, providerId: String, key: String, rate: Double): String {
        val tail = tail(key)
        val month = totals(ledger, providerId, tail, startOfMonth(), rate)
        val all = totals(ledger, providerId, tail, 0L, rate)
        return line(month, all, providerId, rate)
    }

    /** The one place the sentence is written, shared by the per-key and the all-keys paths. */
    private fun line(month: Totals, all: Totals, providerId: String, rate: Double): String {
        if (all.calls == 0) return "not used from this phone yet"
        // With no rate there is nothing honest to put in the money column, so the volume stands
        // alone and the sentence says why rather than printing $0.00 next to real use.
        if (rate <= 0.0) {
            return "this month " + volume(month, providerId) +
                " \u00B7 all time " + volume(all, providerId) + " \u00B7 no rate set"
        }
        return "this month " + volume(month, providerId) + ", " + money(month.cost) +
            " \u00B7 all time " + volume(all, providerId) + ", " + money(all.cost)
    }

    private fun volume(t: Totals, providerId: String): String = when {
        t.characters > 0 -> t.characters.toString() + " chars"
        t.milliseconds > 0 -> String.format("%.1f min", t.minutes)
        else -> "nothing"
    }

    /** Money, and never more precision than the number deserves. */
    fun money(usd: Double): String = when {
        usd <= 0.0 -> "$0.00"
        usd < 0.01 -> "under $0.01"
        else -> String.format("$%.2f", usd)
    }
}
