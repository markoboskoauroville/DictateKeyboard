/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The Little Man's wagon labels and his editor.
 *
 * The summary is computed locally rather than asked of the model, so it has to be good on its own:
 * every case here is a way a label stops being scannable, which is the only job it has.
 */
class MaLittleManTest : FunSpec({

    test("leading filler is dropped so the distinguishing words are the label") {
        MaLittleMan.summarise("Can you please translate this to Croatian") shouldBe "Translate this to\u2026"
        MaLittleMan.summarise("Molim te skrati ovaj tekst") shouldBe "Skrati ovaj tekst"
    }

    test("a short instruction is its own summary") {
        MaLittleMan.summarise("Make it shorter") shouldBe "Make it shorter"
        MaLittleMan.summarise("fix the grammar") shouldBe "Fix the grammar"
    }

    test("nothing in nothing out, and filler alone still says something") {
        MaLittleMan.summarise("   ") shouldBe ""
        // Stripping the filler would empty this, so it falls back to the original rather than
        // producing a blank card with no way to tell what it is.
        MaLittleMan.summarise("please").isNotEmpty() shouldBe true
    }

    test("newlines and runs of spaces collapse") {
        MaLittleMan.summarise("fix\n\n  the   grammar").contains("\n") shouldBe false
    }

    test("a single very long word is cut rather than dropped") {
        MaLittleMan.summarise("Antidisestablishmentarianismically").isNotEmpty() shouldBe true
    }

    // Editing keeps its place, and this is the whole reason edited() exists instead of a remove
    // followed by a remember: remember() moves an instruction to the front, which is right when it
    // is used and wrong when it is corrected.
    test("an edited instruction stays where it was") {
        val list = listOf("third", "second", "first")
        MaLittleMan.edited(list, "second", "SECOND") shouldBe listOf("third", "SECOND", "first")
    }

    test("emptying an instruction deletes it") {
        val list = listOf("third", "second", "first")
        MaLittleMan.edited(list, "second", "   ") shouldBe listOf("third", "first")
    }

    test("editing onto an instruction already in the line collapses them") {
        val list = listOf("third", "second", "first")
        MaLittleMan.edited(list, "first", "third") shouldBe listOf("third", "second")
    }

    test("an instruction that is not there changes nothing") {
        val list = listOf("third", "second", "first")
        MaLittleMan.edited(list, "nope", "x") shouldBe list
    }
})
