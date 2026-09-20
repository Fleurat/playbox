package com.example.playbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.playbox.data.Format
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.player.ShortsFeed
import com.example.playbox.ui.components.Avatar
import com.example.playbox.ui.components.EmptyState
import com.example.playbox.ui.theme.Playbox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen swipe feed.
 *
 * Swipe up   → next video (random when shuffle is on)
 * Swipe down → the video you just came from
 * Tap        → pause / resume
 * Double tap → like
 * Hold       → 2× speed while held
 */
@Composable
fun ShortsScreen(
    library: LibraryStore,
    playback: PlaybackController,
    bottomChrome: Dp,
    onMore: (Video) -> Unit,
) {
    val feed = remember { ShortsFeed() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(library.videos) { feed.refresh(library.shorts) }
    LaunchedEffect(Unit) { playback.suspendForShorts() }

    if (feed.isEmpty) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.PlayArrow,
                title = "Nothing to swipe through",
                message = "Portrait clips and anything under 90 seconds lands here.",
            )
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = feed.cursor) { feed.reel.size }

    // Pager settles → tell the feed, which materialises the page below.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> feed.onSettled(page) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1,
            key = { index -> feed.reel.getOrNull(index)?.id ?: "slot-$index" },
        ) { page ->
            val video = feed.reel.getOrNull(page)
            if (video != null) {
                ShortPage(
                    video = library.byId(video.id) ?: video,
                    isActive = pagerState.settledPage == page,
                    isMuted = feed.isMuted,
                    library = library,
                    bottomChrome = bottomChrome,
                    onMore = { onMore(video) },
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    },
                    onOpenInPlayer = { playback.play(video, library.videos) },
                )
            }
        }

        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))

            HeaderButton(Icons.Default.Shuffle, "Shuffle", tint = if (feed.isShuffling) Playbox.Accent else Color.White) {
                feed.changeShuffling(!feed.isShuffling)
            }
            Spacer(Modifier.width(10.dp))
            HeaderButton(Icons.Default.Casino, "Random") {
                feed.stageRandomNext()
                scope.launch { pagerState.animateScrollToPage(feed.cursor + 1) }
            }
            Spacer(Modifier.width(10.dp))
            HeaderButton(
                if (feed.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                "Mute",
            ) { feed.isMuted = !feed.isMuted }
        }
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** One full-screen page of the swipe feed, with its own looping player. */
@Composable
private fun ShortPage(
    video: Video,
    isActive: Boolean,
    isMuted: Boolean,
    library: LibraryStore,
    bottomChrome: Dp,
    onMore: () -> Unit,
    onNext: () -> Unit,
    onOpenInPlayer: () -> Unit,
) {
    val context = LocalContext.current

    val player = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(video.id) {
        onDispose { player.release() }
    }

    var paused by remember(video.id) { mutableStateOf(false) }
    var showLike by remember(video.id) { mutableStateOf(false) }
    var boosting by remember(video.id) { mutableStateOf(false) }
    var progress by remember(video.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive, paused) {
        player.volume = if (isMuted) 0f else 1f
        if (isActive && !paused) {
            player.play()
            library.recordPlay(video.id)
        } else {
            player.pause()
        }
    }

    LaunchedEffect(isMuted) { player.volume = if (isMuted) 0f else 1f }

    LaunchedEffect(isActive) {
        while (isActive) {
            val total = if (player.duration > 0) player.duration else video.durationMs
            progress = if (total > 0) (player.currentPosition.toFloat() / total).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture surface: tap, double tap, hold.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(video.id) {
                    detectTapGestures(
                        onPress = {
                            // Released inside 350ms → a tap. Otherwise it is a hold.
                            val heldDown = withTimeoutOrNull(350) { tryAwaitRelease() } == null
                            if (heldDown) {
                                boosting = true
                                player.setPlaybackSpeed(2f)
                                tryAwaitRelease()
                                player.setPlaybackSpeed(1f)
                                boosting = false
                            }
                        },
                        onTap = {
                            paused = !paused
                        },
                        onDoubleTap = {
                            library.setLiked(video.id, true)
                            showLike = true
                        },
                    )
                },
        )

        if (paused) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(72.dp),
            )
        }

        if (showLike) {
            Icon(
                Icons.Default.ThumbUp,
                contentDescription = null,
                tint = Playbox.Accent,
                modifier = Modifier.align(Alignment.Center).size(88.dp),
            )
            LaunchedEffect(showLike) {
                delay(600)
                showLike = false
            }
        }

        if (boosting) {
            Text(
                "2×",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Right-hand action rail
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = bottomChrome + 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RailButton(
                Icons.Default.ThumbUp,
                Format.compact(video.plays),
                tint = if (video.isLiked) Playbox.Accent else Color.White,
            ) { library.toggleLike(video.id) }
            RailButton(Icons.Default.SkipNext, "Next", onClick = onNext)
            RailButton(Icons.Default.OpenInFull, "Player", onClick = onOpenInPlayer)
            RailButton(Icons.Default.MoreVert, "More", onClick = onMore)
            Avatar(video, size = 40.dp)
        }

        // Title block
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = bottomChrome + 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(video, size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(video.bucket, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                video.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Progress hairline
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomChrome)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    caption: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(54.dp).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = caption, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(caption, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
