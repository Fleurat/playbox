package com.example.playbox.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.playbox.data.Format
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.player.RepeatMode
import com.example.playbox.ui.components.Avatar
import com.example.playbox.ui.components.PillButton
import com.example.playbox.ui.components.Scrubber
import com.example.playbox.ui.components.ThinProgress
import com.example.playbox.ui.components.VideoThumbnail
import com.example.playbox.ui.components.VideoRow
import com.example.playbox.ui.theme.Playbox
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Transient chrome state, kept in one place so the video surface can be reused
 *  at two very different sizes without a twelve-parameter signature. */
private class PlayerUiState {
    var controlsVisible by mutableStateOf(true)
    var scrubMs by mutableLongStateOf(-1L)
    var seekFlash by mutableIntStateOf(0) // -1 back, +1 forward, 0 none
    var boosting by mutableStateOf(false)
    var showQueue by mutableStateOf(false)
    var speedMenu by mutableStateOf(false)
    var dismissDrag by mutableFloatStateOf(0f)
    var surfaceWidth by mutableIntStateOf(1)
    var swipeFlash by mutableStateOf<String?>(null)
    /** The fullscreen "pick something else" panel. */
    var showPicker by mutableStateOf(false)
}

/** How far a vertical drag must travel in fullscreen to count as a swipe. */
private const val SWIPE_THRESHOLD = 120f

/**
 * The full player.
 *
 * tap → controls · double tap sides → ±10s · hold → 2× · drag sideways → scrub
 * drag down → mini player · drag up → queue · pinch → fit ⇄ fill
 *
 * The fullscreen button rotates to landscape and hides the system bars; back
 * (or the button again) returns to the portrait layout.
 */
