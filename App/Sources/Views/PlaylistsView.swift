import SwiftUI

struct PlaylistsView: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.bottomChrome) private var bottomChrome

    @State private var creating = false
    @State private var newName = ""
    @State private var renaming: Playlist?
    @State private var draftName = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    Button {
                        creating = true
                    } label: {
                        HStack(spacing: 12) {
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(Theme.surfaceHigh)
                                .frame(width: 148, height: 83)
                                .overlay {
                                    Image(systemName: "plus")
                                        .font(.system(size: 22, weight: .medium))
                                        .foregroundStyle(Theme.primaryText)
                                }
                            Text("New playlist")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(Theme.primaryText)
                            Spacer()
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)

                    if library.playlists.isEmpty {
                        EmptyStateView(
                            symbol: "rectangle.stack",
                            title: "No playlists",
                            message: "Group videos into playlists, then play or shuffle the whole list."
                        )
                    }

                    ForEach(library.playlists) { playlist in
                        NavigationLink {
                            PlaylistDetailView(playlistID: playlist.id)
                        } label: {
                            PlaylistRow(playlist: playlist)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                playback.playAll(library.resolve(ids: playlist.videoIDs))
                            } label: { Label("Play", systemImage: "play.fill") }
                            Button {
                                playback.playAll(library.resolve(ids: playlist.videoIDs), shuffled: true)
                            } label: { Label("Shuffle", systemImage: "shuffle") }
                            Button {
                                draftName = playlist.name
                                renaming = playlist
                            } label: { Label("Rename", systemImage: "pencil") }
                            Button(role: .destructive) {
                                library.deletePlaylist(playlist.id)
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }

                    Color.clear.frame(height: bottomChrome + 12)
                }
                .padding(.top, 8)
            }
            .scrollIndicators(.hidden)
            .background(Theme.background)
            .navigationTitle("Playlists")
            .navigationBarTitleDisplayMode(.inline)
        }
        .alert("New playlist", isPresented: $creating) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) { newName = "" }
            Button("Create") {
                library.createPlaylist(named: newName.isEmpty ? "New playlist" : newName)
                newName = ""
            }
        }
        .alert("Rename playlist", isPresented: Binding(
            get: { renaming != nil },
            set: { if !$0 { renaming = nil } }
        )) {
            TextField("Name", text: $draftName)
            Button("Cancel", role: .cancel) { renaming = nil }
            Button("Save") {
                if let renaming { library.renamePlaylist(renaming.id, to: draftName) }
                renaming = nil
            }
        }
    }
}

private struct PlaylistRow: View {
    let playlist: Playlist
    @EnvironmentObject private var library: LibraryStore

    private var videos: [Video] { library.resolve(ids: playlist.videoIDs) }

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                if let first = videos.first {
                    VideoThumbnail(video: first, cornerRadius: 8, showsDuration: false, showsProgress: false)
                        .frame(width: 148)
                } else {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Theme.surface)
                        .frame(width: 148, height: 83)
                        .overlay {
                            Image(systemName: "rectangle.stack")
                                .foregroundStyle(Theme.secondaryText)
                        }
                }

                HStack(spacing: 3) {
                    Image(systemName: "list.bullet").font(.system(size: 9))
                    Text("\(playlist.videoIDs.count)").font(.system(size: 11, weight: .semibold))
                }
                .foregroundStyle(.white)
                .pillBackground()
                .padding(6)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(playlist.name)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
                    .lineLimit(2)
                Text("\(playlist.videoIDs.count) videos")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.secondaryText)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.secondaryText)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}

struct PlaylistDetailView: View {
    let playlistID: UUID

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.bottomChrome) private var bottomChrome

    private var playlist: Playlist? { library.playlist(id: playlistID) }
    private var videos: [Video] { library.resolve(ids: playlist?.videoIDs ?? []) }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10) {
                    Button {
                        playback.playAll(videos)
                    } label: {
                        Label("Play all", systemImage: "play.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(Color.white, in: Capsule())
                    }
                    .buttonStyle(.plain)

                    Button {
                        playback.playAll(videos, shuffled: true)
                    } label: {
                        Label("Shuffle", systemImage: "shuffle")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.primaryText)
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(Theme.surfaceHigh, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .disabled(videos.isEmpty)

                if videos.isEmpty {
                    EmptyStateView(symbol: "plus.rectangle.on.folder",
                                   title: "Empty playlist",
                                   message: "Add videos with the ⋮ menu on any video.")
                }

                ForEach(videos) { video in
                    VideoRow(video: video, context: videos) {
                        playback.play(video, in: videos)
                    }
                    .contextMenu {
                        Button(role: .destructive) {
                            library.removeFromPlaylist(playlistID, video: video.id)
                        } label: {
                            Label("Remove from playlist", systemImage: "minus.circle")
                        }
                    }
                }

                Color.clear.frame(height: bottomChrome + 12)
            }
        }
        .scrollIndicators(.hidden)
        .background(Theme.background)
        .navigationTitle(playlist?.name ?? "Playlist")
        .navigationBarTitleDisplayMode(.inline)
    }
}
