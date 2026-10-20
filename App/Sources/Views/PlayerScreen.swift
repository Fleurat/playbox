import AVFoundation
import SwiftUI
import UIKit

/// The full player.
///
/// Gestures on the video:
///   tap              → show / hide controls
///   double tap left  → back 10s     double tap right → forward 10s
///   hold             → 2× speed while held
///   pinch            → fit ⇄ fill
///   drag down        → shrink to the mini player   drag up → Up next
///   drag ↑↓ (landscape, left half)  → brightness   (right half) → volume
struct SeekFlashInfo: Equatable {
    var forward: Bool
    var seconds: Int
    var token = UUID()
}

struct PlayerScreen: View {
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var playback: PlaybackController

    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var dismissDrag: CGFloat = 0
    @State private var scrubTime: Double?
    @State private var scrubImage: UIImage?
    @State private var scrubImageTask: Task<Void, Never>?
    @State private var seekFlash: SeekFlashInfo?
    @State private var isBoosting = false
    @State private var showingQueue = false
    @State private var showingPicker = false
    @State private var showingActions = false
    @State private var scrubAnchor: Double = 0

    private var video: Video? {
        guard let current = playback.current else { return nil }
        return library.video(id: current.id) ?? current
    }

    var body: some View {
        GeometryReader { geo in
            let isLandscape = geo.size.width > geo.size.height

            ZStack {
                Color.black.ignoresSafeArea()

                if let video {
                    if isLandscape {
                        ZStack(alignment: .bottom) {
                            videoSurface(size: geo.size, landscape: true, video: video)
                                .ignoresSafeArea()

                            if showingPicker {
                                Color.black.opacity(0.55)
                                    .ignoresSafeArea()
                                    .onTapGesture {
                                        withAnimation(.easeOut(duration: 0.2)) { showingPicker = false }
                                    }
                                upNextPanel
                                    .transition(.move(edge: .bottom).combined(with: .opacity))
                            }
                        }
                    } else {
                        VStack(spacing: 0) {
                            videoSurface(
                                size: CGSize(width: geo.size.width, height: (geo.size.width * 9 / 16).rounded()),
                                landscape: false,
                                video: video
                            )
                            details(for: video)
                        }
                    }
                }
            }
            .offset(y: max(0, dismissDrag))
            .scaleEffect(1 - min(0.08, max(0, dismissDrag) / 2600), anchor: .top)
        }
        .background(Theme.background)
        .statusBarHidden(false)
        .sheet(isPresented: $showingQueue) { QueueSheet() }
        .sheet(isPresented: $showingActions) {
            if let video {
                VideoActionSheet(video: video, context: playback.queue)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
        }
        .onAppear { scheduleHide() }
        .onDisappear { hideTask?.cancel() }
        .onChange(of: playback.current?.id) { _, _ in
            showControls()
            scrubTime = nil
        }
    }

    // MARK: - Video surface

    private func videoSurface(size: CGSize, landscape: Bool, video: Video) -> some View {
        ZStack {
            Color.black

            PlayerLayerView(
                player: playback.player,
                gravity: playback.fillsScreen ? .resizeAspectFill : .resizeAspect
            )

            if playback.isBuffering && !playback.isPlaying {
                ProgressView()
                    .controlSize(.large)
                    .tint(.white)
            }

            if let seekFlash {
                SeekRipple(flash: seekFlash)
                    .id(seekFlash.token)
            }

            if isBoosting {
                Text("2× speed")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(.ultraThinMaterial, in: Capsule())
                    .frame(maxHeight: .infinity, alignment: .top)
                    .padding(.top, 24)
            }

            if controlsVisible {
                controlsOverlay(landscape: landscape, video: video)
                    .transition(.opacity)
            } else if playback.duration > 0 {
                // A hairline of progress stays visible once the controls fade.
                VStack {
                    Spacer()
                    ThinProgress(progress: playback.progress)
                }
            }

            if let scrubTime { scrubPreview(at: scrubTime, video: video) }
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .contentShape(Rectangle())
        .onTapGesture(count: 2, coordinateSpace: .local) { location in
            handleDoubleTap(at: location, width: size.width)
        }
        .onTapGesture { toggleControls() }
        .gesture(dragGesture(size: size, landscape: landscape))
        .simultaneousGesture(
            MagnifyGesture()
                .onChanged { value in
                    playback.fillsScreen = value.magnification > 1.06
                }
        )
        .onLongPressGesture(minimumDuration: 0.4, maximumDistance: 24) {
        } onPressingChanged: { pressing in
            guard playback.isPlaying || isBoosting else { return }
            withAnimation(.easeOut(duration: 0.15)) { isBoosting = pressing }
            playback.rate = pressing ? 2.0 : 1.0
        }
    }

    // MARK: - Controls

    private func controlsOverlay(landscape: Bool, video: Video) -> some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.6), .clear, .black.opacity(0.75)],
                           startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)

            VStack(spacing: 0) {
                topRow(video: video, landscape: landscape)
                Spacer()
                centerRow
                Spacer()
                bottomRow(landscape: landscape)
            }
            .padding(.horizontal, landscape ? 24 : 10)
            .padding(.vertical, landscape ? 14 : 8)
        }
    }

    private func topRow(video: Video, landscape: Bool) -> some View {
        HStack(spacing: 14) {
            Button {
                playback.minimize()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 17, weight: .semibold))
            }

            if landscape {
                Text(video.title)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
            }

            Spacer()

            Menu {
                Picker("Speed", selection: Binding(
                    get: { Double(playback.rate) },
                    set: { playback.rate = Float($0) }
                )) {
                    ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                        Text(speed == 1.0 ? "Normal" : "\(speed, specifier: "%g")×").tag(speed)
                    }
                }
                Button {
                    playback.repeatMode = nextRepeatMode()
                } label: {
                    Label(playback.repeatMode.label, systemImage: playback.repeatMode.symbol)
                }
                Toggle("Shuffle", isOn: Binding(
                    get: { playback.isShuffling },
                    set: { playback.isShuffling = $0 }
                ))
                Button {
                    playback.fillsScreen.toggle()
                } label: {
                    Label(playback.fillsScreen ? "Fit to screen" : "Fill screen",
                          systemImage: playback.fillsScreen ? "rectangle.arrowtriangle.2.inward" : "rectangle.arrowtriangle.2.outward")
                }
                Button { showingActions = true } label: {
                    Label("More options", systemImage: "ellipsis.circle")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 32, height: 32)
            }
        }
        .foregroundStyle(.white)
    }

    private var centerRow: some View {
        HStack(spacing: 44) {
            Button { playback.previous(); showControls() } label: {
                Image(systemName: "backward.end.fill").font(.system(size: 24))
            }
            .disabled(!playback.hasPrevious)
            .opacity(playback.hasPrevious ? 1 : 0.35)

            Button {
                playback.togglePlayPause()
                showControls()
            } label: {
                Image(systemName: playback.didReachEnd ? "arrow.counterclockwise"
                      : (playback.isPlaying ? "pause.fill" : "play.fill"))
                    .font(.system(size: 38))
                    .frame(width: 62, height: 62)
            }

            Button { playback.next(); showControls() } label: {
                Image(systemName: "forward.end.fill").font(.system(size: 24))
            }
            .disabled(!playback.hasNext)
            .opacity(playback.hasNext ? 1 : 0.35)
        }
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.4), radius: 6)
    }

    private func bottomRow(landscape: Bool) -> some View {
        VStack(spacing: 2) {
            HStack(spacing: 6) {
                Text(Format.duration(scrubTime ?? playback.currentTime))
                    .font(.system(size: 11, weight: .medium).monospacedDigit())
                Text("/")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.7))
                Text(Format.duration(playback.duration))
                    .font(.system(size: 11, weight: .medium).monospacedDigit())
                    .foregroundStyle(.white.opacity(0.7))

                Spacer()

                if playback.rate != 1.0 {
                    Text("\(playback.rate, specifier: "%g")×")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.accent)
                }

                Button {
                    toggleFullscreen(landscape: landscape)
                } label: {
                    Image(systemName: landscape
                          ? "arrow.down.right.and.arrow.up.left"
                          : "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 30, height: 30)
                }
            }
            .foregroundStyle(.white)

            Scrubber(
                progress: playback.progress,
                duration: playback.duration,
                onBegin: {
                    playback.isScrubbing = true
                    hideTask?.cancel()
                },
                onChange: { time in
                    scrubTime = time
                    requestScrubImage(at: time)
                },
                onEnd: { time in
                    playback.isScrubbing = false
                    playback.seek(to: time)
                    scrubTime = nil
                    scrubImage = nil
                    scheduleHide()
                }
            )
        }
    }

    // MARK: - Details (portrait)

    private func details(for video: Video) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(video.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.primaryText)
                    .padding(.horizontal, 14)
                    .padding(.top, 12)

                Text("\(Format.plays(video.plays)) · \(Format.duration(video.duration)) · \(Format.fileSize(video.fileSize))")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.secondaryText)
                    .padding(.horizontal, 14)
                    .padding(.top, 4)

                HStack(spacing: 10) {
                    Avatar(video: video, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(video.source)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.primaryText)
                        Text(video.dateAdded.formatted(date: .abbreviated, time: .omitted))
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.secondaryText)
                    }
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)

                actionRow(for: video)

                Divider().overlay(Theme.divider).padding(.vertical, 6)

                upNextSection
            }
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background)
    }

    private func actionRow(for video: Video) -> some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                pill(video.isLiked ? "hand.thumbsup.fill" : "hand.thumbsup",
                     video.isLiked ? "Liked" : "Like",
                     active: video.isLiked) {
                    library.toggleLike(video.id)
                }
                pill(playback.isShuffling ? "shuffle.circle.fill" : "shuffle", "Shuffle",
                     active: playback.isShuffling) {
                    playback.isShuffling.toggle()
                }
                pill(playback.repeatMode.symbol, playback.repeatMode == .off ? "Repeat" : playback.repeatMode.label,
                     active: playback.repeatMode != .off) {
                    playback.repeatMode = nextRepeatMode()
                }
                pill("dice", "Random") { playback.playRandom() }
                pill("list.bullet", "Queue") { showingQueue = true }
                pill("plus.rectangle.on.folder", "Save") { showingActions = true }
            }
            .padding(.horizontal, 14)
        }
        .scrollIndicators(.hidden)
    }

    private func pill(_ symbol: String, _ title: String, active: Bool = false,
                      action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol).font(.system(size: 13, weight: .semibold))
                Text(title).font(.system(size: 13, weight: .medium))
            }
            .foregroundStyle(active ? .black : Theme.primaryText)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(active ? Color.white : Theme.surfaceHigh, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var upNextSection: some View {
        let items = playback.upNext
        HStack {
            Text(playback.isShuffling ? "Up next · shuffled" : "Up next")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Theme.primaryText)
            Spacer()
            Button("Queue") { showingQueue = true }
                .font(.system(size: 13, weight: .medium))
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 8)

        if items.isEmpty {
            Text("Nothing queued. Swipe up on the video, or tap Random.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.secondaryText)
                .padding(.horizontal, 14)
        } else {
            ForEach(items.prefix(20)) { item in
                VideoRow(video: item, context: playback.queue) {
                    playback.jump(to: item)
                }
            }
        }
    }

    // MARK: - Fullscreen up next

    /// Horizontal strip over the landscape video: the queue first, then the
    /// rest of the library, so there is always something to pick. Its height
    /// wraps its content rather than taking a fraction of the screen — landscape
    /// has little height to spare, and one row of cards leaves the top of the
    /// video visible behind it.
    private var upNextPanel: some View {
        let queued = playback.upNext
        let queuedIDs = Set(queued.map(\.id))
        let others = library.videos.filter {
            $0.id != playback.current?.id && !queuedIDs.contains($0.id)
        }
        let picks = queued + others

        return VStack(spacing: 0) {
            VStack(spacing: 0) {
                Capsule()
                    .fill(Theme.secondaryText)
                    .frame(width: 36, height: 4)
                    .padding(.vertical, 8)

                HStack(spacing: 18) {
                    Text(queued.isEmpty ? "Other videos" : "Up next")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.primaryText)

                    Spacer()

                    Button {
                        playback.isShuffling.toggle()
                    } label: {
                        Image(systemName: "shuffle")
                            .foregroundStyle(playback.isShuffling ? Theme.accent : Theme.primaryText)
                    }
                    Button {
                        playback.playRandom()
                        closePicker()
                    } label: {
                        Image(systemName: "dice")
                            .foregroundStyle(Theme.primaryText)
                    }
                    Button(action: closePicker) {
                        Image(systemName: "xmark")
                            .foregroundStyle(Theme.primaryText)
                    }
                }
                .font(.system(size: 15, weight: .semibold))
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
            // Drag-to-dismiss sits on the handle and header only, so it can
            // never compete with the strip's horizontal scrolling.
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 10)
                    .onEnded { value in
                        if value.translation.height > 30 { closePicker() }
                    }
            )

            if picks.isEmpty {
                Text("Nothing else in your library yet.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.secondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 18)
            } else {
                ScrollView(.horizontal) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(picks) { item in
                            upNextCard(item, inQueue: queuedIDs.contains(item.id))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 14)
                }
                .scrollIndicators(.hidden)
            }
        }
        .background(Theme.background.opacity(0.96))
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 16, topTrailingRadius: 16))
    }

    private func upNextCard(_ item: Video, inQueue: Bool) -> some View {
        Button {
            if inQueue {
                playback.jump(to: item)
            } else {
                playback.play(item, in: library.videos)
            }
            closePicker()
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                VideoThumbnail(video: item, aspect: 16 / 9, cornerRadius: 10)
                    .frame(width: 172)
                Text(item.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Theme.primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(inQueue ? "In queue" : item.source)
                    .font(.system(size: 10))
                    .foregroundStyle(inQueue ? Theme.accent : Theme.secondaryText)
                    .lineLimit(1)
            }
            .frame(width: 172, alignment: .leading)
        }
        .buttonStyle(.plain)
    }

    private func closePicker() {
        withAnimation(.easeOut(duration: 0.2)) { showingPicker = false }
    }

    // MARK: - Gesture handling

    private func handleDoubleTap(at location: CGPoint, width: CGFloat) {
        let third = width / 3
        if location.x < third {
            playback.skip(by: -10)
            seekFlash = SeekFlashInfo(forward: false, seconds: 10)
        } else if location.x > width - third {
            playback.skip(by: 10)
            seekFlash = SeekFlashInfo(forward: true, seconds: 10)
        } else {
            playback.togglePlayPause()
            showControls()
            return
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task {
            try? await Task.sleep(for: .milliseconds(650))
            seekFlash = nil
        }
    }

    private func dragGesture(size: CGSize, landscape: Bool) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                let horizontal = abs(value.translation.width) > abs(value.translation.height) * 1.2

                if horizontal {
                    // Drag sideways anywhere on the video to scrub.
                    guard playback.duration > 0 else { return }
                    if !playback.isScrubbing {
                        playback.isScrubbing = true
                        scrubAnchor = playback.currentTime
                        hideTask?.cancel()
                        showControls(auto: false)
                    }
                    let span = max(playback.duration * 0.6, 30)
                    let delta = (value.translation.width / size.width) * span
                    let target = min(max(0, scrubAnchor + delta), playback.duration)
                    scrubTime = target
                    requestScrubImage(at: target)
                    return
                }

                if landscape {
                    // Accumulate; the decision happens on release.
                    dismissDrag = value.translation.height
                } else if value.translation.height > 0 {
                    dismissDrag = value.translation.height
                }
            }
            .onEnded { value in
                if playback.isScrubbing, let target = scrubTime {
                    playback.isScrubbing = false
                    playback.seek(to: target)
                    scrubTime = nil
                    scrubImage = nil
                    scheduleHide()
                    return
                }

                if landscape {
                    // Same vertical grammar as the Shorts feed and the Android
                    // build: up reveals the strip, down steps back a video.
                    let predicted = value.predictedEndTranslation.height
                    if predicted < -90 {
                        withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) {
                            showingPicker = true
                        }
                    } else if predicted > 90 {
                        playback.previousTrack()
                        showControls()
                    }
                    dismissDrag = 0
                    return
                }

                if value.predictedEndTranslation.height > 140 || dismissDrag > size.height * 0.45 {
                    playback.minimize()
                    dismissDrag = 0
                } else if value.predictedEndTranslation.height < -120 {
                    // Swipe up on the player opens what's coming next.
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dismissDrag = 0 }
                    showingQueue = true
                } else {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dismissDrag = 0 }
                }
            }
    }

    // MARK: - Helpers

    private func toggleControls() {
        withAnimation(.easeOut(duration: 0.18)) { controlsVisible.toggle() }
        if controlsVisible { scheduleHide() } else { hideTask?.cancel() }
    }

    private func showControls(auto: Bool = true) {
        withAnimation(.easeOut(duration: 0.18)) { controlsVisible = true }
        if auto { scheduleHide() }
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(for: .seconds(3.5))
            guard !Task.isCancelled, playback.isPlaying, !playback.isScrubbing else { return }
            withAnimation(.easeOut(duration: 0.25)) { controlsVisible = false }
        }
    }

    private func nextRepeatMode() -> PlaybackController.RepeatMode {
        switch playback.repeatMode {
        case .off: return .all
        case .all: return .one
        case .one: return .off
        }
    }

    private func toggleFullscreen(landscape: Bool) {
        playback.wantsLandscape = !landscape
        OrientationGate.request(landscape: !landscape)
    }

    /// Throttled frame grab for the bubble above the scrubber.
    private func requestScrubImage(at time: Double) {
        guard let video else { return }
        scrubImageTask?.cancel()
        scrubImageTask = Task {
            try? await Task.sleep(for: .milliseconds(90))
            guard !Task.isCancelled else { return }
            let image = await ThumbnailStore.shared.frame(for: video, at: time)
            guard !Task.isCancelled else { return }
            scrubImage = image
        }
    }

    private func scrubPreview(at time: Double, video: Video) -> some View {
        VStack(spacing: 6) {
            Group {
                if let scrubImage {
                    Image(uiImage: scrubImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    Rectangle().fill(Theme.surface)
                }
            }
            .frame(width: 150, height: 84)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(.white.opacity(0.8), lineWidth: 1.5))

            Text(Format.duration(time))
                .font(.system(size: 12, weight: .bold).monospacedDigit())
                .foregroundStyle(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(.black.opacity(0.7), in: Capsule())
        }
        .allowsHitTesting(false)
        .transition(.opacity)
    }
}

/// The circular "+10s" splash behind a double tap.
private struct SeekRipple: View {
    let flash: SeekFlashInfo

    @State private var animate = false

    var body: some View {
        HStack {
            if flash.forward { Spacer() }
            VStack(spacing: 4) {
                Image(systemName: flash.forward ? "goforward.10" : "gobackward.10")
                    .font(.system(size: 28, weight: .semibold))
                Text("\(flash.seconds) seconds")
                    .font(.system(size: 11, weight: .medium))
            }
            .foregroundStyle(.white)
            .frame(width: 120, height: 120)
            .background(.white.opacity(animate ? 0 : 0.18), in: Circle())
            .scaleEffect(animate ? 1.15 : 0.9)
            .opacity(animate ? 0 : 1)
            if !flash.forward { Spacer() }
        }
        .padding(.horizontal, 12)
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.easeOut(duration: 0.55)) { animate = true }
        }
    }
}

enum OrientationGate {
    /// Asks the scene to rotate. Info.plist allows both, so the request is honoured.
    static func request(landscape: Bool) {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first else { return }
        let mask: UIInterfaceOrientationMask = landscape ? .landscapeRight : .portrait
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask))
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
    }
}
