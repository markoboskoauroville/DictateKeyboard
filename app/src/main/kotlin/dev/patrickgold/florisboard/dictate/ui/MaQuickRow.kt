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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaCase
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.dictate.MaSpeed
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private val MaQuickKeyShape = RoundedCornerShape(12.dp)
private val MaQuickMarginH = 3.dp
private val MaQuickMarginV = 4.dp

private fun maQuickAttributes(code: Int) = mapOf(
    FlorisImeUi.Attr.Code to code,
    FlorisImeUi.Attr.Mode to KeyboardMode.CHARACTERS.toString(),
    FlorisImeUi.Attr.ShiftState to InputShiftState.UNSHIFTED.toString(),
)

/**
 * The quick row of the transcribe view: the language toggle, the four case buttons, and the current
 * transcription model as a dropdown.
 *
 * Changing model used to mean leaving the keyboard for the settings app entirely, and the language
 * meant tapping a chip repeatedly to cycle a list. Both are checks worth making in the second before
 * speaking, so both belong in the view where the speaking happens.
 *
 * **One language key, not one per language.** It drew a button for every enabled language and lit
 * the active one, which is a radio group drawn the expensive way: it spends the width of every
 * option to say what one word already says. That shape is only right while there might be a third
 * language. There are two and permanently two, so a toggle is the honest control, and the width it
 * gives back goes to the keys either side of it on the narrowest surface in the app.
 */
@Composable
fun MaQuickRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    // Its own name: the Row below already declares a `context` for the case buttons, and two of the
    // same name in one scope is a redeclaration, not a shadow.
    val maContext = LocalContext.current

    val activeCode by prefs.dictate.activeInputLanguage.collectAsState()
    // The badge, through MaLanguage, so this row shows the same word as the recording bar and the
    // suggestion strip. Anything that folds a third possibility back in is folding auto-detect back
    // in, which was removed on purpose.
    val languageBadge = remember(activeCode) { MaLanguage.badge() }

    val speed by prefs.dictate.maSpeed.collectAsState()
    val speedScope = rememberCoroutineScope()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ONE key, not one per language. This row used to draw a button for every enabled language
        // and light the active one, which is a radio group drawn the expensive way: it spends the
        // width of every option to say a thing that one word already says, on the narrowest surface
        // in the app.
        //
        // It is only a radio group at all while there might be three of them. There are two, and
        // permanently two, so the honest control is a toggle: it shows where you are and one tap is
        // the only move available. Auto-detect went with the languages it belonged to.
        //
        // Through MaLanguage rather than setLanguage, because this has to move the suggestion
        // language with it, or the two drift apart from the one place they are most obviously
        // expected to agree.
        //
        // No selected state, and that is not an omission. A toggle showing HR is not "HR is on", it
        // is "you are in HR"; lighting it would say the first, and then the eye asks what the off
        // state means and there is no answer.
        MaQuickKey(
            selected = false,
            onClick = { MaLanguage.toggle(maContext) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg ->
            Text(
                text = languageBadge,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }

        // FAST or SLOW, in the slot the language buttons gave back. Marko's: one saving paid straight
        // into the next thing worth reaching without opening settings.
        //
        // The two words name the experience rather than the wire format. SLOW uploads a small
        // compressed file, waits for the job, and costs a third as much; FAST posts the audio and the
        // transcript comes back in the same breath, at about 134 ms, for three times the price. Which
        // one is worth it depends on whether the sentence is worth waiting for, and that is a
        // judgement made in the second before speaking, which is why it belongs on this row and not
        // in a settings screen.
        //
        // Shown like the language key, without a lit state, for the same reason: it says where you
        // are, and one tap is the only move. A pressed-looking FAST would invite the question of what
        // an unpressed FAST would mean, and there is no such thing.
        //
        // FAST IS A PREFERENCE, NEVER A PROMISE, and the key does not pretend otherwise. Anything
        // past the sync endpoint's two minute cap goes down the slow path automatically and silently,
        // because a long recording is the one that took the most effort and must never be the one
        // that fails. See DictateController.maUseSyncPath.
        MaQuickKey(
            selected = false,
            onClick = {
                speedScope.launch {
                    prefs.dictate.maSpeed.set(if (speed == MaSpeed.FAST) MaSpeed.SLOW else MaSpeed.FAST)
                }
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { fg ->
            Text(
                text = if (speed == MaSpeed.FAST) "FAST" else "SLOW",
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
        // Four case buttons, and they only ever act on text that already exists. They set nothing,
        // remember nothing, and have no on state: press one and the words in the field are rewritten
        // there and then.
        //
        // This replaces them having also decided how the next dictation would be written. That made
        // each button a mode as well as an action, so pressing one changed something invisible and
        // the effect of the next dictation depended on a setting nobody could see. A transcript now
        // arrives exactly as the service wrote it, and the case is a decision taken afterwards,
        // looking at the result.
        val caseScope = rememberCoroutineScope()
        val context = LocalContext.current
        listOf(
            MaCase.LOWER to "ab",
            MaCase.UPPER to "AB",
            MaCase.SENTENCE to "Ab",
            MaCase.TITLE to "Ab Ab",
        ).forEach { (mode, label) ->
            MaQuickKey(
                // Never highlighted: an action has no state to show, and a gold outline here would
                // claim a mode that no longer exists.
                selected = false,
                onClick = { caseScope.launch { DictateController.recaseField(context, mode) } },
                modifier = Modifier.weight(if (mode == MaCase.TITLE) 1.1f else 0.8f).fillMaxHeight(),
            ) { fg ->
                Text(text = label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}


/** One key of the quick row, themed like every other key so it follows the active stylesheet. */
@Composable
private fun MaQuickKey(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (Color) -> Unit,
) {
    // Selection is an outline, not a fill. Borrowing the enter key's styling made the chosen chip a
    // block of solid accent, the brightest thing on a dark keyboard, for something that only needs
    // to be distinguishable from two neighbours. A gold stroke does that without shouting, and the
    // key underneath stays the same colour as every other key.
    val style = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, maQuickAttributes(KeyCode.NOOP))
    val background = style.background(default = Color.White.copy(alpha = 0.08f))
    val foreground = if (selected) MaQuickGold else style.foreground(default = Color.White)
    Box(
        modifier = modifier
            .padding(horizontal = MaQuickMarginH, vertical = MaQuickMarginV)
            .clip(MaQuickKeyShape)
            .background(background)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) MaQuickGold else Color.Transparent,
                shape = MaQuickKeyShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(foreground)
    }
}

/** The one gold used for "this is the chosen one", matching the record key's ink. */
private val MaQuickGold = Color(0xFFE8B15C)
