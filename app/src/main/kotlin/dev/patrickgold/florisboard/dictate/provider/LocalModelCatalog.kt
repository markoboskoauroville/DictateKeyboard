/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

/**
 * One downloadable file of an on-device model. [destName] is the fixed name it is stored under (so the
 * runtime stays variant-agnostic — see [LocalTranscriptionProvider]); [sizeBytes] and [sha256] are
 * verified after download to guarantee integrity.
 */
data class LocalModelFile(
    val url: String,
    val destName: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

/**
 * A selectable on-device model (issue #104). [id] doubles as the install directory name and the value
 * stored in [ProviderAccount.transcriptionModel] for the local provider.
 */
data class LocalModelSpec(
    val id: String,
    val displayName: String,
    /** Short note for the picker, e.g. languages / accuracy/speed trade-off. */
    val description: String,
    val files: List<LocalModelFile>,
    /**
     * True for a *streaming* model (issue #233): it transcribes while the user is still speaking, so it
     * can drive the live/real-time path via [LocalRealtimeSession]. Offline models (Whisper, Parakeet)
     * only produce text once the whole utterance is in.
     *
     * This flag — not the presence of `joiner.onnx` — is what tells the two runtimes apart, because a
     * streaming transducer and an offline NeMo transducer both ship a joiner.
     */
    val isStreaming: Boolean = false,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * The fixed catalog of on-device models offered for download: one-shot recognizers (Whisper, NeMo
 * Parakeet). Streaming models were removed, so this list is Parakeet only. All int8-quantised
 * sherpa-onnx builds.
 *
 * **Attribution / licensing:** every model here comes from an upstream project under a license that
 * permits redistribution (see each entry, and NOTICE). The files are mirrored on the project's own
 * GitHub release ([REL]) for a stable, project-controlled source instead of depending on a third party
 * at runtime. To re-point hosting, change [REL] only. The runtime never fetches this list — it is
 * shipped in the app.
 */
object LocalModelCatalog {

    /** Project-hosted mirror of the model files (GitHub release assets). Single re-point for hosting. */
    private const val REL = "https://github.com/DevEmperor/DictateKeyboard/releases/download/whisper-models-v1"

    /**
     * Silero VAD model, downloaded into every model dir so [LocalTranscriptionProvider] can segment
     * long audio at speech pauses (Whisper itself only handles ~30 s per pass). Same file for all models.
     */
    private val VAD_FILE = LocalModelFile(
        "$REL/silero_vad.onnx", LocalTranscriptionProvider.VAD, 643_854,
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )







    /**
     * ~670 MB. NVIDIA Parakeet TDT 0.6B v3 (issue #154) — a NeMo *transducer* (encoder/decoder/joiner),
     * not Whisper. Covers 25 European languages; typically faster and more accurate than the small
     * Whisper variants. Exported to ONNX (int8) by the sherpa-onnx project. Licensing: the Parakeet
     * weights are CC-BY-4.0 (NVIDIA); sherpa-onnx export is Apache-2.0 — both allow redistribution.
     */
    val PARAKEET_TDT_V3 = LocalModelSpec(
        id = "parakeet-tdt-0.6b-v3",
        displayName = "Parakeet TDT 0.6B v3",
        description = "25 European languages · ~670 MB",
        files = listOf(
            LocalModelFile("$REL/parakeet-tdt-0.6b-v3-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 652_184_281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
            LocalModelFile("$REL/parakeet-tdt-0.6b-v3-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 11_845_275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            LocalModelFile("$REL/parakeet-tdt-0.6b-v3-joiner.int8.onnx", LocalTranscriptionProvider.JOINER, 6_355_277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            LocalModelFile("$REL/parakeet-tdt-0.6b-v3-tokens.txt", LocalTranscriptionProvider.TOKENS, 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            VAD_FILE,
        ),
    )

    /**
     * ~670 MB. Parakeet German (primeline, issue #176) — a German-specialized fine-tune of NVIDIA
     * Parakeet TDT 0.6B v3, notably more accurate on German (e.g. ~41 % lower WER on Tuda-De than the
     * base) while keeping the same architecture/speed. Exported to sherpa-onnx ONNX (int8) from the
     * primeline `.nemo` the same way as the base v3. Licensing: CC-BY-4.0 (primeline / NVIDIA base),
     * sherpa-onnx export tooling Apache-2.0 — both allow redistribution with attribution.
     */
    val PARAKEET_PRIMELINE_DE = LocalModelSpec(
        id = "parakeet-primeline-de",
        displayName = "Parakeet German (primeline)",
        description = "German · ~670 MB",
        files = listOf(
            LocalModelFile("$REL/parakeet-primeline-de-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 652_282_409, "4ce2447d5d996f1ea369c68cd8c1a8372c5e2b4c5784c9dc9c706b5e42ddc85e"),
            LocalModelFile("$REL/parakeet-primeline-de-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 11_845_274, "ebcae1f7cf869507c1c77932e607df5f8d650b67897b41fbdcb3aea09fc39c4d"),
            LocalModelFile("$REL/parakeet-primeline-de-joiner.int8.onnx", LocalTranscriptionProvider.JOINER, 6_355_277, "8220c0d117d81bdd0d8c770881932ac340f1ce4b36932941d561d11ad1aaffce"),
            LocalModelFile("$REL/parakeet-primeline-de-tokens.txt", LocalTranscriptionProvider.TOKENS, 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            VAD_FILE,
        ),
    )













    /** Install-dir id of the on-device Smart Turn v3 classifier (issue #191). */
    const val SMART_TURN_ID = "smart-turn-v3"

    /**
     * The Smart Turn v3.2 semantic turn-completion model for long-form auto-split (issue #191). Kept out
     * of [all] because it is not an STT model (it never appears in the transcription-model picker); it is
     * downloaded on demand from the Smart Turn checkbox in the long-form settings. Single derived model
     * file (Pipecat classifier + Whisper feature graph), verified after download.
     */
    val SMART_TURN = LocalModelSpec(
        id = SMART_TURN_ID,
        displayName = "Smart Turn v3",
        description = "On-device thought-completion model for long-form auto-split.",
        files = listOf(
            LocalModelFile(
                "$REL/smart-turn-v3.2-cpu.onnx", "smart-turn.onnx", 8_840_701,
                "7e7bfa1924cf89bd12ca9ba8f6d9165e3154884c377944911926ed9fda2f6bab",
            ),
        ),
    )

    /**
     * All catalog models in display order: Parakeet first (best overall), then the German-specialized
     * Parakeet only. The streaming (Kroko) family and the Whisper family are both gone; the
     * accessors below still exist because the picker and the provider code branch on them, and an
     * empty streaming list is the honest way to say "no live models" without touching that code:
     * [LocalModelSection] relies on this order to know where that heading goes.
     */
    val all: List<LocalModelSpec> = listOf(
        PARAKEET_TDT_V3,
        PARAKEET_PRIMELINE_DE,
    )

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }

    /** The streaming models, in display order — the "Live" group of the on-device picker (#233). */
    val streaming: List<LocalModelSpec> get() = all.filter { it.isStreaming }

    /** The classic one-shot models — everything that is not [streaming]. */
    val batchOnly: List<LocalModelSpec> get() = all.filter { !it.isStreaming }

    /** True if [id] names a streaming model. Unknown ids (e.g. a leftover pref) count as non-streaming. */
    fun isStreaming(id: String): Boolean = byId(id)?.isStreaming == true
}
