package com.kaif.mastercaller.ui.call

import androidx.compose.runtime.collectAsState

import androidx.compose.material.icons.rounded.Stop

import androidx.compose.material.icons.rounded.FiberManualRecord

import androidx.compose.material.icons.rounded.CallMerge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kaif.mastercaller.telecom.AudioRouteManager
import com.kaif.mastercaller.telecom.AudioRouteState
import com.kaif.mastercaller.telecom.CallInfo
import com.kaif.mastercaller.telecom.CallManager
import com.kaif.mastercaller.telecom.CallPhase
import com.kaif.mastercaller.telecom.CallStateManager
import com.kaif.mastercaller.telecom.RouteType
import com.kaif.mastercaller.ui.components.CallerAvatar
import com.kaif.mastercaller.ui.components.CircleActionButton
import com.kaif.mastercaller.ui.components.ControlTile
import com.kaif.mastercaller.ui.components.DialPad
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.dialer.DialerActivity
import com.kaif.mastercaller.ui.theme.MasterCallerTheme
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Ongoing (and outgoing) call screen: duration, speaker/earpiece/Bluetooth, mute, hold, keypad, end. */
class OngoingCallActivity : ComponentActivity() {

    private var callId by mutableStateOf<String?>(null)
    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent, firstLaunch = savedInstanceState == null)
        setContent {
            MasterCallerTheme {
                OngoingCallRoute(requestedId = callId, onFinish = { finish() })
            }
        }
        // Turn the screen off near the ear while on the earpiece, like a stock dialer.
        lifecycleScope.launch {
            combine(CallStateManager.calls, AudioRouteManager.state) { calls, route ->
                val active = calls.any { it.phase == CallPhase.ACTIVE || it.phase == CallPhase.DIALING }
                active && route.current?.type == RouteType.EARPIECE
            }.collect { setProximity(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, firstLaunch = true)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent, firstLaunch: Boolean) {
        val id = intent.getStringExtra(EXTRA_CALL_ID)
        if (id != null) callId = id
        // Answer from the notification's "Answer" button. CallManager guards against double answers.
        if (firstLaunch && intent.action == ACTION_ANSWER && id != null) CallManager.answer(id)
    }

    private fun setProximity(enable: Boolean) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (enable) {
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            val lock = proximityLock ?: pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "MasterCaller:proximity",
            ).also { proximityLock = it }
            if (!lock.isHeld) lock.acquire(3 * 60 * 60 * 1000L)
        } else {
            proximityLock?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        }
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val ACTION_ANSWER = "com.kaif.mastercaller.action.ANSWER"

        fun intent(context: Context, callId: String): Intent =
            Intent(context, OngoingCallActivity::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun answerIntent(context: Context, callId: String): Intent =
            intent(context, callId).setAction(ACTION_ANSWER)
    }
}

@Composable
private fun OngoingCallRoute(requestedId: String?, onFinish: () -> Unit) {
    val calls by CallStateManager.calls.collectAsStateWithLifecycle()
    val route by AudioRouteManager.state.collectAsStateWithLifecycle()

    val requested = calls.firstOrNull { it.id == requestedId && it.phase != CallPhase.RINGING }
    // Always show the person you're talking to right now: after Answer (second call) or Swap the screen
    // follows the active call; the other person moves to the "on hold" bar.
    val call = CallStateManager.primary(calls)?.takeIf { it.isLive } ?: requested?.takeIf { it.isLive } ?: requested
    var lastShown by remember { mutableStateOf<CallInfo?>(null) }
    if (call != null) lastShown = call

    val anyLive = calls.any { it.isLive }
    LaunchedEffect(anyLive, call?.id) {
        if (!anyLive) {
            delay(1_400) // brief "Call ended" before closing
            onFinish()
        }
    }

    val shown = lastShown ?: run {
        LaunchedEffect(Unit) {
            delay(800)
            onFinish()
        }
        McBackground {}
        return
    }
    val ended = !anyLive || shown.phase == CallPhase.DISCONNECTED
    val held = calls.firstOrNull { it.id != shown.id && it.phase == CallPhase.HOLDING && it.parentId == null && it.id !in shown.childIds }
    val members = shown.childIds.mapNotNull { id -> calls.firstOrNull { it.id == id && it.isLive } }
    val waiting = CallStateManager.ringing(calls)

    OngoingCallScreen(shown, ended, route, held, waiting, members)
}

