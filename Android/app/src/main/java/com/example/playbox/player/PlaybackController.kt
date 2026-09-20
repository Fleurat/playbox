package com.example.playbox.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class Presentation { HIDDEN, MINI, FULL }

enum class RepeatMode {
    OFF, ALL, ONE;

    val label: String
        get() = when (this) {
            OFF -> "Repeat off"
            ALL -> "Repeat queue"
            ONE -> "Repeat one"
        }

    fun advance(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

/**
 * Owns the one ExoPlayer behind the full player and the mini player, plus the
 * queue that drives autoplay and "Up next".
 */
class PlaybackController(context: Context, private val library: LibraryStore) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var current by mutableStateOf<Video?>(null)
        private set
    var presentation by mutableStateOf(Presentation.HIDDEN)
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var didReachEnd by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var queue by mutableStateOf<List<Video>>(emptyList())
        private set
    var queueIndex by mutableStateOf(0)
        private set

    var speed by mutableStateOf(1f)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var isShuffling by mutableStateOf(false)
    var isMuted by mutableStateOf(false)
    var isScrubbing by mutableStateOf(false)
    /** Pinch toggles between letterboxed and filled. */
    var fillsScreen by mutableStateOf(false)
    var isFullscreen by mutableStateOf(false)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bag = mutableListOf<Video>()
    private var ticker: Job? = null
    private var lastSavedMs = 0L

    val upNext: List<Video>
        get() = if (queue.isEmpty() || queueIndex + 1 >= queue.size) emptyList()
        else queue.subList(queueIndex + 1, queue.size)

    val hasPrevious: Boolean get() = queueIndex > 0 || positionMs > 3_000
    val hasNext: Boolean get() = isShuffling || queueIndex + 1 < queue.size || repeatMode == RepeatMode.ALL

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) didReachEnd = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val d = player.duration
                    if (d > 0) durationMs = d
                }
                if (state == Player.STATE_ENDED) handleEnded()
            }
        })
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (!isScrubbing && player.isPlaying) {
                    positionMs = player.currentPosition
                    if (durationMs <= 0 && player.duration > 0) durationMs = player.duration
                    // Persist a resume point occasionally rather than every tick.
                    if (abs(positionMs - lastSavedMs) > 5_000) {
                        lastSavedMs = positionMs
                        savePosition()
                    }
                }
                delay(200)
            }
        }
    }

    // MARK: - Starting playback

    /**
     * [context] is the list the video was tapped in — it becomes the queue, so
     * autoplay walks the same shelf the user was browsing.
     */
    fun play(video: Video, context: List<Video> = emptyList(), startAtMs: Long? = null, fullScreen: Boolean = true) {
        var newQueue = context.ifEmpty { listOf(video) }
        if (newQueue.none { it.id == video.id }) newQueue = listOf(video) + newQueue
        if (newQueue.size == 1) newQueue = newQueue + library.relatedTo(video)

        queue = newQueue
        queueIndex = newQueue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        refillBag()
        load(video, startAtMs)
        presentation = if (fullScreen) Presentation.FULL
        else if (presentation == Presentation.HIDDEN) Presentation.MINI else presentation
    }

    fun playAll(videos: List<Video>, shuffled: Boolean = false) {
        if (videos.isEmpty()) return
        isShuffling = shuffled
        val first = if (shuffled) videos.random() else videos.first()
        play(first, videos, startAtMs = 0L)
    }

    private fun load(video: Video, startAtMs: Long?) {
        savePosition()

        current = video
        didReachEnd = false
        durationMs = video.durationMs
        isBuffering = true

        val start = startAtMs ?: resumePoint(video)
        positionMs = start
        lastSavedMs = start

        player.setMediaItem(MediaItem.fromUri(video.uri))
        player.prepare()
        if (start > 500) player.seekTo(start)
        player.volume = if (isMuted) 0f else 1f
        player.playbackParameters = PlaybackParameters(speed)
        player.playWhenReady = true

        library.recordPlay(video.id)
    }

    /** Resume unless the video was basically finished, in which case start over. */
    private fun resumePoint(video: Video): Long =
        if (video.resumeMs > 5_000 && !video.isFinished) video.resumeMs else 0L

    // MARK: - Transport

    fun playPause() {
        if (didReachEnd) {
            restart()
            return
        }
        if (player.isPlaying) pause() else resume()
    }

    fun resume() {
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.pause()
        savePosition()
    }

    fun restart() {
        didReachEnd = false
        seekTo(0)
        resume()
    }

    fun seekTo(ms: Long) {
        val clamped = if (durationMs > 0) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        positionMs = clamped
        didReachEnd = false
        player.seekTo(clamped)
    }

    /** The ±10s double-tap. */
    fun skip(deltaMs: Long) = seekTo(positionMs + deltaMs)

    fun changeSpeed(value: Float) {
        speed = value
        player.playbackParameters = PlaybackParameters(value)
    }

    fun changeMuted(muted: Boolean) {
        isMuted = muted
        player.volume = if (muted) 0f else 1f
    }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return

        if (isShuffling) {
            loadNextFromBag()
            return
        }
        when {
            queueIndex + 1 < queue.size -> {
                queueIndex += 1
                load(queue[queueIndex], 0L)
            }
            repeatMode == RepeatMode.ALL || !auto -> {
                queueIndex = 0
                load(queue[0], 0L)
            }
            else -> {
                didReachEnd = true
                player.pause()
                savePosition()
            }
        }
    }

    /** First press rewinds; a second press within 3s goes back a video. */
    fun previous() {
        if (queue.isEmpty()) return
        if (positionMs > 3_000) {
            seekTo(0)
            return
        }
        if (queueIndex > 0) {
            queueIndex -= 1
            load(queue[queueIndex], 0L)
        } else {
            seekTo(0)
        }
    }

    /** Swipe-down in fullscreen: always the previous video, never a rewind. */
    fun previousTrack() {
        if (queue.isEmpty()) return
        if (queueIndex > 0) {
            queueIndex -= 1
            load(queue[queueIndex], 0L)
        } else {
            seekTo(0)
        }
    }

    fun playRandom() {
        if (queue.isEmpty()) return
        loadNextFromBag()
    }

    private fun handleEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            else -> {
                current?.let { library.setResume(it.id, 0L) }
                next(auto = true)
            }
        }
    }

    // MARK: - Shuffle bag — everything plays once before anything repeats

    private fun refillBag() {
        bag = queue.filter { it.id != current?.id }.shuffled().toMutableList()
    }

    private fun loadNextFromBag() {
        if (bag.isEmpty()) refillBag()
        val pick = bag.removeLastOrNull() ?: current ?: return
        queueIndex = queue.indexOfFirst { it.id == pick.id }.takeIf { it >= 0 } ?: queueIndex
        load(pick, 0L)
    }

    // MARK: - Queue editing

    fun playNext(video: Video) {
        val without = queue.filterNot { it.id == video.id }
        val at = (queueIndex + 1).coerceIn(0, without.size)
        queue = without.toMutableList().apply { add(at, video) }
    }

    fun addToQueue(video: Video) {
        if (queue.none { it.id == video.id }) queue = queue + video
    }

    fun jumpTo(video: Video) {
        val index = queue.indexOfFirst { it.id == video.id }
        if (index < 0) return
        queueIndex = index
        load(video, 0L)
    }

    // MARK: - Presentation

    fun minimize() {
        if (current == null) return
        isFullscreen = false
        presentation = Presentation.MINI
    }

    fun expand() {
        if (current != null) presentation = Presentation.FULL
    }

    fun close() {
        savePosition()
        player.pause()
        player.clearMediaItems()
        current = null
        queue = emptyList()
        queueIndex = 0
        isFullscreen = false
        presentation = Presentation.HIDDEN
    }

    /** Called when the swipe feed takes over, so two videos never play at once. */
    fun suspendForShorts() {
        if (current == null) return
        pause()
        if (presentation == Presentation.FULL) presentation = Presentation.MINI
    }

    fun savePosition() {
        val video = current ?: return
        if (durationMs <= 0) return
        val atEnd = positionMs >= durationMs - 2_000
        library.setResume(video.id, if (atEnd) 0L else positionMs)
    }

    fun release() {
        ticker?.cancel()
        player.release()
    }
}
