package com.kaif.mastercaller.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.os.BundleCompat
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.contacts.ContactMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class SimOption(val handle: PhoneAccountHandle, val label: String)

/**
 * Owns the real android.telecom.Call objects handed to us by Telecom.
 * Everything runs on the main thread (InCallService callbacks, UI, receivers all use it),
 * which keeps the "answer once / reject once" guards race-free.
 */
object CallManager {
    private const val TAG = "CallManager"

    interface Listener {
        fun onIncomingRinging(callId: String)
        fun onRingingStopped(callId: String)
        fun onUserActed(callId: String)
        fun onIncomingCallAnswered(callId: String)
        fun onOutgoingCallStarted(callId: String)
        fun onCallsChanged(calls: List<CallInfo>)
    }

    private class Tracked(val id: String, val call: Call) {
        var callback: Call.Callback? = null
        var contact: ContactMatch? = null
        var wallpaper: android.net.Uri? = null
        var posterStyle = com.kaif.mastercaller.contacts.PosterStyle()
        var resolved = false
        var reportedPhase: CallPhase? = null
        var sawRinging = false
        var answerRequested = false
        var rejectRequested = false
        var hangupRequested = false
        var answeredNotified = false
        var outgoingNotified = false
        val isIncoming: Boolean
            get() = call.details.callDirection == Call.Details.DIRECTION_INCOMING
    }

    private val tracked = LinkedHashMap<String, Tracked>()
    private var nextId = 1
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var listener: Listener? = null

    fun onCallAdded(call: Call) {
        if (tracked.values.any { it.call === call }) return // duplicate callback, ignore
        val t = Tracked("call-${nextId++}", call)
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = sync(t)
            override fun onDetailsChanged(call: Call, details: Call.Details) = sync(t)
        }
        t.callback = cb
        call.registerCallback(cb, mainHandler)
        tracked[t.id] = t
        sync(t)

