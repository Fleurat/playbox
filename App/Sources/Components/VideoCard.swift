import SwiftUI

/// The big home-feed card: wide thumbnail, avatar, title, meta line, overflow menu.
struct VideoCard: View {
    let video: Video
    var context: [Video] = []
    var onPlay: () -> Void

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @State private var showingActions = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onPlay) {
                VideoThumbnail(video: video, cornerRadius: 0)
            }
            .buttonStyle(.plain)

            HStack(alignment: .top, spacing: 12) {
                Avatar(video: video)

                VStack(alignment: .leading, spacing: 3) {
                    Text(video.title)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.primaryText)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(video.source) · \(Format.subtitle(for: video))")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.secondaryText)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button { showingActions = true } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.primaryText)
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 14)
            .contentShape(Rectangle())
            .onTapGesture(perform: onPlay)
        }
        .sheet(isPresented: $showingActions) {
            VideoActionSheet(video: video, context: context)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .contextMenu {
            VideoContextMenu(video: video, context: context)
        }
    }
}

/// Compact horizontal row used in Library, search results and the queue.
struct VideoRow: View {
    let video: Video
    var context: [Video] = []
    var showsHandle = false
    var onPlay: () -> Void

    @State private var showingActions = false

    var body: some View {
        HStack(spacing: 10) {
            if showsHandle {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.secondaryText)
            }

            VideoThumbnail(video: video, cornerRadius: 8)
                .frame(width: 148)

            VStack(alignment: .leading, spacing: 3) {
                Text(video.title)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(Format.subtitle(for: video))
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button { showingActions = true } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.secondaryText)
                    .frame(width: 26, height: 34)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture(perform: onPlay)
        .sheet(isPresented: $showingActions) {
            VideoActionSheet(video: video, context: context)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .contextMenu { VideoContextMenu(video: video, context: context) }
    }
}

/// Poster tile for the horizontal shelves ("Continue watching", "Shorts").
struct VideoTile: View {
    let video: Video
    var width: CGFloat = 132
    var aspect: CGFloat = 9.0 / 16.0
    var onPlay: () -> Void

    var body: some View {
        Button(action: onPlay) {
            VStack(alignment: .leading, spacing: 6) {
                VideoThumbnail(video: video, aspect: aspect, cornerRadius: 10)
                    .frame(width: width)
                Text(video.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(width: width, alignment: .leading)
            }
        }
        .buttonStyle(.plain)
    }
}
