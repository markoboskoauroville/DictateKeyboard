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
import java.util.concurrent.TimeUnit

/**
 * Reads the words out of a picture, so the reader can read a screenshot aloud.
 *
 * One provider, Groq, on the chat-completions endpoint it already exposes in OpenAI shape: a `user`
 * message whose content is a list of parts, one `text` and one `image_url` carrying a base64 data
 * URI. The same envelope OpenAI uses, which is why this needs no new transport, only a new body.
 *
 * **The prompt is the feature.** A screenshot of a phone is mostly not the thing being read. Marko's
 * own example was a chat message with a status bar above it and a third of the screen taken by this
 * very keyboard, and a vision model told only "read this image" will faithfully return `12:01`,
 * `4G+`, `71`, `ENG FAST ab AB Ab`, `AP 1 2 3` and the rest of the furniture, mixed into the
 * sentences. Read aloud, that is unusable. So the instruction below spends most of its words saying
 * what **not** to return, and it is ordered from the most common intrusion downwards.
 *
 * Two further rules in it are worth defending because they look like fussiness and are not:
 *
 * - **No preamble.** Every instruction-tuned model wants to open with "Here is the text from the
 *   image:". That sentence is then spoken aloud, every single time, before the thing actually wanted.
 * - **Nothing when there is nothing.** A model asked for text in a picture with no prose will invent
 *   a description of the picture rather than return empty, and a description is exactly what this is
 *   not for. It is told to return nothing, and [extractText] treats that as nothing.
 */
object MaVision {

    /**
     * Groq's vision model, read off their own docs rather than remembered.
     *
     * Overridable per call because a model name is the single most perishable string in this file:
     * the vision model on this provider has changed name more than once and will again.
     */
    const val DEFAULT_MODEL = "qwen/qwen3.6-27b"

    /** Long edge, in pixels, that a screenshot is scaled to before sending. See MaScreenshot. */
    const val MAX_EDGE = 1568

    /**
     * What the model is told. Kept as one constant so there is exactly one copy to improve.
     *
     * Written as plain imperative sentences rather than a numbered spec, because that is the register
     * these models follow most reliably, and every clause here answers a way the reading went wrong.
     */
    const val OCR_PROMPT: String =
        "Read this screenshot and return only the words a person would actually want read aloud. " +
            "This is a phone screen, so most of it is not that. " +
            "Ignore the status bar completely: the clock, the date, signal bars, wifi, bluetooth, " +
            "battery percentage, and every small icon along the top edge. " +
            "Ignore the on-screen keyboard entirely, including its letter keys, its number keys, " +
            "its suggestion strip and any short labels on it such as ENG, HR, FAST, SLOW, AP, ab, " +
            "AB, db, or bare digits sitting on keys. " +
            "Ignore the navigation bar at the bottom edge. " +
            "Ignore buttons, tabs, menu items, toolbar icons, timestamps beside messages, and " +
            "counters such as likes or unread badges. " +
            "Return the body text: the message, the article, the document, the post, whatever is " +
            "being read. Keep its own paragraph breaks. " +
            "Transcribe the words exactly as written and do not translate, summarise, correct or " +
            "explain them. " +
            "Do not describe the image, the layout, or what application this is. " +
            "Do not write any introduction, heading or closing remark. Return the text and nothing " +
            "else. " +
            "If the screenshot contains no readable body text at all, return nothing."

