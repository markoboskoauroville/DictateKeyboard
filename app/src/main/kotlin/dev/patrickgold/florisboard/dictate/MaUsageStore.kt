/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.dictate.provider.MaUsage
import java.io.File

/**
 * Where the usage ledger lives on disk, and the only thing in the app that knows that.
 *
 * [MaUsage] itself is pure arithmetic with no Android in it, which is what lets it be compiled and
 * run in the sandbox rather than discovered to be wrong by CI. This is the other half: one file in
 * the app's own directory, read once and kept in memory, written after each recorded call.
 *
 * It is deliberately **not** a preference. The ledger grows by an entry per dictation and per spoken
 * sentence, and a preference store is for settings a person chose, not for an append-only log that
 * would be copied whole on every write and dragged into every backup and restore.
 *
 * Writes are cheap and infrequent relative to anything else happening at the time (a network round
 * trip has just finished), so there is no batching here and no thread to get wrong.
 */
object MaUsageStore {

    private const val FILE_NAME = "ma_usage.json"

    @Volatile
    private var cached: MaUsage.Ledger? = null

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** The ledger, read from disk on first use and held afterwards. */
    fun load(context: Context): MaUsage.Ledger {
        cached?.let { return it }
        val parsed = synchronized(this) {
            cached ?: run {
                val raw = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()
                MaUsage.parse(raw).also { cached = it }
            }
        }
        return parsed
    }

    /**
     * Records one call and persists it.
     *
     * A failure to write is swallowed on purpose. This is a meter, not the work: losing a line of it
     * because the disk was momentarily unhappy must never take down the dictation or the sentence
     * being read, which is what an exception thrown from here would do.
     */
    fun record(
        context: Context,
        providerId: String,
        key: String,
        amount: Long,
        unit: MaUsage.Unit,
    ) {
        if (amount <= 0L) return
        synchronized(this) {
            val updated = MaUsage.record(load(context), providerId, key, amount, unit)
            cached = updated
            runCatching { file(context).writeText(MaUsage.serialize(updated)) }
        }
    }

    /** Forgets everything counted so far. Used by the reset in Settings. */
    fun clear(context: Context) {
        synchronized(this) {
            cached = MaUsage.Ledger()
            runCatching { file(context).delete() }
        }
    }
}
