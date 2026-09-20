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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playbox.data.Format
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.ui.components.EmptyState
import com.example.playbox.ui.components.PillButton
import com.example.playbox.ui.components.SectionHeader
import com.example.playbox.ui.components.VideoRow
import com.example.playbox.ui.components.VideoTile
import com.example.playbox.ui.theme.Playbox

@Composable
fun LibraryScreen(
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    onMore: (Video) -> Unit,
    onSeeAll: (String, ListKind) -> Unit,
) {
    val totalBytes = library.videos.sumOf { it.sizeBytes }

    LazyColumn(
        Modifier.fillMaxSize().background(Playbox.Background),
        contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Playbox.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("You", color = Playbox.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${library.videos.size} videos · ${Format.fileSize(totalBytes)}",
                            color = Playbox.SecondaryText,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        label = "Play all",
                        active = true,
                        onClick = { playback.playAll(library.recent) },
                        leading = { Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(15.dp)) },
                    )
                    PillButton(
                        label = "Shuffle",
                        onClick = { playback.playAll(library.recent, shuffled = true) },
                        leading = { Icon(Icons.Default.Shuffle, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp)) },
                    )
                }
            }
        }

        val history = library.historyVideos
        if (history.isNotEmpty()) {
            item { SectionHeader("History", "See all") { onSeeAll("History", ListKind.HISTORY) } }
            item { Shelf(history) { playback.play(it, history) } }
        }

        val resume = library.continueWatching
        if (resume.isNotEmpty()) {
            item { SectionHeader("Continue watching", "See all") { onSeeAll("Continue watching", ListKind.CONTINUE) } }
            item { Shelf(resume) { playback.play(it, resume) } }
        }

        val liked = library.liked
        if (liked.isNotEmpty()) {
            item { SectionHeader("Liked videos", "See all") { onSeeAll("Liked videos", ListKind.LIKED) } }
            item { Shelf(liked) { playback.play(it, liked) } }
        }

        item {
            SectionHeader("All videos (${library.videos.size})", "See all") {
                onSeeAll("All videos", ListKind.ALL)
            }
        }

        if (library.videos.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Your library is empty",
                    message = "Allow access on the Home tab, or use + to import a file.",
                )
            }
        } else {
            items(library.recent.take(8), key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    onPlay = { playback.play(video, library.recent) },
                    onMore = { onMore(video) },
                )
            }
        }
    }
}

@Composable
private fun Shelf(videos: List<Video>, onPlay: (Video) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(videos.take(14), key = { it.id }) { video ->
            VideoTile(video = video, onPlay = { onPlay(video) }, width = 180.dp, aspect = 16f / 9f)
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** Reusable "see all" page. */
@Composable
fun VideoListScreen(
    title: String,
    videos: List<Video>,
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    allowClear: Boolean,
    onMore: (Video) -> Unit,
    onBack: () -> Unit,
) {
    var confirmingClear by remember { mutableStateOf(false) }

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
            Text(title, color = Playbox.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (videos.isNotEmpty()) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Playbox.PrimaryText,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { playback.playAll(videos, shuffled = true) },
                )
            }
            if (allowClear) {
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Clear history",
                    tint = Playbox.Accent,
                    modifier = Modifier.size(22.dp).clickable { confirmingClear = true },
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
        ) {
            if (videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.PlayArrow,
                        title = "Nothing here yet",
                        message = "Come back once you have watched something.",
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

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("Clear watch history?") },
            text = { Text("Resume positions are reset too. Your video files are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    library.clearHistory()
                    confirmingClear = false
                }) { Text("Clear", color = Playbox.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("Cancel") } },
            containerColor = Playbox.Surface,
        )
    }
}
