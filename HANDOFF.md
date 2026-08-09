# TTT&LLL — handoff and development plan

Written at build 88, updated at build 136. Sixteen builds went by without an update once, so
check `git log -- HANDOFF.md` against `git log` before trusting it. Read this first, then
`git log --oneline -20` to see anything newer.

---

## 1. What this is

An Android voice keyboard. Forked from DevEmperor's **Dictate**, which is itself built on Patrick
Goldinger's **FlorisBoard**. Almost nothing of the original dictation code survives; the FlorisBoard
keyboard engine underneath is largely intact.

| | |
|---|---|
| Repo | `markoboskoauroville/DictateKeyboard` |
| Package | `com.mantraproductions.voicetype` |
| Build | push to `main` → GitHub Actions → signed APK on the releases page |
| Owner | Marko Boško, Mantra Productions |
| Theme pack | `markoboskoauroville/auroville-florisboard-theme`, published v2.0.0 |

The working tree used across these sessions is a shallow clone at `/home/claude/kb`. A GitHub token
lives at `/mnt/user-data/uploads/github_token.txt` — **never print it**; scrub every command's output
with `sed "s/$(tr -d ' \t\n\r' < /mnt/user-data/uploads/github_token.txt)/***REDACTED***/g"`.

---

## 2. Rules that are not negotiable

**The name.** The app is **TTT&LLL**. That is what it is called everywhere: the launcher, the
keyboard switcher, the settings title, the release title. It stands for **Talk to Type** and
**Look, Listen, Learn**, the two halves of what it does, and About is the one place the initials
are spelled out, under the short name, so somebody meeting them for the first time can work out
what they mean without asking. Mantra Voice Type became Talk to Type at build 109, and Talk to
Type became TTT&LLL at build 118 when the reader gained a name of its own.

The APK filename is `ttt-lll-build-N.apk` with no ampersand in it. An ampersand is only legal in
a URL escaped, and a download link that needs escaping is one that breaks when it is pasted into
a chat.

**The name must never be translated.** Forty locale files inherited from the fork still carried
`app_name` as `Dictate`, so a phone set to Croatian or German showed the old fork name on the
launcher no matter what the default said. Those overrides are removed and `app_name` is marked
`translatable="false"`, so a future translation import cannot bring them back. If the launcher
ever shows the wrong name again, look for a `values-xx/strings.xml` that has grown an `app_name`
back. The **package id stays `com.mantraproductions.voicetype`** and must not be changed:
Android treats a new package as a different app, so changing it would install alongside the old one
and abandon every API key, preference and the learned n-gram model rather than upgrading. The
package id is not visible to Marko; the launcher name and the APK filename are, and those are what
changed.

**Releases.** The two newest releases may exist, never more. CI deletes everything older after each
successful publish. Two rather than one because a link already handed over dies the moment the next
push publishes, and a one line change to a document is enough to cause that; it happened at build 90.
Two is still bounded, so an old APK never sits there long enough to be mistaken for the current one.

**Version codes.** CI passes `1000 + run_number`. The offset is permanent. Lowering it makes a build
look older than one already installed, and Android refuses the install — this happened at build 62
and produced a misleading "package appears to be invalid" dialog.

**Design language.** Near-black surfaces. Warm gold ink `#E8B15C`. Colour is reserved for state, never
decoration. Red `#9B3B33` means recording and nothing else means red. Green `#6FA85A` means a
modifier is locked. Selection is shown by a gold outline stroke, never a filled block. No pulsing,
breathing or scaling animation anywhere. Minimum text on buttons.

**Every key is styled the same.** Enter had its own fill, ink and border and the space bar had dimmer
ink; both are gone. Nothing gets decorative special treatment. Per-key font sizes stay, because
`ctrl` and `?123` are words rather than letters and have to fit the key they are written on, which is
fitting rather than decorating. An icon that cannot say what a key does is replaced by words: this is
why no row-set button in db has one.

**Croatian and English only, permanently.** Two languages, installed automatically, no choice
offered, no auto-detect, no other layouts or popup mappings. If a future request needs another
language it is a decision to be taken deliberately, not a file quietly added back.

**The scheme is baked in.** `ThemeManager.evaluateActiveThemeName()` returns Sunrise and reads no
preference. The day and night theme settings, the four modes and the sunrise and sunset times are
gone from the interface. Do not reintroduce a theme picker. The Snygg loader underneath stays,
because it is what draws every key; only the choosing is removed. A side effect worth knowing: a
saved theme in internal storage can no longer shadow the bundled stylesheet, which used to make an
edit to `sunrise.json` fail silently.