    /** The reply, and what it cost, since the ledger records tokens the same way it records seconds. */
    data class Read(
        val text: String,
        val promptTokens: Int,
        val completionTokens: Int,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A screenshot is a large body going up over a phone connection, and the model then has
            // to look at it. Sixty seconds is the difference between a slow answer and no answer.
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Sends one image and returns the words in it.
     *
     * @param imageBase64 the image, already encoded, WITHOUT the `data:` prefix. The prefix is added
     *   here so there is one place that knows the wire format and callers only ever hold bytes.
     */
    @Throws(DictateApiException::class)
    fun read(
        key: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        model: String = DEFAULT_MODEL,
        baseUrl: String = ProviderRegistry.GROQ.baseUrl,
        client: OkHttpClient = defaultClient,
    ): Read {
        val body = json.encodeToString(
            VisionRequest.serializer(),
            VisionRequest(
                model = model,
                messages = listOf(
                    VisionMessage(
                        role = "user",
                        content = listOf(
                            ContentPart(type = "text", text = OCR_PROMPT),
                            ContentPart(
                                type = "image_url",
                                imageUrl = ImageUrl("data:$mimeType;base64,$imageBase64"),
                            ),
                        ),
                    ),
                ),
                // Low rather than zero. Transcription wants the likeliest token every time, and the
                // small amount left is only there because some models degenerate into repetition at
                // exactly zero.
                temperature = 0.1,
                maxCompletionTokens = 4096,
            ),
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + key.trim())
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw DictateApiException.fromIo(e)
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val message = runCatching {
                    json.decodeFromString(ErrorEnvelope.serializer(), raw).error?.message
                }.getOrNull()
                throw DictateApiException.fromHttp(it.code, message ?: raw)
            }
            val parsed = runCatching { json.decodeFromString(VisionResponse.serializer(), raw) }
                .getOrElse {
                    throw DictateApiException(
                        DictateApiException.Kind.UNKNOWN,
                        "the reply could not be read",
                        it,
                    )
                }
            return Read(
                text = extractText(parsed.choices.firstOrNull()?.message?.content),
                promptTokens = parsed.usage?.promptTokens ?: 0,
                completionTokens = parsed.usage?.completionTokens ?: 0,
            )
        }
    }

    /**
     * Cleans the reply into something worth speaking.
     *
     * The prompt asks for no preamble and mostly gets none, but "mostly" is not a thing to read
     * aloud, so the two habitual openings are stripped here as well. Belt and braces on purpose:
     * the prompt is a request and this is a guarantee.
     *
     * Fenced code blocks are unwrapped rather than dropped, because a model handed a screenshot of
     * code will often return it fenced, and the fence marks are not words.
     */
    internal fun extractText(content: String?): String {
        var text = content?.trim().orEmpty()
        if (text.isEmpty()) return ""

        // A whole reply wrapped in one fence: take the inside.
        if (text.startsWith("```")) {
            text = text.removePrefix("```").substringAfter('\n', "").substringBeforeLast("```").trim()
        }

        // "Here is the text from the image:" and its relatives, but ONLY when the line is short and
        // ends in a colon. A long first line that happens to contain the word "text" is the text.
        val firstBreak = text.indexOf('\n')
        if (firstBreak in 1..80) {
            val firstLine = text.substring(0, firstBreak).trim()
            val looksLikePreamble = firstLine.endsWith(":") &&
                PREAMBLE_WORDS.any { firstLine.contains(it, ignoreCase = true) }
            if (looksLikePreamble) text = text.substring(firstBreak + 1).trim()
        }

        // A model told to return nothing sometimes says so in words instead.
        if (NOTHING_REPLIES.any { text.equals(it, ignoreCase = true) }) return ""
        return text
    }

    private val PREAMBLE_WORDS = listOf(
        "here is", "here's", "the text", "extracted", "transcription", "screenshot", "image",
    )

    private val NOTHING_REPLIES = listOf(
        "", "nothing", "none", "no text", "no readable text",
        "there is no readable text.", "no readable body text",
    )

    @Serializable
    internal data class VisionRequest(
        val model: String,
        val messages: List<VisionMessage>,
        val temperature: Double,
        @SerialName("max_completion_tokens") val maxCompletionTokens: Int,
    )

    @Serializable
    internal data class VisionMessage(val role: String, val content: List<ContentPart>)

    @Serializable
    internal data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerialName("image_url") val imageUrl: ImageUrl? = null,
    )

    @Serializable
    internal data class ImageUrl(val url: String)

    @Serializable
    internal data class VisionResponse(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    )

    @Serializable
    internal data class Choice(val message: ReplyMessage? = null)

    @Serializable
    internal data class ReplyMessage(val content: String? = null)

    @Serializable
    internal data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0,
    )

    @Serializable
    internal data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    internal data class ErrorBody(val message: String? = null, val code: String? = null)
}
