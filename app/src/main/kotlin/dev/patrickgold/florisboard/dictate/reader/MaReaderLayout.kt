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

package dev.patrickgold.florisboard.dictate.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mantraproductions.reader.engine.MaEdgeVoice
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * LLL, the reader view: it speaks a text and lights each word as it is said.
 *
 * Look, Listen, Learn. Reading and hearing the same word at the same moment is the point of the
 * whole thing, which is why the highlight has had far more work behind it than the screen it lands
 * on: the words are placed by the speech engine, corrected against the visible characters, then
 * moved again onto the real waveform, all before a single pixel is drawn here.
 *
 * The text comes from the clipboard, because that is where a thing worth reading already is by the
 * time somebody wants it read. There is nothing to paste into and nothing to configure.
 *
 * Controls are a single row: leave, back a sentence, play or pause, forward a sentence, the voice,
 * and load whatever is on the clipboard now. One row, because the reading is what the screen is for
 * and every key here costs a line of text.
 */
@Composable
fun MaReaderLayout(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val clipboardManager by context.clipboardManager()

    val text by MaReader.text.collectAsState()
    val highlight by MaReader.highlight.collectAsState()
    val state by MaReader.state.collectAsState()

    var female by remember { mutableStateOf(true) }
    val subtypeManager by context.subtypeManager()
    val language = subtypeManager.activeSubtype.primaryLocale.language

    // Whatever is on the clipboard, loaded on first entry so the view is never a blank page waiting
    // to be told what to do. Coming back to a reading already in progress leaves it alone.
    LaunchedEffect(Unit) {
        if (text.isEmpty()) {
            loadFromClipboard(context, clipboardManager.primaryClip?.text?.toString())
        }
    }

    val style = rememberSnyggThemeQuery(FlorisImeUi.ClipboardHeaderText.elementName)
    val ink = style.foreground(default = Color(0xFFE8B15C))

    SnyggColumn(
        elementName = FlorisImeUi.ClipboardHeader.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        // The page. Everything above the control row belongs to the words.
        SnyggBox(
            elementName = FlorisImeUi.ClipboardGrid.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardUiHeight() - FlorisImeSizing.smartbarHeight),
        ) {
            val scroll = rememberScrollState()
            if (text.isEmpty()) {
                SnyggText(
                    modifier = Modifier.padding(16.dp),
                    text = stringRes(R.string.lll__empty),
                )
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    // The lit word is a solid block behind the text rather than a colour change on
                    // it. Recolouring the glyphs asks the eye to notice a hue while it is busy
                    // reading; a block behind them is seen without being looked at, which is what a
                    // reading aid has to be.
                    text = buildAnnotatedString {
                        val h = highlight
                        if (h == null || h.first < 0 || h.last + 1 > text.length) {
                            append(text)
                        } else {
                            append(text.substring(0, h.first))
                            withStyle(
                                SpanStyle(
                                    background = ink.copy(alpha = 0.28f),
                                    color = ink,
                                ),
                            ) {
                                append(text.substring(h.first, h.last + 1))
                            }
                            append(text.substring(h.last + 1))
                        }
                    },
                    color = ink.copy(alpha = 0.86f),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
            }
        }

        SnyggRow(
            elementName = FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val keyMod = Modifier.weight(1f).fillMaxHeight()
            val playing = (state as? MaReader.State.Playing)?.paused == false

            ReaderKey(Icons.Default.Close, stringRes(R.string.lll__close), keyMod) {
                MaReader.stop()
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
            ReaderKey(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                stringRes(R.string.lll__previous),
                keyMod,
            ) { MaReader.step(context, -1) }

            // Play and pause share a key, because they are one control and two would mean looking
            // for the right one while listening.
            ReaderKey(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringRes(if (playing) R.string.lll__pause else R.string.lll__play),
                keyMod,
            ) {
                if (playing) MaReader.pause() else MaReader.play(context)
            }
            ReaderKey(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                stringRes(R.string.lll__next),
                keyMod,
            ) { MaReader.step(context, 1) }

            // The voice. Four exist, the language is already decided by the keyboard, so the only
            // choice left here is which of the two, and one key is enough to make it.
            ReaderKey(Icons.Default.RecordVoiceOver, stringRes(R.string.lll__voice), keyMod) {
                female = !female
                MaReader.setVoice(MaEdgeVoice.Voices.of(language, female))
                MaReader.reload(context)
            }

            // Load whatever is on the clipboard now, for reading a second thing without leaving.
            ReaderKey(Icons.Default.ContentPaste, stringRes(R.string.lll__load), keyMod) {
                loadFromClipboard(context, clipboardManager.primaryClip?.text?.toString())
            }
        }

        // A thin line of status, and only while there is something to say. A reader that narrates
        // its own machinery is a reader nobody is reading.
        val note = when (val s = state) {
            is MaReader.State.Preparing -> stringRes(R.string.lll__preparing)
            is MaReader.State.Failed -> s.message
            else -> null
        }
        if (note != null) {
            SnyggText(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                text = note,
            )
        }
    }
}

private fun loadFromClipboard(context: Context, clip: String?) {
    val raw = clip?.trim().orEmpty()
    if (raw.isNotEmpty()) MaReader.load(context, raw)
}

@Composable
private fun ReaderKey(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    SnyggIconButton(
        elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
        onClick = onClick,
        modifier = modifier,
    ) {
        SnyggIcon(imageVector = icon, contentDescription = label)
    }
}
