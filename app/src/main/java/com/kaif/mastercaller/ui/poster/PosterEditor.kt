package com.kaif.mastercaller.ui.poster

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaif.mastercaller.contacts.PosterColor
import com.kaif.mastercaller.contacts.PosterFont
import com.kaif.mastercaller.contacts.PosterStyle
import com.kaif.mastercaller.ui.components.PrimaryButton
import com.kaif.mastercaller.ui.components.SettingSwitchRow
import com.kaif.mastercaller.ui.theme.McColors

/** Full-screen live preview of the call poster with font / colour / depth controls (like iOS Contact Posters). */
@Composable
fun PosterEditor(
    wallpaper: Uri,
    name: String,
    initial: PosterStyle,
    onSave: (PosterStyle) -> Unit,
    onClose: () -> Unit,
) {
    var style by remember { mutableStateOf(initial) }
    BackHandler(onBack = onClose)

    CallPoster(wallpaper = wallpaper, name = name, style = style) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSave(style) }) { Text("Done", color = McColors.Accent) }
            }
            Spacer(Modifier.weight(1f))

            val shape = RoundedCornerShape(26.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(McColors.Surface.copy(alpha = 0.88f))
                    .border(1.dp, McColors.Outline.copy(alpha = 0.6f), shape)
                    .padding(16.dp),
            ) {
                SettingSwitchRow(
                    title = "Depth effect",
                    subtitle = "Name behind the person (needs a photo with a person)",
                    checked = style.depth,
                    onCheckedChange = { style = style.copy(depth = it) },
                )
                Spacer(Modifier.height(10.dp))
                Text("Font", style = MaterialTheme.typography.labelMedium, color = McColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PosterFont.entries.forEach { f -> FontChip(f, selected = style.font == f) { style = style.copy(font = f) } }
                }
                Spacer(Modifier.height(14.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = McColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosterColor.entries.forEach { c ->
                        val selected = style.color == c
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c.argb))
                                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else McColors.Outline, CircleShape)
                                .clickable { style = style.copy(color = c) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Save poster", onClick = { onSave(style) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FontChip(font: PosterFont, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val family = when (font) {
        PosterFont.MODERN -> FontFamily.SansSerif
        PosterFont.CLASSIC -> FontFamily.Serif
        PosterFont.ELEGANT -> FontFamily.Cursive
        PosterFont.MONO -> FontFamily.Monospace
    }
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) McColors.Accent else McColors.Surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "Aa  ${font.label}",
            style = TextStyle(
                fontFamily = family,
                fontWeight = if (font == PosterFont.MODERN) FontWeight.Black else FontWeight.Bold,
                fontSize = 15.sp,
                color = if (selected) McColors.Ink else McColors.TextPrimary,
            ),
        )
    }
}
