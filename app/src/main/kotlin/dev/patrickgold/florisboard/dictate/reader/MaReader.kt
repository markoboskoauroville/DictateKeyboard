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

import android.content.Context
import android.media.MediaPlayer
import dev.mantraproductions.reader.engine.MaAlign
import dev.mantraproductions.reader.engine.MaEdgeVoice
import dev.mantraproductions.reader.engine.MaText
import dev.mantraproductions.reader.engine.MaWaveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * LLL, the reader: it takes a piece of text, speaks it, and lights each word as it is said.
 *
 * The shape is one unit at a time, and that is the whole design. A unit is a sentence, it is
 * synthesised, played and lit on its own, and the next one is fetched while the current one plays.
 * Reading starts as soon as the first sentence is ready rather than after the whole text, which on a
 * phone is the difference between a reader and a progress bar.
 *
 * Four steps per unit, three of them in the engine:
 *
 * 1. `MaEdgeVoice` speaks it and returns mp3 plus the engine's own word boundaries.
 * 2. `MaAlign` maps those boundaries onto exact character ranges in the visible text.
 * 3. `MaPcm` decodes the clip and `MaWaveform` moves every word onto where it is really spoken.
 * 4. `MediaPlayer` plays it while the position drives the highlight.
 *
 * Clips are cached on disk by text and voice, so a paragraph read twice costs one round trip, and
 * going back a sentence is instant.
 */
