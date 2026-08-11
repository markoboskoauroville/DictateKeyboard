# TTT&LLL — UX and User Interface handoff

**Talk to Type and Look, Listen, Learn.** Mantra Productions, for Marko Boško.

This document is about **what is on the screen and how it behaves**. No file paths, no build
instructions, no architecture. It is written for another AI that has to change this interface without
breaking the thinking behind it.

Read section 2 before touching anything. Almost every rule here is the survivor of a version that
looked more sensible and turned out to be wrong on a phone.

---

## 1. Who this is for, because it explains every choice

One person. He dictates by voice, works on a phone, has **low vision and dyslexia**, and switches
between jobs and projects all day. He reads the screen while moving.

Three consequences that shape everything:

- **Big targets, few of them.** Four voices, not forty. Two languages, not ninety.
- **Nothing may cost a glance it does not earn.** A row of controls that sits there doing nothing is
  not neutral; it is taking space from a phone that is already half keyboard.
- **State must be readable without pressing anything.** He should be able to look and know, not press
  and find out.

---

## 2. The five rules

Everything else follows from these. Break one and the interface stops being this interface.

### 2.1 Colour means state. Never decoration.

If something on screen is coloured, something is happening. Green means a zone is showing. Red means
recording. Amber means out of quota. Grey means untested. There is no coloured thing anywhere that is
coloured because it looks nice.

The palette is warm dark: near-black grounds, sand ink `#E8B15C`, recording red `#9B3B33`, warning
amber `#F0883E`, state green `#6FA85A`.

### 2.2 Nothing pulses, breathes or blinks.

There is no ambient animation in this app. The recording lamp is **steady**. Movement means something
changed. A blinking light on a strip that already has a moving meter and a running clock is a third
thing competing for an eye that has two other things to watch.

The one exception proves it: while a recording is being *sent*, the level meter becomes a slow
symmetrical sweep. There is no microphone left to display, and the alternative is a meter frozen at
whatever the last syllable happened to be, which reads as a crash. It is decoration and it is honest
about being decoration — it exists only during the wait.

### 2.3 Text where a picture would be a code.

`AP`, `ENG`, `HR`, `FAST`, `SLOW`, `1`, `2`, `3` are drawn as words and numerals, not icons, because
no glyph means them. The clipboard-all key is `AP` for exactly this reason: every clipboard glyph
already means one of the four keys beside it, so a fifth would be a riddle.

`ENG`, not `EN`. `EN` and `HR` are the same shape and the same weight; at a glance on a moving
keyboard they read as "some code" and telling them apart takes a moment of real reading. `ENG` does
not look like `HR`.

### 2.4 No label that only explains the other labels.

A word that is read once and then occupies the row forever is a word to delete. Controls say what
they are; nothing says what to do with them.

### 2.5 Say what is not available rather than showing zero.

No provider here exposes a credit balance, so the usage line reads *"no rate set"* beside a real
character count rather than `$0.00`. A zero is an answer. "Not available" is the truth.

---

## 3. The keyboard, top to bottom

Five bands. From the top:

```
┌──────────────────────────────────────────────┐
│  AUXILIARY ROW  — appears only when needed   │   ← §4
├──────────────────────────────────────────────┤
│  ZONE 3   copy and paste row                 │
│  ZONE 1   number row                         │   ← §5, foldable
│  ZONE 2   the letter keys                    │
├──────────────────────────────────────────────┤
│  CURSOR STRIP  — arrows, and the fold        │   ← §6
├──────────────────────────────────────────────┤
│  FEATURE ROW  — always there                 │   ← §7
└──────────────────────────────────────────────┘
```

The bottom two never disappear. Everything above them can be folded away, and when it all is, the
keyboard is two rows tall and still fully usable for dictation.

---

## 4. The auxiliary row — the part that appears and vanishes

**This is the most distinctive thing in the interface and the easiest to get wrong.**

It is the strip across the very top. It is **not** a permanent bar. It exists only when it has
something to say, and the rest of the time it does not occupy a single pixel.

### When it appears

Exactly four cases:

1. **Word suggestions** exist while typing.
2. **A recording is happening** — recording, paused, transcribing, rewording, or an error worth
   reading, or an interrupted recording waiting to be sent.
3. **The prompt strip** is showing, when text is selected and rewording is on.
4. Inline autofill suggestions arrive from the system.

