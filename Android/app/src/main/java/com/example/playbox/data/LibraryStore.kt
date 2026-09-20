package com.example.playbox.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The library.
 *
 * Videos come from two places: the device's MediaStore (everything the phone
 * already knows about) and Playbox's own folder, where imported files land.
 * MediaStore owns the facts; [VideoMeta] in a JSON file alongside owns the
 * things only this app cares about — plays, likes, resume points, custom titles.
 */
class LibraryStore(private val context: Context) {

    var videos by mutableStateOf<List<Video>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    /** Most recently watched first, de-duplicated. */
    var history by mutableStateOf<List<String>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var hasScanned by mutableStateOf(false)
        private set

    private var meta = mutableMapOf<String, VideoMeta>()
    private val metaFile: File get() = File(context.filesDir, "library.json")

    /** Where imported copies live: /Android/data/<pkg>/files/Videos */
    val importDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Videos").apply { mkdirs() }

    init {
        loadMeta()
    }

    // MARK: - Scanning

    suspend fun scan() {
        if (isScanning) return
        isScanning = true
        try {
            val found = withContext(Dispatchers.IO) { queryMediaStore() + scanImportDir() }
            val merged = found
                .map { it.withMeta(meta[it.id]) }
                .sortedByDescending { it.dateAddedMs }
            val liveIds = merged.map { it.id }.toSet()
            videos = merged
            // Drop references to files that are gone.
            history = history.filter { it in liveIds }
            playlists = playlists.map { p -> p.copy(videoIds = p.videoIds.filter { it in liveIds }) }
            hasScanned = true
        } finally {
            isScanning = false
        }
    }

