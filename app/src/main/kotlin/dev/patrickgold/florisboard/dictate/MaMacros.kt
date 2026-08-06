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

/**
 * The macro bar: named presets, each holding rows of buttons, each button a label and what it types.
 *
 * Stored as one string. The separators are ASCII control characters that cannot be typed on a phone,
 * so nothing needs escaping: a macro may contain commas, quotes, newlines and braces and none of it
 * is mangled on the way to disk. The nesting runs preset, row, button, field, each with its own
 * separator, so no level can ever be mistaken for another.
 */
object MaMacros {

    private const val PRESET_SEP = '\u001C'
    private const val NAME_SEP = '\u001B'
    private const val ROW_SEP = '\u001E'
    private const val BTN_SEP = '\u001F'
    private const val FIELD_SEP = '\u001D'

    /** Caps, purely so a bar cannot grow until it swallows the screen. */
    const val MAX_ROWS = 6
    const val MAX_BUTTONS_PER_ROW = 24
    const val MAX_PRESETS = 12

    /** One button: what it says, and what it types. */
    data class Macro(val label: String, val macro: String)

    /** One saved bar. */
    data class Preset(val name: String, val rows: List<List<Macro>>)

    /**
     * The starting presets. The first holds the commands the old fixed top bar had, rewritten as
     * macros, so nothing was lost when the bar became editable and the editor opens on a worked
     * example of the syntax rather than a blank screen.
     */
    val DEFAULT_PRESETS: List<Preset> = listOf(
        Preset(
            name = "Editing",
            rows = listOf(
                listOf(
                    Macro("\u21e4", "{Home}"),
                    Macro("\u2192|", "{Tab}"),
                    Macro("\u21e5", "{End}"),
                    Macro("undo", "{Ctrl+Z}"),
                    Macro("redo", "{Ctrl+Shift+Z}"),
                    Macro("all", "{Ctrl+A}"),
                    Macro("copy", "{Ctrl+C}"),
                    Macro("paste", "{Ctrl+V}"),
                    Macro("ESC", "{Esc}"),
                ),
            ),
        ),
        Preset(
            name = "Punctuation",
            rows = listOf(
                listOf(
                    Macro(",", ", "),
                    Macro(".", ". "),
                    Macro("?", "? "),
                    Macro("!", "! "),
                    Macro(":", ": "),
                    Macro("\u2026", "\u2026 "),
                    Macro("\u201e", "\u201e"),
                    Macro("\u201d", "\u201d"),
                    Macro("\u23ce", "{Enter}"),
                ),
            ),
        ),
    )

    /** A usable empty preset: one row, one blank button, so there is always something to edit. */
    fun blankPreset(): Preset = Preset("New bar", listOf(listOf(Macro("", ""))))

    fun serialize(presets: List<Preset>): String =
        presets.joinToString(PRESET_SEP.toString()) { preset ->
            val rows = preset.rows.joinToString(ROW_SEP.toString()) { row ->
                row.joinToString(BTN_SEP.toString()) { "${it.label}$FIELD_SEP${it.macro}" }
            }
            "${preset.name}$NAME_SEP$rows"
        }

    /**
     * Parses the stored string. Anything malformed is skipped rather than throwing, so a damaged
     * preference degrades to a smaller bar instead of a keyboard that refuses to draw.
     */
    fun parse(raw: String): List<Preset> {
        if (raw.isBlank()) return emptyList()
        return raw.split(PRESET_SEP).mapNotNull { chunk ->
            if (chunk.isEmpty()) return@mapNotNull null
            val idx = chunk.indexOf(NAME_SEP)
            val name = if (idx >= 0) chunk.substring(0, idx) else chunk
            val body = if (idx >= 0) chunk.substring(idx + 1) else ""
            val rows = body.split(ROW_SEP)
                .map { rowText ->
                    rowText.split(BTN_SEP).mapNotNull inner@{ btn ->
                        if (btn.isEmpty()) return@inner null
                        val f = btn.indexOf(FIELD_SEP)
                        if (f < 0) return@inner null
                        val label = btn.substring(0, f)
                        val macro = btn.substring(f + 1)
                        if (label.isBlank() && macro.isBlank()) null else Macro(label, macro)
                    }
                }
                .filter { it.isNotEmpty() }
            // A preset with no rows left is a dead end on screen: nothing to edit and nothing to add
            // a button to. It keeps one blank row so there is always somewhere to start again.
            Preset(name.ifBlank { "Bar" }, rows.ifEmpty { listOf(listOf(Macro("", ""))) })
        }
    }

    fun defaultSerialized(): String = serialize(DEFAULT_PRESETS)
}
