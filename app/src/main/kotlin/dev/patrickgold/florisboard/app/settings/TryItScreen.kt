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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import org.florisboard.lib.compose.stringRes

/**
 * The first thing after setup: somewhere to actually try the keyboard.
 *
 * What used to happen was that finishing setup dropped straight into the settings list, which is a
 * menu of things to configure at the precise moment somebody wants to find out whether the thing
 * works at all. A page of options answers a question nobody has yet asked.
 *
 * So this is one text field and nothing else. The keyboard opens on it by itself, speaking into it
 * shows the whole loop end to end, and the X in the corner leaves for the settings list once that
 * has been seen. Reachable again afterwards from Settings, so it is a starting point rather than a
 * gate.
 */
@Composable
fun TryItScreen() {
    val navController = LocalNavController.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // Open the keyboard without being asked to. The entire point of the screen is the keyboard, and
    // making someone tap a field first to reach it is a step that earns nothing.
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                text = stringRes(R.string.app_name_full),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 28.dp, end = 44.dp),
            )
            SelectionContainer {
                Text(
                    text = "Try it here. Tap the microphone and speak; the words land in this box. " +
                        "Nothing typed here is kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, end = 44.dp),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text("Speak or type\u2026") },
                minLines = 6,
            )
        }
        // Top-right X, the way out. Deliberately not a "Done" button at the bottom, where the
        // keyboard would be covering it.
        IconButton(
            onClick = {
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Settings.TryIt) { inclusive = true }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
            )
        }
    }
}
