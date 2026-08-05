#!/usr/bin/env python3
"""
MA TWIST v1, applied to the Dictate source before Gradle runs.

1. Providers stripped to AssemblyAI, Gemini and Anthropic.
2. The pulsating dot replaced by an oscilloscope, with the stopwatch and a
   braille spinner in front of it.
3. Colours taken from the MA house palette used across Marko's apps.

Every edit is anchored on an exact string from upstream. If an anchor is missing
the script stops with a clear message instead of half patching, which is what you
want when upstream changes underneath you.
"""

import io
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
REG = "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderRegistry.kt"
UI = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/DictateSmartbarUi.kt"
CTRL = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/DictateController.kt"
CLIENT = "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/OpenAiCompatibleClient.kt"
OPUS = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/audio/MaOpus.kt"

changes = []


def read(path):
    with io.open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(os.path.join(ROOT, path), "w", encoding="utf-8") as f:
        f.write(text)


def swap(text, old, new, label):
    if old not in text:
        sys.exit("MA TWIST failed, anchor missing: %s" % label)
    if text.count(old) != 1:
        sys.exit("MA TWIST failed, anchor not unique: %s" % label)
    changes.append(label)
    return text.replace(old, new, 1)


def ensure_import(text, statement):
    lines = text.split("\n")
    # Whole line comparison, never a substring: "layout.height" is a substring of the
    # "layout.heightIn" this file already imports, which silently skipped the import.
    if statement in lines:
        return text
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    lines.insert(last + 1, statement)
    return "\n".join(lines)


# --------------------------------------------------------------- 1. PROVIDERS
reg = read(REG)
reg = swap(
    reg,
    """    val presets: List<ProviderPreset> = listOf(
        OPENAI, GROQ, OPENROUTER, GEMINI, ANTHROPIC, TOGETHER, DEEPINFRA, MISTRAL, SONIOX,
        ELEVENLABS, DEEPGRAM, ASSEMBLYAI, XAI, DEEPSEEK, OLLAMA, LOCAL,
    )""",
    """    // MA TWIST: three cloud providers plus the on-device engine, nothing else. LOCAL is the
    // offline Whisper/Parakeet runner that needs no key and no network. The other presets stay
    // defined above so code referencing them by name still compiles, they are simply not offered.
    val presets: List<ProviderPreset> = listOf(
        ASSEMBLYAI, GEMINI, ANTHROPIC, LOCAL,
    )""",
    "provider list",
)
write(REG, reg)

# --------------------------------------------------------------- 2. RECORDING UI
ui = read(UI)

for imp in [
    "import androidx.compose.foundation.Canvas",
    "import androidx.compose.foundation.layout.Box",
    "import androidx.compose.foundation.layout.height",
    "import androidx.compose.foundation.layout.width",
    "import androidx.compose.runtime.mutableIntStateOf",
    "import androidx.compose.runtime.mutableStateListOf",
    "import androidx.compose.runtime.setValue",
    "import androidx.compose.runtime.getValue",
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.Path",
    "import androidx.compose.ui.graphics.drawscope.Stroke",
    "import androidx.compose.ui.text.font.FontWeight",
    "import androidx.compose.ui.unit.sp",
    "import androidx.compose.material3.Text",
]:
    ui = ensure_import(ui, imp)

# The dot is now unused. Kotlin only warns about that, but silencing it keeps the build log clean.
ui = swap(
    ui,
    """@Composable
private fun RecordingAudioDot(paused: Boolean, frozen: Boolean = false) {""",
    """@Suppress("unused") // MA TWIST: replaced by MaRecordingScope, kept so upstream diffs stay small
@Composable
private fun RecordingAudioDot(paused: Boolean, frozen: Boolean = false) {""",
    "silence the unused dot",
)

