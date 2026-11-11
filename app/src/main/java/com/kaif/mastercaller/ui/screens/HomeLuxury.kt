package com.kaif.mastercaller.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.R
import com.kaif.mastercaller.contacts.PhoneContact
import com.kaif.mastercaller.ui.theme.Cormorant
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hairline that fades in and out at the ends: champagne at the centre. */
@Composable
fun ChampagneRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, McColors.Accent.copy(alpha = 0.7f), Color.Transparent))),
    )
}

/** The dashboard's centrepiece: date, a large serif clock, the greeting, all on a softly lit card. */
@Composable
fun HeroCard(nowMillis: Long, greeting: String) {
    val shape = RoundedCornerShape(30.dp)
    val date = SimpleDateFormat("EEEE · d MMMM", Locale.ENGLISH).format(Date(nowMillis)).uppercase()
    val time = SimpleDateFormat("h:mm", Locale.ENGLISH).format(Date(nowMillis))
    val amPm = SimpleDateFormat("a", Locale.ENGLISH).format(Date(nowMillis)).uppercase()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(McColors.Surface2, McColors.Ink)))
            .drawBehind {
                // Warm light falling from the top-left corner.
                drawCircle(
                    Brush.radialGradient(
                        listOf(McColors.Accent.copy(alpha = 0.16f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = size.maxDimension * 0.9f,
                    ),
                    radius = size.maxDimension * 0.9f,
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                )
            }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(McColors.Accent.copy(alpha = 0.55f), McColors.Outline.copy(alpha = 0.3f), McColors.Accent.copy(alpha = 0.35f)),
                    ),
                ),
                shape,
            ),
    ) {
        // Monogram watermark.
        Image(
            painter = painterResource(R.drawable.kave_monogram),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .width(120.dp)
                .alpha(0.07f),
        )
        Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
            Text(
                date,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = McColors.AccentSoft,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = Cormorant,
                        fontWeight = FontWeight.Light,
                        fontSize = 76.sp,
                        lineHeight = 78.sp,
                        letterSpacing = (-1).sp,
                    ),
                    color = McColors.TextPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    amPm,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = McColors.TextMuted,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            ChampagneRule(Modifier.width(140.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "$greeting, Boss",
                style = MaterialTheme.typography.headlineMedium,
                color = McColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "BEYOND THE ORDINARY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = McColors.TextMuted,
            )
        }
    }
}

data class ConciergeItem(val icon: ImageVector, val label: String, val value: String, val ok: Boolean, val onClick: () -> Unit)

/** Three refined status columns in one card, separated by hairlines. */
@Composable
fun ConciergeStrip(items: List<ConciergeItem>) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(McColors.Surface)
            .border(1.dp, McColors.Outline, shape),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 18.dp)
                        .background(McColors.Outline),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 18.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .border(1.dp, McColors.Accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, null, tint = McColors.Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontSize = 10.sp), color = McColors.TextMuted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (item.ok) McColors.Success else McColors.Warning))
                    Spacer(Modifier.width(6.dp))
                    Text(item.value, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Starred contacts as gold-ringed portraits; tap to call. */
@Composable
fun FavouritesStrip(refreshKey: Int, onSeeAll: () -> Unit, onAddFavourites: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val favourites by produceState<List<PhoneContact>?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) {
            com.kaif.mastercaller.contacts.PhoneDirectory(context).loadContacts().filter { it.starred }
        }
    }
    val list = favourites ?: return
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("FAVOURITES", style = MaterialTheme.typography.labelSmall, color = McColors.TextMuted, modifier = Modifier.weight(1f))
        if (list.isNotEmpty()) {
            Text(
                "See all",
                style = MaterialTheme.typography.labelLarge,
                color = McColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(list, key = { it.id }) { c ->
            Column(
                Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { placeCallOrOpen(context, c.numbers.first().number) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.border(1.dp, McColors.Accent.copy(alpha = 0.55f), CircleShape).padding(3.dp)) {
                    Portrait(c.name, c.photoUri, 60.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    c.name.substringBefore(' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = McColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "add") {
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onAddFavourites),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(66.dp).border(1.dp, McColors.Outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, "Add favourite", tint = McColors.AccentSoft)
                }
                Spacer(Modifier.height(6.dp))
                Text(if (list.isEmpty()) "Add" else "More", style = MaterialTheme.typography.labelMedium, color = McColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Portrait(name: String, photo: Uri?, size: Dp) {
    val bmp by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { uri ->
            withContext(Dispatchers.IO) { MasterCallerApp.instance.contacts.loadPhoto(uri, maxSide = 200)?.asImageBitmap() }
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(McColors.Surface3, McColors.AccentDark))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Cormorant),
                color = McColors.AccentSoft,
            )
        }
    }
}

/** Calls straight away when allowed; otherwise opens the keypad with the number filled in. */
@SuppressLint("MissingPermission")
private fun placeCallOrOpen(context: Context, number: String) {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    if (granted) {
        try {
            context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
            return
        } catch (e: Exception) {
            // fall through to the dialer
        }
    }
    context.startActivity(
        android.content.Intent(context, com.kaif.mastercaller.ui.dialer.DialerActivity::class.java)
            .setData(Uri.fromParts("tel", number, null)),
    )
}

/** Icons used by the concierge strip (kept here so HomeScreen stays tidy). */
object ConciergeIcons {
    val phone = Icons.Rounded.Call
    val voice = Icons.Rounded.RecordVoiceOver
    val announcer = Icons.Rounded.GraphicEq
}