        val number = numberOf(call)
        val contacts = MasterCallerApp.instance.contacts
        val wallpapers = MasterCallerApp.instance.wallpapers
        scope.launch {
            val match = withTimeoutOrNull(2_500) { withContext(Dispatchers.IO) { contacts.lookup(number) } }
            // App-only photo for this contact -> contact's own photo -> app default -> built-in background.
            withContext(Dispatchers.IO) {
                val own = wallpapers.forContact(match?.lookupKey)
                t.wallpaper = own ?: match?.fullPhotoUri ?: match?.photoUri ?: wallpapers.default()
                t.posterStyle = wallpapers.styleFor(if (own != null) match?.lookupKey else null)
            }
            t.contact = match
            t.resolved = true
            sync(t)
        }
    }

    fun onCallRemoved(call: Call) {
        val t = tracked.values.firstOrNull { it.call === call } ?: return
        t.callback?.let { call.unregisterCallback(it) }
        tracked.remove(t.id)
        if (t.reportedPhase == CallPhase.RINGING) listener?.onRingingStopped(t.id)
        publish()
    }

    /** Called when the InCallService is unbound: Telecom no longer gives us these calls. */
    fun clearAll() {
        tracked.values.forEach { t -> t.callback?.let { t.call.unregisterCallback(it) } }
        tracked.clear()
        publish()
    }

    fun hasUserAction(id: String): Boolean =
        tracked[id]?.let { it.answerRequested || it.rejectRequested || it.hangupRequested } ?: true

    /** Answer a ringing call exactly once. Returns false if it was not possible / already done. */
    fun answer(id: String): Boolean {
        val t = tracked[id] ?: return false
        if (t.answerRequested || t.rejectRequested || t.hangupRequested) return false
        if (phaseOf(t.call.stateCompat()) != CallPhase.RINGING) return false
        t.answerRequested = true
        listener?.onUserActed(id)
        return try {
            t.call.answer(VideoProfile.STATE_AUDIO_ONLY)
            true
        } catch (e: Exception) {
            Log.e(TAG, "answer failed", e)
            false
        }
    }

    /** Reject a ringing call exactly once. */
    fun reject(id: String): Boolean {
        val t = tracked[id] ?: return false
        if (t.answerRequested || t.rejectRequested || t.hangupRequested) return false
        if (phaseOf(t.call.stateCompat()) != CallPhase.RINGING) return false
        t.rejectRequested = true
        listener?.onUserActed(id)
        return try {
            t.call.reject(false, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "reject failed", e)
            false
        }
    }

    /** End any call; a still-ringing call is rejected instead. */
    fun hangup(id: String): Boolean {
        val t = tracked[id] ?: return false
        if (phaseOf(t.call.stateCompat()) == CallPhase.RINGING) return reject(id)
        if (t.hangupRequested) return false
        t.hangupRequested = true
        return try {
            t.call.disconnect()
            true
        } catch (e: Exception) {
            Log.e(TAG, "disconnect failed", e)
            false
        }
    }

    fun hold(id: String) {
        tracked[id]?.call?.hold()
    }

    fun unhold(id: String) {
        tracked[id]?.call?.unhold()
    }

    fun playDtmf(id: String, digit: Char) {
        val call = tracked[id]?.call ?: return
        call.playDtmfTone(digit)
        mainHandler.postDelayed({ call.stopDtmfTone() }, 160)
    }

    // AVAILABLE_PHONE_ACCOUNTS is deprecated but Telecom still populates it; callCapablePhoneAccounts is the fallback.
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun availableSims(context: Context, id: String): List<SimOption> {
        val t = tracked[id] ?: return emptyList()
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        val fromCall = t.call.details.extras?.let {
            BundleCompat.getParcelableArrayList(it, Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)
        }.orEmpty()
        val handles: List<PhoneAccountHandle> = fromCall.ifEmpty {
            try {
                telecom.callCapablePhoneAccounts
            } catch (e: SecurityException) {
                emptyList()
            }
        }
        return handles.mapIndexed { index, handle ->
            val label = try {
                telecom.getPhoneAccount(handle)?.label?.toString()
            } catch (e: Exception) {
                null
            }
            SimOption(handle, label?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}")
        }
    }

    fun selectSim(id: String, handle: PhoneAccountHandle) {
        tracked[id]?.call?.phoneAccountSelected(handle, false)
    }

    // --- internals ---------------------------------------------------------------------------

    private fun numberOf(call: Call): String? = call.details?.handle?.schemeSpecificPart

    private fun sync(t: Tracked) {
        if (tracked[t.id] !== t) return
        publish()
        if (!t.resolved) return // wait for the contact lookup so the announcement uses the right name

        val phase = phaseOf(t.call.stateCompat())
        val previous = t.reportedPhase
        if (previous == phase) return
        t.reportedPhase = phase
        val l = listener

        if (previous == CallPhase.RINGING) l?.onRingingStopped(t.id)
        if (phase == CallPhase.RINGING) {
            t.sawRinging = true
            l?.onIncomingRinging(t.id)
        }
        // Only calls we actually saw ringing count as "answered" (avoids re-routing audio
        // when Telecom re-binds us mid-call after process recreation).
        if (phase == CallPhase.ACTIVE && t.isIncoming && t.sawRinging && !t.answeredNotified) {
            t.answeredNotified = true
            l?.onIncomingCallAnswered(t.id)
        }
        if (!t.isIncoming && !t.outgoingNotified && phase in CallStateManager.OUTGOING_PHASES) {
            t.outgoingNotified = true
            l?.onOutgoingCallStarted(t.id)
        }
    }

    private fun publish() {
        val list = tracked.values.map { it.toInfo() }
        CallStateManager.publish(list)
        listener?.onCallsChanged(list)
    }

    private fun Tracked.toInfo(): CallInfo {
        val details = call.details
        val presentationAllowed = details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED
        // Telecom's own contact match (API 30+) is real saved-contact data, used only as a fallback.
        val systemName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.contactDisplayName?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return CallInfo(
            id = id,
            number = if (presentationAllowed) numberOf(call) else null,
            contactName = contact?.name ?: systemName,
            photoUri = contact?.photoUri,
            wallpaperUri = wallpaper,
            posterStyle = posterStyle,
            ringtoneUri = contact?.ringtoneUri,
            phase = phaseOf(call.stateCompat()),
            isIncoming = isIncoming,
            connectTimeMillis = details.connectTimeMillis,
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMute = details.can(Call.Details.CAPABILITY_MUTE),
            isPrivateNumber = !presentationAllowed,
            disconnectLabel = details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            contactResolved = resolved,
        )
    }
}
