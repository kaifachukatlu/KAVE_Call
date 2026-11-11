package com.kaif.mastercaller.voice

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.telecom.AssistantPhase
import com.kaif.mastercaller.telecom.AssistantState
import com.kaif.mastercaller.telecom.CallManager
import com.kaif.mastercaller.telecom.CallPhase
import com.kaif.mastercaller.telecom.CallStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Set by IncomingCallActivity so voice listening only happens while its indicator is on screen. */
object CallUiVisibility {
    @Volatile
    var incomingVisible: Boolean = false
}

/**
 * Orchestrates one ringing call: ring -> pause ringtone -> speak announcement -> short visible
 * listening window -> act on the command or resume ringing. Each call is announced at most once
 * automatically; the user can repeat it from the call screen.
 */
class IncomingCallAssistant(context: Context) {

    private val appContext = context.applicationContext
    private val app = MasterCallerApp.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ringer = Ringer(appContext)
    private var currentId: String? = null
    private var job: Job? = null
    private var silenced = false
    private val startedIds = HashSet<String>()

    init {
        active = this
    }

    fun onIncomingRinging(callId: String, otherCallInProgress: Boolean) {
        if (!startedIds.add(callId)) return // never auto-announce the same call twice
        if (currentId != null && currentId != callId) {
            // A second call rang while we were handling the first: just signal it.
            ringer.playCallWaitingTone()
            return
        }
        currentId = callId
        silenced = false
        job?.cancel()
        job = scope.launch { runSequence(callId, otherCallInProgress) }
    }

    fun onRingingStopped(callId: String) {
        if (currentId != callId) return
        stopEverything()
        currentId = null
        CallStateManager.setAssistant(AssistantState())
    }

    /** The user tapped Answer/Reject (or a voice command fired): go quiet immediately. */
    fun onUserActed(callId: String) {
        if (currentId == callId) {
            stopEverything()
        }
    }

    /** Volume key pressed while ringing (InCallService.onSilenceRinger). */
    fun silence() {
        silenced = true
        stopEverything()
        currentId?.let { setPhase(it, AssistantPhase.Notice("Silenced")) }
    }

    fun repeatAnnouncement(callId: String) {
        if (!isStillRinging(callId)) return
        job?.cancel()
        job = scope.launch {
            val settings = app.settingsRepository.current()
            announceAndListen(callId, settings, listen = settings.voiceCommandsEnabled)
        }
    }

    fun listenNow(callId: String) {
        if (!isStillRinging(callId)) return
        job?.cancel()
        app.announcer.stop()
        job = scope.launch {
            val settings = app.settingsRepository.current()
            ringer.pauseRingtone()
            try {
                listenForCommand(callId, settings)
            } finally {
                resumeIfStillRinging(callId)
            }
        }
    }

    fun release() {
        stopEverything()
        scope.cancel()
        if (active === this) active = null
    }

    // --- sequence ------------------------------------------------------------------------------

    private suspend fun runSequence(callId: String, otherCallInProgress: Boolean) {
        val settings = app.settingsRepository.current()
        val info = CallStateManager.find(callId) ?: return

        if (otherCallInProgress) {
            ringer.playCallWaitingTone()
            setPhase(callId, AssistantPhase.Notice("Call waiting"))
            return
        }

        val audio = appContext.getSystemService(AudioManager::class.java)
        when {
            !dndAllows(info.number) -> {
                setPhase(callId, AssistantPhase.Notice("Do Not Disturb is on, so ringing silently"))
                return
            }
            audio?.ringerMode == AudioManager.RINGER_MODE_SILENT -> {
                setPhase(callId, AssistantPhase.Notice("Phone is on silent, so no announcement"))
                return
            }
            audio?.ringerMode == AudioManager.RINGER_MODE_VIBRATE -> {
                ringer.startVibration()
                setPhase(callId, AssistantPhase.Notice("Vibrate mode, so no announcement"))
                return
            }
        }

        ringer.startRingtone(info.ringtoneUri)
        if (settings.vibrateWhileRinging) ringer.startVibration()
        setPhase(callId, AssistantPhase.Ringing)

        val shouldAnnounce = settings.announcementsEnabled &&
            (info.contactName != null || settings.announceUnknownCallers)
        if (!shouldAnnounce) return

        delay(INTRO_RING_MS) // let the phone ring briefly so the call is noticed first
        announceAndListen(callId, settings, listen = settings.voiceCommandsEnabled)
    }

