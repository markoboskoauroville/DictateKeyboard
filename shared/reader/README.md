# shared/reader

Code used by **two** projects and owned by neither:

- **LLL**, the reader view inside Talk to Type, in this repository.
- **MA Reader Android**, the standalone port, in `markoboskoauroville/MA_READER_ANDROID`.

Both need a Kotlin client for the Microsoft Edge speech WebSocket, and both need the word timing
alignment ported from Marko's MA Reader v26. Written twice, they drift, and the symptom is a
highlight that no longer lands on the word being spoken. A fix applied to one copy silently never
reaches the other.

**This folder is the canonical home.** Not because this repository is more important, but because
somewhere has to be, and this one already exists and already builds.

## The rule

Every shared file starts with a header line exactly like this:

```kotlin
// SHARED  MaEdgeVoice.kt  v1  2026-08-09  canonical: DictateKeyboard/shared/reader/
```

Before touching a shared file, in either project:

1. Read the copy here **and** the copy in the other project.
2. Compare the version and the date on the header line. **The newer one wins.** Take it whole; do not
   merge by hand, because a half-merged alignment table is worse than either version.
3. Make the change.
4. Bump the version, set today's date, and **push it back here in the same session.**

Step 4 is the whole point. Steps 1 to 3 alone are a rule for copying, and copying is what this folder
exists to stop. If the change does not come back here, the next session compares dates against
something already stale and the drift starts again.

## What lives here

Nothing yet. The first project to need it writes it.

Expected:

- `MaEdgeVoice.kt` — the Edge speech WebSocket client. Voice and text in, audio plus word boundaries
  out. Ask for `WordBoundary` explicitly: edge-tts 7.x changed its default to `SentenceBoundary` and
  the old construction silently returns no word events at all.
- `MaAlign.kt` — the port of `align_tokens` and `refine_tokens`. Port faithfully, branch for branch.
  Every branch in the original exists because something went wrong once, and a cleaner rewrite loses
  the recovery cases in a way that does not show up until a paragraph has a number in it.

## Marko's phrasing, so it is not lost

He asked that the two projects "compare dates and pick up the latest one". That is exactly the rule
above, with the fourth step added, because comparing dates only converges if there is one place the
newer version goes back to.
