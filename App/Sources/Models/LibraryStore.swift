import AVFoundation
import Foundation

@MainActor
final class LibraryStore: ObservableObject {
    @Published private(set) var videos: [Video] = []
    @Published private(set) var playlists: [Playlist] = []
    /// Most recently watched first, de-duplicated.
    @Published private(set) var history: [UUID] = []
    @Published private(set) var isScanning = false

    private var saveTask: Task<Void, Never>?

    init() {
        LibraryPaths.prepare()
        load()
    }

    // MARK: - Persistence

    private func load() {
        guard let data = try? Data(contentsOf: LibraryPaths.metadata),
              let snapshot = try? JSONDecoder().decode(LibrarySnapshot.self, from: data) else { return }
        videos = snapshot.videos
        playlists = snapshot.playlists
        history = snapshot.history
    }

    /// Coalesces rapid mutations (scrubbing writes a resume time constantly) into one write.
    private func scheduleSave() {
        saveTask?.cancel()
        let snapshot = LibrarySnapshot(videos: videos, playlists: playlists, history: history)
        saveTask = Task { [snapshot] in
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            guard let data = try? encoder.encode(snapshot) else { return }
            try? data.write(to: LibraryPaths.metadata, options: .atomic)
        }
    }

    func saveNow() {
        saveTask?.cancel()
        let snapshot = LibrarySnapshot(videos: videos, playlists: playlists, history: history)
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        try? data.write(to: LibraryPaths.metadata, options: .atomic)
    }

    // MARK: - Scanning

    /// Reconciles the metadata file with what is actually in the Videos folder:
    /// picks up anything copied in from Finder/Files, drops entries whose file is gone.
    func rescan() async {
        guard !isScanning else { return }
        isScanning = true
        defer { isScanning = false }

        LibraryPaths.prepare()
        let fm = FileManager.default
        let found = filesOnDisk()

        // Drop entries whose file disappeared.
        let missing = videos.filter { !found.contains($0.fileName) }.map(\.id)
        if !missing.isEmpty {
            videos.removeAll { missing.contains($0.id) }
            history.removeAll { missing.contains($0) }
            for index in playlists.indices {
                playlists[index].videoIDs.removeAll { missing.contains($0) }
            }
        }

        // Add files that have no metadata yet.
        let known = Set(videos.map(\.fileName))
        for name in found.sorted() where !known.contains(name) {
            let url = LibraryPaths.videos.appendingPathComponent(name)
            var video = Video(fileName: name, title: Self.prettyTitle(from: name))
            let attributes = try? fm.attributesOfItem(atPath: url.path)
            video.fileSize = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
            video.dateAdded = (attributes?[.creationDate] as? Date) ?? Date()
            await Self.probe(&video)
            videos.append(video)
        }

        videos.sort { $0.dateAdded > $1.dateAdded }
        scheduleSave()
    }

    private func filesOnDisk() -> Set<String> {
        let fm = FileManager.default
        guard let walker = fm.enumerator(at: LibraryPaths.videos,
                                         includingPropertiesForKeys: [.isRegularFileKey],
                                         options: [.skipsHiddenFiles]) else { return [] }
        var names: Set<String> = []
        let root = LibraryPaths.videos.standardizedFileURL.path
        for case let url as URL in walker {
            guard LibraryPaths.videoExtensions.contains(url.pathExtension.lowercased()) else { continue }
            let path = url.standardizedFileURL.path
            guard path.hasPrefix(root) else { continue }
            names.insert(String(path.dropFirst(root.count + 1)))
        }
        return names
    }

    /// Reads duration and pixel dimensions off the file.
    static func probe(_ video: inout Video) async {
        let asset = AVURLAsset(url: video.url)
        if let duration = try? await asset.load(.duration) {
            let seconds = CMTimeGetSeconds(duration)
            if seconds.isFinite, seconds > 0 { video.duration = seconds }
        }
        if let track = try? await asset.loadTracks(withMediaType: .video).first,
           let size = try? await track.load(.naturalSize),
           let transform = try? await track.load(.preferredTransform) {
            let display = size.applying(transform)
            video.width = Int(abs(display.width).rounded())
            video.height = Int(abs(display.height).rounded())
        }
    }

