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
import androidx.compose.runtime.remember
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
import dev.patrickgold.florisboard.dictate.MaNumericSecondary
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

    val secondaryRaw by prefs.dictate.maNumericSecondary.collectAsState()
    val secondaries = remember(secondaryRaw) { MaNumericSecondary.parse(secondaryRaw) }

    val keys: List<MaRowKey> = when (mode) {
        "diacritics" -> MaRowSets.CROATIAN
        "symbols" -> MaRowSets.BRACKETS
        "arrows" -> MaRowSets.ARROWS
        "editing" -> MaRowSets.EDITING
        else -> remember(secondaries) { MaRowSets.digits(secondaries) }
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
                // A second symbol wins over the cycle, because a key that types something is worth
                // more than a shortcut to a set the dashboard already lists by name. Only the sets
                // that carry no second symbols still cycle.
                onLongPress = maLongPress(key, keyboardManager),
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

/**
 * What holding [key] does, or null for a key with no long press at all.
 *
 * A second symbol wins over the cycle to the next set, because a key that types something is worth
 * more than a shortcut to a set the dashboard already lists by name. Written as a function taking
 * the value out of the key first, so the nullability is settled here rather than inside a lambda
 * that runs long after the check.
 */
private fun maLongPress(
    key: MaRowKey,
    keyboardManager: dev.patrickgold.florisboard.ime.keyboard.KeyboardManager,
): (() -> Unit)? {
    val text = key.secondary
    if (text != null && text.isNotEmpty()) {
        return {
            for (ch in text) {
                keyboardManager.inputEventDispatcher.sendDownUp(
                    TextKeyData(code = ch.code, label = ch.toString()),
                )
            }
        }
    }
    if (key.cyclesOnLongPress) {
        return {
            keyboardManager.inputEventDispatcher.sendDownUp(
                TextKeyData(code = KeyCode.MA_ROW_NEXT_SET),
            )
        }
    }
    return null
}

/** One key: what is printed on it, and either a character to type or an action to fire. */
data class MaRowKey(
    val label: String,
    val code: Int? = null,
    val repeats: Boolean = false,
    /**
     * What a long press types, or null for a key that has no second character.
     *
     * A whole string rather than a character, because a second symbol worth reaching for is
     * sometimes two: an arrow made of a hyphen and a bracket, a pair of braces, a shell prefix.
     * Typed as text, so anything that can be written can be assigned.
     */
    val secondary: String? = null,
    /**
     * True when a long press moves to the next set.
     *
     * Sits on the last key of each set. On digits that is the zero, which is far too useful to give
     * up to a tap, so the cycle is on its long press; on the other sets the last key is a tilde or
     * an arrow and the same rule keeps it consistent. One place on the row, always, whatever it
     * currently says.
     */
    val cyclesOnLongPress: Boolean = false,
)

/**
 * The four sets, ten keys each.
 *
 * Grouped by the job in hand rather than by what happens to fit: brackets with brackets, arrows with
 * arrows, editing with editing. Mixing them, which is what one long strip did, means hunting past
 * nine things nobody wants to reach the one they do.
 */
object MaRowSets {
    /** The ten digits, in the order they are printed. Built with their second symbols by [digits]. */
    val DIGIT_LABELS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    /**
     * The digits, each carrying whatever second symbol is assigned to it.
     *
     * Zero used to hold the cycle to the next set on its long press, which made it the one key on
     * the row that could not have a second character. It has no cycle now and is an ordinary digit
     * like the other nine: the dashboard has a button per set, so the cycle was a shortcut to
     * something already one tap away, and it was costing the row its most useful key.
     */
    fun digits(secondaries: List<String>): List<MaRowKey> =
        DIGIT_LABELS.mapIndexed { index, label ->
            MaRowKey(label, secondary = secondaries.getOrNull(index)?.takeIf { it.isNotEmpty() })
        }

    val CROATIAN = listOf("č", "ć", "ž", "š", "đ", "Č", "Ć", "Ž", "Š").map { MaRowKey(it) } +
        MaRowKey("Đ", cyclesOnLongPress = true)

    val BRACKETS = listOf("(", ")", "[", "]", "{", "}", "<", ">", "|").map { MaRowKey(it) } +
        MaRowKey("~", cyclesOnLongPress = true)

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
        MaRowKey("\u21df", KeyCode.MOVE_END_OF_PAGE, cyclesOnLongPress = true),
    )

    /**
     * Select, move, and move text about, on the same ten keys.
     *
     * The workflow this is for is one thing: a paragraph has been dictated and a piece of it has to
     * be picked up and put somewhere else. Select all and select word start it, the selection lock
     * and the word jumps size it, and cut, copy and paste move it. Undo and redo sit at the end
     * because that workflow goes wrong more often than any other and the way back should not be in
     * some other row.
     *
     * Short words rather than glyphs. Scissors and clipboards read well at icon size in a flat
     * strip, but these are letter-sized keys, and a three letter word is unambiguous where a small
     * symbol is a guess.
     */
    val EDITING = listOf(
        MaRowKey("ALL", KeyCode.CLIPBOARD_SELECT_ALL),
        MaRowKey("WRD", KeyCode.MA_SELECT_WORD),
        MaRowKey("SEL", KeyCode.CLIPBOARD_SELECT),
        MaRowKey("\u00ab", KeyCode.MA_WORD_LEFT, repeats = true),
        MaRowKey("\u00bb", KeyCode.MA_WORD_RIGHT, repeats = true),
        MaRowKey("PST", KeyCode.CLIPBOARD_PASTE),
        MaRowKey("CUT", KeyCode.CLIPBOARD_CUT),
        MaRowKey("CPY", KeyCode.CLIPBOARD_COPY),
        MaRowKey("\u21b6", KeyCode.UNDO),
        MaRowKey("\u21b7", KeyCode.REDO, cyclesOnLongPress = true),
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
    onLongPress: (() -> Unit)? = null,
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
            if (onLongPress != null) {
                // Hold to move to the next set. Waiting first means the character is only typed if
                // the finger leaves before the threshold, which is what keeps zero usable as zero.
                val early = withTimeoutOrNull(400L) {
                    waitForUpOrCancellation()
                    true
                }
                if (early == null) {
                    onLongPress()
                    feedback.keyPress()
                    waitForUpOrCancellation()
                } else {
                    onFire()
                    feedback.keyPress()
                }
                return@awaitEachGesture
            }
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
        // The second symbol, small, in the corner, exactly where the letter keys print theirs.
        //
        // Printed rather than left to be discovered: a long press that types something is invisible
        // until it is found by accident, and the letters below already teach that a corner mark
        // means hold this. Dimmed so the digit stays the thing being read.
        key.secondary?.let { hint ->
            Text(
                text = hint,
                color = foreground.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 5.dp, top = 1.dp),
            )
        }
    }
}
