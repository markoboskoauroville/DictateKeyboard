/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * What AssemblyAI itself will say about a key, beyond "it works".
 *
 * Marko asked whether a key test could report more than a green light. It can, and this is exactly
 * how far it goes, checked against the live API rather than hoped for:
 *
 * - **`GET /v2/transcript`** lists the transcripts made with that key: id, status, created,
 *   completed, error. Up to 200 a page, sorted newest first, and **the last 90 days only**. That is
 *   real server-side history, so it counts work done from the laptop and from any other device,
 *   which the app's own ledger cannot see.
 * - **There is no usage or balance endpoint.** `/v2/usage`, `/v2/billing` and `/v2/account/usage`
 *   are all 404. `/v2/account` answers `200 {}` to an invalid key, so it returns an empty object to
 *   anybody and tells us nothing; it is not the balance endpoint a scanning vendor's page suggests
 *   it is. The balance lives on AssemblyAI's billing page and nowhere else.
 * - **The list carries no `audio_duration`.** That field is on the individual transcript, so minutes
 *   would cost one request per transcript: two hundred round trips to total one page, on a phone, on
 *   a settings screen. Not worth it, and not done. Minutes come from the local ledger, which knows
 *   them already because the app made the recordings.
 *
 * So the honest split is: **the service says how many and when, the ledger says how long and what it
 * cost.** Neither is guessed and neither pretends to be the other.
 */
object MaAssemblyStats {

    private const val BASE_URL = "https://api.assemblyai.com/"

    /** One page is the most the endpoint will give, and one page is plenty for a settings line. */
    private const val PAGE_LIMIT = 200

    /**
     * A key's history as the service knows it.
     *
     * [capped] is true when the page came back full, meaning there are more than [PAGE_LIMIT] and the
     * numbers are a floor rather than a total. The interface says "200+" in that case rather than a
     * precise-looking number that is really a page size.
     */
    data class History(
        val total: Int,
        val failed: Int,
        val lastAt: String?,
        val capped: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Reads the key's recent transcripts, or returns null if anything at all goes wrong.
     *
     * Null rather than an exception on purpose. This runs after a key has already passed its real
     * test, and it is a nicety on a settings row: a key that works must never be reported as broken
     * because the extra call that decorates it did not come back.
     */
    fun history(key: String, httpClient: OkHttpClient = client): History? {
        val request = Request.Builder()
            .url(BASE_URL + "v2/transcript?limit=" + PAGE_LIMIT)
            // Raw key, no Bearer prefix. AssemblyAI differs from Speechify here and getting it wrong
            // is a flat 401 that looks exactly like a dead key.
            .header("authorization", key.trim())
            .get()
            .build()
        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            return null
        }
        val parsed = runCatching { json.decodeFromString(ListResponse.serializer(), body) }.getOrNull()
            ?: return null
        val items = parsed.transcripts.orEmpty()
        if (items.isEmpty()) return History(0, 0, null, false)
        return History(
            total = items.size,
            failed = items.count { it.status == "error" },
            // Newest first, so the first one carries the most recent date. Trimmed to the day: the
            // question this answers is "is this key in use", and a timestamp to the microsecond
            // answers it no better while taking three times the width.
            lastAt = items.firstOrNull()?.created?.substringBefore('T'),
            capped = items.size >= PAGE_LIMIT,
        )
    }

    /** The line shown under a key, or null when there is nothing worth a line. */
    fun describe(history: History?): String? {
        if (history == null) return null
        if (history.total == 0) return "no transcriptions on this key in the last 90 days"
        val count = if (history.capped) PAGE_LIMIT.toString() + "+" else history.total.toString()
        val failed = if (history.failed > 0) ", " + history.failed + " failed" else ""
        val last = history.lastAt?.let { ", last " + it } ?: ""
        return count + " transcriptions in 90 days" + failed + last
    }

    @Serializable
    private data class ListResponse(val transcripts: List<Item>? = null)

    @Serializable
    private data class Item(
        val status: String? = null,
        val created: String? = null,
        @SerialName("resource_url") val resourceUrl: String? = null,
    )
}
