package com.example.playbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.playbox.data.Format
import com.example.playbox.data.Video
import com.example.playbox.ui.theme.Playbox

/** Poster frame with the duration badge and the watched-progress bar. */
@Composable
fun VideoThumbnail(
    video: Video,
    modifier: Modifier = Modifier,
    aspect: Float = 16f / 9f,
    corner: Dp = 12.dp,
    showDuration: Boolean = true,
    showProgress: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    // A frame ~12% in — far enough past black intros to be recognisable.
    val frameMs = if (video.durationMs > 0) {
        (video.durationMs * 0.12f).toLong().coerceIn(500L, (video.durationMs - 200L).coerceAtLeast(500L))
    } else 1_000L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(corner))
            .background(Playbox.Surface),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(video.uri)
                .videoFrameMillis(frameMs)
                .crossfade(true)
                .build(),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
        )

        if (showDuration && video.durationMs > 0) {
            Text(
                text = Format.duration(video.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        if (showProgress && video.watchProgress > 0.01f) {
            WatchProgressBar(
                progress = video.watchProgress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        overlay()
    }
}
