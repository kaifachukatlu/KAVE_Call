package com.kaif.mastercaller.ui.dialer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.contacts.CallKind
import com.kaif.mastercaller.contacts.PhoneContact
import com.kaif.mastercaller.contacts.PhoneDirectory
import com.kaif.mastercaller.contacts.RecentCall
import com.kaif.mastercaller.ui.components.CircleActionButton
import com.kaif.mastercaller.ui.components.DialPad
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.theme.MasterCallerTheme
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KAVÉ phone section: Recents, Contacts and Keypad, like a stock phone app.
 * Handling ACTION_DIAL is also required for the default phone app role.
 */
class DialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        val startTab = intent?.getStringExtra(EXTRA_TAB)
        setContent { MasterCallerTheme { PhoneScreen(initial, startTab, onBack = { finish() }) } }
    }

    companion object {
        private const val EXTRA_TAB = "start_tab"
        const val TAB_RECENTS = "RECENTS"
        const val TAB_CONTACTS = "CONTACTS"
        const val TAB_KEYPAD = "KEYPAD"

        fun intent(context: Context, tab: String): android.content.Intent =
            android.content.Intent(context, DialerActivity::class.java).putExtra(EXTRA_TAB, tab)
    }
}

private enum class PhoneTab(val label: String, val icon: ImageVector) {
    RECENTS("Recents", Icons.Rounded.History),
    CONTACTS("Contacts", Icons.Rounded.Contacts),
    KEYPAD("Keypad", Icons.Rounded.Dialpad),
}

@Composable
private fun PhoneScreen(initialNumber: String, startTab: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val directory = remember { PhoneDirectory(context) }
    var tab by rememberSaveable {
        mutableStateOf(
            when {
                initialNumber.isNotEmpty() -> PhoneTab.KEYPAD
                else -> PhoneTab.entries.firstOrNull { it.name == startTab } ?: PhoneTab.RECENTS
            },
        )
    }

    // One place to start calls (asks for the Phone permission the first time).
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val n = pendingNumber
        pendingNumber = null
        if (granted && n != null) placeCall(context, n)
        else if (!granted) Toast.makeText(context, "Phone permission is needed to place calls", Toast.LENGTH_LONG).show()
    }
    val call: (String) -> Unit = { number ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            placeCall(context, number)
        } else {
            pendingNumber = number
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    var detail by remember { mutableStateOf<PhoneContact?>(null) }
    var contactsVersion by remember { mutableIntStateOf(0) }
    detail?.let { c ->
        ContactDetail(
            contact = c,
            onBack = { detail = null },
            onCall = call,
            onEdited = {
                // The contact may have been renamed/changed in the editor: reload the list and close the page.
                contactsVersion++
                detail = null
            },
        )
        return
    }

    McBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = McColors.TextPrimary)
                }
                Text(tab.label, style = MaterialTheme.typography.headlineMedium, color = McColors.TextPrimary)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PhoneTab.RECENTS -> RecentsTab(directory, call)
                    PhoneTab.CONTACTS -> ContactsTab(directory, contactsVersion, call, onOpen = { detail = it })
                    PhoneTab.KEYPAD -> KeypadTab(initialNumber, call)
                }
            }
            TabBar(tab) { tab = it }
        }
    }
}

@Composable
private fun TabBar(selected: PhoneTab, onSelect: (PhoneTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(McColors.Surface)
            .border(1.dp, McColors.Outline, RoundedCornerShape(22.dp))
            .padding(6.dp),
    ) {
        PhoneTab.entries.forEach { t ->
            val on = t == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) McColors.Surface3 else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, null, tint = if (on) McColors.Accent else McColors.TextMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) McColors.TextPrimary else McColors.TextMuted)
            }
        }
    }
}

// --- Recents --------------------------------------------------------------------------------------

@Composable
private fun RecentsTab(directory: PhoneDirectory, call: (String) -> Unit) {
    var allowed by remember { mutableStateOf(directory.canReadCallLog()) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = directory.canReadCallLog()
    }
    LifecycleResumeEffect(Unit) {
        allowed = directory.canReadCallLog()
        refresh++
        onPauseOrDispose { }
    }
    if (!allowed) {
        PermissionAsk(
            text = "Allow access to your call history to see recent calls here. It stays on your phone.",
            button = "Allow call history",
        ) { permission.launch(Manifest.permission.READ_CALL_LOG) }
        return
    }
    val calls by produceState<List<RecentCall>?>(null, refresh, allowed) {
        value = withContext(Dispatchers.IO) { directory.loadRecents() }
    }
    val list = calls ?: return
    if (list.isEmpty()) {
        EmptyNote("No recent calls")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
        items(list, key = { it.id }) { c ->
            val missed = c.kind == CallKind.MISSED || c.kind == CallKind.REJECTED
            val (icon, tint) = when (c.kind) {
                CallKind.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to McColors.Success
                CallKind.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to McColors.TextMuted
                CallKind.MISSED, CallKind.REJECTED -> Icons.AutoMirrored.Rounded.CallMissed to McColors.Reject
                CallKind.BLOCKED -> Icons.Rounded.Block to McColors.Reject
                CallKind.OTHER -> Icons.Rounded.History to McColors.TextMuted
            }
            ListRow(
                name = c.name ?: c.number.ifEmpty { "Private number" },
                photo = c.photoUri,
                nameColor = if (missed) McColors.Reject else McColors.TextPrimary,
                subtitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (c.name != null) "${c.number} · " else "") +
                                DateUtils.getRelativeTimeSpanString(c.timeMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                            style = MaterialTheme.typography.bodySmall,
                            color = McColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                onCall = if (c.number.isNotBlank()) ({ call(c.number) }) else null,
            )
        }
    }
}