@Composable
private fun OngoingCallScreen(
    call: CallInfo,
    ended: Boolean,
    route: AudioRouteState,
    heldCall: CallInfo?,
    waitingCall: CallInfo?,
    members: List<CallInfo> = emptyList(),
) {
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    McBackground(wallpaper = call.wallpaperUri, brandMark = call.wallpaperUri == null) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (waitingCall != null && !ended) WaitingBanner(waitingCall)
            if (heldCall != null && !ended) HeldBanner(heldCall)

            // --- Top: who you're talking to ---------------------------------------------------
            Spacer(Modifier.height(8.dp))
            CallerAvatar(call.displayName, call.photoUri, size = if (showKeypad) 64.dp else 88.dp, pulsing = false)
            Text(
                call.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = McColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusText(call, ended, now),
                style = MaterialTheme.typography.titleMedium,
                color = if (call.phase == CallPhase.ACTIVE && !ended) McColors.Accent else McColors.TextSecondary,
            )
            if (call.contactName != null && call.formattedNumber != null && !call.isConference) {
                Text(call.formattedNumber!!, style = MaterialTheme.typography.bodyMedium, color = McColors.TextMuted)
            }
            // Conference: everyone who's in it, each can be dropped on their own.
            if (call.isConference && members.isNotEmpty() && !ended) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(McColors.Surface2.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("${members.size + 1} people talking: you and", style = MaterialTheme.typography.labelMedium, color = McColors.TextMuted)
                    members.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.contactName ?: m.formattedNumber ?: "Caller", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { CallManager.hangup(m.id) }) { Text("End", color = McColors.Reject) }
                        }
                    }
                }
            }
            route.lastError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = McColors.Warning)
            }

            // --- Middle: control boxes, centred in the free space ------------------------------
            Spacer(Modifier.weight(1f))
            when {
                ended -> Unit
                call.phase == CallPhase.SELECT_ACCOUNT -> SimPicker(call)
                showKeypad -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dtmf.ifEmpty { " " },
                        style = MaterialTheme.typography.headlineSmall,
                        color = McColors.TextPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    DialPad(onKey = { c ->
                        dtmf = (dtmf + c).takeLast(24)
                        CallManager.playDtmf(call.id, c)
                    })
                    TextButton(onClick = { showKeypad = false }) { Text("Hide keypad", color = McColors.Accent) }
                }
                else -> AudioControls(call, route, onOpenKeypad = { showKeypad = true })
            }
            Spacer(Modifier.weight(1f))

            // --- Bottom: end call ------------------------------------------------------------
            CircleActionButton(
                icon = Icons.Rounded.CallEnd,
                label = if (ended) "Call ended" else "End call",
                container = McColors.Reject,
                onClick = { CallManager.hangup(call.id) },
                enabled = !ended,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudioControls(call: CallInfo, route: AudioRouteState, onOpenKeypad: () -> Unit) {
    val context = LocalContext.current
    val current = route.current?.type
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ControlTile(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = if (route.speakerOn) "Speaker on" else "Speaker",
                active = route.speakerOn,
                onClick = { AudioRouteManager.setSpeaker(!route.speakerOn) },
                enabled = route.speaker != null,
                modifier = Modifier.weight(1f),
            )
            ControlTile(
                icon = Icons.Rounded.Hearing,
                label = "Earpiece",
                active = current == RouteType.EARPIECE,
                onClick = { route.earpiece?.let { AudioRouteManager.select(it) } },
                enabled = route.earpiece != null,
                modifier = Modifier.weight(1f),
            )
            ControlTile(
                icon = Icons.Rounded.MicOff,
                label = if (route.muted) "Muted" else "Mute",
                active = route.muted,
                onClick = { AudioRouteManager.setMuted(!route.muted) },
                enabled = call.canMute || call.phase == CallPhase.ACTIVE,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ControlTile(
                icon = Icons.Rounded.Dialpad,
                label = "Keypad",
                active = false,
                onClick = onOpenKeypad,
                modifier = Modifier.weight(1f),
            )
            ControlTile(
                icon = Icons.Rounded.Add,
                label = "Add call",
                active = false,
                onClick = {
                    context.startActivity(
                        Intent(context, DialerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                enabled = call.phase == CallPhase.ACTIVE || call.phase == CallPhase.HOLDING,
                modifier = Modifier.weight(1f),
            )
            val onHold = call.phase == CallPhase.HOLDING
            ControlTile(
                icon = Icons.Rounded.Pause,
                label = if (onHold) "Resume" else "Hold",
                active = onHold,
                onClick = { if (onHold) CallManager.unhold(call.id) else CallManager.hold(call.id) },
                enabled = call.canHold || onHold,
                modifier = Modifier.weight(1f),
            )
        }
        // Conference + recording.
        val rec by com.kaif.mastercaller.telecom.CallRecorder.state.collectAsState()
        val calls by com.kaif.mastercaller.telecom.CallStateManager.calls.collectAsState()
        val twoCalls = calls.count { (it.phase == CallPhase.ACTIVE || it.phase == CallPhase.HOLDING) && it.parentId == null } >= 2
        // Merge / Swap only make sense with a second call (added, or answered while talking).
        val showConferenceTools = twoCalls && !call.isConference
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showConferenceTools) {
                ControlTile(
                    icon = Icons.Rounded.CallMerge,
                    label = "Merge",
                    active = false,
                    onClick = { CallManager.merge(call.id) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
                ControlTile(
                    icon = Icons.Rounded.SwapCalls,
                    label = "Swap",
                    active = false,
                    onClick = { CallManager.swap(call.id) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ControlTile(
                icon = if (rec.recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                label = if (rec.recording) "Stop rec" else "Record",
                active = rec.recording,
                onClick = {
                    val r = com.kaif.mastercaller.telecom.CallRecorder
                    if (rec.recording) r.stop(context) else r.start(context, call.displayName)
                    com.kaif.mastercaller.telecom.MasterInCallService.instance?.refreshForeground()
                },
                enabled = call.phase == CallPhase.ACTIVE || rec.recording,
                modifier = Modifier.weight(1f),
            )
            if (!showConferenceTools) Spacer(Modifier.weight(1f))
        }
        val note = when {
            rec.recording && rec.quality == com.kaif.mastercaller.telecom.CallRecorder.Quality.MIC_ONLY ->
                "Recording through the microphone: put the call on Speaker so both voices are saved."
            rec.recording -> "Recording both sides."
            rec.error != null -> rec.error
            rec.lastFile != null -> "Saved in ${rec.lastFile}"
            else -> null
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        // Headset / Bluetooth boxes only appear when one is connected.
        val headsets = route.bluetooth + listOfNotNull(route.wired)
        if (headsets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                headsets.take(3).forEach { option ->
                    ControlTile(
                        icon = if (option.type == RouteType.BLUETOOTH) Icons.Rounded.Bluetooth else Icons.Rounded.Headset,
                        label = option.label,
                        active = route.current?.key == option.key,
                        onClick = { AudioRouteManager.select(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - headsets.take(3).size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SimPicker(call: CallInfo) {
    val context = LocalContext.current
    val sims = remember(call.id) { CallManager.availableSims(context, call.id) }
    McCard(highlight = true) {
        Text("Choose a SIM for this call", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        if (sims.isEmpty()) {
            Text(
                "No SIM list available. Grant the Phone permission in Setup.",
                style = MaterialTheme.typography.bodySmall,
                color = McColors.TextSecondary,
            )
        }
        sims.forEach { sim ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { CallManager.selectSim(call.id, sim.handle) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.SimCard, null, tint = McColors.Accent)
                Spacer(Modifier.width(10.dp))
                Text(sim.label, color = McColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun WaitingBanner(call: CallInfo) {
    Banner(
        text = "${call.displayName} is calling",
        actions = {
            TextButton(onClick = { CallManager.reject(call.id) }) { Text("Decline", color = McColors.Reject) }
            TextButton(onClick = { CallManager.answer(call.id) }) {
                androidx.compose.material3.Icon(Icons.Rounded.Call, null, tint = McColors.Answer)
                Spacer(Modifier.width(4.dp))
                Text("Answer", color = McColors.Answer)
            }
        },
    )
}

@Composable
private fun HeldBanner(call: CallInfo) {
    Banner(
        text = "${call.displayName} is on hold",
        actions = {
            TextButton(onClick = { CallManager.unhold(call.id) }) {
                androidx.compose.material3.Icon(Icons.Rounded.SwapCalls, null, tint = McColors.Accent)
                Spacer(Modifier.width(4.dp))
                Text("Swap", color = McColors.Accent)
            }
            TextButton(onClick = { CallManager.merge(call.id) }) {
                androidx.compose.material3.Icon(Icons.Rounded.CallMerge, null, tint = McColors.Accent)
                Spacer(Modifier.width(4.dp))
                Text("Merge", color = McColors.Accent)
            }
        },
    )
}

@Composable
private fun Banner(text: String, actions: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(McColors.Surface2)
            .border(1.dp, McColors.Outline, shape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = McColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

private fun statusText(call: CallInfo, ended: Boolean, now: Long): String {
    if (ended) return call.disconnectLabel ?: "Call ended"
    return when (call.phase) {
        CallPhase.ACTIVE -> if (call.connectTimeMillis > 0) formatDuration(now - call.connectTimeMillis) else "Connected"
        CallPhase.HOLDING -> "On hold"
        CallPhase.DIALING, CallPhase.NEW -> "Calling…"
        CallPhase.CONNECTING -> "Connecting…"
        CallPhase.SELECT_ACCOUNT -> "Choose a SIM"
        CallPhase.DISCONNECTING -> "Ending…"
        CallPhase.SCREENING -> "Screening…"
        else -> ""
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