ui = swap(
    ui,
    """        RecordingAudioDot(paused = state.paused, frozen = ptt.discarding)
        Spacer(modifier = Modifier.width(10.dp))
        SnyggText(text = formatElapsed(elapsedMs))""",
    """        // MA TWIST: oscilloscope behind, braille spinner and stopwatch in front.
        MaRecordingScope(paused = state.paused, frozen = ptt.discarding, elapsedMs = elapsedMs)""",
    "recording centre",
)

ui += '''

// --------------------------------------------------------------------------------
// MA TWIST, Mantra Productions
// The house palette Marko uses across his apps, so the keyboard matches maha_transcribe.
// --------------------------------------------------------------------------------

private val MaCyan = Color(0xFF39D0D8)
private val MaViolet = Color(0xFFA371F7)
private val MaMuted = Color(0xFF8B949E)
private val MaInk = Color(0xFFE6EDF3)
private const val MA_BRAILLE = "\\u280B\\u2819\\u2839\\u2838\\u283C\\u2834\\u2826\\u2827\\u2807\\u280F"

/** How many level samples the scope keeps on screen. */
private const val MA_SCOPE_POINTS = 64

/** Sampling period of the scope, fast enough to look alive, slow enough to cost nothing. */
private const val MA_SCOPE_TICK_MS = 50L

/** Braille frame rate while recording. */
private const val MA_SPINNER_TICK_MS = 90L

/**
 * The centre of the recording bar: a live oscilloscope drawn behind, with the braille spinner and the
 * elapsed stopwatch in front of it.
 *
 * The scope keeps a rolling window of [DictateController.audioLevel] rather than reading the raw PCM,
 * because that flow is already smoothed and published at a rate the UI can follow. Alternate samples
 * are mirrored above and below the centre line, which is what turns a level meter into something that
 * reads as a waveform.
 *
 * Paused or discarded, everything freezes and dims instead of animating, on the same reasoning as the
 * dot this replaces: motion should mean something is being captured.
 */
@Composable
private fun MaRecordingScope(paused: Boolean, frozen: Boolean, elapsedMs: Long) {
    val still = paused || frozen
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(still) {
        while (!still) {
            history.add(DictateController.audioLevel.value)
            if (history.size > MA_SCOPE_POINTS) history.removeAt(0)
            delay(MA_SCOPE_TICK_MS)
        }
    }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) {
        while (!still) {
            frame++
            delay(MA_SPINNER_TICK_MS)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(FlorisImeSizing.smartbarHeight)
            .width(158.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val points = history.size
            if (points < 2) return@Canvas
            val midY = size.height / 2f
            val step = size.width / (points - 1)
            val path = Path()
            for (i in 0 until points) {
                val amp = history[i].coerceIn(0f, 1f) * (size.height * 0.40f)
                val y = if (i % 2 == 0) midY - amp else midY + amp
                val x = step * i
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = MaCyan.copy(alpha = if (still) 0.18f else 0.45f),
                style = Stroke(width = 2.5f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MA_BRAILLE[frame % MA_BRAILLE.length].toString(),
                color = if (still) MaMuted else MaViolet,
                fontSize = 17.sp,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = formatElapsed(elapsedMs),
                color = MaInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
'''
changes.append("oscilloscope composable")
write(UI, ui)


# --------------------------------------------------------------- 3. OPUS UPLOAD
# The recorder produces 16 kHz mono 16 bit WAV, about 1.9 MB a minute, and all of that
# uncompressed stream goes up the mobile connection. Opus at 20 kbps carries speech at the
# same intelligibility for roughly a tenth of the bytes, which is less waiting and less
# data. Any failure returns null and the original WAV is sent, so this can only help.

