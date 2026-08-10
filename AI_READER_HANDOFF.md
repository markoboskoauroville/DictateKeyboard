# AI Reader — handoff for the first session

**Read this whole document before writing any code.** It is written for a session that has never seen
the TTT&LLL codebase, by the session that built the reader inside it. Everything here about that
codebase was checked against the repository, not remembered.

---

## 1. What is being built

**AI Reader.** A standalone Android app that reads text aloud and lights each word as it is spoken.
A sister app to TTT&LLL, sharing its aesthetic and its reading engine, sharing none of its keyboard.

Marko Boško, Mantra Productions. He dictates by voice, works on a phone, has low vision and dyslexia,
and expects autonomous execution: push to main, let CI build, hand him the download link. He does not
want check-in questions; he wants a working APK and an honest account of what is in it.

**The reader is the product, not a feature of something else.** That is the whole reason for the fork.
Inside TTT&LLL the reader is a panel that has to be as tall as the keyboard it replaces, entered from
a key, and constrained by an input method's lifecycle. None of that applies here.

---

## 2. The single most important decision, and it is already made

**Do not fork the TTT&LLL repository.** Start an empty one.

That repo is a fork of FlorisBoard: an input method with a keyboard layout engine, a glide typing
engine, subtype management, an extension system and a theme system, and the reading code is a few
hundred lines living as a guest inside it. Forking it means inheriting tens of thousands of lines
that exist to draw keys, and then spending the first week deleting them and the second week finding
out what the deletions broke.

**Lift the reading stack into a clean app instead.** The list of what to lift is section 4, and it is
about 2,800 lines all told, of which about 1,900 is already free of Android and moves unchanged.

A standalone app also removes the constraint that shaped the current reader most: it is a panel
`FlorisImeSizing.panelUiHeight() - smartbarHeight` tall, because it has to match a keyboard. AI Reader
has a whole screen. Design for the screen; do not port the panel.

---

## 3. What the app does

1. **Text comes in.** From the clipboard, from a share intent, from a screenshot read by a vision
   model, from a file. In TTT&LLL only the first two exist. A standalone reader should take a share
   intent from every app on the phone — that is the single biggest thing the fork buys.
2. **It is split into sentences and spoken**, one sentence at a time, by a cloud voice.
3. **Each word lights as it is said.** This is the feature. Everything else serves it.
4. **Controls:** play/pause, previous/next sentence, voice, load, screenshot.

### The word highlighting, which is the hard part

Two voices, and they work completely differently:

- **Speechify** returns `speech_marks` carrying **character offsets** into the exact string sent,
  with start and end times. There is nothing to align. Use it for English.
- **Microsoft Edge** returns a spoken word and a time and nothing else, so `MaAlign` has to work out
  which characters of the visible sentence that word was. Every branch in it exists because that
  guess went wrong once. Use it for Croatian.

**Croatian is why both exist.** Speechify lists `hr-HR` as *coming soon*, not supported, and its
English models answer a non-English voice with a flat 400. Check that page before assuming this is
still true; if `hr-HR` has shipped, the Edge path can go and `MaAlign` with it.

---

## 4. What to lift, file by file

All paths are in `markoboskoauroville/DictateKeyboard`.

### The engine — take as a submodule, do not copy

```
https://github.com/markoboskoauroville/MA_READER_ENGINE.git
```

Mounted in TTT&LLL as a git submodule at `engine-repo`, wired in `settings.gradle.kts`:

```kotlin
include(":engine")
project(":engine").projectDir = file("engine-repo/engine")
```

Plain Kotlin, no Android in it, **126 tests behind it**. It holds `MaAlign` (word-boundary
alignment), `MaWaveform` (silence detection and boundary refinement on decoded PCM) and
`MaEdgeVoice` (the Microsoft synthesis client). Do the same thing: submodule, not copy. Two copies of
an alignment engine is how the two stop behaving alike.

**CI must check out submodules or the build fails on an empty module directory, and the error points
nowhere near the cause.** `submodules: recursive` in the checkout step.

