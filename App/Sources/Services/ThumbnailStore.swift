import AVFoundation
import UIKit

/// Generates and caches poster frames. Memory cache in front of a disk cache,
/// so scrolling back up the feed never re-decodes a frame.
///
/// The cache bookkeeping is main-actor state; the decoding and file IO are
/// `nonisolated async`, so they run off the main thread and never stall a scroll.
@MainActor
final class ThumbnailStore: ObservableObject {
    static let shared = ThumbnailStore()

    private let memory = NSCache<NSString, UIImage>()
    private var inFlight: [UUID: Task<UIImage?, Never>] = [:]

    private init() {
        memory.countLimit = 120
        LibraryPaths.prepare()
    }

    nonisolated private func diskURL(for id: UUID) -> URL {
        LibraryPaths.thumbnails.appendingPathComponent("\(id.uuidString).jpg")
    }

    func cached(_ video: Video) -> UIImage? {
        memory.object(forKey: video.id.uuidString as NSString)
    }

    func poster(for video: Video) async -> UIImage? {
        if let hit = cached(video) { return hit }
        if let existing = inFlight[video.id] { return await existing.value }

        let id = video.id
        let sourceURL = video.url
        let cacheURL = diskURL(for: id)
        let seconds = Self.posterTime(for: video)

        let task = Task<UIImage?, Never> {
            if let onDisk = await Self.readImage(at: cacheURL) { return onDisk }
            guard let image = await Self.generate(from: sourceURL, at: seconds,
                                                  maxSize: CGSize(width: 800, height: 800)) else { return nil }
            await Self.writeJPEG(image, to: cacheURL)
            return image
        }
        inFlight[id] = task
        let image = await task.value
        inFlight[id] = nil
        if let image { memory.setObject(image, forKey: id.uuidString as NSString) }
        return image
    }

    /// One-off frame for the scrub preview — deliberately small and uncached.
    func frame(for video: Video, at seconds: Double) async -> UIImage? {
        await Self.generate(from: video.url, at: seconds, maxSize: CGSize(width: 320, height: 320))
    }

    func forget(_ video: Video) {
        memory.removeObject(forKey: video.id.uuidString as NSString)
        let url = diskURL(for: video.id)
        Task { await Self.removeFile(at: url) }
    }

    /// A frame ~12% in — far enough past black intros to be recognisable.
    nonisolated private static func posterTime(for video: Video) -> Double {
        guard video.duration > 0 else { return 1 }
        return min(max(video.duration * 0.12, 0.5), max(video.duration - 0.2, 0.5))
    }

    // MARK: - Off-main work

    nonisolated private static func generate(from url: URL, at seconds: Double,
                                             maxSize: CGSize) async -> UIImage? {
        let asset = AVURLAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = maxSize
        generator.requestedTimeToleranceBefore = CMTime(seconds: 1, preferredTimescale: 600)
        generator.requestedTimeToleranceAfter = CMTime(seconds: 1, preferredTimescale: 600)
        let time = CMTime(seconds: max(0, seconds), preferredTimescale: 600)
        guard let cgImage = try? await generator.image(at: time).image else { return nil }
        return UIImage(cgImage: cgImage)
    }

    nonisolated private static func readImage(at url: URL) async -> UIImage? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    nonisolated private static func writeJPEG(_ image: UIImage, to url: URL) async {
        guard let data = image.jpegData(compressionQuality: 0.8) else { return }
        try? data.write(to: url, options: .atomic)
    }

    nonisolated private static func removeFile(at url: URL) async {
        try? FileManager.default.removeItem(at: url)
    }
}