MA_OPUS_SOURCE = r'''/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Compresses the recorded WAV to Ogg Opus before a cloud upload.
 *
 * Speech recognition needs intelligibility, not fidelity. Opus at 20 kbps mono keeps every cue a
 * recogniser uses while cutting roughly nine tenths of the bytes off a 16 kHz PCM stream, so the
 * upload finishes sooner and costs less data. This is Android's own encoder, no ffmpeg and no
 * native library.
 *
 * Requires API 29, where MediaCodec gained an Opus encoder and MediaMuxer gained the Ogg container.
 * Below that, or on any failure at all, this returns null and the caller sends the original WAV, so
 * the worst case is exactly the behaviour that existed before.
 */
object MaOpus {

    /** Speech stays intelligible well below this; 20 kbps is a comfortable margin. */
    private const val BITRATE = 20_000

    /** Not worth the encoder setup for a very short take. */
    private const val MIN_BYTES = 48_000L

    private const val TIMEOUT_US = 10_000L

    private const val WAV_HEADER_BYTES = 44

    fun compress(wav: File): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (!wav.exists() || wav.length() < MIN_BYTES) return null
        val out = File(wav.parentFile, wav.nameWithoutExtension + ".ogg")
        return try {
            encode(wav, out)
            if (out.length() > 0L && out.length() < wav.length()) {
                out
            } else {
                out.delete()
                null
            }
        } catch (t: Throwable) {
            runCatching { out.delete() }
            null
        }
    }

    private fun encode(wav: File, out: File) {
        val raf = RandomAccessFile(wav, "r")
        try {
            val header = ByteArray(WAV_HEADER_BYTES)
            raf.readFully(header)
            val sampleRate = le32(header, 24)
            val channels = le16(header, 22)
            if (sampleRate <= 0 || channels <= 0) throw IllegalStateException("bad wav header")
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                sampleRate,
                channels,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            var track = -1
            var muxing = false
            val info = MediaCodec.BufferInfo()
            var presentationUs = 0L
            var eof = false
            val bytesPerFrame = channels * 2
            try {
                while (true) {
                    if (!eof) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buffer: ByteBuffer = codec.getInputBuffer(inIndex)!!
                            buffer.clear()
                            val chunk = ByteArray(buffer.capacity())
                            val read = raf.read(chunk)
                            if (read <= 0) {
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    presentationUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                eof = true
                            } else {
                                buffer.put(chunk, 0, read)
                                codec.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                                presentationUs += 1_000_000L * (read / bytesPerFrame) / sampleRate
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    } else if (outIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outIndex)
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (encoded != null && muxing && !isConfig && info.size > 0) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(track, encoded, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            } finally {
                runCatching { if (muxing) muxer.stop() }
                runCatching { muxer.release() }
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } finally {
            runCatching { raf.close() }
        }
    }

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
'''

os.makedirs(os.path.dirname(os.path.join(ROOT, OPUS)), exist_ok=True)
write(OPUS, MA_OPUS_SOURCE)
changes.append("MaOpus encoder written")

ctrl = read(CTRL)
ctrl = ensure_import(ctrl, "import dev.patrickgold.florisboard.dictate.audio.MaOpus")
ctrl = swap(
    ctrl,
    """                val request = TranscriptionRequest(
                    audioFile = uploadFile,""",
    """                // MA TWIST: shrink the upload. Cloud only, never the on-device engine, which is
                // handed the audio locally. Null on anything unexpected, and the WAV goes as before.
                if (preset.transcriptionApi != TranscriptionApi.LOCAL_ONDEVICE) {
                    MaOpus.compress(uploadFile)?.let { small ->
                        if (uploadFile !== audioFile) runCatching { uploadFile.delete() }
                        uploadFile = small
                    }
                }
                val request = TranscriptionRequest(
                    audioFile = uploadFile,""",
    "opus upload hook",
)
write(CTRL, ctrl)



# --------------------------------------------------------------- 4. KEY FILE IMPORT
# Marko never pastes keys, he picks a file. The file can hold anything: prose, JSON,
# keys for other services, old dead keys. Only AssemblyAI shaped tokens are taken, and
# several of them are kept so a rate limited key rolls to the next one mid dictation.

SCREEN = "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictate/DictateProvidersScreen.kt"
# MaKeys lives in the core library, not the app: the HTTP client is in the library and a
# library cannot depend on the app module. The package name is identical either way.
KEYS_FILE_KT = "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/MaKeys.kt"

