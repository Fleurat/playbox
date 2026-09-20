package com.example.playbox.data

import android.net.Uri

/**
 * One playable video.
 *
 * [id] is stable across rescans: MediaStore rows keep their row id, imported
 * files are keyed by their path, so likes and resume points survive a restart.
 */
data class Video(
    val id: String,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedMs: Long,
    /** Folder the file sits in — stands in for a channel name. */
    val bucket: String,
    val plays: Int = 0,
    val isLiked: Boolean = false,
    val lastPlayedMs: Long = 0L,
    val resumeMs: Long = 0L,
) {
    val isPortrait: Boolean get() = height > 0 && height > width

    /** The swipe feed prefers portrait clips, then anything short. */
    val qualifiesAsShort: Boolean
        get() = isPortrait || (durationMs in 1 until 90_000)

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f

    /** Drives the red bar along the bottom of a thumbnail. */
    val watchProgress: Float
        get() = if (durationMs > 0 && resumeMs > 0) {
            (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else 0f

    val isFinished: Boolean
        get() = durationMs > 0 && resumeMs >= durationMs - 2_000

    val initial: String
        get() = title.trim().take(1).uppercase().ifEmpty { "V" }
}

data class Playlist(
    val id: String,
    val name: String,
    val videoIds: List<String> = emptyList(),
    val createdMs: Long = System.currentTimeMillis(),
)

/** User-owned facts that MediaStore knows nothing about. */
data class VideoMeta(
    val plays: Int = 0,
    val isLiked: Boolean = false,
    val lastPlayedMs: Long = 0L,
    val resumeMs: Long = 0L,
    val customTitle: String? = null,
)