// --- Contacts -------------------------------------------------------------------------------------

@Composable
private fun ContactsTab(directory: PhoneDirectory, version: Int, call: (String) -> Unit, onOpen: (PhoneContact) -> Unit) {
    var allowed by remember { mutableStateOf(directory.canReadContacts()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = directory.canReadContacts()
    }
    if (!allowed) {
        PermissionAsk("Allow contacts to see and call them here.", "Allow contacts") {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
        return
    }
    val contacts by produceState<List<PhoneContact>?>(null, allowed, version) {
        value = withContext(Dispatchers.IO) { directory.loadContacts() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val all = contacts ?: return

    val q = query.trim()
    val digits = q.filter(Char::isDigit)
    val filtered = if (q.isEmpty()) all else all.filter { c ->
        c.name.contains(q, ignoreCase = true) ||
            (digits.length >= 2 && c.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
    }
    val favourites = if (q.isEmpty()) filtered.filter { it.starred } else emptyList()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ${all.size} contacts") },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = McColors.TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = McColors.Accent,
                unfocusedBorderColor = McColors.Outline,
                cursorColor = McColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            EmptyNote(if (all.isEmpty()) "No contacts with phone numbers" else "No matches")
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
            if (favourites.isNotEmpty()) {
                item(key = "fav-h") { Header("Favourites", Icons.Rounded.Star) }
                items(favourites, key = { "f${it.id}" }) { c -> ContactRow(c, { onOpen(c) }, call) }
            }
            var lastLetter: Char? = null
            filtered.forEach { c ->
                val letter = c.name.first().uppercaseChar().takeIf { it.isLetter() } ?: '#'
                if (letter != lastLetter && q.isEmpty()) {
                    lastLetter = letter
                    item(key = "h$letter${c.id}") { Header(letter.toString(), null) }
                }
                item(key = "c${c.id}") { ContactRow(c, { onOpen(c) }, call) }
            }
        }
    }
}

@Composable
private fun ContactRow(c: PhoneContact, onOpen: () -> Unit, call: (String) -> Unit) {
    Column {
        ListRow(
            name = c.name,
            photo = c.photoUri,
            subtitle = {
                Text(
                    if (c.numbers.size == 1) "${c.numbers[0].label} · ${c.numbers[0].number}" else "${c.numbers.size} numbers",
                    style = MaterialTheme.typography.bodySmall,
                    color = McColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            onClick = onOpen,
            onCall = { call(c.numbers.first().number) },
        )
    }
}

// --- Keypad ---------------------------------------------------------------------------------------

@Composable
private fun KeypadTab(initial: String, call: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Text(
                number.ifEmpty { " " },
                style = MaterialTheme.typography.headlineLarge,
                color = McColors.TextPrimary,
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
        Spacer(Modifier.height(20.dp))
        DialPad(onKey = { number = (number + it).take(32) })
        Spacer(Modifier.height(20.dp))
        CircleActionButton(
            icon = Icons.Rounded.Call,
            label = "Call",
            container = McColors.Answer,
            content = McColors.Ink,
            enabled = number.isNotBlank(),
            onClick = { call(number) },
        )
        Spacer(Modifier.height(12.dp))
    }
}

// --- Shared pieces --------------------------------------------------------------------------------

@Composable
private fun ListRow(
    name: String,
    photo: Uri?,
    subtitle: @Composable () -> Unit,
    onCall: (() -> Unit)?,
    onClick: (() -> Unit)? = onCall,
    nameColor: androidx.compose.ui.graphics.Color = McColors.TextPrimary,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallAvatar(name, photo, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle()
        }
        if (onCall != null) {
            IconButton(onClick = onCall) { Icon(Icons.Rounded.Call, "Call", tint = McColors.Accent) }
        }
    }
}

@Composable
private fun SmallAvatar(name: String, photo: Uri?, size: Dp) {
    val bmp by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { uri ->
            withContext(Dispatchers.IO) { MasterCallerApp.instance.contacts.loadPhoto(uri, maxSide = 160)?.asImageBitmap() }
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(McColors.Surface3).border(1.dp, McColors.Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#",
                style = MaterialTheme.typography.titleMedium,
                color = McColors.AccentSoft,
            )
        }
    }
}

@Composable
private fun Header(text: String, icon: ImageVector?) {
    Row(Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = McColors.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = McColors.TextMuted)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = McColors.TextMuted)
    }
}

@Composable
private fun PermissionAsk(text: String, button: String, onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = McColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        SecondaryButton(button, onClick = onAllow)
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
