package com.kaif.mastercaller.ui.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.WordingPreset
import com.kaif.mastercaller.data.WordingPresets
import com.kaif.mastercaller.telecom.AssistantPhase
import com.kaif.mastercaller.telecom.CallInfo
import com.kaif.mastercaller.telecom.CallManager
import com.kaif.mastercaller.telecom.CallPhase
import com.kaif.mastercaller.telecom.CallStateManager
import com.kaif.mastercaller.ui.components.CallerAvatar
import com.kaif.mastercaller.ui.components.CircleActionButton
import com.kaif.mastercaller.ui.components.ListeningOrb
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.poster.CallPoster
import androidx.compose.foundation.layout.BoxScope
import com.kaif.mastercaller.ui.theme.MasterCallerTheme
import com.kaif.mastercaller.ui.theme.McColors
import com.kaif.mastercaller.voice.CallUiVisibility
import com.kaif.mastercaller.voice.IncomingCallAssistant
import com.kaif.mastercaller.voice.VoiceCommand
import kotlinx.coroutines.delay

/** Full-screen incoming call UI. Shows over the lock screen (manifest showWhenLocked/turnScreenOn). */
class IncomingCallActivity : ComponentActivity() {

    private var callId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        setContent {
            MasterCallerTheme {
                IncomingCallRoute(
                    requestedId = callId,
                    onFinish = { finish() },
                    onAnswered = { id ->
                        startActivity(OngoingCallActivity.intent(this, id))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callId = intent.getStringExtra(EXTRA_CALL_ID)
    }

    override fun onStart() {
        super.onStart()
        CallUiVisibility.incomingVisible = true
    }

    override fun onStop() {
        CallUiVisibility.incomingVisible = false
        super.onStop()
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"

        fun intent(context: Context, callId: String): Intent =
            Intent(context, IncomingCallActivity::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun IncomingCallRoute(requestedId: String?, onFinish: () -> Unit, onAnswered: (String) -> Unit) {
    val calls by CallStateManager.calls.collectAsStateWithLifecycle()
    val assistant by CallStateManager.assistant.collectAsStateWithLifecycle()
    val level by MasterCallerApp.instance.speech.level.collectAsStateWithLifecycle()
    val settings by MasterCallerApp.instance.settingsRepository.settings.collectAsStateWithLifecycle()
    val hints = WordingPresets.forLanguage(settings.languageTag)

    val call = calls.firstOrNull { it.id == requestedId } ?: CallStateManager.ringing(calls)
    var lastShown by remember { mutableStateOf<CallInfo?>(null) }
    if (call != null) lastShown = call

    LaunchedEffect(call?.id, call?.phase) {
        when {
            call == null -> {
                delay(500)
                onFinish()
            }
            call.phase == CallPhase.RINGING -> Unit
            call.phase == CallPhase.ACTIVE || call.phase == CallPhase.HOLDING -> onAnswered(call.id)
            else -> {
                delay(700)
                onFinish()
            }
        }
    }

    val shown = lastShown ?: return
    val phase = if (assistant.callId == shown.id) assistant.phase else AssistantPhase.Idle
    var acted by remember(shown.id) { mutableStateOf(false) }
    val ringing = shown.phase == CallPhase.RINGING

    IncomingCallScreen(
        call = shown,
        phase = phase,
        micLevel = level,
        hints = hints,
        controlsEnabled = ringing && !acted,
        onAnswer = {
            acted = true
            if (!CallManager.answer(shown.id)) acted = false
        },
        onReject = {
            acted = true
            if (!CallManager.reject(shown.id)) acted = false
        },
        onRepeat = { IncomingCallAssistant.active?.repeatAnnouncement(shown.id) },
        onListen = { IncomingCallAssistant.active?.listenNow(shown.id) },
    )
}

@Composable
private fun IncomingCallScreen(
    call: CallInfo,
    phase: AssistantPhase,
    micLevel: Float,
    hints: WordingPreset,
    controlsEnabled: Boolean,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onRepeat: () -> Unit,
    onListen: () -> Unit,
) {
    val wallpaper = call.wallpaperUri
    val posterMode = wallpaper != null
    val body: @Composable BoxScope.() -> Unit = {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text("KAVÉ CALL", style = MaterialTheme.typography.labelSmall, color = McColors.Accent)
            Spacer(Modifier.height(4.dp))
            Text(
                if (call.phase == CallPhase.RINGING) "Incoming call" else "Call ended",
                style = MaterialTheme.typography.bodyMedium,
                color = McColors.TextSecondary,
            )

            if (posterMode) {
                // The poster already shows the big name and the person; leave the upper area to it.
                Spacer(Modifier.weight(1.4f))
            } else {
                Spacer(Modifier.weight(0.6f))
                CallerAvatar(call.displayName, call.photoUri, size = 132.dp, pulsing = call.phase == CallPhase.RINGING)
                Spacer(Modifier.height(8.dp))
                Text(
                    call.displayName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = McColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            val sub = when {
                call.contactName != null -> call.formattedNumber
                call.isPrivateNumber -> "Number withheld"
                else -> "Not in your contacts"
            }
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodyLarge, color = McColors.TextSecondary)
            }

            Spacer(Modifier.weight(0.5f))
            AssistantPanel(phase, micLevel, hints, controlsEnabled, onRepeat, onListen)
            Spacer(Modifier.weight(0.5f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CircleActionButton(
                    icon = Icons.Rounded.CallEnd,
                    label = "Decline",
                    container = McColors.Reject,
                    onClick = onReject,
                    enabled = controlsEnabled,
                )
                CircleActionButton(
                    icon = Icons.Rounded.Call,
                    label = "Accept",
                    container = McColors.Answer,
                    content = McColors.Ink,
                    onClick = onAnswer,
                    enabled = controlsEnabled,
                )
            }
        }
    }
    if (wallpaper != null) {
        CallPoster(wallpaper, call.displayName, call.posterStyle, content = body)
    } else {
        McBackground(content = body)
    }
}

@Composable
private fun AssistantPanel(
    phase: AssistantPhase,
    micLevel: Float,
    hints: WordingPreset,
    enabled: Boolean,
    onRepeat: () -> Unit,
    onListen: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(McColors.Surface.copy(alpha = 0.85f))
            .border(1.dp, if (phase is AssistantPhase.Listening) McColors.Accent else McColors.Outline, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val listening = phase is AssistantPhase.Listening
            val icon: ImageVector = when (phase) {
                is AssistantPhase.Announcing -> Icons.Rounded.RecordVoiceOver
                is AssistantPhase.Listening -> Icons.Rounded.Mic
                is AssistantPhase.Heard -> Icons.Rounded.GraphicEq
                is AssistantPhase.Notice -> Icons.Rounded.MicOff
                else -> Icons.Rounded.Mic
            }
            ListeningOrb(level = micLevel, active = listening || phase is AssistantPhase.Announcing, icon = icon)
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "assistantText",
            ) { p ->
                Column {
                    Text(titleFor(p), style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                    Text(
                        detailFor(p, hints),
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallAction(Icons.Rounded.Replay, "Repeat", enabled, onRepeat)
            SmallAction(Icons.Rounded.Mic, "Speak a command", enabled && phase !is AssistantPhase.Listening, onListen)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = McColors.Surface3,
                contentColor = McColors.Accent,
            ),
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = McColors.TextSecondary)
    }
}

private fun titleFor(p: AssistantPhase): String = when (p) {
    is AssistantPhase.Announcing -> "Announcing caller"
    is AssistantPhase.Listening -> "Listening for ${p.seconds}s…"
    is AssistantPhase.Heard -> if (p.command == VoiceCommand.ANSWER) "Picking up" else "Rejecting"
    is AssistantPhase.Notice -> "Voice assistant"
    AssistantPhase.Ringing -> "Ringing"
    AssistantPhase.Idle -> "Voice assistant"
}

private fun detailFor(p: AssistantPhase, hints: WordingPreset): String = when (p) {
    is AssistantPhase.Announcing -> "“${p.text}”"
    is AssistantPhase.Listening ->
        "Say “${hints.answerHint}” or “${hints.rejectHint}”. The mic turns off after the window."
    is AssistantPhase.Heard -> p.text?.let { "Heard “$it”" } ?: "Command recognised"
    is AssistantPhase.Notice -> p.message
    AssistantPhase.Ringing, AssistantPhase.Idle ->
        "Tap a button, or tap the mic and say “${hints.answerHint}”."
}
