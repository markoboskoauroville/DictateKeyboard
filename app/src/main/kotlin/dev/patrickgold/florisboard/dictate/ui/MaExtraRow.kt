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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.jetpref.datastore.model.collectAsState

/**
 * A row of digits, or of Croatian diacritics, above the keyboard.
 *
 * Both are things constantly reached for here and both are awkward to get at: digits mean switching
 * to the symbol layer and back, and č ć ž š đ mean holding a key and waiting for a popup, mid-word.
 * A row removes the wait for whichever of the two is wanted at the time.
 *
 * One row rather than two, and a swap rather than both at once, because keyboard height is the
 * scarcest thing on a phone screen and nobody needs digits and diacritics in the same sentence often
 * enough to pay a permanent row for each.
 */
@Composable
fun MaExtraRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val enabled by prefs.dictate.maExtraRow.collectAsState()
    val mode by prefs.dictate.maExtraRowMode.collectAsState()
    if (!enabled) return

    // Both uppercase and lowercase forms are reachable: the shift state of the keyboard decides,
    // exactly as it would for a letter typed normally, so nothing here needs its own shift handling.
    val keys = if (mode == "diacritics") {
        listOf("č", "ć", "ž", "š", "đ", "Č", "Ć", "Ž", "Š", "Đ")
    } else {
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { ch ->
            MaFlatKey(
                onFire = {
                    // Sent as the character's own code point, so autocapitalisation, the composing
                    // buffer and undo all behave exactly as they do for a key on the main layout.
                    keyboardManager.inputEventDispatcher.sendDownUp(
                        TextKeyData(code = ch.codePointAt(0), label = ch),
                    )
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { fg ->
                Text(
                    text = ch,
                    color = fg,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
