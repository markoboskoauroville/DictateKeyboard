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
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.MaKeyRing
import dev.patrickgold.florisboard.dictate.provider.MaKeys
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

    /**
     * The application context, bound once from `FlorisApplication.onCreate`.
     *
     * The ring is needed at a dozen call sites in `DictateController`, several of which are deep
     * inside suspend functions that were never handed a Context and have no business acquiring one.
     * Threading a parameter through all of them to reach a file in the app's own directory is a lot
     * of noise for very little, so it is bound once instead.
     *
     * This is an **application** context, so it cannot leak an activity or a service, and every
     * accessor below tolerates it being null: a ring that cannot be read simply means no memory,
     * which degrades to the old behaviour of walking the list from the top rather than to a crash.
     */
    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

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

    // The bound-context versions, for callers with no Context of their own. They return an empty
    // ring rather than throwing when nothing is bound, so the worst case is a forgetful keyring and
    // never a failed dictation.

    fun load(providerId: String): MaKeyRing.Ring =
        appContext?.let { load(it, providerId) } ?: MaKeyRing.Ring()

    fun save(providerId: String, ring: MaKeyRing.Ring) {
        appContext?.let { save(it, providerId, ring) }
    }

    /**
     * The keys of [stored], in the order they should be tried: the one that worked last time first,
     * then the rest as Marko arranged them, flagged ones last.
     *
     * This is also the fix for a bug that predates the ring. Several call sites passed
     * `account.apiKey` straight into a client, and that field holds **every** key separated by
     * newlines. With one key it worked; with two it sent both as a single credential and the service
     * answered with a complaint about a line break in the header. Anything reaching for a key now
     * comes through here and gets one key.
     */
    fun keys(providerId: String, stored: String): List<String> =
        MaKeyRing.order(MaKeys.split(stored).filter { it.isNotBlank() }, load(providerId))

    /** The single key to use right now, or the raw field if there is nothing usable to pick from. */
    fun currentKey(providerId: String, stored: String): String =
        keys(providerId, stored).firstOrNull() ?: stored.trim()

    /** Records what a call learned, so the next one starts from the right key. */
    fun onSuccess(context: Context, providerId: String, key: String) {
        save(context, providerId, MaKeyRing.onSuccess(load(context, providerId), key))
    }

    fun onFailure(
        context: Context,
        providerId: String,
        key: String,
        kind: DictateApiException.Kind,
        detail: String = "",
    ) {
        val ring = load(context, providerId)
        val updated = MaKeyRing.onFailure(ring, key, kind, detail)
        if (updated !== ring) save(context, providerId, updated)
    }

    fun onSuccess(providerId: String, key: String) {
        save(providerId, MaKeyRing.onSuccess(load(providerId), key))
    }

    fun onFailure(providerId: String, key: String, kind: DictateApiException.Kind, detail: String = "") {
        val ring = load(providerId)
        val updated = MaKeyRing.onFailure(ring, key, kind, detail)
        if (updated !== ring) save(providerId, updated)
    }
}
