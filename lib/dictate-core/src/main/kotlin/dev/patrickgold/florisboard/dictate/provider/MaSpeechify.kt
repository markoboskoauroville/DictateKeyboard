/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Speechify, the reader's voice. Text in, audio and word timings out.
 *
 * This is the one place in the app that talks to Speechify, and it is deliberately **not** part of
 * [OpenAiCompatibleClient]: nothing about this API is OpenAI shaped. It is also not a transcription
 * provider. Speechify has no speech to text endpoint at all, which is written up in HANDOFF.md so
 * nobody researches it a third time; the whole product is text to speech.
 *
 * **Why it is worth having beside the Edge voice.** The engine's `MaAlign` exists to guess which
 * characters of the visible sentence a spoken boundary refers to, because Microsoft's endpoint
 * returns text and a time and nothing else. Speechify returns `start` and `end` **character
 * offsets** with every word, alongside `start_time` and `end_time`. The guess disappears. That is
 * the reason to use it, not the voices.
 *
 * **What it cannot do, verified against the language support page rather than hoped for.** Croatian
 * is listed under *Coming soon*, not supported and not even beta, and `simba-3.2` and
 * `simba-english` are English only. So this replaces the Edge voice for English and for English
 * alone; Croatian stays on Edge until `hr-HR` ships. Do not point `MaLanguage`'s Croatian half here
 * and hope: a non-English voice on an English model is answered with a flat 400.
 *
 * Everything below is read off the live API reference, not remembered:
 *
 * ```
 * POST https://api.speechify.ai/v1/audio/speech
 * Authorization: Bearer sk_...          <- Bearer prefix, unlike AssemblyAI's raw key
 * Content-Type: application/json
 * { "input": ..., "voice_id": ..., "model": ..., "audio_format": "mp3", "language": "en-US" }
 * -> { "audio_data": <base64>, "audio_format": ..., "billable_characters_count": N,
 *      "speech_marks": { start, end, start_time, end_time, value, chunks: [ ... ] } }
 * ```
 *
 * Hard limit **2000 characters per request** on this endpoint (20000 on the streaming one, which we
 * do not use: the reader already works one sentence at a time, so a sentence never approaches
 * either figure and a single envelope is simpler than a chunked response). Errors arrive in one
 * envelope, `{ "error": { "code", "message" }, "request_id" }`, and `error.code` is documented as
 * stable and safe to branch on while `message` is not.
 */
object MaSpeechify {

    const val BASE_URL = "https://api.speechify.ai/"

    /** English, current generation, and the model the curated `*_32` voices belong to. */
    const val MODEL_ENGLISH = "simba-3.2"

    /**
     * The 30+ locale model. Kept as a constant because it is what Croatian will need on the day
     * `hr-HR` leaves the coming-soon list, and naming it here is cheaper than finding it again.
     */
    const val MODEL_MULTILINGUAL = "simba-multilingual"

    /** The endpoint's own ceiling. Longer input is refused, so it is cut here instead. */
    const val MAX_INPUT_CHARS = 2000

    /**
     * The four English voices, in the order the picker shows them.
     *
     * Four, matching the reader's existing rule of four voices and no more. They are the curated
     * `simba-3.2` set; the full catalog is larger and a longer list is a worse control, not a
     * better one. Marko chose these four from the console.
     */
    val ENGLISH_VOICES: List<Voice> = listOf(
        Voice("geffen_32", "Geffen", male = true),
        Voice("beatrice_32", "Beatrice", male = false),
        Voice("dominic_32", "Dominic", male = true),
        Voice("edmund_32", "Edmund", male = true),
    )

    data class Voice(val id: String, val displayName: String, val male: Boolean)

    /**
     * One spoken word: where it sits in the text that was sent, and when it is said.
     *
     * [start] and [end] are character offsets into the input string, which is what makes this worth
     * having. [startMs] and [endMs] are milliseconds from the beginning of the clip.
     */
    data class Mark(
        val start: Int,
        val end: Int,
        val startMs: Int,
        val endMs: Int,
        val value: String,
    )

    /** A finished synthesis: the audio, the word timings, and what it cost. */
    data class Spoken(
        val audio: ByteArray,
        val marks: List<Mark>,
        val billableCharacters: Int,
        val format: String,
    ) {
        // Data classes with an array member need these written out, or equality compares references
        // and two identical clips are never equal. The cache keys on text and voice, not on this,
        // but a surprising equals is the kind of thing that costs an afternoon later.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Spoken) return false
            return audio.contentEquals(other.audio) && marks == other.marks &&
                billableCharacters == other.billableCharacters && format == other.format
        }

