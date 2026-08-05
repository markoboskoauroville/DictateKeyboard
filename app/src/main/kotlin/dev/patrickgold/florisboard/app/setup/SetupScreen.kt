/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.setup

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.MaVault
import dev.patrickgold.florisboard.dictate.provider.MaKeys
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreenScope
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.FlorisStep
import org.florisboard.lib.compose.FlorisStepLayout
import org.florisboard.lib.compose.FlorisStepLayoutScope
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.stringRes

/** The provider recommended to new (non-technical) users: fast and free for everyday dictation. */
private const val RECOMMENDED_PROVIDER_ID = "groq"

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()

    // Dictate onboarding: the active transcription provider must have a usable key (or be keyless)
    // before the user can dictate. This drives the new "Connect a free AI service" step.
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val activeProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
    val isProviderConfigured = isProviderConfigured(accounts, activeProviderId)
    var providerSkipped by rememberSaveable { mutableStateOf(false) }
    // The floating-button step is optional and has no completion signal of its own, so (like the
    // provider step) a flag lets the user move past it to the final page once they've decided.
    // Mantra Voice Type: starts passed, so setup never shows the floating-button page. The bubble
    // was removed, and a wizard step offering a feature that no longer exists is worse than no step.
    var floatingButtonStepPassed by rememberSaveable { mutableStateOf(true) }

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                if (isGranted) {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.GRANTED)
                } else {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                }
            }
        }

    var isMicGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            isMicGranted = isGranted
        }

    content(
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
        isMicGranted,
        isProviderConfigured,
        providerSkipped,
        { providerSkipped = true },
        floatingButtonStepPassed,
        { floatingButtonStepPassed = true },
        accounts,
        context,
        navController,
        requestNotification,
        requestMic,
        hasNotificationPermission,
        scope,
    )
}

/** Reads the current clipboard text (used to paste an API key without opening the on-screen keyboard). */
private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    return cm.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

/** Masks an API key for on-screen confirmation, e.g. "gsk_…AB12" (keeps the ends, hides the middle). */
private fun maskKey(key: String): String =
    if (key.length > 8) "${key.take(4)}…${key.takeLast(4)}" else "•".repeat(key.length)

/** True once the active transcription provider has a saved key, or is a keyless endpoint (Ollama). */
private fun isProviderConfigured(accounts: ProviderAccounts, providerId: String): Boolean {
    if (accounts.getOrEmpty(providerId).hasKey) return true
    val preset = ProviderRegistry.byId(providerId)
    return preset != null && preset.apiKeyUrl == null
}

@Composable
private fun FlorisScreenScope.content(
    isFlorisBoardEnabled: Boolean,
    isFlorisBoardSelected: Boolean,
    isMicGranted: Boolean,
    isProviderConfigured: Boolean,
    providerSkipped: Boolean,
    onSkipProvider: () -> Unit,
    floatingButtonStepPassed: Boolean,
    onPassFloatingButton: () -> Unit,
    accounts: ProviderAccounts,
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    requestMic: ManagedActivityResultLauncher<String, Boolean>,
    hasNotificationPermission: NotificationPermissionState,
    scope: CoroutineScope,
) {

    fun targetStep(): Int = when {
        !isFlorisBoardEnabled -> Steps.EnableIme.id
        !isFlorisBoardSelected -> Steps.SelectIme.id
        !isMicGranted -> Steps.GrantMicPermission.id
        !isProviderConfigured && !providerSkipped -> Steps.SetUpProvider.id
        else -> Steps.FinishUp.id
    }

    val stepState = rememberSaveable(saver = FlorisStepState.Saver) {
        FlorisStepState.new(init = targetStep())
    }

    content {
        LaunchedEffect(
            isFlorisBoardEnabled, isFlorisBoardSelected, isMicGranted,
            hasNotificationPermission, isProviderConfigured, providerSkipped,
            floatingButtonStepPassed,
        ) {
            stepState.setCurrentAuto(targetStep())
        }

        // Below block allows to return from the system IME enabler activity
        // as soon as it gets selected.
        LaunchedEffect(Unit) {
            while (true) {
                delay(200L)
                val isEnabled = InputMethodUtils.isFlorisboardEnabled(context)
                if (stepState.getCurrentAuto().value == Steps.EnableIme.id &&
                    stepState.getCurrentManual().value == -1 &&
                    !isFlorisBoardEnabled &&
                    !isFlorisBoardSelected &&
                    hasNotificationPermission == NotificationPermissionState.NOT_SET &&
                    isEnabled
                ) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
            }
        }
        FlorisStepLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            stepState = stepState,
            backLabel = stringRes(R.string.setup__nav_back),
            nextLabel = stringRes(R.string.setup__nav_next),
            header = {
                StepText(stringRes(R.string.setup__intro_message))
                Spacer(modifier = Modifier.height(16.dp))
            },
            steps = steps(
                context, navController, requestNotification, requestMic,
                isProviderConfigured, onSkipProvider, onPassFloatingButton, accounts, scope,
            ),
            footer = {
                footer(context)
            },
        )
    }
}

