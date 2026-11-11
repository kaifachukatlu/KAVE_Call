package com.kaif.mastercaller.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ListenResult(val command: VoiceCommand?, val heard: String?, val message: String?)

/**
 * Short, explicitly bounded listening window using Android's SpeechRecognizer.
 * The microphone is only used between listen() and the end of the window — never continuously.
 * Main-thread only.
 */
class SpeechCommandManager(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private var onFinish: ((ListenResult) -> Unit)? = null
    private var timeout: Runnable? = null

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /** 0..1 microphone level for the on-screen indicator. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isRecognitionAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    } catch (e: Exception) {
        false
    }

    suspend fun listenAwait(languageTag: String, windowMs: Long, preferOffline: Boolean): ListenResult =
        suspendCancellableCoroutine { cont ->
            listen(languageTag, windowMs, preferOffline) { r -> if (cont.isActive) cont.resume(r) }
            cont.invokeOnCancellation { handler.post { cancel() } }
        }

    fun listen(languageTag: String, windowMs: Long, preferOffline: Boolean, onResult: (ListenResult) -> Unit) {
        cancel()
        if (!hasMicPermission()) {
            onResult(ListenResult(null, null, "Microphone permission is off"))
            return
        }
        if (!isRecognitionAvailable()) {
            onResult(ListenResult(null, null, "Speech recognition isn't available on this phone"))
            return
        }
        val mySession = ++session
        val deadline = SystemClock.elapsedRealtime() + windowMs
        onFinish = onResult
        _listening.value = true
        val t = Runnable { complete(mySession, ListenResult(null, null, "No command heard")) }
        timeout = t
        handler.postDelayed(t, windowMs)
        start(mySession, languageTag, preferOffline, deadline)
    }

    fun cancel() {
        if (onFinish != null) complete(session, ListenResult(null, null, "Listening stopped"))
    }

    // --- internals -----------------------------------------------------------------------------

    private fun remaining(deadline: Long) = deadline - SystemClock.elapsedRealtime()

    @SuppressLint("InlinedApi")
    private fun start(mySession: Int, languageTag: String, preferOffline: Boolean, deadline: Long) {
        destroyRecognizer()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "createSpeechRecognizer failed", e)
            complete(mySession, ListenResult(null, null, "Speech recognition could not start"))
            return
        }
        recognizer = r

        fun retryOrFinish(message: String, heard: String? = null) {
            if (remaining(deadline) > RESTART_MIN_MS) {
                start(mySession, languageTag, preferOffline, deadline)
            } else {
                complete(mySession, ListenResult(null, heard, message))
            }
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                if (session == mySession) _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                if (session != mySession) return
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val command = VoiceCommandParser.parseBest(list)
                if (command != null) {
                    complete(mySession, ListenResult(command, list.firstOrNull(), null))
                } else {
                    retryOrFinish("Didn't recognise a command", list.firstOrNull())
                }
            }

            override fun onError(error: Int) {
                if (session != mySession) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> retryOrFinish("No command heard")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> handler.postDelayed({
                        if (session == mySession) retryOrFinish("Speech recognizer is busy")
                    }, 250)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        complete(mySession, ListenResult(null, null, "Microphone permission is off"))
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER ->
                        complete(mySession, ListenResult(null, null, "Voice recognition needs internet or an offline language pack"))
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        complete(mySession, ListenResult(null, null, "Recognition language not available on this phone"))
                    SpeechRecognizer.ERROR_AUDIO ->
                        complete(mySession, ListenResult(null, null, "Microphone is unavailable right now"))
                    else -> complete(mySession, ListenResult(null, null, "Voice recognition error ($error)"))
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            complete(mySession, ListenResult(null, null, "Speech recognition could not start"))
        }
    }

    private fun complete(mySession: Int, result: ListenResult) {
        if (mySession != session) return
        session++ // invalidates any late callbacks from this session
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        destroyRecognizer()
        _listening.value = false
        _level.value = 0f
        val f = onFinish
        onFinish = null
        f?.invoke(result)
    }

    private fun destroyRecognizer() {
        val r = recognizer ?: return
        recognizer = null
        try {
            r.cancel()
            r.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy failed", e)
        }
    }

    private companion object {
        const val TAG = "SpeechCommandManager"
        const val RESTART_MIN_MS = 1_800L
    }
}
