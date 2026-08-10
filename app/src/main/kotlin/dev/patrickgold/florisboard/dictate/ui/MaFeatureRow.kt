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
import dev.patrickgold.florisboard.dictate.MaFeatureOrder
import dev.patrickgold.florisboard.dictate.MaFeatureKey
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R

/**
 * The feature row: one row, nine keys, drawn by both views and the last row standing when everything
 * above it is folded away.
 *
 * `AP · select-all · backspace · mic · book · 1 2 3 · enter`, **by default**. The order now lives in a
 * preference and is rearranged by the user in Settings, Mantra, Feature row; what follows is the
 * reasoning behind the default and the shape of the row, not a description of what is on screen.
 *
 * **This order is Marko's and it has been corrected twice.** At build 139 the switches were on the
 * left and the borrowed keys on the right, and he swapped them. At build 146 he moved the microphone
 * in beside backspace, put the book next to it, and sent enter to the far end. Do not rearrange this
 * row on a theory; it is arranged by the person using it.
 *
 * What the current order means, so a future change can tell what it would be breaking:
 *
 * - **The busy end is the end the thumb starts from.** AP, select-all and backspace are used inside
 *   a sentence; the numbered switches are reached a few times a session.
 * - **The two view swaps sit together.** The microphone and the book are the only keys here that
 *   change which view is on screen, and a pair that does one kind of thing is found by feel. They
 *   were at opposite ends of the row before, which is the arrangement that made that hard.
 * - **Enter is bottom right**, where every keyboard ever made has put it. Borrowing a habit somebody
 *   already has beats any argument about grouping.
 * - **The numbers read left to right** as the parts of the keyboard they control.
 * - **AP, select-all, backspace and enter are the keys that must survive a fold.** The first two are
 *   the copy row's most used pair; the last two are the only keys from the keyboard proper with no
 *   substitute anywhere else once zone two is shut.
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
 * on the three keys that are switches so their positions are readable without pressing them.
 */
