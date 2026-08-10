# TTT (light) — handoff for the first session

**Read this whole document before writing any code.** Written by the session that built the features
being removed. Everything about the codebase here was checked against the repository, not remembered.

---

## 1. What is being built

**TTT (light).** A stripped fork of TTT&LLL: **voice dictation into any text field, and nothing else.**
Same keyboard, same aesthetic, same key ring, same providers. Everything that is not typing or
dictating comes out.

Marko Boško, Mantra Productions. Dictates by voice, works on a phone, low vision and dyslexia. Expects
autonomous execution: push to main, let CI build, hand him the link. No check-in questions.

**This one IS a fork**, unlike AI Reader. The starting point is nine tenths of the finished product,
and the work is deletion. Clone `markoboskoauroville/DictateKeyboard`, push to a new repository, and
start cutting.

Suggested identity, all of which must be decided once and never changed:

- Repository: `TTT_LIGHT`
- Package id: `com.mantraproductions.tttlight` — **must differ from `com.mantraproductions.voicetype`**
  or it installs over the full app and replaces it. This is the single most damaging thing to get
  wrong, because it silently destroys his working keyboard.
- APK name: `ttt-light-build-N.apk`. **No ampersand in the filename** — legal in a URL only when
  escaped, and a link that needs escaping breaks when pasted into a chat.
- A **new signing keystore**, its own repository secret. Different app, different key.

---

## 2. What comes out

In the order they are easiest to remove.

### 2.1 The reader (LLL), entirely

The whole point of the sister app AI Reader, and dead weight here.

- `app/.../dictate/reader/` — `MaReader.kt`, `MaReaderLayout.kt`, `MaPcm.kt`
- `ImeUiMode.READER` and its branch in `ImeWindow.kt` (~line 286)
- The **book key** from the feature row: `MaFeatureKey.BOOK` and its branch in `MaFeatureRow.kt`
- The **engine submodule** `engine-repo` / `MA_READER_ENGINE`, its `include(":engine")` lines in
  `settings.gradle.kts`, and `submodules: recursive` in the CI checkout
- `MaSpeechify.kt` and the Speechify preset — its only consumer was the reader's voice
- `MaVision.kt`, `MaScreenshot.kt`, the Groq preset, and the `READ_MEDIA_IMAGES` permission — the
  screenshot reader fed the reader and nothing else
- `lll__*` strings

Once the engine submodule is gone the build gets simpler and faster, and CI no longer fails on an
empty module directory when someone forgets a checkout flag.

### 2.2 The Little Man AI Assistant

- `MaLittleMan.kt`, `MaLittleManTrain.kt`, `MaLivePrompts.kt`
- `DictatePromptStrip.kt` — both `DictatePromptRow` and `DictatePromptStrip`
- `DictateInputLayout.kt` and `ImeUiMode.DICTATE` (the prompt panel) with its branch in `ImeWindow.kt`
- `DictatePromptsLayout`, `promptsLayout`, `maShowPrompts`, `livePromptActive`, `pendingPrompts`,
  `applyRememberedPrompt`, `startLivePrompt`, `togglePendingPrompt`
- `DictateLittleManScreen.kt`, `DictatePromptsScreen.kt`, and the prompts database
- `MaFeatureKey.LITTLE_MAN`
- The prompt-strip terms in `maSmartbarHasContent()` and in `FlorisImeSizing`'s height arithmetic

**Rewording is a separate question.** `applyPrompt` and the rewording model are what the Little Man
*used*, not what he *was*. Selecting text and rewording it is plain dictation-adjacent editing and
probably stays. **Ask Marko.** If it goes, `rewordingModel` and the Anthropic/Gemini presets go too.

### 2.3 db

The dashboard panel and its editor: `DbEditorScreen.kt`, its route, its settings entry, and the panel
itself.

**Do not grep for `db` alone.** `maDb`, `maDbColour` and `db > -3f` in `MaRecordMeter.kt` are decibels
in the recording level meter and must stay. Delete by file and by route, never by string match.

### 2.4 Gestures

FlorisBoard's own gesture settings screen, on the FlorisBoard tab. Route, screen, and the glide typing
machinery behind it if it is cleanly separable. **Keep Smartbar** — Marko asked what it was and then
said keep it. It is the strip above the keys that shows word suggestions and, in this app, the
recording bar. It is load-bearing for dictation: the red lamp, the timer, `ENG`/`HR` and `FAST`/`SLOW`
all live there.

### 2.5 The transcribe view — **read §3 before removing this**

The second view: `LegacyDictateLayout.kt` (1,543 lines), `MaQuickRow.kt`, `MaCursorRow.kt`,
`MaExtraRow.kt`, `MaKeyboards.kt`, `AudioReactiveCloudOrbView.kt`, `DictateHoldTargets.kt`,
`DictateHoldTouch.kt`, `ImeUiMode.TRANSCRIBE` and its branch in `ImeWindow.kt`.

This is the largest single deletion in the fork, about 3,200 lines.

---

## 3. The one decision that needs Marko before you cut

**Removing the transcribe view removes every on-screen way to start a recording.**

Marko's reasoning is right as far as it goes: with one view, a key whose job is *switching views* has
no job. But that key is currently doing two things at once, and only one of them is switching.

Here is what recording depends on today:

| Route to start a recording | Where it lives | Survives the cut? |
|---|---|---|
| Volume up | hardware key, `FlorisImeService` | **yes** |
| Microphone key on the feature row | switches to the transcribe view | **no** |
| Record button inside the transcribe view | that view | **no** |

