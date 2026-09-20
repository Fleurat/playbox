package com.example.playbox.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.playbox.data.Video

/**
 * The swipe feed's path through the library.
 *
 * [reel] is the linear trail actually visited: swiping up walks forward, swiping
 * down walks back through exactly what you already saw — even in shuffle mode,
 * where the next pick is random but, once drawn, becomes a fixed part of history.
 */
class ShortsFeed {

    var reel by mutableStateOf<List<Video>>(emptyList())
        private set
    var cursor by mutableStateOf(0)
        private set
    var isShuffling by mutableStateOf(true)
        private set
    var isMuted by mutableStateOf(false)

    private var pool: List<Video> = emptyList()
    private var bag = mutableListOf<Video>()
    private val trailLimit = 60

    val current: Video? get() = reel.getOrNull(cursor)
    val isEmpty: Boolean get() = pool.isEmpty()

    fun changeShuffling(value: Boolean) {
        isShuffling = value
        bag.clear()
    }

    /** Re-seeds from the library, keeping the current video on screen if it survived. */
    fun refresh(videos: List<Video>) {
        pool = videos
        if (pool.isEmpty()) {
            reel = emptyList()
            cursor = 0
            return
        }

        val ids = pool.map { it.id }.toSet()
        val keep = current?.takeIf { it.id in ids }
        val trimmed = reel.filter { it.id in ids }
        val keepIndex = keep?.let { k -> trimmed.indexOfFirst { it.id == k.id } } ?: -1

        if (keepIndex >= 0) {
            reel = trimmed
            cursor = keepIndex
        } else {
            reel = listOf(if (isShuffling) pool.random() else pool.first())
            cursor = 0
        }
        bag.clear()
        ensureNext()
    }

    /** Called when the pager settles on a page, so the feed always has one more below. */
    fun onSettled(page: Int) {
        cursor = page.coerceIn(0, (reel.size - 1).coerceAtLeast(0))
        ensureNext()
        trim()
    }

    /** "Surprise me": swap whatever was queued for a fresh random pick. */
    fun stageRandomNext() {
        if (pool.size < 2) return
        if (reel.size > cursor + 1) reel = reel.subList(0, cursor + 1)
        drawRandom()?.let { reel = reel + it }
    }

    fun jumpTo(video: Video) {
        reel = reel.subList(0, (cursor + 1).coerceAtMost(reel.size)) + video
        cursor = reel.size - 1
        ensureNext()
        trim()
    }

    // MARK: - Picking

    /**
     * Guarantees the page below exists, so it is composed and buffering before
     * the finger even moves.
     */
    private fun ensureNext() {
        if (pool.isEmpty() || reel.size > cursor + 1) return
        val pick = if (isShuffling) drawRandom() else sequentialNext()
        if (pick != null) reel = reel + pick
    }

    private fun sequentialNext(): Video? {
        val now = current ?: return pool.firstOrNull()
        val index = pool.indexOfFirst { it.id == now.id }
        if (index < 0) return pool.firstOrNull()
        return pool[(index + 1) % pool.size]
    }

    /** Bag shuffle: nothing repeats until the whole library has been seen once. */
    private fun drawRandom(): Video? {
        if (bag.isEmpty()) {
            bag = pool.filter { it.id != current?.id }.shuffled().toMutableList()
            if (bag.isEmpty()) bag = pool.shuffled().toMutableList()
        }
        return bag.removeLastOrNull()
    }

    private fun trim() {
        if (reel.size <= trailLimit || cursor <= 20) return
        val drop = cursor - 20
        reel = reel.drop(drop)
        cursor -= drop
    }
}
