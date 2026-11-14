package com.kaif.mastercaller.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kaif.mastercaller.telecom.CallManager

/** Handles Decline / Hang up taps on call notifications. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CALL_ID)
        when (intent.action) {
            ACTION_DECLINE -> if (id == null || !CallManager.reject(id)) endViaTelecom(context)
            ACTION_HANGUP -> if (!CallManager.hangupAny(id)) endViaTelecom(context)
        }
    }

    /** Last resort when the app was restarted and doesn't hold the call yet: ask Telecom to end it. */
    @Suppress("DEPRECATION")
    private fun endViaTelecom(context: Context) {
        runCatching {
            context.getSystemService(android.telecom.TelecomManager::class.java)?.endCall()
        }.onFailure { android.util.Log.w("CallActionReceiver", "endCall failed", it) }
    }

    companion object {
        const val ACTION_DECLINE = "com.kaif.mastercaller.action.DECLINE"
        const val ACTION_HANGUP = "com.kaif.mastercaller.action.HANGUP"
        const val EXTRA_CALL_ID = "call_id"

        fun intent(context: Context, action: String, callId: String): Intent =
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_CALL_ID, callId)
    }
}
