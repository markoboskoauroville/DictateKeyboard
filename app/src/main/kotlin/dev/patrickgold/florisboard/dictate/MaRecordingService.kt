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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity

/**
 * Keeps a dictation alive when the keyboard goes away.
 *
 * An input method has no window of its own. Switch to another app mid-sentence and the keyboard is
 * torn down, and on Android 14 and later the microphone is taken with it: background microphone
 * access is only granted to a foreground service that declared it wants one. That is the whole
 * reason a recording used to end the moment anything came to the front.
 *
 * So while a recording runs, this service runs, holding exactly one thing: the right to keep
 * listening. The audio pipeline itself is untouched and still lives in RecordingController. The
 * service is deliberately thin, because a recording that depends on two moving parts is a recording
 * with two ways to fail.
 *
 * Started while the keyboard is on screen, which is what makes it legal: an app cannot start a
 * microphone foreground service from the background, but an IME that the user is looking at is very
 * much in the foreground. Once running, it stays running through anything the user does next.
 *
 * The notification is not decoration. Android requires one, and it is the honest thing besides: an
 * app holding a live microphone while out of sight should say so. Tapping it opens the app; the
 * stop button on it ends the recording from anywhere, without going back to find the keyboard.
 */
class MaRecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Force stop from the notification: end the dictation, then this service.
                holding = false
                DictateController.forceStop(applicationContext)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                promote()
                holding = true
            }
        }
        // Not sticky: if the system kills this, there is no live AudioRecord left to attach to, and
        // restarting an empty recorder would only produce a notification for a recording that is not
        // happening.
        return START_NOT_STICKY
    }

    private fun promote() {
        ensureChannel()
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // Low importance: this must be visible, it must never make a sound or push itself in front
        // of anything. It is a statement of fact, not an interruption.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name_full),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, FlorisAppActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MaRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name_full))
            .setContentText("Recording. This continues while you are in other apps.")
            .setSmallIcon(R.drawable.ic_app_icon_monochrome)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(null, "Stop", stop).build(),
            )
            .build()
    }

    override fun onDestroy() {
        holding = false
        super.onDestroy()
    }

    companion object {
        /**
         * Whether the microphone is currently held by this service. The controller checks it before
         * deciding that a hidden keyboard means a finished recording: when the mic is held, it does
         * not, and salvaging audio that is still being recorded would end the very thing it is
         * trying to protect.
         */
        @Volatile
        var isHoldingMic: Boolean = false
            private set

        private var holding: Boolean
            get() = isHoldingMic
            set(value) { isHoldingMic = value }

        private const val CHANNEL_ID = "ma_recording"
        private const val NOTIF_ID = 0xB0B0
        private const val ACTION_STOP = "ma.recording.stop"

        /**
         * Starts holding the microphone. Called as a recording begins, while the keyboard is still on
         * screen, which is what makes starting a microphone foreground service permitted at all.
         */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context.applicationContext, MaRecordingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            }
        }

        /** Releases it. Called from every path that ends a recording, and safe to call twice. */
        fun stop(context: Context) {
            isHoldingMic = false
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, MaRecordingService::class.java)
                )
            }
        }
    }
}
