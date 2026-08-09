/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.dictate.gif.GifSearchPanel
import dev.patrickgold.florisboard.dictate.ui.MaMacroBar
import dev.patrickgold.florisboard.dictate.ui.MaCursorRow
import dev.patrickgold.florisboard.dictate.ui.MaFeatureRow
import dev.patrickgold.florisboard.dictate.ui.MaExtraRow
import dev.patrickgold.florisboard.dictate.ui.LegacyEditRow
import dev.patrickgold.florisboard.ime.clipboard.ClipboardEditorPanel
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSearchPanel
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()
    val clipboardEditorActive by keyboardManager.clipboardEditorText.collectAsState()
    val emojiSearchActive by keyboardManager.emojiSearchQuery.collectAsState()
    val gifSearchActive by keyboardManager.gifSearchQuery.collectAsState()

    InlineSuggestionsStyleCache()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        // While an emoji search is running (issue #110), the search panel takes the Smartbar's slot so the
        // keyboard layout below stays available for typing the query.
        if (clipboardEditorActive != null) {
            // Writing or rewriting a clipboard note. Same arrangement as the two searches: the editor
            // takes the Smartbar's slot and the keyboard below it does the typing, because an input
            // method has no way to type into a field of its own.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                ClipboardEditorPanel()
            }
        } else if (emojiSearchActive != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                EmojiSearchPanel()
            }
        } else if (gifSearchActive != null) {
            // GIF search: a results strip takes the Smartbar's slot; the keyboard below types the query.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.smartbarHeight),
            ) {
                GifSearchPanel()
            }
        } else {
            // The pin sits at the very left of the top row, so it is the first thing in the corner
            // of the keyboard view. The Smartbar takes the rest of the width unchanged.
            // No pin. Remembering the last view unconditionally does the same job without a
            // control to understand, and a pin that has to be found and pressed to keep a view is a
            // worse answer than simply not losing it.
            Smartbar()
        }
        // Macro bar (Marko): user-defined buttons directly under the Smartbar, in the same flat
        // style as the arrow strip at the bottom. Hidden while the overflow panel is open, which
        // owns the whole area below it. It draws nothing when the active preset has no rows, so it
        // needs no toggle of its own: emptying it is how it is turned off.
        if (!state.isActionsOverflowVisible) {
            MaMacroBar()
        }
        // The copy and paste row (Marko), the same one the transcribe view draws, in the space the
        // macro bar fix freed in build 88. Both views now show the identical row from the identical
        // code, so arranging it in Settings arranges it everywhere and the two cannot drift apart.
        if (!state.isActionsOverflowVisible) {
            val maEditRowEnabled by prefs.dictate.maEditRow.collectAsState()
            if (maEditRowEnabled) {
                LegacyEditRow(keyboardManager = keyboardManager)
            }
        }
        // The extra row sits directly above the keys, where a number row belongs, and renders
        // nothing at all when switched off.
        // Zone two, part one: the number row. Gated on the zone as well as on its own switch, so
        // folding the keyboard away takes the number row with it without erasing the decision about
        // whether it should be there when the keyboard comes back.
        val maZoneKeyboard by prefs.dictate.maZoneKeyboard.collectAsState()
        if (!state.isActionsOverflowVisible && maZoneKeyboard) {
            MaExtraRow()
        }
        if (state.isActionsOverflowVisible) {
            QuickActionsOverflowPanel()
        } else {
            // Zone two, part two: the keys themselves, from the letters to the bottom row.
            //
            // Not composed at all rather than filtered row by row. The keyboard sets its own height,
            // so leaving it out returns that height exactly, with no arithmetic anywhere that could
            // fall out of step and leave a band of nothing where the keys used to be. That is what
            // went wrong when this was done by removing rows.
            //
            // Nothing is stranded by this. The arrow strip and the feature row always survive, the
            // feature row carries the way back, and it carries the microphone so the dictation view
            // is still one key away with the whole keyboard closed.
            if (maZoneKeyboard) Box {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        FlorisImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator)
            }
            // Arrow strip along the very bottom, below the letters, exactly where the reference
            // keyboard puts it.
            val maCursorRowEnabled by prefs.dictate.maCursorRow.collectAsState()
            val maExtraRowOn by prefs.dictate.maExtraRow.collectAsState()
            val maExtraRowMode by prefs.dictate.maExtraRowMode.collectAsState()
            // The bottom strip stands down while the extra row is in editing mode. Two rows of
            // arrows on one keyboard is one row of arrows wasted, and the taller keyboard costs more
            // than the second set of keys is worth.
            val maEditingRowShown = maExtraRowOn && maExtraRowMode == "editing"
            if (maCursorRowEnabled && !maEditingRowShown) {
                MaCursorRow()
            }
            // The feature row, along the very bottom, the same one the transcribe view draws and
            // from the same code, so the two views cannot drift apart.
            //
            // It has to be here. The fold key that controls it lives in the arrow strip above, and
            // that strip is shared between both views, so without this row the key was drawn in the
            // keyboard view too and folded something that was not there. A control that does nothing
            // is worse than a missing one, because it teaches that pressing things has no effect.
            //
            // Folded away it is not composed at all, so the height is genuinely returned rather than
            // left as an empty strip holding the space it was asked to give up.
            val maFeatureRowShown by prefs.dictate.maFeatureRowShown.collectAsState()
            if (maFeatureRowShown) {
                MaFeatureRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.smartbarHeight),
                )
            }
        }
    }
}