MA_KEYS_SOURCE = r'''/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.provider

/**
 * Several API keys per provider, and a parser that digs them out of an arbitrary text file.
 *
 * The keyring upstream stores one key string per provider. Rather than change that storage, the
 * keys are kept in that same field one per line, which older builds still read as a single key and
 * which the editor shows as an ordinary multi-line value. [split] turns it back into a list.
 *
 * [extract] is the file import: it accepts whatever the user picked and keeps only what looks like
 * a key, so a notes file full of prose and other services' credentials imports cleanly.
 */
object MaKeys {

    /** AssemblyAI keys are 32 hex characters standing alone. */
    private val HEX32 = Regex("(?<![0-9A-Za-z])[0-9a-fA-F]{32}(?![0-9A-Za-z])")

    /**
     * Google keys, both generations. Google moved Gemini from Standard keys beginning AIza to
     * Auth keys beginning AQ.Ab during 2026, so a parser that knows only AIza throws away every
     * key issued today. The AQ form contains dots, which the character class has to allow.
     */
    private val GEMINI = Regex("(AQ\\.[0-9A-Za-z._-]{20,}|AIza[0-9A-Za-z_-]{20,})")

    /** Anthropic keys: sk-ant- then the body. */
    private val ANTHROPIC = Regex("sk-ant-[0-9A-Za-z_-]{20,}")

    /** OpenAI style, kept for custom endpoints. */
    private val OPENAI = Regex("sk-(?!ant-)[0-9A-Za-z_-]{20,}")

    /** Fallback shape for anything else key-like, used only when no hex key is present. */
    private val LOOSE = Regex("[A-Za-z0-9._-]{24,120}")

    /** Prefixes belonging to other services, never imported. */
    private val FOREIGN = listOf(
        "gsk_", "xai-", "hf_", "ghp_", "gho_", "github_pat_",
        "pk_", "rk_", "xoxb-", "xoxp-", "akia", "eyj",
    )

    /** Words that mark a line as belonging to another service, unless it also says assembly. */
    private val FOREIGN_WORDS = listOf(
        "groq", "gemini", "google", "openai", "anthropic", "claude", "elevenlabs",
        "deepgram", "huggingface", "replicate", "tidal", "spotify",
    )

    /** The stored field, one key per line, back into a list. Blank and # lines are ignored. */
    fun split(stored: String): List<String> {
        val keys = stored.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
        return if (keys.isEmpty()) listOf(stored.trim()) else keys
    }

    /** How the list is written back into the single stored field. */
    fun join(keys: List<String>): String = keys.joinToString("\n")

    /** Masked summary of what is stored, shown where the paste field used to be. */
    fun describe(stored: String): String {
        val keys = split(stored).filter { it.isNotBlank() }
        if (keys.isEmpty()) return "No key yet. Import one from a file below."
        val first = keys.first()
        val masked = if (first.length > 12) {
            first.take(6) + "\u2026" + first.takeLast(4)
        } else {
            "key stored"
        }
        return if (keys.size == 1) {
            "1 key: " + masked
        } else {
            keys.size.toString() + " keys, first " + masked + ", the rest are fallbacks"
        }
    }

    /**
     * Turns whatever a provider or the HTTP stack threw into one short sentence.
     *
     * Raw messages carry JSON bodies, stack detail and library wording that means nothing to the
     * person holding the phone. The common cases are recognised by their fingerprints and given a
     * plain reply; anything unrecognised is trimmed to its first line and capped, so the dialog
     * never fills with a payload again.
     */
    fun tidyError(raw: String?, fallback: String): String {
        val text = raw.orEmpty().trim()
        if (text.isEmpty()) return fallback
        val low = text.lowercase()
        return when {
            low.contains("0x0a") || low.contains("unexpected char") ->
                "That key contains a line break. Re-import it from your file."
            low.contains("401") || low.contains("invalid x-api-key") ||
                low.contains("invalid api key") || low.contains("unauthorized") ->
                "The service rejected this key. Check you imported the right one for this provider."
            low.contains("429") || low.contains("rate limit") || low.contains("quota") ->
                "Out of quota or too many requests. A second key would cover this."
            low.contains("403") -> "This key is not allowed to use that model."
            low.contains("404") -> "The service has no such endpoint or model."
            low.contains("timeout") || low.contains("timed out") ->
                "The service did not answer in time."
            low.contains("unable to resolve host") || low.contains("failed to connect") ||
                low.contains("network") || low.contains("unreachable") ->
                "No connection to the service."
            low.contains("certificate") || low.contains("ssl") ->
                "The secure connection could not be verified."
            low.startsWith("{") || low.contains("\"error\"") ->
                "The service refused the request."
            else -> {
                val firstLine = text.lineSequence().first().trim()
                if (firstLine.length > 110) firstLine.take(107) + "\u2026" else firstLine
            }
        }
    }

    private fun foreign(token: String, line: String): Boolean {
        val low = token.lowercase()
        if (FOREIGN.any { low.startsWith(it) }) return true
        val context = line.lowercase()
        return FOREIGN_WORDS.any { context.contains(it) } && !context.contains("assembly")
    }

    /**
     * Pulls keys out of an arbitrary text file, in order, without duplicates.
     *
     * Tier one takes bare 32 character hex tokens, the AssemblyAI shape, which walks straight past
     * prose, dates and other providers' keys. Only if that finds nothing does tier two accept any
     * long token containing a digit, so an unusual key format is never silently lost.
     */
    fun extract(text: String, providerId: String = ""): List<String> {
        val lines = text.split('\n')
        val found = LinkedHashSet<String>()
        // Each provider has its own key shape. Matching the right one is what keeps a file
        // holding keys for all three services from handing Gemini's key to Anthropic.
        val shape = when (providerId) {
            "gemini" -> GEMINI
            "anthropic" -> ANTHROPIC
            "assemblyai" -> HEX32
            "openai", "groq" -> OPENAI
            else -> null
        }
        if (shape != null) {
            for (line in lines) {
                if (line.trim().startsWith("#")) continue
                for (m in shape.findAll(line)) {
                    // Hex keys are case-insensitive, the prefixed ones are not.
                    found.add(if (shape === HEX32) m.value.lowercase() else m.value)
                }
            }
            if (found.isNotEmpty()) return found.toList()
            // Nothing matched the expected shape. That does NOT mean the file holds no key: a
            // provider can rebrand its format at any time, exactly as Google did. So fall through
            // to the generic scan rather than telling the user their good key was not found.
        }
        for (line in lines) {
            if (line.trim().startsWith("#")) continue
            for (m in HEX32.findAll(line)) {
                val token = m.value.lowercase()
                if (!foreign(token, line)) found.add(token)
            }
        }
        if (found.isNotEmpty()) return found.toList()
        for (line in lines) {
            if (line.trim().startsWith("#")) continue
            for (m in LOOSE.findAll(line)) {
                val token = m.value
                if (foreign(token, line)) continue
                if (!token.any { it.isDigit() }) continue
                found.add(token)
            }
        }
        return found.toList()
    }
}

/** Failures that mean this particular key is the problem, so the next one is worth trying. */
@PublishedApi
internal val KEY_PROBLEMS = setOf(
    DictateApiException.Kind.INVALID_API_KEY,
    DictateApiException.Kind.QUOTA_EXCEEDED,
)

/**
 * Runs [block] with each key in turn, moving on when a key is rejected or out of quota.
 *
 * Anything else, a timeout or a network drop, is thrown immediately: trying a second key against a
 * dead connection only makes the user wait twice for the same failure. If every key is refused the
 * last exception is thrown, so the message the user sees is a real one.
 */
inline fun <T> maWithKeyFallback(keys: List<String>, block: (String) -> T): T {
    var last: DictateApiException? = null
    for (key in keys) {
        try {
            return block(key)
        } catch (e: DictateApiException) {
            if (e.kind !in KEY_PROBLEMS) throw e
            last = e
        }
    }
    throw last ?: IllegalStateException("no API key configured")
}
'''

