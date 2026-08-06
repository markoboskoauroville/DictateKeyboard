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

package dev.patrickgold.florisboard.app.settings.gestures

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun GesturesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__gestures__title)
    previewFieldVisible = true

    content {
        PreferenceGroup(title = stringRes(R.string.pref__gestures__general_title)) {
            ListPreference(
                prefs.gestures.swipeUp,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_up__label"),
                title = stringRes(R.string.pref__gestures__swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeDown,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_down__label"),
                title = stringRes(R.string.pref__gestures__swipe_down__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeLeft,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_left__label"),
                title = stringRes(R.string.pref__gestures__swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeRight,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_right__label"),
                title = stringRes(R.string.pref__gestures__swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__gestures__space_bar_title)) {
            ListPreference(
                prefs.gestures.spaceBarSwipeUp,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__space_bar_swipe_up__label"),
                title = stringRes(R.string.pref__gestures__space_bar_swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeLeft,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__space_bar_swipe_left__label"),
                title = stringRes(R.string.pref__gestures__space_bar_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeRight,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__space_bar_swipe_right__label"),
                title = stringRes(R.string.pref__gestures__space_bar_swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarLongPress,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__space_bar_long_press__label"),
                title = stringRes(R.string.pref__gestures__space_bar_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__gestures__other_title)) {
            ListPreference(
                prefs.gestures.deleteKeySwipeLeft,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__delete_key_swipe_left__label"),
                title = stringRes(R.string.pref__gestures__delete_key_swipe_left__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
            )
            ListPreference(
                prefs.gestures.deleteKeyLongPress,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__delete_key_long_press__label"),
                title = stringRes(R.string.pref__gestures__delete_key_long_press__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
            )
            // Mantra Voice Type: the swipe-to-symbol switch, placed directly above the two
            // thresholds that govern how far and how fast a swipe must be, since turning it on
            // and then tuning those two is one continuous task rather than three separate ones.
            // Mantra Voice Type: the cursor row. Kept beside the space-bar cursor gesture below,
            // because the two are the same job solved twice and it should be obvious that turning
            // one on does not turn the other off.
            SwitchPreference(
                prefs.dictate.maCursorRow,
                title = "Command and arrow bars",
                summary = "A flat command bar under the Smartbar and an arrow strip along the " +
                    "bottom, in both the keyboard and the transcribe view. Hold an arrow to " +
                    "repeat it.",
            )
            SwitchPreference(
                prefs.gestures.maSwipeToSymbol,
                title = "Swipe to symbol",
                summary = "Swipe from a key toward one of its printed symbols to type it, " +
                    "instead of long pressing. Up types the hint, left, right and down type the " +
                    "first three extras. Tune the two thresholds below if a flick reads as a tap.",
            )
            DialogSliderPreference(
                prefs.gestures.swipeVelocityThreshold,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_velocity_threshold__label"),
                title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                min = 400,
                max = 4000,
                stepIncrement = 100,
            )
            DialogSliderPreference(
                prefs.gestures.swipeDistanceThreshold,
                modifier = Modifier.settingsSearchAnchor("pref__gestures__swipe_distance_threshold__label"),
                title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 12,
                max = 72,
                stepIncrement = 1,
            )
        }
    }
}