@Composable
fun PlayerScreen(
    library: LibraryStore,
    playback: PlaybackController,
    onMore: (Video) -> Unit,
) {
    val current = playback.current ?: return
    val video = library.byId(current.id) ?: current
    val ui = remember { PlayerUiState() }

    ImmersiveMode(active = playback.isFullscreen)

    // Auto-hide the controls while playing.
    LaunchedEffect(ui.controlsVisible, playback.isPlaying, playback.isScrubbing) {
        if (ui.controlsVisible && playback.isPlaying && !playback.isScrubbing) {
            delay(3500)
            ui.controlsVisible = false
        }
    }

    LaunchedEffect(ui.seekFlash) {
        if (ui.seekFlash != 0) {
            delay(650)
            ui.seekFlash = 0
        }
    }

    LaunchedEffect(ui.swipeFlash) {
        if (ui.swipeFlash != null) {
            delay(800)
            ui.swipeFlash = null
        }
    }

    LaunchedEffect(playback.current?.id) {
        ui.controlsVisible = true
        ui.scrubMs = -1L
    }

    if (playback.isFullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(
                modifier = Modifier.fillMaxSize(),
                playback = playback,
                video = video,
                ui = ui,
                fullscreen = true,
                onMore = { onMore(video) },
            )

            if (ui.showPicker) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            detectTapGestures { ui.showPicker = false }
                        },
                )
            }

            AnimatedVisibility(
                visible = ui.showPicker,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                UpNextPanel(
                    library = library,
                    playback = playback,
                    onDismiss = { ui.showPicker = false },
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Playbox.Background)) {
            VideoSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f),
                playback = playback,
                video = video,
                ui = ui,
                fullscreen = false,
                onMore = { onMore(video) },
            )
            Details(library = library, playback = playback, video = video, ui = ui, onMore = onMore)
        }
    }

    if (ui.showQueue) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { ui.showQueue = false },
            sheetState = sheetState,
            containerColor = Playbox.Surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Queue (${playback.upNext.size} up next)",
                    color = Playbox.PrimaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(playback.upNext, key = { it.id }) { item ->
                        VideoRow(
                            video = item,
                            onPlay = {
                                playback.jumpTo(item)
                                ui.showQueue = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- fullscreen

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Locks the activity to landscape and hides the status and navigation bars
 * while [active]. Everything is put back when the player closes, so leaving
 * mid-fullscreen cannot strand the app sideways with no system bars.
 */
@Composable
private fun ImmersiveMode(active: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(active) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null

        if (activity != null && controller != null) {
            if (active) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            val a = context.findActivity()
            val w = a?.window
            if (a != null && w != null) {
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(w, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

// ------------------------------------------------------------- video surface

@Composable
private fun VideoSurface(
    modifier: Modifier,
    playback: PlaybackController,
    video: Video,
    ui: PlayerUiState,
    fullscreen: Boolean,
    onMore: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { ui.surfaceWidth = it.width.coerceAtLeast(1) }
            .pointerInput(video.id) {
                detectTapGestures(
                    onPress = {
                        // Released inside 400ms → a tap. Otherwise it is a hold.
                        val heldDown = withTimeoutOrNull(400) { tryAwaitRelease() } == null
                        if (heldDown) {
                            ui.boosting = true
                            playback.changeSpeed(2f)
                            tryAwaitRelease()
                            playback.changeSpeed(1f)
                            ui.boosting = false
                        }
                    },
                    onTap = { ui.controlsVisible = !ui.controlsVisible },
                    onDoubleTap = { offset ->
                        val third = ui.surfaceWidth / 3f
                        when {
                            offset.x < third -> {
                                playback.skip(-10_000)
                                ui.seekFlash = -1
                            }
                            offset.x > ui.surfaceWidth - third -> {
                                playback.skip(10_000)
                                ui.seekFlash = 1
                            }
                            else -> {
                                playback.playPause()
                                ui.controlsVisible = true
                            }
                        }
                    },
                )
            }
            .pointerInput(video.id, fullscreen) {
                var horizontal = false
                var anchorMs = 0L
                detectDragGestures(
                    onDragStart = {
                        horizontal = false
                        anchorMs = playback.positionMs
                    },
                    onDragEnd = {
                        if (horizontal && ui.scrubMs >= 0) {
                            playback.isScrubbing = false
                            playback.seekTo(ui.scrubMs)
                            ui.scrubMs = -1L
                        } else if (fullscreen) {
                            // Same vertical grammar as the Shorts feed: up for the
                            // next video, down for the one you came from. Shuffle
                            // makes "next" a random pick, exactly as it does there.
                            when {
                                ui.dismissDrag < -SWIPE_THRESHOLD -> {
                                    // Reveal the list rather than jumping blind.
                                    ui.showPicker = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                ui.dismissDrag > SWIPE_THRESHOLD -> {
                                    playback.previousTrack()
                                    ui.swipeFlash = "Previous video"
                                    ui.controlsVisible = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        } else if (ui.dismissDrag > 140f) {
                            playback.minimize()
                        } else if (ui.dismissDrag < -100f) {
                            ui.showQueue = true
                        }
                        ui.dismissDrag = 0f
                    },
                    onDragCancel = {
                        playback.isScrubbing = false
                        ui.scrubMs = -1L
                        ui.dismissDrag = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (!horizontal && abs(dragAmount.x) > abs(dragAmount.y) * 1.2f) {
                        horizontal = true
                        playback.isScrubbing = true
                        ui.controlsVisible = true
                        ui.scrubMs = anchorMs
                    }
                    if (horizontal) {
                        val span = maxOf(playback.durationMs * 6 / 10, 30_000L)
                        val delta = (dragAmount.x / ui.surfaceWidth) * span
                        ui.scrubMs = (ui.scrubMs + delta.toLong())
                            .coerceIn(0L, playback.durationMs.coerceAtLeast(1L))
                    } else {
                        ui.dismissDrag += dragAmount.y
                    }
                }
            }
            .pointerInput(video.id) {
                // Two fingers only. detectTransformGestures() also fires on a
                // ONE-finger pan and consumes it; sitting last in this chain it
                // received events first and starved every drag gesture above.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomAccum = 1f
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            zoomAccum *= event.calculateZoom()
                            if (zoomAccum > 1.08f) {
                                playback.fillsScreen = true
                                zoomAccum = 1f
                            } else if (zoomAccum < 0.92f) {
                                playback.fillsScreen = false
                                zoomAccum = 1f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = playback.player
                }
            },
            update = { view ->
                view.player = playback.player
                view.resizeMode = if (playback.fillsScreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (playback.isBuffering && !playback.isPlaying) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        if (ui.seekFlash != 0) {
            Icon(
                if (ui.seekFlash > 0) Icons.Default.Forward10 else Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(if (ui.seekFlash > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 44.dp)
                    .size(44.dp),
            )
        }

        if (ui.boosting) {
            Text(
                "2× speed",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        ui.swipeFlash?.let { label ->
            Text(
                label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (ui.controlsVisible) {
            PlayerControls(
                playback = playback,
                ui = ui,
                fullscreen = fullscreen,
                title = video.title,
                onMore = onMore,
            )
        } else {
            ThinProgress(
                progress = playback.progress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ----------------------------------------------------------------- controls

@Composable
private fun BoxScope.PlayerControls(
    playback: PlaybackController,
    ui: PlayerUiState,
    fullscreen: Boolean,
    title: String,
    onMore: () -> Unit,
) {
    // With the system bars hidden, the cutout is the only thing left to avoid.
    val inset = if (fullscreen) Modifier.displayCutoutPadding() else Modifier

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(inset)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (fullscreen) "Exit fullscreen" else "Minimise",
                tint = Color.White,
                modifier = Modifier.size(26.dp).clickable {
                    if (fullscreen) playback.isFullscreen = false else playback.minimize()
                },
            )

            if (fullscreen) {
                Spacer(Modifier.width(14.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Spacer(Modifier.weight(1f))

            if (fullscreen) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.isShuffling) Playbox.Accent else Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { playback.isShuffling = !playback.isShuffling },
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Default.Casino,
                    contentDescription = "Random video",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            playback.playRandom()
                            ui.swipeFlash = "Random video"
                        },
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = "Up next",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp).clickable { ui.showPicker = true },
                )
                Spacer(Modifier.width(16.dp))
            }

            Box {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "Speed",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp).clickable { ui.speedMenu = true },
                )
                DropdownMenu(expanded = ui.speedMenu, onDismissRequest = { ui.speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem(
                            text = { Text(if (value == 1f) "Normal" else "${value}×") },
                            onClick = {
                                playback.changeSpeed(value)
                                ui.speedMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White,
                modifier = Modifier.size(22.dp).clickable(onClick = onMore),
            )
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportIcon(Icons.Default.SkipPrevious, "Previous", enabled = playback.hasPrevious) {
                playback.previous()
            }
            TransportIcon(
                when {
                    playback.didReachEnd -> Icons.Default.Refresh
                    playback.isPlaying -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                "Play/pause",
                size = 52,
            ) { playback.playPause() }
            TransportIcon(Icons.Default.SkipNext, "Next", enabled = playback.hasNext) {
                playback.next()
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(inset)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.duration(if (ui.scrubMs >= 0) ui.scrubMs else playback.positionMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(" / ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(
                    Format.duration(playback.durationMs),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                if (playback.speed != 1f) {
                    Text(
                        "${playback.speed}×",
                        color = Playbox.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { playback.isFullscreen = !playback.isFullscreen },
                )
            }
            Scrubber(
                progress = playback.progress,
                durationMs = playback.durationMs,
                onBegin = { playback.isScrubbing = true },
                onChange = { ui.scrubMs = it },
                onEnd = {
                    playback.isScrubbing = false
                    playback.seekTo(it)
                    ui.scrubMs = -1L
                },
            )
        }
    }
}

@Composable
private fun TransportIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    size: Int = 32,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = label,
        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier
            .size(size.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

// ------------------------------------------------------------------ details

@Composable
private fun Details(
    library: LibraryStore,
    playback: PlaybackController,
    video: Video,
    ui: PlayerUiState,
    onMore: (Video) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    video.title,
                    color = Playbox.PrimaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Format.plays(video.plays)} · ${Format.duration(video.durationMs)} · ${Format.fileSize(video.sizeBytes)}",
                    color = Playbox.SecondaryText,
                    fontSize = 12.sp,
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(video, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    video.bucket,
                    color = Playbox.PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    PillButton(
                        label = if (video.isLiked) "Liked" else "Like",
                        active = video.isLiked,
                        onClick = { library.toggleLike(video.id) },
                        leading = {
                            Icon(
                                Icons.Default.ThumbUp, null,
                                tint = if (video.isLiked) Color.Black else Playbox.PrimaryText,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
                item {
                    PillButton(
                        label = "Fullscreen",
                        onClick = { playback.isFullscreen = true },
                        leading = {
                            Icon(Icons.Default.Fullscreen, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp))
                        },
                    )
                }
                item {
                    PillButton(
                        label = "Shuffle",
                        active = playback.isShuffling,
                        onClick = { playback.isShuffling = !playback.isShuffling },
                        leading = {
                            Icon(
                                Icons.Default.Shuffle, null,
                                tint = if (playback.isShuffling) Color.Black else Playbox.PrimaryText,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
                item {
                    PillButton(
                        label = if (playback.repeatMode == RepeatMode.OFF) "Repeat" else playback.repeatMode.label,
                        active = playback.repeatMode != RepeatMode.OFF,
                        onClick = { playback.repeatMode = playback.repeatMode.advance() },
                        leading = {
                            Icon(
                                if (playback.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                null,
                                tint = if (playback.repeatMode != RepeatMode.OFF) Color.Black else Playbox.PrimaryText,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
                item {
                    PillButton(
                        label = "Random",
                        onClick = { playback.playRandom() },
                        leading = {
                            Icon(Icons.Default.Casino, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp))
                        },
                    )
                }
                item {
                    PillButton(
                        label = "Queue",
                        onClick = { ui.showQueue = true },
                        leading = {
                            Icon(Icons.Default.QueueMusic, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp))
                        },
                    )
                }
                item {
                    PillButton(
                        label = "Save",
                        onClick = { onMore(video) },
                        leading = {
                            Icon(Icons.Default.PlaylistAdd, null, tint = Playbox.PrimaryText, modifier = Modifier.size(15.dp))
                        },
                    )
                }
            }
        }

        item {
            Text(
                if (playback.isShuffling) "Up next · shuffled" else "Up next",
                color = Playbox.PrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        val queue = playback.upNext
        if (queue.isEmpty()) {
            item {
                Text(
                    "Nothing queued. Tap Random, or play from a shelf to fill this up.",
                    color = Playbox.SecondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        } else {
            items(queue.take(25), key = { it.id }) { item ->
                VideoRow(
                    video = item,
                    onPlay = { playback.jumpTo(item) },
                    onMore = { onMore(item) },
                )
            }
        }
    }
}

// -------------------------------------------------------------- up next panel

/**
 * Slides up over the fullscreen video as a horizontal strip of cards: the queue
 * first, then everything else in the library, so there is always something to
 * pick even with an empty queue. Picking from the queue keeps the queue;
 * picking from the library rebuilds it around the choice.
 *
 * The height wraps its content instead of taking a fraction of the screen —
 * landscape has very little height to spare, and a strip only needs one card's
 * worth, which leaves the top of the video visible behind it.
 */
@Composable
private fun UpNextPanel(
    library: LibraryStore,
    playback: PlaybackController,
    onDismiss: () -> Unit,
) {
    val currentId = playback.current?.id
    val queued = playback.upNext
    val queuedIds = queued.map { it.id }.toSet()
    val others = library.videos.filter { it.id != currentId && it.id !in queuedIds }
    val picks = queued + others

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Playbox.Background.copy(alpha = 0.96f),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .displayCutoutPadding(),
    ) {
        // Handle and header carry the drag-to-dismiss. Keeping it off the strip
        // itself means it can never fight the row's horizontal scrolling.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 14f) onDismiss()
                    }
                },
        ) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Playbox.SecondaryText),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (queued.isEmpty()) "Other videos" else "Up next",
                    color = Playbox.PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))

                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.isShuffling) Playbox.Accent else Playbox.PrimaryText,
                    modifier = Modifier
                        .size(21.dp)
                        .clickable { playback.isShuffling = !playback.isShuffling },
                )
                Spacer(Modifier.width(18.dp))
                Icon(
                    Icons.Default.Casino,
                    contentDescription = "Play something random",
                    tint = Playbox.PrimaryText,
                    modifier = Modifier
                        .size(21.dp)
                        .clickable {
                            playback.playRandom()
                            onDismiss()
                        },
                )
                Spacer(Modifier.width(18.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Playbox.PrimaryText,
                    modifier = Modifier.size(21.dp).clickable(onClick = onDismiss),
                )
            }
        }

        if (picks.isEmpty()) {
            Text(
                "Nothing else in your library yet.",
                color = Playbox.SecondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 18.dp),
            )
        } else {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(picks, key = { it.id }) { item ->
                    UpNextCard(
                        video = item,
                        inQueue = item.id in queuedIds,
                        onPlay = {
                            if (item.id in queuedIds) playback.jumpTo(item)
                            else playback.play(item, picks)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(
    video: Video,
    inQueue: Boolean,
    onPlay: () -> Unit,
) {
    Column(
        Modifier
            .width(172.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay),
    ) {
        VideoThumbnail(video = video, aspect = 16f / 9f, corner = 10.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            video.title,
            color = Playbox.PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (inQueue) "In queue" else video.bucket,
            color = if (inQueue) Playbox.Accent else Playbox.SecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
