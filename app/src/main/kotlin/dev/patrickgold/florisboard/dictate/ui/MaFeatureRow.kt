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

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.ime.keyboard.ImeUiMode
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.keyboard.KeyCode
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R
import org.florisboard.lib.android.showShortToastSync

/**
 * The feature row: one row, ten keys, everything this app can do reachable from the dictation view
 * without going to settings first.
 *
 * The rule it exists to satisfy is Marko's, and it is a good one: a feature you have to enable in a
 * settings app before you can see it is a feature most people never find. The dictation view is not
 * the full keyboard, so it has the vertical room the typing view does not, and this spends some of
 * that room on reach.
 *
 * Three things borrowed from keyboards that have already been judged by very large numbers of
 * people:
 *
 * - **Gboard put its shortcuts in key-shaped buttons and then deleted its overflow menu**, freeing
 *   the slot it occupied. A shortcut behind a menu is a shortcut to a menu. Every key here acts on
 *   the first tap; none of them opens a list of more keys except the dashboard, which is a list of
 *   switches rather than of actions.
 * - **HeliBoard distributes its toolbar keys evenly** rather than packing them from one edge. Equal
 *   weights below, so the row reads as a row and the thumb learns positions rather than icons.
 * - **Ten keys is exactly what the letter row already is.** The usual objection to ten controls in a
 *   phone-width row is that each falls under the 48dp touch minimum, and at a typical 360dp width
 *   these are about 36dp. But the QWERTY row above is ten keys at the same width, on the same
 *   device, in the same hand, and it has been usable for as long as touch keyboards have existed.
 *   The precedent is not theoretical, it is one row up.
 *
 * Colour is state only, as everywhere else in this app: gold ink on the near-black key, and green
 * on the two keys that are switches so their position is readable without pressing them.
 */
@Composable
fun MaFeatureRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val longform by prefs.dictate.longformMode.collectAsState()
    val rewording by prefs.dictate.rewordingEnabled.collectAsState()

    // The one green in this app that means "on", matching the locked-modifier green on the keyboard
    // so the same colour never means two different things.
    val onGreen = Color(0xFF6FA85A)

    // The reader key is drawn faint rather than bright, so the row shows at a glance that one of its
    // ten is not live yet. Dimmed, not hidden: a control that appears later in a row people have
    // already learned costs more than a slot held open.
    val notReadyInk = Color(0x55E8B15C)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val keyMod = Modifier.weight(1f).fillMaxHeight()

        // 1. The reader. Rendered but not yet wired: the LLL view does not exist, and a key that
        //    silently does nothing is worse than one that is visibly not ready. Its slot is held
        //    here so the row does not get rearranged the day the reader lands, because a control
        //    that moves after people have learned where it is costs more than it gains.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringRes(R.string.ma__feature_lll),
            modifier = keyMod,
            tint = notReadyInk,
        ) {
            context.showShortToastSync(R.string.ma__feature_lll_not_ready)
        }

        // 2. Transcribe an audio file. Until now this lived only on a long press of the microphone,
        //    which is not a place anybody looks. The single most hidden feature in the app.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Outlined.AudioFile,
            contentDescription = stringRes(R.string.ma__feature_file),
            modifier = keyMod,
        ) {
            DictateController.startFileTranscription(context)
        }

        // 3. Longform, cycling off → manual → auto. It decides what happens to speech longer than a
        //    breath, which is exactly the thing worth knowing before speaking rather than after.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Default.Timer,
            contentDescription = stringRes(R.string.ma__feature_longform),
            modifier = keyMod,
            tint = if (longform.isEnabled) onGreen else null,
        ) {
            scope.launch {
                prefs.dictate.longformMode.set(
                    when (longform) {
                        DictateLongformMode.OFF -> DictateLongformMode.MANUAL
                        DictateLongformMode.MANUAL -> DictateLongformMode.AUTO
                        DictateLongformMode.AUTO -> DictateLongformMode.OFF
                    },
                )
            }
        }

        // 4. Rewording. The master switch for the prompt strip and everything an LLM does to a
        //    transcript afterwards. Green when the text will be touched, dark when it will not,
        //    which is the difference between what was said and what was written.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Outlined.AutoFixHigh,
            contentDescription = stringRes(R.string.ma__feature_rewording),
            modifier = keyMod,
            tint = if (rewording) onGreen else null,
        ) {
            scope.launch { prefs.dictate.rewordingEnabled.set(!rewording) }
        }

        // 5. Put the last dictation back. The recovery when a send lands in the wrong field or gets
        //    swallowed, and the reason it belongs next to the switches rather than buried.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Default.Replay,
            contentDescription = stringRes(R.string.ma__feature_reinsert),
            modifier = keyMod,
        ) {
            DictateController.reinsertLastDictation(context)
        }

        // 6. Past dictations. Not the clipboard, which is a different past and already has its own
        //    key in the edit row above.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Default.History,
            contentDescription = stringRes(R.string.ma__feature_history),
            modifier = keyMod,
        ) {
            keyboardManager.activeState.imeUiMode = ImeUiMode.HISTORY
        }

        // 7. The model. The quick row shows it too, but the quick row can be hidden, and which model
        //    is about to hear you is not something that should ever be unavailable.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Outlined.Psychology,
            contentDescription = stringRes(R.string.ma__feature_model),
            modifier = keyMod,
        ) {
            FlorisImeService.launchSettings("settings/dictate/providers")
        }

        // 8. db, the dashboard of show and hide switches. The one key here that opens a list, and it
        //    earns that because its contents are settings rather than actions.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Default.Dashboard,
            contentDescription = stringRes(R.string.ma__feature_db),
            modifier = keyMod,
        ) {
            FlorisImeService.launchSettings("settings/dictate/layout")
        }

        // 9. API keys. When a key expires the whole app stops working, and the fix currently lives in
        //    another application entirely. Nothing else in the row can fail this completely.
        ThemedIconKey(
            code = KeyCode.NOOP,
            icon = Icons.Default.VpnKey,
            contentDescription = stringRes(R.string.ma__feature_keys),
            modifier = keyMod,
        ) {
            FlorisImeService.launchSettings("settings/dictate/keys")
        }

        // 10. Settings, last, on the far edge. The way out to everything not worth a key.
        ThemedIconKey(
            code = KeyCode.SETTINGS,
            icon = Icons.Default.Settings,
            contentDescription = stringRes(R.string.ma__feature_settings),
            modifier = keyMod,
        ) {
            FlorisImeService.launchSettings("settings/dictate")
        }
    }
}
