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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R

/**
 * The feature row: one row, ten keys, everything this app can do reachable from the dictation view
 * without going to settings first.
 *
 * The rule it exists to satisfy is Marko's, and it is a good one: a feature you have to enable in a
 * settings app before you can see it is a feature most people never find. The dictation view is not
 * the full keyboard, so it has the vertical room the typing view does not, and this spends some of
 * that room on reach.
 *
 * Three things borrowed from keyboards that have already been judged by very large numbers of
 * people:
 *
 * - **Gboard put its shortcuts in key-shaped buttons and then deleted its overflow menu**, freeing
 *   the slot it occupied. A shortcut behind a menu is a shortcut to a menu. Every key here acts on
 *   the first tap; none of them opens a list of more keys except the dashboard, which is a list of
 *   switches rather than of actions.
 * - **HeliBoard distributes its toolbar keys evenly** rather than packing them from one edge. Equal
 *   weights below, so the row reads as a row and the thumb learns positions rather than icons.
 * - **Ten keys is exactly what the letter row already is.** The usual objection to ten controls in a
 *   phone-width row is that each falls under the 48dp touch minimum, and at a typical 360dp width
 *   these are about 36dp. But the QWERTY row above is ten keys at the same width, on the same
 *   device, in the same hand, and it has been usable for as long as touch keyboards have existed.
 *   The precedent is not theoretical, it is one row up.
 *
 * Colour is state only, as everywhere else in this app: gold ink on the near-black key, and green
 * on the two keys that are switches so their position is readable without pressing them.
 */
@Composable
fun MaFeatureRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    // Two zones, and that is the whole keyboard. Zone one is the edit strip along the top, zone two
    // is everything from the number row down to the bottom row.
    val zone1 by prefs.dictate.maEditRow.collectAsState()
    val zone2 by prefs.dictate.maZoneKeyboard.collectAsState()

    // Green when the zone is showing, dark when it is folded away, so the row is a map of what is
    // above it and can be read without pressing anything.
    val onGreen = Color(0xFF6FA85A)

    // Long press anywhere here folds the row away. The finger is already on the row it wants gone.
    val fold: () -> kotlin.Unit = { scope.launch { prefs.dictate.maFeatureRowShown.set(false) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val keyMod = Modifier.weight(1f).fillMaxHeight()

        // 1, the edit strip. Paste, copy, history and the rest of the top row.
        ThemedTextKey("1", keyMod, if (zone1) onGreen else null, fold) {
            scope.launch { prefs.dictate.maEditRow.set(!zone1) }
        }

        // 2, the keyboard itself, all of it at once. This is the one that gives back real estate,
        // and on a keyboard driven by voice it is off more often than it is on.
        ThemedTextKey("2", keyMod, if (zone2) onGreen else null, fold) {
            scope.launch { prefs.dictate.maZoneKeyboard.set(!zone2) }
        }

        // The reader. LLL: it speaks the clipboard and lights each word as it is said.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringRes(R.string.ma__feature_lll),
            modifier = keyMod,
            onLongClick = fold,
        ) {
            keyboardManager.activeState.imeUiMode = ImeUiMode.READER
        }

        // The microphone, last, and the reason the keyboard can be folded away entirely. With zone
        // two closed there is no key left that reaches the dictation screen, so the way between the
        // two views has to live in the row that always survives.
        val inTranscribe = keyboardManager.activeState.imeUiMode == ImeUiMode.TRANSCRIBE
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = if (inTranscribe) Icons.Default.Keyboard else Icons.Default.Mic,
            contentDescription = stringRes(
                if (inTranscribe) R.string.ma__feature_keyboard else R.string.ma__feature_mic,
            ),
            modifier = keyMod,
            onLongClick = fold,
        ) {
            keyboardManager.activeState.imeUiMode =
                if (inTranscribe) ImeUiMode.TEXT else ImeUiMode.TRANSCRIBE
        }
    }
}

/** A round key carrying a numeral, styled exactly as every other key in the row. */
@Composable
private fun ThemedTextKey(
    label: String,
    modifier: Modifier,
    tint: Color?,
    onLongClick: () -> kotlin.Unit,
    onClick: () -> kotlin.Unit,
) {
    ThemedKey(
        code = KeyCode.NOOP,
        modifier = modifier,
        onLongClick = onLongClick,
        onClick = onClick,
    ) { fg ->
        Text(
            text = label,
            color = tint ?: fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
