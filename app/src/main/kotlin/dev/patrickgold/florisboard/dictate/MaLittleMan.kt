/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.dictate

/**
 * The Little Man AI Assistant's own logic: what a wagon is labelled, and how one is edited.
 *
 * Kept apart from the row that draws it so it can be compiled and run in the sandbox. Everything in
 * here is a pure function of a string.
 */
object MaLittleMan {

    /** Longest a summary may be before it is cut. Two or three words at a glance, not a sentence. */
    const val SUMMARY_CHARS = 22

    /**
     * The short label above a prompt in its wagon.
     *
     * **Computed here, not asked of the model.** A summary from the assistant would cost a request,
     * a wait and money per prompt, to label something Marko wrote himself and will recognise from its
     * own first words. The opening of an instruction is almost always the instruction: "translate to
     * Croatian", "make this shorter", "fix the grammar". The first few words are the summary.
     *
     * Leading filler is dropped first, because a list where six wagons all read "can you" is a list
     * that has to be read rather than scanned, and the distinguishing words are the ones after it.
     */
    fun summarise(prompt: String): String {
        var text = prompt.trim().replace(WHITESPACE, " ")
        if (text.isEmpty()) return ""
        for (filler in FILLERS) {
            if (text.startsWith(filler, ignoreCase = true)) {
                text = text.substring(filler.length).trimStart()
                break
            }
        }
        if (text.isEmpty()) text = prompt.trim().replace(WHITESPACE, " ")
        val firstLetter = text.firstOrNull()
        if (firstLetter != null && firstLetter.isLowerCase()) {
            text = firstLetter.uppercaseChar() + text.substring(1)
        }
        if (text.length <= SUMMARY_CHARS) return text
        // Cut at a word boundary when there is one reasonably near the limit, so the label ends on a
        // word rather than mid-syllable. Below that, an ellipsis says more than half a word does.
        val cut = text.lastIndexOf(' ', SUMMARY_CHARS)
        val head = if (cut >= SUMMARY_CHARS / 2) text.substring(0, cut) else text.substring(0, SUMMARY_CHARS)
        return head.trimEnd().trimEnd(',', ';', ':', '.') + "\u2026"
    }

    /**
     * Replaces one prompt with another, keeping its place in the line.
     *
     * **Its place, deliberately.** [MaLivePrompts.remember] moves a reused instruction back to the
     * front, which is right when it is used and wrong when it is corrected: a wagon edited in place
     * should stay where the eye last saw it, not jump to the locomotive. Editing is not using.
     *
     * An edit that collides with an instruction already in the list collapses onto it rather than
     * creating a duplicate, and the surviving copy keeps the earlier position.
     */
    fun edited(list: List<String>, old: String, new: String): List<String> {
        val text = new.trim()
        val index = list.indexOf(old)
        if (index < 0) return list
        if (text.isEmpty()) return list.filterNot { it == old }
        val out = list.toMutableList()
        out[index] = text
        // distinct() keeps the first occurrence, which is the earlier position.
        return out.distinct()
    }

    private val WHITESPACE = Regex("\\s+")

    /**
     * Openings that carry no information. Ordered longest first, so "can you please" is stripped
     * whole rather than leaving "please" behind.
     */
    private val FILLERS = listOf(
        "can you please ", "could you please ", "would you please ",
        "can you ", "could you ", "would you ", "please ",
        "i want you to ", "i would like you to ", "molim te ", "možeš li ",
    )
}
