package com.example.playbox

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.playbox.data.LibraryStore
import com.example.playbox.player.PlaybackController

class PlayboxApplication : Application(), ImageLoaderFactory {

    lateinit var library: LibraryStore
        private set
    lateinit var playback: PlaybackController
        private set

    override fun onCreate() {
        super.onCreate()
        library = LibraryStore(this)
        playback = PlaybackController(this, library)
    }

    /** Teaches Coil to pull a frame out of a video file for thumbnails. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