### Pure Kotlin, moves unchanged (~1,900 lines)

| File | What it is |
|---|---|
| `lib/dictate-core/.../provider/MaSpeechify.kt` | Speechify TTS, speech marks, the 2000-char cap, 402 split three ways |
| `lib/dictate-core/.../provider/MaVision.kt` | Groq vision OCR. **The prompt is the feature** — see §6 |
| `lib/dictate-core/.../provider/MaKeyRing.kt` | First working key wins, remembered, re-armed. See §5 |
| `lib/dictate-core/.../provider/MaKeys.kt` | Key parser: pulls keys of each shape out of a messy file |
| `lib/dictate-core/.../provider/MaUsage.kt` | Local usage and cost ledger |
| `lib/dictate-core/.../provider/DictateApiException.kt` | The error kinds everything above maps onto |
| `lib/dictate-core/.../provider/ProviderRegistry.kt` | Provider presets. **Strip it to the three needed** |

Their tests come too: `MaSpeechifyTest`, `MaVisionTest`, `MaKeyRingTest`, `MaUsageTest`. They run in a
plain JVM, which is what lets this whole layer be verified without an Android device — see §8.

Only three providers matter here: **Speechify** (voice), **Groq** (screenshot OCR), and Edge (no key).
Delete AssemblyAI, Anthropic, Gemini, OpenAI, local and the rest from the registry. A provider list
that offers things the app cannot use is a list that has to be read.

### Android, lift and adapt (~950 lines)

| File | Note |
|---|---|
| `app/.../dictate/reader/MaReader.kt` | The reader itself: state, sentence stepping, synthesis, cache. **Rewrite the view coupling**; keep the logic |
| `app/.../dictate/reader/MaPcm.kt` | Decodes audio to PCM for the waveform refinement |
| `app/.../dictate/MaScreenshot.kt` | Newest screenshot from MediaStore, downscaled and encoded |
| `app/.../dictate/MaKeyRingStore.kt` | File-backed ring, one small JSON per provider |
| `app/.../dictate/MaUsageStore.kt` | File-backed ledger |
| `app/.../dictate/reader/MaReaderLayout.kt` | **Reference only.** It is built for a keyboard panel |
| `app/.../app/settings/dictate/DictateKeysScreen.kt` | **Reference only.** The key manager: import, test, lights |
| `app/.../app/settings/dictate/MaReorderableColumn.kt` | Generic hold-and-drag list, if the fork wants one |

---

## 5. The key ring — the rule to preserve exactly

Marko's rule, arrived at over several builds, and it is not negotiable:

> Go down the list in order. Take the first key that works. Use it until it stops. Then flag it and
> move to the next. **Never test them all at once.**

The hard part is not flagging, it is **not flagging too eagerly**:

| what came back | the key's fault | what happens |
|---|---|---|
| 401 rejected | yes, permanently | flagged; only a manual test clears it |
| 402 cap, budget or balance | yes, for now | flagged; re-armed after six hours or at the UTC month roll |
| 429 too many requests | **no** | nothing recorded |
| timeout, no signal | **no** | nothing recorded, and the run stops |

Flagging a good key because the train went into a tunnel is worse than never flagging: the key is
skipped, the next is used, and the meter fills up somewhere Marko did not choose.

Three rules that look like details and are not:

- **The ring can never return nothing.** Every key flagged still returns them all, worst last. A stale
  flag must not silence the app, and a key dead in March may work in August.
- **`NoKeyLeft` carries the ring.** The run that discovers the whole keyring is in trouble is the most
  expensive one there is, and its flags live in an exception rather than a return value.
- **A manual test clears the flag first**, or a key just topped up stays greyed out because of
  yesterday.

**Key shapes:** Speechify `sk_`, Groq `gsk_`, OpenAI `sk-`. Speechify and OpenAI differ by one
character and that character is all that stops a mixed keyring filing them under each other.

