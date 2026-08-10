/*
 * MA TWIST, Mantra Productions.
 * Licensed under the Apache License, Version 2.0.
 */

package dev.patrickgold.florisboard.app.settings

/**
 * The Mantra settings list, as data, so its order belongs to the person using it.
 *
 * Same argument as the feature row and the same shape of solution. The order of a list of links is
 * not a thing the person who wrote the list can know: it depends entirely on which four of them
 * somebody opens every week and which four they have opened twice ever.
 *
 * **The group headings are gone with it, and that is the real change.** "Start here", "Dictation",
 * "Saved", "Keyboard extras" were a filing system, and a filing system contradicts a free order: as
 * soon as History can sit above Recording, a heading saying *Saved* is either a lie or a cage. A flat
 * list that the user arranges is the honest version, and on a phone it is also two screens shorter.
 *
 * **Order only, nothing hidden.** As with the feature row: rearranging cannot lose anything, and for
 * a settings list the stakes are the same in kind, since API keys is on it and a keyboard with no
 * reachable key screen is a keyboard that cannot be fixed from inside itself.
 */
enum class MaSettingsEntry(val id: String, val title: String, val summary: String?) {
    LITTLE_MAN("little_man", "Little man", "His buttons, their prompts, and what he remembers"),
    FEATURE_ROW("feature_row", "Feature row", "Drag the nine keys into the order you want"),
    DEFAULT_VIEW("default_view", "Opening view", "Which view the keyboard shows when it appears"),
    MACROS("macros", "Macro bar", "Buttons that type text or press keys"),
    DB("db", "db", "The dashboard: what is on it, and how to open it"),
    KEYS("keys", "API keys", "Import, test and manage every key"),
    RECORDING("recording", "Recording", null),
    REWORDING("rewording", "Rewording (AI)", null),
    MAPPINGS("mappings", "Custom mappings", null),
    OUTPUT("output", "Output", null),
    HISTORY("history", "History", "Every transcription, with its audio"),
    RECOVERED("recovered", "Recovered recordings", "Audio saved when a recording was cut short"),
    SETTINGS_ORDER("settings_order", "Edit settings order", "Drag these entries into the order you want");

    companion object {
        fun byId(id: String): MaSettingsEntry? = entries.firstOrNull { it.id == id }
    }
}

/** Reads and writes the stored order. Pure, so it is provable in the sandbox. */
object MaSettingsOrder {

    /**
     * Marko's order, and the one a reset returns to.
     *
     * His instruction was "at the top the custom ones", and this is that read literally: the screens
     * that exist only in this app and that change how the keyboard behaves come first, then the
     * dictation settings in the sequence a dictation actually happens in, then the two archives, and
     * the editor for this very list last, where a thing used once belongs.
     *
     * `SETTINGS_ORDER` is deliberately in the list rather than pinned outside it. It is a link like
     * any other and somebody who wants it at the top should be able to drag it there.
     */
    val DEFAULT: List<MaSettingsEntry> = listOf(
        MaSettingsEntry.LITTLE_MAN,
        MaSettingsEntry.FEATURE_ROW,
        MaSettingsEntry.DEFAULT_VIEW,
        MaSettingsEntry.MACROS,
        MaSettingsEntry.DB,
        MaSettingsEntry.KEYS,
        MaSettingsEntry.RECORDING,
        MaSettingsEntry.REWORDING,
        MaSettingsEntry.MAPPINGS,
        MaSettingsEntry.OUTPUT,
        MaSettingsEntry.HISTORY,
        MaSettingsEntry.RECOVERED,
        MaSettingsEntry.SETTINGS_ORDER,
    )

    val DEFAULT_RAW: String = serialize(DEFAULT)

    /**
     * Parses a stored order and **always returns every entry**.
     *
     * Unknown ids drop, duplicates collapse, and anything missing is appended in default order. That
     * last clause is what makes a new settings screen safe to add later: it appears at the bottom for
     * people who have already arranged the list, rather than being invisible to exactly them.
     */
    fun parse(raw: String?): List<MaSettingsEntry> {
        val wanted = raw.orEmpty()
            .split(',')
            .mapNotNull { MaSettingsEntry.byId(it.trim()) }
            .distinct()
        return wanted + DEFAULT.filterNot { it in wanted }
    }

    fun serialize(order: List<MaSettingsEntry>): String = order.joinToString(",") { it.id }

    /** Moves the entry at [from] to [to], shifting the rest. A move, not a swap. */
    fun move(order: List<MaSettingsEntry>, from: Int, to: Int): List<MaSettingsEntry> {
        if (from !in order.indices) return order
        val target = to.coerceIn(0, order.size - 1)
        if (from == target) return order
        val out = order.toMutableList()
        out.add(target, out.removeAt(from))
        return out
    }
}
