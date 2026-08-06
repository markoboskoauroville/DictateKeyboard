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
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Encodes the 16 kHz WAV into what actually goes up the wire.
 *
 * AAC in an MP4 container, and nothing else. Opus was tried and removed: encoded here it was slow to
 * transcribe, while the identical audio encoded by FFmpeg on the same phone was not, which puts the
 * fault in Android's own Opus encoder and muxer rather than in the codec or the service. AAC is the
 * path every camera app on Android uses, supported since API 18 rather than 29, and accepted by
 * every transcription service worth using.
 *
 * Notable absence: MP3. Android has no MP3 encoder at all, only a decoder, so offering it would mean
 * bundling one and tens of megabytes of APK for a format worse than this.
 */
object MaEncoder {

    /**
     * The one output format. AAC in an MP4 container is Android's strongest audio path by a
     * distance: the same encoder and muxer every camera app uses, supported since API 18, and
     * accepted by every transcription service worth using.
     *
     * Opus is gone entirely. Encoded here it was slow to transcribe; the identical audio encoded by
     * FFmpeg on the same phone was not, which puts the fault in Android's own Opus encoder and muxer
     * rather than in the codec or the service. There is no reason to keep a broken path around.
     */
    const val TAG = "aac"
    const val EXTENSION = "m4a"

    /** Below half a second the encoder setup costs more than any saving. */
    private const val MIN_BYTES = 16_000L
    private const val TIMEOUT_US = 10_000L
    private const val WAV_HEADER_BYTES = 44

    /** AAC at 32 kbps mono is transparent for speech and about a twentieth of raw PCM. */
    private const val AAC_BITRATE = 32_000

    /**
     * Encodes a 16 kHz WAV to AAC, returning the new file or null on any failure.
     *
     * Null always means "use what you already have". Every failure path is safe, because losing a
     * recording to an encoder problem would be far worse than sending a larger file.
     */
    fun encode(wav: File): File? {
        if (!wav.exists() || wav.length() < MIN_BYTES) return null
        val out = File(wav.parentFile, wav.nameWithoutExtension + "." + EXTENSION)
        return try {
            run(wav, out)
            if (out.length() > 0L) out else null.also { out.delete() }
        } catch (t: Throwable) {
            runCatching { out.delete() }
            null
        }
    }

    private fun run(wav: File, out: File) {
        val raf = RandomAccessFile(wav, "r")
        try {
            val header = ByteArray(WAV_HEADER_BYTES)
            raf.readFully(header)
            val sampleRate = le32(header, 24)
            val channels = le16(header, 22)
            if (sampleRate <= 0 || channels <= 0) throw IllegalStateException("bad wav header")

            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val container = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            val mediaFormat = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
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
