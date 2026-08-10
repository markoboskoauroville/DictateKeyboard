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

package dev.patrickgold.florisboard.app.settings.dictate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaMacroSyntax
import dev.patrickgold.florisboard.dictate.MaMacros
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * The macro bar editor: two boxes per button, a label and what it types.
 *
 * Deliberately plain. A macro bar is a small idea and an editor that turns it into a project is a
 * worse editor, so there is no drag-and-drop, no icon picker and no nesting: a list of rows, each a
 * list of buttons, each button two text fields. Everything saves as it is typed.
 *
 * Presets sit at the top. Several bars can be kept, one for editing, one for punctuation, one for
 * whatever a particular job needs, and switched from here or from the bar itself.
 */
@Composable
fun DictateMacroEditorScreen() = FlorisScreen {
    title = "Macro bar"
    iconSpaceReserved = false

    val prefs by FlorisPreferenceStore

    content {
        val scope = rememberCoroutineScope()
        val macroContext = LocalContext.current
        val clipboard = remember(macroContext) {
            macroContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        // Which token was copied last, so the confirmation names it rather than saying "copied" and
        // leaving the user to wonder which of twenty rows they actually hit.
        var copied by remember { mutableStateOf("") }
        val raw by prefs.dictate.maMacroBar.collectAsState()
        val activeIndex by prefs.dictate.maMacroPreset.collectAsState()

        // Never zero presets. Deleting the last row of a preset leaves it with no rows, the parser
        // drops an empty preset, and the screen was then returning early before the New preset button
        // was ever drawn: no presets, and no way to make one. A blank preset stands in instead, so
        // there is always something on screen to edit and always a way forward.
        val presets = remember(raw) {
            val parsed = if (raw.isBlank()) MaMacros.DEFAULT_PRESETS else MaMacros.parse(raw)
            parsed.ifEmpty { listOf(MaMacros.blankPreset()) }
        }
        val index = activeIndex.coerceIn(0, presets.size - 1)
        val preset = presets[index]

        /**
         * Persists the preset list, refusing ever to store a state that reads back as nothing.
         *
         * The failure this prevents: a preset holding one empty button serialises to a string whose
         * every field is empty, the parser discards empty buttons, and the preset comes back with
         * nothing in it. Enough of those in a row and the list reads back shorter than it was
         * written, which is how a list can quietly shrink to zero and strand the screen.
         *
         * So the result is parsed back before being trusted. If the round trip loses presets, a
         * blank one is substituted rather than writing a string that cannot be read.
         */
        fun write(updated: List<MaMacros.Preset>, newIndex: Int = index) {
            val safe = updated.ifEmpty { listOf(MaMacros.blankPreset()) }
            val encoded = MaMacros.serialize(safe)
            val survives = MaMacros.parse(encoded).size >= safe.size
            val finalEncoded = if (survives) {
                encoded
            } else {
                MaMacros.serialize(safe.map { preset ->
                    // A preset whose only button is empty gets a visible placeholder, so it has
                    // something to survive on and something to see on screen.
                    if (preset.rows.all { row -> row.all { it.label.isBlank() && it.macro.isBlank() } }) {
                        preset.copy(rows = listOf(listOf(MaMacros.Macro("\u2022", ""))))
                    } else {
                        preset
                    }
                })
            }
            scope.launch {
                prefs.dictate.maMacroBar.set(finalEncoded)
                prefs.dictate.maMacroPreset.set(newIndex.coerceIn(0, (safe.size - 1).coerceAtLeast(0)))
            }
        }

        /** Replaces the active preset, leaving the others alone. */
        fun writePreset(updated: MaMacros.Preset) {
            write(presets.toMutableList().also { it[index] = updated })
        }

        // Preset picker, rename, add, delete.
        var menuOpen by remember { mutableStateOf(false) }
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = preset.name,
                        onValueChange = { writePreset(preset.copy(name = it)) },
                        singleLine = true,
                        label = { Text("Preset name") },
                    )
                    TextButton(onClick = { menuOpen = true }) { Text("Switch") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        presets.forEachIndexed { i, p ->
                            DropdownMenuItem(
                                text = { Text(if (i == index) "${p.name}  ✓" else p.name) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { prefs.dictate.maMacroPreset.set(i) }
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = presets.size < MaMacros.MAX_PRESETS,
                        onClick = {
                            // Numbered, so a new preset is visibly a new one. Two called "New bar"
                            // look like nothing happened, which is what "it does not work" describes.
                            val used = presets.map { it.name }.toSet()
                            var n = presets.size + 1
                            while ("Bar $n" in used) n++
                            write(presets + MaMacros.blankPreset().copy(name = "Bar $n"), presets.size)
                        },
                    ) { Text("New preset") }
                    TextButton(
                        // Always available. Deleting the last one leaves a fresh blank rather than
                        // nothing, so there is no state with no presets and no way back.
                        enabled = true,
                        onClick = {
                            write(presets.filterIndexed { i, _ -> i != index }, 0)
                        },
                    ) { Text("Delete preset") }
                }
            }
        }

        preset.rows.forEachIndexed { rowIndex, row ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Row ${rowIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = preset.rows.size > 1,
                            onClick = {
                                writePreset(
                                    preset.copy(
                                        rows = preset.rows.filterIndexed { i, _ -> i != rowIndex },
                                    )
                                )
                            },
                        ) { Text("Remove row") }
                    }

                    row.forEachIndexed { btnIndex, macro ->
                        fun replaceButton(updated: MaMacros.Macro?) {
                            val newRow = row.toMutableList()
                            if (updated == null) newRow.removeAt(btnIndex) else newRow[btnIndex] = updated
                            val newRows = preset.rows.toMutableList()
                            if (newRow.isEmpty()) {
                                newRows.removeAt(rowIndex)
                            } else {
                                newRows[rowIndex] = newRow
                            }
                            writePreset(preset.copy(rows = newRows))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.width(96.dp),
                                value = macro.label,
                                onValueChange = { replaceButton(macro.copy(label = it)) },
                                singleLine = true,
                                label = { Text("Label") },
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                value = macro.macro,
                                onValueChange = { replaceButton(macro.copy(macro = it)) },
                                singleLine = true,
                                label = { Text("Types") },
                            )
                            IconButton(onClick = { replaceButton(null) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove this button",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val unknown = MaMacroSyntax.unknownTokens(macro.macro)
                        if (unknown.isNotEmpty()) {
                            Text(
                                text = "Not a key: " + unknown.joinToString(", ") { "{$it}" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    TextButton(
                        enabled = row.size < MaMacros.MAX_BUTTONS_PER_ROW,
                        onClick = {
                            val newRows = preset.rows.toMutableList()
                            newRows[rowIndex] = row + MaMacros.Macro("", "")
                            writePreset(preset.copy(rows = newRows))
                        },
                    ) { Text("Add button") }
                }
            }
        }

        TextButton(
            modifier = Modifier.padding(horizontal = 12.dp),
            enabled = preset.rows.size < MaMacros.MAX_ROWS,
            onClick = {
                writePreset(preset.copy(rows = preset.rows + listOf(listOf(MaMacros.Macro("", "")))))
            },
        ) { Text("Add row") }

        // The syntax, listed rather than explained. Anyone who has written an AutoHotkey line
        // already knows it; anyone who has not can copy a line from here.
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Special keys",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Anything in braces is a real key press, the same way AutoHotkey writes " +
                        "it. Everything else is typed as it stands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // Tap a token to copy it. Typing {Ctrl+Shift+Z} by hand into a small field, with the
                // braces on a symbol layer, is a slow way to make a typo; the reference is right
                // here, so it may as well hand the text over.
                MaMacroSyntax.HELP_TOKENS.forEach { (token, meaning) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Only the first token on a row that lists several, which is the one
                                // the row is named after and the one most likely wanted.
                                val single = token.trim().split(Regex("\\s+")).first()
                                clipboard.setPrimaryClip(ClipData.newPlainText("macro", single))
                                copied = single
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = token,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(148.dp),
                        )
                        Text(
                            text = meaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (copied.isNotEmpty()) {
                    Text(
                        text = "$copied copied. Paste it into a Types box above.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = "Tap any key above to copy it. A key only does something if the app you are typing into listens for " +
                        "it. Ctrl+A, Ctrl+C, Ctrl+V and Ctrl+Z work in most text fields; an exotic " +
                        "key may be delivered correctly and simply ignored.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