write(KEYS_FILE_KT, MA_KEYS_SOURCE)
changes.append("MaKeys parser and fallback")

# --- the transcription call site tries every key
ctrl = read(CTRL)
ctrl = ensure_import(ctrl, "import dev.patrickgold.florisboard.dictate.provider.MaKeys")
ctrl = ensure_import(ctrl, "import dev.patrickgold.florisboard.dictate.provider.maWithKeyFallback")
ctrl = swap(
    ctrl,
    """                    try {
                        OpenAiCompatibleClient.from(
                            preset, apiKey,
                            baseUrlOverride = baseUrlOverrideFor(account),
                            proxy = prefs.dictate.dictateProxyConfig(),
                            // Single-call multimodal (issue #130): route audio through chat/completions.
                            useChatAudio = chatAudio,
                            trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                        ).transcribe(
                            request,
                            onRetry = { attempt -> _state.value = UiState.Transcribing(attempt) },
                        )""",
    """                    try {
                        // MA TWIST: the key field may hold several keys, one per line. A rejected or
                        // exhausted key rolls to the next one; anything else fails straight away.
                        maWithKeyFallback(MaKeys.split(apiKey)) { maKey ->
                            OpenAiCompatibleClient.from(
                                preset, maKey,
                                baseUrlOverride = baseUrlOverrideFor(account),
                                proxy = prefs.dictate.dictateProxyConfig(),
                                // Single-call multimodal (issue #130): route audio through chat/completions.
                                useChatAudio = chatAudio,
                                trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                            ).transcribe(
                                request,
                                onRetry = { attempt -> _state.value = UiState.Transcribing(attempt) },
                            )
                        }""",
    "key fallback at the transcribe call",
)
write(CTRL, ctrl)