Otherwise: gone. Not empty, not collapsed to a thin line — **absent**, with the keyboard moved up into
the space.

### Why it works this way

It used to be permanent, and Marko sent a screenshot of it holding the letters `EN` and nothing else,
across a full row, above a keyboard that is already the bottom half of the display. A row that is
usually empty is a row that is usually a waste.

### The two rules that make it safe

**One decision, read in two places.** The code that draws the row and the code that reserves its
height must agree. They are the same condition, computed once. When they disagreed elsewhere in this
app the result was keys that vanished while their space stayed behind — a band of nothing in the
middle of the keyboard.

**No animation on the appearance.** The height changes in one frame. A fade against an instant height
change is that same disagreement in slow motion: you see a gap while the fade finishes. The row
appears and disappears *with* its height, in the same frame.

### The recorder living there

When dictation starts, this row **becomes the recording bar**. Not a dialog, not an overlay, not a
new screen — the same strip, now showing:

```
  [🗑]   ENG  FAST      ● 0:14  ▁▃▅▂▇▃▁      [▮▮] [➤]
   bin  toggles      lamp timer meter        pause send
```

- **The red lamp sits immediately before the clock.** A red circle has meant "recording" on every
  camera and tape machine for fifty years; a number counting up only means recording once it has been
  read. The lamp is understood before it is read. It goes **dark while paused** — the one thing a
  steady lamp must get right, or it lies for as long as the pause lasts.
- **The timer is centred**, because it is the number always wanted. Anything placed to the left of the
  centred group pushes it off centre by its own width, and a stopwatch that is *nearly* centred reads
  as a mistake rather than a measurement. This is why the language and speed toggles sit inside the
  centred group rather than out at the edge.
- **Three readings, each on the side where it belongs:** level in dB on the left (changes fastest,
  glanced at), elapsed time in the centre, size on disk on the right (transfer figures while sending).
- **The readings do not overlap the meter.** They used to be drawn on top of it and were genuinely
  unreadable: dark text over a bar that is green in one place and pale in another has no reliable
  contrast anywhere. The readings have their own line above, in bold; the meter has the bottom of the
  box to itself.
- **`ENG`/`HR` and `FAST`/`SLOW` are live during the recording.** Both are read when the request is
  built, not when recording started, so changing either mid-sentence really changes what gets sent.
  This is why there is no "get ready" step before recording: the settings that matter are correctable
  while you are still speaking.

The whole point: **dictation never leaves the keyboard.** No second screen, no mode change, no losing
sight of the text field being typed into.

---

## 5. Zones — folding the keyboard away

The keyboard is three zones, numbered as they stack outward from the letters:

- **Zone 1** — the number row
- **Zone 2** — the letter keys themselves
- **Zone 3** — the copy and paste row along the top

Each has one switch, and each switch is one key on the feature row, labelled `1`, `2`, `3`. **Green
when showing, dark when folded.** The three keys are a map of what is above them, readable without
pressing anything.

**Each key shows its own switch, not what is visible.** Folding the letters away with `2` takes the
number row with it, because the number row lives inside that zone — but it must not erase the decision
about whether the number row should be there when the letters come back. So `1` can be green while
nothing is visible, and that is the truth about the switch rather than a bug.

With zone 2 shut the keyboard becomes a dictation instrument: no letters, just the cursor strip and
the feature row, and the whole screen given back to the app being dictated into.

---

## 6. The cursor strip

A short row of arrowheads above the feature row: fold, up, down, left, right. Four-way cursor
movement without leaving the keyboard.

**The fold control lives here, not in the row it hides.** A switch that lives inside the thing it
hides must keep a slot of that thing forever — the feature row would permanently be nine features and
a switch. Here it costs nothing: this strip was four glyphs spread across the full width with room to
spare, and the feature row keeps all ten of its slots.

Both arrowheads are drawn in one place: **apart when the row is open, together when it is closed.**
That is not a direction, it is a height. Same vocabulary as the four arrows beside it.

---

## 7. The feature row — the row that always survives

Ten round keys across the bottom. **This row never folds away**, and that is a safety property, not a
preference: it is the only route to backspace, to enter, and to the microphone when everything else is
shut.

Default order:

```
AP · select-all · backspace · mic · book · 1 · 2 · 3 · enter · (little man, off by default)
```

