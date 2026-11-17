package com.metrolist.music.arc.vinyl

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

private const val BACKDROP_PX = 48

/**
 * The cover's colours melted across the screen. Starting from a tiny copy means the blur has little
 * work to do, and older Android versions that can't blur still get a soft enlarged image.
 */
@Composable
internal fun VinylBackdrop(art: ImageBitmap?, modifier: Modifier = Modifier) {
    if (com.metrolist.music.arc.rememberVisualsStill()) return
    Crossfade(targetState = art, label = "vinylBackdrop", modifier = modifier) { image ->
        if (image == null) return@Crossfade
        val small =
            remember(image) {
                Bitmap.createScaledBitmap(image.asAndroidBitmap(), BACKDROP_PX, BACKDROP_PX, true).asImageBitmap()
            }
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = small,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
    }
}