# --- the file picker in the provider editor
screen = read(SCREEN)
for imp in [
    "import androidx.activity.compose.rememberLauncherForActivityResult",
    "import androidx.activity.result.contract.ActivityResultContracts",
    "import androidx.compose.material3.Text",
    "import androidx.compose.material3.TextButton",
    "import dev.patrickgold.florisboard.dictate.provider.MaKeys",
]:
    screen = ensure_import(screen, imp)

screen = swap(
    screen,
    """            EditorField(
                label = stringRes(R.string.dictate__api_key_title),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = stringRes(R.string.dictate__api_key_placeholder),
                isSecret = true,
            )
            ConnectionTestRow(preset = effectivePreset, apiKey = apiKey)""",
    """            // MA TWIST: no paste field. Keys only ever arrive from a file, so what is shown
            // here is what is stored, masked, and the button below replaces it.
            Text(text = MaKeys.describe(apiKey))
            // Pick a text file instead of pasting. Everything that is not a key is
            // ignored, and several keys are kept so a rate limited one rolls to the next.
            val maFileContext = LocalContext.current
            var maImportNote by remember { mutableStateOf("") }
            val maPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    val text = runCatching {
                        maFileContext.contentResolver.openInputStream(uri)?.use { stream ->
                            String(stream.readBytes())
                        }
                    }.getOrNull().orEmpty()
                    val keys = MaKeys.extract(text, effectivePreset.id)
                    maImportNote = if (keys.isEmpty()) {
                        "No keys found in that file"
                    } else {
                        apiKey = MaKeys.join(keys)
                        if (keys.size == 1) "1 key imported" else "${keys.size} keys imported, tried in order"
                    }
                }
            }
            TextButton(onClick = { maPicker.launch(arrayOf("*/*")) }) {
                Text(text = "Import keys from a file")
            }
            if (maImportNote.isNotEmpty()) {
                Text(text = maImportNote)
            }
            ConnectionTestRow(preset = effectivePreset, apiKey = apiKey)""",
    "key file picker",
)
write(SCREEN, screen)
changes.append("key file picker")



