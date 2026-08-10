/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictatePromptsLayout
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the suggestion strip has anything to say. **The one place that decides**, and it has to
 * stay the one place.
 *
 * Marko's ask: the strip should appear when there is a suggestion or a recording, and otherwise not
 * exist. It was holding a full row of screen for a language badge and a stretch of nothing, on a
 * phone, above a keyboard that is already the bottom half of the display.
 *
 * **Why this is a separate function rather than a boolean inside [Smartbar].** Two pieces of code
 * have to agree about this row: the one that draws it and the one that reserves its height in
 * `FlorisImeSizing.smartbarRowCountAsState`. This project has already paid for them disagreeing
 * once, on the keyboard fold at build 138: the keys stopped being drawn while the height arithmetic
 * went on counting four rows, so the keys vanished and the space stayed. The fix then was to make
 * one thing decide, and that is what this is. Anything that changes when the strip appears changes
 * it **here**, and both callers follow.
 *
 * For the same reason there is **no animation** on this row. A 200 ms fade against a height that
 * changes in one frame is the same disagreement in slow motion, visible as a band of nothing while
 * the fade finishes. It appears and disappears with its height, in the same frame.
 *
 * The layouts that carry buttons rather than suggestions are always shown: in those the row is a
 * toolbar, and a toolbar that comes and goes is not a toolbar. Only the suggestion-carrying layouts
 * become dynamic, which is the pair this app actually uses.
 */
@Composable
fun maSmartbarHasContent(): Boolean {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current

    // Every one of these is read unconditionally, on every call, before anything is decided. An
    // early return above a composable call changes how many slots the composition has and Compose
    // will either crash or silently lose state. Cheap: they are all cached flows.
    val smartbarLayout by prefs.smartbar.layout.collectAsState()

    val nlpManager by context.nlpManager()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()
    val inlineSuggestions by NlpInlineAutofill.suggestions.collectAsState()

    // Anything other than Idle: recording, transcribing, rewording, an error worth reading, or an
    // interrupted recording waiting to be sent. Every one of those is the strip saying something,
    // and an error that vanishes with the row it is written on is an error nobody reads.
    val dictateState by DictateController.state.collectAsState()

    // The contextual prompt strip replaces the candidates while text is selected, so it counts as
    // content too. Derived as a distinct boolean so this does not recompose on every keystroke,
    // only when the selection actually flips, exactly as the Smartbar itself does it.
    val rewordingEnabled by prefs.dictate.rewordingEnabled.collectAsState()
    val promptsLayout by prefs.dictate.promptsLayout.collectAsState()
    val prompts by DictateController.prompts.collectAsState()
    val editorInstance by context.editorInstance()
    val hasSelection by remember(editorInstance) {
        editorInstance.activeContentFlow
            .map { it.selection.isSelectionMode && it.selectedText.isNotBlank() }
            .distinctUntilChanged()
    }.collectAsState(initial = false)

    val alwaysOn = smartbarLayout == SmartbarLayout.ACTIONS_ONLY ||
        smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED

    val promptStripShowing = rewordingEnabled &&
        promptsLayout == DictatePromptsLayout.PANEL &&
        hasSelection &&
        prompts.isNotEmpty()

    return alwaysOn ||
        candidates.isNotEmpty() ||
        inlineSuggestions.isNotEmpty() ||
        dictateState !is DictateController.UiState.Idle ||
        promptStripShowing
}
