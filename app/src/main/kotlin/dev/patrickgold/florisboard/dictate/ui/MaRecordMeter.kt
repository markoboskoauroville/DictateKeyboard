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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.delay

/**
 * The recording meter, shared by both views.
 *
 * Extracted so the transcribe view and the keyboard view show the same thing while recording. They
 * had drifted into two different visuals, a level bar in one and a waveform with a spinner in the
 * other, which made the same act look like two different features depending on where it was started.
 * One module, one appearance, one place to change it.
 */

/**
 * A proper level meter across the record button, the kind a sound engineer expects.
 *
 * The earlier versions drew a waveform and then a row of bars, and neither answered the question a
 * meter exists to answer: how hot am I, in dB. This is a horizontal bar in dBFS with a peak hold,
 * the same instrument as the small meter in a video timeline.
 *
 * Amplitude is converted with 20*log10, floored at [FLOOR_DB], because below that a speech signal
 * is silence for our purposes. The peak marker falls back slowly rather than snapping, so a
 * transient stays visible long enough to read. Green through amber to red, with the top of the
 * scale reserved for the region where a recogniser starts losing consonants to clipping.
 */
@Composable
fun MaScopeCanvas(active: Boolean, tint: Color) {
    if (!active) return
    val level by DictateController.audioLevel.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            val h = size.height
            val full = size.width
            // The scale, drawn faintly so the bar has something to sit against.
            drawRect(
                color = tint.copy(alpha = 0.14f),
                topLeft = Offset(0f, h * 0.25f),
                size = Size(full, h * 0.5f),
            )
            val filled = full * maNorm(smoothed)
            drawRect(
                color = maDbColour(smoothed, tint),
                topLeft = Offset(0f, h * 0.25f),
                size = Size(filled, h * 0.5f),
            )
            // Peak hold.
            val peakX = (full * maNorm(peakDb)).coerceIn(0f, full - 2f)
            drawRect(
                color = maDbColour(peakDb, tint),
                topLeft = Offset(peakX, h * 0.1f),
                size = Size(2.5f, h * 0.8f),
            )
            // Marks at -18 and -6, the two that matter when speaking.
            for (mark in intArrayOf(-18, -6)) {
                val x = full * maNorm(mark.toFloat())
                drawRect(
                    color = tint.copy(alpha = 0.30f),
                    topLeft = Offset(x, h * 0.15f),
                    size = Size(1f, h * 0.7f),
                )
            }
        }
    }
        // dB back, but small and centred over the bar, out of the timer's way. Two readings that
        // never collide: level in the middle, elapsed time at the far right. Inside the Box rather
        // than the Row, because a Row can only align its children vertically.
        Text(
            text = if (smoothed <= FLOOR_DB + 0.5f) "-∞" else "%.0f".format(smoothed),
            color = tint.copy(alpha = 0.75f),
            fontSize = 10.sp,
        )
    }
}

/** Below this a speech signal is silence as far as this meter is concerned. */
private const val FLOOR_DB = -54f

/** Amplitude 0..1 to dBFS, floored so log10(0) never reaches the UI. */
private fun maToDb(level: Float): Float {
    val v = level.coerceIn(0f, 1f)
    if (v <= 0.0005f) return FLOOR_DB
    return (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(FLOOR_DB, 0f)
}

/** dBFS to a 0..1 position on the scale. */
private fun maNorm(db: Float): Float = ((db - FLOOR_DB) / (0f - FLOOR_DB)).coerceIn(0f, 1f)

/** Green while there is headroom, amber approaching, red where consonants start to clip. */
private fun maDbColour(db: Float, tint: Color): Color = when {
    db >= -3f -> Color(0xFFF85149)
    db >= -9f -> Color(0xFFF0883E)
    db >= -30f -> Color(0xFF56D364)
    else -> tint.copy(alpha = 0.55f)
}


/**