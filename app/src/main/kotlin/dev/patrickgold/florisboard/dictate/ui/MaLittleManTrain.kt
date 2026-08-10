/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.MaLittleMan
import dev.patrickgold.florisboard.dictate.MaLivePrompts
import kotlinx.coroutines.launch

/**
 * The Little Man AI Assistant and his train.
 *
 * Marko's picture, and it is the right one: the assistant is the locomotive, fixed at the head of the
 * line, and every instruction ever spoken to him is a wagon behind it. The wagons scroll; the
 * locomotive does not. That is the whole layout and it explains itself the moment it is seen.
 *
 * **His bar used to hold a chip per saved prompt as well.** All of it is gone. There is one Little
 * Man, on the left, and everything else in the row is history. Two kinds of button on one strip, one
 * meaning "run this saved thing" and the other meaning "run this thing I said", was a row that had to
 * be read to be used.
 *
 * Each wagon carries the summary above and the prompt below, which is the one arrangement that works
 * at this size: the summary is what the eye lands on and the prompt underneath is what confirms it is
 * the right one. Tap runs it. **Long press opens the editor**, because a spoken instruction is a
 * dictated instruction and dictation gets one word wrong now and then, and re-speaking a whole
 * sentence to fix a word is the thing this avoids.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MaLittleManTrain(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val livePromptActive by DictateController.livePromptActive.collectAsState()
    // Re-read whenever an edit lands. The store is a preference string rather than a flow, so the
    // counter is what tells this row that something changed underneath it.
    var revision by remember { mutableStateOf(0) }
    val history = remember(revision) { MaLivePrompts.list() }
    var editing by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The locomotive. Outside the LazyRow on purpose: it must never scroll away, because it is
        // the button that starts a new instruction and the one thing on this row that is always
        // wanted. A head that can be scrolled off is a head that gets lost behind its own wagons.
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (livePromptActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .combinedClickable(
                    onClick = { DictateController.startLivePrompt(context) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = "Little Man AI Assistant",
                tint = if (livePromptActive) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }

        if (history.isEmpty()) {
            // Nothing spoken yet. One quiet line rather than an empty rail, which reads as broken.
            Text(
                text = "Hold the little man and speak an instruction",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(history, key = { it }) { prompt ->
                    MaPromptWagon(
                        prompt = prompt,
                        onClick = { DictateController.applyRememberedPrompt(context, prompt) },
                        onLongClick = { editing = prompt },
                    )
                }
            }
        }
    }

    editing?.let { original ->
        MaPromptEditor(
            original = original,
            onDismiss = { editing = null },
            onSave = { text ->
                scope.launch {
                    MaLivePrompts.replaceAll(MaLittleMan.edited(MaLivePrompts.list(), original, text))
                    revision++
                    editing = null
                }
            },
            onDelete = {
                scope.launch {
                    MaLivePrompts.forget(original)
                    revision++
                    editing = null
                }
            },
        )
    }
}

/**
 * One wagon: what was said, with its summary above it.
 *
 * A fixed width rather than one that fits the text. Wagons of varying width make the row jump about
 * as the list changes and give the eye nothing to count along, and the point of a train is that the
 * carriages are the same shape.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaPromptWagon(prompt: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val summary = remember(prompt) { MaLittleMan.summarise(prompt) }
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = prompt,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The editor, opened by holding a wagon.
 *
 * Deliberately plain: a text field, save, delete, cancel. The reason it exists is narrow and worth
 * keeping in view, which is that these instructions arrived by voice and voice gets one word wrong.
 * Re-speaking a whole sentence to fix a word is exactly what this saves.
 *
 * Saving an empty field deletes, which is what emptying a thing means everywhere else, and the
 * delete button is still there for people who would rather say it than mean it.
 */
@Composable
private fun MaPromptEditor(
    original: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(original) { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit instruction") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What the little man should do") },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Its place in the line does not change when you edit it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("SAVE") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("DELETE") }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}
