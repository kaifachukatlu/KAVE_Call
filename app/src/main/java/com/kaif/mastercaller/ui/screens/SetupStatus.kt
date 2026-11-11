package com.kaif.mastercaller.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class SetupSnapshot(
    val roleAvailable: Boolean,
    val isDefaultDialer: Boolean,
    val contacts: Boolean,
    val microphone: Boolean,
    val notifications: Boolean,
    val callPhone: Boolean,
    val phoneState: Boolean,
    val fullScreenIntent: Boolean,
) {
    val essentialsComplete: Boolean
        get() = isDefaultDialer && contacts && notifications
}

object SetupStatus {

    fun snapshot(context: Context): SetupSnapshot {
        val roleManager = context.getSystemService(RoleManager::class.java)
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
        return SetupSnapshot(
            roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true,
            isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true,
            contacts = granted(Manifest.permission.READ_CONTACTS),
            microphone = granted(Manifest.permission.RECORD_AUDIO),
            notifications = notifications,
            callPhone = granted(Manifest.permission.CALL_PHONE),
            phoneState = granted(Manifest.permission.READ_PHONE_STATE),
            fullScreenIntent = fullScreen,
        )
    }

    fun roleRequestIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appSettingsIntent(context)
        }
}
