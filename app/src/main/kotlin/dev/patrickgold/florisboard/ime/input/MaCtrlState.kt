/*
 * Copyright (C) 2026 Marko Boško, Mantra Productions
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

package dev.patrickgold.florisboard.ime.input

/**
 * The three states of the Ctrl key, mirroring what [InputShiftState] does for shift.
 *
 * A finger cannot hold a key down on a touch screen and press another one, so a modifier here is
 * either off, armed for exactly the next key, or locked until it is pressed again. Locked is what
 * makes a run of shortcuts possible without pressing Ctrl before each of them.
 *
 * [toString] returns the lowercase name so it can be matched from a stylesheet, the same way
 * shiftstate already is.
 */
enum class MaCtrlState(val value: Int) {
    /** Ctrl is off, keys type what they say. */
    OFF(0),
    /** Ctrl applies to exactly one key and then turns itself off. */
    ARMED(1),
    /** Ctrl stays on until pressed again. */
    LOCKED(2);

    companion object {
        fun fromInt(int: Int) = entries.firstOrNull { it.value == int } ?: OFF
    }

    override fun toString() = name.lowercase()

    fun toInt() = value
}
