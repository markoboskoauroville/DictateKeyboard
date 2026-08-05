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

package dev.patrickgold.florisboard.dictate

import android.content.Context
import java.io.File

/**
 * The last ten recordings that were cut short, kept so none of them is ever lost.
 *
 * An input method has no window of its own. The moment another app takes the screen, the keyboard is
 * torn down and the microphone goes with it. The recording is finalised rather than thrown away, but
 * upstream kept exactly one such file, at a fixed path, and the next interruption overwrote it. So
 * switching apps twice while speaking lost the first recording permanently, which is what "it
 * vanished forever" describes.
 *
 * A ring of ten fixes the common case completely: check something in another app mid-sentence, come
 * back, and the audio is still here to be transcribed. It is a buffer, not an archive. The finished
 * transcriptions have their own history; this holds only the raw audio that never got that far, and
 * the oldest entry falls off when an eleventh arrives.
 *
 * This deliberately does not attempt to keep recording in the background. Doing that needs a
 * microphone foreground service with its own persistent notification and a much more careful hand
 * on the audio session, and half of it would be worse than none.
 */
object MaRecordingBuffer {

    /** How many interrupted recordings are kept before the oldest is dropped. */
    const val CAPACITY = 10

    private const val DIR_NAME = "ma_recordings"
    private const val PREFIX = "rec_"
    private const val EXT = ".wav"

    /** One kept recording. */
    data class Entry(
        val file: File,
        val timestamp: Long,
        val seconds: Long,
    )

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    /**
     * Copies a finalised recording into the buffer and drops the oldest if that makes eleven.
     *
     * The source file is copied rather than moved, because the caller still owns it: the normal
     * interrupted-recording path may go on to offer that same file for immediate transcription, and
     * pulling it out from under that would trade one lost recording for another.
     */
    fun add(context: Context, source: File?, seconds: Long) {
        if (source == null || !source.exists() || source.length() <= 0L) return
        runCatching {
            val stamp = System.currentTimeMillis()
            val dest = File(dir(context), "$PREFIX${stamp}_$seconds$EXT")
            source.copyTo(dest, overwrite = true)
            prune(context)
        }
    }

    /** Everything currently kept, newest first. */
    fun list(context: Context): List<Entry> = runCatching {
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(EXT) }
            ?.mapNotNull { file ->
                // rec_<millis>_<seconds>.wav; anything that does not parse is left alone rather than
                // guessed at, so a stray file can never be reported with a made-up duration.
                val body = file.name.removePrefix(PREFIX).removeSuffix(EXT)
                val parts = body.split('_')
                val stamp = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val secs = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                Entry(file, stamp, secs)
            }
            ?.sortedByDescending { it.timestamp }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** Removes one entry. */
    fun delete(entry: Entry) {
        runCatching { entry.file.delete() }
    }

    /** Removes everything. */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /** Drops the oldest entries beyond [CAPACITY]. */
    private fun prune(context: Context) {
        val all = list(context)
        if (all.size <= CAPACITY) return
        all.drop(CAPACITY).forEach { runCatching { it.file.delete() } }
    }
}
