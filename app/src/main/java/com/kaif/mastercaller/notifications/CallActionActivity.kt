package com.kaif.mastercaller.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.kaif.mastercaller.telecom.CallManager

/**
 * Invisible screen behind the notification's Hang up / Decline buttons. Some phones (vivo, Xiaomi, Oppo)
 * silently block notification broadcasts to apps that aren't on their auto-start list; opening an activity
 * is always delivered. It ends the call and closes immediately.
 */
class CallActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(CallActionReceiver.EXTRA_CALL_ID)
        Log.i("CallActionActivity", "action=${intent.action} id=$id")
        val done = when (intent.action) {
            CallActionReceiver.ACTION_DECLINE -> id != null && CallManager.reject(id)
            else -> CallManager.hangupAny(id)
        }
        if (!done) endViaTelecom()
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun endViaTelecom() {
        runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.endCall() }
            .onFailure { Log.w("CallActionActivity", "endCall failed", it) }
    }

    companion object {
        fun intent(context: Context, action: String, callId: String): Intent =
            Intent(context, CallActionActivity::class.java).setAction(action)
                .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