**The status line.** Small, gold, monospace, from `MaStatusFontSize` and `MaStatusFontFamily` in
`LegacyDictateLayout.kt`. Both views read those two values so they cannot drift apart again.

**Prose in commits and replies.** No dashes or em dashes as connectors. Months as numbers, never
names.

---

## 3. Traps that have caught me repeatedly

Each of these cost at least one wasted build. They are listed because they will catch the next
session too.

**A changed default never reaches a written preference.** Changing `default =` in `AppPrefs` does
nothing for an install that already has a value. Every such change needs a one-shot pass in
`DictateLegacyMigrator` called from `FlorisApplication`, each with **its own flag**. Do not bump an
older flag to re-run its pass: that re-runs everything in it, including the accent colour and the
action row order, and silently overwrites anything set by hand since. Passes so far: `v13` rows,
`v14` panel, `v15` removals, `v17` languages, `v18` dashboard order, `v19` paste position, `v20`
clipboard and AP.

**A migration that reads extension resources runs before they exist.** Migrations run while the
preference store loads; `extensionManager.init()` runs afterwards. The Croatian subtype pass looked
up the bundled subtype presets, found an empty list, added nothing, and marked itself done, so
Croatian was missing for three builds while the code looked correct. Build subtypes from literal
values, or run after the extension manager.

**Two constants can hold the same key code and nothing will warn you.** `MA_QUICK_RECORD` was `-232`
and so was `IME_HIDE_UI`. A `when` takes the branch written first, so quick record never ran, while
the label lookup was ordered the other way and named the key correctly on a tile that did the wrong
thing. After adding any key code, re-run the collision check over `KeyCode.kt`. `INTERNAL_MAX` and
`CTRL` both being `-1` is intentional; anything else is a bug.

**Something else may be overwriting the obvious place.** The enter key stayed orange through four
attempts at editing the stylesheet because `rememberSnyggTheme(..., accentColor)` *replaces*
`--primary` at load time. Rules that must not be repainted use literal colours, not `var(--primary)`.
When editing the obvious place has no effect twice, find what is overwriting it.

**Icon imports.** `Icons.Default.X` needs `...icons.filled.X`; `Icons.Outlined.X` needs
`...icons.outlined.X`. Importing both `filled.X` and `outlined.X` is a name clash and will not
compile. Use `/home/claude/iconcheck.py <files>` — it checks both. Also verify any icon name is
already used elsewhere in the project before trusting it.

**Kotlin `\uXXXX` escapes take four hex digits.** Anything outside the Basic Multilingual Plane needs
a surrogate pair, so pick BMP glyphs.

**Verify against reality, not memory.** Key codes, function signatures and parameter names have all
bitten me when assumed. `TOGGLE_ONE_HANDED_MODE_LEFT` does not exist; one-handed mode is
`TOGGLE_COMPACT_LAYOUT`. `ListPreference` has no `summary` parameter.

**A panel drawn by one view does not exist in the other.** db set its flag from the transcribe view
and appeared on the typing keyboard, which was not on screen, and the flag stayed set so the next
trip to the keyboard opened a panel nobody asked for. Anything that covers the keyboard must be
drawn by both views.

**Commit messages go through a file.** `git commit -m` with an apostrophe in the text breaks shell
quoting and the commit is lost. Write the message to `/tmp/msg.txt` and use `-F`.

**The handoff rule was broken for sixteen builds** between 118 and 134, and it was only caught
because Marko asked. Nothing warns you: the APK builds green, the feature works, and the document
quietly describes an app that no longer exists. It is worse than no document, because the next
session reads it and believes it. Update it in the SAME push as the code, every time, and if you
find yourself several builds behind, write the gap up honestly rather than only the newest change.

**Before pushing**, run the brace balance check (`/home/claude/kbal.py`, rewrite it if the sandbox has
reset), a sweep for `R.string.*` references with no definition (they live in `strings.xml` *and*
`strings_dont_translate.xml`), a JSON parse of any edited asset, and the key code collision check.
Verify every icon name is already used elsewhere in the project. Expect roughly one CI failure per
non-trivial change anyway; there is no Kotlin compiler in the sandbox.

---

## 4. Where the code lives

