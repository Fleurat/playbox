package com.example.playbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playbox.data.LibraryStore
import com.example.playbox.player.PlaybackController
import com.example.playbox.ui.components.EmptyState
import com.example.playbox.ui.components.VideoRow
import com.example.playbox.ui.theme.Playbox

@Composable
fun SearchScreen(
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = library.search(query)

    Column(Modifier.fillMaxSize().background(Playbox.Background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your videos", color = Playbox.SecondaryText, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Playbox.SecondaryText) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Playbox.SurfaceHigh,
                    unfocusedContainerColor = Playbox.SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Playbox.PrimaryText,
                    unfocusedTextColor = Playbox.PrimaryText,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50)),
            )
            Text(
                "Cancel",
                color = Playbox.PrimaryText,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onClose).padding(6.dp),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomChrome + 12.dp),
        ) {
            if (query.isBlank()) {
                val recent = library.historyVideos.take(10)
                if (recent.isNotEmpty()) {
                    item {
                        Text(
                            "Recently played",
                            color = Playbox.PrimaryText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    items(recent, key = { it.id }) { video ->
                        VideoRow(video = video, onPlay = { playback.play(video, recent) })
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No results",
                        message = "Nothing in your library matches \"$query\".",
                    )
                }
            } else {
                items(results, key = { it.id }) { video ->
                    VideoRow(video = video, onPlay = { playback.play(video, results) })
                }
            }
            item { Spacer(Modifier.width(1.dp)) }
        }
    }
}
