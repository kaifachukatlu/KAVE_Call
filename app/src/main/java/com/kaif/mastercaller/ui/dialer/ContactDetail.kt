package com.kaif.mastercaller.ui.dialer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.contacts.PhoneContact
import com.kaif.mastercaller.contacts.PosterStyle
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.poster.CallPoster
import com.kaif.mastercaller.ui.poster.PosterEditor
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contact page, like Google Phone: poster preview, numbers, "Edit contact" (Android's own editor for
 * name / numbers / email…), and a Call wallpaper section (photo, poster style, remove).
 */
@Composable
fun ContactDetail(contact: PhoneContact, onBack: () -> Unit, onCall: (String) -> Unit, onEdited: () -> Unit) {
    val app = MasterCallerApp.instance
    val context = LocalContext.current
    val store = app.wallpapers
    val key = contact.lookupKey
    val entries by store.entries.collectAsStateWithLifecycle()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var editingPoster by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // This contact's KAVÉ wallpaper, else their contact photo.
    val ownWallpaper = remember(entries, key) { key?.let { store.forContact(it) } }
    val wallpaper = ownWallpaper ?: contact.fullPhotoUri ?: contact.photoUri
    val style by produceState(PosterStyle(), key, refresh) {
        value = withContext(Dispatchers.IO) { store.styleFor(if (ownWallpaper != null) key else null) }
    }

    if (editingPoster && wallpaper != null && key != null) {
        PosterEditor(
            wallpaper = wallpaper,
            name = contact.name,
            initial = style,
            onSave = { s ->
                app.appScope.launch(Dispatchers.IO) { store.setStyle(key, s) }
                refresh++
                editingPoster = false
            },
            onClose = { editingPoster = false },
        )
        return
    }
    BackHandler(onBack = onBack)

    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && key != null) syncToContacts(key)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        busy = true
        app.appScope.launch {
            val ok = withContext(Dispatchers.IO) { store.setForContact(key, contact.name, uri) }
            busy = false
            if (!ok) {
                Toast.makeText(context, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (settings.syncWallpaperToContacts) {
                if (app.contacts.canWriteContacts()) syncToContacts(key) else writePermission.launch(Manifest.permission.WRITE_CONTACTS)
            }
            editingPoster = true // straight into the poster editor
        }
    }
    val editorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onEdited() }

    McBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = McColors.TextPrimary) }

            // Poster preview, exactly as it will look when they call.
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(shape)
                    .border(1.dp, McColors.Outline, shape),
            ) {
                if (wallpaper != null) {
                    CallPoster(wallpaper = wallpaper, name = contact.name, style = style)
                } else {
                    McBackground(brandMark = true) {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.displayMedium,
                            color = McColors.TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(contact.name, style = MaterialTheme.typography.headlineLarge, color = McColors.TextPrimary)

            SectionLabel("Phone")
            contact.numbers.forEach { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(McColors.Surface)
                        .border(1.dp, McColors.Outline, RoundedCornerShape(16.dp))
                        .clickable { onCall(n.number) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(n.number, style = MaterialTheme.typography.titleMedium, color = McColors.TextPrimary)
                        Text(n.label, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted)
                    }
                    Icon(Icons.Rounded.Call, "Call", tint = McColors.Accent)
                }
                Spacer(Modifier.height(8.dp))
            }

            ActionRow(Icons.Rounded.Edit, "Edit contact", "Name, numbers, email and more") {
                val intent = Intent(Intent.ACTION_EDIT)
                    .setDataAndType(contact.contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    .putExtra("finishActivityOnSaveCompleted", true)
                try {
                    editorLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No contacts editor found on this phone", Toast.LENGTH_SHORT).show()
                }
            }

            SectionLabel("Call wallpaper")
            if (key == null) {
                Text("This contact can't have a wallpaper.", style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted)
            } else {
                ActionRow(
                    Icons.Rounded.AddPhotoAlternate,
                    if (ownWallpaper != null) "Change photo" else "Choose photo",
                    if (busy) "Saving…" else "Shown full-screen when ${contact.name.substringBefore(' ')} calls",
                ) {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (wallpaper != null) {
                    ActionRow(Icons.Rounded.Palette, "Customize poster", "Depth effect, font and colour") {
                        if (ownWallpaper == null) {
                            Toast.makeText(context, "Choose a photo first to give them their own poster", Toast.LENGTH_SHORT).show()
                        } else {
                            editingPoster = true
                        }
                    }
                }
                if (ownWallpaper != null) {
                    ActionRow(Icons.Rounded.Delete, "Remove KAVÉ wallpaper", "Their contact photo is kept", danger = true) {
                        store.removeForContact(key)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun syncToContacts(key: String) {
    val app = MasterCallerApp.instance
    app.appScope.launch(Dispatchers.IO) {
        app.syncWallpaperToContact(key)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(McColors.Surface)
            .border(1.dp, McColors.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = if (danger) McColors.Reject else McColors.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) McColors.Reject else McColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted)
        }
    }
}