**The order is the user's, not the designer's.** It is rearranged by hold-and-drag in the settings, and
it has been corrected by hand twice — both times from a screenshot with arrows drawn on it, and **both
times in the direction opposite to what looked sensible from inside the code.** That is the argument
for the editor existing at all. Do not rearrange this row on a theory.

What the current order means, so a future change knows what it would be breaking:

- **The busy end is the end the thumb starts from.** `AP`, select-all and backspace are used inside a
  sentence; the zone switches a few times a session.
- **The two view swaps sit together.** The microphone and the book are the only keys that change which
  view is on screen, and a pair doing one kind of thing is found by feel. They used to be at opposite
  ends, which is precisely what made that hard.
- **Enter is bottom right**, where every keyboard ever made has put it. Borrowing a habit somebody
  already has beats any argument about grouping.
- **The numbers read left to right** as the parts of the keyboard they control.

**Long press on most keys folds the row away.** Not on `AP`, select-all, backspace or enter: backspace
holds to repeat and swipes to select, and a key that repeats cannot also mean something else when held.

**Three keys can be reordered but never switched off:** microphone, backspace, enter. Rearranging can
never lock anyone out of anything; hiding can.

---

## 8. Views

- **Keyboard** — typing. The default when the keyboard opens.
- **Dictation** — a fuller recording screen, opened by the microphone key.
- **Reader (LLL)** — speaks text aloud and lights each word as it is said.
- Clipboard, history and emoji panels.

**The keyboard is the opening view**, always, unless changed in settings. Reopening whichever view was
last used sounds helpful and means the keyboard you get depends on something done in another app an
hour ago — and a text field that greets you with a live microphone is a surprise every time it
happens.

---

## 9. Hardware keys

- **Volume up** — start recording. Press again to stop and send.
- **Volume down** — throw away a recording in progress. With nothing recording, switch language.

Down always means the smaller state. Up always means the next step. That symmetry is what lets them be
used without looking, which is the entire point of putting anything on a hardware key.

---

## 10. The Little Man AI Assistant

An assistant that takes spoken instructions ("make this shorter", "translate to Croatian") and applies
them to the text.

His row is a **train**: he is the locomotive at the head, fixed, and every instruction ever spoken to
him is a wagon behind it. **The wagons scroll horizontally; the locomotive never does** — it is the
button that starts a new instruction, and a head that can be scrolled off is a head lost behind its own
wagons.

Each wagon: **summary above, prompt below.** The summary is what the eye lands on; the prompt
underneath confirms it is the right one. Fixed width, because carriages of varying width give the eye
nothing to count along.

**Tap runs it. Long press opens an editor.** The editor exists for a narrow, real reason: these
instructions arrived by voice, voice gets one word wrong, and re-speaking a whole sentence to fix a
word is exactly what it saves. An edited wagon **keeps its place in the line** — being corrected is not
being used, and a card that jumped to the front because a typo was fixed would lose the place the eye
had.

---

## 11. Feedback and messages

- **Toasts, not banners.** Brief, at the bottom, gone.
- **Say the slow thing before the wait, not after.** The screenshot reader announces "Reading the
  screenshot" *before* going to the network, because a key that appears to do nothing gets pressed
  again — and the second press sends a second screenshot and pays for it.
- **Distinguish failures that send the user to different places.** "Out of credit" and "key rejected"
  and "no connection" are three different sentences, because each one is fixed somewhere else.
- **Never show a verdict for something that is not the thing's fault.** A rate limit hit while testing
  many keys says nothing about any key; showing it as "no quota" paints a healthy key amber and invites
  the user to delete it.

---

## 12. Settings

Two tabs: **Mantra** (this app) and **FlorisBoard** (what survived from the fork). Always opens on
Mantra.

The Mantra tab is a **flat list with no group headings**, arranged by the user with hold-and-drag. The
headings were removed deliberately: they were a filing system, and a filing system contradicts a free
order. The moment *History* can sit above *Recording*, a heading saying *Saved* is either a lie or a
cage.

**Editors never hide, only reorder** — except the feature row, which can hide the seven keys that are
safe to hide.

---

## 13. If you change one thing, check these

- Did you add colour that is not state?
- Did you add something that moves on its own?
- Does the auxiliary row still reserve exactly the height it draws, in the same frame?
- Can the keyboard still delete a character, end a line, and reach dictation with every zone folded?
- Is the timer still centred, and does the lamp still go dark when paused?
- Did you add a label whose only job is explaining the labels next to it?
- Did you rearrange something the user arranges themselves?