    private fun queryMediaStore(): List<Video> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val out = mutableListOf<Video>()
        runCatching {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val wCol = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val bucketCol = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (c.moveToNext()) {
                    val rowId = c.getLong(idCol)
                    val name = nameCol.takeIf { it >= 0 }?.let { c.getString(it) } ?: "Video $rowId"
                    out += Video(
                        id = "ms:$rowId",
                        uri = ContentUris.withAppendedId(collection, rowId),
                        title = prettyTitle(name),
                        durationMs = durCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                        sizeBytes = sizeCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L,
                        width = wCol.takeIf { it >= 0 }?.let { c.getInt(it) } ?: 0,
                        height = hCol.takeIf { it >= 0 }?.let { c.getInt(it) } ?: 0,
                        // MediaStore stores DATE_ADDED in seconds.
                        dateAddedMs = (dateCol.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L) * 1000L,
                        bucket = bucketCol.takeIf { it >= 0 }?.let { c.getString(it) } ?: "Device",
                    )
                }
            }
        }
        return out
    }

    /** Anything copied into Playbox's own folder, which MediaStore does not index. */
    private fun scanImportDir(): List<Video> {
        val dir = importDir
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in VIDEO_EXTENSIONS }
            ?: return emptyList()
        return files.map { file ->
            val retriever = MediaMetadataRetriever()
            var duration = 0L
            var width = 0
            var height = 0
            runCatching {
                retriever.setDataSource(file.absolutePath)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val swap = width; width = height; height = swap
                }
            }
            runCatching { retriever.release() }

            Video(
                id = "file:${file.name}",
                uri = Uri.fromFile(file),
                title = prettyTitle(file.name),
                durationMs = duration,
                sizeBytes = file.length(),
                width = width,
                height = height,
                dateAddedMs = file.lastModified(),
                bucket = "Playbox",
            )
        }
    }

    private fun Video.withMeta(m: VideoMeta?): Video {
        if (m == null) return this
        return copy(
            title = m.customTitle ?: title,
            plays = m.plays,
            isLiked = m.isLiked,
            lastPlayedMs = m.lastPlayedMs,
            resumeMs = m.resumeMs,
        )
    }

    // MARK: - Shelves

    fun byId(id: String?): Video? = id?.let { key -> videos.firstOrNull { it.id == key } }

    fun resolve(ids: List<String>): List<Video> {
        val index = videos.associateBy { it.id }
        return ids.mapNotNull { index[it] }
    }

    val recent: List<Video> get() = videos
    val liked: List<Video> get() = videos.filter { it.isLiked }.sortedByDescending { maxOf(it.lastPlayedMs, it.dateAddedMs) }
    val mostPlayed: List<Video> get() = videos.filter { it.plays > 0 }.sortedByDescending { it.plays }
    val unwatched: List<Video> get() = videos.filter { it.plays == 0 }
    val longest: List<Video> get() = videos.sortedByDescending { it.durationMs }
    val historyVideos: List<Video> get() = resolve(history)

    val continueWatching: List<Video>
        get() = videos.filter { it.watchProgress > 0.02f && !it.isFinished }
            .sortedByDescending { it.lastPlayedMs }

    /** Portrait or short clips; falls back to everything so the feed is never empty. */
    val shorts: List<Video>
        get() = videos.filter { it.qualifiesAsShort }.ifEmpty { videos }

    fun search(query: String): List<Video> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return videos.filter { it.title.lowercase().contains(needle) || it.bucket.lowercase().contains(needle) }
    }

    fun relatedTo(video: Video): List<Video> = videos.filter { it.id != video.id }

    // MARK: - Mutations

    private fun edit(id: String, change: (VideoMeta) -> VideoMeta) {
        val updated = change(meta[id] ?: VideoMeta())
        meta[id] = updated
        videos = videos.map { if (it.id == id) it.withMeta(updated) else it }
        saveMeta()
    }

    fun toggleLike(id: String) = edit(id) { it.copy(isLiked = !it.isLiked) }

    fun setLiked(id: String, liked: Boolean) = edit(id) { it.copy(isLiked = liked) }

    fun rename(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        edit(id) { it.copy(customTitle = clean) }
    }

    fun recordPlay(id: String) {
        edit(id) { it.copy(plays = it.plays + 1, lastPlayedMs = System.currentTimeMillis()) }
        history = (listOf(id) + history.filter { it != id }).take(200)
        saveMeta()
    }

    fun setResume(id: String, ms: Long) = edit(id) { it.copy(resumeMs = ms.coerceAtLeast(0L)) }

    fun clearHistory() {
        history = emptyList()
        meta = meta.mapValues { (_, v) -> v.copy(resumeMs = 0L) }.toMutableMap()
        videos = videos.map { it.copy(resumeMs = 0L) }
        saveMeta()
    }

    /** Only files Playbox itself owns can be deleted without a system prompt. */
    fun canDelete(video: Video): Boolean = video.id.startsWith("file:")

    fun deleteImported(video: Video): Boolean {
        if (!canDelete(video)) return false
        val file = File(importDir, video.id.removePrefix("file:"))
        val gone = runCatching { file.delete() }.getOrDefault(false)
        if (gone) {
            videos = videos.filterNot { it.id == video.id }
            history = history.filterNot { it == video.id }
            playlists = playlists.map { p -> p.copy(videoIds = p.videoIds.filterNot { it == video.id }) }
            meta.remove(video.id)
            saveMeta()
        }
        return gone
    }

    // MARK: - Playlists

    fun createPlaylist(name: String, ids: List<String> = emptyList()): Playlist {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name.trim().ifEmpty { "New playlist" }, videoIds = ids)
        playlists = playlists + playlist
        saveMeta()
        return playlist
    }

    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun addToPlaylist(playlistId: String, videoId: String) {
        playlists = playlists.map {
            if (it.id == playlistId && videoId !in it.videoIds) it.copy(videoIds = it.videoIds + videoId) else it
        }
        saveMeta()
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(videoIds = it.videoIds.filterNot { v -> v == videoId }) else it
        }
        saveMeta()
    }

    fun deletePlaylist(playlistId: String) {
        playlists = playlists.filterNot { it.id == playlistId }
        saveMeta()
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        playlists = playlists.map { if (it.id == playlistId) it.copy(name = clean) else it }
        saveMeta()
    }

    // MARK: - Persistence

    private fun loadMeta() {
        val file = metaFile
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())

            val metaObj = root.optJSONObject("meta") ?: JSONObject()
            for (key in metaObj.keys()) {
                val o = metaObj.getJSONObject(key)
                meta[key] = VideoMeta(
                    plays = o.optInt("plays", 0),
                    isLiked = o.optBoolean("liked", false),
                    lastPlayedMs = o.optLong("lastPlayed", 0L),
                    resumeMs = o.optLong("resume", 0L),
                    customTitle = o.optString("title", "").ifEmpty { null },
                )
            }

            val lists = root.optJSONArray("playlists") ?: JSONArray()
            playlists = (0 until lists.length()).map { i ->
                val o = lists.getJSONObject(i)
                val ids = o.optJSONArray("videos") ?: JSONArray()
                Playlist(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Playlist"),
                    videoIds = (0 until ids.length()).map { ids.getString(it) },
                    createdMs = o.optLong("created", System.currentTimeMillis()),
                )
            }

            val hist = root.optJSONArray("history") ?: JSONArray()
            history = (0 until hist.length()).map { hist.getString(it) }
        }
    }

    fun saveMeta() {
        runCatching {
            val metaObj = JSONObject()
            meta.forEach { (key, value) ->
                metaObj.put(key, JSONObject().apply {
                    put("plays", value.plays)
                    put("liked", value.isLiked)
                    put("lastPlayed", value.lastPlayedMs)
                    put("resume", value.resumeMs)
                    value.customTitle?.let { put("title", it) }
                })
            }
            val lists = JSONArray()
            playlists.forEach { p ->
                lists.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("videos", JSONArray(p.videoIds))
                    put("created", p.createdMs)
                })
            }
            val root = JSONObject().apply {
                put("meta", metaObj)
                put("playlists", lists)
                put("history", JSONArray(history))
            }
            metaFile.writeText(root.toString())
        }
    }

    companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi", "ts", "mpg", "mpeg")

        fun prettyTitle(fileName: String): String {
            val stem = fileName.substringBeforeLast('.', fileName)
            return stem.replace('_', ' ').replace('-', ' ').trim().ifEmpty { fileName }
        }
    }
}
