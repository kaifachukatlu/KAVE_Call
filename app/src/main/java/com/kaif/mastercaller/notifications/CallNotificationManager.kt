package com.kaif.mastercaller.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.kaif.mastercaller.R
import com.kaif.mastercaller.telecom.CallInfo
import com.kaif.mastercaller.telecom.CallPhase
import com.kaif.mastercaller.telecom.CallStateManager
import com.kaif.mastercaller.ui.call.IncomingCallActivity
import com.kaif.mastercaller.ui.call.OngoingCallActivity

/** Incoming (full-screen, CallStyle) and ongoing call notifications. */
class CallNotificationManager(
    context: Context,
    /** The in-call service keeps itself in the foreground with the ongoing-call notification. */
    private val onOngoing: (Int, Notification?) -> Unit = { _, _ -> },
) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private var incomingKey: String? = null
    private var ongoingKey: String? = null

    fun update(calls: List<CallInfo>) {
        val ringing = CallStateManager.ringing(calls)
        if (ringing != null) showIncoming(ringing) else cancelIncoming()

        val primary = CallStateManager.primary(calls)?.takeIf { it.isLive }
        if (primary != null) showOngoing(primary) else cancelOngoing()
    }

    fun showIncoming(call: CallInfo) {
        val key = "${call.id}|${call.displayName}"
        if (key == incomingKey) return
        incomingKey = key

        val fullScreen = PendingIntent.getActivity(
            appContext, call.id.hashCode(), IncomingCallActivity.intent(appContext, call.id), FLAGS,
        )
        // Answer opens an activity directly (Android 12+ forbids notification trampolines).
        val answer = PendingIntent.getActivity(
            appContext, call.id.hashCode() + 1, OngoingCallActivity.answerIntent(appContext, call.id), FLAGS,
        )
        val decline = PendingIntent.getBroadcast(
            appContext, call.id.hashCode() + 2,
            CallActionReceiver.intent(appContext, CallActionReceiver.ACTION_DECLINE, call.id), FLAGS,
        )
        val person = Person.Builder().setName(call.displayName).setImportant(true).build()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.displayName)
            .setContentText(call.formattedNumber?.takeIf { call.contactName != null } ?: "Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFB9A27B.toInt())
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
        post(INCOMING_ID, notification)
    }

    private fun showOngoing(call: CallInfo) {
        val key = "${call.id}|${call.phase}|${call.displayName}|${call.connectTimeMillis}"
        if (key == ongoingKey) return
        ongoingKey = key

        val open = PendingIntent.getActivity(
            appContext, call.id.hashCode() + 3, OngoingCallActivity.intent(appContext, call.id), FLAGS,
        )
        val hangup = PendingIntent.getBroadcast(
            appContext, call.id.hashCode() + 4,
            CallActionReceiver.intent(appContext, CallActionReceiver.ACTION_HANGUP, call.id), FLAGS,
        )
        val status = when (call.phase) {
            CallPhase.ACTIVE -> "Ongoing call"
            CallPhase.HOLDING -> "On hold"
            CallPhase.DIALING, CallPhase.CONNECTING, CallPhase.NEW -> "Calling…"
            CallPhase.SELECT_ACCOUNT -> "Choose a SIM"
            else -> "Call"
        }
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.displayName)
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFFB9A27B.toInt())
            .setContentIntent(open)
            .addAction(NotificationCompat.Action.Builder(null, "Hang up", hangup).build())
        if (call.phase == CallPhase.ACTIVE && call.connectTimeMillis > 0) {
            builder.setWhen(call.connectTimeMillis).setUsesChronometer(true).setShowWhen(true)
        }
        val n = builder.build()
        post(ONGOING_ID, n)
        onOngoing(ONGOING_ID, n)
    }

    fun cancelAll() {
        cancelIncoming()
        cancelOngoing()
    }

    private fun cancelIncoming() {
        if (incomingKey != null) {
            manager.cancel(INCOMING_ID)
            incomingKey = null
        }
    }

    private fun cancelOngoing() {
        if (ongoingKey != null) {
            manager.cancel(ONGOING_ID)
            ongoingKey = null
            onOngoing(ONGOING_ID, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            manager.notify(id, notification)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }

    companion object {
        private const val TAG = "CallNotifications"
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_ONGOING = "ongoing_calls"
        private const val INCOMING_ID = 4201
        private const val ONGOING_ID = 4202
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val incoming = NotificationChannel(
                CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Full-screen incoming call alerts"
                setSound(null, null) // the app rings and announces itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val ongoing = NotificationChannel(
                CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Shows the active call"
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannels(listOf(incoming, ongoing))
        }
    }
}
