package com.kaif.mastercaller.ui.poster

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.contacts.PosterFont
import com.kaif.mastercaller.contacts.PosterStyle
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loaded poster images: the photo, and (if depth is on and a person was found) the cut-out person. */
private data class PosterImages(val photo: Bitmap?, val cutout: Bitmap?, val done: Boolean)

/**
 * iPhone-style contact poster:
 *   layer 1  photo
 *   layer 2  big caller name
 *   layer 3  the person cut out of the photo (so they stand in front of their name)
 *   layer 4  bottom fade + [content] (call controls)
 * If the photo has no clear person, or depth is off, the name is simply drawn over the photo.
 */
@Composable
fun CallPoster(
    wallpaper: Uri,
    name: String,
    style: PosterStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val images by produceState(PosterImages(null, null, false), wallpaper, style.depth) {
        val photo = withContext(Dispatchers.IO) {
            // Full screen resolution: no upscaling, no blur.
            MasterCallerApp.instance.contacts.loadPhoto(
                wallpaper,
                maxSide = MasterCallerApp.instance.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) },
            )
        }
        value = PosterImages(photo, null, done = photo == null || !style.depth)
        if (photo != null && style.depth) {
            val cut = withContext(Dispatchers.Default) { SubjectCutout.cutout(wallpaper.toString(), photo) }
            value = PosterImages(photo, cut, done = true)
        }
    }

    Box(modifier.fillMaxSize().background(McColors.Ink)) {
        images.photo?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }

        // Name appears once we know whether there is a person to put in front of it (avoids a "pop").
        AnimatedVisibility(visible = images.done, enter = fadeIn(tween(350))) {
            PosterName(name, style)
        }

        images.cutout?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to McColors.Ink.copy(alpha = 0.92f),
                    ),
                ),
        )
        content()
    }
}

@Composable
private fun PosterName(name: String, style: PosterStyle) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // First name / last name stacked like an iPhone poster; size to fit the longest line.
        val lines = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.let {
            if (it.size <= 2) it else listOf(it.first(), it.drop(1).joinToString(" "))
        }.ifEmpty { listOf(name) }
        val longest = lines.maxOf { it.length }.coerceAtLeast(3)
        val widthFactor = when (style.font) {
            PosterFont.MONO -> 0.66f
            PosterFont.ELEGANT -> 0.52f
            else -> 0.6f
        }
        val sizeSp = (maxWidth.value * 0.92f / (widthFactor * longest)).coerceIn(40f, 112f)
        val family = when (style.font) {
            PosterFont.MODERN -> FontFamily.SansSerif
            PosterFont.CLASSIC -> FontFamily.Serif
            PosterFont.ELEGANT -> FontFamily.Cursive
            PosterFont.MONO -> FontFamily.Monospace
        }
        val weight = if (style.font == PosterFont.MODERN) FontWeight.Black else FontWeight.Bold

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = maxHeight * 0.13f)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEach { line ->
                Text(
                    line,
                    style = TextStyle(
                        fontFamily = family,
                        fontWeight = weight,
                        fontSize = sizeSp.sp,
                        lineHeight = (sizeSp * 0.98f).sp,
                        letterSpacing = if (style.font == PosterFont.MODERN) (-1.5).sp else 0.sp,
                        color = Color(style.color.argb),
                        textAlign = TextAlign.Center,
                        shadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 18f),
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