```
dictate/
  DictateController.kt      recording, transcription, prompts, history, recasing
  MaCase.kt                 the four letter cases, shared by buttons and pipeline
  MaLivePrompts.kt          instructions spoken to the little man
  MaMacros.kt               macro bar model and AHK-style syntax
  audio/
    RecordingController.kt  capture at the device's best rate
    MaResample.kt           anti-aliased downsample to 16 kHz
    MaEncoder.kt            AAC encode
  ui/
    LegacyDictateLayout.kt  the transcribe view, the big one
    MaQuickRow.kt           languages and the four case buttons
    MaRecordMeter.kt        the meter panel, shared by both views
    MaExtraRow.kt           the top row that changes its labels
    MaCursorRow.kt          arrow strip, and MaMacroBar
    DictateSmartbarUi.kt    the recording bar inside the keyboard view
  MaLanguage.kt             THE one place either language is written; nothing else may write them
  MaNumericSecondary.kt     what each digit types when held, ten slots
  nlp/MaNgramModel.kt       the personal prediction model: counts, backoff, save and load
  nlp/MaNgram.kt            owns the model, the sentence buffer, the debounced save
ime/
  keyboard/KeyboardManager.kt    key handling, the central switch
  keyboard/ComputingEvaluator.kt icons per key code
  text/key/KeyCode.kt            Marko's codes are -219 and below
  smartbar/quickaction/          the three-dots panel
app/settings/                    settings screens, two tabs: Mantra and FlorisBoard
  settings/dictate/DbEditorScreen.kt        the db editor
  settings/dictate/MaNumericSecondarySetting.kt
  settings/dictate/MaNgramSetting.kt
```

**The audio pipeline**, settled and not to be revisited without reason: capture at the device's best
rate (48 kHz where available) → resample to 16 kHz mono with a windowed-sinc filter → encode AAC →
send. Each step deletes what it replaces. Opus was removed: Android's own encoder produced files that
transcribed slowly, while FFmpeg's Opus of the same audio did not.

---

## 5. The plan

### Shipped since this document was last accurate (builds 89 to 104)

Read the commit messages for the reasoning; each one says why, not just what.

- **Ctrl and modifier locking.** Ctrl replaced `?123` in the corner; tap arms, long press locks, tap
  releases. Gold armed, green locked, with an outline rather than a fill. `Ctrl+A/C/X/V/Z`, plus
  `Ctrl+Shift+Z` and `Ctrl+Y`; other letters fall through to a real ctrl+key event. Shift locked also
  makes arrows extend the selection, through the existing `isManualSelectionMode`.
- **Shift is one arrow in three weights**, drawn in the project (`ic_ma_shift*.xml`). The Material
  set has no hollow arrow without a bar.
- **Volume keys.** Up starts and sends. Down cancels while recording, and switches language when
  idle.
- **One language for the whole app.** `MaLanguage` is the only place the transcription language or
  the keyboard subtype is written. Croatian and English install on first run; there is no language
  screen in Settings and no auto-detect.
- **Croatian and English only on disk.** 178 asset files removed; keyboard assets 552 kB to 212 kB.
  Croatian diacritics live in the `hr` popup mapping, not in a layout: c gives č ć, s gives š, z
  gives ž, d gives đ. Do not touch that file.
- **One-handed mode and the floating window removed**, including the upstream migration that kept
  adding the floating button back.
- **The suggestion strip is only suggestions.** The microphone moved into the copy row as the view
  swap, holding it opens db, and the language badge is typed like a candidate.
- **The copy and paste row is shared by both views**, drawn by the same composable.
- **db**: the dashboard. Row sets first, named in words with no icons, a close key, drawn by both
  views, and a full editor at Settings → Keyboard extras → db.
- **The number row** has five sets, and every digit types an assignable second symbol on long press,
  underscore by default, editable in Settings.
- **Personal n-gram prediction.** See `nlp/MaNgram*.kt`.

### Also shipped since (builds 105 to 110)

- **Retry waits scale with the upload size** and with the attempt, capped at twenty seconds. They
  were three seconds flat, so a long dictation burned all its attempts in ten seconds while the
  signal was merely poor. The status line says *waiting to retry*, not *retrying*.
- **The scheme is baked in.** Sunrise always, no picker, see the rule above.
- **Snippets**, see below.
- **Renamed to Talk to Type (TTT)** at build 109, then to **TTT&LLL** at build 118. Package id
  unchanged through both, see the rule above.

### Shipped since (builds 119 to 134)

Sixteen builds landed before this section was written, which is itself the lesson: the handoff rule
was broken quietly and nobody noticed until Marko asked. Check `git log -- HANDOFF.md` against
`git log` before believing this document is current.

- **The clipboard became a note keeper.** Long-press an entry offers **Edit**, which rewrites it in
  place keeping the same id and pin and taking a fresh timestamp. An **X** closes the panel and a
  **plus** under it writes an entry by hand. The editor has no caret and no selection: text is added
  and removed at the end only, because a caret needs hit testing, a selection model and cursor keys
  rerouted away from the app.
