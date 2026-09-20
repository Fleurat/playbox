import SwiftUI

/// The ⋮ sheet: play next, queue, like, playlists, rename, share, delete.
struct VideoActionSheet: View {
    let video: Video
    var context: [Video] = []

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.dismiss) private var dismiss

    @State private var showingRename = false
    @State private var showingPlaylists = false
    @State private var confirmingDelete = false
    @State private var draftTitle = ""

    private var live: Video { library.video(id: video.id) ?? video }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 12) {
                        VideoThumbnail(video: live, cornerRadius: 8)
                            .frame(width: 120)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(live.title)
                                .font(.system(size: 14, weight: .semibold))
                                .lineLimit(2)
                            Text("\(Format.duration(live.duration)) · \(Format.fileSize(live.fileSize))")
                                .font(.system(size: 11))
                                .foregroundStyle(Theme.secondaryText)
                        }
                    }
                    .listRowBackground(Color.clear)
                }

                Section {
                    row("text.line.first.and.arrowtriangle.forward", "Play next") {
                        playback.playNext(live)
                        dismiss()
                    }
                    row("list.bullet", "Add to queue") {
                        playback.addToQueue(live)
                        dismiss()
                    }
                    row(live.isLiked ? "hand.thumbsup.fill" : "hand.thumbsup",
                        live.isLiked ? "Remove from liked" : "Like") {
                        library.toggleLike(live.id)
                        dismiss()
                    }
                    row("plus.rectangle.on.folder", "Save to playlist") { showingPlaylists = true }
                }

                Section {
                    row("pencil", "Rename") {
                        draftTitle = live.title
                        showingRename = true
                    }
                    ShareLink(item: live.url) {
                        Label("Share file", systemImage: "square.and.arrow.up")
                            .foregroundStyle(Theme.primaryText)
                    }
                    row("trash", "Delete from device", tint: Theme.accent) {
                        confirmingDelete = true
                    }
                }

                Section("Details") {
                    detail("File", live.fileName)
                    detail("Size", Format.fileSize(live.fileSize))
                    detail("Resolution", live.width > 0 ? "\(live.width) × \(live.height)" : "Unknown")
                    detail("Plays", "\(live.plays)")
                    detail("Added", live.dateAdded.formatted(date: .abbreviated, time: .shortened))
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .sheet(isPresented: $showingPlaylists) {
            PlaylistPickerView(video: live)
                .presentationDetents([.medium])
        }
        .alert("Rename video", isPresented: $showingRename) {
            TextField("Title", text: $draftTitle)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                library.rename(live.id, to: draftTitle)
                dismiss()
            }
        }
        .alert("Delete this video?", isPresented: $confirmingDelete) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                if playback.current?.id == live.id { playback.close() }
                library.delete(live.id)
                dismiss()
            }
        } message: {
            Text("The file is removed from this device. This cannot be undone.")
        }
    }

    private func row(_ symbol: String, _ title: String,
                     tint: Color = Theme.primaryText,
                     action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol).foregroundStyle(tint)
        }
    }

    private func detail(_ key: String, _ value: String) -> some View {
        HStack {
            Text(key).foregroundStyle(Theme.secondaryText)
            Spacer()
            Text(value)
                .foregroundStyle(Theme.primaryText)
                .multilineTextAlignment(.trailing)
        }
        .font(.system(size: 12))
    }
}

/// Long-press menu — the fast subset of the same actions.
struct VideoContextMenu: View {
    let video: Video
    var context: [Video] = []

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController

    var body: some View {
        Button {
            playback.play(video, in: context)
        } label: {
            Label("Play", systemImage: "play.fill")
        }
        Button {
            playback.playNext(video)
        } label: {
            Label("Play next", systemImage: "text.line.first.and.arrowtriangle.forward")
        }
        Button {
            playback.addToQueue(video)
        } label: {
            Label("Add to queue", systemImage: "list.bullet")
        }
        Button {
            library.toggleLike(video.id)
        } label: {
            Label(video.isLiked ? "Remove from liked" : "Like",
                  systemImage: video.isLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
        }
    }
}

struct PlaylistPickerView: View {
    let video: Video

    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""
    @State private var creating = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        creating = true
                    } label: {
                        Label("New playlist", systemImage: "plus")
                    }
                }
                Section {
                    if library.playlists.isEmpty {
                        Text("No playlists yet")
                            .foregroundStyle(Theme.secondaryText)
                    }
                    ForEach(library.playlists) { playlist in
                        Button {
                            library.addToPlaylist(playlist.id, video: video.id)
                            dismiss()
                        } label: {
                            HStack {
                                Label(playlist.name, systemImage: "rectangle.stack")
                                Spacer()
                                if playlist.videoIDs.contains(video.id) {
                                    Image(systemName: "checkmark").foregroundStyle(Theme.accent)
                                }
                            }
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Save to…")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } }
            }
            .alert("New playlist", isPresented: $creating) {
                TextField("Name", text: $newName)
                Button("Cancel", role: .cancel) { newName = "" }
                Button("Create") {
                    let playlist = library.createPlaylist(named: newName.isEmpty ? "New playlist" : newName,
                                                          with: [video.id])
                    newName = ""
                    _ = playlist
                    dismiss()
                }
            }
        }
    }
}
