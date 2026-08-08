# Mantra Voice Type — handoff and development plan

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

**Releases.** Only one release may exist at any time, always the newest. CI deletes older releases
and their tags automatically after each successful publish. An old APK on the releases page is an old
APK somebody downloads.

**Version codes.** CI passes `1000 + run_number`. The offset is permanent. Lowering it makes a build
look older than one already installed, and Android refuses the install — this happened at build 62
and produced a misleading "package appears to be invalid" dialog.

**Design language.** Near-black surfaces. Warm gold ink `#E8B15C`. Colour is reserved for state, never
decoration. Red `#9B3B33` means recording and nothing else means red. Selection is shown by a gold
outline stroke, never a filled block. No pulsing, breathing or scaling animation anywhere. Minimum
text on buttons — an icon that is clear needs no label beside it.

**Prose in commits and replies.** No dashes or em dashes as connectors. Months as numbers, never
names.

---

## 3. Traps that have caught me repeatedly

Each of these cost at least one wasted build. They are listed because they will catch the next
session too.

**A changed default never reaches a written preference.** Changing `default =` in `AppPrefs` does
nothing for an install that already has a value. Every such change needs a one-shot pass in
`DictateLegacyMigrator`, and the migration flag key must be bumped
(`dictate__ma_row_vN_applied`, currently **v13**) or the pass will not run again. This caught me with
the action row, the smartbar arrangement, one-handed mode and the text case.

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

**Before pushing**, run the brace balance check (`/home/claude/kbal.py`), the icon check, and a sweep
for `R.string.*` references that have no definition. Expect roughly one CI failure per non-trivial
change anyway; there is no Kotlin compiler in the sandbox.

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
ime/
  keyboard/KeyboardManager.kt    key handling, the central switch
  keyboard/ComputingEvaluator.kt icons per key code
  text/key/KeyCode.kt            Marko's codes are -219 and below
  smartbar/quickaction/          the three-dots panel
app/settings/                    settings screens, two tabs: Mantra and FlorisBoard
```

**The audio pipeline**, settled and not to be revisited without reason: capture at the device's best
rate (48 kHz where available) → resample to 16 kHz mono with a windowed-sinc filter → encode AAC →
send. Each step deletes what it replaces. Opus was removed: Android's own encoder produced files that
transcribed slowly, while FFmpeg's Opus of the same audio did not.

---

## 5. The plan

Order matters. Items are sequenced so that a failure is contained and so that each one leaves the app
in a usable state.

### Done at build 89: the Ctrl key and modifier locking

Built as described. `?123` is now **ctrl** in the bottom left corner, the same width as Shift so the
two make a modifier column, and the layout switch sits where the smiley was. Tap arms for one key,
long press locks, tap while locked releases. Colour carries the state: gold armed, green `#6FA85A`
with a matching outline locked. `Ctrl+A/C/X/V/Z` go to the editor operations that already existed,
`Ctrl+Shift+Z` and `Ctrl+Y` redo, every other letter falls through to a real ctrl+key event, which is
what gives `Ctrl+B` and `Ctrl+I` where the app understands them. Shift locked plus an arrow extends
the selection, through `isManualSelectionMode`, so no new mechanism was added for it.

Where it lives: `MaCtrlState` (new, three states, `toString` lowercase so a stylesheet can match it),
a two-bit region at offset 18 in `KeyboardState`, `handleCtrlUp` / `handleCtrlLock` /
`maHandleCtrlCombo` / `maSyncSelectionLock` in `KeyboardManager`, a `KeyCode.CTRL` branch in the
`onLongPress` block of `TextKeyboardLayout`, `modifierLock()` in `InputFeedbackController`, and the
`ctrlstate` attribute added to `FlorisImeUi.Attr` and to every key's attribute map.

Two things worth knowing next time. Arrows are deliberately **not** read as Ctrl combinations: they
open a mass selection on key down that must be closed on key up, so swallowing their key up would
leave it open. And `maCtrlJustLocked` exists because a long press sends `CTRL_LOCK` and then lets the
Ctrl key up through; without it the finger lifting would release the lock it had just made. It is
cleared on cancel too, or a finger sliding off the key would leave it stale and swallow the next tap.

**Still open, one answer needed.** The symbols view was left alone, so `ABC` stays in the far left
corner while `?123` now sits beside the space bar, and the round trip is asymmetric. Mirroring it is
two lines. Do not guess which way.

Also unverified on a device: if the green never appears, the first thing to rule out is a saved copy
of the Sunrise theme in internal storage shadowing the bundled asset. That only happens if the theme
editor was ever opened and saved.

### Next: the language row in the keyboard view

**Blocked on one answer from Marko, ask before building.** HR / EN / AUTO currently set the
*transcription* language. He wants the same row in the keyboard view, also driving the *keyboard's*
suggestion language, so Croatian suggestions appear when typing Croatian.

The question: should the two always be locked together, or should the keyboard view's row be a
separate choice? He might dictate Croatian into an English field. This changes the design, so do not
guess.

The copy/paste bar belongs in the same new row, between the suggestion strip and the keys — the space
freed by the fix in build 88.

### After that: the keyboard-view recorder made identical to the transcribe view

The meter is already shared (`MaRecordMeter`). What differs is the surrounding bar. Make the two
byte-identical so starting a recording from either place looks like one feature.

### Smaller items, any order

- The trailing space after a recased word
- Retranscribe button in both views for an interrupted recording (the Recovered screen covers part of
  this)
- Verbose upload feedback: phase, size, percent, speed
- The braille spinner must never silently freeze; say what it waits for
- Personal n-gram word prediction
- Theme manager: watch the Downloads folder and import a `.flex` automatically

### Parked, explicitly

**Beehive hexagonal layout.** Marko's instruction: not until he reactivates it.

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
