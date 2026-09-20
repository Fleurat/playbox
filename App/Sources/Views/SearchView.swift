import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""
    @FocusState private var focused: Bool

    private var results: [Video] { library.search(query) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.secondaryText)
                        TextField("Search your videos", text: $query)
                            .focused($focused)
                            .submitLabel(.search)
                            .autocorrectionDisabled()
                            .foregroundStyle(Theme.primaryText)
                        if !query.isEmpty {
                            Button { query = "" } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundStyle(Theme.secondaryText)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(Theme.surfaceHigh, in: Capsule())

                    Button("Cancel") { dismiss() }
                        .font(.system(size: 14))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)

                ScrollView {
                    LazyVStack(spacing: 0) {
                        if query.isEmpty {
                            suggestions
                        } else if results.isEmpty {
                            EmptyStateView(symbol: "magnifyingglass",
                                           title: "No results",
                                           message: "Nothing in your library matches “\(query)”.")
                        } else {
                            ForEach(results) { video in
                                VideoRow(video: video, context: results) {
                                    playback.play(video, in: results)
                                    dismiss()
                                }
                            }
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }
            .background(Theme.background)
            .navigationBarHidden(true)
        }
        .onAppear { focused = true }
    }

    @ViewBuilder
    private var suggestions: some View {
        let recent = Array(library.historyVideos.prefix(8))
        if !recent.isEmpty {
            HStack {
                Text("Recently played")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.primaryText)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)

            ForEach(recent) { video in
                Button {
                    query = video.title
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.secondaryText)
                        Text(video.title)
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.primaryText)
                            .lineLimit(1)
                        Spacer()
                        Image(systemName: "arrow.up.left")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.secondaryText)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}