    private suspend fun announceAndListen(callId: String, settings: AppSettings, listen: Boolean) {
        if (!isStillRinging(callId)) return
        val info = CallStateManager.find(callId) ?: return
        val text = AnnouncementText.forCaller(settings, info.contactName)
        val spoken = AnnouncementText.forCaller(settings, info.contactName, spoken = true)
        ringer.pauseRingtone()
        try {
            setPhase(callId, AssistantPhase.Announcing(text))
            app.announcer.speakAwait(spoken, AnnouncementHelper.Purpose.CALL)
            if (!isStillRinging(callId)) return
            if (listen) listenForCommand(callId, settings) else setPhase(callId, AssistantPhase.Ringing)
        } finally {
            resumeIfStillRinging(callId)
        }
    }

    private suspend fun listenForCommand(callId: String, settings: AppSettings) {
        val blocked = voiceBlockReason()
        if (blocked != null) {
            setPhase(callId, AssistantPhase.Notice(blocked))
            return
        }
        if (!isStillRinging(callId)) return
        setPhase(callId, AssistantPhase.Listening(settings.listenSeconds))
        val result = app.speech.listenAwait(
            languageTag = settings.languageTag,
            windowMs = settings.listenSeconds * 1_000L,
            preferOffline = settings.preferOfflineRecognition,
        )
        when (result.command) {
            VoiceCommand.ANSWER -> {
                setPhase(callId, AssistantPhase.Heard(result.heard, VoiceCommand.ANSWER))
                CallManager.answer(callId)
            }
            VoiceCommand.REJECT -> {
                setPhase(callId, AssistantPhase.Heard(result.heard, VoiceCommand.REJECT))
                CallManager.reject(callId)
            }
            null -> setPhase(
                callId,
                AssistantPhase.Notice(
                    (result.message ?: "No command heard") + (result.heard?.let { " (heard \"$it\")" } ?: "") +
                        ". Use the buttons.",
                ),
            )
        }
    }

    private suspend fun voiceBlockReason(): String? {
        if (!app.speech.hasMicPermission()) return "Microphone permission is off. Use the buttons."
        if (!app.speech.isRecognitionAvailable()) return "Speech recognition isn't available. Use the buttons."
        var waited = 0L
        while (!CallUiVisibility.incomingVisible && waited < 2_000) {
            delay(100)
            waited += 100
        }
        if (!CallUiVisibility.incomingVisible) return "Open the call screen to use voice commands"
        return null
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun isStillRinging(callId: String): Boolean =
        CallStateManager.find(callId)?.phase == CallPhase.RINGING && !CallManager.hasUserAction(callId)

    private fun resumeIfStillRinging(callId: String) {
        if (!silenced && isStillRinging(callId)) ringer.resumeRingtone()
    }

    private fun stopEverything() {
        job?.cancel()
        job = null
        ringer.stopAll()
        app.announcer.stop()
        app.speech.cancel()
    }

    private fun setPhase(callId: String, phase: AssistantPhase) {
        CallStateManager.setAssistant(AssistantState(callId, phase))
    }

    /** Respect Do Not Disturb: only ring/announce if the system would let this call through. */
    @SuppressLint("MissingPermission")
    private fun dndAllows(number: String?): Boolean {
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return true
        val filter = nm.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !number.isNullOrBlank()) {
            return try {
                nm.matchesCallFilter(Uri.fromParts("tel", number, null))
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    companion object {
        private const val INTRO_RING_MS = 2_500L

        /** The instance owned by the bound InCallService (null when no call service is bound). */
        var active: IncomingCallAssistant? = null
            private set
    }
}
