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

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaRecordingBuffer
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recordings that were cut short, kept so they can still be transcribed.
 *
 * A keyboard has no window of its own, so the moment another app takes the screen the microphone is
 * taken with it. The audio is finalised rather than discarded, but only one such file used to be
 * kept, at a fixed path, and the next interruption overwrote it. Ten are kept now.
 *
 * Sending one back for transcription goes through the same cache handoff the file picker uses, which
 * exists precisely because this process gets killed while another app is in front: a file on disk is
 * the only signal that reliably survives that.
 */
@Composable
fun DictateRecoveredScreen() = FlorisScreen {
    title = "Recovered recordings"
    iconSpaceReserved = false

    content {
        val context = LocalContext.current
        // Bumped after any change so the list re-reads from disk rather than trusting a stale copy.
        var tick by remember { mutableStateOf(0) }
        var note by remember { mutableStateOf("") }
        val entries = remember(tick) { MaRecordingBuffer.list(context) }
        val stamps = remember { SimpleDateFormat("d.M. HH:mm", Locale.getDefault()) }

        Text(
            text = "The last ${MaRecordingBuffer.CAPACITY} recordings that were interrupted, newest " +
                "first. Switching to another app while speaking ends the recording, and this is " +
                "where the audio lands instead of being lost.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )

        if (entries.isEmpty()) {
            Text(
                text = "Nothing here. Recordings only appear once one has actually been cut short.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        entries.forEach { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stamps.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val size = entry.file.length() / 1024
                        Text(
                            text = "${entry.seconds}s, $size kB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        val ok = runCatching {
                            val dir = DictateController.pendingTranscriptionDir(context).apply {
                                if (!exists()) mkdirs()
                            }
                            dir.listFiles()?.forEach { it.delete() }
                            entry.file.copyTo(dir.resolve(entry.file.name), overwrite = true)
                        }.isSuccess
                        note = if (ok) {
                            "Queued. Open the keyboard on any text field and it transcribes there."
                        } else {
                            "That recording could not be queued."
                        }
                    }) {
                        Text("Transcribe")
                    }
                    IconButton(onClick = {
                        MaRecordingBuffer.delete(entry)
                        tick += 1
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete this recording",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (note.isNotEmpty()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    MaRecordingBuffer.clear(context)
                    tick += 1
                    note = "Cleared."
                }) {
                    Text("Delete all")
                }
            }
        }
    }
}
