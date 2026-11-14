package com.kaif.mastercaller.telecom

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Call recording, saved to Music/KAVE Call Recordings.
 *
 * Android 10+ only lets the phone maker's own dialer record the call line itself. Other apps (including a
 * default dialer like KAVÉ Call) get the microphone at best. So we try the call line first and fall back
 * to the microphone; [quality] tells the Boss which one worked (on speaker, the mic hears both sides).
 */
object CallRecorder {
    enum class Quality { BOTH_SIDES, MIC_ONLY }

    data class State(val recording: Boolean = false, val startedAt: Long = 0, val quality: Quality? = null, val lastFile: String? = null, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var recorder: MediaRecorder? = null
    private var uri: Uri? = null

    fun start(context: Context, who: String): Boolean {
        if (recorder != null) return true
        val name = "KAVE ${who.replace(Regex("[^A-Za-z0-9 +]"), "").trim().take(30)} " +
            SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ENGLISH).format(Date()) + ".m4a"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/KAVE Call Recordings")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val target = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: run {
            _state.value = State(error = "Couldn't create the recording file"); return false
        }
        // Call line first (works only where the phone allows it), then microphone sources.
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL to Quality.BOTH_SIDES,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to Quality.MIC_ONLY,
            MediaRecorder.AudioSource.MIC to Quality.MIC_ONLY,
        )
        for ((src, q) in sources) {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                val fd = context.contentResolver.openFileDescriptor(target, "w") ?: continue
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(96_000)
                r.setAudioSamplingRate(44_100)
                r.setOutputFile(fd.fileDescriptor)
                r.prepare()
                r.start()
                fd.close()
                recorder = r
                uri = target
                _state.value = State(recording = true, startedAt = System.currentTimeMillis(), quality = q)
                Log.i(TAG, "recording with source $src ($q)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "source $src not allowed: ${e.message}")
                runCatching { r.release() }
            }
        }
        context.contentResolver.delete(target, null, null)
        _state.value = State(error = "This phone doesn't allow call recording by other apps")
        return false
    }

    fun stop(context: Context) {
        val r = recorder ?: return
        runCatching { r.stop() }
        runCatching { r.release() }
        recorder = null
        val u = uri
        uri = null
        if (u != null && Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }
        _state.value = State(quality = _state.value.quality, lastFile = "Music/KAVE Call Recordings")
    }

    private const val TAG = "CallRecorder"
}
