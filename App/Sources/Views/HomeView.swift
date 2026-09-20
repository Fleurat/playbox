import SwiftUI

struct HomeView: View {
    enum Filter: String, CaseIterable, Identifiable {
        case all = "All"
        case new = "New"
        case continueWatching = "Continue"
        case liked = "Liked"
        case unwatched = "Unwatched"
        case longest = "Longest"
        case mostPlayed = "Most played"
        var id: String { rawValue }
    }

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.bottomChrome) private var bottomChrome

    @State private var filter: Filter = .all
    @State private var showingSearch = false

    private var feed: [Video] {
        switch filter {
        case .all: return library.recent
        case .new: return library.recent.filter { $0.dateAdded > Date().addingTimeInterval(-60 * 60 * 24 * 14) }
        case .continueWatching: return library.continueWatching
        case .liked: return library.liked
        case .unwatched: return library.unwatched
        case .longest: return library.longest
        case .mostPlayed: return library.mostPlayed
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(showingSearch: $showingSearch)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: []) {
                    chips

                    if library.videos.isEmpty {
                        EmptyStateView(
                            symbol: "tray.and.arrow.down",
                            title: "No videos yet",
                            message: "Tap + below to bring videos in from Photos or Files. You can also drop files into the Playbox folder in the Files app."
                        )
                    } else {
                        if filter == .all {
                            continueShelf
                            shortsShelf
                        }

                        ForEach(feed) { video in
                            VideoCard(video: video, context: feed) {
                                playback.play(video, in: feed)
                            }
                        }

                        if feed.isEmpty {
                            EmptyStateView(symbol: "line.3.horizontal.decrease.circle",
                                           title: "Nothing here",
                                           message: "No videos match “\(filter.rawValue)”.")
                        }
                    }

                    Color.clear.frame(height: bottomChrome + 12)
                }
            }
            .refreshable { await library.rescan() }
            .scrollIndicators(.hidden)
        }
        .background(Theme.background)
        .sheet(isPresented: $showingSearch) { SearchView() }
    }

    private var chips: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(Filter.allCases) { option in
                    Button {
                        withAnimation(.easeOut(duration: 0.15)) { filter = option }
                    } label: {
                        Text(option.rawValue)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(filter == option ? .black : Theme.primaryText)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(filter == option ? Color.white : Theme.surfaceHigh, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .scrollIndicators(.hidden)
    }

    @ViewBuilder
    private var continueShelf: some View {
        let items = library.continueWatching
        if !items.isEmpty {
            shelfHeader("Continue watching", symbol: "clock.arrow.circlepath")
            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: 10) {
                    ForEach(items) { video in
                        VideoTile(video: video, width: 200, aspect: 16.0 / 9.0) {
                            playback.play(video, in: items)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 14)
            }
            .scrollIndicators(.hidden)
        }
    }

    @ViewBuilder
    private var shortsShelf: some View {
        let items = library.videos.filter(\.qualifiesAsShort)
        if items.count >= 2 {
            shelfHeader("Shorts", symbol: "play.square.stack.fill")
            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: 10) {
                    ForEach(items.prefix(12)) { video in
                        VideoTile(video: video) {
                            playback.play(video, in: items)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 14)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func shelfHeader(_ title: String, symbol: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.system(size: 15))
                .foregroundStyle(Theme.accent)
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.primaryText)
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }
}

struct TopBar: View {
    @Binding var showingSearch: Bool
    @EnvironmentObject private var library: LibraryStore

    var body: some View {
        HStack(spacing: 14) {
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Theme.accent)
                    .frame(width: 26, height: 19)
                    .overlay {
                        Image(systemName: "play.fill")
                            .font(.system(size: 9, weight: .black))
                            .foregroundStyle(.white)
                    }
                Text("Playbox")
                    .font(.system(size: 19, weight: .heavy))
                    .foregroundStyle(Theme.primaryText)
                    .kerning(-0.6)
            }

            Spacer()

            if library.isScanning {
                ProgressView().scaleEffect(0.7)
            }

            Button { showingSearch = true } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Theme.background)
    }
}
