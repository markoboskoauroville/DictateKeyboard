/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prompts

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateReasoningEffort

/**
 * Storage for user-defined rewording prompts.
 *
 * IMPORTANT (data contract – see `docs/COMPATIBILITY.md`):
 * Database name (`prompts.db`) and the legacy `PROMPTS` columns are FROZEN so that existing Dictate
 * users keep their prompts after the in-place app update. New columns may only be ADDED via a bumped
 * version + an `onUpgrade` `ALTER TABLE … ADD COLUMN` (as done for `AUTO_APPLY` in v2 and
 * `REASONING_EFFORT` in v3). Do not migrate this to Room without a deliberately tested migration –
 * Room's strict type-affinity validation would reject the legacy `BOOLEAN`/`INTEGER` columns of an
 * existing user database.
 *
 * Lifecycle (issue #138): obtain via [getInstance] and NEVER call `close()` on the returned
 * [SQLiteDatabase]. `SQLiteOpenHelper` hands out one shared, reference-counted database whose
 * connection pool is meant to stay open for the process lifetime. Closing it per query disposed the
 * pool out from under a concurrent, in-flight cursor (overlapping `reload` on the Prompts screen) →
 * `IllegalStateException: connection pool has been closed`. The single process-wide instance is safe
 * to use from multiple threads/coroutines; SQLite's own connection pool serialises access.
 */
