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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.playbox.ui.components.Chip
import com.example.playbox.ui.components.EmptyState
import com.example.playbox.ui.components.SectionHeader
import com.example.playbox.ui.components.VideoCard
import com.example.playbox.ui.components.VideoTile
import com.example.playbox.ui.theme.Playbox
import kotlinx.coroutines.launch

private enum class HomeFilter(val label: String) {
    ALL("All"),
    NEW("New"),
    CONTINUE("Continue"),
    LIKED("Liked"),
    UNWATCHED("Unwatched"),
    LONGEST("Longest"),
    MOST_PLAYED("Most played"),
}

@Composable
fun HomeScreen(
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMore: (Video) -> Unit,
    onImport: () -> Unit,
) {
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val feed: List<Video> = when (filter) {
        HomeFilter.ALL -> library.recent
        HomeFilter.NEW -> library.recent.filter {
            it.dateAddedMs > System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        }
        HomeFilter.CONTINUE -> library.continueWatching
        HomeFilter.LIKED -> library.liked
        HomeFilter.UNWATCHED -> library.unwatched
        HomeFilter.LONGEST -> library.longest
        HomeFilter.MOST_PLAYED -> library.mostPlayed
    }

    if (searching) {
        SearchScreen(
            library = library,
            playback = playback,
            bottomChrome = bottomChrome,
            onClose = { searching = false },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Playbox.Background)) {
        TopBar(library = library, onSearch = { searching = true })

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
        ) {
            item {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(HomeFilter.entries.toList()) { option ->
                        Chip(option.label, filter == option, { filter = option })
                    }
                }
            }

            if (!hasMediaPermission && library.videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.PlayArrow,
                        title = "Let Playbox see your videos",
                        message = "Playbox reads videos already on this phone. Nothing leaves the device.",
                        actionLabel = "Allow access",
                        onAction = onRequestPermission,
                    )
                }
            } else if (library.videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.PlayArrow,
                        title = if (library.isScanning) "Looking for videos…" else "No videos found",
                        message = "Record something, or tap + below to bring in a file from Downloads or Drive.",
                        actionLabel = if (library.isScanning) null else "Import a video",
                        onAction = if (library.isScanning) null else onImport,
                    )
                }
            } else {
                if (filter == HomeFilter.ALL) {
                    val resume = library.continueWatching
                    if (resume.isNotEmpty()) {
                        item { SectionHeader("Continue watching") }
                        item { Shelf(resume, 200.dp, 16f / 9f) { playback.play(it, resume) } }
                    }
                    val shortClips = library.videos.filter { it.qualifiesAsShort }
                    if (shortClips.size >= 2) {
                        item { SectionHeader("Shorts") }
                        item { Shelf(shortClips.take(12), 132.dp, 9f / 16f) { playback.play(it, shortClips) } }
                    }
                }

                items(feed, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        onPlay = { playback.play(video, feed) },
                        onMore = { onMore(video) },
                    )
                }

                if (feed.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = "Nothing here",
                            message = "No videos match \"${filter.label}\".",
                        )
                    }
                }
            }
        }
    }

    // Kick a rescan whenever permission arrives.
    if (hasMediaPermission && !library.hasScanned && !library.isScanning) {
        scope.launch { library.scan() }
    }
}

@Composable
private fun Shelf(
    videos: List<Video>,
    width: Dp,
    aspect: Float,
    onPlay: (Video) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoTile(video = video, onPlay = { onPlay(video) }, width = width, aspect = aspect)
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun TopBar(library: LibraryStore, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Playbox.Background)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 26.dp, height = 19.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Playbox.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("Playbox", color = Playbox.PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)

        Spacer(Modifier.weight(1f))

        if (library.isScanning) {
            Text("Scanning…", color = Playbox.SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
        }

        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = Playbox.PrimaryText,
            modifier = Modifier.size(22.dp).clickable(onClick = onSearch),
        )
    }
}
