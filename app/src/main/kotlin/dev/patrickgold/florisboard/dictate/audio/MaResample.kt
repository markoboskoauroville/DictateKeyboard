/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reduces a recorded WAV to 16 kHz mono, properly.
 *
 * Speech recognisers want 16 kHz, and something has to do the reduction. Doing it by simply taking
 * every third sample, which is what a naive resampler and several phone drivers do, folds everything
 * above 8 kHz back down into the speech band as aliasing: sibilants smear into buzzes, and the
 * recogniser is handed a harder problem than the microphone actually captured.
 *
 * So the signal is low-pass filtered first and only then decimated. The filter is a windowed-sinc
 * FIR, which is the textbook answer and cheap enough at these lengths that a minute of audio takes a
 * fraction of a second on a phone.
 *
 * Working in 16-bit PCM throughout, because that is what goes in and what must come out, and a
 * float round-trip would add rounding for no benefit at this bit depth.
 */
object MaResample {

    /** What every recogniser in this app expects. */
    const val TARGET_RATE = 16_000

    /**
     * Half-length of the filter. 32 taps either side gives roughly 60 dB of stopband rejection with
     * a Hann window, which is well below anything a microphone's own noise floor contributes.
     */
    private const val HALF_TAPS = 32

    private const val WAV_HEADER = 44

    /**
     * Writes a 16 kHz mono copy of [src] and returns it, or null when no conversion was needed or
     * possible.
     *
     * Null for a file already at 16 kHz, which is not a failure: the caller simply keeps using the
     * file it has. Null on a malformed header too, for the same reason. Never throws, because losing
     * a recording to a resampling problem would be a far worse outcome than sending it unresampled.
     */
    fun toTargetRate(src: File): File? = runCatching {
        if (!src.exists() || src.length() <= WAV_HEADER) return null
        val raf = RandomAccessFile(src, "r")
        val pcm: ShortArray
        val srcRate: Int
        try {
            val header = ByteArray(WAV_HEADER)
            raf.readFully(header)
            srcRate = le32(header, 24)
            val channels = le16(header, 22)
            val bits = le16(header, 34)
            if (srcRate <= 0 || channels <= 0 || bits != 16) return null
            if (srcRate == TARGET_RATE && channels == 1) return null

            val dataBytes = (src.length() - WAV_HEADER).toInt()
            val raw = ByteArray(dataBytes)
            raf.readFully(raw)
            val frames = dataBytes / (2 * channels)
            pcm = ShortArray(frames)
            // Down-mix to mono while reading, so the filter only ever sees one channel.
            for (i in 0 until frames) {
                var sum = 0
                for (c in 0 until channels) {
                    val at = (i * channels + c) * 2
                    sum += ((raw[at].toInt() and 0xFF) or (raw[at + 1].toInt() shl 8)).toShort()
                }
                pcm[i] = (sum / channels).toShort()
            }
        } finally {
            runCatching { raf.close() }
        }

        val out = File(src.parentFile, src.nameWithoutExtension + "_16k.wav")
        val resampled = resample(pcm, srcRate, TARGET_RATE)
        writeWav(out, resampled)
        if (out.length() > WAV_HEADER) out else null.also { runCatching { out.delete() } }
    }.getOrNull()

    /**
     * Low-pass then resample, in one pass.
     *
     * The cutoff sits just under half the *output* rate, which is what stops the fold-back. Output
     * positions are computed in the source's own time base, so a non-integer ratio such as 44100 to
     * 16000 is handled by the same code as the exact 3:1 of 48000.
     */
    private fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (input.isEmpty()) return input
        if (from == to) return input
        val ratio = to.toDouble() / from.toDouble()
        // Only band-limit when going down. Upsampling cannot alias, and filtering it would only
        // remove detail that is genuinely there.
        val cutoff = if (ratio < 1.0) 0.5 * ratio else 0.5
        val outLen = (input.size * ratio).toInt()
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val center = i / ratio
            val nearest = center.toInt()
            var acc = 0.0
            var norm = 0.0
            val first = maxOf(0, nearest - HALF_TAPS)
            val last = min(input.size - 1, nearest + HALF_TAPS)
            for (j in first..last) {
                val t = center - j
                val w = hann(t)
                if (w == 0.0) continue
                val h = sinc(2.0 * cutoff * t) * 2.0 * cutoff * w
                acc += input[j] * h
                norm += h
            }
            // Normalising by the actual tap sum keeps the level right at the edges, where the window
            // is truncated and the taps no longer sum to one.
            val v = if (abs(norm) > 1e-9) acc / norm else 0.0
            out[i] = v.coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return out
    }

    private fun sinc(x: Double): Double =
        if (abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)

    private fun hann(t: Double): Double {
        val a = abs(t)
        if (a > HALF_TAPS) return 0.0
        return 0.5 * (1.0 + cos(PI * a / HALF_TAPS))
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val bytes = ByteArray(WAV_HEADER + dataLen)
        fun ascii(at: Int, s: String) {
            for (k in s.indices) bytes[at + k] = s[k].code.toByte()
        }
        fun i32(at: Int, v: Int) {
            bytes[at] = (v and 0xFF).toByte()
            bytes[at + 1] = ((v shr 8) and 0xFF).toByte()
            bytes[at + 2] = ((v shr 16) and 0xFF).toByte()
            bytes[at + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun i16(at: Int, v: Int) {
            bytes[at] = (v and 0xFF).toByte()
            bytes[at + 1] = ((v shr 8) and 0xFF).toByte()
        }
        ascii(0, "RIFF"); i32(4, 36 + dataLen); ascii(8, "WAVE")
        ascii(12, "fmt "); i32(16, 16); i16(20, 1); i16(22, 1)
        i32(24, TARGET_RATE); i32(28, TARGET_RATE * 2); i16(32, 2); i16(34, 16)
        ascii(36, "data"); i32(40, dataLen)
        for (i in pcm.indices) i16(WAV_HEADER + i * 2, pcm[i].toInt())
        file.writeBytes(bytes)
    }

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
