package com.kaif.mastercaller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.ui.theme.McColors

/**
 * Every language the phone's speech engine can speak. Downloaded ones are ready; others show a
 * download hint (the engine downloads them, KAVÉ can't do it itself).
 */
@Composable
fun LanguagePickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val app = MasterCallerApp.instance
    val languages by app.announcer.languages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = languages.filter {
        q.isEmpty() || it.englishName.contains(q, true) || it.nativeName.contains(q, true) || it.tag.contains(q, true)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxSize(0.86f)
                .clip(shape)
                .background(McColors.Surface)
                .border(1.dp, McColors.Outline, shape)
                .padding(18.dp),
        ) {
            Text("More languages", style = MaterialTheme.typography.headlineMedium, color = McColors.TextPrimary)
            Text(
                "${languages.size} languages your phone can speak. Not downloaded? Pick it, then tap Download on the voice screen.",
                style = MaterialTheme.typography.bodySmall,
                color = McColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search, e.g. Tamil, Arabic, Français") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = McColors.TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = McColors.Accent,
                    unfocusedBorderColor = McColors.Outline,
                    cursorColor = McColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (languages.isEmpty()) {
                Text("Loading languages…", color = McColors.TextMuted, modifier = Modifier.padding(12.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.tag }) { lang ->
                    val selected = lang.tag.equals(current, ignoreCase = true)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) McColors.AccentDark else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(lang.tag) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lang.englishName, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!lang.nativeName.equals(lang.englishName, true)) {
                                Text(lang.nativeName, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            selected -> Icon(Icons.Rounded.Check, "Selected", tint = McColors.Accent)
                            lang.installed -> Text("Ready", style = MaterialTheme.typography.labelMedium, color = McColors.Success)
                            else -> Icon(Icons.Rounded.Download, "Needs download", tint = McColors.TextMuted)
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = McColors.Accent)
            }
        }
    }
}
