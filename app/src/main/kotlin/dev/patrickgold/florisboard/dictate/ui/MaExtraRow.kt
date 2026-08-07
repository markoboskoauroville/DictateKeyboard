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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.withTimeoutOrNull
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * The top row of the keyboard, whose labels change while its keys do not.
 *
 * This is the number row. Not a strip above it, not a second row, and not a set of small glyphs
 * crammed into one line: the same ten keys in the same places at the same size, with different
 * things written on them. Only what is printed on a key changes, so the hand does not have to
 * relearn anything when the contents do.
 *
 * Four sets of ten. Ten because that is what fits at the size a key has to be to hit reliably, and
 * anything that does not fit into ten belongs in a different set rather than squeezed into this one.
 */
@Composable
fun MaExtraRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val enabled by prefs.dictate.maExtraRow.collectAsState()
    val mode by prefs.dictate.maExtraRowMode.collectAsState()
    if (!enabled) return

    val keys: List<MaRowKey> = when (mode) {
        "diacritics" -> MaRowSets.CROATIAN
        "symbols" -> MaRowSets.BRACKETS
        "arrows" -> MaRowSets.ARROWS
        "editing" -> MaRowSets.EDITING
        else -> MaRowSets.DIGITS
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The height of a real key row, not the Smartbar's. This row is part of the keyboard and
            // has to measure like it, or it reads as something bolted on above.
            .height(FlorisImeSizing.keyboardRowBaseHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            MaRowKeyButton(
                key = key,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onFire = {
                    if (key.code != null) {
                        keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData(code = key.code))
                    } else {
                        val ch = key.label
                        keyboardManager.inputEventDispatcher.sendDownUp(
                            TextKeyData(code = ch.codePointAt(0), label = ch),
                        )
                    }
                },
            )
        }
    }
}

/** One key: what is printed on it, and either a character to type or an action to fire. */
data class MaRowKey(val label: String, val code: Int? = null, val repeats: Boolean = false)

/**
 * The four sets, ten keys each.
 *
 * Grouped by the job in hand rather than by what happens to fit: brackets with brackets, arrows with
 * arrows, editing with editing. Mixing them, which is what one long strip did, means hunting past
 * nine things nobody wants to reach the one they do.
 */
object MaRowSets {
    val DIGITS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { MaRowKey(it) }

    val CROATIAN = listOf("č", "ć", "ž", "š", "đ", "Č", "Ć", "Ž", "Š", "Đ").map { MaRowKey(it) }

    val BRACKETS = listOf("(", ")", "[", "]", "{", "}", "<", ">", "|", "~").map { MaRowKey(it) }

    /** Movement, coarse and fine, in the order a hand reaches for them. */
    val ARROWS = listOf(
        MaRowKey("\u21e4", KeyCode.MOVE_START_OF_LINE),
        MaRowKey("\u00ab", KeyCode.MA_WORD_LEFT, repeats = true),
        MaRowKey("\u2190", KeyCode.ARROW_LEFT, repeats = true),
        MaRowKey("\u2192", KeyCode.ARROW_RIGHT, repeats = true),
        MaRowKey("\u00bb", KeyCode.MA_WORD_RIGHT, repeats = true),
        MaRowKey("\u21e5", KeyCode.MOVE_END_OF_LINE),
        MaRowKey("\u2191", KeyCode.ARROW_UP, repeats = true),
        MaRowKey("\u2193", KeyCode.ARROW_DOWN, repeats = true),
        MaRowKey("\u21de", KeyCode.MOVE_START_OF_PAGE),
        MaRowKey("\u21df", KeyCode.MOVE_END_OF_PAGE),
    )

    /** Selecting and moving text about. */
    val EDITING = listOf(
        MaRowKey("\u21e7", KeyCode.CLIPBOARD_SELECT),
        MaRowKey("\u25ad", KeyCode.MA_SELECT_WORD),
        MaRowKey("\u2b1a", KeyCode.CLIPBOARD_SELECT_ALL),
        MaRowKey("\u2704", KeyCode.CLIPBOARD_CUT),
        MaRowKey("\u29c9", KeyCode.CLIPBOARD_COPY),
        MaRowKey("\u2913", KeyCode.CLIPBOARD_PASTE),
        MaRowKey("\u21b6", KeyCode.UNDO),
        MaRowKey("\u21b7", KeyCode.REDO),
        MaRowKey("\u232b", KeyCode.DELETE_WORD, repeats = true),
        MaRowKey("\u238b", KeyCode.ESCAPE),
    )
}

/**
 * A key drawn exactly like the ones below it.
 *
 * Takes its colours from the active theme's `key` styling, so it matches whatever stylesheet is in
 * use rather than approximating it, and it is shaped and spaced like the letter keys because it is
 * one of them as far as the hand is concerned.
 */
@Composable
private fun MaRowKeyButton(
    key: MaRowKey,
    modifier: Modifier,
    onFire: () -> Unit,
) {
    val style = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        mapOf(
            FlorisImeUi.Attr.Code to (key.code ?: KeyCode.NOOP),
            FlorisImeUi.Attr.Mode to KeyboardMode.CHARACTERS.toString(),
            FlorisImeUi.Attr.ShiftState to InputShiftState.UNSHIFTED.toString(),
        ),
    )
    val background = style.background(default = Color.White.copy(alpha = 0.08f))
    val foreground = style.foreground(default = Color.White)
    val feedback = LocalInputFeedbackController.current

    val gesture = Modifier.pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onFire()
            feedback.keyPress()
            if (key.repeats) {
                var wait = 380L
                while (true) {
                    val released = withTimeoutOrNull(wait) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == true) break
                    onFire()
                    feedback.keyPress()
                    wait = 55L
                }
            } else {
                waitForUpOrCancellation()
            }
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(background)
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = foreground,
            // Sized like a letter key rather than shrunk to fit a crowd, because ten keys across is
            // exactly what the row below manages at full size.
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