@Composable
fun MaFeatureRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    // Three zones, and that is the whole keyboard, numbered as they are stacked on screen from the
    // keys outwards: 1 the number row, 2 the keys themselves, 3 the copy and paste row along the
    // top. Each has one switch and each switch has one key here.
    //
    // The numbering is Marko's and it is the arrangement, not a naming: he drew the three dividing
    // lines on the keyboard and these are the three parts they cut it into.
    val zone1 by prefs.dictate.maExtraRow.collectAsState()
    val zone2 by prefs.dictate.maZoneKeyboard.collectAsState()
    val zone3 by prefs.dictate.maEditRow.collectAsState()

    // Green when the zone is showing, dark when it is folded away, so the row is a map of what is
    // above it and can be read without pressing anything.
    //
    // Each key shows its own switch rather than what is actually on screen. Folding the keys away
    // with 2 takes the number row with them, since the number row sits inside that zone, but it
    // must not erase the decision about whether the number row should be there when the keys come
    // back. So 1 can be green while nothing is visible, and that is the truth about the switch.
    val onGreen = Color(0xFF6FA85A)

    // Long press anywhere here folds the row away. The finger is already on the row it wants gone.
    val fold: () -> kotlin.Unit = { scope.launch { prefs.dictate.maFeatureRowShown.set(false) } }

    // Select-all becomes deselect when there is a selection, so the key it draws has to know. Read
    // once here and handed down, exactly as the copy row reads it.
    val editorInstance by context.editorInstance()
    val editorContent by editorInstance.activeContentFlow.collectAsState()
    val hasSelection = editorContent.selection.isSelectionMode

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val keyMod = Modifier.weight(1f).fillMaxHeight()

        // THE ORDER IS THE USER'S, read from a preference and rearranged in Settings, Mantra,
        // Feature row. It was corrected by hand twice, at builds 139 and 146, both times from a
        // screenshot with arrows drawn on it and both times in the direction opposite to what looked
        // sensible from inside this file. That is the argument for the editor: the person holding the
        // keyboard should not have to send a picture and wait for a build to move a key.
        //
        // parse() always returns all nine, whatever is stored. Order is the only thing the editor can
        // change, and that is a safety rule rather than a simplification: this row is the one that
        // survives when every other row is folded away, so it is the only route to backspace, to
        // enter and to the microphone. Rearranging cannot lock anybody out of anything. Hiding could.
        val orderRaw by prefs.dictate.maFeatureRowOrder.collectAsState()
        val order = remember(orderRaw) { MaFeatureOrder.parse(orderRaw) }

        order.forEach { key ->
            when (key) {
                MaFeatureKey.ALL_PASTE, MaFeatureKey.SELECT_ALL, MaFeatureKey.BACKSPACE -> {
                    // The three borrowed from the copy row, drawn by that row's own code rather than
                    // rebuilt here. AP and select-all are its two most used keys, and backspace is
                    // the one key from the keyboard proper that nothing else can stand in for: with
                    // zone two closed there is no other way to delete a character.
                    //
                    // These three do not fold the row on a long press, and must not. Backspace holds
                    // to repeat and swipes to select, which is the behaviour it has everywhere else
                    // in this app, and a key that repeats cannot also mean something else when held.
                    LegacyActionKey(
                        action = when (key) {
                            MaFeatureKey.ALL_PASTE -> LegacyEditAction.ALL_PASTE
                            MaFeatureKey.SELECT_ALL -> LegacyEditAction.SELECT_ALL
                            else -> LegacyEditAction.BACKSPACE
                        },
                        modifier = keyMod,
                        keyboardManager = keyboardManager,
                        hasSelection = hasSelection,
                    )
                }

                MaFeatureKey.MIC -> {
                    // The two ways out of this view, side by side: the microphone to the dictation screen and
                    // the book to the reader. They moved here from the far end at Marko's instruction, and
                    // grouping them is the point rather than a side effect. They are the same kind of key, the
                    // only two that change which view you are looking at, and a pair that does one kind of thing
                    // is easier to find by feel than two keys of the same kind at opposite ends of a row.
                    //
                    // The microphone is also the reason the keyboard can be folded away entirely. With zone two
                    // closed there is no key left anywhere that reaches the dictation screen, so the way between
                    // the two views has to live in the row that always survives. Wherever this row is rearranged
                    // in future, the microphone stays in it.
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

                MaFeatureKey.BOOK -> {
                    // The reader. LLL: it speaks the clipboard and lights each word as it is said. Beside the
                    // microphone, because listening and dictating are the two halves of what this app is for.
                    ThemedIconKey(
                        code = KeyCode.NOOP,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringRes(R.string.ma__feature_lll),
                        modifier = keyMod,
                        onLongClick = fold,
                    ) {
                        keyboardManager.activeState.imeUiMode = ImeUiMode.READER
                    }
                }

                MaFeatureKey.ZONE_1 -> {
                    // 1, the number row. Digits, or whichever set the row is showing.
                    ThemedTextKey("1", keyMod, if (zone1) onGreen else null, fold) {
                        scope.launch { prefs.dictate.maExtraRow.set(!zone1) }
                    }
                }

                MaFeatureKey.ZONE_2 -> {
                    // 2, the keyboard itself, all of it at once. This is the one that gives back real estate,
                    // and on a keyboard driven by voice it is off more often than it is on.
                    ThemedTextKey("2", keyMod, if (zone2) onGreen else null, fold) {
                        scope.launch { prefs.dictate.maZoneKeyboard.set(!zone2) }
                    }
                }

                MaFeatureKey.ZONE_3 -> {
                    // 3, the copy and paste row along the top. Paste, copy, history and the rest of it.
                    ThemedTextKey("3", keyMod, if (zone3) onGreen else null, fold) {
                        scope.launch { prefs.dictate.maEditRow.set(!zone3) }
                    }
                }

                MaFeatureKey.ENTER -> {
                    // Enter, last, which is where every keyboard ever made has put it. It was beside backspace
                    // until Marko moved it here, and the bottom right corner is a position the thumb finds
                    // without looking because it has been finding it on other keyboards for decades. Borrowing a
                    // habit somebody already has is worth more than any argument about grouping.
                    //
                    // Still the same key the bottom row draws rather than a new one that types a newline: tap
                    // for a newline, hold for the character popup from the settings screen. And still here for
                    // the reason backspace is, that with zone two folded away there is no enter key anywhere
                    // else, and a keyboard that cannot end a line has to be unfolded to finish a sentence.
                    //
                    // No fold on long press. The hold already means the popup.
                    LegacyEnterKey(
                        keyboardManager = keyboardManager,
                        modifier = keyMod,
                    )
                }
            }
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
