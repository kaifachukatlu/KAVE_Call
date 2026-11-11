package com.kaif.mastercaller.telecom

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RouteType { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET, STREAMING, UNKNOWN }

data class RouteOption(val key: String, val type: RouteType, val label: String)

data class AudioRouteState(
    val current: RouteOption? = null,
    val available: List<RouteOption> = emptyList(),
    val muted: Boolean = false,
    val usesEndpointApi: Boolean = false,
    val lastError: String? = null,
) {
    val speakerOn: Boolean get() = current?.type == RouteType.SPEAKER
    val speaker: RouteOption? get() = available.firstOrNull { it.type == RouteType.SPEAKER }
    val earpiece: RouteOption? get() = available.firstOrNull { it.type == RouteType.EARPIECE }
    val wired: RouteOption? get() = available.firstOrNull { it.type == RouteType.WIRED_HEADSET }
    val bluetooth: List<RouteOption> get() = available.filter { it.type == RouteType.BLUETOOTH }
}

/**
 * Call audio routing.
 *  - Android 14+ (API 34): CallEndpoint + InCallService.requestCallEndpointChange().
 *  - Android 10-13: CallAudioState + InCallService.setAudioRoute() (the only API available there).
 * Speaker is only requested after the call is ACTIVE, never while ringing.
 */
object AudioRouteManager {
    private const val TAG = "AudioRouteManager"
    private const val MAX_AUTO_ATTEMPTS = 3
    private const val VERIFY_DELAY_MS = 1_500L

    private val handler = Handler(Looper.getMainLooper())

    // Only set while Telecom has the service bound; cleared in detach() from InCallService.onDestroy().
    @SuppressLint("StaticFieldLeak")
    private var service: InCallService? = null
    private val _state = MutableStateFlow(AudioRouteState())
    val state: StateFlow<AudioRouteState> = _state.asStateFlow()

    private var autoSpeakerArmed = false
    private var autoSpeakerAttempts = 0
    private var userChoseRoute = false
    private val verifyRunnable = Runnable { maybeApplyAutoSpeaker() }

    private val headsetTypes = setOf(RouteType.BLUETOOTH, RouteType.WIRED_HEADSET, RouteType.STREAMING)

    fun attach(s: InCallService) {
        service = s
    }

    fun detach(s: InCallService) {
        if (service === s) {
            service = null
            resetForNextCall()
        }
    }

    /** Clears per-call decisions; keeps the last known endpoint list (Telecom only re-sends on change). */
    fun resetForNextCall() {
        handler.removeCallbacks(verifyRunnable)
        autoSpeakerArmed = false
        autoSpeakerAttempts = 0
        userChoseRoute = false
        _state.update { it.copy(lastError = null) }
    }

    /** An incoming call has just become ACTIVE after the user answered. */
    fun onIncomingCallAnswered(autoSpeakerEnabled: Boolean) {
        handler.removeCallbacks(verifyRunnable)
        userChoseRoute = false
        autoSpeakerAttempts = 0
        autoSpeakerArmed = autoSpeakerEnabled
        maybeApplyAutoSpeaker()
    }

    // --- callbacks from MasterInCallService ----------------------------------------------------

    @RequiresApi(34)
    fun onAvailableEndpoints(endpoints: List<CallEndpoint>) {
        Api34.endpoints.clear()
        endpoints.forEach { Api34.endpoints[Api34.key(it)] = it }
        _state.update { st -> st.copy(available = endpoints.map { Api34.option(it) }, usesEndpointApi = true) }
        maybeApplyAutoSpeaker()
    }

    @RequiresApi(34)
    fun onEndpointChanged(endpoint: CallEndpoint) {
        _state.update { it.copy(current = Api34.option(endpoint), usesEndpointApi = true, lastError = null) }
        maybeApplyAutoSpeaker()
    }

    fun onMuteChanged(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
    }

