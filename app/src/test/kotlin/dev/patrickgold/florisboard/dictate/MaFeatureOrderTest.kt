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
        MaFeatureOrder.DEFAULT.size shouldBe 9
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
                parsed.size shouldBe 9
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
        MaFeatureOrder.move(d, 0, 8).last() shouldBe d[0]
        MaFeatureOrder.move(d, 8, 0).first() shouldBe d[8]
        MaFeatureOrder.move(d, 3, 7).size shouldBe 9
        MaFeatureOrder.move(d, 3, 7).toSet() shouldBe all
    }

    test("an impossible move is ignored rather than crashing") {
        val d = MaFeatureOrder.DEFAULT
        MaFeatureOrder.move(d, 4, 4) shouldBe d
        MaFeatureOrder.move(d, 99, 0) shouldBe d
        MaFeatureOrder.move(d, 0, 99) shouldBe MaFeatureOrder.move(d, 0, 8)
        MaFeatureOrder.move(d, 5, -3) shouldBe MaFeatureOrder.move(d, 5, 0)
    }

    // Dragging one key the length of the row and back must land exactly where it started.
    test("a full round trip drag restores the order") {
        var order = MaFeatureOrder.DEFAULT
        for (i in 0 until 8) order = MaFeatureOrder.move(order, i, i + 1)
        for (i in 8 downTo 1) order = MaFeatureOrder.move(order, i, i - 1)
        order shouldBe MaFeatureOrder.DEFAULT
    }

    test("ids are unique and resolvable") {
        MaFeatureKey.entries.map { it.id }.toSet().size shouldBe 9
        MaFeatureKey.entries.all { MaFeatureKey.byId(it.id) == it } shouldBe true
        MaFeatureKey.byId("nope") shouldBe null
    }
})
