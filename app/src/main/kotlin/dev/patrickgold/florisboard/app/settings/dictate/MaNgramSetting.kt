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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.nlp.MaNgram
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * The switch for personal word prediction, and the way to make it forget.
 *
 * The count of known words is shown because this feature is worthless for its first few thousand
 * words and there is otherwise no way to tell whether it is working or simply still learning. A
 * number that climbs is the difference between "not working" and "not ready".
 */
@Composable
fun MaNgramSetting() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val enabled by prefs.dictate.maNgramEnabled.collectAsState()
    var forgotten by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Learn my words",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Suggests from what you have written on this phone, including names and " +
                        "words no dictionary has. Stays on the device and is never sent anywhere. " +
                        "Nothing is learned in incognito mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { next ->
                    scope.launch { prefs.dictate.maNgramEnabled.set(next) }
                },
            )
        }

        val known = if (forgotten) 0 else MaNgram.model.vocabularySize
        val written = if (forgotten) 0L else MaNgram.model.totalWords
        Text(
            text = "$known words known, from $written written. " +
                "It stays quiet until it has read a few hundred.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = {
                    MaNgram.forgetEverything()
                    forgotten = true
                },
            ) {
                Text(text = "Forget everything learned")
            }
        }
    }
}
