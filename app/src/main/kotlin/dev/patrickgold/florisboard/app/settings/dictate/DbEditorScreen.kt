/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
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

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.computeDisplayName
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

/**
 * The db editor.
 *
 * db is the panel that covers the keyboard and holds every switch this app has: which set the number
 * row shows, and which parts of the interface are drawn at all. This screen decides what is on it
 * and in what order, on a whole phone screen rather than through a grid of tiles on a keyboard.
 *
 * There are two lists. The first is what db shows, top to bottom, in the order the tiles appear. The
 * second is everything set aside. An entry moves between them, and moves within the first, and that
 * is the whole model; there is nothing else to learn and nothing that can be put into a state the
 * keyboard cannot draw.
 *
 * Every change is written the moment it is made. There is no save button and no draft: a settings
 * screen that can be left in an unsaved state is a settings screen that loses work, and this one is
 * opened mid sentence.
 *
 * The arrangement is shared with the keyboard's own tile editor, so a change here shows there and a
 * change there shows here. This screen deliberately does not touch the sticky action, which belongs
 * to a smartbar layout this app no longer uses.
 */
@Composable
fun DbEditorScreen() = FlorisScreen {
    title = "db"
    previewFieldVisible = false
    iconSpaceReserved = false

    val prefs by FlorisPreferenceStore

    content {
        val scope = rememberCoroutineScope()
        val arrangement by prefs.smartbar.actionArrangement.collectAsState()

        /** Writes an arrangement, and is the only path by which this screen changes anything. */
        fun commit(next: QuickActionArrangement) {
            scope.launch { prefs.smartbar.actionArrangement.set(next) }
        }

        fun move(from: Int, to: Int) {
            val shown = arrangement.dynamicActions.toMutableList()
            if (from !in shown.indices || to !in shown.indices) return
            shown.add(to, shown.removeAt(from))
            commit(arrangement.copy(dynamicActions = shown))
        }

        fun hide(action: QuickAction) {
            commit(
                arrangement.copy(
                    dynamicActions = arrangement.dynamicActions.filterNot { it == action },
                    hiddenActions = arrangement.hiddenActions + action,
                ),
            )
        }

        fun show(action: QuickAction) {
            commit(
                arrangement.copy(
                    dynamicActions = arrangement.dynamicActions + action,
                    hiddenActions = arrangement.hiddenActions.filterNot { it == action },
                ),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

            DbHelpCard()

            DbSectionTitle(text = "On db")
            if (arrangement.dynamicActions.isEmpty()) {
                DbEmptyNote(
                    text = "Nothing on db. Add something from the list below, or db opens empty.",
                )
            }
            arrangement.dynamicActions.forEachIndexed { index, action ->
                DbRow(
                    label = action.computeDisplayName(evaluator = DefaultComputingEvaluator),
                    position = "${index + 1}",
                ) {
                    IconButton(
                        onClick = { move(index, index - 1) },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(
                        onClick = { move(index, index + 1) },
                        enabled = index < arrangement.dynamicActions.lastIndex,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { hide(action) }) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Set aside")
                    }
                }
            }

            Spacer(modifier = Modifier.size(20.dp))

            DbSectionTitle(text = "Set aside")
            if (arrangement.hiddenActions.isEmpty()) {
                DbEmptyNote(text = "Nothing set aside. Everything this app has is on db.")
            }
            arrangement.hiddenActions.forEach { action ->
                DbRow(
                    label = action.computeDisplayName(evaluator = DefaultComputingEvaluator),
                    position = null,
                ) {
                    IconButton(onClick = { show(action) }) {
                        Icon(Icons.Default.Visibility, contentDescription = "Put on db")
                    }
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { commit(QuickActionArrangement.Default) }) {
                    Text(text = "Reset db to default")
                }
            }
        }
    }
}

/**
 * How to open db, said once at the top.
 *
 * db has no button of its own on the keyboard any more; the strip it used to sit in is given over to
 * suggestions. That is a good trade only if the two ways in are stated somewhere, and the screen
 * that edits db is where somebody will be when they wonder.
 */
@Composable
private fun DbHelpCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = "Opening db",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "On the keyboard, hold the microphone key in the copy and paste row.\n\n" +
                "In the dictation view, tap db, above the gear in the corner.\n\n" +
                "Close it with the X in the top left of the panel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DbSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DbEmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * One entry: where it sits, what it is, and what can be done to it.
 *
 * The position is printed rather than implied by order alone, because the panel it describes is a
 * grid that wraps, and a number is the only thing that survives the wrap.
 */
@Composable
private fun DbRow(
    label: String,
    position: String?,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = position,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        actions()
    }
}
