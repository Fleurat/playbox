package com.example.playbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playbox.data.Format
import com.example.playbox.data.Video
import com.example.playbox.ui.theme.Playbox

/** The big home-feed card: wide thumbnail, avatar, title, meta line, overflow menu. */
@Composable
fun VideoCard(
    video: Video,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        VideoThumbnail(video = video, corner = 0.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(video)

            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = Playbox.PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    "${video.bucket} · ${Format.subtitle(video)}",
                    color = Playbox.SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Playbox.PrimaryText,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore)
                    .padding(4.dp),
            )
        }
    }
}

/** Compact horizontal row used in Library, search results and the queue. */
@Composable
fun VideoRow(
    video: Video,
    onPlay: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(video = video, modifier = Modifier.width(148.dp), corner = 8.dp)

        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = Playbox.PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                Format.subtitle(video),
                color = Playbox.SecondaryText,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }

        if (onMore != null) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Playbox.SecondaryText,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore)
                    .padding(4.dp),
            )
        }
    }
}

/** Poster tile for the horizontal shelves. */
@Composable
fun VideoTile(
    video: Video,
    onPlay: () -> Unit,
    width: Dp = 132.dp,
    aspect: Float = 9f / 16f,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onPlay),
    ) {
        VideoThumbnail(video = video, aspect = aspect, corner = 10.dp)
        Spacer(Modifier.size(6.dp))
        Text(
            video.title,
            color = Playbox.PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Playbox.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (trailing != null && onTrailing != null) {
            Text(
                trailing,
                color = Playbox.SecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onTrailing).padding(4.dp),
            )
        }
    }
}

@Composable
fun PillButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (active) androidx.compose.ui.graphics.Color.White else Playbox.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(contentAlignment = Alignment.Center) { leading() }
        }
        Text(
            label,
            color = if (active) androidx.compose.ui.graphics.Color.Black else Playbox.PrimaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