@Composable
private fun footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy))
        }
        FlorisBulletSpacer()
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository))
        }
    }
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    requestMic: ManagedActivityResultLauncher<String, Boolean>,
    isProviderConfigured: Boolean,
    onSkipProvider: () -> Unit,
    onPassFloatingButton: () -> Unit,
    accounts: ProviderAccounts,
    scope: CoroutineScope,
): List<FlorisStep> {

    // Persists the entered key into the keyring and points the active transcription (and, where the
    // provider also supports chat, rewording) provider at it. Done in this scope so the step composable
    // stays free of preference plumbing.
    fun saveKey(providerId: String, key: String) {
        scope.launch {
            this@steps.prefs.dictate.providerAccounts.set(
                accounts.edit(providerId) { it.copy(apiKey = key.trim()) }
            )
            this@steps.prefs.dictate.transcriptionProviderId.set(providerId)
            if (ProviderRegistry.byId(providerId)?.capabilities?.chat == true) {
                this@steps.prefs.dictate.rewordingProviderId.set(providerId)
            }
        }
    }

    return listOfNotNull(
        FlorisStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        FlorisStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        FlorisStep(
            id = Steps.GrantMicPermission.id,
            title = stringRes(R.string.setup__grant_mic_permission__title),
        ) {
            StepText(stringRes(R.string.setup__grant_mic_permission__description))
            StepButton(stringRes(R.string.setup__grant_mic_permission__btn)) {
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        FlorisStep(
            id = Steps.SetUpProvider.id,
            title = stringRes(R.string.setup__provider__title),
        ) {
            ProviderSetupStep(
                onSaveKey = ::saveKey,
                onSkip = onSkipProvider,
            )
        },
        FlorisStep(
            id = Steps.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
        ) {
            StepText(stringRes(R.string.setup__finish_up__description_p1))
            StepText(stringRes(R.string.setup__finish_up__description_p2))
            if (!isProviderConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                StepText(
                    text = stringRes(R.string.setup__finish_up__add_key_hint),
                    fontStyle = FontStyle.Italic,
                )
            }
            StepButton(label = stringRes(R.string.setup__finish_up__finish_btn)) {
                scope.launch { this@steps.prefs.internal.isImeSetUp.set(true) }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            }
        }
    )
}

/**
 * The key step: three file pickers, nothing else.
 *
 * What was here before was written for a stranger who has never heard of an API key: a recommended
 * free provider, a five-point guide to signing up for it, a sign-up link and a paste box. None of
 * that applies. The keys already exist in a file on the phone, and the three providers this build
 * actually uses are fixed, so the whole step is three buttons that read that file.
 *
 * Each picker parses the same file for its own provider, so one file holding all three keys can be
 * picked three times, or a separate file used for each. Importing twice adds nothing.
 */
@Composable
private fun FlorisStepLayoutScope.ProviderSetupStep(
    onSaveKey: (providerId: String, key: String) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    StepText(
        "Point each provider at your keys file. The same file can be used for all three, since " +
            "each one takes only the keys that belong to it."
    )
    Spacer(modifier = Modifier.height(8.dp))

    MaSetupKeyPicker("assemblyai", "AssemblyAI", "transcription", onSaveKey)
    MaSetupKeyPicker("anthropic", "Anthropic Claude", "rewording", onSaveKey)
    MaSetupKeyPicker("gemini", "Google Gemini", "optional", onSaveKey)

    TextButton(
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        onClick = onSkip,
    ) {
        Text(
            text = "Set up later",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One provider's picker. Reports how many keys it took, so the step gives the same feedback the key
 * manager does rather than silently succeeding.
 */
@Composable
private fun FlorisStepLayoutScope.MaSetupKeyPicker(
    providerId: String,
    label: String,
    role: String,
    onSaveKey: (providerId: String, key: String) -> Unit,
) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
            }.getOrNull().orEmpty()
            val keys = MaKeys.extract(text, providerId)
            if (keys.isEmpty()) {
                note = MaKeys.mismatchWarning(text, providerId, keys)
                    ?: "No $label key found in that file"
            } else {
                onSaveKey(providerId, MaKeys.join(keys))
                MaVault.write(text)
                note = if (keys.size == 1) "1 key imported" else "${keys.size} keys imported"
            }
        }
    }
    StepButton(label = "$label ($role)") {
        picker.launch(arrayOf("*/*"))
    }
    if (note.isNotEmpty()) {
        StepText(note)
    }
}

/**
 * Four steps, down from seven.
 *
 * The crash-report notification step is gone: nothing posts crash notifications any more, so asking
 * for the permission was asking for nothing. The floating-button step is gone too, since the
 * floating bubble itself was removed from this build and the step was still advertising it.
 */
private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object GrantMicPermission : Steps(id = 3)
    data object SetUpProvider : Steps(id = 4)
    data object FinishUp : Steps(id = 5)
}
