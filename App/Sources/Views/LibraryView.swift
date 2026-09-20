import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.bottomChrome) private var bottomChrome

    @State private var showingHistory = false
    @State private var showingLiked = false
    @State private var showingAll = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header

                    if !library.historyVideos.isEmpty {
                        sectionHeader("History", trailing: "See all") { showingHistory = true }
                        shelf(library.historyVideos)
                    }

                    if !library.continueWatching.isEmpty {
                        sectionHeader("Continue watching")
                        shelf(library.continueWatching)
                    }

                    if !library.liked.isEmpty {
                        sectionHeader("Liked videos", trailing: "See all") { showingLiked = true }
                        shelf(library.liked)
                    }

                    sectionHeader("All videos (\(library.videos.count))", trailing: "See all") { showingAll = true }
                    if library.videos.isEmpty {
                        EmptyStateView(symbol: "square.stack.3d.up.slash",
                                       title: "Your library is empty",
                                       message: "Use + in the tab bar to import videos from Photos or Files.")
                    } else {
                        ForEach(library.recent.prefix(8)) { video in
                            VideoRow(video: video, context: library.recent) {
                                playback.play(video, in: library.recent)
                            }
                        }
                    }

                    Color.clear.frame(height: bottomChrome + 12)
                }
            }
            .scrollIndicators(.hidden)
            .background(Theme.background)
            .refreshable { await library.rescan() }
            .navigationDestination(isPresented: $showingHistory) {
                VideoListPage(title: "History", videos: library.historyVideos, allowsClear: true)
            }
            .navigationDestination(isPresented: $showingLiked) {
                VideoListPage(title: "Liked videos", videos: library.liked)
            }
            .navigationDestination(isPresented: $showingAll) {
                VideoListPage(title: "All videos", videos: library.recent)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Circle()
                    .fill(Theme.accent)
                    .frame(width: 44, height: 44)
                    .overlay {
                        Image(systemName: "person.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(.white)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text("You")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.primaryText)
                    Text("\(library.videos.count) videos · \(Format.fileSize(totalBytes))")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.secondaryText)
                }
                Spacer()
            }

            HStack(spacing: 8) {
                Button {
                    playback.playAll(library.recent, shuffled: false)
                } label: {
                    Label("Play all", systemImage: "play.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(library.videos.isEmpty)

                Button {
                    playback.playAll(library.recent, shuffled: true)
                } label: {
                    Label("Shuffle", systemImage: "shuffle")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.primaryText)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(Theme.surfaceHigh, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(library.videos.isEmpty)
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 10)
        .padding(.bottom, 18)
    }

    private var totalBytes: Int64 {
        library.videos.reduce(0) { $0 + $1.fileSize }
    }

    private func sectionHeader(_ title: String, trailing: String? = nil,
                               action: (() -> Void)? = nil) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.primaryText)
            Spacer()
            if let trailing, let action {
                Button(trailing, action: action)
                    .font(.system(size: 13, weight: .medium))
            }
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    private func shelf(_ videos: [Video]) -> some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 10) {
                ForEach(videos.prefix(14)) { video in
                    VideoTile(video: video, width: 180, aspect: 16.0 / 9.0) {
                        playback.play(video, in: videos)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 18)
        }
        .scrollIndicators(.hidden)
    }
}

/// Reusable "see all" page.
struct VideoListPage: View {
    let title: String
    let videos: [Video]
    var allowsClear = false

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.bottomChrome) private var bottomChrome
    @State private var confirmingClear = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(videos) { video in
                    VideoRow(video: video, context: videos) {
                        playback.play(video, in: videos)
                    }
                }
                if videos.isEmpty {
                    EmptyStateView(symbol: "tray", title: "Nothing here yet", message: "Come back once you have watched something.")
                }
                Color.clear.frame(height: bottomChrome + 12)
            }
        }
        .scrollIndicators(.hidden)
        .background(Theme.background)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !videos.isEmpty {
                    Button {
                        playback.playAll(videos, shuffled: true)
                    } label: {
                        Image(systemName: "shuffle")
                    }
                }
                if allowsClear {
                    Button(role: .destructive) { confirmingClear = true } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .alert("Clear watch history?", isPresented: $confirmingClear) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) { library.clearHistory() }
        } message: {
            Text("Resume positions are reset too. Your video files are not touched.")
        }
    }
}
