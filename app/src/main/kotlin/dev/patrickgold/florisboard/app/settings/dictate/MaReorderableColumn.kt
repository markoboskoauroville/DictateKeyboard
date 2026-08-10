/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * A list whose rows can be held and dragged into a different order.
 *
 * Extracted from the feature row editor when the settings list needed exactly the same thing. Two
 * copies of drag arithmetic is how the two quietly stop behaving alike, and this is the kind of code
 * where "quietly different" means one list swaps where the other shifts and nobody can say why.
 *
 * **Written by hand rather than pulled from a library, and the reason is [rowHeight].** With every
 * row the same fixed height, the target index is the drag distance divided by that height, and there
 * is nothing else to it. A library would buy a dependency, a migration and a set of behaviours to
 * learn, in exchange for one division. If these rows ever gain different heights this calculation has
 * to be **replaced** rather than adjusted, and that is the whole warning.
 *
 * The caller owns the order and is told when it changes: [onMove] fires during the drag so the list
 * moves under the finger, and [onSettled] fires when the finger lifts so the caller can persist once
 * instead of on every frame.
 */
@Composable
fun <T> MaReorderableColumn(
    items: List<T>,
    rowHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T, lifted: Boolean) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    // Read inside the gesture without being part of its key. See the comment on pointerInput below.
    val liveItems by rememberUpdatedState(items)
    val liveOnMove by rememberUpdatedState(onMove)
    val liveOnSettled by rememberUpdatedState(onSettled)
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = draggingIndex == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    // The dragged row rides above its neighbours and follows the finger, while the
                    // rest sit still and let the list re-sort underneath it.
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                    // KEYED ON index ALONE, never on items.
                    //
                    // With `items` in the key this restarted the moment the order changed, which is
                    // the moment the first swap happened, and restarting a pointerInput cancels the
                    // gesture running inside it. The symptom was a drag that moved a row exactly one
                    // place and then died, every time, which is what Marko reported. The live list is
                    // read through rememberUpdatedState instead, so the lambda always sees the
                    // current one without the input ever being torn down mid-drag.
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                // Rounding, not truncating, so the swap happens once the row is more
                                // than halfway over its neighbour, which is the moment the eye
                                // expects rather than a full row later.
                                val moved = (dragOffset / rowHeightPx).roundToInt()
                                if (moved != 0) {
                                    val target = (from + moved).coerceIn(0, liveItems.size - 1)
                                    if (target != from) {
                                        liveOnMove(from, target)
                                        draggingIndex = target
                                        // Rebased to the NEW slot. Without this the row jumps a full
                                        // height at the exact moment it changes places.
                                        dragOffset -= (target - from) * rowHeightPx
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                                liveOnSettled()
                            },
                            // A cancelled drag KEEPS the order it reached. The rows have already
                            // moved on screen, and snapping them back reads as the app undoing
                            // something the user watched happen.
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                                liveOnSettled()
                            },
                        )
                    },
            ) {
                row(index, item, isDragging)
            }
        }
    }
}
