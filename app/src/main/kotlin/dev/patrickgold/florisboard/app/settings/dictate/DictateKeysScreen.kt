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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.dictate.MaKeyImport
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a key test concluded. The colour is the whole point: one glance, one answer. */
private enum class KeyHealth { UNTESTED, TESTING, WORKING, REJECTED, NO_QUOTA, OFFLINE }

private val GREEN = Color(0xFF56D364)
private val RED = Color(0xFFF85149)
private val AMBER = Color(0xFFF0883E)
private val YELLOW = Color(0xFFE3B341)
private val GREY = Color(0xFF8B949E)

private fun KeyHealth.colour(): Color = when (this) {
    KeyHealth.WORKING -> GREEN
    KeyHealth.REJECTED -> RED
    KeyHealth.NO_QUOTA -> AMBER
    KeyHealth.OFFLINE -> YELLOW
    else -> GREY
}

private fun KeyHealth.label(): String = when (this) {
    KeyHealth.WORKING -> "works"
    KeyHealth.REJECTED -> "rejected"
    KeyHealth.NO_QUOTA -> "no quota"
    KeyHealth.OFFLINE -> "no connection"
    KeyHealth.TESTING -> "testing"
    KeyHealth.UNTESTED -> "untested"
}

/** Result of testing one key: its light, and a short sentence explaining it. */
private data class KeyStatus(val health: KeyHealth, val detail: String = "")

/**
 * Providers this build actually uses, in the order they matter, with what each one is for. Anything
 * else in the registry is still supported if a key turns up for it, but is not advertised here.
 */
/** Shared with the setup wizard so both places import the same way. */
private val MA_PROVIDERS = MaKeyImport.PROVIDERS

/**
 * The key manager. One picker, one list, one place.
 *
 * The old version had a file picker per provider, which asked the user to know which key belongs to
 * which service before importing it. That is backwards: the parser already knows. So there is a
 * single button now. It reads the file once, works out which keys belong to which provider, and files
 * them. One file holding every key is the normal case, and importing it twice changes nothing.
 *
 * Lights, per key:
 *   green   the service accepted this key
 *   red     the service rejected it, it is dead or belongs somewhere else
 *   amber   accepted but out of quota, so it will be skipped in favour of the next
 *   yellow  the phone could not reach the service, so nothing was learned about the key
 *   grey    not tested yet
 *
 * The red/yellow split is the one that matters. Without it a test run with no signal looks exactly
 * like a dead key, and a good key gets deleted for nothing.
 *
 * The radio button picks which key is tried first. The rest stay as fallbacks in order, which is how
 * the call path already treats them: a rejected or exhausted key rolls on to the next one.
 */