object MaReader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One prepared sentence. */
    data class ReadUnit(
        val index: Int,
        val range: IntRange,
        val text: String,
        val tokens: List<MaAlign.Token>,
        val clip: File,
    )

    sealed interface State {
        data object Idle : State
        data class Preparing(val unitIndex: Int, val total: Int) : State
        data class Playing(val unit: ReadUnit, val paused: Boolean) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    /** The full cleaned text currently loaded, which is what the view renders. */
    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    /** Character range of the word to light right now, or null. */
    private val _highlight = MutableStateFlow<IntRange?>(null)
    val highlight = _highlight.asStateFlow()

    private var units: List<IntRange> = emptyList()
    private var voice: String = MaEdgeVoice.Voices.CROATIAN_FEMALE
    private var player: MediaPlayer? = null
    private var job: Job? = null

    /** Where reading resumes from, so pause and play do not start the sentence again. */
    private var cursor = 0

    fun setVoice(v: String) {
        voice = v
    }

    /**
     * Re-reads the loaded text from the current sentence, after a voice change.
     *
     * The clips are cached per voice, so switching back to a voice already heard costs nothing, and
     * the position is kept: changing voice mid-paragraph should not send anybody back to the top.
     */
    fun reload(context: Context) {
        if (_text.value.isEmpty()) return
        job?.cancel()
        releasePlayer()
        job = scope.launch { readFrom(context, cursor) }
    }

    /**
     * Loads [raw] and starts reading from the beginning. Cleaning happens here, once, so every
     * character offset the highlight uses refers to the text the view is actually showing.
     */
    fun load(context: Context, raw: String) {
        stop()
        val cleaned = MaText.cleanText(raw)
        _text.value = cleaned
        units = MaText.splitUnits(cleaned)
        cursor = 0
        if (units.isEmpty()) {
            _state.value = State.Idle
            return
        }
        play(context)
    }

    /** Starts or resumes. */
    fun play(context: Context) {
        val p = player
        if (p != null && !p.isPlaying) {
            p.start()
            val current = _state.value
            if (current is State.Playing) _state.value = current.copy(paused = false)
            return
        }
        if (p != null) return
        job?.cancel()
        job = scope.launch { readFrom(context, cursor) }
    }

    fun pause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                val current = _state.value
                if (current is State.Playing) _state.value = current.copy(paused = true)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        releasePlayer()
        _highlight.value = null
        _state.value = State.Idle
    }

    /** Back or forward a sentence. The unit is the step, because the sentence is the unit of sense. */
    fun step(context: Context, delta: Int) {
        val next = (cursor + delta).coerceIn(0, maxOf(0, units.size - 1))
        job?.cancel()
        releasePlayer()
        cursor = next
        job = scope.launch { readFrom(context, next) }
    }

    private suspend fun readFrom(context: Context, start: Int) {
        var i = start
        // The next sentence is prepared while the current one plays, so the gap between sentences is
        // the voice's own pause rather than a network round trip.
        var ahead: ReadUnit? = null
        while (i < units.size) {
            cursor = i
            val prepared = ahead?.takeIf { it.index == i } ?: run {
                _state.value = State.Preparing(i, units.size)
                prepare(context, i)
            }
            ahead = null
            if (prepared == null) {
                _state.value = State.Failed("could not read sentence ${i + 1}")
                return
            }
            val nextIndex = i + 1
            val prefetch = if (nextIndex < units.size) {
                scope.launch { ahead = prepare(context, nextIndex) }
            } else {
                null
            }
            val finished = playUnit(prepared)
            prefetch?.join()
            if (!finished) return // cancelled, stepped or stopped
            i++
        }
        _highlight.value = null
        _state.value = State.Idle
    }

    private suspend fun prepare(context: Context, index: Int): ReadUnit? = withContext(Dispatchers.IO) {
        val range = units.getOrNull(index) ?: return@withContext null
        val whole = _text.value
        val sentence = whole.substring(range.first, range.last + 1)
        val clip = cacheFile(context, sentence, voice)
        val tokenFile = File(clip.absolutePath + ".json")

        var tokens: List<MaAlign.Token>? = null
        if (clip.exists() && clip.length() > 512 && tokenFile.exists()) {
            tokens = runCatching { readTokens(tokenFile) }.getOrNull()
        }
        if (tokens == null) {
            val spoken = runCatching { MaEdgeVoice.synthesize(sentence, voice) }.getOrNull()
                ?: return@withContext null
            clip.parentFile?.mkdirs()
            clip.writeBytes(spoken.audio)
            val aligned = MaAlign.alignTokens(sentence, spoken.bounds, spoken.total)
            // The waveform pass is best effort by design. When it cannot measure, the engine's own
            // times stand: slightly late, and never anywhere else.
            val refined = MaPcm.decode(clip)?.let { MaWaveform.refine(it, aligned).tokens } ?: aligned
            tokens = spread(refined)
            runCatching { writeTokens(tokenFile, tokens) }
        }
        // Offsets so far are relative to the sentence; the view highlights inside the whole text.
        val shifted = tokens.map { it.copy(s = it.s + range.first, e = it.e + range.first) }
        ReadUnit(index = index, range = range, text = sentence, tokens = shifted, clip = clip)
    }

    /**
     * Gives every word a visible share of the time.
     *
     * The engine merges a number with the word after it: "8. mjesec" arrives as one boundary, so the
     * alignment hands the whole span to "8." and the tidy pass then trims it back to where "mjesec"
     * starts, leaving the number lit for zero seconds. Faithful to the reference and wrong on screen,
     * where a word that never lights reads as the highlight skipping.
     *
     * Fixed here rather than in the engine on purpose. The ports are checked against the Python
     * line for line and should stay that way; this is a presentation rule, and it belongs on the
     * side that presents. Adjacent words sharing a start simply split the span between them.
     */
    private fun spread(tokens: List<MaAlign.Token>): List<MaAlign.Token> {
        if (tokens.size < 2) return tokens
        val out = tokens.toMutableList()
        var i = 0
        while (i < out.size) {
            var j = i
            while (j + 1 < out.size && out[j + 1].t - out[i].t < 0.02) j++
            if (j > i) {
                val from = out[i].t
                val to = maxOf(out[j].d, if (j + 1 < out.size) out[j + 1].t else out[j].d)
                val each = (to - from) / (j - i + 1)
                if (each > 0.0) {
                    for (k in i..j) {
                        val t = from + each * (k - i)
                        out[k] = out[k].copy(t = t, d = t + each)
                    }
                }
            }
            i = j + 1
        }
        return out
    }

    /** Plays one unit and drives the highlight. Returns false when interrupted. */
    private suspend fun playUnit(unit: ReadUnit): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(unit.clip.absolutePath)
            mp.prepare()
            player = mp
            mp.start()
            _state.value = State.Playing(unit, paused = false)
            while (coroutineContext.isActive && (mp.isPlaying || isPaused())) {
                if (mp.isPlaying) {
                    val now = mp.currentPosition / 1000.0
                    _highlight.value = unit.tokens.lastOrNull { it.t <= now }
                        ?.takeIf { now < it.d }
                        ?.let { it.s until it.e }
                }
                // 40 ms is about two frames on a 60 Hz screen: fast enough that the highlight never
                // reads as stepping, slow enough to cost nothing while a sentence plays.
                delay(40)
            }
            coroutineContext.isActive
        } catch (e: Throwable) {
            false
        } finally {
            if (player === mp) releasePlayer() else runCatching { mp.release() }
        }
    }

    private fun isPaused(): Boolean = (_state.value as? State.Playing)?.paused == true

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    // ---------- cache ----------

    private fun cacheFile(context: Context, sentence: String, voice: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$voice\u0000$sentence".toByteArray(Charsets.UTF_8))
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "lll"), "$name.mp3")
    }

    private fun writeTokens(file: File, tokens: List<MaAlign.Token>) {
        file.writeText(tokens.joinToString("\n") { "${it.s},${it.e},${it.t},${it.d}" })
    }

    private fun readTokens(file: File): List<MaAlign.Token> =
        file.readLines().filter { it.isNotBlank() }.map { line ->
            val p = line.split(",")
            MaAlign.Token(p[0].toInt(), p[1].toInt(), p[2].toDouble(), p[3].toDouble())
        }
}
