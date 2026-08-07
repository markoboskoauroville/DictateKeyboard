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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaMacroSyntax
import dev.patrickgold.florisboard.dictate.MaMacros
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
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
 * The macro bar along the top: rows of user-defined buttons, same flat treatment as the arrow strip.
 *
 * This replaced a fixed command row. The commands it held were fine, but they were mine to choose
 * and not Marko's, and a bar of nine buttons that cannot be changed is a worse bar than one of five
 * that can. The defaults are those same commands rewritten as macros, so nothing was lost.
 *
 * Buttons are a fixed width and each row scrolls sideways, rather than the whole row dividing the
 * screen between however many buttons it holds. Weighted buttons shrink as more are added, so the
 * twelfth macro makes the first eleven unreadable and unhittable; a fixed width keeps every button
 * the same size it was and puts the extras off the edge, where a swipe reaches them. Adding a row is
 * still there for anyone who would rather see them all at once.
 *
 * Each button types its macro through [MaMacroSyntax]: plain text goes straight in, anything in
 * braces is a real key press.
 */
@Composable
fun MaMacroBar(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val raw by prefs.dictate.maMacroBar.collectAsState()
    val activeIndex by prefs.dictate.maMacroPreset.collectAsState()
    // An empty preference means untouched, not empty: fall back to the shipped presets rather than
    // drawing no bar at all. Nothing is written to disk until the editor actually changes something,
    // so a future change to the defaults still reaches anyone who never edited them.
    val presets = remember(raw) {
        if (raw.isBlank()) MaMacros.DEFAULT_PRESETS else MaMacros.parse(raw)
    }
    if (presets.isEmpty()) return
    val preset = presets.getOrNull(activeIndex) ?: presets.first()
    // A row whose buttons are all blank still measured a full row high and drew nothing, which is
    // the empty band between the suggestions and the keys. Blank rows are dropped, and a preset with
    // nothing in it draws no bar at all rather than a strip of invisible keys.
    val rows = preset.rows.filter { row ->
        row.any { it.label.isNotBlank() || it.macro.isNotBlank() }
    }
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight * 0.8f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { macro ->
                    MaFlatKey(
                        onFire = { MaMacroSyntax.run(macro.macro, maInputConnection()) },
                        modifier = Modifier.width(MaMacroKeyWidth).fillMaxHeight(),
                    ) { fg ->
                        MaLabel(
                            text = macro.label,
                            tint = fg,
                            bold = macro.label.length <= 4,
                            size = if (macro.label.length > 5) 12 else 14,
                        )
                    }
                }
            }
        }
    }
}

/** Fixed button width, so a row of twenty stays as readable as a row of four. */
private val MaMacroKeyWidth = 62.dp

/** The live input connection of the running IME, or null when detached. */
private fun maInputConnection() = FlorisImeService.currentInputConnection()

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
internal fun MaFlatKey(
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
