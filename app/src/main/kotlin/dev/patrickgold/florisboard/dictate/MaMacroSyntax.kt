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

package dev.patrickgold.florisboard.dictate

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * AutoHotkey-style macro text: plain text types itself, anything in braces is a real key press.
 *
 * `Dear {F5} team{Enter}{Ctrl+A}` types "Dear ", presses F5, types " team", presses Enter, then
 * Ctrl+A. The brace form is borrowed deliberately: it is the syntax already in Marko's fingers from
 * AutoHotkey, so there is nothing new to learn.
 *
 * A word on what a keyboard can and cannot do here. Plain text is committed straight into the field
 * and always works. A key press is a real Android KeyEvent with its meta state set, which ordinary
 * text fields do honour, so Ctrl+A, Ctrl+C, Ctrl+V and Ctrl+Z behave as expected in most apps. Keys
 * that no app is listening for, F13 in a chat box for instance, do nothing at all: the event is
 * delivered correctly and simply ignored. That is the app's decision, not a failure here, and it is
 * worth knowing before wiring a macro to an exotic key and wondering why nothing happens.
 *
 * Literal braces are written `{{` and `}}`.
 */
object MaMacroSyntax {

    /** One piece of a macro: either text to type, or a key to press. */
    sealed interface Step {
        data class Text(val value: String) : Step
        data class Key(val keyCode: Int, val meta: Int, val token: String) : Step
        /** A brace token that matched nothing known; kept so the editor can point at it. */
        data class Unknown(val token: String) : Step
    }

