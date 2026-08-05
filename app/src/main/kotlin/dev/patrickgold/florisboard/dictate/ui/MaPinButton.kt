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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * A pin in the top-left corner of each view, deciding which one opens first.
 *
 * Before this the app guessed, by remembering whichever view was last used. Guessing is fine until
 * it guesses wrong, and then there is no way to tell it otherwise. Pinning is the same decision made
 * out loud: tap the pin in the view you want and every keyboard from then on opens there.
 *
 * Tapping the pin in the already-pinned view unpins it, which hands the choice back to the
 * last-used behaviour rather than forcing a pin to exist. Filled means this view is pinned, outline
 * means it is not.
 *
 * @param mode the view this particular pin sits in.
 */
@Composable
fun MaPinButton(
    mode: ImeUiMode,
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val pinned by prefs.dictate.maPinnedView.collectAsState()
    val holdOpen by prefs.dictate.maHoldOpen.collectAsState()
    val isPinnedHere = pinned == mode.name

    // Borrows the keyboard's own key colours so the pin belongs to whatever theme is active rather
    // than being a foreign dot in the corner.
    val style = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        mapOf(
            FlorisImeUi.Attr.Code to KeyCode.NOOP,
            FlorisImeUi.Attr.Mode to KeyboardMode.CHARACTERS.toString(),
            FlorisImeUi.Attr.ShiftState to InputShiftState.UNSHIFTED.toString(),
        ),
    )
    val foreground = style.foreground(default = Color.White)

    // Three states, cycled by tapping, in the order they escalate:
    //
    //   outline  nothing pinned, the last-used view opens
    //   filled   this view opens first
    //   green    this view opens first AND the keyboard stays up until dismissed by hand
    //
    // Escalation is the right shape here: holding the keyboard open only makes sense once a view has
    // been chosen, and one control that goes further each tap is easier to reason about than two
    // separate toggles that can disagree.
    val stage = when {
        isPinnedHere && holdOpen -> 2
        isPinnedHere -> 1
        else -> 0
    }
    val green = Color(0xFF56D364)

    Box(
        modifier = modifier
            .size(30.dp)
            .clickable {
                scope.launch {
                    when (stage) {
                        0 -> prefs.dictate.maPinnedView.set(mode.name)
                        1 -> prefs.dictate.maHoldOpen.set(true)
                        else -> {
                            prefs.dictate.maHoldOpen.set(false)
                            prefs.dictate.maPinnedView.set("")
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (stage == 0) Icons.Outlined.PushPin else Icons.Filled.PushPin,
            contentDescription = when (stage) {
                0 -> "Pin this view so it opens first"
                1 -> "This view opens first. Tap again to keep the keyboard open."
                else -> "Keyboard held open. Tap to release."
            },
            tint = when (stage) {
                0 -> foreground.copy(alpha = 0.45f)
                1 -> foreground
                else -> green
            },
            modifier = Modifier.padding(6.dp).size(16.dp),
        )
    }
}
