/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.ui

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.delay
import org.florisboard.lib.android.systemServiceOrNull

private val MaCyan = Color(0xFF39D0D8)
private val MaViolet = Color(0xFFA371F7)
private val MaInk = Color(0xFFE6EDF3)
private val MaMuted = Color(0xFF8B949E)
private val MaBg = Color(0xFF0D1117)
private val MaCard = Color(0xFF161B22)
private const val MA_BRAILLE = "\u280B\u2819\u2839\u2838\u283C\u2834\u2826\u2827\u2807\u280F"
private const val SCOPE_POINTS = 96

/**
 * The dictation screen, opened by the microphone key.
 *
 * The keyboard bar was the wrong place to dictate: a strip the height of one key cannot show a
 * waveform, a clock and a status at once, so it showed a dot. Here the whole panel belongs to the
 * recording, and the single large button carries all three, the oscilloscope drawn behind, the
 * braille spinner and the elapsed clock in front.
 *
 * The bottom row is the way out: back to this keyboard, or the system picker to hand over to any
 * other keyboard installed on the phone.
 */
@Composable
fun MaTranscribeLayout() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by DictateController.state.collectAsState()

    val recording = state is DictateController.UiState.Recording
    val working = state is DictateController.UiState.Transcribing ||
        state is DictateController.UiState.Rewording

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
            .background(MaBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when {
                recording -> "Listening"
                working -> "Transcribing"
                else -> "Tap to dictate"
            },
            color = if (recording) MaCyan else MaMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        MaBigButton(
            recording = recording,
            working = working,
            onClick = { DictateController.onMicClick(context) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaFootButton(
                icon = Icons.Default.Keyboard,
                label = "Keyboard",
            ) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
            MaFootButton(
                icon = Icons.Default.Language,
                label = "Other keyboard",
            ) {
                context.systemServiceOrNull(InputMethodManager::class)
                    ?.showInputMethodPicker()
            }
        }
    }
}

@Composable
private fun MaFootButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(MaCard)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaCyan)
        Text(
            text = label,
            color = MaInk,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * The button itself. One circle, three jobs.
 *
 * A rolling window of the smoothed microphone level is drawn as a waveform behind everything, with
 * alternate samples mirrored across the centre line so a level meter reads as a wave. In front sits
 * the clock while recording and the braille spinner while the request is in flight.
 */
@Composable
private fun MaBigButton(recording: Boolean, working: Boolean, onClick: () -> Unit) {
    val history = remember { mutableStateListOf<Float>() }
    var frame by remember { mutableIntStateOf(0) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(recording) {
        if (recording) {
            startedAt = SystemClock.elapsedRealtime()
            history.clear()
            while (true) {
                history.add(DictateController.audioLevel.value)
                if (history.size > SCOPE_POINTS) history.removeAt(0)
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                delay(50L)
            }
        }
    }
    LaunchedEffect(working) {
        while (working) {
            frame++
            delay(90L)
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(MaCard)
                .clickable(onClick = onClick),
        )
        Canvas(modifier = Modifier.size(176.dp)) {
            val points = history.size
            if (points < 2) return@Canvas
            val midY = size.height / 2f
            val step = size.width / (points - 1)
            val path = Path()
            for (i in 0 until points) {
                val amp = history[i].coerceIn(0f, 1f) * (size.height * 0.38f)
                val y = if (i % 2 == 0) midY - amp else midY + amp
                val x = step * i
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = MaCyan.copy(alpha = if (recording) 0.45f else 0.15f),
                style = Stroke(width = 3f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (working) {
                Text(
                    text = MA_BRAILLE[frame % MA_BRAILLE.length].toString(),
                    color = MaViolet,
                    fontSize = 40.sp,
                )
            } else {
                val total = elapsedMs / 1000L
                Text(
                    text = "%d:%02d".format(total / 60, total % 60),
                    color = MaInk,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (recording) "tap to stop" else if (working) "please wait" else "tap to talk",
                color = MaMuted,
                fontSize = 11.sp,
            )
        }
    }
}
