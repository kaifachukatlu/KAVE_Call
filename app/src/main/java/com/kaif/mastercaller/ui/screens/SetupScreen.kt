package com.kaif.mastercaller.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kaif.mastercaller.ui.components.IconBadge
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.PrimaryButton
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.components.StatusChip
import com.kaif.mastercaller.ui.theme.McColors

@Composable
fun SetupScreen(onBack: (() -> Unit)?, onDone: () -> Unit) {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(SetupStatus.snapshot(context)) }
    val refresh = { snap = SetupStatus.snapshot(context) }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        if (!granted) {
            Toast.makeText(context, "Permission denied. You can enable it in App settings.", Toast.LENGTH_LONG).show()
        }
    }

    McBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ScreenHeader("Setup", "Permissions KAVÉ Call needs, and why", onBack)

            SectionLabel("Step 1: Default phone app")
            PermissionCard(
                icon = Icons.Rounded.Call,
                title = "Default phone app",
                why = "Android only lets the default phone app show its own incoming-call screen, answer, " +
                    "reject and route call audio. KAVÉ Call will then handle all calls. You can switch back any time.",
                granted = snap.isDefaultDialer,
                actionLabel = "Set as default",
                onAction = {
                    val intent = SetupStatus.roleRequestIntent(context)
                    if (intent != null && snap.roleAvailable) {
                        roleLauncher.launch(intent)
                    } else {
                        safeStart(context, SetupStatus.defaultAppsSettingsIntent())
                    }
                },
                secondaryLabel = "Default apps settings",
                onSecondary = { safeStart(context, SetupStatus.defaultAppsSettingsIntent()) },
            )

            SectionLabel("Step 2: Permissions")
            PermissionCard(
                icon = Icons.Rounded.Contacts,
                title = "Contacts",
                why = "Reads your saved contacts on the phone to say the caller's name. Nothing is uploaded.",
                granted = snap.contacts,
                actionLabel = "Allow",
                onAction = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                secondaryLabel = "App settings",
                onSecondary = { safeStart(context, SetupStatus.appSettingsIntent(context)) },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Rounded.Mic,
                title = "Microphone",
                why = "Used only during a short, on-screen listening window after the announcement, so you can " +
                    "say \"Pick up\" or \"Reject\". Never records calls or listens in the background.",
                granted = snap.microphone,
                actionLabel = "Allow",
                onAction = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                secondaryLabel = "App settings",
                onSecondary = { safeStart(context, SetupStatus.appSettingsIntent(context)) },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Rounded.Notifications,
                title = "Notifications",
                why = "Shows the incoming-call alert (including over the lock screen) and the ongoing-call notification.",
                granted = snap.notifications,
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        safeStart(context, SetupStatus.appSettingsIntent(context))
                    }
                },
                secondaryLabel = "App settings",
                onSecondary = { safeStart(context, SetupStatus.appSettingsIntent(context)) },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Rounded.SimCard,
                title = "Phone",
                why = "Places calls from the built-in dialer and lists your SIMs on dual-SIM phones.",
                granted = snap.callPhone && snap.phoneState,
                actionLabel = "Allow",
                onAction = {
                    permissionLauncher.launch(
                        if (!snap.callPhone) Manifest.permission.CALL_PHONE else Manifest.permission.READ_PHONE_STATE,
                    )
                },
                secondaryLabel = "App settings",
                onSecondary = { safeStart(context, SetupStatus.appSettingsIntent(context)) },
            )
            if (Build.VERSION.SDK_INT >= 34) {
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Full-screen call alerts",
                    why = "Android 14+ lets you control whether apps may show full-screen alerts. Needed for the " +
                        "incoming-call screen to appear over the lock screen.",
                    granted = snap.fullScreenIntent,
                    actionLabel = "Open settings",
                    onAction = { safeStart(context, SetupStatus.fullScreenSettingsIntent(context)) },
                )
            }

            Spacer(Modifier.height(16.dp))
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Security)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Privacy: contacts, call details and voice never leave your phone. No accounts, no servers, no call recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (snap.essentialsComplete) "Continue to dashboard" else "Continue anyway",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    McCard(highlight = !granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = if (granted) McColors.Success else McColors.Warning)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = McColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusChip(if (granted) "Granted" else "Needed", ok = granted)
        }
        Spacer(Modifier.height(10.dp))
        Text(why, style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(actionLabel, onAction)
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) { Text(secondaryLabel, color = McColors.TextSecondary) }
                }
            }
        }
    }
}

fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "That settings page isn't available on this phone", Toast.LENGTH_LONG).show()
    }
}
