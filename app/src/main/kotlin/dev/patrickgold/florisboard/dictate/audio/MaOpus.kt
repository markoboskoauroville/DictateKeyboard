/*
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

    /**
     * The recorder produces 16 kHz mono, which is already the rate a recogniser wants, so the only
     * thing left to remove is the PCM overhead. 16 kbps Opus keeps speech fully intelligible and is
     * one twentieth of the raw stream: a minute of dictation goes from about 1.9 MB to 120 kB.
     */
    private const val BITRATE = 16_000

    /**
     * Below half a second the encoder setup costs more than it saves. This used to be 48 kB, a
     * second and a half, which meant every short correction went up as raw PCM.
     */
    private const val MIN_BYTES = 16_000L

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
