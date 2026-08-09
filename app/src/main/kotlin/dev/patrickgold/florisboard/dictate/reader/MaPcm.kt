/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.reader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes a synthesised clip to the mono 16 kHz PCM the engine's timing pass needs.
 *
 * This is the one piece of the reader that cannot live in the engine module. MA Reader shells out to
 * ffmpeg; an input method has no shell and no ffmpeg, so decoding goes through MediaCodec here while
 * the maths stays in `MaWaveform`, where it can be tested in seconds without a device.
 *
 * Everything about the output is chosen to match what the engine expects: mono, signed 16-bit,
 * 16 kHz. The rate matters more than it looks. Nyquist at 8 kHz keeps fricatives visible, and the
 * fricatives are exactly what tells the difference between a word starting at its s and a word
 * appearing to start 150 ms later at its vowel.
 */
object MaPcm {

    private const val TIMEOUT_US = 10_000L

    /**
     * Decoded samples, or null when the clip cannot be read. Null is a normal answer: the caller
     * falls back to the engine's own timings, which are slightly late rather than wrong.
     */
    fun decode(file: File): ShortArray? = runCatching { decodeOrThrow(file) }.getOrNull()

    private fun decodeOrThrow(file: File): ShortArray? {
        if (!file.exists() || file.length() < 512L) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val raw = ByteArrayOutputStream(1 shl 16)
            val info = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            // A stuck decoder must not hang the keyboard. This bounds the loop at a few seconds of
            // wall clock even if the codec never reports end of stream.
            var spins = 0

            while (!sawOutputEnd && spins < 20_000) {
                spins++
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(outIndex)
                        if (buf != null) {
                            val chunk = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(chunk, 0, info.size)
                            raw.write(chunk)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
                }
            }
            codec.stop()

            val bytes = raw.toByteArray()
            if (bytes.size < 4) return null
            val interleaved = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(interleaved)
            return resample(toMono(interleaved, channels), srcRate, MaWaveformRate)
        } finally {
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Channels averaged rather than one picked, so a voice panned off centre does not go quiet. */
    private fun toMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return samples
        val frames = samples.size / channels
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += samples[i * channels + c].toInt()
            out[i] = (sum / channels).toShort()
        }
        return out
    }

    /**
     * Linear resampling to the engine's rate.
     *
     * Linear is enough here and deliberately so. This signal is never played; it exists only to be
     * squared into an energy envelope, and interpolation error well below the noise floor cannot
     * move a 5 ms envelope frame. A polyphase filter would cost real time on a phone to change
     * nothing that any threshold in the timing pass can see.
     */
    private fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || samples.isEmpty()) return samples
        val ratio = from.toDouble() / to
        val count = (samples.size / ratio).toInt()
        if (count < 2) return ShortArray(0)
        val out = ShortArray(count)
        for (i in 0 until count) {
            val x = i * ratio
            val i0 = x.toInt()
            val i1 = minOf(i0 + 1, samples.size - 1)
            val f = x - i0
            out[i] = ((samples[i0] * (1 - f)) + (samples[i1] * f)).roundToInt().toShort()
        }
        return out
    }
}

/** The engine's decode rate, kept here so the decoder does not import the engine for one integer. */
private const val MaWaveformRate = 16_000
