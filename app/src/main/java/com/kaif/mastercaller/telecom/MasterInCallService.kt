package com.kaif.mastercaller.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.notifications.CallNotificationManager
import com.kaif.mastercaller.ui.call.IncomingCallActivity
import com.kaif.mastercaller.ui.call.OngoingCallActivity
import com.kaif.mastercaller.voice.IncomingCallAssistant

/**
 * Bound by Android Telecom for every call once KAVÉ Call is the default phone app.
 * Bridges Telecom callbacks into CallManager / AudioRouteManager and drives the UI + assistant.
 */
class MasterInCallService : InCallService(), CallManager.Listener {

    private lateinit var assistant: IncomingCallAssistant
    private lateinit var notifications: CallNotificationManager

    override fun onCreate() {
        super.onCreate()
        assistant = IncomingCallAssistant(this)
        notifications = CallNotificationManager(this) { id, n -> foreground(id, n) }
        instance = this
        CallManager.listener = this
        AudioRouteManager.attach(this)
        AudioRouteManager.resetForNextCall()
    }

    override fun onDestroy() {
        CallRecorder.stop(this)
        if (instance === this) instance = null
        if (CallManager.listener === this) CallManager.listener = null
        CallManager.clearAll()
        AudioRouteManager.detach(this)
        assistant.release()
        notifications.cancelAll()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        if (Build.VERSION.SDK_INT < 34) AudioRouteManager.onLegacyAudioState(audioState)
    }

    @RequiresApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        AudioRouteManager.onEndpointChanged(callEndpoint)
    }

    @RequiresApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        AudioRouteManager.onAvailableEndpoints(availableEndpoints)
    }

    @RequiresApi(34)
    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        AudioRouteManager.onMuteChanged(isMuted)
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        assistant.silence()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        val ringing = CallStateManager.ringing()
        if (ringing != null) {
            launch(IncomingCallActivity.intent(this, ringing.id))
        } else {
            CallStateManager.primary()?.let { launch(OngoingCallActivity.intent(this, it.id)) }
        }
    }

    // --- CallManager.Listener ------------------------------------------------------------------

    override fun onIncomingRinging(callId: String) {
        val otherCall = CallStateManager.hasCallInProgressOtherThan(callId)
        CallStateManager.find(callId)?.let { notifications.showIncoming(it) }
        // InCallService is one of the services allowed to start an activity from the background;
        // the full-screen notification is the fallback when the OEM blocks it.
        launch(IncomingCallActivity.intent(this, callId))
        assistant.onIncomingRinging(callId, otherCall)
    }

    override fun onRingingStopped(callId: String) {
        assistant.onRingingStopped(callId)
    }

    override fun onUserActed(callId: String) {
        assistant.onUserActed(callId)
    }

    override fun onIncomingCallAnswered(callId: String) {
        val settings = MasterCallerApp.instance.settingsRepository.settings.value
        AudioRouteManager.onIncomingCallAnswered(settings.autoSpeaker)
        launch(OngoingCallActivity.intent(this, callId))
    }

    override fun onOutgoingCallStarted(callId: String) {
        AudioRouteManager.resetForNextCall()
        launch(OngoingCallActivity.intent(this, callId))
    }

    override fun onCallsChanged(calls: List<CallInfo>) {
        notifications.update(calls)
        if (calls.none { it.isLive }) {
            AudioRouteManager.resetForNextCall()
            CallRecorder.stop(this)
        }
    }

    private var foregroundOn = false
    private var lastOngoing: android.app.Notification? = null

    /**
     * While a call is live the service runs in the foreground (type "phone call"), so swiping KAVÉ Call
     * out of Recents can't kill it and the notification's Hang up always works.
     */
    private fun foreground(id: Int, n: android.app.Notification?) {
        try {
            if (n != null) {
                lastOngoing = n
                val type = if (Build.VERSION.SDK_INT >= 30) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                        (if (CallRecorder.state.value.recording) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                } else 0
                androidx.core.app.ServiceCompat.startForeground(this, id, n, type)
                foregroundOn = true
            } else if (foregroundOn) {
                androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
                foregroundOn = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "foreground change failed", e)
        }
    }

    /** Re-applies the foreground types (adds "microphone" while recording). */
    fun refreshForeground() { lastOngoing?.let { foreground(4202, it) } }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start call UI; relying on notification", e)
        }
    }

    companion object {
        private const val TAG = "MasterInCallService"
        @Volatile var instance: MasterInCallService? = null
            private set
    }
}
