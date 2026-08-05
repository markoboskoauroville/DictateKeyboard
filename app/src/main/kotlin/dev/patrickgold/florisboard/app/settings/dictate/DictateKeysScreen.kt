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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
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

/** Result of testing one key: its light, and a short sentence explaining it. */
private data class KeyStatus(val health: KeyHealth, val detail: String = "")

/**
 * The key manager.
 *
 * Everything in this app depends on a working key, so a key deserves more than a masked string and a
 * single "Test connection" button that lumps every provider together. Each key gets its own row, its
 * own light and its own test:
 *
 *   green   the service accepted this key
 *   red     the service rejected it, it is dead or belongs somewhere else
 *   amber   accepted but out of quota, it will be skipped in favour of the next one
 *   yellow  the phone could not reach the service, so nothing was learned about the key
 *   grey    not tested yet
 *
 * The distinction between red and yellow is the one that matters. Without it an aeroplane-mode test
 * looks exactly like a dead key, and a good key gets deleted for nothing.
 *
 * Models are refreshed from here too, per provider, because a key that works against a model the
 * provider has retired fails in a way that reads like a key problem and is not one.
 */
@Composable
fun DictateKeysScreen() = FlorisScreen {
    title = "API keys"
    previewFieldVisible = false
    iconSpaceReserved = false

    val prefs by FlorisPreferenceStore

    content {
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val activeTranscriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
        val activeRewordingId by prefs.dictate.rewordingProviderId.collectAsState()

        // Keyed by "providerId\u0000key" so a key shared between two providers is tracked separately.
        val statuses = remember { mutableStateMapOf<String, KeyStatus>() }

        // Every provider that has a key, plus the two that are currently active even when empty, so
        // an unconfigured active provider is visibly missing its key rather than silently absent.
        val presets = remember(accounts, activeTranscriptionId, activeRewordingId) {
            ProviderRegistry.presets.filter { preset ->
                val stored = accounts.accounts[preset.id]?.apiKey.orEmpty()
                stored.isNotBlank() ||
                    preset.id == activeTranscriptionId ||
                    preset.id == activeRewordingId
            }
        }

        Text(
            text = "One row per key. Tap a light to test that key on its own, or test a whole " +
                "provider at once. Re-importing a file you have already imported adds nothing, so " +
                "it is always safe.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        presets.forEach { preset ->
            ProviderKeyCard(
                preset = preset,
                account = accounts.accounts[preset.id],
                isTranscription = preset.id == activeTranscriptionId,
                isRewording = preset.id == activeRewordingId,
                statuses = statuses,
            )
        }

        Text(
            text = "Keys are also mirrored to ${MaVault.DISPLAY_PATH}, which an uninstall does not " +
                "delete, so this list comes back by itself on a clean install.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ProviderKeyCard(
    preset: ProviderPreset,
    account: ProviderAccount?,
    isTranscription: Boolean,
    isRewording: Boolean,
    statuses: androidx.compose.runtime.snapshots.SnapshotStateMap<String, KeyStatus>,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by prefs.dictate.providerAccounts.collectAsState()

    val keys = remember(account?.apiKey) {
        MaKeys.split(account?.apiKey.orEmpty()).filter { it.isNotBlank() }
    }
    var note by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    fun statusKey(key: String) = preset.id + "\u0000" + key

    /** Writes a changed key list back into the keyring. */
    fun saveKeys(updated: List<String>) {
        val existing = account ?: ProviderAccount(providerId = preset.id)
        scope.launch {
            prefs.dictate.providerAccounts.set(
                accounts.put(existing.copy(apiKey = MaKeys.join(updated)))
            )
        }
    }

    /** Runs one key against the live service and records what came back. */
    fun testKey(key: String) {
        statuses[statusKey(key)] = KeyStatus(KeyHealth.TESTING)
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
                        if (count >= 0) "accepted, $count models" else "accepted",
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
                    KeyStatus(KeyHealth.OFFLINE, MaKeys.tidyError(e.message, "could not be checked"))
                }
            }
            statuses[statusKey(key)] = status
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
            }.getOrNull().orEmpty()
            val found = MaKeys.extract(text, preset.id)
            if (found.isEmpty()) {
                note = MaKeys.mismatchWarning(text, preset.id, found)
                    ?: "No key for ${preset.displayName} in that file"
            } else {
                val (merged, added) = MaKeys.merge(keys, found)
                saveKeys(merged)
                MaVault.write(text)
                note = when (added) {
                    0 -> "Already had every key in that file, nothing changed"
                    1 -> "1 new key added"
                    else -> "$added new keys added"
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "No key yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RED,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            keys.forEachIndexed { index, key ->
                val status = statuses[statusKey(key)] ?: KeyStatus(KeyHealth.UNTESTED)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (status.health == KeyHealth.TESTING) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(status.health.colour()),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = MaKeys.mask(key),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = when {
                            status.detail.isNotEmpty() -> status.detail
                            index == 0 -> "first choice"
                            else -> "fallback ${index + 1}"
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { testKey(key) }) { Text("Test") }
                    IconButton(onClick = {
                        statuses.remove(statusKey(key))
                        saveKeys(keys.filterNot { it == key })
                        note = "Key removed"
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove this key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Current model, and a refresh that asks the provider what it actually offers today. A
            // key tested green against a model the provider has since retired still fails, and the
            // failure reads like a key problem, so the two belong on the same screen.
            val currentModel = account?.transcriptionModel?.takeIf { it.isNotBlank() }
                ?: preset.defaultTranscriptionModel.orEmpty()
            if (currentModel.isNotEmpty()) {
                val known = account?.cachedModels.orEmpty()
                val retired = known.isNotEmpty() && currentModel !in known
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model: $currentModel",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (retired) {
                            Text(
                                text = "Not in this provider's current list. Pick another in AI providers.",
                                style = MaterialTheme.typography.labelSmall,
                                color = AMBER,
                            )
                        }
                    }
                    IconButton(
                        enabled = !refreshing && keys.isNotEmpty(),
                        onClick = {
                            refreshing = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        OpenAiCompatibleClient
                                            .from(
                                                preset, keys.first(),
                                                baseUrlOverride = account?.customBaseUrl
                                                    ?.takeIf { it.isNotBlank() } ?: preset.baseUrl,
                                                proxy = prefs.dictate.dictateProxyConfig(),
                                                trustUserCerts = prefs.dictate.trustUserCertificates.get(),
                                            )
                                            .listModels()
                                            .map { it.id }
                                    }
                                }
                                result.onSuccess { ids ->
                                    val existing = account ?: ProviderAccount(providerId = preset.id)
                                    prefs.dictate.providerAccounts.set(
                                        accounts.put(
                                            existing.copy(
                                                cachedModels = ids,
                                                cachedModelsAt = System.currentTimeMillis(),
                                            )
                                        )
                                    )
                                    note = "${ids.size} models refreshed"
                                }.onFailure { e ->
                                    note = MaKeys.tidyError(e.message, "Model list could not be refreshed")
                                }
                                refreshing = false
                            }
                        },
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh the model list",
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Import from file")
                }
                if (keys.isNotEmpty()) {
                    TextButton(onClick = { keys.forEach { testKey(it) } }) {
                        Text("Test all")
                    }
                }
            }

            if (note.isNotEmpty()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