    static func prettyTitle(from fileName: String) -> String {
        let base = (fileName as NSString).lastPathComponent
        let stem = (base as NSString).deletingPathExtension
        let spaced = stem
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: "  ", with: " ")
            .trimmingCharacters(in: .whitespaces)
        return spaced.isEmpty ? base : spaced
    }

    // MARK: - Lookups

    func video(id: UUID) -> Video? { videos.first { $0.id == id } }

    func resolve(ids: [UUID]) -> [Video] {
        let byID = Dictionary(uniqueKeysWithValues: videos.map { ($0.id, $0) })
        return ids.compactMap { byID[$0] }
    }

    var recent: [Video] { videos.sorted { $0.dateAdded > $1.dateAdded } }
    var liked: [Video] { videos.filter(\.isLiked).sorted { ($0.lastPlayed ?? $0.dateAdded) > ($1.lastPlayed ?? $1.dateAdded) } }
    var mostPlayed: [Video] { videos.filter { $0.plays > 0 }.sorted { $0.plays > $1.plays } }
    var unwatched: [Video] { videos.filter { $0.plays == 0 } }
    var longest: [Video] { videos.sorted { $0.duration > $1.duration } }
    var continueWatching: [Video] {
        videos.filter { $0.watchProgress > 0.02 && !$0.isFinished }
            .sorted { ($0.lastPlayed ?? .distantPast) > ($1.lastPlayed ?? .distantPast) }
    }
    var historyVideos: [Video] { resolve(ids: history) }

    /// Portrait or short clips for the swipe feed; falls back to everything
    /// so the feed is never empty just because nothing is portrait.
    var shorts: [Video] {
        let picks = videos.filter(\.qualifiesAsShort)
        return picks.isEmpty ? videos : picks
    }

    func search(_ query: String) -> [Video] {
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !needle.isEmpty else { return [] }
        return videos.filter {
            $0.title.lowercased().contains(needle) || $0.fileName.lowercased().contains(needle)
        }
    }

    /// Everything except `video`, used to build the "Up next" queue.
    func relatedTo(_ video: Video) -> [Video] {
        videos.filter { $0.id != video.id }
    }

    // MARK: - Mutations

    private func mutate(_ id: UUID, _ change: (inout Video) -> Void) {
        guard let index = videos.firstIndex(where: { $0.id == id }) else { return }
        change(&videos[index])
        scheduleSave()
    }

    func toggleLike(_ id: UUID) {
        mutate(id) { $0.isLiked.toggle() }
    }

    func setLiked(_ id: UUID, _ liked: Bool) {
        mutate(id) { $0.isLiked = liked }
    }

    func rename(_ id: UUID, to title: String) {
        let clean = title.trimmingCharacters(in: .whitespaces)
        guard !clean.isEmpty else { return }
        mutate(id) { $0.title = clean }
    }

    func recordPlay(_ id: UUID) {
        mutate(id) {
            $0.plays += 1
            $0.lastPlayed = Date()
        }
        history.removeAll { $0 == id }
        history.insert(id, at: 0)
        if history.count > 200 { history.removeLast(history.count - 200) }
        scheduleSave()
    }

    func setResume(_ id: UUID, time: Double) {
        mutate(id) { $0.resumeTime = max(0, time) }
    }

    func clearHistory() {
        history.removeAll()
        for index in videos.indices { videos[index].resumeTime = 0 }
        scheduleSave()
    }

    func removeFromHistory(_ id: UUID) {
        history.removeAll { $0 == id }
        scheduleSave()
    }

    func delete(_ id: UUID) {
        guard let video = video(id: id) else { return }
        try? FileManager.default.removeItem(at: video.url)
        ThumbnailStore.shared.forget(video)
        videos.removeAll { $0.id == id }
        history.removeAll { $0 == id }
        for index in playlists.indices {
            playlists[index].videoIDs.removeAll { $0 == id }
        }
        scheduleSave()
    }

    func add(_ video: Video) {
        videos.insert(video, at: 0)
        scheduleSave()
    }

    // MARK: - Playlists

    @discardableResult
    func createPlaylist(named name: String, with ids: [UUID] = []) -> Playlist {
        let playlist = Playlist(name: name, videoIDs: ids)
        playlists.append(playlist)
        scheduleSave()
        return playlist
    }

    func playlist(id: UUID) -> Playlist? { playlists.first { $0.id == id } }

    func addToPlaylist(_ playlistID: UUID, video videoID: UUID) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        guard !playlists[index].videoIDs.contains(videoID) else { return }
        playlists[index].videoIDs.append(videoID)
        scheduleSave()
    }

    func removeFromPlaylist(_ playlistID: UUID, video videoID: UUID) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        playlists[index].videoIDs.removeAll { $0 == videoID }
        scheduleSave()
    }

    func deletePlaylist(_ playlistID: UUID) {
        playlists.removeAll { $0.id == playlistID }
        scheduleSave()
    }

    func renamePlaylist(_ playlistID: UUID, to name: String) {
        guard let index = playlists.firstIndex(where: { $0.id == playlistID }) else { return }
        let clean = name.trimmingCharacters(in: .whitespaces)
        guard !clean.isEmpty else { return }
        playlists[index].name = clean
        scheduleSave()
    }
}