# --------------------------------------------------------------- 5. NO KEYS IN HEADERS
# The key field can hold several keys, one per line. Only the transcription path knew
# that; connection tests, rewording and model fetches were handing the whole block to
# OkHttp, which rejects a newline in a header value. Normalising at the single client
# factory fixes every one of those paths at once. The transcription path still passes a
# specific key explicitly, so its fallback is unaffected.

client = read(CLIENT)
client = ensure_import(client, "import dev.patrickgold.florisboard.dictate.provider.MaKeys")
client = swap(
    client,
    """        ): OpenAiCompatibleClient = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = baseUrlOverride ?: preset.baseUrl,
                apiKey = apiKey,""",
    """        ): OpenAiCompatibleClient = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = baseUrlOverride ?: preset.baseUrl,
                // MA TWIST: the stored field may hold several keys, one per line. A header can
                // hold exactly one, so take the first unless a caller named a specific key.
                apiKey = MaKeys.split(apiKey).firstOrNull().orEmpty(),""",
    "single key per header",
)
write(CLIENT, client)



# --------------------------------------------------------------- 6. READABLE ERRORS
# The connection test put the raw exception on screen, which is how a JSON body and an
# OkHttp header complaint ended up in front of the user. One formatter, one anchor.

screen = read(SCREEN)
screen = swap(
    screen,
    """                    } catch (e: Exception) {
                        false to (e.message ?: failedFallback)""",
    """                    } catch (e: Exception) {
                        // MA TWIST: a short sentence rather than a raw exception or a JSON body.
                        false to MaKeys.tidyError(e.message, failedFallback)""",
    "readable connection test errors",
)
write(SCREEN, screen)


# --------------------------------------------------------------- 6. TRANSCRIBE VIEW
# The blue microphone used to start recording inside the keyboard bar. It now switches
# to a view built for dictation: a large button carrying the oscilloscope, the braille
# spinner and the clock, with a way back to a keyboard at the bottom.

MODE = "app/src/main/kotlin/dev/patrickgold/florisboard/ime/ImeUiMode.kt"
WINDOW = "app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindow.kt"
KBM = "app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt"
PANEL = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/MaTranscribeLayout.kt"

mode = read(MODE)
mode = swap(
    mode,
    """    GIF(5);""",
    """    GIF(5),

    /** MA TWIST: the dictation screen the microphone key opens. */
    TRANSCRIBE(6);""",
    "transcribe mode",
)
write(MODE, mode)

window = read(WINDOW)
window = ensure_import(window, "import dev.patrickgold.florisboard.dictate.ui.MaTranscribeLayout")
window = swap(
    window,
    """                ImeUiMode.GIF -> ProvideActualLayoutDirection { GifPanel() }""",
    """                ImeUiMode.GIF -> ProvideActualLayoutDirection { GifPanel() }
                ImeUiMode.TRANSCRIBE -> ProvideActualLayoutDirection { MaTranscribeLayout() }""",
    "panel dispatch",
)
write(WINDOW, window)

kbm = read(KBM)
kbm = swap(
    kbm,
    """            KeyCode.IME_UI_MODE_DICTATE -> dev.patrickgold.florisboard.dictate.DictateController.onMicClick(appContext)""",
    """            // MA TWIST: the microphone is a view switcher now, not a record button. Recording
            // happens on the transcribe screen, where there is room to show what is happening.
            KeyCode.IME_UI_MODE_DICTATE -> activeState.imeUiMode = ImeUiMode.TRANSCRIBE""",
    "mic becomes a switcher",
)
write(KBM, kbm)

