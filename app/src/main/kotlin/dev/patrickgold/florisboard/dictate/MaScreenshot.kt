/*
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.dictate.provider.MaVision
import java.io.ByteArrayOutputStream

/**
 * The most recent screenshot on the phone, ready to send.
 *
 * **Why the newest file rather than taking the picture ourselves.** A keyboard cannot capture the
 * screen. Android offers exactly two ways and both are worse here: `MediaProjection` shows a consent
 * dialog for every session and needs a foreground service and a trampoline activity to receive the
 * result, and an `AccessibilityService` is a permission that can read everything on screen forever.
 * Marko's own suggestion avoids both: he presses power and volume down, which is a gesture his thumbs
 * already know, and the app reads what that produced. One ordinary media permission, granted once,
 * and no dialog ever again. It is also the version that keeps him in control of what is sent.
 *
 * The cost is that the app is trusting the newest file to be the one meant, which is why
 * [ageMinutes] exists: the caller can say so when the newest screenshot is hours old rather than
 * reading something from yesterday out loud.
 */
object MaScreenshot {

    /** What the reader is handed: the image, already encoded, and where it came from. */
    data class Shot(
        val base64: String,
        val mimeType: String,
        val displayName: String,
        val takenAtMillis: Long,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        /** How long ago it was taken. Used to warn rather than to refuse. */
        val ageMinutes: Long
            get() = ((System.currentTimeMillis() - takenAtMillis) / 60_000L).coerceAtLeast(0L)
    }

    /**
     * The permission this needs, which differs by Android version.
     *
     * `READ_MEDIA_IMAGES` on 33 and above; `READ_EXTERNAL_STORAGE` below it. Getting this wrong is a
     * permission the system silently never grants, which looks exactly like a missing screenshot.
     */
    val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Finds the newest screenshot, or null when there is none this app can see.
     *
     * Screenshots live in different folders on different phones, so this asks by **bucket name**
     * first, which is what every launcher, gallery and manufacturer skin agrees on, and falls back to
     * matching the path. The two together cover `Pictures/Screenshots`, `DCIM/Screenshots` and the
     * vendor variants without hardcoding any of them.
     */
    fun newestUri(context: Context): Pair<Uri, Long>? {
        if (!hasPermission(context)) return null
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        // Bucket name is the reliable one; the path match is the safety net for skins that file
        // screenshots somewhere unusual. LIKE is case sensitive on some providers, hence both cases.
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? OR " +
            "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?"
        val args = arrayOf("Screenshots", "%/Screenshots/%", "%/screenshots/%")
        val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return runCatching {
            context.contentResolver.query(collection, projection, selection, args, order)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                // DATE_ADDED is in SECONDS, not milliseconds. Reading it as millis puts every
                // screenshot in 1970 and makes the staleness warning fire on everything.
                val addedSeconds = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                ContentUris.withAppendedId(collection, id) to (addedSeconds * 1000L)
            }
        }.getOrNull()
    }

    /**
     * Loads the newest screenshot and encodes it for the vision model.
     *
     * **Downscaled first, and this is not an optimisation.** A phone screenshot is around 1080 by
     * 2400, and these models bill by tiles of the image; sending the original costs several times
     * more tokens for text that is already far larger than the model needs. The long edge is brought
     * to [MaVision.MAX_EDGE], which keeps body text comfortably legible while cutting the upload to a
     * fraction. `inSampleSize` does the heavy halving during decode, so the full-size bitmap is never
     * held in memory at all, which matters inside a keyboard process.
     *
     * JPEG rather than PNG: a screenshot re-encoded as PNG stays enormous, and at quality 88 the
     * artefacts are nowhere near the size of a letterform.
     */
    fun newest(context: Context): Shot? {
        val (uri, takenAt) = newestUri(context) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(longEdge, MaVision.MAX_EDGE)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val scaled = scaleToFit(decoded, MaVision.MAX_EDGE)
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            Shot(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mimeType = "image/jpeg",
                displayName = uri.lastPathSegment.orEmpty(),
                takenAtMillis = takenAt,
                widthPx = bounds.outWidth,
                heightPx = bounds.outHeight,
            )
        }.getOrNull()
    }

    /**
     * The power-of-two shrink to apply during decode.
     *
     * `inSampleSize` only honours powers of two, so this deliberately **under**shoots: it stops at
     * the last power of two that still leaves the image at or above the target, and [scaleToFit]
     * finishes the job exactly. Overshooting here would throw away detail that cannot come back.
     */
    internal fun sampleSizeFor(longEdge: Int, target: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Brings the long edge down to [target] exactly. Returns the original when it already fits. */
    internal fun scaleToFit(source: Bitmap, target: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= target) return source
        val ratio = target.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
