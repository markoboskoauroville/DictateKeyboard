# Talk to Type — handoff and development plan

Written at build 88. Read this first, then `git log --oneline -20` to see anything newer.

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

**The name.** The app is **Talk to Type**, short form **TTT**. Renamed from Mantra Voice Type at
build 109. The **package id stays `com.mantraproductions.voicetype`** and must not be changed:
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
- **Renamed to Talk to Type (TTT)** at build 109. Package id unchanged, see the rule above.

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

**What it does.** Takes the text on the clipboard (or a selection), speaks it in a Microsoft Edge
neural voice, and highlights each word as it is spoken. It occupies exactly the keyboard's footprint
and uses the same scheme: near-black surfaces, gold ink, no decoration. Marko is dyslexic and the
word highlight is the point of the whole thing, not a flourish.

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

#### Build order

1. **The Kotlin voice client.** Highest technical risk, needed whichever way anything else goes.
   Voice in, audio file plus word boundaries out. Prove it against a Croatian sentence with a number
   in it, since that is where boundaries stop matching cleanly.
2. **The alignment port.** `align_tokens` and `refine_tokens` in Kotlin, tested against the same
   sentences the Python handles. This is where the quality lives.
3. **The reader view.** Third `ImeUiMode`, keyboard footprint, gold on near-black, the sweep, and
   play/pause. Two finger horizontal flip to enter and leave it.

#### Open questions, do not guess

- Whether the reader reads the clipboard automatically on entry, or waits for a play press.
- What happens at the end of a passage: stop, or return to the previous view.

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
