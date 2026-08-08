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

package dev.patrickgold.florisboard.ime.text.key

object KeyCode {
    object Spec {
        const val CHARACTERS_MIN = 1
        const val CHARACTERS_MAX = 65535
        val CHARACTERS = CHARACTERS_MIN..CHARACTERS_MAX

        const val INTERNAL_MIN = -9999
        const val INTERNAL_MAX = -1
        val INTERNAL = INTERNAL_MIN..INTERNAL_MAX
    }

    const val UNSPECIFIED =                    0

    const val PHONE_WAIT =                    59 // ;
    const val PHONE_PAUSE =                   44 // ,

    const val SPACE =                         32
    const val ESCAPE =                        27
    const val ENTER =                         10
    const val TAB =                            9

    const val CTRL =                          -1
    const val CTRL_LOCK =                     -2
    const val ALT =                           -3
    const val ALT_LOCK =                      -4
    const val FN =                            -5
    const val FN_LOCK =                       -6
    const val DELETE =                        -7
    const val DELETE_WORD =                   -8
    const val FORWARD_DELETE =                -9
    const val FORWARD_DELETE_WORD =          -10
    const val SHIFT =                        -11
    const val CAPS_LOCK =                    -13

    const val ARROW_LEFT =                   -21
    const val ARROW_RIGHT =                  -22
    const val ARROW_UP =                     -23
    const val ARROW_DOWN =                   -24
    const val MOVE_START_OF_PAGE =           -25
    const val MOVE_END_OF_PAGE =             -26
    const val MOVE_START_OF_LINE =           -27
    const val MOVE_END_OF_LINE =             -28

    const val CLIPBOARD_COPY =               -31
    const val CLIPBOARD_CUT =                -32
    const val CLIPBOARD_PASTE =              -33
    const val CLIPBOARD_SELECT =             -34
    const val CLIPBOARD_SELECT_ALL =         -35
    const val CLIPBOARD_CLEAR_HISTORY =      -36
    const val CLIPBOARD_CLEAR_FULL_HISTORY = -37
    const val CLIPBOARD_CLEAR_PRIMARY_CLIP = -38

    // -109 to -114 were the floating window and the one-handed layout, with its two nudge keys and
    // the split and merge pair. Removed outright: a dictation keyboard is held in one hand and
    // spoken into, and neither feature was ever reached for here. The codes are left unused rather
    // than reassigned, so an old saved arrangement naming one of them resolves to nothing at all
    // instead of quietly becoming some other key.
    const val TOGGLE_RESIZE_MODE =          -115

    const val UNDO =                        -131
    const val REDO =                        -132

    const val VIEW_CHARACTERS =             -201
    const val VIEW_SYMBOLS =                -202
    const val VIEW_SYMBOLS2 =               -203
    const val VIEW_NUMERIC =                -204
    const val VIEW_NUMERIC_ADVANCED =       -205
    const val VIEW_PHONE =                  -206
    const val VIEW_PHONE2 =                 -207

    const val IME_UI_MODE_TEXT =            -211
    const val IME_UI_MODE_MEDIA =           -212
    const val IME_UI_MODE_CLIPBOARD =       -213
    const val IME_UI_MODE_DICTATE =         -214

