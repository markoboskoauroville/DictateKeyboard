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

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Turns the recorded WAV into whatever is actually going up the wire.
 *
 * There is a real measurement behind this being configurable. Sending Ogg Opus produced a small
 * upload and then a long wait; sending the raw WAV produced a large upload and a transcript almost
 * immediately. The same audio encoded to Opus by FFmpeg on the same phone came back as fast as the
 * WAV did, which rules out both Opus as a format and the service's handling of it, and points at the
 * file Android's own encoder and muxer produce. Rather than argue about that from theory, the format
 * is now a setting and the app times every send, so the question gets answered with numbers.
 *
 * Notable absence: MP3. Android has no MP3 encoder at all, only a decoder, so offering it would mean
 * bundling a third-party encoder and tens of megabytes of APK for a format that is worse than the
 * alternatives here.
 */
object MaEncoder {

    /** The formats worth comparing, in the order they appear in the picker. */
    enum class Format(val tag: String, val label: String, val extension: String) {
        /** No encoding at all. The baseline, and currently the fastest end to end. */
        WAV("wav", "WAV", "wav"),

        /**
         * AAC in an MP4 container. Android's strongest audio path by a distance: the same encoder and
         * muxer combination every camera app uses, supported since API 18 rather than 29.
         */
        M4A("m4a", "M4A", "m4a"),

        /**
         * Ogg Opus. Kept in the comparison precisely because it is the suspect. An experiment that
         * drops the suspected cause cannot confirm anything, and its timings are what will turn an
         * impression into evidence.
         */
        OGG("opus", "OPUS", "ogg"),
        ;

        companion object {
            fun of(tag: String?): Format = entries.firstOrNull { it.tag == tag } ?: WAV
        }
    }

    /** Below half a second the encoder setup costs more than any saving. */
    private const val MIN_BYTES = 16_000L
    private const val TIMEOUT_US = 10_000L
    private const val WAV_HEADER_BYTES = 44

    /** AAC at 32 kbps mono is transparent for speech and about a twentieth of raw PCM. */
    private const val AAC_BITRATE = 32_000

    /** Opus needs less for the same intelligibility, being the newer codec. */
    private const val OPUS_BITRATE = 16_000

    /**
     * Encodes [wav] into [format], or returns null to mean "send the WAV as it is".
     *
     * Null is returned for [Format.WAV], for clips too short to be worth encoding, when the platform
     * is too old, and on any failure at all. Every one of those cases is safe: the caller falls back
     * to the original file, which always works.
     */
    fun encode(wav: File, format: Format): File? {
        if (format == Format.WAV) return null
        if (!wav.exists() || wav.length() < MIN_BYTES) return null
        if (format == Format.OGG && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val out = File(wav.parentFile, wav.nameWithoutExtension + "." + format.extension)
        return try {
            run(wav, out, format)
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

    private fun run(wav: File, out: File, format: Format) {
        val raf = RandomAccessFile(wav, "r")
        try {
            val header = ByteArray(WAV_HEADER_BYTES)
            raf.readFully(header)
            val sampleRate = le32(header, 24)
            val channels = le16(header, 22)
            if (sampleRate <= 0 || channels <= 0) throw IllegalStateException("bad wav header")

            val mime = when (format) {
                Format.M4A -> MediaFormat.MIMETYPE_AUDIO_AAC
                Format.OGG -> MediaFormat.MIMETYPE_AUDIO_OPUS
                Format.WAV -> throw IllegalStateException("wav is not encoded")
            }
            val container = when (format) {
                Format.M4A -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                Format.OGG -> MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                Format.WAV -> throw IllegalStateException("wav is not encoded")
            }
            val mediaFormat = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    if (format == Format.M4A) AAC_BITRATE else OPUS_BITRATE,
                )
                if (format == Format.M4A) {
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    )
                }
            }

            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val muxer = MediaMuxer(out.absolutePath, container)
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
                                    inIndex, 0, 0, presentationUs,
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
