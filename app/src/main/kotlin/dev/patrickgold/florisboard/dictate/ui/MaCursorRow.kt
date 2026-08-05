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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

/** Corner radius, matching the record button and the legacy keys so nothing looks bolted on. */
private val MaCursorKeyShape = RoundedCornerShape(12.dp)
private val MaCursorKeyMarginH = 3.dp
private val MaCursorKeyMarginV = 4.dp

/** How long a key must be held before it starts repeating, and how fast it repeats after that. */
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
 * A dedicated cursor row that sits between the Smartbar and the keys.
 *
 * Moving the caret used to mean either swiping the space bar or digging the arrow actions out of the
 * Smartbar overflow. Neither is any good when the point is to fix one wrong letter in the middle of a
 * long dictated paragraph. This gives the four arrows their own permanent keys, with line start and
 * line end on the outside, all repeating when held, all styled by whatever theme is active so it
 * looks like part of the keyboard rather than an addition to it.
 *
 * Nothing else in the layout moves: the row is inserted above the keys and is off unless switched on
 * in Settings, so the keyboard anyone else sees is unchanged.
 */
@Composable
fun MaCursorRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaCursorKey(
            code = KeyCode.MOVE_START_OF_LINE,
            label = "\u21e4",
            description = "Start of line",
            keyboardManager = keyboardManager,
            repeats = false,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MaCursorKey(
            code = KeyCode.ARROW_LEFT,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = "Cursor left",
            keyboardManager = keyboardManager,
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
        )
        MaCursorKey(
            code = KeyCode.ARROW_UP,
            icon = Icons.Default.KeyboardArrowUp,
            description = "Cursor up",
            keyboardManager = keyboardManager,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MaCursorKey(
            code = KeyCode.ARROW_DOWN,
            icon = Icons.Default.KeyboardArrowDown,
            description = "Cursor down",
            keyboardManager = keyboardManager,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MaCursorKey(
            code = KeyCode.ARROW_RIGHT,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = "Cursor right",
            keyboardManager = keyboardManager,
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
        )
        MaCursorKey(
            code = KeyCode.MOVE_END_OF_LINE,
            label = "\u21e5",
            description = "End of line",
            keyboardManager = keyboardManager,
            repeats = false,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/**
 * One key of the cursor row. A tap fires once; holding an arrow repeats it after a short delay, which
 * is what makes moving across a long line bearable. The line start and line end keys never repeat,
 * since firing them twice does nothing anyway.
 */
@Composable
private fun MaCursorKey(
    code: Int,
    description: String,
    keyboardManager: KeyboardManager,
    modifier: Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    repeats: Boolean = true,
) {
    val style = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, maKeyAttributes(code))
    val background = style.background(default = Color.White.copy(alpha = 0.08f))
    val foreground = style.foreground(default = Color.White)
    val feedback = LocalInputFeedbackController.current

    val gesture = Modifier.pointerInput(code, repeats) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            keyboardManager.maTapKey(code)
            feedback.keyPress()
            if (repeats) {
                // Hold to repeat. waitForUpOrCancellation suspends until the finger leaves, so racing
                // it against a timeout gives a clean answer every round: null means the timeout won
                // and the key is still held, true means the finger is gone and the loop is over.
                // Mixing delay with awaitPointerEvent here would queue up events and misfire.
                var wait = MA_REPEAT_DELAY_MS
                while (true) {
                    val released = withTimeoutOrNull(wait) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == true) break
                    keyboardManager.maTapKey(code)
                    feedback.keyPress()
                    wait = MA_REPEAT_INTERVAL_MS
                }
            } else {
                // Still wait for the lift so the gesture completes cleanly.
                waitForUpOrCancellation()
            }
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = MaCursorKeyMarginH, vertical = MaCursorKeyMarginV)
            .clip(MaCursorKeyShape)
            .background(background)
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        // The four arrows are icons; line start and line end are the Unicode tab arrows, which say
        // the same thing without depending on icon names that keep moving between Compose releases.
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = foreground,
                modifier = Modifier.size(22.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = foreground,
                fontSize = 20.sp,
            )
        }
    }
}
