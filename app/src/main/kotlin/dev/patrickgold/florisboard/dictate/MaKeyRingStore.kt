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
import dev.patrickgold.florisboard.dictate.provider.MaKeyRing
import java.io.File

/**
 * Where the key ring lives, one small file per provider.
 *
 * Separate from [MaUsageStore] on purpose even though both are JSON in `filesDir`. The ledger is an
 * append-only log that grows past a megabyte; the ring is a handful of lines that is read before
 * every spoken sentence. Putting them in one file would mean parsing the log to answer "which key",
 * which is the question that has to be fast.
 *
 * Held in memory per provider and written on change. A failure to write is swallowed: losing the
 * flag costs one wasted request next time, while throwing here would cost the sentence.
 */
object MaKeyRingStore {

    private val cache = HashMap<String, MaKeyRing.Ring>()

    private fun file(context: Context, providerId: String) =
        File(context.filesDir, "ma_keyring_$providerId.json")

    fun load(context: Context, providerId: String): MaKeyRing.Ring = synchronized(this) {
        cache[providerId]?.let { return it }
        val raw = runCatching {
            file(context, providerId).takeIf { it.exists() }?.readText()
        }.getOrNull()
        return MaKeyRing.parse(raw).also { cache[providerId] = it }
    }

    fun save(context: Context, providerId: String, ring: MaKeyRing.Ring) = synchronized(this) {
        cache[providerId] = ring
        runCatching { file(context, providerId).writeText(MaKeyRing.serialize(ring)) }
        Unit
    }

    /** Clears one key's flag, which is what testing it by hand means. */
    fun forget(context: Context, providerId: String, key: String) {
        save(context, providerId, MaKeyRing.forget(load(context, providerId), key))
    }
}
