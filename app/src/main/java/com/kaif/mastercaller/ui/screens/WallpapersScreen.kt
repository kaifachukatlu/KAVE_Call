package com.kaif.mastercaller.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.contacts.PosterStyle
import com.kaif.mastercaller.contacts.WallpaperStore
import com.kaif.mastercaller.ui.poster.PosterEditor
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.PrimaryButton
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A wallpaper open in the poster editor. */
private data class PosterEdit(val styleKey: String, val name: String, val uri: Uri, val style: PosterStyle)

/** What the photo picker result should be applied to. */
private sealed interface PhotoTarget {
    data object Default : PhotoTarget
    data class Contact(val lookupKey: String, val name: String) : PhotoTarget
}

@Composable
fun WallpapersScreen(onBack: () -> Unit) {
    val app = MasterCallerApp.instance
    val context = LocalContext.current
    val store = app.wallpapers
    val entries by store.entries.collectAsStateWithLifecycle()
    val defaultWallpaper by store.defaultWallpaper.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<PhotoTarget?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PosterEdit?>(null) }

    editing?.let { e ->
        PosterEditor(
            wallpaper = e.uri,
            name = e.name,
            initial = e.style,
            onSave = { style ->
                app.appScope.launch(Dispatchers.IO) { store.setStyle(e.styleKey, style) }
                editing = null
                Toast.makeText(context, "Poster saved", Toast.LENGTH_SHORT).show()
            },
            onClose = { editing = null },
        )
        return
    }
    val editContact = { key: String, name: String, uri: Uri ->
        editing = PosterEdit(key, name, uri, store.styleFor(key))
    }
    val editDefault = { uri: Uri ->
        editing = PosterEdit(WallpaperStore.DEFAULT_STYLE_KEY, "Caller Name", uri, store.styleFor(null))
    }

    // Android photo picker: no storage permission needed; the chosen image is copied into private app storage.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val t = target
        target = null
        if (uri == null || t == null) return@rememberLauncherForActivityResult
        busy = true
        app.appScope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (t) {
                    PhotoTarget.Default -> store.setDefault(uri)
                    is PhotoTarget.Contact -> store.setForContact(t.lookupKey, t.name, uri)
                }
            }
            busy = false
            if (!ok) {
                Toast.makeText(context, "Couldn't use that photo, try another", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Go straight into the poster editor, like iOS.
            when (t) {
                PhotoTarget.Default -> store.default()?.let { editDefault(it) }
                is PhotoTarget.Contact -> store.forContact(t.lookupKey)?.let { editContact(t.lookupKey, t.name, it) }
            }
        }
    }
    val pickImage = { t: PhotoTarget ->
        target = t
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contactUri ->
        if (contactUri == null) return@rememberLauncherForActivityResult
        val picked = app.contacts.pickedContact(contactUri)
        if (picked == null) {
            Toast.makeText(context, "Couldn't read that contact", Toast.LENGTH_SHORT).show()
        } else {
            pickImage(PhotoTarget.Contact(picked.first, picked.second))
        }
    }

    McBackground {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader("Call wallpapers", "Photos shown full-screen when someone calls", onBack)
                McCard {
                    Text(
                        "Photos you set here are stored only inside Master Caller AI. Your Contacts and Google Phone " +
                            "aren't changed.\n\nOn a call the app uses, in order: the photo you set here for that " +
                            "person → their photo in Contacts → your default wallpaper below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextSecondary,
                    )
                }
            }

            item {
                SectionLabel("Default wallpaper (all other calls)")
                McCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(defaultWallpaper)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (defaultWallpaper != null) "Default wallpaper set" else "No default wallpaper",
                                style = MaterialTheme.typography.titleSmall,
                                color = McColors.TextPrimary,
                            )
                            Text(
                                "Used for unknown numbers and contacts without a photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = McColors.TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton(
                            if (defaultWallpaper != null) "Change" else "Choose photo",
                            onClick = { pickImage(PhotoTarget.Default) },
                            enabled = !busy,
                        )
                        defaultWallpaper?.let { uri ->
                            TextButton(onClick = { editDefault(uri) }) { Text("Customize", color = McColors.Lavender) }
                            TextButton(onClick = { store.removeDefault() }) { Text("Remove", color = McColors.Reject) }
                        }
                    }
                }
            }

            item {
                SectionLabel("Per-contact wallpapers")
                PrimaryButton(
                    text = if (busy) "Saving…" else "Add wallpaper for a contact",
                    icon = Icons.Rounded.AddPhotoAlternate,
                    onClick = { contactPicker.launch(null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "None yet. Contacts' own photos from your Contacts app are used automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextMuted,
                    )
                }
            }

            items(entries, key = { it.lookupKey }) { entry ->
                McCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(entry.uri)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.contactName, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                            Row {
                                TextButton(onClick = { editContact(entry.lookupKey, entry.contactName, entry.uri) }) {
                                    Text("Customize", color = McColors.Lavender)
                                }
                                TextButton(
                                    onClick = { pickImage(PhotoTarget.Contact(entry.lookupKey, entry.contactName)) },
                                    enabled = !busy,
                                ) { Text("Photo", color = McColors.Accent) }
                                TextButton(onClick = { store.removeForContact(entry.lookupKey) }) {
                                    Text("Remove", color = McColors.Reject)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Thumb(uri: Uri?) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = uri?.let { u ->
            withContext(Dispatchers.IO) { MasterCallerApp.instance.contacts.loadPhoto(u, maxSide = 300)?.asImageBitmap() }
        }
    }
    Box(
        Modifier
            .size(width = 54.dp, height = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(McColors.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = McColors.TextMuted)
        }
    }
}