- **The feature row**, along the very bottom of both views, then rebuilt twice. It is now four keys:
  **1** folds the edit strip, **2** folds the entire keyboard, **book** opens the reader, **mic**
  switches between the keyboard and dictation views. Long press any of them to fold the row away.
- **Zone two is not composed at all** rather than filtered row by row. The first attempt removed rows
  from the keyboard arrangement, which made the keys vanish while `keyboardUiHeight` went on
  counting four rows, so the space stayed. Two pieces of arithmetic had to agree and did not. Not
  composing the keyboard returns its height exactly, with no arithmetic to disagree.
- **The mic is what makes closing the keyboard safe.** With zone two shut, no key anywhere else
  reaches the dictation screen, so the way between views must live in the row that always survives.
- **The fold key lives in the arrow strip**, at the left end, and is the fifth glyph there. Opening
  is a **one second hold with a buzz**; closing is a tap. `MaFlatKey` fired on touch *down*, which is
  right for a repeating arrow and wrong for anything that changes the shape of the keyboard.
- **Modifier locks buzz.** The haptic existed but was wired to the long press in `TextKeyboardLayout`,
  so locking caps by double tap or by the cycle felt nothing. It now fires on the state change in
  `KeyboardManager`, the single point every route into a lock passes through, and is gated only on
  haptics being on at all rather than on the long-press switch.
- **AP is timed 100, 100, 500**: a short lead before the select and before the delete, then half a
  second before the paste. It originally waited once, for a third of a second, and not at all after
  the select, and it failed silently in some apps. The waits are deliberately unequal, because only
  the paste was being dropped; the first two steps just need the field to have noticed. The input
  connection is refetched at every step, since a field can be replaced underneath a macro.
- **The app name is never translated.** Forty locale files still carried `app_name` as `Dictate`, so
  a phone set to Croatian or German showed the old fork name on the launcher whatever the default
  said. All removed, and `app_name` is now `translatable="false"`.

### LLL, the reader: shipped at build 128

The whole chain works end to end and is in the APK. Clipboard text is cleaned of Markdown and
addresses, cut into sentences, spoken by one of four Edge voices, the word boundaries are mapped onto
the visible characters, moved again onto the real decoded waveform, and a highlight position ticks at
40 ms against the player.

- **The engine lives in `markoboskoauroville/MA_READER_ENGINE`**, package
  `dev.mantraproductions.reader.engine`, mounted as the `:engine` Gradle module from the `engine-repo`
  submodule. CI checks out submodules. 126 tests, one of which speaks a real Croatian sentence to
  Microsoft on every push and once a night.
- `MaEdgeVoice` speaks, `MaAlign` maps boundaries to characters, `MaText` cleans and splits,
  `MaWaveform` moves every word onto where it is actually spoken.
- **App side**: `MaPcm` decodes with MediaCodec, which is the one piece that cannot live in the
  engine, and `MaReader` orchestrates one sentence at a time with a disk cache keyed by text and
  voice. `MaReaderLayout` is the view, reached by `ImeUiMode.READER`.
- **The zero-duration fix is in the app, not the engine.** The engine merges a number with the word
  after it, so `8. mjesec` arrives as one boundary and the number ends up lit for zero seconds.
  Adjacent words sharing a start now split the span between them, in `MaReader.spread`. The ports
  stay faithful to the Python; presentation rules belong on the side that presents.
- **Still to do on the reader**: nothing blocking. Worth watching on the device is whether the first
  sentence starts fast enough to feel like reading rather than loading.

### Next: AssemblyAI Sync, fast dictation on one API

Decided and researched, not yet built. One provider throughout: AssemblyAI, the same key and the
same bill as now. No second vendor.

**Speechify was investigated and rejected.** It has no speech-to-text API at all; the whole product
is text to speech. Their "10 Best Speech to Text APIs" article recommends other companies and reads
like a product page, which is where the confusion came from. Speechify IS worth revisiting for the
LLL reader, because its TTS returns word-level timestamps, which is exactly what `MaAlign` needs and
would replace an undocumented Microsoft endpoint with a contractual one.

**The verified endpoint**, checked against the docs rather than remembered:

```
POST https://sync.assemblyai.com/transcribe
Authorization: <raw key, no Bearer prefix>
X-AAI-Model: universal-3-5-pro
multipart/form-data, field "audio"
-> { "text": ..., "confidence": ... }
```

