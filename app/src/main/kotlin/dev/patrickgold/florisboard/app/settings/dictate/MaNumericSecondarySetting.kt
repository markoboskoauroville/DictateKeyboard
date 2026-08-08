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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaNumericSecondary
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * Editor for what each digit on the number row types when it is held.
 *
 * Ten fields, one per key, laid out in the order the keys are printed so the screen reads as the row
 * it edits. Every edit is persisted immediately: there is no save button, because a settings screen
 * that can be left in an unsaved state is a settings screen that loses work.
 *
 * A field may be emptied, which leaves that key with nothing behind it rather than falling back to
 * some default. Choosing nothing is a choice.
 */
@Composable
fun MaNumericSecondarySetting() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val stored by prefs.dictate.maNumericSecondary.collectAsState()

    // Seeded from the preference and re-seeded whenever it changes underneath, so a reset elsewhere
    // is reflected here rather than being overwritten by a stale field.
    var slots by remember(stored) { mutableStateOf(MaNumericSecondary.parse(stored)) }

    fun write(index: Int, value: String) {
        val next = slots.toMutableList()
        next[index] = MaNumericSecondary.sanitize(value)
        slots = next
        scope.launch { prefs.dictate.maNumericSecondary.set(MaNumericSecondary.serialize(next)) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Number row long press",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "What each digit types when you hold it. Leave a field empty for a key with " +
                "nothing behind it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        MaNumericSecondary.LABELS.forEachIndexed { index, digit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The digit drawn as the key it is, so the eye matches a row on screen to a row
                // under the thumb without reading anything.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = slots.getOrElse(index) { "" },
                    onValueChange = { write(index, it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    slots = MaNumericSecondary.parse(MaNumericSecondary.DEFAULT)
                    scope.launch { prefs.dictate.maNumericSecondary.set(MaNumericSecondary.DEFAULT) }
                },
            ) {
                Text(text = "Underscore on all ten")
            }
        }
    }
}