    @Suppress("DEPRECATION")
    fun onLegacyAudioState(audio: CallAudioState) {
        val mask = audio.supportedRouteMask
        val options = buildList {
            if ((mask and CallAudioState.ROUTE_EARPIECE) != 0) add(legacyOption(RouteType.EARPIECE))
            if ((mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) add(legacyOption(RouteType.WIRED_HEADSET))
            if ((mask and CallAudioState.ROUTE_BLUETOOTH) != 0) add(legacyOption(RouteType.BLUETOOTH))
            if ((mask and CallAudioState.ROUTE_SPEAKER) != 0) add(legacyOption(RouteType.SPEAKER))
        }
        val current = when (audio.route) {
            CallAudioState.ROUTE_EARPIECE -> RouteType.EARPIECE
            CallAudioState.ROUTE_SPEAKER -> RouteType.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> RouteType.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> RouteType.WIRED_HEADSET
            else -> RouteType.UNKNOWN
        }
        _state.update {
            it.copy(available = options, current = legacyOption(current), muted = audio.isMuted, usesEndpointApi = false)
        }
        maybeApplyAutoSpeaker()
    }

    // --- user actions --------------------------------------------------------------------------

    fun select(option: RouteOption, byUser: Boolean = true) {
        if (byUser) {
            userChoseRoute = true
            autoSpeakerArmed = false
            handler.removeCallbacks(verifyRunnable)
        }
        val s = service
        if (s == null) {
            _state.update { it.copy(lastError = "No active call") }
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val endpoint = Api34.endpoints[option.key]
            if (endpoint != null) {
                Api34.request(s, endpoint, handler) { msg -> _state.update { it.copy(lastError = msg) } }
                return
            }
        }
        legacySetRoute(s, option.type)
    }

    /** Speaker on, or back to the normal earpiece (or wired/Bluetooth headset if no earpiece). */
    fun setSpeaker(on: Boolean) {
        val st = _state.value
        val target = if (on) st.speaker else st.earpiece ?: st.wired ?: st.bluetooth.firstOrNull()
        if (target != null) select(target) else _state.update { it.copy(lastError = "That audio route is not available") }
    }

    fun setMuted(muted: Boolean) {
        val s = service ?: return
        s.setMuted(muted)
        _state.update { it.copy(muted = muted) }
    }

    // --- internals -----------------------------------------------------------------------------

    private fun maybeApplyAutoSpeaker() {
        if (!autoSpeakerArmed || userChoseRoute) return
        val st = _state.value
        val current = st.current ?: return // wait until Telecom tells us the route
        if (current.type in headsetTypes) {
            autoSpeakerArmed = false // respect a connected Bluetooth / wired headset
            return
        }
        if (current.type == RouteType.SPEAKER) {
            autoSpeakerArmed = false
            return
        }
        val speaker = st.speaker ?: return
        if (autoSpeakerAttempts >= MAX_AUTO_ATTEMPTS) {
            autoSpeakerArmed = false
            Log.w(TAG, "Speaker request not honoured after $MAX_AUTO_ATTEMPTS attempts")
            return
        }
        autoSpeakerAttempts++
        select(speaker, byUser = false)
        // Some OEM builds reset the route right after activation; verify and retry a few times.
        handler.removeCallbacks(verifyRunnable)
        handler.postDelayed(verifyRunnable, VERIFY_DELAY_MS)
    }

    @Suppress("DEPRECATION")
    private fun legacySetRoute(s: InCallService, type: RouteType) {
        val route = when (type) {
            RouteType.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            RouteType.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            RouteType.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            RouteType.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> return
        }
        s.setAudioRoute(route)
    }

    private fun legacyOption(type: RouteType) = RouteOption("legacy-${type.name}", type, defaultLabel(type))

    internal fun defaultLabel(type: RouteType): String = when (type) {
        RouteType.EARPIECE -> "Earpiece"
        RouteType.SPEAKER -> "Speaker"
        RouteType.BLUETOOTH -> "Bluetooth"
        RouteType.WIRED_HEADSET -> "Headset"
        RouteType.STREAMING -> "Streaming"
        RouteType.UNKNOWN -> "Audio"
    }

    @RequiresApi(34)
    private object Api34 {
        val endpoints = HashMap<String, CallEndpoint>()

        fun key(e: CallEndpoint): String = "ep-${e.identifier}"

        fun option(e: CallEndpoint): RouteOption {
            val type = when (e.endpointType) {
                CallEndpoint.TYPE_EARPIECE -> RouteType.EARPIECE
                CallEndpoint.TYPE_SPEAKER -> RouteType.SPEAKER
                CallEndpoint.TYPE_BLUETOOTH -> RouteType.BLUETOOTH
                CallEndpoint.TYPE_WIRED_HEADSET -> RouteType.WIRED_HEADSET
                CallEndpoint.TYPE_STREAMING -> RouteType.STREAMING
                else -> RouteType.UNKNOWN
            }
            val name = e.endpointName.toString().ifBlank { defaultLabel(type) }
            return RouteOption(key(e), type, name)
        }

        fun request(s: InCallService, e: CallEndpoint, handler: Handler, report: (String) -> Unit) {
            s.requestCallEndpointChange(
                e,
                { runnable -> handler.post(runnable) },
                object : OutcomeReceiver<Void?, CallEndpointException> {
                    override fun onResult(result: Void?) = Unit
                    override fun onError(error: CallEndpointException) {
                        Log.w(TAG, "Endpoint change failed: code=${error.code}", error)
                        report(error.message ?: "Could not switch audio route")
                    }
                },
            )
        }
    }
}
