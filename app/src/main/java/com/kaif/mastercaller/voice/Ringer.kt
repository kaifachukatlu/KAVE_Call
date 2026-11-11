package com.kaif.mastercaller.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

/**
 * Because the app declares IN_CALL_SERVICE_RINGING, Telecom expects us to ring.
 * This lets the ringtone pause while the announcement speaks and the mic listens.
 */
class Ringer(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var ringtoneUri: Uri? = null
    private var ringtoneWanted = false
    private var vibrating = false

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            appContext.getSystemService(Vibrator::class.java)
        }

    private val ringAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun startRingtone(customUri: Uri?) {
        ringtoneUri = customUri
        ringtoneWanted = true
        play()
    }

    fun pauseRingtone() = stopRingtonePlayer()

    fun resumeRingtone() {
        if (ringtoneWanted && ringtone == null) play()
    }

    fun startVibration() {
        val v = vibrator ?: return
        if (vibrating || !v.hasVibrator()) return
        vibrating = true
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 900, 900), 0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
            } else {
                legacyVibrate(v, effect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "vibrate failed", e)
        }
    }

    /** Short beep in the call audio for a second incoming call (no loud ringtone mid-call). */
    fun playCallWaitingTone() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            tone.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 1_200)
            handler.postDelayed({ tone.release() }, 1_600)
        } catch (e: Exception) {
            Log.w(TAG, "call waiting tone failed", e)
        }
    }

    fun stopAll() {
        ringtoneWanted = false
        stopRingtonePlayer()
        if (vibrating) {
            vibrating = false
            vibrator?.cancel()
        }
    }

    private fun play() {
        stopRingtonePlayer()
        val r = create(ringtoneUri) ?: create(null) ?: return
        try {
            r.audioAttributes = ringAttributes
            r.isLooping = true
            r.play()
            ringtone = r
        } catch (e: Exception) {
            Log.w(TAG, "ringtone play failed", e)
        }
    }

    private fun create(uri: Uri?): Ringtone? = try {
        RingtoneManager.getRingtone(appContext, uri ?: Settings.System.DEFAULT_RINGTONE_URI)
    } catch (e: Exception) {
        null
    }

    private fun stopRingtonePlayer() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "ringtone stop failed", e)
        }
        ringtone = null
    }

    @Suppress("DEPRECATION")
    private fun legacyVibrate(v: Vibrator, effect: VibrationEffect) {
        v.vibrate(effect, ringAttributes)
    }

    private companion object {
        const val TAG = "Ringer"
    }
}
