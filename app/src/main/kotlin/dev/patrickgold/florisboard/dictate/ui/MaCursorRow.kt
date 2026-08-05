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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.withTimeoutOrNull
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** Hold-to-repeat timings for the arrows. */
private const val MA_REPEAT_DELAY_MS = 380L
private const val MA_REPEAT_INTERVAL_MS = 55L

/** Dispatch a single key code through the normal input pipeline, exactly as a real key does. */
private fun KeyboardManager.maTapKey(code: Int) =
    inputEventDispatcher.sendDownUp(TextKeyData(code = code))

/** Query attributes that make the active theme resolve its `key` styling for the given code. */
private fun maKeyAttributes(code: Int) = mapOf(
    FlorisImeUi.Attr.Code to code,
    FlorisImeUi.Attr.Mode to KeyboardMode.CHARACTERS.toString(),
    FlorisImeUi.Attr.ShiftState to InputShiftState.UNSHIFTED.toString(),
)

/**
 * The arrow strip along the bottom, in the flat style of the reference keyboard.
 *
 * No key shapes, no fills, no margins: four evenly spaced glyphs sitting on the window's own colour,
 * so the strip reads as part of the frame rather than another row of keys competing with the letters
 * above it. Up and down first, then left and right, matching the reference.
 *
 * Holding an arrow repeats it, which is what makes crossing a long dictated paragraph bearable.
 */
@Composable
fun MaCursorRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.ARROW_UP) },
            repeats = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.Default.KeyboardArrowUp, "Cursor up", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.ARROW_DOWN) },
            repeats = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.Default.KeyboardArrowDown, "Cursor down", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.ARROW_LEFT) },
            repeats = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Cursor left", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.ARROW_RIGHT) },
            repeats = true,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Cursor right", fg) }
    }
}

/**
 * The command bar along the top, same flat treatment: short bold words and plain glyphs, evenly
 * spaced, no key shapes.
 *
 * These are the commands worth having in reach while editing dictated text rather than typing it.
 * Line start and line end take the place of the reference keyboard's CTRL, which on Android controls
 * nothing an input method can rely on. TAB, ESC, undo, redo, select-all, copy and paste all go
 * through the ordinary input pipeline and do exactly what they say.
 */
@Composable
fun MaCommandRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.MOVE_START_OF_LINE) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaLabel("\u21e4", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.TAB) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaLabel("\u2192|", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.MOVE_END_OF_LINE) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaLabel("\u21e5", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.UNDO) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.AutoMirrored.Filled.Undo, "Undo", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.REDO) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.AutoMirrored.Filled.Redo, "Redo", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.CLIPBOARD_SELECT_ALL) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.Default.SelectAll, "Select all", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.CLIPBOARD_COPY) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.Default.ContentCopy, "Copy", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.CLIPBOARD_PASTE) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg -> MaGlyph(Icons.Default.ContentPaste, "Paste", fg) }
        MaFlatKey(
            onFire = { keyboardManager.maTapKey(KeyCode.ESCAPE) },
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
        ) { fg -> MaLabel("ESC", fg, bold = true, size = 12) }
    }
}

@Composable
private fun MaGlyph(icon: ImageVector, description: String, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun MaLabel(text: String, tint: Color, bold: Boolean = false, size: Int = 15) {
    Text(
        text = text,
        color = tint,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    )
}

/**
 * One flat command: no shape, no fill, just the glyph on the window's own background.
 *
 * The foreground is taken from the theme's key styling, so both bars follow whatever stylesheet is
 * active, Sunrise included, rather than being a hardcoded white strip that fights every theme.
 */
@Composable
private fun MaFlatKey(
    onFire: () -> Unit,
    modifier: Modifier,
    repeats: Boolean = false,
    content: @Composable (Color) -> Unit,
) {
    val style = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, maKeyAttributes(KeyCode.NOOP))
    val foreground = style.foreground(default = Color.White)
    val feedback = LocalInputFeedbackController.current

    val gesture = Modifier.pointerInput(repeats) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onFire()
            feedback.keyPress()
            if (repeats) {
                // Racing waitForUpOrCancellation against a timeout gives a clean answer each round:
                // null means the timeout won and the finger is still down, true means it is gone.
                var wait = MA_REPEAT_DELAY_MS
                while (true) {
                    val released = withTimeoutOrNull(wait) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == true) break
                    onFire()
                    feedback.keyPress()
                    wait = MA_REPEAT_INTERVAL_MS
                }
            } else {
                waitForUpOrCancellation()
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Transparent)
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        content(foreground)
    }
}