write(PANEL, r'''/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.ui

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.delay
import org.florisboard.lib.android.systemServiceOrNull

private val MaCyan = Color(0xFF39D0D8)
private val MaViolet = Color(0xFFA371F7)
private val MaInk = Color(0xFFE6EDF3)
private val MaMuted = Color(0xFF8B949E)
private val MaBg = Color(0xFF0D1117)
private val MaCard = Color(0xFF161B22)
private const val MA_BRAILLE = "\u280B\u2819\u2839\u2838\u283C\u2834\u2826\u2827\u2807\u280F"
private const val SCOPE_POINTS = 96

/**
 * The dictation screen, opened by the microphone key.
 *
 * The keyboard bar was the wrong place to dictate: a strip the height of one key cannot show a
 * waveform, a clock and a status at once, so it showed a dot. Here the whole panel belongs to the
 * recording, and the single large button carries all three, the oscilloscope drawn behind, the
 * braille spinner and the elapsed clock in front.
 *
 * The bottom row is the way out: back to this keyboard, or the system picker to hand over to any
 * other keyboard installed on the phone.
 */
@Composable
fun MaTranscribeLayout() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by DictateController.state.collectAsState()

    val recording = state is DictateController.UiState.Recording
    val working = state is DictateController.UiState.Transcribing ||
        state is DictateController.UiState.Rewording

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .background(MaBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                recording -> "Listening"
                working -> "Transcribing"
                else -> "Tap to dictate"
            },
            color = if (recording) MaCyan else MaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        MaBigButton(
            recording = recording,
            working = working,
            onClick = { DictateController.onMicClick(context) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaFootButton(
                icon = Icons.Default.Keyboard,
                label = "Keyboard",
            ) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
            MaFootButton(
                icon = Icons.Default.Language,
                label = "Other keyboard",
            ) {
                context.systemServiceOrNull(InputMethodManager::class)
                    ?.showInputMethodPicker()
            }
        }
    }
}

@Composable
private fun MaFootButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(MaCard)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaCyan)
        Text(
            text = label,
            color = MaInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The button itself. One circle, three jobs.
 *
 * A rolling window of the smoothed microphone level is drawn as a waveform behind everything, with
 * alternate samples mirrored across the centre line so a level meter reads as a wave. In front sits
 * the clock while recording and the braille spinner while the request is in flight.
 */
@Composable
private fun MaBigButton(recording: Boolean, working: Boolean, onClick: () -> Unit) {
    val history = remember { mutableStateListOf<Float>() }
    var frame by remember { mutableIntStateOf(0) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(recording) {
        if (recording) {
            startedAt = SystemClock.elapsedRealtime()
            history.clear()
            while (true) {
                history.add(DictateController.audioLevel.value)
                if (history.size > SCOPE_POINTS) history.removeAt(0)
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                delay(50L)
            }
        }
    }
    LaunchedEffect(working) {
        while (working) {
            frame++
            delay(90L)
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(MaCard)
                .clickable(onClick = onClick),
        )
        Canvas(modifier = Modifier.size(176.dp)) {
            val points = history.size
            if (points < 2) return@Canvas
            val midY = size.height / 2f
            val step = size.width / (points - 1)
            val path = Path()
            for (i in 0 until points) {
                val amp = history[i].coerceIn(0f, 1f) * (size.height * 0.38f)
                val y = if (i % 2 == 0) midY - amp else midY + amp
                val x = step * i
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = MaCyan.copy(alpha = if (recording) 0.45f else 0.15f),
                style = Stroke(width = 3f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (working) {
                Text(
                    text = MA_BRAILLE[frame % MA_BRAILLE.length].toString(),
                    color = MaViolet,
                    fontSize = 40.sp,
                )
            } else {
                val total = elapsedMs / 1000L
                Text(
                    text = "%d:%02d".format(total / 60, total % 60),
                    color = MaInk,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (recording) "tap to stop" else if (working) "please wait" else "tap to talk",
                color = MaMuted,
                fontSize = 11.sp,
            )
        }
    }
}
''')
changes.append("transcribe panel")


print("MA TWIST applied:")
for c in changes:
    print("  - " + c)
