package com.example.playbox.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.playbox.data.Format
import com.example.playbox.data.LibraryStore
import com.example.playbox.data.Video
import com.example.playbox.player.PlaybackController
import com.example.playbox.ui.theme.Playbox
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    video: Video,
    library: LibraryStore,
    playback: PlaybackController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val live = library.byId(video.id) ?: video

    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingPlaylist by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf(live.title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Playbox.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumbnail(
                    video = live,
                    modifier = Modifier.width(120.dp),
                    corner = 8.dp,
                    showProgress = false,
                )
                Column {
                    Text(
                        live.title,
                        color = Playbox.PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "${Format.duration(live.durationMs)} · ${Format.fileSize(live.sizeBytes)}",
                        color = Playbox.SecondaryText,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

            SheetRow(Icons.Default.PlaylistPlay, "Play next") {
                playback.playNext(live); onDismiss()
            }
            SheetRow(Icons.Default.QueueMusic, "Add to queue") {
                playback.addToQueue(live); onDismiss()
            }
            SheetRow(
                Icons.Default.ThumbUp,
                if (live.isLiked) "Remove from liked" else "Like",
                tint = if (live.isLiked) Playbox.Accent else Playbox.PrimaryText,
            ) {
                library.toggleLike(live.id); onDismiss()
            }
            SheetRow(Icons.Default.PlaylistAdd, "Save to playlist") { pickingPlaylist = true }
            SheetRow(Icons.Default.Edit, "Rename") {
                draftTitle = live.title
                renaming = true
            }
            SheetRow(Icons.Default.Share, "Share") {
                runCatching {
                    val shareUri = if (live.id.startsWith("file:")) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(library.importDir, live.id.removePrefix("file:")),
                        )
                    } else {
                        live.uri
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share video"))
                }
                onDismiss()
            }
            if (library.canDelete(live)) {
                SheetRow(Icons.Default.Delete, "Delete from device", tint = Playbox.Accent) {
                    confirmingDelete = true
                }
            }

            Spacer(Modifier.size(12.dp))
            Detail("File", live.bucket)
            Detail("Size", Format.fileSize(live.sizeBytes))
            Detail("Resolution", if (live.width > 0) "${live.width} × ${live.height}" else "Unknown")
            Detail("Plays", live.plays.toString())
            Detail("Added", Format.age(live.dateAddedMs))
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename video") },
            text = {
                TextField(value = draftTitle, onValueChange = { draftTitle = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    library.rename(live.id, draftTitle)
                    renaming = false
                    onDismiss()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            containerColor = Playbox.Surface,
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this video?") },
            text = { Text("The file is removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (playback.current?.id == live.id) playback.close()
                    library.deleteImported(live)
                    confirmingDelete = false
                    onDismiss()
                }) { Text("Delete", color = Playbox.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            containerColor = Playbox.Surface,
        )
    }

    if (pickingPlaylist) {
        PlaylistPickerDialog(
            video = live,
            library = library,
            onDismiss = {
                pickingPlaylist = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    tint: Color = Playbox.PrimaryText,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

@Composable
private fun Detail(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
    ) {
        Text(key, color = Playbox.SecondaryText, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = Playbox.PrimaryText, fontSize = 12.sp)
    }
}

@Composable
fun PlaylistPickerDialog(
    video: Video,
    library: LibraryStore,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = { TextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    library.createPlaylist(newName, listOf(video.id))
                    creating = false
                    onDismiss()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
            containerColor = Playbox.Surface,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to playlist") },
        text = {
            Column {
                Text(
                    "New playlist",
                    color = Playbox.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 10.dp),
                )
                if (library.playlists.isEmpty()) {
                    Text("No playlists yet", color = Playbox.SecondaryText, fontSize = 13.sp)
                }
                library.playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                library.addToPlaylist(playlist.id, video.id)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(playlist.name, color = Playbox.PrimaryText, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        if (video.id in playlist.videoIds) {
                            Text("Added", color = Playbox.Accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = Playbox.Surface,
    )
}