About 134 ms at the median. **Hard limits: 2 minutes and 40 MB per request**, and oversized requests
are rejected up front rather than failing partway. `language_codes` is accepted, so Croatian may
work; do not assume either way, one recording answers it. Price is $0.45/hr against $0.15/hr for the
async path in use today.

**Why Sync rather than streaming.** A WebSocket is an open line billed by how long it is open, and it
must be held, reconnected and closed. A single request is one envelope: the clip goes up whole and
the finished text comes back in the same breath. Dictation already has a clean beginning and end
because Marko presses and releases, so there is nothing streaming buys here that is worth a socket.
AssemblyAI's own description of Sync names dictation and voice commands as its purpose.

**Build in this order. Each step is useful alone.**

1. **The Sync client and the fast/slow switch.** `TranscriptionApi.ASSEMBLYAI_SYNC` beside the
   existing `ASSEMBLYAI_ASYNC` in `ProviderConfig.kt`, dispatched in `OpenAiCompatibleClient` around
   line 161 where `ASSEMBLYAI_ASYNC` already is, and a preset in `ProviderRegistry`. Settings calls
   them **fast** and **slow**, because those are the words that mean something. Anything over two
   minutes falls back to the async path AUTOMATICALLY and silently, since a long recording is exactly
   the one that took the most effort and must never be the one that fails.
2. **Chunking with silence-aware cuts**, which makes long recordings fast as well. Cut at a silence
   near the two minute mark, never at the mark itself, or a word is sliced in half and both halves are
   misheard. **The silence detection already exists and is already tested**: `MaWaveform.silenceRuns`
   in the reader engine, pure maths on decoded PCM, 126 tests behind it, already shipping in the APK.
   The seams then need cleaning, since each chunk comes back capitalised as a fresh sentence and a
   visible scar every two minutes is not acceptable.
3. **Pipelining**, sending chunks while Marko is still speaking, so by the time he releases the key
   everything but the last chunk is already transcribed. This is the step that makes a six minute
   dictation land as fast as a twenty second one, and it is the closest thing to streaming that this
   design needs.

**Also asked for: a cost meter.** Build it locally rather than from a billing API. The app already
knows the duration of every recording it sends and which path took it, so it can keep its own ledger:
minutes and cost this week, this month and all time, per provider, against an editable rate. Works
offline, needs no extra credentials, covers every provider, and cannot silently disagree with itself.
Settings labels it something plain like "usage and cost".

**And: long press on the microphone key opens the model chooser**, so fast and slow can be swapped
without going into settings.

### Next: retranscribe

Robust sending is done. What remains of that item is the manual half: a circular back-arrow, in
**both** views, whenever a recording was interrupted or failed, so the kept audio can be resent
deliberately. The resend path already exists in the error handling (`ErrorAction.RESEND`, retained
audio); it is simply not surfaced as a key. This is the last item where a failure costs Marko words
rather than convenience.

### Snippets

A snippet is a **pinned clipboard item**, not a third kind of thing. Hold the clipboard key in the
copy row to save the selection (or the current clip); tap it to open the panel, where pinned items
sit at the top. `ClipboardManager.saveSnippet` inserts then looks the item back up before pinning,
because insertion is what assigns the id. Do not build a separate snippet store: it would mean a
second list, a second screen and a second place to look, for something the clipboard already does.

### The reader view (LLL), the next real project

**Decided: one app, not two.** The reader is a **third view inside TTT**, beside the keyboard view
and the transcribe view. Marko first asked for a separate sister APK that flips across with a two
finger gesture. That cannot work: Android allows one active input method at a time and an IME cannot
hand over to another without sending the user through the system input-method picker, so every flip
would open a grey system dialog. As one app the flip is instant, there is no second install, and the
clipboard is already shared because it is the same process. He agreed to this.

**What it is: a window onto the clipboard that reads.** Not a text editor, not a box to paste into.
It shows what was last copied and speaks it in a Microsoft Edge neural voice, highlighting each word
as it goes, and it lists the clipboard history so anything copied earlier can be read again. That is
the whole product. Marko's words: *a window to the clipboard which reads*.

Framing it this way settles more than it looks:

- **No text input of its own.** Nothing to type into, nothing to paste into, no keyboard needed
  inside it. Text arrives by being copied anywhere on the phone, which is a thing the user already
  does constantly and needs no instruction.
- **No dependency on a focused text field.** The reader does not touch the `InputConnection` at all,
  so it works with nothing focused, over any app, on a page that has no editable field. This is a
  large simplification: everything else in this keyboard is built around an editor being present.
