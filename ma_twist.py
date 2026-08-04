#!/usr/bin/env python3
"""
MA TWIST v1, applied to the Dictate source before Gradle runs.

1. Providers stripped to AssemblyAI, Gemini and Anthropic.
2. The pulsating dot replaced by an oscilloscope, with the stopwatch and a
   braille spinner in front of it.
3. Colours taken from the MA house palette used across Marko's apps.

Every edit is anchored on an exact string from upstream. If an anchor is missing
the script stops with a clear message instead of half patching, which is what you
want when upstream changes underneath you.
"""

import io
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
REG = "lib/dictate-core/src/main/kotlin/dev/patrickgold/florisboard/dictate/provider/ProviderRegistry.kt"
UI = "app/src/main/kotlin/dev/patrickgold/florisboard/dictate/ui/DictateSmartbarUi.kt"

changes = []


def read(path):
    with io.open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(os.path.join(ROOT, path), "w", encoding="utf-8") as f:
        f.write(text)


def swap(text, old, new, label):
    if old not in text:
        sys.exit("MA TWIST failed, anchor missing: %s" % label)
    if text.count(old) != 1:
        sys.exit("MA TWIST failed, anchor not unique: %s" % label)
    changes.append(label)
    return text.replace(old, new, 1)


def ensure_import(text, statement):
    if statement in text:
        return text
    lines = text.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    lines.insert(last + 1, statement)
    return "\n".join(lines)


# --------------------------------------------------------------- 1. PROVIDERS
reg = read(REG)
reg = swap(
    reg,
    """    val presets: List<ProviderPreset> = listOf(
        OPENAI, GROQ, OPENROUTER, GEMINI, ANTHROPIC, TOGETHER, DEEPINFRA, MISTRAL, SONIOX,
        ELEVENLABS, DEEPGRAM, ASSEMBLYAI, XAI, DEEPSEEK, OLLAMA, LOCAL,
    )""",
    """    // MA TWIST: three providers, nothing else. The other presets stay defined above so the
    // rest of the code that references them by name still compiles, they are simply not offered.
    val presets: List<ProviderPreset> = listOf(
        ASSEMBLYAI, GEMINI, ANTHROPIC,
    )""",
    "provider list",
)
write(REG, reg)

# --------------------------------------------------------------- 2. RECORDING UI
ui = read(UI)

for imp in [
    "import androidx.compose.foundation.Canvas",
    "import androidx.compose.foundation.layout.Box",
    "import androidx.compose.foundation.layout.height",
    "import androidx.compose.foundation.layout.width",
    "import androidx.compose.runtime.mutableIntStateOf",
    "import androidx.compose.runtime.mutableStateListOf",
    "import androidx.compose.runtime.setValue",
    "import androidx.compose.runtime.getValue",
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.Path",
    "import androidx.compose.ui.graphics.drawscope.Stroke",
    "import androidx.compose.ui.text.font.FontWeight",
    "import androidx.compose.ui.unit.sp",
    "import androidx.compose.material3.Text",
]:
    ui = ensure_import(ui, imp)

# The dot is now unused. Kotlin only warns about that, but silencing it keeps the build log clean.
ui = swap(
    ui,
    """@Composable
private fun RecordingAudioDot(paused: Boolean, frozen: Boolean = false) {""",
    """@Suppress("unused") // MA TWIST: replaced by MaRecordingScope, kept so upstream diffs stay small
@Composable
private fun RecordingAudioDot(paused: Boolean, frozen: Boolean = false) {""",
    "silence the unused dot",
)

ui = swap(
    ui,
    """        RecordingAudioDot(paused = state.paused, frozen = ptt.discarding)
        Spacer(modifier = Modifier.width(10.dp))
        SnyggText(text = formatElapsed(elapsedMs))""",
    """        // MA TWIST: oscilloscope behind, braille spinner and stopwatch in front.
        MaRecordingScope(paused = state.paused, frozen = ptt.discarding, elapsedMs = elapsedMs)""",
    "recording centre",
)

ui += '''

// --------------------------------------------------------------------------------
// MA TWIST, Mantra Productions
// The house palette Marko uses across his apps, so the keyboard matches maha_transcribe.
// --------------------------------------------------------------------------------

private val MaCyan = Color(0xFF39D0D8)
private val MaViolet = Color(0xFFA371F7)
private val MaMuted = Color(0xFF8B949E)
private val MaInk = Color(0xFFE6EDF3)
private const val MA_BRAILLE = "\\u280B\\u2819\\u2839\\u2838\\u283C\\u2834\\u2826\\u2827\\u2807\\u280F"

/** How many level samples the scope keeps on screen. */
private const val MA_SCOPE_POINTS = 64

/** Sampling period of the scope, fast enough to look alive, slow enough to cost nothing. */
private const val MA_SCOPE_TICK_MS = 50L

/** Braille frame rate while recording. */
private const val MA_SPINNER_TICK_MS = 90L

/**
 * The centre of the recording bar: a live oscilloscope drawn behind, with the braille spinner and the
 * elapsed stopwatch in front of it.
 *
 * The scope keeps a rolling window of [DictateController.audioLevel] rather than reading the raw PCM,
 * because that flow is already smoothed and published at a rate the UI can follow. Alternate samples
 * are mirrored above and below the centre line, which is what turns a level meter into something that
 * reads as a waveform.
 *
 * Paused or discarded, everything freezes and dims instead of animating, on the same reasoning as the
 * dot this replaces: motion should mean something is being captured.
 */
@Composable
private fun MaRecordingScope(paused: Boolean, frozen: Boolean, elapsedMs: Long) {
    val still = paused || frozen
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(still) {
        while (!still) {
            history.add(DictateController.audioLevel.value)
            if (history.size > MA_SCOPE_POINTS) history.removeAt(0)
            delay(MA_SCOPE_TICK_MS)
        }
    }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) {
        while (!still) {
            frame++
            delay(MA_SPINNER_TICK_MS)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(FlorisImeSizing.smartbarHeight)
            .width(158.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val points = history.size
            if (points < 2) return@Canvas
            val midY = size.height / 2f
            val step = size.width / (points - 1)
            val path = Path()
            for (i in 0 until points) {
                val amp = history[i].coerceIn(0f, 1f) * (size.height * 0.40f)
                val y = if (i % 2 == 0) midY - amp else midY + amp
                val x = step * i
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = MaCyan.copy(alpha = if (still) 0.18f else 0.45f),
                style = Stroke(width = 2.5f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MA_BRAILLE[frame % MA_BRAILLE.length].toString(),
                color = if (still) MaMuted else MaViolet,
                fontSize = 17.sp,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = formatElapsed(elapsedMs),
                color = MaInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
'''
changes.append("oscilloscope composable")
write(UI, ui)

print("MA TWIST applied:")
for c in changes:
    print("  - " + c)
