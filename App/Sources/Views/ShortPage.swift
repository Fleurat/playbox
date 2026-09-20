import AVFoundation
import Combine
import SwiftUI
import UIKit

/// One full-screen page of the swipe feed.
struct ShortPage: View {
    let video: Video
    let isActive: Bool
    let isMuted: Bool
    var onRequestNext: () -> Void

    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @StateObject private var box = ShortPlayerBox()

    @State private var showingPauseGlyph = false
    @State private var heartBurst = false
    @State private var isHolding = false
    @State private var showingActions = false

    private var live: Video { library.video(id: video.id) ?? video }

    var body: some View {
        ZStack {
            Color.black

            PlayerLayerView(player: box.player, gravity: .resizeAspectFill)
                .allowsHitTesting(false)

            // Keeps the title and rail readable over bright footage.
            LinearGradient(colors: [.black.opacity(0.45), .clear, .clear, .black.opacity(0.65)],
                           startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)

            if showingPauseGlyph {
                Image(systemName: "play.fill")
                    .font(.system(size: 62))
                    .foregroundStyle(.white.opacity(0.85))
                    .shadow(radius: 12)
                    .transition(.scale(scale: 0.7).combined(with: .opacity))
                    .allowsHitTesting(false)
            }

            if heartBurst {
                Image(systemName: "hand.thumbsup.fill")
                    .font(.system(size: 88))
                    .foregroundStyle(Theme.accent)
                    .shadow(radius: 14)
                    .transition(.scale(scale: 0.4).combined(with: .opacity))
                    .allowsHitTesting(false)
            }

            if isHolding {
                Text("2×")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial, in: Capsule())
                    .offset(y: -220)
                    .allowsHitTesting(false)
            }

            actionRail
            metadata
            progressBar
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2) { likeWithBurst() }
        .onTapGesture { togglePlayback() }
        .onLongPressGesture(minimumDuration: 0.35, maximumDistance: 30) {
            // fires when the press completes; speed is driven from onPressingChanged
        } onPressingChanged: { pressing in
            guard isActive else { return }
            withAnimation(.easeOut(duration: 0.15)) { isHolding = pressing }
            box.setRate(pressing ? 2.0 : 1.0)
        }
        .task(id: video.id) {
            box.setMuted(isMuted)
            box.load(video)
            if isActive {
                box.play()
                library.recordPlay(video.id)
            }
        }
        .onChange(of: isActive) { _, active in
            if active {
                box.play()
                library.recordPlay(video.id)
            } else {
                box.pause()
            }
        }
        .onChange(of: isMuted) { _, muted in box.setMuted(muted) }
        .onDisappear { box.pause() }
        .sheet(isPresented: $showingActions) {
            VideoActionSheet(video: live)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - Interaction

    private func togglePlayback() {
        guard isActive else { return }
        box.toggle()
        withAnimation(.easeOut(duration: 0.18)) { showingPauseGlyph = box.isPaused }
    }

    private func likeWithBurst() {
        library.setLiked(video.id, true)
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        withAnimation(.spring(response: 0.28, dampingFraction: 0.5)) { heartBurst = true }
        Task {
            try? await Task.sleep(for: .milliseconds(550))
            withAnimation(.easeOut(duration: 0.25)) { heartBurst = false }
        }
    }

    // MARK: - Overlays

    private var actionRail: some View {
        VStack {
            Spacer()
            VStack(spacing: 20) {
                railButton(live.isLiked ? "hand.thumbsup.fill" : "hand.thumbsup",
                           tint: live.isLiked ? Theme.accent : .white,
                           caption: Format.compact(live.plays)) {
                    library.toggleLike(live.id)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }

                railButton("arrow.down.to.line", caption: "Next") {
                    onRequestNext()
                }

                ShareLink(item: live.url) {
                    VStack(spacing: 4) {
                        Image(systemName: "arrowshape.turn.up.right.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(.white)
                        Text("Share").font(.system(size: 11, weight: .medium)).foregroundStyle(.white)
                    }
                }

                railButton("ellipsis", caption: "More") { showingActions = true }

                Avatar(video: live, size: 40)
                    .overlay(Circle().stroke(.white, lineWidth: 1.5))
            }
            .padding(.trailing, 12)
            .padding(.bottom, 130)
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .shadow(color: .black.opacity(0.45), radius: 5)
    }

    private func railButton(_ symbol: String, tint: Color = .white, caption: String,
                            action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: symbol)
                    .font(.system(size: 24))
                    .foregroundStyle(tint)
                    .frame(height: 26)
                Text(caption)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white)
            }
            .frame(width: 54)
        }
        .buttonStyle(.plain)
    }

    private var metadata: some View {
        VStack {
            Spacer()
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Avatar(video: live, size: 26)
                    Text(live.source)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                }
                Text(live.title)
                    .font(.system(size: 14))
                    .foregroundStyle(.white)
                    .lineLimit(2)

                Button {
                    playback.play(live, in: library.videos)
                } label: {
                    Label("Open in player", systemImage: "rectangle.expand.vertical")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.white.opacity(0.18), in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 16)
            .padding(.trailing, 80)
            .padding(.bottom, 130)
        }
        .shadow(color: .black.opacity(0.5), radius: 5)
    }

    private var progressBar: some View {
        VStack {
            Spacer()
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(.white.opacity(0.25))
                    Rectangle()
                        .fill(.white)
                        .frame(width: geo.size.width * box.progress)
                }
            }
            .frame(height: 2)
            .padding(.bottom, 102)
        }
        .allowsHitTesting(false)
    }
}

/// A looping player scoped to one page of the feed.
@MainActor
final class ShortPlayerBox: ObservableObject {
    let player = AVQueuePlayer()

    @Published private(set) var isPaused = false
    @Published private(set) var progress: Double = 0

    private var looper: AVPlayerLooper?
    private var observer: Any?
    private var loadedID: UUID?
    private var duration: Double = 0

    init() {
        player.actionAtItemEnd = .advance
        let interval = CMTime(seconds: 0.1, preferredTimescale: 600)
        observer = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self, self.duration > 0 else { return }
                let seconds = CMTimeGetSeconds(time)
                guard seconds.isFinite else { return }
                self.progress = min(1, max(0, seconds / self.duration))
            }
        }
    }

    deinit {
        // AVPlayer keeps decoding until it is explicitly emptied.
        if let observer { player.removeTimeObserver(observer) }
        player.pause()
        player.removeAllItems()
    }

    func load(_ video: Video) {
        guard loadedID != video.id else { return }
        loadedID = video.id
        progress = 0
        duration = video.duration

        let item = AVPlayerItem(url: video.url)
        looper = AVPlayerLooper(player: player, templateItem: item)
        player.pause()
        isPaused = false

        if duration <= 0 {
            Task { [weak self] in
                guard let total = try? await item.asset.load(.duration) else { return }
                let seconds = CMTimeGetSeconds(total)
                guard seconds.isFinite, seconds > 0 else { return }
                await MainActor.run { self?.duration = seconds }
            }
        }
    }

    func play() {
        isPaused = false
        player.play()
    }

    func pause() {
        player.pause()
    }

    func toggle() {
        isPaused.toggle()
        isPaused ? player.pause() : player.play()
    }

    func setRate(_ rate: Float) {
        guard !isPaused else { return }
        player.rate = rate
    }

    func setMuted(_ muted: Bool) {
        player.isMuted = muted
    }
}