- **It reads TTT's own clipboard history, not the system clipboard.** Android 10 and later only let
  the foreground app or the active IME read the system clipboard, and TTT already keeps its own
  history database with pinning. Reading from that store sidesteps the restriction entirely and gets
  snippets for free: a pinned snippet is a thing kept to be read again.
- **Snippets and the reader are the same feature seen twice.** Hold the clipboard key to save
  something worth keeping; open the reader to hear it. Do not build a second store for reading
  material.

It occupies exactly the keyboard's footprint and uses the same scheme: near-black surfaces, gold ink,
no decoration. Marko is dyslexic and the word highlight is the point of the whole thing, not a
flourish.

**The name is the brief.** LLL is Look, Listen, Learn, and Marko says every app he builds has the
property of teaching something. The pairing of a lit word and a spoken word is the teaching
mechanism, so anything that weakens it (a highlight that lags, a sweep that collapses onto one word,
a sentence that scrolls away before it is finished) is not a cosmetic bug. It is the product
failing.

**Voices: four, no more.** From the reference implementation:

| | female | male |
|---|---|---|
| English (UK) | `en-GB-SoniaNeural` | `en-GB-RyanNeural` |
| Croatian | `hr-HR-GabrijelaNeural` | `hr-HR-SreckoNeural` |

Same two languages as the keyboard, so `MaLanguage` should pick the pair and the reader only chooses
male or female within it.

#### The reference implementation

`reference/26sh_i_ma_reader_v26_macos.sh` in this repo is Marko's MA Reader v26: a single-file
macOS installer with a Flask server and the whole reader embedded. It is a year of work and it is
the specification. **Read it before writing anything.** The parts that matter:

- `synth_unit` around line 1365: the edge-tts call. Thirty lines, and the least valuable part.
- `_communicate` around line 1351: edge-tts 7.x changed the default boundary from `WordBoundary` to
  `SentenceBoundary`, which silently produced no word events at all and dropped the app onto its
  fallback. Whatever we build must ask for word boundaries explicitly.
- `align_tokens` around line 828: **the valuable part.** Maps engine word boundaries onto exact
  character ranges in the visible text, resyncing when the voice expands a number or spells an
  acronym, and interpolating by word width where matching fails so the highlight always travels left
  to right and never collapses onto the last word.
- `refine_tokens` / `measure_silence`: pins each onset to the real waveform after synthesis.

`align_tokens` and `refine_tokens` are pure logic with nothing Python-specific in them and port to
Kotlin almost line for line. Port them faithfully rather than reinventing; every branch in there
exists because something went wrong once.

#### The one hard technical fact

**edge-tts cannot be used on Android.** It is a Python library and Python does not run in an IME.
But it is only a *client*: it opens a WebSocket to Microsoft's speech endpoint, sends SSML, and reads
back binary audio frames interleaved with `WordBoundary` JSON text frames. That protocol is
reimplementable in Kotlin, and this project already ships OkHttp with a WebSocket client in use at
`lib/dictate-core/.../RealtimeClient.kt`, which is the pattern to follow.

Do not reach for Chaquopy to run Python on device: tens of megabytes for a library we would use
thirty lines of, in a process the system kills routinely.

Android's own `TextToSpeech` with `onRangeStart` is the fallback if the Edge protocol proves
unworkable. It is offline and simpler, but the voices are worse and Croatian is often not installed
at all, which would break half the point.

#### The engine lives in its own repository, and there are no copies

The Edge speech client and the word timing alignment are needed by LLL **and** by the standalone
**MA Reader Android** port. They live in **`markoboskoauroville/MA_READER_ENGINE`** and nowhere else.

This document previously described a `shared/reader/` folder here plus a rule for keeping copies in
step. That is gone and must not come back. Marko rejected it and he was right: two copies with a
synchronisation rule are still two copies, and a rule that depends on being remembered is one that
eventually is not. The failure is quiet, a fix reaching one app and not the other, and the symptom is
a highlight that stops sitting on the word being spoken.

Consume it as a **git submodule included as a Gradle module**, not as a published artifact.
Publishing means pinning a version, and pinning means the two apps can be on different versions,
which is the thing the repository exists to prevent.

```
git submodule add https://github.com/markoboskoauroville/MA_READER_ENGINE.git engine-repo
```

`settings.gradle.kts`:

```kotlin
include(":engine")
project(":engine").projectDir = file("engine-repo/engine")
```

**CI must check out submodules** or the build fails on a missing module, which is a confusing error
for the cause:

```yaml
- uses: actions/checkout@v4
  with:
    submodules: recursive
```

