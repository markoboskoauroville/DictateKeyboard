/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The feature row order, and mostly one question asked nine ways: **can a damaged preference ever
 * produce a row with fewer than nine keys?**
 *
 * It matters more than it looks. This row is the one that survives when every other row is folded
 * away, so it is the only route to backspace, to enter and to the microphone. A parse that dropped a
 * key on a truncated string would leave somebody with a keyboard that cannot end a line and no way
 * back except the settings app.
 */
class MaFeatureOrderTest : FunSpec({

    val all = MaFeatureKey.entries.toSet()

    test("the default is all nine and round trips") {
        MaFeatureOrder.DEFAULT.size shouldBe MaFeatureKey.entries.size
        MaFeatureOrder.DEFAULT.toSet() shouldBe all
        MaFeatureOrder.parse(MaFeatureOrder.DEFAULT_RAW) shouldBe MaFeatureOrder.DEFAULT
    }

    // Every one of these once had a plausible way of losing a key. None of them may.
    test("every damaged preference still yields all nine keys") {
        val damaged = listOf(
            null,
            "",
            "@@@,,,###",
            "ap,select_all,back",
            "ap,teleport,mic,warp",
            "mic,mic,mic,enter,enter",
            "mic,,,enter,",
            "  mic , enter ,book ",
        )
        for (raw in damaged) {
            val parsed = MaFeatureOrder.parse(raw)
            withClue("lost a key on: " + raw) {
                parsed.size shouldBe MaFeatureKey.entries.size
                parsed.toSet() shouldBe all
            }
        }
    }

    test("what is stored comes first and the rest follow in default order") {
        MaFeatureOrder.parse("enter,mic").take(2) shouldBe
            listOf(MaFeatureKey.ENTER, MaFeatureKey.MIC)
        // A key added in some future build has to appear for the people who already customised the
        // row, not be invisible to exactly them.
        MaFeatureOrder.parse("enter").drop(1) shouldBe
            MaFeatureOrder.DEFAULT.filter { it != MaFeatureKey.ENTER }
        MaFeatureOrder.parse("mic,enter,mic").take(2) shouldBe
            listOf(MaFeatureKey.MIC, MaFeatureKey.ENTER)
    }

    // A move, not a swap. Dragging a key across the row should slide everything it passes over by
    // one place, which is what the eye expects from watching the drag. A swap would fling whatever
    // sat at the far end back to where the drag began.
    test("move shifts rather than swaps") {
        val d = MaFeatureOrder.DEFAULT
        MaFeatureOrder.move(d, 0, 2) shouldBe listOf(d[1], d[2], d[0]) + d.drop(3)
        MaFeatureOrder.move(d, 0, d.size - 1).last() shouldBe d[0]
        MaFeatureOrder.move(d, d.size - 1, 0).first() shouldBe d[d.size - 1]
        MaFeatureOrder.move(d, 3, 7).size shouldBe MaFeatureKey.entries.size
        MaFeatureOrder.move(d, 3, 7).toSet() shouldBe all
    }

    test("an impossible move is ignored rather than crashing") {
        val d = MaFeatureOrder.DEFAULT
        MaFeatureOrder.move(d, 4, 4) shouldBe d
        MaFeatureOrder.move(d, 99, 0) shouldBe d
        MaFeatureOrder.move(d, 0, 99) shouldBe MaFeatureOrder.move(d, 0, d.size - 1)
        MaFeatureOrder.move(d, 5, -3) shouldBe MaFeatureOrder.move(d, 5, 0)
    }

    // Dragging one key the length of the row and back must land exactly where it started.
    test("a full round trip drag restores the order") {
        var order = MaFeatureOrder.DEFAULT
        val last = order.size - 1
        for (i in 0 until last) order = MaFeatureOrder.move(order, i, i + 1)
        for (i in last downTo 1) order = MaFeatureOrder.move(order, i, i - 1)
        order shouldBe MaFeatureOrder.DEFAULT
    }

    // The safety rule, asserted at the point the value is READ, not where it is set. A preference
    // edited by hand or restored from an old backup must still not be able to produce a keyboard
    // with no enter key.
    test("the three keys that must survive can never be switched off") {
        val everything = MaFeatureKey.entries.joinToString(",") { it.id }
        val hidden = MaFeatureOrder.parseHidden(everything)
        for (key in MaFeatureOrder.ALWAYS_ON) {
            withClue(key.id + " must never be hideable") { (key in hidden) shouldBe false }
        }
        val visible = MaFeatureOrder.visible(MaFeatureOrder.DEFAULT, hidden)
        visible.toSet() shouldBe MaFeatureOrder.ALWAYS_ON
        MaFeatureOrder.serializeHidden(MaFeatureKey.entries.toSet())
            .split(",").mapNotNull { MaFeatureKey.byId(it) }.toSet()
            .intersect(MaFeatureOrder.ALWAYS_ON) shouldBe emptySet()
    }

    test("switching a key off removes it from the row and nothing else") {
        val hidden = setOf(MaFeatureKey.BOOK)
        val visible = MaFeatureOrder.visible(MaFeatureOrder.DEFAULT, hidden)
        visible.size shouldBe MaFeatureOrder.DEFAULT.size - 1
        (MaFeatureKey.BOOK in visible) shouldBe false
        // Order of the survivors is untouched.
        visible shouldBe MaFeatureOrder.DEFAULT.filter { it != MaFeatureKey.BOOK }
    }

    test("the little man starts switched off so no row silently gains a key") {
        (MaFeatureKey.LITTLE_MAN in MaFeatureOrder.DEFAULT) shouldBe true
        (MaFeatureKey.LITTLE_MAN in MaFeatureOrder.DEFAULT_HIDDEN) shouldBe true
        MaFeatureOrder.visible(MaFeatureOrder.DEFAULT, MaFeatureOrder.DEFAULT_HIDDEN)
            .contains(MaFeatureKey.LITTLE_MAN) shouldBe false
    }

    test("ids are unique and resolvable") {
        MaFeatureKey.entries.map { it.id }.toSet().size shouldBe MaFeatureKey.entries.size
        MaFeatureKey.entries.all { MaFeatureKey.byId(it.id) == it } shouldBe true
        MaFeatureKey.byId("nope") shouldBe null
    }
})
