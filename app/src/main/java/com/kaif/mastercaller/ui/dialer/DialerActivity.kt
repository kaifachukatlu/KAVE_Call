package com.kaif.mastercaller.ui.dialer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaif.mastercaller.ui.components.CircleActionButton
import com.kaif.mastercaller.ui.components.DialPad
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.theme.MasterCallerTheme
import com.kaif.mastercaller.ui.theme.McColors

/**
 * Minimal dial pad. Handling ACTION_DIAL is required to hold the default phone app role, and it lets
 * you place calls while Master Caller AI is the default dialer.
 */
class DialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        setContent { MasterCallerTheme { DialerScreen(initial, onBack = { finish() }) } }
    }
}

@Composable
private fun DialerScreen(initial: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf(initial) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) placeCall(context, number)
        else Toast.makeText(context, "Phone permission is needed to place calls", Toast.LENGTH_LONG).show()
    }

    McBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Dialer", onBack = onBack)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    number.ifEmpty { "Enter number" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (number.isEmpty()) McColors.TextMuted else McColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(48.dp)) {
                    if (number.isNotEmpty()) {
                        IconButton(onClick = { number = number.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = McColors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            DialPad(onKey = { number = (number + it).take(32) })
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircleActionButton(
                    icon = Icons.Rounded.Call,
                    label = "Call",
                    container = McColors.Answer,
                    enabled = number.isNotBlank(),
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) placeCall(context, number) else permission.launch(Manifest.permission.CALL_PHONE)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@SuppressLint("MissingPermission")
private fun placeCall(context: Context, number: String) {
    if (number.isBlank()) return
    val telecom = context.getSystemService(TelecomManager::class.java)
    try {
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
        Toast.makeText(context, "Phone permission is needed to place calls", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not place the call", Toast.LENGTH_LONG).show()
    }
}