A submodule points at a commit, so a change in the engine does not arrive here until this repository
is told to move: `git submodule update --remote engine-repo`, then commit the new pointer. That is
deliberate. A change can never break both apps at once without somebody choosing it.

Marko's rule applies at that level: when the two apps sit on different engine commits, compare the
dates and move the older one up. There is only ever one version of the code, so this is a question of
when a change is picked up, never of which version is correct.

Nothing that knows about a keyboard, an `ImeUiMode` or a Compose theme goes in the engine. The test:
both apps must need it, and it must not care which is calling.

#### Build order

1. ~~**The Kotlin voice client.**~~ **Done**, in the engine repository, see below.
2. **The alignment port.** `align_tokens` and `refine_tokens` in Kotlin, tested against the same
   sentences the Python handles. This is where the quality lives.
3. **The reader view.** Third `ImeUiMode`, keyboard footprint, gold on near-black, the word
   highlight, play and pause, the latest clip shown on entry and the history reachable. Reached by
   the three way view swap key, not a gesture.

#### Step 1, the voice client: done, and living in the engine repository

`MaEdgeVoice` is in **`markoboskoauroville/MA_READER_ENGINE`**, package
`dev.mantraproductions.reader.engine`. Text and a voice in, mp3 bytes and word boundaries out. It is
**not** in this repository and must not be copied here.

It was written into `lib/dictate-core` first, before the engine repository existed, and moved. If
anything that looks like a reader engine ever turns up under `lib/` again, that is the mistake
repeating, not a second implementation.

Read that repository's README before touching the speech protocol. The short version, because it is
the part that fails silently and none of it is guessable: the accepted Chromium version in
`Sec-MS-GEC-Version` moves and a stale one is a flat 403, word boundaries have to be asked for
explicitly or no word events arrive at all, and OkHttp writes `Sec-WebSocket-Version` itself so
setting it again is answered with HTTP 400. A wrong clock reads as forbidden and is handled by
reading the server's own `Date` header and retrying once.

**Proven against the live service before it was pushed**, not left for CI to discover. Croatian,
`hr-HR-GabrijelaNeural`:

```
Danas je 8. mjesec i imam 25 godina.
26064 bytes of real mp3 frames, 4.344 s, 6 boundaries
   0.100 +0.475  Danas
   0.575 +0.088  je
   0.663 +0.825  8. mjesec
   1.488 +0.188  i
   1.675 +0.463  imam
   2.138 +1.350  25 godina
```

**Six boundaries for eight visible words, and that is the brief for step 2.** The engine answered
`8. mjesec` and `25 godina` as single boundaries covering two visible words each, so boundary text
cannot be matched to visible text one to one. That is exactly the mess `align_tokens` absorbs. Do
not simplify it when porting, and test the port against that sentence.

That repository has its own CI job which speaks for real on every push and once a night. If the
reader ever goes quiet, look there before looking here: the failure is far more likely to be
Microsoft moving than anything in this app.

**The submodule is deliberately not wired in yet.** Nothing in the keyboard calls the engine until
step 3, and a submodule that nothing uses can only break the APK build, which is what everything
else here depends on. Wire it when the reader view needs it, following the instructions above, and
remember `submodules: recursive` in CI at the same time.

**Where the sandbox got the proof from.** There is no Kotlin compiler in it by default, but the JVM
is there and the network is open, so `kotlin-compiler-2.3.20.zip` plus OkHttp, Kotest and the
serialization jars from Maven Central are enough to compile *and run* anything with no Android
imports. That is what turns "expect roughly one CI failure per non-trivial change" into none. Keep
engine code free of Android APIs and it stays provable this way.

#### Switching views: a button, and the existing flip is not to be touched

**Decided, and this corrects an earlier plan in this document.** The view swap key must stay a **two
way flip** between the typing view and the transcribe view. Marko uses that pair constantly and likes
it exactly as it is. A three way cycle was proposed here and he rejected it: it would make the pair
he uses every minute cost two presses instead of one, to save a press on the view he uses least.

The reader is reached by **its own button, placed in the transcribe view**. The common path stays one
press, the third view is one press away when it is wanted, and whoever never opens the reader never
pays for it.

A gesture was also considered and rejected: pressing a button is easier than making a gesture, and a
gesture that must never fire accidentally is awkward on purpose.

Holding the view swap key opens db and must keep doing so.

#### The reading pane behaves like a teleprompter

The currently spoken **sentence** stays in the vertical centre of the display and the text feeds
through it. The word highlight moves within that sentence. Two scales at once: sentences centred,
words lit.

