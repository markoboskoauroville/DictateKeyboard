/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * A plain folder in the shared Documents directory that outlives the app.
 *
 * Everything the app normally remembers, preferences and the persisted SAF grant on the picked keys
 * file, is destroyed by a full uninstall. That is why the API keys had to be entered again after a
 * clean install. A file written to `Documents/MantraVoiceType/` is not touched by Android when the
 * package is removed, so a fresh install can pick the keys straight back up with no clicks at all.
 *
 * The catch is reading it back. Once the package is uninstalled the MediaStore ownership record for
 * that file is gone, so on Android 11 and newer the app is no longer allowed to read its own former
 * file without all-files access. That permission is therefore offered, once, from the settings
 * screen, and everything here degrades quietly when it has not been granted: writing still works
 * through the app's own scoped access on most devices, and the SAF picker remains the fallback.
 *
 * Nothing here is encrypted, deliberately. The whole point is that the file stays readable after the
 * app is gone. It sits in Documents where the user put it, exactly like the keys file that is picked
 * manually today.
 */
object MaVault {
    /** Folder inside the shared Documents directory. Visible, findable, and easy to back up. */
    const val DIR_NAME = "MantraVoiceType"

    /** Raw copy of whatever keys file was last imported, parsed by MaKeys exactly as before. */
    const val KEYS_FILE = "keys.txt"

    /** Human-readable location, shown in settings so the file can be found by hand. */
    const val DISPLAY_PATH = "Documents/$DIR_NAME/$KEYS_FILE"

    private fun documentsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    /** The vault folder. Not created here; [write] creates it on demand. */
    fun dir(): File = File(documentsDir(), DIR_NAME)

    /** The keys file itself, whether or not it exists yet. */
    fun keysFile(): File = File(dir(), KEYS_FILE)

    /**
     * True when the app may read files it does not own. Below Android 11 the legacy storage
     * permissions cover this, so the answer is yes as far as this check is concerned.
     */
    fun hasFullAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /** True when a vault file is actually there and has something in it. */
    fun exists(): Boolean = runCatching { keysFile().length() > 0L }.getOrDefault(false)

    /**
     * Mirror the imported keys file into the vault. Called every time a file is picked, so the vault
     * always holds the newest set. Returns true when the copy landed.
     */
    fun write(text: String): Boolean = runCatching {
        val folder = dir()
        if (!folder.exists()) {
            folder.mkdirs()
        }
        keysFile().writeText(text)
        true
    }.getOrDefault(false)

    /**
     * Writes every key currently held, grouped by provider, as the backup file.
     *
     * Distinct from the copy made at import time, which is only ever the last file that was picked.
     * After a few imports, some deletions and some reordering, what is actually in use no longer
     * matches any single file on disk, and that curated list is the thing worth keeping.
     *
     * The format is plain text that this app's own parser reads back without help: comment lines
     * naming each provider, which the parser skips, and one key per line, which it recognises by
     * shape. That also makes it readable by a human, which matters for a file whose whole job is to
     * still make sense on a phone that no longer has the app installed.
     */
    fun writeBackup(sections: List<Pair<String, List<String>>>): Boolean {
        val total = sections.sumOf { it.second.size }
        if (total == 0) return false
        val text = buildString {
            appendLine("# TTT&LLL, API key backup")
            appendLine("# Written " + java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()))
            appendLine("# Keep this file. A fresh install reads it from here with no clicks.")
            for ((provider, keys) in sections) {
                if (keys.isEmpty()) continue
                appendLine()
                appendLine("# $provider")
                keys.forEach { appendLine(it) }
            }
        }
        return write(text)
    }

    /**
     * Read the vault back, or null when it is missing or unreadable. Unreadable is the normal state
     * after an uninstall until all-files access is granted, and is not an error worth shouting about.
     */
    fun read(): String? = runCatching {
        val file = keysFile()
        if (file.exists() && file.canRead()) file.readText() else null
    }.getOrNull()

    /**
     * Intent that opens the system screen where all-files access is granted for this app. Falls back
     * to the general list of apps on devices where the per-app screen refuses to open.
     */
    fun accessIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /** Short sentence describing the current state, shown under the vault row in settings. */
    fun status(): String = when {
        exists() && hasFullAccess() -> "Keys are saved in $DISPLAY_PATH and will survive a reinstall."
        exists() -> "Saved in $DISPLAY_PATH, but this build cannot read it back yet. Grant all-files access."
        hasFullAccess() -> "Nothing saved yet. Import a keys file and a copy is kept in $DISPLAY_PATH."
        else -> "Grant all-files access so keys can be kept in $DISPLAY_PATH across reinstalls."
    }
}
