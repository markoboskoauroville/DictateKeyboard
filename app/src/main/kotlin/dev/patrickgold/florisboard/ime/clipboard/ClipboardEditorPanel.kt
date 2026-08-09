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

package dev.patrickgold.florisboard.ime.clipboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import androidx.compose.material3.Text

/**
 * The clipboard note editor, shown in the Smartbar's slot while a note is being written or rewritten
 * (see `KeyboardManager.clipboardEditorText`).
 *
 * It sits here rather than inside the clipboard panel for a reason that is easy to miss: an input
 * method cannot type into a text field of its own. The app on the other side of the input connection
 * holds the focus, and a field drawn inside the keyboard would never receive a keystroke. So the
 * keyboard stays visible and typing is intercepted upstream and folded into a plain string, which is
 * what this draws. The same trick already runs the emoji and GIF searches.
 *
 * There is no caret and no selection, and text is appended and removed at the end only. That is a
 * real limit rather than an oversight: a caret would need hit testing, a selection model and cursor
 * keys rerouted away from the app, which is a much larger piece of work. For fixing a typo, adding a
 * line, or writing a short note it is enough, and it does not pretend otherwise.
 */
@Composable
fun ClipboardEditorPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val text by keyboardManager.clipboardEditorText.collectAsState()
    val source by keyboardManager.clipboardEditorSource.collectAsState()
    val current = text ?: return

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(
            imageVector = Icons.Outlined.EditNote,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(20.dp),
        )
        val rowStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarCandidatesRow.elementName)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (current.isEmpty()) {
                SnyggText(
                    text = stringRes(
                        if (source == null) {
                            R.string.clipboard__editor__hint_new
                        } else {
                            R.string.clipboard__editor__hint_edit
                        },
                    ),
                )
            } else {
                // The row is one line tall, so a long note scrolls rather than being cut off with no
                // way to see what was typed. The end is what matters, since that is where typing
                // happens, so the last line stays in view.
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState(), reverseScrolling = true),
                    text = current,
                    color = rowStyle.foreground(),
                    overflow = TextOverflow.Clip,
                )
            }
        }
        // Discard on the left of save, so the destructive one is never where the thumb lands after
        // finishing a sentence.
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionTile.elementName,
            onClick = { keyboardManager.closeClipboardEditor() },
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            SnyggIcon(imageVector = Icons.Default.Close)
        }
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionTile.elementName,
            onClick = { keyboardManager.saveClipboardEditor() },
            modifier = Modifier.padding(end = 4.dp),
            enabled = current.isNotBlank(),
        ) {
            SnyggIcon(imageVector = Icons.Default.Check)
        }
    }
}
