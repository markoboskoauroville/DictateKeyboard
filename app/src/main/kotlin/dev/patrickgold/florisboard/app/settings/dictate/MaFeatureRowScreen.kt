/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaFeatureKey
import dev.patrickgold.florisboard.dictate.MaFeatureOrder
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/** The height of one row. Fixed on purpose; see [MaFeatureRowScreen] for why the drag depends on it. */
val ROW_HEIGHT = 64.dp

/**
 * Settings, Mantra, Feature row: the nine keys, in a list, dragged into whatever order suits.
 *
 * This row's order has been corrected by hand twice, at builds 139 and 146, both times from a
 * screenshot with arrows drawn on it, and both times in the direction opposite to what looked
 * sensible from inside the code. That is the whole argument for this screen. The person holding the
 * keyboard should not have to send a picture and wait for a build to move a key.
 *
 * **Order only. Nothing here can hide a key**, and that is a safety rule rather than a missing
 * feature. This row is the one that survives when every other row is folded away, so it is the only
 * route to backspace, to enter, and to the microphone. An editor that could hide those could leave
 * somebody with a keyboard that cannot delete a character or end a line, and no way back except the
 * settings app. Rearranging cannot lock anyone out of anything. Hiding could.
 *
 * **The drag is written by hand rather than pulled from a library.** Nine items of one fixed height
 * is the case where a reorderable list is a division: the target index is the drag distance divided
 * by the row height, and there is nothing else to it. A library for this would be a dependency, a
 * migration and a set of behaviours to learn, in exchange for arithmetic that fits on one line.
 * [ROW_HEIGHT] being fixed is what buys that, so if these rows ever grow to different heights this
 * calculation has to be replaced rather than adjusted.
 */
@Composable
fun MaFeatureRowScreen() = FlorisScreen {
    title = "Feature row"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val storedRaw by prefs.dictate.maFeatureRowOrder.collectAsState()

        // The order being dragged, held locally so the row under the finger moves at the speed of the
        // finger. Writing to the preference on every pixel would round-trip through the datastore and
        // the keyboard's own recomposition for each frame of a drag.
        var order by remember(storedRaw) { mutableStateOf(MaFeatureOrder.parse(storedRaw)) }
        var draggingIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }

        Text(
            text = "Hold a key, then drag it. The row on your keyboard follows immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "Every key stays on the row. Nothing here can remove one: with the keyboard " +
                "folded away this row is the only way to reach backspace, enter and the microphone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        MaReorderableColumn(
            items = order,
            rowHeight = ROW_HEIGHT,
            onMove = { from, to -> order = MaFeatureOrder.move(order, from, to) },
            onSettled = {
                scope.launch { prefs.dictate.maFeatureRowOrder.set(MaFeatureOrder.serialize(order)) }
            },
        ) { index, key, lifted ->
            MaFeatureRowItem(position = index + 1, key = key, lifted = lifted)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                order = MaFeatureOrder.DEFAULT
                scope.launch { prefs.dictate.maFeatureRowOrder.set(MaFeatureOrder.DEFAULT_RAW) }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("RESET TO DEFAULT")
        }
    }
}

@Composable
private fun MaFeatureRowItem(position: Int, key: MaFeatureKey, lifted: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(if (lifted) 8.dp else 0.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = if (lifted) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            MaFeatureIcon(key)
            Spacer(Modifier.width(14.dp))
            Text(
                text = key.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (lifted) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Hold and drag to move",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The same glyph the key itself carries, so the list can be read against the keyboard.
 *
 * The three zone switches are numerals on the row rather than icons, so they are numerals here too.
 * Giving them a picture that exists nowhere else would make this list something to translate rather
 * than something to recognise.
 */
@Composable
private fun MaFeatureIcon(key: MaFeatureKey) {
    val tint = MaterialTheme.colorScheme.onSurface
    val size = Modifier.size(24.dp)
    when (key) {
        MaFeatureKey.ALL_PASTE ->
            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = tint, modifier = size)
        MaFeatureKey.SELECT_ALL ->
            Icon(Icons.Default.SelectAll, contentDescription = null, tint = tint, modifier = size)
        MaFeatureKey.BACKSPACE ->
            Icon(Icons.Default.Backspace, contentDescription = null, tint = tint, modifier = size)
        MaFeatureKey.MIC ->
            Icon(Icons.Default.Mic, contentDescription = null, tint = tint, modifier = size)
        MaFeatureKey.BOOK ->
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = tint,
                modifier = size,
            )
        MaFeatureKey.ZONE_1, MaFeatureKey.ZONE_2, MaFeatureKey.ZONE_3 -> Box(
            modifier = size,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (key) {
                    MaFeatureKey.ZONE_1 -> "1"
                    MaFeatureKey.ZONE_2 -> "2"
                    else -> "3"
                },
                // Green, the colour the key wears on the row when its zone is showing. Colour means
                // state everywhere in this app and nothing else, so it is used here only because
                // these three are the keys that carry it.
                color = Color(0xFF6FA85A),
                fontWeight = FontWeight.SemiBold,
            )
        }
        MaFeatureKey.ENTER ->
            // AutoMirrored, not Default. The repo already uses it that way in ComputingEvaluator,
            // which is the check that caught this: Icons.Default.KeyboardReturn is the deprecated
            // spelling and would have been the build failure.
            Icon(
                Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = null,
                tint = tint,
                modifier = size,
            )
    }
}
