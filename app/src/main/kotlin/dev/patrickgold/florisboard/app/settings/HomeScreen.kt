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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import java.text.NumberFormat
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    actions {
        FlorisIconButton(
            onClick = { navController.navigate(Routes.Settings.Search) },
            icon = Icons.Default.Search,
        )
    }

    content {
        val isCollapsed by prefs.internal.homeIsBetaToolboxCollapsed.collectAsState()

        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_enabled),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_selected),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        }

        // No usage banner. Counting dictations, words and "time saved" meant a stats table being
        // written on every single transcription for a number nobody acts on. The whole of it is
        // gone, screen, storage and the counters that fed it.

        // Reorganised around how this app is actually used. Keys first, because nothing works
        // without one and everything else is decoration until one is green. Then dictation, then the
        // things that shape what the keyboard looks like and does, then About.
        //
        // Two tabs, split by where a screen came from rather than by what it does.
        //
        // Mantra holds everything built for this app. FlorisBoard holds what survived from the
        // keyboard underneath, which is the pile still worth going through: anything in there that
        // is never opened is a candidate for deletion, and the split makes that visible at a glance
        // instead of requiring the history of every screen to be remembered.
        //
        // Named FlorisBoard rather than Dictate because these particular screens are FlorisBoard's,
        // by Patrick Goldinger. Dictate, the fork this came from, contributed the dictation screens,
        // and those are on the Mantra side because they have been rebuilt almost entirely.
        var tab by rememberSaveable { mutableStateOf(0) }
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Mantra") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("FlorisBoard") },
            )
        }

        if (tab == 0) {
            PreferenceGroup(title = "Start here") {
                Preference(
                    icon = Icons.Default.Edit,
                    title = "Try the keyboard",
                    summary = "A blank page to dictate into",
                    onClick = { navController.navigate(Routes.Settings.TryIt) },
                )
                Preference(
                    icon = Icons.Default.Key,
                    title = "API keys",
                    summary = "Import, test and manage every key",
                    onClick = { navController.navigate(Routes.Settings.DictateKeys) },
                )
            }

            PreferenceGroup(title = "Dictation") {
                Preference(
                    icon = Icons.Default.Mic,
                    title = stringRes(R.string.dictate__recording_group),
                    onClick = { navController.navigate(Routes.Settings.DictateRecording) },
                )
                Preference(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Little man",
                    summary = "His buttons, their prompts, and what he remembers",
                    onClick = { navController.navigate(Routes.Settings.DictateLittleMan) },
                )
                Preference(
                    icon = Icons.Default.AutoAwesome,
                    title = stringRes(R.string.dictate__rewording_title),
                    onClick = { navController.navigate(Routes.Settings.DictateRewording) },
                )
                Preference(
                    icon = Icons.Default.Spellcheck,
                    title = stringRes(R.string.dictate__mappings_title),
                    onClick = { navController.navigate(Routes.Settings.DictateMappings) },
                )
                Preference(
                    icon = Icons.Default.Keyboard,
                    title = stringRes(R.string.dictate__output_group),
                    onClick = { navController.navigate(Routes.Settings.DictateOutput) },
                )
            }

            PreferenceGroup(title = "Saved") {
                Preference(
                    icon = Icons.Default.History,
                    title = stringRes(R.string.dictate__history_title),
                    summary = "Every transcription, with its audio",
                    onClick = { navController.navigate(Routes.Settings.DictateHistory) },
                )
                Preference(
                    icon = Icons.Default.History,
                    title = "Recovered recordings",
                    summary = "Audio saved when a recording was cut short",
                    onClick = { navController.navigate(Routes.Settings.DictateRecovered) },
                )
            }

            PreferenceGroup(title = "Keyboard extras") {
                // db first in this group, and with the longest summary of anything here, because it
                // is the one screen that changes what the keyboard looks like and the one panel that
                // has no button of its own left on the keyboard.
                Preference(
                    icon = Icons.Default.Dashboard,
                    title = "db",
                    summary = "The dashboard: what is on it, and how to open it",
                    onClick = { navController.navigate(Routes.Settings.DbEditor) },
                )
                Preference(
                    icon = Icons.Default.Bolt,
                    title = "Macro bar",
                    summary = "Buttons that type text or press keys",
                    onClick = { navController.navigate(Routes.Settings.DictateMacros) },
                )
            }
        } else {
            PreferenceGroup(title = "Inherited from FlorisBoard") {
                // No theme entry. The scheme is baked in, so this screen could only be used to
                // leave it, and a saved theme in internal storage used to shadow the bundled one,
                // which made an edit to the stylesheet fail silently.
                Preference(
                    icon = Icons.Outlined.Keyboard,
                    title = stringRes(R.string.settings__keyboard__title),
                    onClick = { navController.navigate(Routes.Settings.Keyboard) },
                )
                Preference(
                    icon = Icons.Default.SmartButton,
                    title = stringRes(R.string.settings__smartbar__title),
                    onClick = { navController.navigate(Routes.Settings.Smartbar) },
                )
                Preference(
                    icon = Icons.Default.Gesture,
                    title = stringRes(R.string.settings__gestures__title),
                    onClick = { navController.navigate(Routes.Settings.Gestures) },
                )
                Preference(
                    icon = Icons.Default.Spellcheck,
                    title = stringRes(R.string.settings__typing__title),
                    onClick = { navController.navigate(Routes.Settings.Typing) },
                )
                Preference(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    title = stringRes(R.string.settings__clipboard__title),
                    onClick = { navController.navigate(Routes.Settings.Clipboard) },
                )
                // No language entry. The app is Croatian and English, both installed on first run,
                // and the switch between them is a badge on the keyboard and the volume down key.
                // A screen offering a choice that has already been made can only be used to get it
                // wrong, which is how an install ended up with no subtypes at all.
            }
        }

        Preference(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__title),
            onClick = { navController.navigate(Routes.Settings.About) },
        )
    }
}

/** Compact duration for the home stats card / milestone text: `3h`, `3h 12m`, `12m`, `45s`. */
private fun homeDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}