So the light fork as literally specified can only start a recording with **volume up**. That is one
route, on a hardware key, and it fails on a phone with a broken volume rocker, in a case that covers
it, or when a bluetooth headset grabs the volume stream.

The whole reason `MIC` is in `MaFeatureOrder.ALWAYS_ON` — the three keys the feature row editor
refuses to let anyone switch off — is that it is the only on-screen route to dictation.

**Recommendation: keep the microphone key on the feature row and repurpose it.** Instead of
`imeUiMode = TRANSCRIBE` it calls `DictateController.onMicClick(context)` — start when idle, stop and
send when recording. Same key, same position his thumb already knows, and it becomes the record button
rather than a door to a room that no longer exists. Keep it in `ALWAYS_ON` for the same reason as
before.

Everything the transcribe view offered around recording already exists in the keyboard view: the red
lamp, the timer, the live level meter, `ENG`/`HR`, `FAST`/`SLOW`, cancel on volume down. That work was
done in builds 147 to 154 and it is what makes this deletion possible at all.

**Confirm with Marko before deleting the mic key.** It is one question and it is cheaper than shipping
a keyboard that cannot dictate when the volume rocker fails.

---

## 4. What stays

The whole point of the fork is that this list is short and solid.

- **The keyboard**: layouts, the feature row and its editor, the copy row, zones one/two/three folding
- **The smartbar**: suggestions, and the recording bar with the lamp, timer, meter and the two toggles
- **Dictation end to end**: `DictateController`, `RecordingController`, resample to 16 kHz mono WAV,
  AssemblyAI async and sync, the FAST/SLOW decision, history, recovered recordings
- **The key ring**: `MaKeyRing`, `MaKeyRingStore`, `MaKeys`, `MaKeyImport`, the key manager screen
- **The usage ledger**: `MaUsage`, `MaUsageStore`
- **Settings**: the Mantra tab, its flat ordered list, the settings order editor, opening view
- **Volume keys**: up starts and sends, down cancels a recording or switches language
- **Croatian and English only.** Permanent, and re-decided twice already

Providers left after the cut: **AssemblyAI** for transcription, and whatever rewording needs if
rewording stays. Delete Speechify, Groq, and anything else from `ProviderRegistry.presets`. A provider
list offering things the app cannot use is a list that has to be read.

---

## 5. Order of work

1. **Clone, rename, repoint.** New repo, new package id, new keystore secret, new APK name, CI green
   with nothing removed yet. **Install it alongside the full app and confirm both survive.** Do this
   first; if the package id is wrong you want to know now, not after a day of deletions.
2. **Reader out.** Cleanest cut, no entanglement, and it removes the submodule.
3. **db out**, then **gestures out**. Small and independent.
4. **Little Man out.** Touches the smartbar's visibility rule and the height arithmetic — expect one
   CI failure and read the log.
5. **Ask about the mic key**, then the transcribe view out. Biggest cut, last, once everything else is
   proven.
6. **Prune the settings list and the provider registry** to what remains.
7. **Update `HANDOFF.md`** to describe this app, not its parent. A handoff describing features that no
   longer exist is worse than none.

---

## 6. Practices carried over, and traps

### Verify locally — there is no Android compiler in the sandbox

Download the Kotlin compiler and the jars, compile the pure-Kotlin files and run their tests in a
plain JVM. About a minute, and it is why several builds went green first time. Android files cannot be
checked this way, so expect roughly one CI failure per non-trivial UI change: **read the log, never
guess.**

### Traps that each cost a real build

- **Deleting by line leaves annotations stranded.** Removing a route by matching its line left its
  `@Serializable` and `@Deeplink` above the *next* route, which then had two of each. The error named
  a route nobody had touched. After any deletion, check for dangling annotations.
- **Never test for an import with `in`.** `"…runtime.remember"` is a substring of
  `"…runtime.rememberCoroutineScope"`. The check reports present on something absent, and the missing
  import cascades into ten errors about composable context, none naming the cause.
- **Removing a block strands the line that used it.** An import goes with the deleted code, one line
  that used it stays, and the compiler says "cannot infer type parameter R".
- **A Kotlin object initialises top to bottom.** A property whose initialiser calls a function reading
  a `val` declared below it sees null and throws on first touch. Nothing warns.
- **A nullable property from another Gradle module will not smart-cast.** Pull it into a local first.
- **Check every icon against the repo.** `Icons.Default.KeyboardReturn` does not exist; it is
  `AutoMirrored`. Zero existing usages is the signal.
- **Never put a changing collection in a `pointerInput` key** — the input restarts and cancels the
  gesture. Symptom: a drag that moves one place and dies.
- **Never write a fake key beginning `sk_live_` or `sk_test_`.** Stripe's prefixes; push protection
  rejected a build over two invented strings in a test file.

### Repository rules

- **`git commit -F`, never `-m`.** Prose, about why.
- **Update the handoff in the same push as the code.** It fell sixteen builds behind once.
- **Two releases only**, pruned by CI. One is too few: a link dies the moment the next push lands.
- **`versionCode = 1000 + run_number`**, offset permanent, never lowered. About subtracts it back.
- **Colour is state, never decoration. Nothing pulses or breathes.** Warm dark, sand ink `0xFFE8B15C`,
  recording red `0xFF9B3B33`, warning amber `0xFFF0883E`.

---

## 7. One small thing to fix in the parent app

The Feature row settings entry still reads *"Drag the nine keys into the order you want"*. There are
ten now — the Little Man's key was added at build 159. In this fork it will be eight, or nine if the
microphone stays. **Do not hardcode the number again**; derive it, or drop it from the sentence.
