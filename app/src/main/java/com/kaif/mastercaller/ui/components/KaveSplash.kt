package com.kaif.mastercaller.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaif.mastercaller.R
import com.kaif.mastercaller.ui.theme.McColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brand splash: obsidian, the KAVÉ monogram and wordmark fading in, one slow champagne light sweep
 * across the monogram, then the tagline. About 1.7 s, then [onFinished].
 */
@Composable
fun KaveSplash(onFinished: () -> Unit) {
    val logo = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.4f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        launch {
            delay(350)
            sweep.animateTo(1.4f, tween(1100, easing = LinearEasing))
        }
        delay(450)
        text.animateTo(1f, tween(600))
        delay(450)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(McColors.Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.kave_monogram),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(118.dp)
                    .graphicsLayer {
                        alpha = logo.value
                        scaleX = 0.94f + 0.06f * logo.value
                        scaleY = 0.94f + 0.06f * logo.value
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // Soft diagonal highlight that travels across the gold, masked to the logo shape.
                        val x = size.width * sweep.value
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                start = androidx.compose.ui.geometry.Offset(x - size.width * 0.25f, 0f),
                                end = androidx.compose.ui.geometry.Offset(x + size.width * 0.25f, size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
            )
            Spacer(Modifier.height(34.dp))
            Image(
                painter = painterResource(R.drawable.kave_wordmark),
                contentDescription = "KAVÉ",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(196.dp).alpha(logo.value),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "CALL",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 9.sp, fontSize = 13.sp),
                color = McColors.AccentSoft,
                modifier = Modifier.alpha(text.value),
            )
            Spacer(Modifier.height(48.dp))
            Text(
                "BEYOND THE ORDINARY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 4.sp),
                color = McColors.TextMuted,
                modifier = Modifier.alpha(text.value),
            )
        }
    }
}
