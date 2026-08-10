/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate

/**
 * The nine keys of the feature row, as data, and the order they are drawn in.
 *
 * The order has been corrected by hand twice, at builds 139 and 146, from screenshots with arrows
 * drawn on them. Both corrections went the opposite way to what looked sensible from inside the
 * code, which is the argument for this file: the person using the keyboard should not have to send a
 * screenshot and wait for a build to move a key.
 *
 * **Order only. Nothing can be hidden, and that is a safety rule rather than a simplification.**
 * This row is the one that survives when every other row is folded away. `MIC` is the only way to
 * reach the dictation screen with zone two shut, and `BACKSPACE` and `ENTER` are the only keys from
 * the keyboard proper with no substitute anywhere. An editor that could hide those could leave the
 * keyboard with no way to delete a character, no way to end a line, and no way to reach the feature
 * that the app is named after, with no way back except the settings app. Rearranging cannot lock
 * anybody out of anything; hiding can.
 */
enum class MaFeatureKey(val id: String, val label: String) {
    ALL_PASTE("ap", "Paste all"),
    SELECT_ALL("select_all", "Select all"),
    BACKSPACE("backspace", "Backspace"),
    MIC("mic", "Microphone, and the way back"),
    BOOK("book", "Reader"),
    ZONE_1("zone1", "1, the number row"),
    ZONE_2("zone2", "2, the keys"),
    ZONE_3("zone3", "3, the copy row"),
    ENTER("enter", "Enter");

    companion object {
        fun byId(id: String): MaFeatureKey? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Reads and writes the stored order.
 *
 * Pure, so it can be tested in the sandbox rather than discovered to be wrong by CI, and separate
 * from the composable that draws the row so that both the row and the editor read the same thing.
 */
object MaFeatureOrder {

    /**
     * Marko's order at build 146, and the one a reset returns to.
     *
     * The busy keys first, at the end the thumb starts from. The two view swaps together, because
     * they are the only pair here that changes what is on screen. The zone switches in the middle,
     * reading left to right as the parts of the keyboard they control. Enter last, where every
     * keyboard ever made has put it.
     */
    val DEFAULT: List<MaFeatureKey> = listOf(
        MaFeatureKey.ALL_PASTE,
        MaFeatureKey.SELECT_ALL,
        MaFeatureKey.BACKSPACE,
        MaFeatureKey.MIC,
        MaFeatureKey.BOOK,
        MaFeatureKey.ZONE_1,
        MaFeatureKey.ZONE_2,
        MaFeatureKey.ZONE_3,
        MaFeatureKey.ENTER,
    )

    val DEFAULT_RAW: String = serialize(DEFAULT)

    /**
     * Parses a stored order, and **always returns all nine keys**.
     *
     * Unknown ids are dropped, duplicates collapse to their first appearance, and anything missing is
     * appended in default order. That last part is what makes this safe to change later: a tenth key
     * added in some future build appears at the end for everybody who already has a saved order,
     * instead of being invisible to exactly the people who had customised the row. And a truncated or
     * garbled preference degrades to the default rather than to a keyboard with no enter key.
     */
    fun parse(raw: String?): List<MaFeatureKey> {
        val wanted = raw.orEmpty()
            .split(',')
            .mapNotNull { MaFeatureKey.byId(it.trim()) }
            .distinct()
        return wanted + DEFAULT.filterNot { it in wanted }
    }

    fun serialize(order: List<MaFeatureKey>): String = order.joinToString(",") { it.id }

    /**
     * Moves the key at [from] to [to], shifting the rest along.
     *
     * A move, not a swap. Dragging a key from one end of a row to the other should slide everything
     * it passes over by one place, which is what the eye expects from watching the drag; a swap would
     * fling whatever happened to be at the far end back to where the drag began.
     */
    fun move(order: List<MaFeatureKey>, from: Int, to: Int): List<MaFeatureKey> {
        if (from !in order.indices) return order
        val target = to.coerceIn(0, order.size - 1)
        if (from == target) return order
        val out = order.toMutableList()
        out.add(target, out.removeAt(from))
        return out
    }
}
