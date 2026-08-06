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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        // Removed rather than hidden: Emojis & GIFs, Addons & Extensions, and Other. They belong to
        // the general-purpose keyboard this was forked from, not to a voice-typing tool.
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
        // Dictate as a menu is gone. It was a lobby: a screen whose only job was to hold five other
        // screens, so everything worth reaching sat one tap further away than it needed to. Its
        // children are here now, and the two that only duplicated the key manager, provider roles
        // and model choice, are not, because that is where those already live.
        Preference(
            icon = Icons.Default.RecordVoiceOver,
            title = "Little man",
            summary = "Speak an instruction; the AI rewrites what is in the field",
            onClick = { navController.navigate(Routes.Settings.DictatePrompts()) },
        )
        Preference(
            icon = Icons.Default.Mic,
            title = stringRes(R.string.dictate__recording_group),
            onClick = { navController.navigate(Routes.Settings.DictateRecording) },
        )
        Preference(
            icon = Icons.Outlined.Keyboard,
            title = stringRes(R.string.dictate__output_group),
            onClick = { navController.navigate(Routes.Settings.DictateOutput) },
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
            icon = Icons.Default.Translate,
            title = stringRes(R.string.dictate__languages_title),
            onClick = { navController.navigate(Routes.Settings.DictateLanguages) },
        )
        // The transcription archive already existed, three levels down under Dictate, which is why it
        // read as missing. Every finished dictation is logged and can be re-inserted or re-transcribed
        // from here, so it belongs beside dictation itself rather than buried under it.
        Preference(
            icon = Icons.Default.Schedule,
            title = stringRes(R.string.dictate__history_title),
            summary = "Every transcription, re-insert or re-transcribe",
            onClick = { navController.navigate(Routes.Settings.DictateHistory) },
        )
        Preference(
            icon = Icons.Default.Bolt,
            title = "Macro bar",
            summary = "Buttons that type text or press keys",
            onClick = { navController.navigate(Routes.Settings.DictateMacros) },
        )
        Preference(
            icon = Icons.Default.Restore,
            title = "Recovered recordings",
            summary = "Audio saved when a recording was cut short",
            onClick = { navController.navigate(Routes.Settings.DictateRecovered) },
        )
        Preference(
            icon = Icons.Default.Language,
            title = stringRes(R.string.settings__localization__title),
            onClick = { navController.navigate(Routes.Settings.Localization) },
        )
        Preference(
            icon = Icons.Outlined.Palette,
            title = stringRes(R.string.settings__theme__title),
            onClick = { navController.navigate(Routes.Settings.Theme) },
        )
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
