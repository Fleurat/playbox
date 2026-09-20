import AVFoundation
import SwiftUI
import UIKit

/// Full-screen swipe feed.
///
/// Swipe up  → next video (random when shuffle is on)
/// Swipe down → the video you just came from
/// Tap        → pause / resume
/// Double tap → like
/// Hold       → 2× speed while held
struct ShortsView: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var feed = ShortsFeed()
    @State private var dragOffset: CGFloat = 0
    @State private var isSettling = false
    @State private var isOnScreen = false
    @AppStorage("didSeeSwipeHint") private var didSeeSwipeHint = false
    @State private var showingHint = false

    var body: some View {
        GeometryReader { geo in
            let height = geo.size.height

            ZStack {
                Color.black

                if feed.isEmpty {
                    EmptyStateView(
                        symbol: "play.square.stack",
                        title: "Nothing to swipe through",
                        message: "Import a few videos and they show up here. Portrait clips and anything under 90 seconds land in Shorts."
                    )
                } else {
                    if let previous = feed.previous {
                        page(previous, active: false, height: height)
                            .offset(y: -height + dragOffset)
                    }
                    if let current = feed.current {
                        page(current, active: isOnScreen && !isSettling, height: height)
                            .offset(y: dragOffset)
                    }
                    if let next = feed.next {
                        page(next, active: false, height: height)
                            .offset(y: height + dragOffset)
                    }
                }
            }
            .frame(width: geo.size.width, height: height)
            .clipped()
            .contentShape(Rectangle())
            .simultaneousGesture(swipe(height: height))
            .overlay(alignment: .top) { header(height: height) }
            .overlay { if showingHint { swipeHint } }
        }
        .ignoresSafeArea()
        .onAppear {
            isOnScreen = true
            playback.suspendForShorts()
            feed.refresh(with: library.shorts)
            if !didSeeSwipeHint, !library.shorts.isEmpty {
                showingHint = true
                Task {
                    try? await Task.sleep(for: .seconds(3.5))
                    withAnimation(.easeOut(duration: 0.4)) { showingHint = false }
                    didSeeSwipeHint = true
                }
            }
        }
        .onDisappear { isOnScreen = false }
        .onChange(of: library.videos) { _, _ in feed.refresh(with: library.shorts) }
        .onChange(of: scenePhase) { _, phase in isOnScreen = (phase == .active) }
        .onChange(of: playback.presentation) { _, value in
            // The full player owns the audio session while it is up.
            isOnScreen = (value != .full)
        }
    }

    // MARK: - Pages

    private func page(_ video: Video, active: Bool, height: CGFloat) -> some View {
        ShortPage(video: video,
                  isActive: active,
                  isMuted: feed.isMuted,
                  onRequestNext: { advance(height: height) })
            .frame(height: height)
            .id(video.id)
    }

    // MARK: - Gesture

    private func swipe(height: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                guard !isSettling else { return }
                // Only take over once the drag is clearly vertical, so the
                // action rail and horizontal content still behave.
                guard abs(value.translation.height) > abs(value.translation.width) else { return }
                var translation = value.translation.height
                let atEdge = (translation > 0 && feed.previous == nil) || (translation < 0 && feed.next == nil)
                if atEdge { translation *= 0.28 }      // rubber-band at the ends
                dragOffset = translation
            }
            .onEnded { value in
                guard !isSettling else { return }
                let predicted = value.predictedEndTranslation.height
                let threshold = height * 0.14

                if predicted < -threshold, feed.next != nil {
                    settle(to: -height) { feed.advance() }
                } else if predicted > threshold, feed.previous != nil {
                    settle(to: height) { feed.goBack() }
                } else {
                    withAnimation(.interpolatingSpring(stiffness: 240, damping: 28)) { dragOffset = 0 }
                }
            }
    }

    /// Slides the page fully off, then swaps the feed position and snaps back to
    /// zero in the same frame — so the swap is invisible.
    private func settle(to offset: CGFloat, then commit: @escaping () -> Void) {
        isSettling = true
        withAnimation(.interpolatingSpring(stiffness: 240, damping: 28)) {
            dragOffset = offset
        } completion: {
            commit()
            dragOffset = 0
            isSettling = false
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }

    private func advance(height: CGFloat) {
        guard feed.next != nil else { return }
        settle(to: -height) { feed.advance() }
    }

    // MARK: - Chrome

    private func header(height: CGFloat) -> some View {
        HStack(spacing: 16) {
            Text("Shorts")
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(.white)

            Spacer()

            Button {
                feed.isShuffling.toggle()
                UISelectionFeedbackGenerator().selectionChanged()
            } label: {
                Image(systemName: "shuffle")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(feed.isShuffling ? Theme.accent : .white)
                    .frame(width: 34, height: 34)
                    .background(Color.black.opacity(0.35), in: Circle())
            }
            .buttonStyle(.plain)

            Button {
                feed.stageRandomNext()
                advance(height: height)
            } label: {
                Image(systemName: "dice")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Color.black.opacity(0.35), in: Circle())
            }
            .buttonStyle(.plain)

            Button {
                feed.isMuted.toggle()
            } label: {
                Image(systemName: feed.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Color.black.opacity(0.35), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 56)
        .shadow(color: .black.opacity(0.5), radius: 6)
    }

    private var swipeHint: some View {
        VStack(spacing: 10) {
            Image(systemName: "hand.draw")
                .font(.system(size: 30, weight: .light))
            Text("Swipe up for the next video")
                .font(.system(size: 15, weight: .semibold))
            Text("Swipe down to go back · Shuffle is on")
                .font(.system(size: 12))
                .foregroundStyle(.white.opacity(0.75))
        }
        .foregroundStyle(.white)
        .padding(.vertical, 20)
        .padding(.horizontal, 26)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .transition(.opacity)
        .allowsHitTesting(false)
    }
}