    // Dictate: record a spoken instruction and send it straight to the rewording model (live prompt).
    const val DICTATE_LIVE_PROMPT =         -215
    // Dictate: open the AI prompt panel (always-available list of rewording prompts).
    const val DICTATE_PROMPTS =             -216
    // Dictate: re-insert the last successful dictation (safety net after a field clear, e.g. rotation).
    const val DICTATE_REINSERT =            -217
    // Opens the GIF search panel (KLIPY).
    const val IME_UI_MODE_GIF =             -218
    // Marko: the extra row above the keyboard. One action turns it on and off, the other swaps what
    // it holds between digits and Croatian diacritics, which is the pair of things actually reached
    // for while typing here.
    const val MA_TOGGLE_EXTRA_ROW =         -219
    // Marko: select the word under the cursor. Nothing upstream does this; CLIPBOARD_SELECT is a
    // manual selection-mode toggle, which is a different thing entirely.
    const val MA_SELECT_WORD =              -229
    // Marko: move the caret a whole word at a time. Sent as ctrl+arrow, which is what a hardware
    // keyboard does and what Android's text views already understand.
    const val MA_WORD_LEFT =                -230
    // -249, not -231. -231 is IME_SHOW_UI upstream, and two names for one number is not a clash the
    // compiler can see: the `when` in KeyboardManager simply takes whichever branch is written
    // first and the other becomes unreachable. Moved rather than reordered, because reordering only
    // decides which of the two is silently broken.
    const val MA_WORD_RIGHT =               -249
    // Marko: record without leaving the typing keyboard. The Smartbar already draws a full recording
    // bar in this view, so there is somewhere for it to happen; nothing was starting it.
    // -248, not -232, which is IME_HIDE_UI upstream. This one was not theoretical: IME_HIDE_UI is
    // written first, so quick record never ran, while the label lookup is ordered the other way and
    // so read "quick record" on a key that hid the keyboard.
    const val MA_QUICK_RECORD =             -248
    // Marko: one button per row set, not one button cycling through them. Cycling means pressing
    // three times to reach the third, and no way to see which is coming next.
    const val MA_ROW_BRACKETS =             -233
    const val MA_ROW_ARROWS =               -234
    // Marko: the editing set. Select, cut, copy, paste and undo on the number row's ten keys, so a
    // block of dictated text can be selected and moved without the row ever changing size or place.
    const val MA_ROW_EDITING =              -235
    // Long press on the last key of the top row moves to the next set.
    const val MA_ROW_NEXT_SET =             -236
    // Marko: the Menu Macro dashboard. Each of these shows or hides one section of the keyboard,
    // from the same three-dots panel, so the layout can be trimmed without leaving the keyboard.
    const val MA_TOGGLE_PROMPTS =           -237
    const val MA_TOGGLE_QUICK_ROW =         -238
    const val MA_TOGGLE_CURSOR_ROW =        -239
    // Marko: the remaining two row sets get their own buttons for the same reason the first two did.
    // One button per set, never a button that cycles: cycling means pressing three times to reach the
    // third and gives no way to see which is coming next.
    const val MA_ROW_DIGITS =               -245
    const val MA_ROW_DIACRITICS =           -246
    // Marko: the copy and paste row, the one from the transcribe view, shown or hidden in either view.
    const val MA_TOGGLE_EDIT_ROW =          -247

    const val SYSTEM_INPUT_METHOD_PICKER =  -221
    const val SYSTEM_PREV_INPUT_METHOD =    -222
    const val SYSTEM_NEXT_INPUT_METHOD =    -223
    const val IME_SUBTYPE_PICKER =          -224
    const val IME_PREV_SUBTYPE =            -225
    const val IME_NEXT_SUBTYPE =            -226
    const val LANGUAGE_SWITCH =             -227
    const val SHOW_SUBTYPE_PICKER =         -228

    const val IME_SHOW_UI =                 -231
    const val IME_HIDE_UI =                 -232

    const val TOGGLE_SMARTBAR_VISIBILITY =  -241
    const val TOGGLE_ACTIONS_OVERFLOW =     -242
    const val TOGGLE_ACTIONS_EDITOR =       -243
    const val TOGGLE_INCOGNITO_MODE =       -244

    const val URI_COMPONENT_TLD =           -255

    const val SETTINGS =                    -301

    const val CURRENCY_SLOT_1 =             -801
    const val CURRENCY_SLOT_2 =             -802
    const val CURRENCY_SLOT_3 =             -803
    const val CURRENCY_SLOT_4 =             -804
    const val CURRENCY_SLOT_5 =             -805
    const val CURRENCY_SLOT_6 =             -806

    const val MULTIPLE_CODE_POINTS =        -902
    const val DRAG_MARKER =                 -991
    const val NOOP =                        -999

    const val CHAR_WIDTH_SWITCHER =        -9701
    const val CHAR_WIDTH_FULL =            -9702
    const val CHAR_WIDTH_HALF =            -9703

    const val KANA_SMALL =                 12307
    const val KANA_SWITCHER =              -9710
    const val KANA_HIRA =                  -9711
    const val KANA_KATA =                  -9712
    const val KANA_HALF_KATA =             -9713

    const val KESHIDA =                     1600
    const val HALF_SPACE =                  8204

    const val CJK_SPACE =                  12288
}
