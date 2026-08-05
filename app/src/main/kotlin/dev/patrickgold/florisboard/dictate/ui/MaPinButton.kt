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

    Box(
        modifier = modifier
            .size(30.dp)
            .clickable {
                scope.launch {
                    prefs.dictate.maPinnedView.set(if (isPinnedHere) "" else mode.name)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPinnedHere) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = if (isPinnedHere) {
                "This view opens first. Tap to unpin."
            } else {
                "Pin this view so it opens first"
            },
            tint = if (isPinnedHere) foreground else foreground.copy(alpha = 0.45f),
            modifier = Modifier.padding(6.dp).size(16.dp),
        )
    }
}