**Never write a fake key beginning `sk_live_` or `sk_test_` anywhere in the repository.** Those are
Stripe's prefixes, GitHub's push protection scans every push, and it rejected a build over two
invented strings in a test file. Shape a fake key as its prefix plus twenty characters, nothing more.

---

## 6. The screenshot reader, and why its prompt is the feature

Marko presses power and volume down — his phone's own gesture — and the app reads the newest file in
the screenshots folder. It does **not** capture the screen. `MediaProjection` means a consent dialog
per session plus a foreground service; an accessibility service is permission to read everything
forever. His way needs one ordinary media permission, granted once, and leaves him choosing what is
sent.

**A phone screenshot is mostly not the thing being read.** His own example had a status bar on top and
a third of the screen taken by his keyboard. A model told only "read this image" returns `12:01`,
`4G+`, `71`, `ENG FAST ab AB`, `AP 1 2 3` woven through the sentences. Spoken aloud that is unusable.

So `MaVision.OCR_PROMPT` spends most of its words on what **not** to return, and a test asserts the
exclusions are still in it. Two clauses look like fussiness and are not: **no preamble**, because every
model opens with "Here is the text from the image:" and that sentence then gets spoken every time; and
**nothing when there is nothing**, because a model asked for text in a picture with no prose invents a
description instead.

`extractText` strips the preamble as a guarantee, but only when the first line is short **and** ends in
a colon — dropping the first line unconditionally eats the opening sentence of an article.

`DATE_ADDED` in MediaStore is in **seconds**. Read as milliseconds it puts every screenshot in 1970.

Downscale before sending: these models bill by image tiles, and a 1080×2400 screenshot costs several
times more tokens than the text needs. `inSampleSize` during decode, so the full bitmap never exists
in memory.

Model names are the most perishable strings in the codebase. Read the provider's own docs; do not
trust this document or your training data for them.

---

## 7. The aesthetic — this is what makes it a sister app

- **Colour is reserved for state. Never decoration.** A coloured thing on screen means something is
  happening. If it is not state, it is not coloured.
- **Nothing pulses, breathes or blinks.** There is no ambient animation anywhere. A recording lamp is
  steady. Movement means something changed.
- **Warm dark.** Near-black grounds, warm sand ink. The reader's ink is `0xFFE8B15C`. The recording
  red is `0xFF9B3B33`, the warning amber `0xFFF0883E`. The palette is the Sunrise theme, baked into
  the app rather than selectable — a saved theme in internal storage once shadowed the bundled one and
  made an edit to the stylesheet fail silently.
- **Round keys, generous hit areas, few of them.** Four voices, not forty. Two languages, not ninety.
- **Text where a picture would be a code.** `AP`, `HR`, `ENG`, `FAST` are drawn as words because no
  glyph means them. `ENG` rather than `EN`: `EN` and `HR` are the same shape and weight and read as
  "some code" at a glance.
- **No label that only explains the other labels.** A word read once and then occupying the row
  forever is a word to delete.
- **Say what is not available rather than showing zero.** No provider here exposes a balance, so the
  usage line says "no rate set" beside a real character count instead of `$0.00`.

---

## 8. How to work — the practices that make this fast

### Verify locally; there is no Android compiler in the sandbox

This is the single highest-value habit, and it is why several builds went green first time.

```bash
curl -sL -o kotlinc.zip https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip
unzip -q kotlinc.zip
# jars: okhttp, okio, kotlinx-serialization-json + core, kotlin-test, junit-jupiter-api,
#       junit-platform-console-standalone
kotlinc -classpath "$CP" -Xplugin=kotlinc/lib/kotlinx-serialization-compiler-plugin.jar src/*.kt -d out
java -jar junit-platform-console-standalone.jar -cp "out:$CP" --select-class=... --details=summary
```

Every pure-Kotlin file compiles and its tests run in about a minute. Android files cannot, so expect
roughly one CI failure per non-trivial UI change — **read the log, never guess.**

### Traps that cost real builds in TTT&LLL

