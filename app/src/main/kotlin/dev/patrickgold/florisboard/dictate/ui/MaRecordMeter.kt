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

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The record button's instrument panel: readings on top, meter along the bottom.
 *
 * The previous version put the numbers in the middle of the bar, on top of the moving trace, where
 * they were genuinely hard to read: dark text over a bar that is green in one place and pale in
 * another has no reliable contrast anywhere. Nothing overlaps now. The readings live on their own
 * line above, in bold, and the meter has the bottom of the box to itself.
 *
 * Three readings, each on the side where it belongs:
 *
 *   left    level in dB, the thing that changes fastest and is glanced at rather than read
 *   centre  elapsed time, the one number always wanted, so it gets the middle
 *   right   size on disk in megabytes while recording, and transfer figures while sending
 *
 * While sending, the meter has no microphone to show, so it becomes a stereo-style bar spreading out
 * from the centre: decoration rather than data, and honest about it, but it keeps the panel alive
 * during the wait rather than freezing at whatever the last syllable happened to be.
 */
@Composable
fun MaScopeCanvas(active: Boolean, tint: Color) {
    if (!active) return
    val level by DictateController.audioLevel.collectAsState()
    val state by DictateController.state.collectAsState()
    val sending = state is DictateController.UiState.Transcribing ||
        state is DictateController.UiState.Rewording

    val db = maToDb(level)
    val smoothed by animateFloatAsState(
        targetValue = db,
        animationSpec = tween(70),
        label = "maDb",
    )
    var peakDb by remember { mutableFloatStateOf(FLOOR_DB) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = maToDb(DictateController.audioLevel.value)
            peakDb = if (now > peakDb) now else (peakDb - 0.6f).coerceAtLeast(FLOOR_DB)
            delay(60L)
        }
    }

    // Sweeps 0..1 and back, driving the sending animation. Its own clock rather than the audio
    // level, because there is no audio arriving once the file has gone.
    var sweep by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sending) {
        if (!sending) return@LaunchedEffect
        var rising = true
        while (true) {
            sweep = (sweep + if (rising) 0.06f else -0.06f).coerceIn(0f, 1f)
            if (sweep >= 1f) rising = false
            if (sweep <= 0f) rising = true
            delay(40L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        MaReadings(sending = sending, tint = tint)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(bottom = 4.dp),
        ) {
            val h = size.height
            val full = size.width
            drawRoundRect(
                color = tint.copy(alpha = 0.14f),
                topLeft = Offset(0f, h * 0.2f),
                size = Size(full, h * 0.6f),
                cornerRadius = CornerRadius(h * 0.3f, h * 0.3f),
            )
            if (sending) {
                // Out from the middle, both ways at once, like a broadcast meter. It says "this is
                // going somewhere" without pretending to measure anything.
                val half = full / 2f
                val reach = half * sweep
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = Offset(half - reach, h * 0.2f),
                    size = Size(reach * 2f, h * 0.6f),
                    cornerRadius = CornerRadius(h * 0.3f, h * 0.3f),
                )
            } else {
                val filled = full * maNorm(smoothed)
                drawRoundRect(
                    color = maDbColour(smoothed, tint),
                    topLeft = Offset(0f, h * 0.2f),
                    size = Size(filled, h * 0.6f),
                    cornerRadius = CornerRadius(h * 0.3f, h * 0.3f),
                )
                // Peak hold, falling back slowly so a transient stays readable.
                val peakX = (full * maNorm(peakDb)).coerceIn(0f, full - 2f)
                drawRoundRect(
                    color = maDbColour(peakDb, tint).copy(alpha = 0.9f),
                    topLeft = Offset(peakX, h * 0.05f),
                    size = Size(2f, h * 0.9f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            }
        }
    }
}

/** The three readings, bold, on their own line clear of the meter. */
@Composable
private fun MaReadings(sending: Boolean, tint: Color) {
    val recording = DictateController.state.collectAsState().value
        as? DictateController.UiState.Recording

    // Size on disk, sampled rather than computed: the recorder is writing the file, so asking the
    // file how big it is now is both simplest and always true.
    var bytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sending) {
        while (!sending) {
            bytes = DictateController.currentRecordingBytes()
            delay(400L)
        }
    }

    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording?.startedAtMs, recording?.accumulatedMs, recording?.paused) {
        val r = recording ?: return@LaunchedEffect
        while (true) {
            elapsedMs = if (r.paused) {
                r.accumulatedMs
            } else {
                r.accumulatedMs + (android.os.SystemClock.elapsedRealtime() - r.startedAtMs)
            }
            delay(200L)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The dB number is gone. The bar underneath already says how loud, continuously and without
        // being read, which is what a meter is for; a number saying the same thing was one reading
        // too many on a strip this size and it was taking room from the one that matters.
        Spacer(modifier = Modifier.weight(1f))
        Text(
            // As large as the row allows. This is the number actually watched during a dictation,
            // and it now has the space the level readout was using.
            text = "%d:%02d".format(elapsedMs / 60000, (elapsedMs / 1000) % 60),
            color = tint,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            // Three characters, always: a digit, a dot, a digit, in megabytes. Fixed width so the
            // line does not jitter as it climbs.
            text = if (sending) "" else "%.1f".format(bytes / 1_048_576.0),
            color = tint.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
    }
}

/** Below this a speech signal is silence as far as this meter is concerned. */
private const val FLOOR_DB = -54f

private fun maToDb(level: Float): Float {
    val v = abs(level)
    if (v <= 0.0005f) return FLOOR_DB
    return (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(FLOOR_DB, 0f)
}

private fun maNorm(db: Float): Float = ((db - FLOOR_DB) / (0f - FLOOR_DB)).coerceIn(0f, 1f)

private fun maDbColour(db: Float, tint: Color): Color = when {
    db > -3f -> Color(0xFFE5534B)
    db > -12f -> Color(0xFFF0883E)
    else -> Color(0xFF56D364)
}
