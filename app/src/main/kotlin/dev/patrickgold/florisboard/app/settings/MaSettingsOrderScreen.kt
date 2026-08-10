/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.app.settings

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictate.MaReorderableColumn
import dev.patrickgold.florisboard.app.settings.dictate.ROW_HEIGHT
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * The icon each settings link wears, kept beside the list it belongs to.
 *
 * Public because the settings list itself draws from the same table. One place, so a link and its
 * row in the editor can never end up wearing different pictures, which would make the editor a thing
 * to decode rather than recognise.
 */
val MaSettingsEntry.icon: ImageVector
    get() = when (this) {
        MaSettingsEntry.LITTLE_MAN -> Icons.Default.RecordVoiceOver
        MaSettingsEntry.FEATURE_ROW -> Icons.Default.DragHandle
        MaSettingsEntry.DEFAULT_VIEW -> Icons.Default.Visibility
        MaSettingsEntry.MACROS -> Icons.Default.Bolt
        MaSettingsEntry.DB -> Icons.Default.Dashboard
        MaSettingsEntry.KEYS -> Icons.Default.Key
        MaSettingsEntry.RECORDING -> Icons.Default.Mic
        MaSettingsEntry.REWORDING -> Icons.Default.AutoAwesome
        MaSettingsEntry.MAPPINGS -> Icons.Default.Spellcheck
        MaSettingsEntry.OUTPUT -> Icons.Default.Keyboard
        MaSettingsEntry.HISTORY -> Icons.Default.History
        MaSettingsEntry.RECOVERED -> Icons.Default.History
        // Tune rather than Reorder: Reorder has no proven import anywhere in this repo, and an icon
        // that exists in the docs but not in the artifact this project actually resolves is a build
        // failure named after a file you were not editing. See the KeyboardReturn note in HANDOFF.
        MaSettingsEntry.SETTINGS_ORDER -> Icons.Default.Tune
    }

/**
 * Where each settings link goes.
 *
 * An extension rather than a field on the enum, because [MaSettingsEntry] lives in the settings
 * package and `Routes` knows about every screen in the app: putting a route on the enum would drag
 * the whole navigation graph into a file whose only job is to hold names and an order.
 */
val MaSettingsEntry.route: Any
    get() = when (this) {
        MaSettingsEntry.LITTLE_MAN -> Routes.Settings.DictateLittleMan
        MaSettingsEntry.FEATURE_ROW -> Routes.Settings.MaFeatureRow
        MaSettingsEntry.DEFAULT_VIEW -> Routes.Settings.MaOpeningView
        MaSettingsEntry.MACROS -> Routes.Settings.DictateMacros
        MaSettingsEntry.DB -> Routes.Settings.DbEditor
        MaSettingsEntry.KEYS -> Routes.Settings.DictateKeys
        MaSettingsEntry.RECORDING -> Routes.Settings.DictateRecording
        MaSettingsEntry.REWORDING -> Routes.Settings.DictateRewording
        MaSettingsEntry.MAPPINGS -> Routes.Settings.DictateMappings
        MaSettingsEntry.OUTPUT -> Routes.Settings.DictateOutput
        MaSettingsEntry.HISTORY -> Routes.Settings.DictateHistory
        MaSettingsEntry.RECOVERED -> Routes.Settings.DictateRecovered
        MaSettingsEntry.SETTINGS_ORDER -> Routes.Settings.MaSettingsOrder
    }

/**
 * Settings, Mantra, Edit settings order.
 *
 * The same screen as the feature row editor and, since build 156, literally the same drag: both use
 * [MaReorderableColumn]. Two copies of drag arithmetic is how two lists quietly stop behaving alike,
 * with one swapping where the other shifts and nobody able to say why.
 *
 * **Order only, nothing hidden.** As on the feature row, and here the reason is the same in kind:
 * API keys is on this list, and a keyboard whose key screen cannot be reached is a keyboard that
 * cannot be repaired from inside itself.
 */
@Composable
fun MaSettingsOrderScreen() = FlorisScreen {
    title = "Settings order"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val storedRaw by prefs.dictate.maSettingsOrder.collectAsState()
        var order by remember(storedRaw) { mutableStateOf(MaSettingsOrder.parse(storedRaw)) }

        Text(
            text = "Hold an entry, then drag it. The settings list follows immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "Every entry stays on the list. Nothing here can remove one.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        MaReorderableColumn(
            items = order,
            rowHeight = ROW_HEIGHT,
            onMove = { from, to -> order = MaSettingsOrder.move(order, from, to) },
            onSettled = {
                scope.launch { prefs.dictate.maSettingsOrder.set(MaSettingsOrder.serialize(order)) }
            },
        ) { index, entry, lifted ->
            MaSettingsOrderItem(index + 1, entry, lifted)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                order = MaSettingsOrder.DEFAULT
                scope.launch { prefs.dictate.maSettingsOrder.set(MaSettingsOrder.DEFAULT_RAW) }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("RESET TO DEFAULT")
        }
    }
}

@Composable
private fun MaSettingsOrderItem(position: Int, entry: MaSettingsEntry, lifted: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(if (lifted) 8.dp else 0.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = if (lifted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.width(24.dp),
            )
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = entry.title,
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
 * Settings, Mantra, Opening view: which view the keyboard shows when it appears.
 *
 * Three choices and no more. The reader is deliberately not one of them: it is entered to read
 * something specific and a keyboard that opens into it would be a keyboard that cannot type.
 */
@Composable
fun MaOpeningViewScreen() = FlorisScreen {
    title = "Opening view"

    content {
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val current by prefs.dictate.maOpeningView.collectAsState()

        Text(
            text = "Which view appears when the keyboard opens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val options = listOf(
            Triple("keyboard", "Keyboard", "The typing keyboard, every time"),
            Triple("dictation", "Dictation", "The recording view, every time"),
            Triple("last", "Whichever was last used", "Reopens the view you left"),
        )
        options.forEach { (value, title, summary) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (current == value) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
                onClick = { scope.launch { prefs.dictate.maOpeningView.set(value) } },
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column2(title, summary, current == value)
                    }
                }
            }
        }
    }
}

@Composable
private fun Column2(title: String, summary: String, selected: Boolean) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
