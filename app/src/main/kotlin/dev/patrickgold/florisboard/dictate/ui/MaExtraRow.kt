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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.jetpref.datastore.model.collectAsState

/**
 * A row of digits, or of Croatian diacritics, above the keyboard.
 *
 * Both are things constantly reached for here and both are awkward to get at: digits mean switching
 * to the symbol layer and back, and č ć ž š đ mean holding a key and waiting for a popup, mid-word.
 * A row removes the wait for whichever of the two is wanted at the time.
 *
 * One row rather than two, and a swap rather than both at once, because keyboard height is the
 * scarcest thing on a phone screen and nobody needs digits and diacritics in the same sentence often
 * enough to pay a permanent row for each.
 */
@Composable
fun MaExtraRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val enabled by prefs.dictate.maExtraRow.collectAsState()
    val mode by prefs.dictate.maExtraRowMode.collectAsState()
    if (!enabled) return

    // Four modes, one row. Digits and diacritics are characters; symbols are characters too;
    // editing is key codes, which is why the row dispatches two different ways below.
    //
    // Both cases of every Croatian letter are present, so shift never has to be involved mid-word.
    val chars: List<String> = when (mode) {
        "diacritics" -> listOf("č", "ć", "ž", "š", "đ", "Č", "Ć", "Ž", "Š", "Đ")
        // Brackets in pairs and in the order a hand reaches for them, then the symbols that are
        // genuinely awkward to find: tilde, pipe, backtick and backslash all live two layers deep.
        "symbols" -> listOf("(", ")", "[", "]", "{", "}", "<", ">", "~", "|", "`", "\\")
        "editing" -> emptyList()
        else -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    }

    // Editing mode: moving and selecting, which is the row's whole point. Home and end sit on the
    // outside where a thumb lands, the four arrows in the middle, then select word, select all and
    // the three clipboard actions.
    val editing: List<Pair<String, Int>> = listOf(
        "\u21e4" to KeyCode.MOVE_START_OF_LINE,
        "\u2190" to KeyCode.ARROW_LEFT,
        "\u2192" to KeyCode.ARROW_RIGHT,
        "\u21e5" to KeyCode.MOVE_END_OF_LINE,
        "\u2191" to KeyCode.ARROW_UP,
        "\u2193" to KeyCode.ARROW_DOWN,
        "\u25ad" to KeyCode.MA_SELECT_WORD,
        "\u2b1a" to KeyCode.CLIPBOARD_SELECT_ALL,
        "\u2704" to KeyCode.CLIPBOARD_CUT,
        "\u29c9" to KeyCode.CLIPBOARD_COPY,
        "\u2913" to KeyCode.CLIPBOARD_PASTE,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chars.forEach { ch ->
            MaFlatKey(
                onFire = {
                    // Sent as the character's own code point, so autocapitalisation, the composing
                    // buffer and undo all behave exactly as they do for a key on the main layout.
                    keyboardManager.inputEventDispatcher.sendDownUp(
                        TextKeyData(code = ch.codePointAt(0), label = ch),
                    )
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { fg ->
                Text(text = ch, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            }
        }
        editing.forEach { (glyph, code) ->
            MaFlatKey(
                // Arrows repeat when held; the clipboard and selection keys do not, since firing
                // those twice is never what was meant.
                repeats = code == KeyCode.ARROW_LEFT || code == KeyCode.ARROW_RIGHT ||
                    code == KeyCode.ARROW_UP || code == KeyCode.ARROW_DOWN,
                onFire = {
                    keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData(code = code))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { fg ->
                Text(text = glyph, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}