class PromptsDatabaseHelper private constructor(
    private val context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE PROMPTS (ID INTEGER PRIMARY KEY, POS INTEGER, NAME TEXT, PROMPT TEXT, " +
                "REQUIRES_SELECTION BOOLEAN, AUTO_APPLY BOOLEAN DEFAULT 0, REASONING_EFFORT TEXT, " +
                "REASONING_EFFORT_CUSTOM TEXT)"
        )
        // Seed the example prompts for fresh installs only (existing users skip onCreate). These are
        // the same defaults the legacy Dictate app shipped, resolved from string resources so they are
        // localized (e.g. German) just like the original.
        defaultSeeds().forEachIndexed { index, seed ->
            val cv = ContentValues().apply {
                put("POS", index)
                put("NAME", seed.name)
                put("PROMPT", seed.prompt)
                put("REQUIRES_SELECTION", if (seed.requiresSelection) 1 else 0)
                put("AUTO_APPLY", 0)
            }
            db.insert("PROMPTS", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE PROMPTS ADD COLUMN AUTO_APPLY BOOLEAN DEFAULT 0")
        }
        if (oldVersion < 3) {
            // Per-prompt reasoning-effort override (issue #155). Nullable TEXT holding the enum name;
            // NULL = fall back to the global reasoning setting.
            db.execSQL("ALTER TABLE PROMPTS ADD COLUMN REASONING_EFFORT TEXT")
        }
        if (oldVersion < 4) {
            // Custom wire value used when REASONING_EFFORT is CUSTOM (issue #186).
            db.execSQL("ALTER TABLE PROMPTS ADD COLUMN REASONING_EFFORT_CUSTOM TEXT")
        }
    }

    fun add(model: PromptModel): Int {
        val db = writableDatabase
        val cv = model.toContentValues()
        return db.insert("PROMPTS", null, cv).toInt()
    }

    fun addAll(models: List<PromptModel>?) {
        if (models.isNullOrEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            models.forEach { db.insert("PROMPTS", null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceAll(models: List<PromptModel>?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("PROMPTS", null, null)
            models?.forEach { db.insert("PROMPTS", null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Replaces the prompt list with the shipped seeds, once, for an install that already has the
     * fork's originals.
     *
     * Only when nothing has been added or edited: the count and the names must both still match the
     * old starter set exactly. Somebody who has written their own prompts keeps them, because
     * overwriting a person's own work to install a better default is not an improvement.
     */
    fun replaceStarterSetIfUntouched(): Boolean {
        val current = getAll()
        val oldNames = setOf(
            "Fix Grammar", "Improve Writing", "Make Formal", "Make Friendlier", "Make Shorter",
            "Translate to English", "Quote", "Fun Fact", "Shrug", "Sign-off",
        )
        val untouched = current.isNotEmpty() && current.all { (it.name ?: "") in oldNames }
        if (!untouched) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("PROMPTS", null, null)
            defaultSeeds().forEachIndexed { index, seed ->
                db.insert("PROMPTS", null, ContentValues().apply {
                    put("POS", index)
                    put("NAME", seed.name)
                    put("PROMPT", seed.prompt)
                    put("REQUIRES_SELECTION", if (seed.requiresSelection) 1 else 0)
                    put("AUTO_APPLY", 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    fun update(model: PromptModel) {
        val db = writableDatabase
        db.update("PROMPTS", model.toContentValues(), "ID = ?", arrayOf(model.id.toString()))
    }

    fun delete(id: Int) {
        val db = writableDatabase
        db.delete("PROMPTS", "ID = ?", arrayOf(id.toString()))
    }

    fun get(id: Int): PromptModel? {
        val db = readableDatabase
        return db.rawQuery("SELECT * FROM PROMPTS WHERE ID = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.toPromptModel() else null
        }
    }

    fun getAll(): List<PromptModel> {
        val db = readableDatabase
        val models = ArrayList<PromptModel>()
        db.rawQuery("SELECT * FROM PROMPTS ORDER BY POS ASC", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do { models.add(cursor.toPromptModel()) } while (cursor.moveToNext())
            }
        }
        return models
    }

    /**
     * Returns all persisted prompts plus the synthetic UI buttons in display order:
     * instant prompt, select-all, [persisted prompts…], add prompt.
     */
    fun getAllForKeyboard(): List<PromptModel> {
        val persisted = getAll()
        val result = ArrayList<PromptModel>(persisted.size + 3)
        result.add(PromptModel(PromptModel.ID_INSTANT_PROMPT, Int.MIN_VALUE, null, null, false, false))
        result.add(PromptModel(PromptModel.ID_SELECT_ALL, Int.MIN_VALUE + 1, null, null, false, false))
        result.addAll(persisted)
        result.add(PromptModel(PromptModel.ID_ADD_PROMPT, Int.MAX_VALUE, null, null, false, false))
        return result
    }

    fun getAutoApplyIds(): List<Int> {
        val db = readableDatabase
        val ids = ArrayList<Int>()
        db.rawQuery("SELECT ID FROM PROMPTS WHERE AUTO_APPLY = 1 ORDER BY POS ASC", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do { ids.add(cursor.getInt(0)) } while (cursor.moveToNext())
            }
        }
        return ids
    }

    fun count(): Int {
        val db = readableDatabase
        return db.rawQuery("SELECT COUNT(*) FROM PROMPTS", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun PromptModel.toContentValues() = ContentValues().apply {
        put("POS", pos)
        put("NAME", name)
        put("PROMPT", prompt)
        put("REQUIRES_SELECTION", if (requiresSelection) 1 else 0)
        put("AUTO_APPLY", if (autoApply) 1 else 0)
        put("REASONING_EFFORT", reasoningEffort?.name)
        put("REASONING_EFFORT_CUSTOM", reasoningEffortCustom?.takeIf { it.isNotBlank() })
    }

    private fun android.database.Cursor.toPromptModel(): PromptModel {
        // REASONING_EFFORT (v3) is read defensively: an unknown/old value maps back to null (= global).
        val reasoningIdx = getColumnIndex("REASONING_EFFORT")
        val reasoning = if (reasoningIdx >= 0 && !isNull(reasoningIdx)) {
            runCatching { DictateReasoningEffort.valueOf(getString(reasoningIdx)) }.getOrNull()
        } else null
        val customIdx = getColumnIndex("REASONING_EFFORT_CUSTOM")
        val reasoningCustom = if (customIdx >= 0 && !isNull(customIdx)) getString(customIdx) else null
        return PromptModel(
            id = getInt(getColumnIndexOrThrow("ID")),
            pos = getInt(getColumnIndexOrThrow("POS")),
            name = getString(getColumnIndexOrThrow("NAME")),
            prompt = getString(getColumnIndexOrThrow("PROMPT")),
            requiresSelection = getInt(getColumnIndexOrThrow("REQUIRES_SELECTION")) == 1,
            autoApply = getInt(getColumnIndexOrThrow("AUTO_APPLY")) == 1,
            reasoningEffort = reasoning,
            reasoningEffortCustom = reasoningCustom,
        )
    }

    private data class Seed(val name: String, val prompt: String, val requiresSelection: Boolean)

    companion object {
        const val DATABASE_NAME = "prompts.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var instance: PromptsDatabaseHelper? = null

        /**
         * The single process-wide helper (issue #138). All callers share one open database instead of
         * each opening — and formerly closing — their own connection, which is what caused overlapping
         * queries to race on a disposed pool. Always built with the application context so it outlives
         * any Activity/Composable that requested it.
         */
        fun getInstance(context: Context): PromptsDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: PromptsDatabaseHelper(context.applicationContext).also { instance = it }
            }

        /**
         * Marko's own restyle prompts, which is what ships instead of the fork's starter set.
         *
         * The originals were written for a general audience: Fix Grammar, Improve Writing, Make
         * Formal, plus four novelty ones. None of them are how this keyboard is used. These are the
         * voices actually wanted, written as voices rather than labels, with no length ceiling so a
         * long piece comes back long.
         *
         * Plain strings rather than string resources on purpose: these are one person's writing
         * instructions, not UI chrome, and translating them into forty languages would change what
         * they ask for.
         */
        private fun defaultSeeds() = listOf(
            Seed(
                name = "Yshai style",
                prompt = "Rewrite the text in the voice of a quiet teacher writing to a student. All lowercase, including the start of sentences. Short simple sentences in plain everyday words. Warm and humble, never grand. Short paragraphs of one to three sentences with a blank line between them. No bullet points, no headings, no dashes or em dashes. Keep the length close to the original and keep the writer's own meaning; say less rather than more. Keep the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Condense",
                prompt = "Rewrite the text so it says the same thing in fewer words. Remove repetition, filler and any word that carries no meaning. Merge sentences that make the same point. You may reorder sentences freely if a different order communicates more clearly. Lose no fact, no nuance and no intent. Keep the original language and the writer's tone. Return only the condensed text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "In a nutshell",
                prompt = "Read the whole text and return what actually matters in it: the takeaways and the principles behind them, not the details or the examples. Ignore digressions, repetitions and thinking out loud. Write a short piece of prose, no more than a third the length of the original, in the original language. If the text contains decisions or actions, state them plainly. Return only that summary, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Business",
                prompt = "Rewrite the text as clear professional business communication. Direct, courteous, no filler, no flattery, no exclamation marks. Lead with the point, then the detail. Keep the original language and every fact. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Casual",
                prompt = "Rewrite the text the way you would say it to a colleague you like. Relaxed, natural, contractions welcome, no stiffness and no corporate wording. Keep the meaning and the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Friendly",
                prompt = "Rewrite the text so it sounds open and kind without becoming sweet or exaggerated. Keep every fact, soften anything that reads as blunt, and keep the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Warm",
                prompt = "Rewrite the text with real warmth, as if writing to someone you care about. Unhurried, personal, gentle in its wording, but never sentimental and never longer than it needs to be. Keep the meaning and the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Fun",
                prompt = "Rewrite the text with a light touch and a sense of play. Keep it quick, let the humour come from rhythm and word choice rather than jokes bolted on, and never let the joke cost the meaning. Keep the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Spiritual",
                prompt = "Rewrite the text in a contemplative voice: unhurried, grounded, attentive to what is underneath the words. Plain language rather than mystical vocabulary, and no preaching. Keep the writer's meaning and the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Technical",
                prompt = "Rewrite the text as precise technical writing. Exact terms, unambiguous sentences, no marketing language and no hedging. Preserve every number, name and detail exactly. State assumptions where the original is vague rather than inventing facts. Keep the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
            Seed(
                name = "Musical",
                prompt = "Rewrite the text with attention to its rhythm: vary sentence lengths, let it breathe, place the strongest word where the ear expects it, and use silence by keeping some sentences very short. Meaning first, music second, never the other way round. Keep the original language. Return only the rewritten text, without quotation marks or explanations.",
                requiresSelection = true,
            ),
        )
    }
}
