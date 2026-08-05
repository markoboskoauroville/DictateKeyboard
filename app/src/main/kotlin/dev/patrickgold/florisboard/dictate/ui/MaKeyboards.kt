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

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService

/**
 * The other keyboards installed on this phone, so one tap reaches any of them.
 *
 * Android's own answer is a modal picker: tap, wait for a dialog, read a list, tap again, and the
 * field you were typing in has lost focus by the time it closes. For someone who moves between a
 * voice keyboard and a typing keyboard constantly that is three interactions too many, every time.
 *
 * These are the enabled input methods in the order the system reports them, numbered so the
 * positions stay put and become muscle memory. This app is excluded, since a button that switches to
 * the keyboard already on screen does nothing.
 *
 * Switching itself is the awkward part. [InputMethodManager.setInputMethod] needs the IME's window
 * token, which only the running service has, so the call is made from there; there is no way to do
 * it from a composable alone.
 */
object MaKeyboards {

    /** One installed keyboard: what to show on the button, and what to switch to. */
    data class Entry(val id: String, val label: String)

    /** How many fit on a row before the labels stop being readable. */
    const val MAX_SHOWN = 4

    /**
     * Enabled input methods other than this one, in system order.
     *
     * Read fresh each time rather than cached: keyboards are enabled and disabled in system settings
     * while this app is not running, and a stale list would send a tap to a keyboard that is no
     * longer there.
     */
    fun list(context: Context): List<Entry> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptyList()
        val enabled: List<InputMethodInfo> = runCatching { imm.enabledInputMethodList }
            .getOrDefault(emptyList())
        return enabled
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .map { info ->
                val label = runCatching {
                    info.loadLabel(context.packageManager).toString()
                }.getOrDefault(info.packageName)
                Entry(id = info.id, label = label)
            }
    }

    /**
     * Switches to [entry]. Returns false when the switch could not be made, in which case the caller
     * should fall back to the system picker rather than leaving the tap doing nothing at all.
     */
    fun switchTo(entry: Entry): Boolean = FlorisImeService.switchToInputMethod(entry.id)

    /** A short label for a button: the first word, capped, so "Gboard" and "SwiftKey" both fit. */
    fun shortLabel(entry: Entry): String {
        val first = entry.label.trim().substringBefore(' ')
        return if (first.length > 8) first.take(8) else first
    }
}