    /** Modifier names accepted inside a combo, mapped to their KeyEvent meta bits. */
    private val MODIFIERS = mapOf(
        "ctrl" to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        "control" to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        "shift" to (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON),
        "alt" to (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
        "meta" to (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
        "win" to (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
        "cmd" to (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
    )

    /** Named keys. Aliases are generous on purpose; nobody should have to guess the spelling. */
    private val NAMED_KEYS: Map<String, Int> = buildMap {
        put("enter", KeyEvent.KEYCODE_ENTER)
        put("return", KeyEvent.KEYCODE_ENTER)
        put("tab", KeyEvent.KEYCODE_TAB)
        put("space", KeyEvent.KEYCODE_SPACE)
        put("esc", KeyEvent.KEYCODE_ESCAPE)
        put("escape", KeyEvent.KEYCODE_ESCAPE)
        put("backspace", KeyEvent.KEYCODE_DEL)
        put("bs", KeyEvent.KEYCODE_DEL)
        put("delete", KeyEvent.KEYCODE_FORWARD_DEL)
        put("del", KeyEvent.KEYCODE_FORWARD_DEL)
        put("up", KeyEvent.KEYCODE_DPAD_UP)
        put("down", KeyEvent.KEYCODE_DPAD_DOWN)
        put("left", KeyEvent.KEYCODE_DPAD_LEFT)
        put("right", KeyEvent.KEYCODE_DPAD_RIGHT)
        put("home", KeyEvent.KEYCODE_MOVE_HOME)
        put("end", KeyEvent.KEYCODE_MOVE_END)
        put("pgup", KeyEvent.KEYCODE_PAGE_UP)
        put("pageup", KeyEvent.KEYCODE_PAGE_UP)
        put("pgdn", KeyEvent.KEYCODE_PAGE_DOWN)
        put("pagedown", KeyEvent.KEYCODE_PAGE_DOWN)
        put("insert", KeyEvent.KEYCODE_INSERT)
        put("ins", KeyEvent.KEYCODE_INSERT)
        put("capslock", KeyEvent.KEYCODE_CAPS_LOCK)
        put("menu", KeyEvent.KEYCODE_MENU)
        put("back", KeyEvent.KEYCODE_BACK)
        put("search", KeyEvent.KEYCODE_SEARCH)
        // F1 to F12.
        for (i in 1..12) {
            put("f$i", KeyEvent.KEYCODE_F1 + (i - 1))
        }
        // Numpad 0 to 9, then its operators.
        for (i in 0..9) {
            put("numpad$i", KeyEvent.KEYCODE_NUMPAD_0 + i)
            put("np$i", KeyEvent.KEYCODE_NUMPAD_0 + i)
        }
        put("numpadadd", KeyEvent.KEYCODE_NUMPAD_ADD)
        put("numpadsub", KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
        put("numpadmult", KeyEvent.KEYCODE_NUMPAD_MULTIPLY)
        put("numpaddiv", KeyEvent.KEYCODE_NUMPAD_DIVIDE)
        put("numpaddot", KeyEvent.KEYCODE_NUMPAD_DOT)
        put("numpadenter", KeyEvent.KEYCODE_NUMPAD_ENTER)
        // Letters and digits, so {Ctrl+C} and {Alt+1} resolve without special cases.
        for (c in 'a'..'z') {
            put(c.toString(), KeyEvent.KEYCODE_A + (c - 'a'))
        }
        for (d in '0'..'9') {
            put(d.toString(), KeyEvent.KEYCODE_0 + (d - '0'))
        }
    }

    /** Every token the editor's help list offers, in the order it shows them. */
    val HELP_TOKENS: List<Pair<String, String>> = listOf(
        "{Enter}" to "Enter",
        "{Tab}" to "Tab",
        "{Esc}" to "Escape",
        "{Backspace}" to "Delete backwards",
        "{Del}" to "Delete forwards",
        "{Up} {Down} {Left} {Right}" to "Move the cursor",
        "{Home} {End}" to "Start and end of the line",
        "{F1} to {F12}" to "Function keys",
        "{Numpad0} to {Numpad9}" to "Numeric keypad",
        "{Ctrl+A}" to "Select all",
        "{Ctrl+C} {Ctrl+V} {Ctrl+X}" to "Copy, paste, cut",
        "{Ctrl+Z} {Ctrl+Shift+Z}" to "Undo and redo",
        "{Alt+1}" to "Any modifier plus any key",
        "{{ and }}" to "A literal brace",
    )

    /**
     * Splits macro text into the steps to run. Never throws: an unrecognised token becomes
     * [Step.Unknown] rather than failing the whole macro, so one typo cannot silence a button.
     */
    fun parse(macro: String): List<Step> {
        val steps = mutableListOf<Step>()
        val text = StringBuilder()
        var i = 0
        fun flush() {
            if (text.isNotEmpty()) {
                steps += Step.Text(text.toString())
                text.setLength(0)
            }
        }
        while (i < macro.length) {
            val c = macro[i]
            when {
                c == '{' && i + 1 < macro.length && macro[i + 1] == '{' -> {
                    text.append('{'); i += 2
                }
                c == '}' && i + 1 < macro.length && macro[i + 1] == '}' -> {
                    text.append('}'); i += 2
                }
                c == '{' -> {
                    val close = macro.indexOf('}', i + 1)
                    if (close < 0) {
                        // No closing brace: treat the rest as literal text rather than eating it.
                        text.append(macro.substring(i)); i = macro.length
                    } else {
                        val token = macro.substring(i + 1, close)
                        flush()
                        steps += resolve(token)
                        i = close + 1
                    }
                }
                else -> {
                    text.append(c); i++
                }
            }
        }
        flush()
        return steps
    }

    /** Turns one brace token, modifiers included, into a key step. */
    private fun resolve(token: String): Step {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return Step.Unknown(token)
        // Split on + or -, so {Ctrl+Shift+Z} and {Ctrl-Z} both work.
        val parts = trimmed.split('+', '-').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Step.Unknown(token)
        var meta = 0
        for (p in parts.dropLast(1)) {
            val bits = MODIFIERS[p.lowercase()] ?: return Step.Unknown(token)
            meta = meta or bits
        }
        val keyName = parts.last().lowercase()
        val code = NAMED_KEYS[keyName] ?: return Step.Unknown(token)
        return Step.Key(code, meta, trimmed)
    }

    /**
     * Runs a macro against the live input connection.
     *
     * Text is committed directly. Keys are sent as a matched down/up pair with the meta state on
     * both events, which is what a physical keyboard produces and what apps check for.
     *
     * @return false when the connection was gone, so the caller can stay quiet rather than pretend.
     */
    fun run(macro: String, ic: InputConnection?): Boolean {
        val connection = ic ?: return false
        for (step in parse(macro)) {
            when (step) {
                is Step.Text -> connection.commitText(step.value, 1)
                is Step.Key -> {
                    val now = android.os.SystemClock.uptimeMillis()
                    connection.sendKeyEvent(
                        KeyEvent(
                            now, now, KeyEvent.ACTION_DOWN, step.keyCode, 0, step.meta,
                            KeyCharacterMapDeviceId, 0, KeyEvent.FLAG_SOFT_KEYBOARD,
                        )
                    )
                    connection.sendKeyEvent(
                        KeyEvent(
                            now, now, KeyEvent.ACTION_UP, step.keyCode, 0, step.meta,
                            KeyCharacterMapDeviceId, 0, KeyEvent.FLAG_SOFT_KEYBOARD,
                        )
                    )
                }
                is Step.Unknown -> Unit
            }
        }
        return true
    }

    /** Virtual device id for synthesised events, matching what soft keyboards normally report. */
    private val KeyCharacterMapDeviceId = KeyEvent.KEYCODE_UNKNOWN

    /** Tokens in [macro] that resolve to nothing, for the editor to warn about. */
    fun unknownTokens(macro: String): List<String> =
        parse(macro).filterIsInstance<Step.Unknown>().map { it.token }
}
