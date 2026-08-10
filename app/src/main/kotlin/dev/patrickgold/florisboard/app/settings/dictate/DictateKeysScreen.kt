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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.patrickgold.florisboard.dictate.MaKeyRingStore
import dev.patrickgold.florisboard.dictate.MaUsageStore
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.MaKeyRing
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.dictate.provider.MaSpeechify
import dev.patrickgold.florisboard.dictate.provider.MaUsage
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a key test concluded. The colour is the whole point: one glance, one answer. */
private enum class KeyHealth { UNTESTED, TESTING, WORKING, REJECTED, NO_QUOTA, OFFLINE }

private val GREEN = Color(0xFF56D364)
private val RED = Color(0xFF9B3B33)
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

/** The on-device provider's id, whose "keys" are downloaded models rather than a secret. */
private const val LOCAL_PROVIDER_ID = "local"

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

        /**
         * Writes the keyring, and mirrors it straight back out to the backup file.
         *
         * This is the fix for deleted keys rising from the dead. The backup used to be written only
         * on import, from the raw file that was picked, so deleting a dud key changed the keyring
         * and left the backup untouched. A reinstall then restored the old file, dud key and all,
         * and it looked as though the deletion had never happened. It had; the backup simply had not
         * heard about it. Every change now goes to both places, so what is on screen is what comes
         * back.
         */
        fun save(updated: ProviderAccounts) {
            scope.launch { prefs.dictate.providerAccounts.set(updated) }
            val sections = updated.accounts.values
                .filter { it.apiKey.isNotBlank() }
                .map { account ->
                    (ProviderRegistry.byId(account.providerId)?.displayName ?: account.providerId) to
                        MaKeys.split(account.apiKey).filter { it.isNotBlank() }
                }
            if (sections.isNotEmpty()) MaVault.writeBackup(sections)
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

        /**
         * Runs one key against the live service and records what came back.
         *
         * Returns its Job so a bulk run can wait for each before starting the next. A fire and
         * forget version cannot be sequenced, and sequencing is the whole point at sixty keys.
         */
        fun testKey(preset: ProviderPreset, key: String): Job {
            val slot = preset.id + "\u0000" + key
            statuses[slot] = KeyStatus(KeyHealth.TESTING)
            val account = accounts.accounts[preset.id]
            // Testing a key by hand is a fresh question, so any flag on it is dropped before the
            // question is asked. Otherwise a key Marko has just topped up stays greyed out because
            // of what happened to it yesterday, and the only way to clear it would be to know the
            // rule.
            MaKeyRingStore.forget(context, preset.id, key)
            return scope.launch {
                val status = withContext(Dispatchers.IO) {
                    // Speechify speaks rather than listens, so there is no model list to count and
                    // nothing in OpenAiCompatibleClient that fits. It is probed by synthesising two
                    // characters, which is the only test that answers the real question: not "does
                    // this string authenticate" but "will this key make audio when the reader asks".
                    // A key whose balance is gone lists voices perfectly well and then fails.
                    if (preset.capabilities.tts) {
                        val probe = MaSpeechify.probe(key)
                        // The probe was billed, so it goes in the ledger like any other call. A
                        // meter that quietly excludes its own traffic is a meter that drifts.
                        if (probe.billableCharacters > 0) {
                            MaUsageStore.record(
                                context, preset.id, key,
                                probe.billableCharacters.toLong(), MaUsage.Unit.CHARACTER,
                            )
                        }
                        // The verdict goes into the ring as well as onto the screen, so what the
                        // key manager learns is the same thing the reader will act on. Two places
                        // holding two opinions about the same key is how a green light ends up
                        // above a key nothing will ever use.
                        val ring = MaKeyRingStore.load(context, preset.id)
                        val updated = when {
                            probe.ok -> MaKeyRing.onSuccess(ring, key, probe.detail)
                            probe.kind != null -> MaKeyRing.onFailure(ring, key, probe.kind, probe.detail)
                            else -> ring
                        }
                        if (updated !== ring) MaKeyRingStore.save(context, preset.id, updated)
                        return@withContext when {
                            probe.ok -> KeyStatus(KeyHealth.WORKING, probe.detail)
                            probe.kind == DictateApiException.Kind.INVALID_API_KEY ->
                                KeyStatus(KeyHealth.REJECTED, probe.detail)
                            probe.kind == DictateApiException.Kind.QUOTA_EXCEEDED ->
                                KeyStatus(KeyHealth.NO_QUOTA, probe.detail)
                            else -> KeyStatus(KeyHealth.OFFLINE, probe.detail)
                        }
                    }
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
                        MaKeyRingStore.onSuccess(context, preset.id, key)
                        KeyStatus(
                            KeyHealth.WORKING,
                            if (count >= 0) "works, $count models" else "works",
                        )
                    } catch (e: DictateApiException) {
                        // Same ring as Speechify, same rules, every provider. A verdict reached here
                        // is the one the dictation and rewording paths will act on, because there is
                        // only one place holding it.
                        MaKeyRingStore.onFailure(
                            context, preset.id, key, e.kind,
                            MaKeys.tidyError(e.message, ""),
                        )
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
                    // save() mirrors the merged keyring to the backup itself. Writing the raw picked
                    // file here as well would put keys in the backup that were rejected as belonging
                    // to another provider, and they would come back on the next restore.
                    save(result.accounts)
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
                    // FIND A WORKING KEY, one at a time, and STOP at the first one that answers.
                    //
                    // This replaces a TEST ALL button, and the change is Marko's rule: go down the
                    // list in order, take the first key that works, and stay on it. Testing every
                    // key was wrong twice over. It costs money on a per-character bill, once per
                    // key, to learn something about keys that were never going to be reached. And
                    // firing them together trips a rate limit, whose answer says nothing about any
                    // key while looking exactly like a verdict on all of them.
                    //
                    // The keys already known to be refused are skipped, so a long keyring is walked
                    // once in its life rather than once per press.
                    busy = true
                    scope.launch {
                        var found = 0
                        for (preset in shown) {
                            val stored = accounts.accounts[preset.id]?.apiKey.orEmpty()
                            val ring = MaKeyRingStore.load(context, preset.id)
                            val ordered = MaKeyRing.order(
                                MaKeys.split(stored).filter { it.isNotBlank() },
                                ring,
                            )
                            if (ordered.isEmpty()) continue
                            for ((index, key) in ordered.withIndex()) {
                                note = "${preset.displayName}: trying key ${index + 1} of ${ordered.size}"
                                testKey(preset, key).join()
                                val health = statuses[preset.id + "\u0000" + key]?.health
                                if (health == KeyHealth.WORKING) {
                                    found++
                                    note = "${preset.displayName}: key ${index + 1} works, using it"
                                    break
                                }
                                // No signal is not a verdict on the key, and it will be no more of
                                // one for the next key either. Stop rather than walk the whole ring
                                // against a connection that is not there.
                                if (health == KeyHealth.OFFLINE) {
                                    note = "${preset.displayName}: no connection, nothing checked"
                                    break
                                }
                            }
                        }
                        if (found == 0) note = "No working key found"
                        busy = false
                    }
                },
            ) {
                Text("FIND A WORKING KEY")
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

        // Telling someone to grant a permission without giving them a way to do it is not a message,
        // it is a dead end. The system screen is one tap from here whenever access is missing.
        if (!MaVault.hasFullAccess()) {
            TextButton(
                modifier = Modifier.padding(horizontal = 8.dp),
                onClick = {
                    runCatching { context.startActivity(MaVault.accessIntent(context)) }
                },
            ) {
                Text("GRANT ALL-FILES ACCESS")
            }
            Text(
                text = "Android will not let this app read or write a file it does not own without " +
                    "it, which is what the backup in Documents is. Find " +
                    "${context.getString(R.string.app_name_full)} in the list and turn it on.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Text(
            text = "The app goes down this list in order, keeps the first key that works, and stays " +
                "on it until it stops. A key the service refuses is skipped from then on; one that " +
                "is out of credit is tried again after six hours, or as soon as the month turns.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Text(
            text = "The filled circle is the key tried first. The others are fallbacks, in order.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // The ledger is read once here rather than per row. It is recomputed whenever a test writes
        // to it, which is what `statuses.size` is doing in the key: a test finishing is the only
        // thing on this screen that can change the numbers, so it is the right thing to watch.
        // Read off the main thread. At sixty keys and a year of history the file is over a megabyte
        // and parsing it took 204 ms in a measured run, which is a dropped frame on the way into the
        // screen. It is read once, in the background, and the lines simply appear when it lands.
        var usage by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
        LaunchedEffect(statuses.size, shown.size) {
            usage = withContext(Dispatchers.IO) {
                val ledger = MaUsageStore.load(context)
                shown.associate { preset ->
                    preset.id to MaUsage.describeAll(
                        ledger = ledger,
                        providerId = preset.id,
                        rate = MaUsage.DEFAULT_RATES[preset.id] ?: 0.0,
                    )
                }
            }
        }

        shown.forEach { preset ->
            ProviderSection(
                preset = preset,
                accounts = accounts,
                statuses = statuses,
                isTranscription = preset.id == activeTranscriptionId,
                isRewording = preset.id == activeRewordingId,
                onTest = { key -> testKey(preset, key) },
                onSave = ::save,
                // Looked up, not computed. The map was built once above in a single pass over the
                // ledger, so adding keys no longer costs anything per row.
                usageOf = { key -> usage[preset.id]?.get(MaUsage.tail(key)).orEmpty() },
            )
        }

        Text(
            text = "The usage figures under each key are counted by this phone, from what each " +
                "reply says it billed. No provider here offers a balance to read, so nothing " +
                "spent from the console or another device can appear in them.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

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

        // On-device, offline. It used to live behind a dialog on the AI providers screen, which is
        // gone, and deleting that screen left this with nowhere to be reached from at all. It
        // belongs here anyway: this is the page about where transcription comes from, and a local
        // model is exactly that, just one that needs no key.
        val localAccount = accounts.accounts[LOCAL_PROVIDER_ID]
        Text(
            text = "ON-DEVICE (OFFLINE)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
        )
        Text(
            text = "Runs on this phone, so no audio leaves it and no key is involved. Download a " +
                "Parakeet model to use it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LocalModelSection(
            activeModelId = localAccount?.transcriptionModel.orEmpty(),
            activeStreamingModelId = localAccount?.realtimeModel.orEmpty(),
            onActiveModelChange = { id ->
                val existing = localAccount ?: ProviderAccount(providerId = LOCAL_PROVIDER_ID)
                save(accounts.put(existing.copy(transcriptionModel = id)))
            },
            onActiveStreamingModelChange = { id ->
                val existing = localAccount ?: ProviderAccount(providerId = LOCAL_PROVIDER_ID)
                save(accounts.put(existing.copy(realtimeModel = id)))
            },
        )

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
    usageOf: (String) -> String = { "" },
) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
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
    }

    // The two roles, chosen here rather than on a separate screen. They are radio buttons and not
    // checkboxes because each role belongs to exactly one provider: two providers cannot both be
    // "the transcriber". A role only appears on a provider that can actually do it, so Anthropic
    // never offers to transcribe, and only on one that has a key, since pointing a role at an empty
    // provider is how the app ends up saying it has no key when it does.
    val canTranscribe = preset.capabilities.transcription
    val canReword = preset.capabilities.chat
    if (keys.isNotEmpty() && (canTranscribe || canReword)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canTranscribe) {
                MaRoleChip(
                    label = "transcription",
                    selected = isTranscription,
                    onSelect = { scope.launch { prefs.dictate.transcriptionProviderId.set(preset.id) } },
                )
            }
            if (canReword) {
                MaRoleChip(
                    label = "rewording",
                    selected = isRewording,
                    onSelect = { scope.launch { prefs.dictate.rewordingProviderId.set(preset.id) } },
                )
            }
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
                // What this key has cost, under the key it belongs to. Two lines of very small type
                // rather than a separate screen: the question "is this key any good" and the
                // question "what has it cost me" are the same question asked twice, and answering
                // them in two places means looking in two places.
                val usage = usageOf(key)
                if (usage.isNotEmpty()) {
                    Text(
                        text = usage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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

/**
 * One role, as a radio button with its name beside it.
 *
 * A radio rather than a checkbox because the choice is exclusive: selecting a provider for a role
 * takes that role away from whoever had it, which is precisely what a radio group means and what a
 * row of checkboxes would quietly fail to say.
 */
@Composable
private fun MaRoleChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !selected, onClick = onSelect)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
