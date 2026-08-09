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

/**
 * Which AssemblyAI path a dictation takes.
 *
 * Two words, and they are the two words that mean something while waiting for text to appear. Not
 * "sync" and "async", which name the wire format rather than the experience, and not the model ids,
 * which name neither.
 *
 * The choice is only ever a preference, never a promise: [FAST] falls back to [SLOW] on its own
 * whenever the recording is too long for the sync endpoint or the sync endpoint does not answer. See
 * `DictateController.maUseSyncPath`.
 */
enum class MaSpeed {
    /**
     * AssemblyAI Sync. One request, the transcript in the same response, about 134 ms at the median.
     * Costs $0.45/hr against $0.15/hr, is capped at two minutes, and uploads the uncompressed 16 kHz
     * WAV because the endpoint takes no compressed format at all.
     */
    FAST,

    /**
     * AssemblyAI's upload, create and poll flow. Slower to answer, a third of the price, no length
     * limit worth worrying about, and it sends a small AAC file. This is what the app has always done
     * and it stays the default.
     */
    SLOW,
}
