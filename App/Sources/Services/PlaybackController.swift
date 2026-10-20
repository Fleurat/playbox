import AVFoundation
import Combine
import MediaPlayer
import UIKit

/// Owns the one AVPlayer behind the full-screen player and the mini player,
/// plus the queue that drives autoplay and "Up next".
@MainActor
final class PlaybackController: ObservableObject {
    enum Presentation: Equatable { case hidden, mini, full }
    enum RepeatMode: String, CaseIterable, Identifiable {
        case off, all, one
        var id: String { rawValue }
        var symbol: String { self == .one ? "repeat.1" : "repeat" }
        var label: String {
            switch self {
            case .off: return "Repeat off"
            case .all: return "Repeat queue"
            case .one: return "Repeat one"
            }
        }
    }

    let player = AVPlayer()

    @Published private(set) var current: Video?
    @Published var presentation: Presentation = .hidden
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var didReachEnd = false
    @Published var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var queue: [Video] = []
    @Published private(set) var queueIndex: Int = 0

    @Published var rate: Float = 1.0 { didSet { if isPlaying { player.rate = rate } } }
    @Published var repeatMode: RepeatMode = .off
    @Published var isShuffling = false { didSet { refillBag() } }
    @Published var isMuted = false { didSet { player.isMuted = isMuted } }
    @Published var volume: Float = 1.0 { didSet { player.volume = volume } }
    @Published var isScrubbing = false
    /// Pinch-to-zoom between letterboxed and filled, as the real thing does.
    @Published var fillsScreen = false
    @Published var wantsLandscape = false

    private weak var library: LibraryStore?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var cancellables: Set<AnyCancellable> = []
    private var shuffleBag: [Video] = []
    private var lastSavedPosition: Double = 0

    var upNext: [Video] {
        guard !queue.isEmpty, queueIndex + 1 < queue.count else { return [] }
        return Array(queue[(queueIndex + 1)...])
    }

    var hasPrevious: Bool { queueIndex > 0 || currentTime > 3 }
    var hasNext: Bool { isShuffling || queueIndex + 1 < queue.count || repeatMode == .all }

    var progress: Double {
        guard duration > 0 else { return 0 }
        return min(1, max(0, currentTime / duration))
    }

    init(library: LibraryStore) {
        self.library = library
        configureAudioSession()
        observePlayer()
        configureRemoteCommands()
    }

    deinit {
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let timeObserver { player.removeTimeObserver(timeObserver) }
    }

