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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.MaLivePrompts
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The little man's settings: the buttons that sit beside him, and the instructions he remembers.
 *
 * Two boxes per button, a label and the prompt sent to the model, laid out exactly like the macro
 * editor because it is the same idea applied to a different thing and learning one layout twice is
 * a waste of somebody's attention.
 *
 * The prompts that ship are Marko's own, not the fork's Fix Grammar and Improve Writing. Everything
 * here is editable, so a shipped prompt is a starting point rather than a fixture.
 */
@Composable
fun DictateLittleManScreen() = FlorisScreen {
    title = "Little man"
    previewFieldVisible = true
    iconSpaceReserved = false

    content {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val db = remember { PromptsDatabaseHelper.getInstance(context) }
        val prompts = remember { mutableStateListOf<PromptModel>() }
        var loaded by remember { mutableStateOf(false) }
        var spoken by remember { mutableStateOf(MaLivePrompts.list()) }

        LaunchedEffect(Unit) {
            val all = withContext(Dispatchers.IO) { db.getAll() }
            prompts.clear()
            prompts.addAll(all)
            loaded = true
        }

        /** Writes one row back. Saving per edit rather than behind a button: there is no draft here. */
        fun persist(model: PromptModel) {
            scope.launch(Dispatchers.IO) { db.update(model) }
        }

        Text(
            text = "The buttons beside the little man. The label is what the button says; the prompt " +
                "is what gets sent to the model along with your text. Edits save as you type.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )

        prompts.forEachIndexed { index, model ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = model.name.orEmpty(),
                            onValueChange = {
                                val updated = model.copy(name = it)
                                prompts[index] = updated
                                persist(updated)
                            },
                            label = { Text("Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val removed = prompts.removeAt(index)
                            scope.launch(Dispatchers.IO) { db.delete(removed.id) }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete this prompt",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = model.prompt.orEmpty(),
                        onValueChange = {
                            val updated = model.copy(prompt = it)
                            prompts[index] = updated
                            persist(updated)
                        },
                        label = { Text("Prompt sent to the model") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }

        if (loaded) {
            TextButton(
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = {
                    scope.launch {
                        val fresh = PromptModel(
                            id = -1,
                            pos = prompts.size,
                            name = "New button",
                            prompt = "",
                            requiresSelection = true,
                            autoApply = false,
                        )
                        val id = withContext(Dispatchers.IO) { db.add(fresh) }
                        prompts.add(fresh.copy(id = id))
                    }
                },
            ) {
                Text("ADD BUTTON")
            }
        }

        // The spoken instructions, the same list the long press shows, editable here because a list
        // that can only grow eventually becomes one nobody reads.
        if (spoken.isNotEmpty()) {
            Text(
                text = "REMEMBERED INSTRUCTIONS",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = "What you have said to the little man, offered on a long press so it need not " +
                    "be said again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            spoken.forEach { instruction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        scope.launch {
                            MaLivePrompts.forget(instruction)
                            spoken = MaLivePrompts.list()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Forget this instruction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                onClick = {
                    scope.launch {
                        MaLivePrompts.clear()
                        spoken = MaLivePrompts.list()
                    }
                },
            ) {
                Text("FORGET ALL")
            }
        }
    }
}
