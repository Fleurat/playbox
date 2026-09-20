package com.example.playbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.ui.components.EmptyState
import com.example.playbox.ui.components.PillButton
import com.example.playbox.ui.components.VideoRow
import com.example.playbox.ui.components.VideoThumbnail
import com.example.playbox.ui.theme.Playbox

@Composable
fun PlaylistsScreen(
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    onOpen: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().background(Playbox.Background),
        contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
    ) {
        item {
            Text(
                "Playlists",
                color = Playbox.PrimaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { creating = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(148.dp)
                        .height(83.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Playbox.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = Playbox.PrimaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = Playbox.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (library.playlists.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    title = "No playlists",
                    message = "Group videos into playlists, then play or shuffle the whole list.",
                )
            }
        }

        items(library.playlists, key = { it.id }) { playlist ->
            val videos = library.resolve(playlist.videoIds)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(148.dp)) {
                    val first = videos.firstOrNull()
                    if (first != null) {
                        VideoThumbnail(video = first, corner = 8.dp, showDuration = false, showProgress = false)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(83.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Playbox.Surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, tint = Playbox.SecondaryText)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, color = Playbox.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${playlist.videoIds.size} videos",
                        color = Playbox.SecondaryText,
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete playlist",
                    tint = Playbox.SecondaryText,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { library.deletePlaylist(playlist.id) }
                        .padding(4.dp),
                )
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = { TextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    library.createPlaylist(newName)
                    newName = ""
                    creating = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
            containerColor = Playbox.Surface,
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    onMore: (Video) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = library.playlist(playlistId)
    val videos = library.resolve(playlist?.videoIds ?: emptyList())

    Column(Modifier.fillMaxSize().background(Playbox.Background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Playbox.PrimaryText,
                modifier = Modifier.size(24.dp).clickable(onClick = onBack),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                playlist?.name ?: "Playlist",
                color = Playbox.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                label = "Play all",
                active = true,
                onClick = { playback.playAll(videos) },
                leading = { Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(15.dp)) },
            )
            PillButton(
                label = "Shuffle",
                onClick = { playback.playAll(videos, shuffled = true) },
                leading = { Icon(Icons.Default.Shuffle, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp)) },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
        ) {
            if (videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Add,
                        title = "Empty playlist",
                        message = "Add videos with the ⋮ menu on any video.",
                    )
                }
            }
            items(videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    onPlay = { playback.play(video, videos) },
                    onMore = { onMore(video) },
                )
            }
        }
    }
}