- **Never test for an import with `in`.** `"…runtime.remember"` is a substring of
  `"…runtime.rememberCoroutineScope"`, so the check reports present on something absent. The missing
  import then cascades into ten errors about composable context, none of which names the cause.
- **A refactor that moves code out strands the line that used it.** Moving a drag into a shared file
  took its `LocalDensity` import with it and left the line behind. The compiler said "cannot infer
  type parameter R".
- **A Kotlin object initialises top to bottom.** A property whose initialiser calls a function that
  reads a `val` declared below it sees null and throws on first touch. Nothing warns.
- **Never put a changing collection in a `pointerInput` key.** The input restarts and cancels the
  gesture inside it. Symptom: a drag that moves one place and dies.
- **A nullable property from another Gradle module will not smart-cast.** Pull it into a local first.
- **Check every icon against the repo before using it.** `Icons.Default.KeyboardReturn` does not
  exist; it is `AutoMirrored`. Grep for existing usages — zero usages is the signal.
- **Deleting a route by matching its line leaves its annotations over the next one.** Check for
  dangling `@Serializable` / `@Deeplink`.

### Repository rules to carry over

- **Commit messages go through a file: `git commit -F`, never `-m`.** Write about *why*, in prose.
- **Update the handoff in the same push as the code.** Every time. In TTT&LLL it silently fell sixteen
  builds behind once and nobody noticed until Marko asked.
- **Two releases only.** CI prunes older ones; one is too few, because a link handed over dies the
  moment the next push lands.
- **A permanent signing key as a repository secret**, and `versionCode = 1000 + run_number`. A
  throwaway key per build forces an uninstall; a version code that goes backwards is refused as a
  downgrade. The offset is permanent and must never be lowered.
- **No ampersand in the APK filename.** Legal in a URL only when escaped, and a link that needs
  escaping breaks when pasted into a chat.
- **The package id is chosen once and never changed.** TTT&LLL is `com.mantraproductions.voicetype`;
  pick something like `com.mantraproductions.aireader` and never touch it again.
- **Croatian and English only.** This is permanent and has been re-decided twice. A ninety-nine
  language picker was built, shipped and then deleted.

---

## 9. Suggested order of work

1. **Empty repo, empty app, CI green.** Kotlin, Compose, Material 3, one activity, the Sunrise palette,
   the engine submodule mounted, and a workflow that builds a signed APK and publishes a release.
   Nothing else. Get a download link into Marko's hands on day one.
2. **Text in and on screen.** Clipboard, plus a share intent so every app on the phone can send to it.
   Scrolling page, warm ink.
3. **Speak it, with highlighting.** Speechify for English first, because its character offsets mean no
   alignment at all. Sentence stepping, play/pause, previous/next.
4. **The key ring and the key manager.** Import from a file, one key per line, lights per key, first
   working key wins. Nothing works without keys, but nothing can be tested until step 3 exists.
5. **Croatian on the Edge voice**, with `MaAlign` and the waveform refinement. Check first whether
   Speechify has shipped `hr-HR`, which would make this unnecessary.
6. **The screenshot reader.** Groq vision, the prompt from §6 verbatim.
7. **Usage and cost**, per key, counted locally.

Steps 1 to 3 are the app. Everything after is what makes it his.

---

## 10. What to ask Marko, and what not to

Do not ask him to choose between implementations, to confirm a design you can check in the code, or to
approve each step. Execute, then report plainly, including what you did not do and why.

**Do** ask when the answer is only in his head and the cost of guessing is a rebuild:

- Should the reader keep a library of past readings, or only the current one?
- Should it read a whole document unattended, or one sentence at a time under his thumb?
- On the phone, does he want it full screen, or floating over whatever he is reading?

One warning learned the hard way: a two-press flow was built for the keyboard to let settings be
checked before recording, and it was reverted a few builds later because reading the pipeline showed
those settings were already changeable mid-recording. **Check the constraint in the code before
building around it.** He is fast at spotting when the shape is wrong, and it is cheaper to ask him
one sharp question than to ship a mechanism he then has to reject.
