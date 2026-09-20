package com.example.playbox.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.player.Presentation
import com.example.playbox.ui.theme.Playbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab { HOME, SHORTS, PLAYLISTS, LIBRARY }

/** Which derived shelf a "see all" page is showing. */
enum class ListKind { HISTORY, LIKED, ALL, CONTINUE, MOST_PLAYED }

sealed interface Route {
    data object Tabs : Route
    data class VideoList(val title: String, val kind: ListKind) : Route
    data class PlaylistDetail(val id: String) : Route
}

val BOTTOM_BAR_HEIGHT = 56.dp
val MINI_PLAYER_HEIGHT = 58.dp

@Composable
fun RootScreen(
    library: LibraryStore,
    playback: PlaybackController,
    hasMediaPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.HOME) }
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    var sheetVideo by remember { mutableStateOf<Video?>(null) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) library.scan() else library.scan()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            withContext(Dispatchers.IO) {
                uris.forEach { uri -> copyIntoLibrary(context, library, uri) }
            }
            library.scan()
            importing = false
        }
    }

    val chrome = BOTTOM_BAR_HEIGHT + if (playback.presentation == Presentation.MINI) MINI_PLAYER_HEIGHT else 0.dp

    // Don't let the screen time out while anything is actually playing. The
    // Shorts tab autoplays whatever is on screen, so it counts too.
    KeepScreenOn(enabled = playback.isPlaying || tab == Tab.SHORTS)

    BackHandler(enabled = playback.presentation == Presentation.FULL) {
        if (playback.isFullscreen) playback.isFullscreen = false else playback.minimize()
    }
    BackHandler(enabled = playback.presentation != Presentation.FULL && route !is Route.Tabs) { route = Route.Tabs }

    Box(
        Modifier
            .fillMaxSize()
            .background(Playbox.Background),
    ) {
        when (val current = route) {
            is Route.Tabs -> when (tab) {
                Tab.HOME -> HomeScreen(
                    library = library,
                    playback = playback,
                    bottomChrome = chrome,
                    hasMediaPermission = hasMediaPermission,
                    onRequestPermission = onRequestPermission,
                    onMore = { sheetVideo = it },
                    onImport = { importLauncher.launch(arrayOf("video/*")) },
                )
                Tab.SHORTS -> ShortsScreen(
                    library = library,
                    playback = playback,
                    bottomChrome = chrome,
                    onMore = { sheetVideo = it },
                )
                Tab.PLAYLISTS -> PlaylistsScreen(
                    library = library,
                    playback = playback,
                    bottomChrome = chrome,
                    onOpen = { route = Route.PlaylistDetail(it) },
                )
                Tab.LIBRARY -> LibraryScreen(
                    library = library,
                    playback = playback,
                    bottomChrome = chrome,
                    onMore = { sheetVideo = it },
                    onSeeAll = { title, kind -> route = Route.VideoList(title, kind) },
                )
            }

            is Route.VideoList -> VideoListScreen(
                title = current.title,
                videos = library.shelf(current.kind),
                library = library,
                playback = playback,
                bottomChrome = chrome,
                allowClear = current.kind == ListKind.HISTORY,
                onMore = { sheetVideo = it },
                onBack = { route = Route.Tabs },
            )

            is Route.PlaylistDetail -> PlaylistDetailScreen(
                playlistId = current.id,
                library = library,
                playback = playback,
                bottomChrome = chrome,
                onMore = { sheetVideo = it },
                onBack = { route = Route.Tabs },
            )
        }

        // Mini player + tab bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = playback.presentation == Presentation.MINI,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                MiniPlayer(library = library, playback = playback)
            }
            BottomBar(
                selected = tab,
                onSelect = {
                    tab = it
                    route = Route.Tabs
                    if (it == Tab.SHORTS) playback.suspendForShorts()
                },
                onCreate = { importLauncher.launch(arrayOf("video/*")) },
            )
        }

        if (playback.presentation == Presentation.FULL) {
            PlayerScreen(
                library = library,
                playback = playback,
                onMore = { sheetVideo = it },
            )
        }

        if (importing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Playbox.Accent)
                    Spacer(Modifier.size(12.dp))
                    Text("Importing…", color = Playbox.PrimaryText, fontSize = 13.sp)
                }
            }
        }
    }

    sheetVideo?.let { video ->
        com.example.playbox.ui.components.VideoActionSheet(
            video = video,
            library = library,
            playback = playback,
            onDismiss = { sheetVideo = null },
        )
    }
}

/**
 * Holds the screen awake while [enabled]. Uses the View flag rather than a
 * wake lock: it needs no permission and Android drops it automatically if the
 * app is backgrounded or killed, so it cannot leak and flatten the battery.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

fun LibraryStore.shelf(kind: ListKind): List<Video> = when (kind) {
    ListKind.HISTORY -> historyVideos
    ListKind.LIKED -> liked
    ListKind.ALL -> recent
    ListKind.CONTINUE -> continueWatching
    ListKind.MOST_PLAYED -> mostPlayed
}

@Composable
private fun BottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Playbox.Surface)
            .navigationBarsPadding()
            .height(BOTTOM_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarItem(Icons.Default.Home, "Home", selected == Tab.HOME, Modifier.weight(1f)) { onSelect(Tab.HOME) }
        BarItem(Icons.Default.PlayCircleFilled, "Shorts", selected == Tab.SHORTS, Modifier.weight(1f)) { onSelect(Tab.SHORTS) }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.AddCircle,
                contentDescription = "Import videos",
                tint = Playbox.PrimaryText,
                modifier = Modifier.size(32.dp).clickable(onClick = onCreate),
            )
        }

        BarItem(Icons.Default.VideoLibrary, "Playlists", selected == Tab.PLAYLISTS, Modifier.weight(1f)) { onSelect(Tab.PLAYLISTS) }
        BarItem(Icons.Default.Person, "You", selected == Tab.LIBRARY, Modifier.weight(1f)) { onSelect(Tab.LIBRARY) }
    }
}

@Composable
private fun BarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Playbox.PrimaryText else Playbox.SecondaryText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(2.dp))
        Text(
            label,
            color = if (selected) Playbox.PrimaryText else Playbox.SecondaryText,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Copies a picked document into Playbox's own folder so it plays without the picker. */
private fun copyIntoLibrary(
    context: android.content.Context,
    library: LibraryStore,
    uri: Uri,
) {
    runCatching {
        val name = queryDisplayName(context, uri) ?: "Video ${System.currentTimeMillis()}.mp4"
        var target = File(library.importDir, name)
        var counter = 2
        while (target.exists()) {
            val stem = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "mp4")
            target = File(library.importDir, "$stem $counter.$ext")
            counter++
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && c.moveToFirst()) c.getString(index) else null
        }
    }.getOrNull()