@Composable
fun DictateKeysScreen() = FlorisScreen {
    title = "API keys"
    previewFieldVisible = false
    iconSpaceReserved = false

    val prefs by FlorisPreferenceStore

    content {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val activeTranscriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
        val activeRewordingId by prefs.dictate.rewordingProviderId.collectAsState()

        // Keyed by "providerId\u0000key" so the same key held by two providers is tracked separately.
        val statuses = remember { mutableStateMapOf<String, KeyStatus>() }
        var note by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        /** Every provider with a key, plus whichever two are currently active. */
        val shown = remember(accounts, activeTranscriptionId, activeRewordingId) {
            val ids = LinkedHashSet<String>()
            MA_PROVIDERS.forEach { (id, _) ->
                if (accounts.accounts[id]?.apiKey.orEmpty().isNotBlank()) ids.add(id)
            }
            accounts.accounts.forEach { (id, acc) -> if (acc.apiKey.isNotBlank()) ids.add(id) }
            ids.add(activeTranscriptionId)
            ids.add(activeRewordingId)
            ids.mapNotNull { ProviderRegistry.byId(it) }
        }

        fun save(updated: ProviderAccounts) {
            scope.launch { prefs.dictate.providerAccounts.set(updated) }
        }

        // Restore, the other half of backup. On a phone with no keys at all, this is a fresh
        // install, so read the backup in Documents and file it. Only ever when the keyring is
        // completely empty: once a single key exists, silently merging a file the user has not
        // asked for would fight whatever they have set up by hand.
        var restoreChecked by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(restoreChecked) {
            if (restoreChecked) return@LaunchedEffect
            restoreChecked = true
            val anyKey = accounts.accounts.values.any { it.apiKey.isNotBlank() }
            if (anyKey) return@LaunchedEffect
            val text = MaVault.read()
            if (text.isNullOrBlank()) return@LaunchedEffect
            val result = MaKeyImport.importAll(text, accounts)
            if (result.added > 0) {
                prefs.dictate.providerAccounts.set(result.accounts)
                note = "Restored ${result.added} keys from ${MaVault.DISPLAY_PATH}"
            }
        }

        /** Runs one key against the live service and records what came back. */
        fun testKey(preset: ProviderPreset, key: String) {
            val slot = preset.id + "\u0000" + key
            statuses[slot] = KeyStatus(KeyHealth.TESTING)
            val account = accounts.accounts[preset.id]
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    try {
                        val count = OpenAiCompatibleClient
                            .from(
                                preset, key,
                                baseUrlOverride = account?.customBaseUrl?.takeIf { it.isNotBlank() }
                                    ?: preset.baseUrl,
                                proxy = prefs.dictate.dictateProxyConfig(),
                                trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                            )
                            .validateKey()
                        KeyStatus(
                            KeyHealth.WORKING,
                            if (count >= 0) "works, $count models" else "works",
                        )
                    } catch (e: DictateApiException) {
                        when (e.kind) {
                            DictateApiException.Kind.INVALID_API_KEY ->
                                KeyStatus(KeyHealth.REJECTED, "rejected by the service")
                            DictateApiException.Kind.QUOTA_EXCEEDED ->
                                KeyStatus(KeyHealth.NO_QUOTA, "out of quota, will be skipped")
                            DictateApiException.Kind.NETWORK, DictateApiException.Kind.TIMEOUT ->
                                KeyStatus(KeyHealth.OFFLINE, "no connection, key not checked")
                            else -> KeyStatus(
                                KeyHealth.OFFLINE,
                                MaKeys.tidyError(e.message, "could not be checked"),
                            )
                        }
                    } catch (e: Exception) {
                        KeyStatus(
                            KeyHealth.OFFLINE,
                            MaKeys.tidyError(e.message, "could not be checked"),
                        )
                    }
                }
                statuses[slot] = status
            }
        }

        // THE single picker. One file, every provider, sorted automatically.
        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                }.getOrNull().orEmpty()
                val result = MaKeyImport.importAll(text, accounts)
                if (result.added > 0) {
                    save(result.accounts)
                    MaVault.write(text)
                }
                note = result.summary
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = { picker.launch(arrayOf("*/*")) },
        ) {
            Text("LOAD KEYS FROM FILE")
        }
        Text(
            text = "Any text file. The keys are lifted out of it, sorted to the right provider and " +
                "everything else is ignored. Nothing is ever pasted, and importing the same file " +
                "twice changes nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = {
                    shown.forEach { preset ->
                        MaKeys.split(accounts.accounts[preset.id]?.apiKey.orEmpty())
                            .filter { it.isNotBlank() }
                            .forEach { testKey(preset, it) }
                    }
                },
            ) {
                Text("TEST ALL")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        var working = accounts
                        val lines = mutableListOf<String>()
                        for (preset in shown) {
                            val key = MaKeys.split(working.accounts[preset.id]?.apiKey.orEmpty())
                                .firstOrNull { it.isNotBlank() } ?: continue
                            val ids = withContext(Dispatchers.IO) {
                                runCatching {
                                    OpenAiCompatibleClient
                                        .from(
                                            preset, key,
                                            proxy = prefs.dictate.dictateProxyConfig(),
                                            trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                                        )
                                        .listModels()
                                        .map { it.id }
                                }.getOrNull()
                            }
                            if (ids != null) {
                                val existing = working.accounts[preset.id]
                                    ?: ProviderAccount(providerId = preset.id)
                                working = working.put(
                                    existing.copy(
                                        cachedModels = ids,
                                        cachedModelsAt = System.currentTimeMillis(),
                                    )
                                )
                                lines += "${preset.displayName} ${ids.size}"
                            }
                        }
                        save(working)
                        note = if (lines.isEmpty()) {
                            "No model lists could be refreshed"
                        } else {
                            "Models: " + lines.joinToString(", ")
                        }
                        busy = false
                    }
                },
            ) {
                Text("CHECK MODELS")
            }
        }

        // Backup. The copy made at import time is only ever the last file that was picked; after a
        // few imports, some deletions and some reordering, the list actually in use matches no file
        // on disk. This writes that curated list out, to the same place a fresh install reads from.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val sections = shown.map { preset ->
                        preset.displayName to MaKeys
                            .split(accounts.accounts[preset.id]?.apiKey.orEmpty())
                            .filter { it.isNotBlank() }
                    }
                    val count = sections.sumOf { it.second.size }
                    note = when {
                        count == 0 -> "No keys to back up yet"
                        MaVault.writeBackup(sections) ->
                            "$count keys backed up to ${MaVault.DISPLAY_PATH}"
                        else ->
                            "Could not write the backup. Grant all-files access and try again."
                    }
                },
            ) {
                Text("BACK UP KEYS")
            }
        }

        if (note.isNotEmpty()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Text(
            text = "The filled circle is the key tried first. The others are fallbacks, in order.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        shown.forEach { preset ->
            ProviderSection(
                preset = preset,
                accounts = accounts,
                statuses = statuses,
                isTranscription = preset.id == activeTranscriptionId,
                isRewording = preset.id == activeRewordingId,
                onTest = { key -> testKey(preset, key) },
                onSave = ::save,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Where to get a key, and what each one does here. Kept at the bottom deliberately: it is
        // reference material, needed once, and does not belong in the way of the daily list above.
        Text(
            text = "Where the keys come from",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        MA_PROVIDERS.forEach { (id, purpose) ->
            val preset = ProviderRegistry.byId(id) ?: return@forEach
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = preset.apiKeyUrl != null) {
                        preset.apiKeyUrl?.let { context.launchUrl(it) }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (preset.apiKeyUrl != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "Keys are also mirrored to ${MaVault.DISPLAY_PATH}, which an uninstall does not " +
                "delete, so this list comes back by itself on a clean install.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ProviderSection(
    preset: ProviderPreset,
    accounts: ProviderAccounts,
    statuses: SnapshotStateMap<String, KeyStatus>,
    isTranscription: Boolean,
    isRewording: Boolean,
    onTest: (String) -> Unit,
    onSave: (ProviderAccounts) -> Unit,
) {
    val account = accounts.accounts[preset.id]
    val keys = remember(account?.apiKey) {
        MaKeys.split(account?.apiKey.orEmpty()).filter { it.isNotBlank() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        val roles = buildList {
            if (isTranscription) add("transcription")
            if (isRewording) add("rewording")
        }
        if (roles.isNotEmpty()) {
            Text(
                text = roles.joinToString(" + "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (keys.isEmpty()) {
        Text(
            text = "No key yet. Load your keys file above.",
            style = MaterialTheme.typography.bodySmall,
            color = RED,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }

    keys.forEachIndexed { index, key ->
        val status = statuses[preset.id + "\u0000" + key] ?: KeyStatus(KeyHealth.UNTESTED)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selecting a key moves it to the front, which is exactly what "default" means to the
            // call path: it walks the list in order and rolls on when one is refused.
            RadioButton(
                selected = index == 0,
                onClick = {
                    if (index != 0) {
                        val reordered = listOf(key) + keys.filterNot { it == key }
                        val existing = account ?: ProviderAccount(providerId = preset.id)
                        onSave(accounts.put(existing.copy(apiKey = MaKeys.join(reordered))))
                    }
                },
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = "${index + 1}. ${MaKeys.mask(key)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status.detail.isNotEmpty()) {
                    Text(
                        text = status.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (status.health == KeyHealth.TESTING) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(status.health.colour()),
                )
            }
            Text(
                text = status.health.label(),
                style = MaterialTheme.typography.labelSmall,
                color = status.health.colour(),
                modifier = Modifier.padding(start = 6.dp),
            )
            TextButton(onClick = { onTest(key) }) { Text("test") }
            IconButton(onClick = {
                statuses.remove(preset.id + "\u0000" + key)
                val existing = account ?: ProviderAccount(providerId = preset.id)
                onSave(
                    accounts.put(
                        existing.copy(apiKey = MaKeys.join(keys.filterNot { it == key }))
                    )
                )
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove this key",
                    tint = RED,
                )
            }
        }
    }
}
