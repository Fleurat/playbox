package com.example.playbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.playbox.data.LibraryStore
import com.example.playbox.player.PlaybackController
import com.example.playbox.ui.components.ThinProgress
import com.example.playbox.ui.theme.Playbox

/** The docked bar that appears when the full player is swiped down. */
@Composable
fun MiniPlayer(library: LibraryStore, playback: PlaybackController) {
    val current = playback.current ?: return
    val video = library.byId(current.id) ?: current

    Column(
        Modifier
            .fillMaxWidth()
            .background(Playbox.Surface)
            .clickable { playback.expand() },
    ) {
        ThinProgress(progress = playback.progress)

        Row(
            Modifier
                .fillMaxWidth()
                .height(MINI_PLAYER_HEIGHT)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        player = playback.player
                    }
                },
                update = { it.player = playback.player },
                modifier = Modifier
                    .width(84.dp)
                    .height(47.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = Playbox.PrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    video.bucket,
                    color = Playbox.SecondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }

            Icon(
                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/pause",
                tint = Playbox.PrimaryText,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { playback.playPause() }
                    .padding(6.dp),
            )
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = if (playback.hasNext) Playbox.PrimaryText else Playbox.SecondaryText,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(enabled = playback.hasNext) { playback.next() }
                    .padding(6.dp),
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Playbox.PrimaryText,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { playback.close() }
                    .padding(7.dp),
            )
        }
    }
}
