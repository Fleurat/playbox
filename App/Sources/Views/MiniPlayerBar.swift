import SwiftUI

/// The docked bar that appears when the full player is swiped down.
/// Tap to expand, swipe down again to close, swipe up to expand.
struct MiniPlayerBar: View {
    static let height: CGFloat = 58

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @State private var offset: CGFloat = 0

    private var video: Video? {
        guard let current = playback.current else { return nil }
        return library.video(id: current.id) ?? current
    }

    var body: some View {
        if let video {
            VStack(spacing: 0) {
                ThinProgress(progress: playback.progress)

                HStack(spacing: 10) {
                    PlayerLayerView(player: playback.player, gravity: .resizeAspectFill)
                        .frame(width: 84, height: 47)
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

                    VStack(alignment: .leading, spacing: 1) {
                        Text(video.title)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Theme.primaryText)
                            .lineLimit(1)
                        Text(video.source)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.secondaryText)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Button { playback.togglePlayPause() } label: {
                        Image(systemName: playback.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 17))
                            .foregroundStyle(Theme.primaryText)
                            .frame(width: 36, height: 44)
                    }
                    .buttonStyle(.plain)

                    Button { playback.next() } label: {
                        Image(systemName: "forward.end.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(playback.hasNext ? Theme.primaryText : Theme.secondaryText)
                            .frame(width: 32, height: 44)
                    }
                    .buttonStyle(.plain)
                    .disabled(!playback.hasNext)

                    Button { playback.close() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.primaryText)
                            .frame(width: 34, height: 44)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 8)
                .frame(height: Self.height)
            }
            .background(Theme.surface)
            .overlay(alignment: .top) { Rectangle().fill(Theme.divider).frame(height: 0.5) }
            .offset(y: offset)
            .contentShape(Rectangle())
            .onTapGesture { playback.expand() }
            .gesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { value in
                        offset = value.translation.height > 0 ? value.translation.height : value.translation.height * 0.2
                    }
                    .onEnded { value in
                        if value.predictedEndTranslation.height > 70 {
                            playback.close()
                        } else if value.predictedEndTranslation.height < -50 {
                            playback.expand()
                        }
                        withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) { offset = 0 }
                    }
            )
        }
    }
}

/// "Up next" as a sheet — reorderable, removable, with the shuffle/repeat toggles.
struct QueueSheet: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 10) {
                        toggle("shuffle", "Shuffle", on: playback.isShuffling) {
                            playback.isShuffling.toggle()
                        }
                        toggle(playback.repeatMode.symbol, "Repeat", on: playback.repeatMode != .off) {
                            playback.repeatMode = playback.repeatMode == .off ? .all
                                : (playback.repeatMode == .all ? .one : .off)
                        }
                        toggle("dice", "Random now", on: false) {
                            playback.playRandom()
                            dismiss()
                        }
                    }
                    .listRowBackground(Color.clear)
                }

                Section("Playing") {
                    if let current = playback.current {
                        VideoRow(video: current, context: playback.queue) {}
                            .listRowInsets(EdgeInsets())
                    }
                }

                Section("Up next (\(playback.upNext.count))") {
                    if playback.upNext.isEmpty {
                        Text("Queue is empty").foregroundStyle(Theme.secondaryText)
                    }
                    ForEach(playback.upNext) { video in
                        VideoRow(video: video, context: playback.queue) {
                            playback.jump(to: video)
                            dismiss()
                        }
                        .listRowInsets(EdgeInsets())
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Done") { dismiss() } }
            }
        }
    }

    private func toggle(_ symbol: String, _ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: symbol).font(.system(size: 15, weight: .semibold))
                Text(title).font(.system(size: 11))
            }
            .foregroundStyle(on ? .black : Theme.primaryText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(on ? Color.white : Theme.surfaceHigh,
                        in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
