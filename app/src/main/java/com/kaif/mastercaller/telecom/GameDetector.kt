package com.kaif.mastercaller.telecom

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PowerManager
import android.os.Process

/** Knows whether the Boss is using the phone (and whether that's a game) when a call comes in. */
object GameDetector {

    /** Screen on and unlocked: show a small pop-up instead of taking over the screen. */
    fun phoneInUse(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        return pm?.isInteractive == true && km?.isKeyguardLocked != true
    }

    /** "Usage access" lets us see which app is in front (needed to know it's a game). */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The app currently in front, from the last "moved to foreground" event (null without usage access). */
    fun foregroundApp(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30 * 60_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        }
        return last
    }

    /** True when a game is on screen right now. */
    fun gameInFront(context: Context): Boolean {
        if (!phoneInUse(context)) return false
        val pkg = foregroundApp(context) ?: return false
        if (pkg == context.packageName) return false
        return runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            @Suppress("DEPRECATION")
            (Build.VERSION.SDK_INT >= 26 && info.category == ApplicationInfo.CATEGORY_GAME) ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        }.getOrDefault(false)
    }
}
