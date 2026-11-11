package com.kaif.mastercaller.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.R
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun McBackground(
    modifier: Modifier = Modifier,
    wallpaper: Uri? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val photo by produceState<ImageBitmap?>(initialValue = null, wallpaper) {
        value = wallpaper?.let { uri ->
            withContext(Dispatchers.IO) { MasterCallerApp.instance.contacts.loadPhoto(uri)?.asImageBitmap() }
        }
    }
    val bmp = photo
    if (bmp != null) {
        // Caller's contact photo as a full-screen wallpaper, faded towards the bottom so text and buttons stay readable.
        Box(modifier.fillMaxSize().background(McColors.Ink)) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to McColors.Ink.copy(alpha = 0.35f),
                            0.45f to McColors.Ink.copy(alpha = 0.55f),
                            1f to McColors.Ink.copy(alpha = 0.92f),
                        ),
                    ),
            )
            content()
        }
        return
    }
    Box(
        modifier
            .fillMaxSize()
            .background(McColors.Ink)
            .drawBehind {
                // A single, very faint champagne sheen from the top corner: depth without decoration.
                val r = size.maxDimension * 0.75f
                val origin = androidx.compose.ui.geometry.Offset(size.width * 0.85f, -size.height * 0.05f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(McColors.Accent.copy(alpha = 0.07f), Color.Transparent), origin, r),
                    radius = r,
                    center = origin,
                )
            },
        content = content,
    )
}

@Composable
fun McCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val border = if (highlight) McColors.Accent.copy(alpha = 0.35f) else McColors.Outline
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(McColors.Surface)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        content = content,
    )
}

/** KAVÉ app-icon tile: the original champagne monogram on obsidian, with a hairline champagne edge. */
@Composable
fun AppLogo(size: Dp = 56.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(McColors.Surface2, McColors.Ink)))
            .border(1.dp, McColors.Accent.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.kave_monogram),
            contentDescription = "KAVÉ",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

/** The KAVÉ wordmark from the brand artwork, optionally with the product line underneath. */
@Composable
fun KaveWordmark(
    width: Dp,
    modifier: Modifier = Modifier,
    product: String? = "CALL",
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.kave_wordmark),
            contentDescription = "KAVÉ",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(width),
        )
        if (product != null) {
            Spacer(Modifier.height(width * 0.06f))
            Text(
                product,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = (width.value / 26f).sp),
                color = McColors.AccentSoft,
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = McColors.TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = McColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = McColors.TextSecondary)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = McColors.TextMuted,
        modifier = modifier.padding(start = 4.dp, top = 28.dp, bottom = 12.dp),
    )
}

@Composable
fun StatusChip(text: String, ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> McColors.Success
        false -> McColors.Warning
        null -> McColors.TextMuted
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = McColors.Accent, size: Dp = 42.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = McColors.Accent, contentColor = McColors.Ink),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, McColors.Accent.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = McColors.Accent),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = McColors.Ink,
                checkedTrackColor = McColors.Accent,
                uncheckedThumbColor = McColors.TextSecondary,
                uncheckedTrackColor = McColors.Surface3,
                uncheckedBorderColor = McColors.Outline,
            ),
        )
    }
}

/** Contact photo if available, otherwise initials; optional animated "ringing" pulse. */
@Composable
fun CallerAvatar(name: String, photoUri: Uri?, size: Dp, pulsing: Boolean = false) {
    val photo by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = photoUri?.let { uri ->
            withContext(Dispatchers.IO) { MasterCallerApp.instance.contacts.loadPhoto(uri)?.asImageBitmap() }
        }
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress",
    )
    Box(Modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val base = this.size.minDimension / 3.2f
                        for (i in 0 until 3) {
                            val p = (progress + i / 3f) % 1f
                            drawCircle(
                                color = (if (i % 2 == 0) McColors.Accent else McColors.AccentSoft)
                                    .copy(alpha = (1f - p) * 0.35f),
                                radius = base + p * (this.size.minDimension / 2f - base),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    },
            )
        }
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = McColors.Surface3,
            border = BorderStroke(2.dp, McColors.AccentSoft.copy(alpha = 0.5f)),
        ) {
            val bmp = photo
            if (bmp != null) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                val initials = initialsOf(name)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(McColors.AccentDeep, McColors.AccentSoft.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (initials.isNotEmpty()) {
                        Text(
                            initials,
                            color = McColors.TextPrimary,
                            fontSize = (size.value * 0.36f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Icon(Icons.Rounded.Person, null, tint = McColors.TextPrimary, modifier = Modifier.size(size * 0.5f))
                    }
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(' ', '-', '.')
        .filter { it.firstOrNull()?.isLetter() == true }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }

/** Big round call control (Pick Up / Reject / End). */
@Composable
fun CircleActionButton(
    icon: ImageVector,
    label: String,
    container: Color,
    onClick: () -> Unit,
    size: Dp = 76.dp,
    content: Color = Color.White,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) container else container.copy(alpha = 0.4f),
            modifier = Modifier.size(size),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(size * 0.42f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = McColors.TextPrimary)
    }
}

/** Toggle tile used for Speaker / Earpiece / Mute / Hold / Keypad. */
@Composable
fun ControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (active) McColors.Accent.copy(alpha = 0.14f) else McColors.Surface2.copy(alpha = 0.9f))
            .border(1.dp, if (active) McColors.Accent.copy(alpha = 0.45f) else McColors.Outline.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (active) McColors.Accent else McColors.Surface3),
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = when {
                !enabled -> McColors.TextMuted
                active -> McColors.Ink
                else -> McColors.TextPrimary
            }
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) McColors.TextPrimary else McColors.TextMuted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Animated listening indicator; [level] is the 0..1 mic level. */
@Composable
fun ListeningOrb(level: Float, active: Boolean, size: Dp = 44.dp, icon: ImageVector) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "breathe",
    )
    val scale = if (active) breathe + level * 0.25f else 1f
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (active) McColors.Accent else McColors.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) McColors.Ink else McColors.TextSecondary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
