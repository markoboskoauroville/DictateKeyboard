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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private val MaQuickKeyShape = RoundedCornerShape(12.dp)
private val MaQuickMarginH = 3.dp
private val MaQuickMarginV = 4.dp

private fun maQuickAttributes(code: Int) = mapOf(
    FlorisImeUi.Attr.Code to code,
    FlorisImeUi.Attr.Mode to KeyboardMode.CHARACTERS.toString(),
    FlorisImeUi.Attr.ShiftState to InputShiftState.UNSHIFTED.toString(),
)

/**
 * The quick row of the transcribe view: one button per enabled dictation language, plus the current
 * transcription model as a dropdown.
 *
 * Before this, switching between Croatian and English meant tapping a single chip repeatedly to cycle
 * through the list, and changing model meant leaving the keyboard for the settings app entirely. Both
 * are checks worth making in the second before speaking, so both belong in the view where the speaking
 * happens. The languages come from what is enabled in settings, so this row is exactly as wide as the
 * user's own selection rather than a fixed set.
 */
@Composable
fun MaQuickRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore

    val activeCode by prefs.dictate.activeInputLanguage.collectAsState()
    val selectionRaw by prefs.dictate.inputLanguages.collectAsState()
    val selection = remember(selectionRaw) { DictateLanguages.parseSelection(selectionRaw) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Auto goes last, and says so in words. A globe means "language" in the abstract, not "work
        // it out for me", and sitting first it read as the current choice rather than the fallback.
        // The concrete languages come first because they are the deliberate act; auto is what you
        // pick when you cannot be bothered to choose, which is exactly where the eye should land
        // last.
        val ordered = remember(selection) {
            selection.filterNot { it.code == DictateLanguages.DETECT } +
                selection.filter { it.code == DictateLanguages.DETECT }
        }
        ordered.forEach { lang ->
            val isAuto = lang.code == DictateLanguages.DETECT
            MaQuickKey(
                selected = lang.code == activeCode,
                onClick = { DictateController.setLanguage(lang.code) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { fg ->
                if (isAuto) {
                    Text(
                        text = "AUTO",
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = lang.shortCode.uppercase(),
                        color = fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        // Case, forced on whatever comes back. Two buttons rather than one cycling through three
        // states, so the current one is visible instead of having to be remembered, and pressing the
        // active one turns it off again.
        val textCase by prefs.dictate.maTextCase.collectAsState()
        val caseScope = rememberCoroutineScope()
        MaQuickKey(
            selected = textCase == "lower",
            onClick = {
                caseScope.launch {
                    prefs.dictate.maTextCase.set(if (textCase == "lower") "none" else "lower")
                }
            },
            modifier = Modifier.weight(0.8f).fillMaxHeight(),
        ) { fg ->
            Text(text = "ab", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        MaQuickKey(
            selected = textCase == "upper",
            onClick = {
                caseScope.launch {
                    prefs.dictate.maTextCase.set(if (textCase == "upper") "none" else "upper")
                }
            },
            modifier = Modifier.weight(0.8f).fillMaxHeight(),
        ) { fg ->
            Text(text = "AB", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        MaModelKey(modifier = Modifier.weight(1.8f).fillMaxHeight())
    }
}

/**
 * The transcription model, shown as a button that opens the models already known for the active
 * provider. The list is whatever the last successful model fetch cached in the keyring, so nothing
 * here goes to the network: opening the keyboard must never wait on an HTTP call.
 */
@Composable
private fun MaModelKey(modifier: Modifier) {
    val prefs by FlorisPreferenceStore
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val activeProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    val account = accounts.accounts[activeProviderId]
    val preset = remember(activeProviderId) { ProviderRegistry.byId(activeProviderId) }
    val current = account?.transcriptionModel?.takeIf { it.isNotBlank() }
        ?: preset?.defaultTranscriptionModel.orEmpty()

    // Dedicated speech-to-text models first, then anything else the catalog reported, then whatever
    // the provider preset curates. Duplicates dropped, order preserved.
    val models = remember(account, preset) {
        buildList {
            account?.cachedTranscriptionModels?.let { addAll(it) }
            account?.cachedAudioModels?.let { addAll(it) }
            preset?.defaultTranscriptionModel?.let { if (it.isNotBlank()) add(it) }
            if (current.isNotBlank()) add(current)
        }.distinct()
    }

    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        MaQuickKey(
            selected = false,
            onClick = { if (models.size > 1) menuOpen = true },
            modifier = Modifier.fillMaxSize(),
        ) { fg ->
            Text(
                text = if (current.isBlank()) "model" else current,
                color = fg,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        menuOpen = false
                        val existing = accounts.accounts[activeProviderId]
                        if (existing != null) {
                            val updated = accounts.put(existing.copy(transcriptionModel = model))
                            scope.launch { prefs.dictate.providerAccounts.set(updated) }
                        }
                    },
                )
            }
        }
    }
}

/** One key of the quick row, themed like every other key so it follows the active stylesheet. */
@Composable
private fun MaQuickKey(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (Color) -> Unit,
) {
    // The enter key's styling is what every theme uses for "this one is active", so a selected
    // language borrows it rather than inventing a colour that no stylesheet knows about.
    val code = if (selected) KeyCode.ENTER else KeyCode.NOOP
    val style = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, maQuickAttributes(code))
    val background = style.background(default = Color.White.copy(alpha = 0.08f))
    val foreground = style.foreground(default = Color.White)
    Box(
        modifier = modifier
            .padding(horizontal = MaQuickMarginH, vertical = MaQuickMarginV)
            .clip(MaQuickKeyShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(foreground)
    }
}