    // MARK: - Wiring

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
    }

    private func observePlayer() {
        let interval = CMTime(seconds: 0.2, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in self?.tick(time) }
        }

        player.publisher(for: \.timeControlStatus)
            .sink { [weak self] status in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.isPlaying = status == .playing
                    self.isBuffering = status == .waitingToPlayAtSpecifiedRate
                    self.updateNowPlaying()
                }
            }
            .store(in: &cancellables)

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main
        ) { [weak self] note in
            Task { @MainActor [weak self] in
                guard let self, let item = note.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.handleItemEnded()
            }
        }
    }

    private func tick(_ time: CMTime) {
        let seconds = CMTimeGetSeconds(time)
        guard seconds.isFinite else { return }
        if !isScrubbing { currentTime = seconds }

        if duration <= 0, let itemDuration = player.currentItem?.duration {
            let total = CMTimeGetSeconds(itemDuration)
            if total.isFinite, total > 0 { duration = total }
        }

        // Persist a resume point occasionally rather than on every tick.
        if abs(seconds - lastSavedPosition) > 5 {
            lastSavedPosition = seconds
            savePosition()
        }
        updateNowPlayingElapsed()
    }

    // MARK: - Starting playback

    /// `context` is the list the video was tapped in — it becomes the queue, so
    /// autoplay walks the same shelf the user was browsing.
    func play(_ video: Video, in context: [Video] = [], startAt: Double? = nil, fullScreen: Bool = true) {
        var newQueue = context.isEmpty ? [video] : context
        if !newQueue.contains(where: { $0.id == video.id }) {
            newQueue.insert(video, at: 0)
        }
        if newQueue.count == 1, let library {
            newQueue += library.relatedTo(video)
        }
        queue = newQueue
        queueIndex = newQueue.firstIndex { $0.id == video.id } ?? 0
        refillBag()
        load(video, startAt: startAt)
        if fullScreen { presentation = .full }
        else if presentation == .hidden { presentation = .mini }
    }

    func playAll(_ videos: [Video], shuffled: Bool = false) {
        guard !videos.isEmpty else { return }
        isShuffling = shuffled
        let first = shuffled ? (videos.randomElement() ?? videos[0]) : videos[0]
        play(first, in: videos, startAt: 0)
    }

    private func load(_ video: Video, startAt: Double?) {
        savePosition()

        current = video
        didReachEnd = false
        duration = video.duration
        isBuffering = true

        let item = AVPlayerItem(url: video.url)
        player.replaceCurrentItem(with: item)
        player.isMuted = isMuted

        let start = startAt ?? resumePoint(for: video)
        currentTime = start
        lastSavedPosition = start
        if start > 0.5 {
            player.seek(to: CMTime(seconds: start, preferredTimescale: 600),
                        toleranceBefore: .zero, toleranceAfter: .zero)
        }
        player.playImmediately(atRate: rate)

        library?.recordPlay(video.id)
        Task { await resolveDuration(for: item) }
        updateNowPlaying()
    }

    /// Resume unless the video was basically finished, in which case start over.
    private func resumePoint(for video: Video) -> Double {
        guard video.resumeTime > 5, !video.isFinished else { return 0 }
        return video.resumeTime
    }

    private func resolveDuration(for item: AVPlayerItem) async {
        guard let loaded = try? await item.asset.load(.duration) else { return }
        let seconds = CMTimeGetSeconds(loaded)
        guard seconds.isFinite, seconds > 0, item === player.currentItem else { return }
        duration = seconds
        updateNowPlaying()
    }

    // MARK: - Transport

    func play() {
        if didReachEnd { restart(); return }
        player.playImmediately(atRate: rate)
    }

    func pause() {
        player.pause()
        savePosition()
    }

    func togglePlayPause() { isPlaying ? pause() : play() }

    func restart() {
        didReachEnd = false
        seek(to: 0)
        player.playImmediately(atRate: rate)
    }

    func seek(to seconds: Double) {
        let clamped = max(0, duration > 0 ? min(seconds, duration) : seconds)
        currentTime = clamped
        didReachEnd = false
        player.seek(to: CMTime(seconds: clamped, preferredTimescale: 600),
                    toleranceBefore: .zero, toleranceAfter: .zero)
        updateNowPlayingElapsed()
    }

    /// The ±10s double-tap.
    func skip(by delta: Double) {
        seek(to: currentTime + delta)
    }

    func next(auto: Bool = false) {
        guard !queue.isEmpty else { return }

        if isShuffling {
            loadNextFromBag()
            return
        }
        if queueIndex + 1 < queue.count {
            queueIndex += 1
            load(queue[queueIndex], startAt: 0)
        } else if repeatMode == .all || !auto {
            queueIndex = 0
            load(queue[0], startAt: 0)
        } else {
            didReachEnd = true
            player.pause()
            savePosition()
        }
    }

    /// First press rewinds, a second press within 3s goes back a video — as YouTube behaves.
    func previous() {
        guard !queue.isEmpty else { return }
        if currentTime > 3 {
            seek(to: 0)
            return
        }
        if queueIndex > 0 {
            queueIndex -= 1
            load(queue[queueIndex], startAt: 0)
        } else {
            seek(to: 0)
        }
    }

    /// Swipe-down in fullscreen: always the previous video, never a rewind.
    /// `previous()` keeps its rewind-first behaviour, which is right for a button.
    func previousTrack() {
        guard !queue.isEmpty else { return }
        if queueIndex > 0 {
            queueIndex -= 1
            load(queue[queueIndex], startAt: 0)
        } else {
            seek(to: 0)
        }
    }

    func playRandom() {
        guard !queue.isEmpty else { return }
        loadNextFromBag()
    }

    private func handleItemEnded() {
        switch repeatMode {
        case .one:
            seek(to: 0)
            player.playImmediately(atRate: rate)
        case .off, .all:
            if let id = current?.id { library?.setResume(id, time: 0) }
            next(auto: true)
        }
    }

    // MARK: - Shuffle bag — every video plays once before any repeats

    private func refillBag() {
        shuffleBag = queue.filter { $0.id != current?.id }.shuffled()
    }

    private func loadNextFromBag() {
        if shuffleBag.isEmpty { refillBag() }
        guard let pick = shuffleBag.popLast() else {
            if let current { load(current, startAt: 0) }
            return
        }
        queueIndex = queue.firstIndex { $0.id == pick.id } ?? queueIndex
        load(pick, startAt: 0)
    }

    // MARK: - Queue editing

    func playNext(_ video: Video) {
        queue.removeAll { $0.id == video.id }
        let insertAt = min(queueIndex + 1, queue.count)
        queue.insert(video, at: insertAt)
    }

    func addToQueue(_ video: Video) {
        guard !queue.contains(where: { $0.id == video.id }) else { return }
        queue.append(video)
    }

    func jump(to video: Video) {
        guard let index = queue.firstIndex(where: { $0.id == video.id }) else { return }
        queueIndex = index
        load(video, startAt: 0)
    }

    // MARK: - Presentation

    func minimize() { if current != nil { presentation = .mini } }
    func expand() { if current != nil { presentation = .full } }

    func close() {
        savePosition()
        player.pause()
        player.replaceCurrentItem(with: nil)
        current = nil
        queue = []
        queueIndex = 0
        presentation = .hidden
        wantsLandscape = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    /// Called when the Shorts feed takes over, so two videos never play at once.
    func suspendForShorts() {
        guard current != nil else { return }
        pause()
        if presentation == .full { presentation = .mini }
    }

    private func savePosition() {
        guard let current, duration > 0 else { return }
        let atEnd = currentTime >= duration - 2
        library?.setResume(current.id, time: atEnd ? 0 : currentTime)
    }

    // MARK: - Lock screen / Control Centre

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.play() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.pause() }
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.togglePlayPause() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.next() }
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.previous() }
            return .success
        }
        center.skipForwardCommand.preferredIntervals = [10]
        center.skipForwardCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.skip(by: 10) }
            return .success
        }
        center.skipBackwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.skip(by: -10) }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor [weak self] in self?.seek(to: event.positionTime) }
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let current else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: current.title,
            MPMediaItemPropertyArtist: current.source,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? Double(rate) : 0
        ]
        if let poster = ThumbnailStore.shared.cached(current) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: poster.size) { _ in poster }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func updateNowPlayingElapsed() {
        guard MPNowPlayingInfoCenter.default().nowPlayingInfo != nil else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? Double(rate) : 0
    }
}