This lines up with how the reference implementation already works, which is worth noticing rather
than rediscovering: it splits the passage into sentences and synthesises each sentence into its own
mp3 with its own word timings. One sentence is therefore the natural unit for the audio, the cache
and the scroll, and all three stay in step for free.

#### Fullscreen, and the honest constraint

Next to play there is a fullscreen button, and pressing it should give an immersive dark reader: text
highlighting itself and nothing else on screen. Marko's phrase is *ebook reader*.

**The constraint to face before building it.** An input method's window sits at the bottom of the
screen with the host app above it. It can be made very tall, but it is still a keyboard window: it
cannot own the screen, and it disappears the moment the keyboard closes.

Two ways to do it, and the second is probably right:

1. Grow the input view until it nearly fills the screen. Cheap, one process, but the host app is
   still visible above it and the reading still dies when the keyboard closes.
2. **Launch a real Activity.** True fullscreen, survives the keyboard closing, can keep playing while
   another app is used, and takes a back press to return. It also answers the open question below
   about audio outliving the keyboard, and it is the honest answer to *ebook reader* rather than an
   approximation of one.

The Activity is more work and gives the reader a second place to live, so the reading state must
belong to one object that both the view and the Activity read from, never be duplicated.

#### Storage: a day cache

**Decided.** Synthesised mp3s are kept for the day and are gone the next. Keyed by the text and the
voice, so re-reading the same passage today costs nothing and reading it tomorrow simply synthesises
again.

This is the right shape because it needs no management: no size limit to tune, no cleanup screen, no
files to prune, nothing accumulating unnoticed. Wipe on the first use of a new day rather than on a
timer, so a phone left alone for a week does not carry a week of audio.

The day cache and the per sentence synthesis fit together: the unit cached is one sentence, so a long
article stopped halfway resumes without re-synthesising what was already heard.

#### The relationship with the clipboard panel, worth understanding before building

The clipboard panel (`IME_UI_MODE_CLIPBOARD`, the first key in the copy row) already shows the
history as a grid, with pinned items first. The reader shows the same history and adds a reading
pane and a voice. They are close enough that building the reader carelessly produces two lists of
the same things.

The distinction to hold: the **panel is for putting text back into a field**, the **reader is for
taking text off the screen and into the ear**. Same store, opposite directions. If the reader ends up
needing a list, it should look like the panel's list, not a second design.

#### Open questions, do not guess

- **Does it speak the moment the view opens, or show the text and wait for a press?** Marko leaned
  towards automatic and then said he was not sure, so this is genuinely undecided. Note the risk
  before deciding: a keyboard that starts talking out loud the instant it is opened, in a bus or an
  edit suite, is a keyboard that gets switched off. A large obvious play control with the text
  already shown is the safer default and costs one press.
- **What happens at the end of a passage:** stop and stay, or return to the previous view.
- **Whether reading survives the keyboard closing.** Largely answered by the fullscreen Activity
  above: inside an Activity it survives naturally. Still open for the non-fullscreen case, where
  closing the keyboard should probably just stop the audio rather than leave a voice talking from an
  invisible window.

### Smaller items, any order

- The trailing space after a recased word
- Theme manager: watch the Downloads folder and import a `.flex` automatically
- The Auroville theme pack lags the app: locked modifiers show green only on Sunrise, because the
  green lives in the bundled stylesheet. The external repo needs the same rules.
- The symbols view is asymmetric: `ABC` sits far left while `?123` sits by the space bar. Two lines.
  **Asked three times, never answered. Do not guess.**

### Watch, do not act yet

The n-gram model shipped at build 104 and has predicted nothing on a real phone. Croatian case
endings are its known weakness: kuću, kući and kuća are three strings to a counter. Fixing that needs
a morphology table, and a wrong table merges words that are genuinely different. Thin counts recover
with use; wrong merges do not. Wait for Marko to say how it actually behaves.

### Parked, explicitly

- **The beehive hexagonal layout.** Parked until Marko reactivates it. Do not start it.

---

## 6. How Marko works

He dictates most of his messages, so transcription artefacts appear in them; read for intent. He
sends annotated screenshots and they are usually the fastest route to the real problem — look at them
closely before reasoning from the text alone.

He is a filmmaker and painter, and judges the app visually. "It's screaming at me" is a real bug
report about contrast. He would rather have one thing done properly than four half-done, and he says
so.

He corrects course often and his corrections are usually right. When he says a thing was
misunderstood, it usually was. Build what he describes rather than what seems more sensible, and if
something genuinely will not work, say why plainly instead of quietly building something else.

Tell him what was *not* done and why. A short honest list of what remains is worth more than an
optimistic summary.
