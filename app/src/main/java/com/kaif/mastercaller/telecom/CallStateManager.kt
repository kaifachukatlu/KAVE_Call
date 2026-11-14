package com.kaif.mastercaller.telecom

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telephony.PhoneNumberUtils
import com.kaif.mastercaller.voice.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class CallPhase {
    NEW, DIALING, CONNECTING, RINGING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED,
    SELECT_ACCOUNT, SCREENING, UNKNOWN
}

/** Immutable UI snapshot of one Telecom call. */
data class CallInfo(
    val id: String,
    val number: String?,
    val contactName: String?,
    val photoUri: Uri?,
    /** Full-size contact photo for the call-screen wallpaper (falls back to the thumbnail). */
    val wallpaperUri: Uri?,
    /** iPhone-style poster settings (font, colour, depth) for [wallpaperUri]. */
    val posterStyle: com.kaif.mastercaller.contacts.PosterStyle = com.kaif.mastercaller.contacts.PosterStyle(),
    val ringtoneUri: Uri?,
    val phase: CallPhase,
    val isIncoming: Boolean,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val canMute: Boolean,
    val isPrivateNumber: Boolean,
    val disconnectLabel: String?,
    val contactResolved: Boolean,
    /** Can be merged with another call into a conference. */
    val canMerge: Boolean = false,
    /** This is a conference (two or more people). */
    val isConference: Boolean = false,
    /** Set when this call is one of the people inside a conference. */
    val parentId: String? = null,
    /** For a conference: the people in it. */
    val childIds: List<String> = emptyList(),
) {
    val formattedNumber: String?
        get() = number?.takeIf { it.isNotBlank() }?.let {
            PhoneNumberUtils.formatNumber(it, Locale.getDefault().country) ?: it
        }

    val displayName: String
        get() = if (isConference) "Conference call" else
            contactName ?: formattedNumber ?: if (isPrivateNumber) "Private number" else "Unknown caller"

    val isLive: Boolean get() = phase != CallPhase.DISCONNECTED
}

/** What the voice assistant is doing for the ringing call (drives the incoming-call UI). */
sealed interface AssistantPhase {
    data object Idle : AssistantPhase
    data object Ringing : AssistantPhase
    data class Announcing(val text: String) : AssistantPhase
    data class Listening(val seconds: Int) : AssistantPhase
    data class Heard(val text: String?, val command: VoiceCommand) : AssistantPhase
    data class Notice(val message: String) : AssistantPhase
}

data class AssistantState(val callId: String? = null, val phase: AssistantPhase = AssistantPhase.Idle)

/** Single source of truth for call + assistant state, observed by activities and notifications. */
object CallStateManager {
    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _assistant = MutableStateFlow(AssistantState())
    val assistant: StateFlow<AssistantState> = _assistant.asStateFlow()

    val OUTGOING_PHASES = setOf(CallPhase.NEW, CallPhase.DIALING, CallPhase.CONNECTING, CallPhase.SELECT_ACCOUNT)
    private val IN_PROGRESS = setOf(CallPhase.ACTIVE, CallPhase.HOLDING, CallPhase.DIALING, CallPhase.CONNECTING)

    internal fun publish(list: List<CallInfo>) {
        _calls.value = list
    }

    internal fun setAssistant(state: AssistantState) {
        _assistant.value = state
    }

    fun find(id: String?): CallInfo? = id?.let { wanted -> _calls.value.firstOrNull { it.id == wanted } }

    fun ringing(list: List<CallInfo> = _calls.value): CallInfo? =
        list.firstOrNull { it.phase == CallPhase.RINGING }

    /** The call the ongoing-call screen should focus on. */
    fun primary(list: List<CallInfo> = _calls.value): CallInfo? {
        // People inside a conference are shown through the conference itself.
        val candidates = list.filter { it.phase != CallPhase.RINGING && it.parentId == null }
        return candidates.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: candidates.firstOrNull { it.phase in OUTGOING_PHASES }
            ?: candidates.firstOrNull { it.phase == CallPhase.HOLDING }
            ?: candidates.firstOrNull { it.isLive }
            ?: candidates.firstOrNull()
    }

    fun hasCallInProgressOtherThan(id: String): Boolean =
        _calls.value.any { it.id != id && it.phase in IN_PROGRESS }
}

@Suppress("DEPRECATION")
private fun Call.legacyState(): Int = state

/** Call.getState() is deprecated on API 31+; Details.getState() replaces it. */
fun Call.stateCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else legacyState()

@SuppressLint("InlinedApi")
fun phaseOf(state: Int): CallPhase = when (state) {
    Call.STATE_NEW -> CallPhase.NEW
    Call.STATE_DIALING -> CallPhase.DIALING
    Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> CallPhase.CONNECTING
    Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.RINGING
    Call.STATE_ACTIVE -> CallPhase.ACTIVE
    Call.STATE_HOLDING -> CallPhase.HOLDING
    Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTING
    Call.STATE_DISCONNECTED -> CallPhase.DISCONNECTED
    Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SELECT_ACCOUNT
    Call.STATE_AUDIO_PROCESSING -> CallPhase.SCREENING
    else -> CallPhase.UNKNOWN
}
