/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prefs

import android.content.Context
import dev.patrickgold.florisboard.dictate.MaLanguage
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.keyboard.extCoreComposer
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.ime.keyboard.extCorePunctuationRule
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.dictate.MaKeyImport
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.DictatePromptsLayout
import dev.patrickgold.florisboard.dictate.provider.DictateProxyType
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProxyConfig
import java.net.Proxy
import java.util.Locale
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.keyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

/**
 * One-time import of the legacy Dictate transcription settings (provider, API key, model) into the
 * unified JetPref store, so upgrading users keep their configuration after the in-place update while
 * everything is edited in one place going forward (see `docs/COMPATIBILITY.md`).
 *
 * Idempotent: guarded by `prefs.dictate.legacyImported`, which is set on the first run regardless of
 * whether legacy data was present (fresh installs simply mark it done).
 *
 * Must be called only after [FlorisPreferenceStore] has finished loading.
 */
object DictateLegacyMigrator {

    @Suppress("DEPRECATION") // writes the deprecated flat prefs that migrateProviderKeyringIfNeeded folds in
    suspend fun migrateIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.legacyImported.get()) return

        val legacy = DictateLegacyPreferences(context.applicationContext)
        if (legacy.isPresent()) {
            val s = legacy.readSnapshot()

            // Legacy provider index: 0 = OpenAI, 1 = Groq, 2 = Custom.
            val providerId = when (s.transcriptionProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.transcriptionProviderId.set(providerId)

            s.effectiveTranscriptionApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.apiKey.set(it) }

            val model = when (s.transcriptionProvider) {
                1 -> s.transcriptionGroqModel
                2 -> s.transcriptionCustomModel
                else -> s.transcriptionOpenaiModel
            }
            model?.takeIf { it.isNotBlank() }?.let { prefs.dictate.transcriptionModel.set(it) }

            s.transcriptionCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.customBaseUrl.set(it) }

            // --- Rewording / GPT settings (roadmap section 4). The prompts themselves live in the
            // shared prompts.db and carry over automatically; only these settings need importing. ---
            prefs.dictate.rewordingEnabled.set(s.rewordingEnabled)
            prefs.dictate.autoFormattingEnabled.set(s.autoFormattingEnabled)

            val rewordingProviderId = when (s.rewordingProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.rewordingProviderId.set(rewordingProviderId)

            s.effectiveRewordingApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.rewordingApiKey.set(it) }

            val rewordingModel = when (s.rewordingProvider) {
                1 -> s.rewordingGroqModel
                2 -> s.rewordingCustomModel
                else -> s.rewordingOpenaiModel
            }
            rewordingModel?.takeIf { it.isNotBlank() }?.let { prefs.dictate.rewordingModel.set(it) }

            s.rewordingCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.rewordingCustomBaseUrl.set(it) }

            // --- Per-provider credential carry-over. The legacy app stored a *separate* API key (and
            // model) for each provider, but the imports above only fold the *active* provider's key into
            // the flat prefs. Seed the keyring directly from every stored provider key so a user who had
            // configured e.g. both an OpenAI and a Groq key keeps both after the update. The transcription
            // key takes precedence over the rewording one for the same provider; the per-provider keys are
            // never cross-filled with the old global `api_key`, which belonged to a single configured
            // provider. The keyring migration that runs next ([migrateProviderKeyringIfNeeded]) refines the
            // *active* accounts (models, custom-host split) on top of this seed.
            var keyring = prefs.dictate.providerAccounts.get()
            keyring = keyring.seedAccount(
                providerId = "openai",
                apiKey = s.transcriptionApiKeyOpenai ?: s.rewordingApiKeyOpenai,
                transcriptionModel = s.transcriptionOpenaiModel,
                chatModel = s.rewordingOpenaiModel,
            )
            keyring = keyring.seedAccount(
                providerId = "groq",
                apiKey = s.transcriptionApiKeyGroq ?: s.rewordingApiKeyGroq,
                transcriptionModel = s.transcriptionGroqModel,
                chatModel = s.rewordingGroqModel,
            )
            keyring = keyring.seedAccount(
                providerId = ProviderAccount.LEGACY_CUSTOM_ID,
                apiKey = s.transcriptionApiKeyCustom ?: s.rewordingApiKeyCustom,
                transcriptionModel = s.transcriptionCustomModel,
                chatModel = s.rewordingCustomModel,
                customBaseUrl = s.transcriptionCustomHost ?: s.rewordingCustomHost,
            )
            prefs.dictate.providerAccounts.set(keyring)

            prefs.dictate.systemPromptSelection.set(s.systemPromptSelection)
            s.systemPromptCustomText?.let { prefs.dictate.systemPromptCustom.set(it) }
            prefs.dictate.stylePromptSelection.set(s.stylePromptSelection)
            s.stylePromptCustomText?.let { prefs.dictate.stylePromptCustom.set(it) }

            // --- Output behavior (roadmap section 10) ---
            prefs.dictate.autoEnter.set(s.autoEnter)
            prefs.dictate.instantOutput.set(s.instantOutput)
            prefs.dictate.outputSpeed.set(s.outputSpeed)
            prefs.dictate.resendButton.set(s.resendButton)

            // --- Recording capture toggles (roadmap 11.7). These were read from the legacy snapshot
            // but previously never written, so an upgrading user silently lost them. ---
            prefs.dictate.audioFocus.set(s.audioFocus)
            prefs.dictate.useBluetoothMic.set(s.useBluetoothMic)
            prefs.dictate.instantRecording.set(s.instantRecording)

            // --- Dictation languages (roadmap 11.7). The legacy value is an *unordered* StringSet, so
            // rebuild a stable order ("detect" first, then the rest sorted) and map the legacy active
            // index onto it as a best effort. Previously neither the selection nor the active language
            // was migrated, resetting the user back to the default {detect,en}. ---
            val orderedLanguages = buildList {
                if (s.inputLanguages.contains("detect")) add("detect")
                addAll(s.inputLanguages.filter { it != "detect" }.sorted())
            }
            if (orderedLanguages.isNotEmpty()) {
                prefs.dictate.inputLanguages.set(orderedLanguages.joinToString(","))
                prefs.dictate.activeInputLanguage.set(
                    orderedLanguages.getOrNull(s.inputLanguagePos) ?: orderedLanguages.first(),
                )
            }

            // --- App UI language (roadmap 11.7): legacy "system" maps to FlorisBoard's "auto". ---
            prefs.other.settingsLanguage.set(if (s.appLanguage == "system") "auto" else s.appLanguage)

            // --- Accent color: the legacy app had a single user-pickable accent (ARGB int, key
            // "net.devemperor.dictate.accent_color") that tinted the keyboard prompt UI. It was read
            // into the snapshot but never applied, so upgraders lost their familiar color. Carry it
            // over to both new accent prefs so the look stays identical: theme.accentColor drives the
            // keyboard (FlorisImeTheme), other.accentColor the settings app. ---
            val legacyAccent = Color(s.accentColor)
            prefs.theme.accentColor.set(legacyAccent)
            prefs.other.accentColor.set(legacyAccent)

            // --- Network proxy (roadmap 5.6): the legacy app stored one combined spec string
            // ("socks5|http://user:pass@host:port"); split it into the new structured fields. ---
            prefs.dictate.proxyEnabled.set(s.proxyEnabled)
            ProxyConfig.parse(s.proxyHost)?.let { proxy ->
                prefs.dictate.proxyType.set(
                    if (proxy.type == Proxy.Type.SOCKS) DictateProxyType.SOCKS5 else DictateProxyType.HTTP,
                )
                prefs.dictate.proxyHost.set(proxy.host)
                prefs.dictate.proxyPort.set(proxy.port.toString())
                proxy.username?.let { prefs.dictate.proxyUsername.set(it) }
                proxy.password?.let { prefs.dictate.proxyPassword.set(it) }
            }

            // --- Rate/donate nudges (roadmap 9.7/9.8): carry over the "handled" flags so users who
            // already rated/donated in the legacy app are never asked again. The old usage DB that
            // tracked total audio time was dropped, so the new counter simply starts at 0. ---
            prefs.dictate.hasRated.set(s.hasRatedInPlaystore)
            prefs.dictate.hasDonated.set(s.hasDonated)
        }

        prefs.dictate.legacyImported.set(true)
    }

    /**
     * On the first run of a fresh install, adds the device's system language to the dictation language
     * selection (on top of the default `{detect, en}`) so a non-English user can dictate in their own
     * language straight away without digging through settings. Only an *untouched* default selection is
     * augmented, so legacy upgraders (whose languages were already imported above) and users who have
     * customised the list are left alone. Idempotent via `prefs.dictate.inputLanguagesSeeded`.
     */
    suspend fun seedDeviceLanguageIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.inputLanguagesSeeded.get()) return
        if (prefs.dictate.inputLanguages.get() == "detect,en") {
            val device = DictateLanguages.matchDevice(Locale.getDefault())
            if (device != null && device.code != "en" && device.code != DictateLanguages.DETECT) {
                prefs.dictate.inputLanguages.set("detect,en,${device.code}")
            }
        }
        prefs.dictate.inputLanguagesSeeded.set(true)
    }

    /**
     * One-time fold of the deprecated flat credential prefs (api key, models, custom base URLs) into
     * the per-provider keyring ([ProviderAccounts]). Runs after [migrateIfNeeded], so it covers both
     * legacy-Java upgraders (whose flat prefs were just populated above) and existing fork users (who
     * already had flat prefs from an earlier build). Idempotent via `providerAccountsMigrated`.
     *
     * Each provider keeps one account holding its key plus separate transcription/chat models. If the
     * rewording side used a *different* custom host than the transcription side, it gets its own
     * `custom:<uuid>` account so the two base URLs don't collide.
     */
    @Suppress("DEPRECATION")
    suspend fun migrateProviderKeyringIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.providerAccountsMigrated.get()) return

        var keyring = prefs.dictate.providerAccounts.get()

        // --- Transcription side -> its active provider id ---
        val tProviderId = prefs.dictate.transcriptionProviderId.get()
        val tKey = prefs.dictate.apiKey.get()
        val tModel = prefs.dictate.transcriptionModel.get()
        val tBaseUrl = prefs.dictate.customBaseUrl.get()
        keyring = keyring.edit(tProviderId) { account ->
            account.copy(
                apiKey = tKey.ifBlank { account.apiKey },
                transcriptionModel = tModel.ifBlank { account.transcriptionModel },
                customBaseUrl = tBaseUrl.ifBlank { account.customBaseUrl },
            )
        }

        // --- Rewording side -> its active provider id (may equal the transcription one) ---
        var rProviderId = prefs.dictate.rewordingProviderId.get()
        val rKey = prefs.dictate.rewordingApiKey.get()
        val rModel = prefs.dictate.rewordingModel.get()
        val rBaseUrl = prefs.dictate.rewordingCustomBaseUrl.get()

        // If both sides are "custom" but point at different hosts, split the rewording one off into its
        // own custom account so each keeps its correct base URL.
        if (rProviderId == "custom" && tProviderId == "custom" &&
            rBaseUrl.isNotBlank() && tBaseUrl.isNotBlank() && rBaseUrl != tBaseUrl
        ) {
            val splitId = ProviderAccount.newCustomId()
            keyring = keyring.edit(splitId) { account ->
                account.copy(
                    apiKey = rKey.ifBlank { keyring.getOrEmpty("custom").apiKey },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl,
                )
            }
            rProviderId = splitId
            prefs.dictate.rewordingProviderId.set(splitId)
        } else {
            keyring = keyring.edit(rProviderId) { account ->
                account.copy(
                    // Blank legacy rewording key historically meant "reuse the transcription key".
                    apiKey = rKey.ifBlank { account.apiKey.ifBlank { if (rProviderId == tProviderId) tKey else account.apiKey } },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl.ifBlank { account.customBaseUrl },
                )
            }
        }

        prefs.dictate.providerAccounts.set(keyring)
        prefs.dictate.providerAccountsMigrated.set(true)
    }

    /**
     * Removes the live-prompt Smartbar action ([KeyCode.DICTATE_LIVE_PROMPT]) from the saved action
     * arrangement. The live prompt is now a chip inside the prompt panel/row, so it no longer ships as a
     * separate Smartbar button; this strips the action that the earlier injection added (and that the
     * old default placed). Idempotent via `prefs.dictate.livePromptActionRemoved`. Power users can still
     * re-add it manually from the Smartbar editor – the action itself is left intact.
     */
    suspend fun removeLivePromptActionIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.livePromptActionRemoved.get()) return
        removeActionIfPresent(KeyCode.DICTATE_LIVE_PROMPT)
        prefs.dictate.livePromptActionRemoved.set(true)
    }

    /**
     * Ensures the AI prompt-panel action ([KeyCode.DICTATE_PROMPTS]) is present in the saved arrangement.
     * Injected separately (own guard) so users who already ran the live-prompt migration still receive it.
     */
    suspend fun migratePromptsActionIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.promptsActionMigrated.get()) return
        ensureActionPresent(TextKeyData.DICTATE_PROMPTS, KeyCode.DICTATE_PROMPTS)
        prefs.dictate.promptsActionMigrated.set(true)
    }

    /**
     * One-time re-engagement reset for the 4.0.0 relaunch: re-offers the rate & donate nudges to
     * existing users. Many of them already acted on (or were long past) these prompts in an earlier
     * version, so the "handled" flags were set and/or their audio counter sat well beyond the
     * thresholds – meaning the nudges would never appear again. As the app has changed substantially,
     * we clear [Dictate.hasRated]/[Dictate.hasDonated] and reset [Dictate.totalAudioSeconds] so the
     * rate prompt (after [DictateController] RATE threshold) and then the donate prompt (after the
     * DONATE threshold) surface once more as the user dictates with the new version. For a brand-new
     * install this is a no-op (everything is already at its default). Idempotent via
     * `prefs.dictate.promoReengagementDone`, so it fires exactly once.
     */
    suspend fun reofferRateAndDonateIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.promoReengagementDone.get()) return
        prefs.dictate.hasRated.set(false)
        prefs.dictate.hasDonated.set(false)
        prefs.dictate.totalAudioSeconds.set(0L)
        prefs.dictate.promoReengagementDone.set(true)
    }

    /**
     * One-time switch to the always-on prompt ROW layout, now the default. Existing users who were on the
     * PANEL layout are moved to ROW once on update so the prompt chips are immediately visible; they can
     * switch back in settings. A brand-new install is already on ROW (the new default), so this is a no-op
     * for them. Idempotent via `prefs.dictate.promptsLayoutRowMigrated`.
     */
    /**
     * Puts an existing install onto the Sunrise theme, once.
     *
     * Defaults only apply to preferences that were never written, and any phone that has run this
     * app already has the upstream night theme on disk. Switching the default alone therefore
     * changes nothing for the only user there is. This flips it once and records that it did, so
     * choosing a different theme afterwards is respected.
     */
    suspend fun applySunriseThemeIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.maSunriseApplied.get()) return
        prefs.theme.dayThemeId.set(extCoreTheme("sunrise"))
        prefs.theme.nightThemeId.set(extCoreTheme("sunrise"))
        // Same reasoning for the command and arrow bars: a changed default never reaches a phone
        // that has already written the old value, so switch them on in the same one-shot pass.
        prefs.dictate.maCursorRow.set(true)
        prefs.dictate.maSunriseApplied.set(true)
    }

    /**
     * Drops the emoji key from the classic row, once.
     *
     * Its own flag on purpose. The Sunrise pass has already run on every phone carrying the previous
     * build, so folding this into it would mean the change never arrived anywhere. Only the emoji
     * entry is removed; whatever else the user has arranged is left exactly as it is.
     */
    /**
     * Loads the key backup at startup, when there are no keys at all.
     *
     * This used to happen only on the API keys screen, which meant a fresh install had an empty
     * keyring until that screen happened to be opened. The keyboard would say "no API key set" while
     * a perfectly good backup sat in Documents, and opening the screen and pressing test appeared to
     * fix it. Nothing was being fixed; the screen was simply where the restore lived.
     *
     * Startup is the right place. The keys are in memory before the first recording can be started,
     * so there is nothing to fiddle with and nothing to refresh.
     *
     * Runs whenever the keyring is empty rather than once ever, because a restore that quietly gives
     * up after one failed attempt is worse than useless: the one attempt is likely to be the boot
     * where all-files access had not been granted yet.
     */
    suspend fun restoreKeysFromVaultIfEmpty(context: Context) {
        val prefs by FlorisPreferenceStore
        val accounts = prefs.dictate.providerAccounts.get()
        if (accounts.accounts.values.any { it.apiKey.isNotBlank() }) return
        val text = MaVault.read() ?: return
        if (text.isBlank()) return
        val result = MaKeyImport.importAll(text, accounts)
        if (result.added > 0) {
            prefs.dictate.providerAccounts.set(result.accounts)
            // Point the two roles at the providers that do them here, but only where a key actually
            // arrived, so a partial backup never leaves the app aimed at an empty provider.
            if (result.accounts.accounts["assemblyai"]?.apiKey.orEmpty().isNotBlank()) {
                prefs.dictate.transcriptionProviderId.set("assemblyai")
            }
            if (result.accounts.accounts["anthropic"]?.apiKey.orEmpty().isNotBlank()) {
                prefs.dictate.rewordingProviderId.set("anthropic")
            }
        }
    }

    suspend fun applyRowV2IfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.maRowV2Applied.get()) return
        val row = prefs.dictate.legacyActionRow.get()
        val entries = row.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "EMOJI" }
        // History goes in where emoji was, unless it is already somewhere in the row: a changed
        // default never reaches an install that has written its own, and the whole point of this
        // pass is to carry these two edits across to the phone that already exists.
        val withHistory = if (entries.contains("HISTORY")) {
            entries
        } else {
            val at = entries.indexOf("NUMBERS")
            if (at >= 0) entries.toMutableList().apply { add(at, "HISTORY") } else entries + "HISTORY"
        }
        // Reordered into its two groups, keeping only the entries the user actually has, so anyone
        // who removed a key does not get it back and anyone who added one keeps it at the end.
        val preferred = listOf("UNDO", "REDO", "SELECT_ALL", "CUT", "COPY", "PASTE", "HISTORY", "NUMBERS")
        val grouped = preferred.filter { it in withHistory } + withHistory.filterNot { it in preferred }
        prefs.dictate.legacyActionRow.set(grouped.joinToString(","))
        // Audio retention on, once, for the same reason as the row edits: a changed default cannot
        // reach a preference an existing install has already written, and without the audio the
        // archive cannot re-transcribe anything.
        prefs.dictate.historyAudioRetention.set(true)
        // Microphone to the right, expand arrow to the left. The sticky action renders last when
        // toggles are unflipped, and the microphone is the sticky action, so this is the one flag
        // that puts each on the side it is wanted.
        prefs.smartbar.flipToggles.set(false)
        // The smiley beside the space bar goes, on installs as well as fresh ones.
        prefs.keyboard.utilityKeyEnabled.set(false)
        // And the accent itself, which is the thing that was really keeping the enter key orange.
        prefs.theme.accentColor.set(androidx.compose.ui.graphics.Color(0xFFE8B15C))
        // Move an install that never chose a case rule onto the length-aware one. Anyone who picked
        // lower or upper on purpose keeps it.
        // Back to leaving transcripts alone. The automatic rule was briefly the default and is now
        // opt-in, so an install carrying it from that window is moved back rather than left with a
        // behaviour that was never chosen.
        if (prefs.dictate.maTextCase.get() == "auto") {
            prefs.dictate.maTextCase.set("none")
        }
        // Strip retired actions out of the SAVED arrangement, not just the default. This is the
        // trap that keeps catching me: a changed default never reaches a preference that has already
        // been written, so one-handed mode kept appearing in the panel long after it left the code.
        val retired = setOf(
            // One-handed mode and the floating window are gone from the code entirely, so their
            // codes are gone too and cannot be named here. They are stripped from saved
            // arrangements by number in applyRemovalV15IfNeeded instead.
            KeyCode.LANGUAGE_SWITCH,
            KeyCode.IME_UI_MODE_MEDIA,
            KeyCode.IME_UI_MODE_GIF,
            KeyCode.TOGGLE_INCOGNITO_MODE,
            KeyCode.ARROW_UP,
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.FORWARD_DELETE,
        )
        fun keeps(a: QuickAction): Boolean =
            (a as? QuickAction.InsertKey)?.data?.code !in retired
        val arrangement = prefs.smartbar.actionArrangement.get()
        val cleaned = arrangement.copy(
            stickyAction = arrangement.stickyAction?.takeIf { keeps(it) },
            dynamicActions = arrangement.dynamicActions.filter { keeps(it) },
            hiddenActions = arrangement.hiddenActions.filter { keeps(it) },
        )
        if (cleaned != arrangement) {
            prefs.smartbar.actionArrangement.set(cleaned)
        }
        // NUMBERS leaves the action row and KEYBOARD takes its place, so the key that returns to
        // typing sits under the microphone that comes the other way. Numbers moved to the slot the
        // keyboard key vacated at the bottom left.
        val rowNow = prefs.dictate.legacyActionRow.get()
        if (rowNow.contains("NUMBERS")) {
            prefs.dictate.legacyActionRow.set(rowNow.replace("NUMBERS", "KEYBOARD"))
        }
        // Move the space bar long press over, once, but only from the old default. Anyone who chose
        // their own action for it keeps it; a changed default cannot reach a preference that has
        // already been written, which is why this pass exists at all.
        // Space bar long press lands on the layout picker. Moved from either of the two defaults it
        // has had, and only from those: anyone who chose their own action for it keeps it.
        val spaceLong = prefs.gestures.spaceBarLongPress.get()
        if (spaceLong == SwipeAction.SHOW_INPUT_METHOD_PICKER ||
            spaceLong == SwipeAction.MA_SWITCH_DICTATE_VIEW
        ) {
            prefs.gestures.spaceBarLongPress.set(SwipeAction.SHOW_SUBTYPE_PICKER)
        }
        // The built-in numeric row is retired; the multi-purpose row occupies that slot now. Turning
        // the old preference off keeps the hinted-digits setting, which is gated on it, available.
        prefs.keyboard.numberRow.set(false)
        // The fork's starter prompts give way to Marko's own, once, and only on an install where
        // nobody has edited them. Someone's own prompts are not ours to overwrite.
        runCatching {
            PromptsDatabaseHelper.getInstance(context).replaceStarterSetIfUntouched()
        }
        // The transcribe view becomes the main one. Only set when nothing is pinned, so an explicit
        prefs.dictate.maRowV2Applied.set(true)
    }

    /**
     * Puts the new Menu Macro switches into an arrangement that has already been written.
     *
     * Its own flag rather than a bump of the row one, because bumping that key re-runs everything in
     * that pass, including the accent colour and the action row order, and a phone that has since
     * been set up by hand would quietly lose those choices. Each batch of "change something a user
     * may already have written" gets its own flag; that is the rule the earlier passes set and it is
     * the right one.
     *
     * Only inserts what is missing, and only into the visible list, so anything deliberately hidden
     * stays hidden and anything already placed stays where it was put.
     */
    suspend fun applyPanelV14IfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.maPanelV14Applied.get()) return
        val wanted = listOf(
            TextKeyData.MA_TOGGLE_EDIT_ROW,
            TextKeyData.MA_ROW_DIGITS,
            TextKeyData.MA_ROW_DIACRITICS,
            TextKeyData.MA_ROW_EDITING,
        )
        val arrangement = prefs.smartbar.actionArrangement.get()
        val placed = arrangement.dynamicActions + arrangement.hiddenActions +
            listOfNotNull(arrangement.stickyAction)
        val present: Set<Int> = placed
            .mapNotNull { (it as? QuickAction.InsertKey)?.data?.code }
            .toSet()
        val missing = wanted.filter { it.code !in present }.map { QuickAction.InsertKey(it) }
        if (missing.isNotEmpty()) {
            prefs.smartbar.actionArrangement.set(
                arrangement.copy(dynamicActions = arrangement.dynamicActions + missing),
            )
        }
        prefs.dictate.maPanelV14Applied.set(true)
    }

    /**
     * Strips the one-handed and floating window buttons out of an arrangement already written.
     *
     * By number, not by name, because the constants are gone with the features. -109 was the
     * floating window, -110 the one-handed layout, and -111 and -112 its two nudge keys.
     *
     * The reason these kept coming back is worth recording. One pass was removing them and an
     * upstream preference migration was adding the floating one straight back on every run, so the
     * removal looked like it had simply never worked. That adder is deleted; this clears whatever it
     * left behind.
     */
    suspend fun applyRemovalV15IfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.maRemovalV15Applied.get()) return
        val gone = setOf(-109, -110, -111, -112, -113, -114)
        fun keeps(a: QuickAction): Boolean =
            (a as? QuickAction.InsertKey)?.data?.code !in gone
        val arrangement = prefs.smartbar.actionArrangement.get()
        val cleaned = arrangement.copy(
            stickyAction = arrangement.stickyAction?.takeIf { keeps(it) },
            dynamicActions = arrangement.dynamicActions.filter { keeps(it) },
            hiddenActions = arrangement.hiddenActions.filter { keeps(it) },
        )
        if (cleaned != arrangement) {
            prefs.smartbar.actionArrangement.set(cleaned)
        }
        prefs.dictate.maRemovalV15Applied.set(true)
    }

    /**
     * Puts Croatian and English on the keyboard, once, without being asked.
     *
     * This app is Croatian and English. Shipping with no subtypes configured meant a yellow warning
     * in Settings, a fallback to English QWERTY, and a language switch with nothing to switch to.
     *
     * The two are built here from literal values rather than read out of the bundled subtype
     * presets, and that is the whole fix rather than a style choice. Migrations run while the
     * preference store is loading, and the extension manager that parses those presets is only
     * started afterwards, so the preset list was reliably empty at this moment and the pass added
     * nothing at all while reporting itself done. That is why the picker still opened on a single
     * language. These values are copied from the presets in org.florisboard.localization: Croatian
     * is qwertz with the euro and the hr popup mapping, English is qwerty with the dollar and the en
     * one.
     *
     * The dictation language list is narrowed to the same two, so the transcribe view's row and the
     * badge in the suggestion strip offer exactly the same choice.
     *
     * Only ever adds. Anything already configured stays, because a subtype added by hand is not ours
     * to delete on an upgrade, and addSubtype itself ignores an exact duplicate.
     */
    suspend fun applyLanguagesV16IfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.maLanguagesV16Applied.get()) return
        val subtypeManager by context.subtypeManager()
        val wanted = listOf(
            Subtype(
                id = -1,
                primaryLocale = FlorisLocale.from("hr"),
                secondaryLocales = emptyList(),
                composer = extCoreComposer("appender"),
                currencySet = extCoreCurrencySet("euro"),
                punctuationRule = extCorePunctuationRule("default"),
                popupMapping = extCorePopupMapping("hr"),
                layoutMap = SubtypeLayoutMap(characters = extCoreLayout("qwertz")),
            ),
            Subtype(
                id = -1,
                primaryLocale = FlorisLocale.from("en", "US"),
                secondaryLocales = emptyList(),
                composer = extCoreComposer("appender"),
                currencySet = extCoreCurrencySet("dollar"),
                punctuationRule = extCorePunctuationRule("default"),
                popupMapping = extCorePopupMapping("en"),
                layoutMap = SubtypeLayoutMap(characters = extCoreLayout("qwerty")),
            ),
        )
        for (subtype in wanted) {
            val alreadyThere = subtypeManager.subtypes.any {
                it.primaryLocale.language.equals(subtype.primaryLocale.language, ignoreCase = true)
            }
            if (!alreadyThere) subtypeManager.addSubtype(subtype)
        }
        // The two, in the order the toggle moves through them, and nothing else.
        prefs.dictate.inputLanguages.set("${MaLanguage.HR},${MaLanguage.EN}")
        val current = prefs.dictate.activeInputLanguage.get().substringBefore('-').lowercase()
        if (current != MaLanguage.HR && current != MaLanguage.EN) {
            prefs.dictate.activeInputLanguage.set(MaLanguage.HR)
        }
        prefs.dictate.maLanguagesV16Applied.set(true)
    }

    suspend fun migratePromptsLayoutToRowIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.promptsLayoutRowMigrated.get()) return
        prefs.dictate.promptsLayout.set(DictatePromptsLayout.ROW)
        prefs.dictate.promptsLayoutRowMigrated.set(true)
    }

    /**
     * Injects [keyData] at the front of the saved dynamic action row unless an action with [code] is
     * already present anywhere in the arrangement. New defaults do not retroactively merge into a
     * persisted arrangement, so without this an upgrading user could never see/place the action.
     */
    private suspend fun ensureActionPresent(keyData: TextKeyData, code: Int) {
        val prefs by FlorisPreferenceStore
        val arrangement = prefs.smartbar.actionArrangement.get()
        val alreadyPresent = arrangement.run { dynamicActions + hiddenActions + listOfNotNull(stickyAction) }
            .any { it.keyData().code == code }
        if (!alreadyPresent) {
            val action = QuickAction.InsertKey(keyData)
            prefs.smartbar.actionArrangement.set(
                arrangement.copy(dynamicActions = listOf(action) + arrangement.dynamicActions),
            )
        }
    }

    /**
     * Strips every action with [code] from the saved arrangement (sticky/dynamic/hidden). No-op if it is
     * not present, so it is safe to run unconditionally behind a one-time guard.
     */
    private suspend fun removeActionIfPresent(code: Int) {
        val prefs by FlorisPreferenceStore
        val arrangement = prefs.smartbar.actionArrangement.get()
        val matches = { action: QuickAction -> action.keyData().code == code }
        val present = (arrangement.dynamicActions + arrangement.hiddenActions +
            listOfNotNull(arrangement.stickyAction)).any(matches)
        if (!present) return
        prefs.smartbar.actionArrangement.set(
            arrangement.copy(
                stickyAction = arrangement.stickyAction?.takeUnless(matches),
                dynamicActions = arrangement.dynamicActions.filterNot(matches),
                hiddenActions = arrangement.hiddenActions.filterNot(matches),
            ),
        )
    }

    /**
     * Folds a single legacy provider's stored credentials into [this] keyring. No-op (returns the keyring
     * unchanged) when there is no usable key, so providers the user never configured don't create empty
     * accounts. Existing non-blank fields are never overwritten, making it safe to run before the active
     * provider's flat prefs are folded in by [migrateProviderKeyringIfNeeded].
     */
    private fun ProviderAccounts.seedAccount(
        providerId: String,
        apiKey: String?,
        transcriptionModel: String? = null,
        chatModel: String? = null,
        customBaseUrl: String? = null,
    ): ProviderAccounts {
        val key = apiKey?.takeIf { it.isNotBlank() && it != "NO_API_KEY" } ?: return this
        return edit(providerId) { account ->
            account.copy(
                apiKey = account.apiKey.ifBlank { key },
                transcriptionModel = account.transcriptionModel.ifBlank { transcriptionModel.orEmpty() },
                chatModel = account.chatModel.ifBlank { chatModel.orEmpty() },
                customBaseUrl = account.customBaseUrl.ifBlank { customBaseUrl.orEmpty() },
            )
        }
    }
}