        override fun hashCode(): Int {
            var result = audio.contentHashCode()
            result = 31 * result + marks.hashCode()
            result = 31 * result + billableCharacters
            result = 31 * result + format.hashCode()
            return result
        }
    }

    /**
     * What a key test learned. [ok] is the light; [detail] is the sentence under it.
     *
     * [billableCharacters] comes back from the probe itself, which is the point of probing by
     * synthesising rather than by listing voices: a key that can list voices but can no longer spend
     * is a key that fails the moment it is actually needed, and a green light on it is a lie.
     */
    data class Probe(
        val ok: Boolean,
        val kind: DictateApiException.Kind?,
        val detail: String,
        val billableCharacters: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Speaks [text] and returns the clip with its word timings.
     *
     * @param language a BCP-47 locale such as `en-US`. Documented as optional and as improving
     *   quality when the language is known, which for this app it always is: the keyboard has
     *   exactly two languages and knows which one is active.
     */
    @Throws(DictateApiException::class)
    fun synthesize(
        key: String,
        text: String,
        voiceId: String = ENGLISH_VOICES.first().id,
        model: String = MODEL_ENGLISH,
        language: String? = "en-US",
        audioFormat: String = "mp3",
        client: OkHttpClient = defaultClient,
    ): Spoken {
        val input = text.take(MAX_INPUT_CHARS)
        if (input.isBlank()) {
            throw DictateApiException(DictateApiException.Kind.UNKNOWN, "nothing to speak")
        }
        val body = json.encodeToString(
            SpeechRequest.serializer(),
            SpeechRequest(
                input = input,
                voiceId = voiceId,
                model = model,
                audioFormat = audioFormat,
                language = language,
            ),
        )
        val response = call(client, key, "v1/audio/speech", body)
        val parsed = runCatching { json.decodeFromString(SpeechResponse.serializer(), response) }
            .getOrElse {
                throw DictateApiException(
                    DictateApiException.Kind.UNKNOWN,
                    "Speechify sent a reply this app could not read",
                    it,
                )
            }
        val audio = runCatching { Base64.getDecoder().decode(parsed.audioData) }
            .getOrElse {
                throw DictateApiException(
                    DictateApiException.Kind.FORMAT_NOT_SUPPORTED,
                    "the audio could not be decoded",
                    it,
                )
            }
        return Spoken(
            audio = audio,
            marks = flatten(parsed.speechMarks),
            billableCharacters = parsed.billableCharactersCount ?: input.length,
            format = parsed.audioFormat ?: audioFormat,
        )
    }

    /**
     * Tests one key by making the smallest real request there is.
     *
     * It synthesises two characters. That costs essentially nothing on a per-character bill and it
     * is the only test that answers the question actually being asked, which is not "does this
     * string authenticate" but "will this key produce audio when the reader asks it to". Listing
     * voices would answer the first and quietly pass a key whose balance is gone.
     */
    fun probe(key: String, client: OkHttpClient = defaultClient): Probe {
        var attempt = 0
        while (true) {
            attempt++
            try {
                val spoken = synthesize(key = key, text = "ok", client = client)
                return Probe(
                    ok = true,
                    kind = null,
                    detail = "works, spoke " + spoken.marks.size + " word" +
                        (if (spoken.marks.size == 1) "" else "s"),
                    billableCharacters = spoken.billableCharacters,
                )
            } catch (e: DictateApiException) {
                // A 429 during a bulk test is about how fast the requests arrived, not about this
                // key. Testing fifty keys is exactly what provokes it, and reporting it as "no
                // quota" paints a healthy key amber and invites Marko to delete it. So: back off
                // once, then report it as NOT CHECKED rather than as a verdict.
                if (e.httpStatus == 429 && attempt == 1) {
                    Thread.sleep(RATE_LIMIT_BACKOFF_MS)
                    continue
                }
                if (e.httpStatus == 429) {
                    return Probe(
                        ok = false,
                        kind = DictateApiException.Kind.TIMEOUT,
                        detail = "too many tests at once, this key was not checked",
                    )
                }
                return Probe(
                    ok = false,
                    kind = e.kind,
                    detail = MaKeys.tidyError(e.message, "could not be checked"),
                )
            } catch (e: Exception) {
                return Probe(
                    ok = false,
                    kind = DictateApiException.Kind.NETWORK,
                    detail = MaKeys.tidyError(e.message, "could not be checked"),
                )
            }
        }
    }

    /** Long enough to clear a per-second window, short enough not to stall a long test run. */
    private const val RATE_LIMIT_BACKOFF_MS = 1500L

    /**
     * Flattens the nested speech-mark tree into the words, in order.
     *
     * The reply is one chunk for the whole utterance holding a chunk per word, and the docs do not
     * promise the nesting is only ever two deep, so this recurses and keeps the leaves. Anything
     * with children is a container, whatever its `type` says: trusting the label rather than the
     * shape is how a future third level would silently vanish.
     */
    internal fun flatten(root: SpeechMark?): List<Mark> {
        if (root == null) return emptyList()
        val out = mutableListOf<Mark>()
        fun walk(node: SpeechMark) {
            val children = node.chunks
            if (children.isNullOrEmpty()) {
                val value = node.value.orEmpty()
                if (value.isNotEmpty()) {
                    out += Mark(
                        start = node.start ?: 0,
                        end = node.end ?: ((node.start ?: 0) + value.length),
                        startMs = node.startTime ?: 0,
                        endMs = node.endTime ?: (node.startTime ?: 0),
                        value = value,
                    )
                }
            } else {
                children.forEach { walk(it) }
            }
        }
        walk(root)
        return out.sortedBy { it.startMs }
    }

    /** One POST, with the error envelope turned into the app's own exception type. */
    private fun call(client: OkHttpClient, key: String, path: String, body: String): String {
        val request = Request.Builder()
            .url(BASE_URL + path)
            // Bearer, unlike AssemblyAI which takes the raw key. Getting this wrong is a flat 401
            // and looks exactly like a dead key, so it is worth the comment.
            .header("Authorization", "Bearer " + key.trim())
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw DictateApiException.fromIo(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (it.isSuccessful) return text
            val error = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), text) }
                .getOrNull()?.error
            throw classify(it.code, error?.code, error?.message ?: text)
        }
    }

    /**
     * Maps Speechify's status and error code onto the app's kinds.
     *
     * The shared [DictateApiException.fromHttp] is close but not close enough here, because 402 on
     * this API means three different things and two of them are fixable by Marko in different
     * places. `spend_cap_exceeded` is the monthly limit set on that one key; `payment_required` is
     * the workspace balance; `spend_budget_exceeded` is the workspace's own monthly budget. All
     * three are QUOTA_EXCEEDED so the key-fallback loop rolls on to the next key, but the sentence
     * under the light says which, because "out of quota" sends somebody to the wrong screen.
     */
    internal fun classify(status: Int, code: String?, message: String?): DictateApiException {
        val detail = when (code) {
            "spend_cap_exceeded" -> "this key hit its own monthly spend cap"
            "spend_budget_exceeded" -> "the workspace monthly budget is used up"
            "payment_required" -> "the workspace balance needs topping up"
            "voice_not_found" -> "that voice does not exist on this account"
            "speech_marks_unsupported" -> "this model returns no word timings"
            else -> message
        }
        val kind = when {
            status == 401 -> DictateApiException.Kind.INVALID_API_KEY
            status == 402 -> DictateApiException.Kind.QUOTA_EXCEEDED
            status == 429 -> DictateApiException.Kind.QUOTA_EXCEEDED
            status == 413 -> DictateApiException.Kind.CONTENT_SIZE_LIMIT
            status in 500..599 -> DictateApiException.Kind.SERVER_ERROR
            status == 400 && code == "speech_marks_unsupported" ->
                DictateApiException.Kind.FORMAT_NOT_SUPPORTED
            else -> DictateApiException.Kind.UNKNOWN
        }
        return DictateApiException(kind, detail ?: ("HTTP " + status), null, status)
    }

    @Serializable
    internal data class SpeechRequest(
        val input: String,
        @SerialName("voice_id") val voiceId: String,
        val model: String,
        @SerialName("audio_format") val audioFormat: String,
        val language: String? = null,
    )

    @Serializable
    internal data class SpeechResponse(
        @SerialName("audio_data") val audioData: String = "",
        @SerialName("audio_format") val audioFormat: String? = null,
        @SerialName("billable_characters_count") val billableCharactersCount: Int? = null,
        @SerialName("speech_marks") val speechMarks: SpeechMark? = null,
    )

    @Serializable
    internal data class SpeechMark(
        val start: Int? = null,
        val end: Int? = null,
        @SerialName("start_time") val startTime: Int? = null,
        @SerialName("end_time") val endTime: Int? = null,
        val value: String? = null,
        val type: String? = null,
        val chunks: List<SpeechMark>? = null,
    )

    @Serializable
    internal data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    internal data class ErrorBody(val code: String? = null, val message: String? = null)
}
